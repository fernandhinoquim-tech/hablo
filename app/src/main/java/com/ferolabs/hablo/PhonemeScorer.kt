package com.ferolabs.hablo

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.sqrt

/** Veredicto de un fonema objetivo dentro de una palabra. */
data class SoundItem(val word: String, val phone: String, val score: Double, val verdict: WordScore)

/** Lo que se le muestra al alumno sobre el sonido que entrena el ejercicio. */
data class SoundReport(val sound: Sound, val items: List<SoundItem>, val millis: Long) {
    val worst: WordScore
        get() = items.maxByOrNull { it.verdict.ordinal }?.verdict ?: WordScore.BIEN
}

/**
 * Evaluación de pronunciación por fonema (GOP), como la hacen las apps
 * comerciales: se conoce la frase, se sacan sus fonemas esperados del
 * diccionario CMU, un reconocedor de fonemas (wav2vec2 entrenado con habla
 * no nativa) da la probabilidad de cada fonema en cada trama, y `Gop`
 * puntúa cuánto mejor explica el audio el fonema esperado que sus
 * alternativas. El veredicto sale de umbrales calibrados con grabaciones
 * reales (`assets/gop/thresholds.json`, generado por tools/asr-bench/calibrate.py)
 * y solo sobre el sonido que entrena el ejercicio: lo demás no se muestra.
 *
 * Usar la frase esperada para ALINEAR fonemas no es sesgar el reconocedor:
 * el puntaje puede ser bajo. La regla "el reconocedor de palabras nunca ve la
 * frase" sigue igual para Moonshine.
 *
 * Pesado (≈300 MB en disco, ≈400 MB cargado): se carga al entrar a una
 * pantalla que lo usa y se suelta al salir (`release`). Nunca convive con el
 * modelo de IA de la Fase 3.
 */
class PhonemeScorer(context: Context) {

    private val app = context.applicationContext
    private val assetsVersion = "v1"

    private class Threshold(val score: String, val red: Double, val yellow: Double)


    private var session: OrtSession? = null
    private var id2sym: Map<Int, String> = emptyMap()
    private var sym2id: Map<String, Int> = emptyMap()
    private var blank = -1
    private var phoneIds = IntArray(0)
    private var thresholds: Map<Sound, Threshold> = emptyMap()
    private var dict: HashMap<String, List<String>>? = null

    /** Null si está listo; si no, por qué no (se muestra en pantalla). */
    @Volatile
    var unavailableReason: String? = null
        private set

    val loaded: Boolean get() = session != null

    private fun root(): File = File(app.filesDir, "gop-$assetsVersion")

    /** Copia el modelo al almacenamiento interno la primera vez y abre la sesión. */
    @Synchronized
    fun ensureLoaded(): Boolean {
        if (session != null) return true
        return try {
            val names = app.assets.list("gop") ?: emptyArray()
            if ("model.int8.onnx" !in names) {
                unavailableReason = "Esta compilación no trae el modelo de fonemas."
                return false
            }
            val dir = root()
            val model = File(dir, "model.int8.onnx")
            if (!File(dir, ".listo").exists()) {
                app.filesDir.listFiles { f -> f.isDirectory && f.name.startsWith("gop-") && f != dir }
                    ?.forEach { it.deleteRecursively() }
                dir.deleteRecursively()
                dir.mkdirs()
                app.assets.open("gop/model.int8.onnx").use { input ->
                    model.outputStream().use { output -> input.copyTo(output) }
                }
                File(dir, ".listo").writeText(assetsVersion)
            }

            loadVocab()
            loadThresholds()
            val t0 = System.currentTimeMillis()
            val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(4) }
            session = OrtEnvironment.getEnvironment().createSession(model.absolutePath, opts)
            Log.i(TAG, "Modelo de fonemas listo en ${System.currentTimeMillis() - t0} ms; sonidos con umbral: ${thresholds.keys.joinToString { it.key }}")
            unavailableReason = null
            true
        } catch (e: Throwable) {
            Log.e(TAG, "No arrancó el modelo de fonemas", e)
            unavailableReason = "El modelo de fonemas no arrancó: ${e.javaClass.simpleName} ${e.message}"
            false
        }
    }

    private fun loadVocab() {
        val json = JSONObject(app.assets.open("gop/vocab.json").bufferedReader().use { it.readText() })
        val map = HashMap<Int, String>()
        for (key in json.keys()) map[key.toInt()] = json.getString(key)
        id2sym = map
        sym2id = map.entries.associate { it.value to it.key }
        blank = map.entries.first { it.value == "[PAD]" || it.value == "<pad>" }.key
        val notPhones = setOf("[PAD]", "<pad>", "[UNK]", "<unk>", "|", " ", "ˌ", "ˈ", "͡", "<s>", "</s>")
        phoneIds = map.entries.filter { it.value !in notPhones }.map { it.key }.sorted().toIntArray()
    }

    private fun loadThresholds() {
        val json = JSONObject(app.assets.open("gop/thresholds.json").bufferedReader().use { it.readText() })
        val sounds = json.getJSONObject("sounds")
        val out = HashMap<Sound, Threshold>()
        for (key in sounds.keys()) {
            val s = Sound.parse(key) ?: continue
            val o = sounds.getJSONObject(key)
            // Una banda cuya precisión medida no llega al mínimo no se
            // muestra: se apaga poniéndole un umbral inalcanzable.
            val redOk = o.optDouble("red_precision", 0.0) >= MIN_PRECISION
            val yellowOk = o.optDouble("yellow_precision", 0.0) >= MIN_PRECISION
            if (!redOk && !yellowOk) {
                Log.w(
                    TAG,
                    "sonido $key sin banda utilizable (precisión rojo " +
                        o.optDouble("red_precision", 0.0) + ", amarillo " +
                        o.optDouble("yellow_precision", 0.0) + "): no se evalúa"
                )
                continue
            }
            out[s] = Threshold(
                o.getString("score"),
                if (redOk) o.getDouble("red") else Double.NEGATIVE_INFINITY,
                if (yellowOk) o.getDouble("yellow") else Double.NEGATIVE_INFINITY
            )
        }
        thresholds = out
    }

    /** ¿Este sonido tiene umbral calibrado (y por tanto veredicto)? */
    fun evaluates(sound: Sound): Boolean = sound in thresholds

    // ------------------------------------------------------------------------
    // Fonemas esperados: CMUdict (ARPAbet) → alfabeto del modelo (IPA de L2-ARCTIC).
    // Misma tabla que tools/asr-bench/phoneme_eval.py.
    // ------------------------------------------------------------------------

    private fun ensureDict(): HashMap<String, List<String>> {
        dict?.let { return it }
        val map = HashMap<String, List<String>>(140_000)
        app.assets.open("gop/cmudict.dict").bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.startsWith(";;;")) continue
                val sp = line.indexOf(' ')
                if (sp <= 0) continue
                val word = line.substring(0, sp)
                if (word.contains('(')) continue // variantes: solo la primera
                map[word] = line.substring(sp + 1).trim().split(' ').map { it.trimEnd('0', '1', '2') }
            }
        }
        dict = map
        return map
    }

    /** Palabras tal como se buscan en el diccionario (guion = dos palabras). */
    private fun spokenWords(text: String): List<String> =
        normalizeAnswer(text.replace('-', ' ')).split(' ').filter { it.isNotBlank() }

    private fun ipa(arpa: String): List<String> = when (arpa) {
        "AA" -> listOf("ɑ"); "AE" -> listOf("æ"); "AH" -> listOf("ʌ"); "AO" -> listOf("ɔ")
        "AW" -> listOf("a", "ʊ"); "AY" -> listOf("a", "ɪ"); "B" -> listOf("b"); "CH" -> listOf("tʃ")
        "D" -> listOf("d"); "DH" -> listOf("ð"); "EH" -> listOf("ɛ"); "ER" -> listOf("ɚ")
        "EY" -> listOf("e", "ɪ"); "F" -> listOf("f"); "G" -> listOf("ɡ"); "HH" -> listOf("h")
        "IH" -> listOf("ɪ"); "IY" -> listOf("i"); "JH" -> listOf("dʒ"); "K" -> listOf("k")
        "L" -> listOf("l"); "M" -> listOf("m"); "N" -> listOf("n"); "NG" -> listOf("ŋ")
        "OW" -> listOf("o", "ʊ"); "OY" -> listOf("ɔ", "ɪ"); "P" -> listOf("p"); "R" -> listOf("ɹ")
        "S" -> listOf("s"); "SH" -> listOf("ʃ"); "T" -> listOf("t"); "TH" -> listOf("θ")
        "UH" -> listOf("ʊ"); "UW" -> listOf("u"); "V" -> listOf("v"); "W" -> listOf("w")
        "Y" -> listOf("j"); "Z" -> listOf("z"); "ZH" -> listOf("ʒ")
        else -> emptyList()
    }

    /** [(palabra, fonemas)] o null si alguna palabra no está en el diccionario. */
    private fun expectedPhones(phrase: String): List<Pair<String, List<String>>>? {
        val d = ensureDict()
        val out = ArrayList<Pair<String, List<String>>>()
        for (w in spokenWords(phrase)) {
            val arpa = d[w] ?: run {
                Log.w(TAG, "'$w' no está en cmudict: sin fonemas para '$phrase'")
                return null
            }
            val phones = ArrayList<String>()
            for (a in arpa) for (sym0 in ipa(a)) {
                var sym = sym0
                if (sym !in sym2id) sym = when (sym) { "ʌ" -> "ə"; "ə" -> "ʌ"; "ɚ" -> "ɝ"; else -> sym }
                if (sym !in sym2id && sym.length == 2 && "͡" in sym2id) {
                    phones += listOf(sym.substring(0, 1), "͡", sym.substring(1)) // t ͡ ʃ
                    continue
                }
                if (sym !in sym2id) {
                    Log.w(TAG, "el modelo no tiene el símbolo '$sym'")
                    return null
                }
                phones.add(sym)
            }
            out.add(w to phones)
        }
        return out
    }

    private val vowels = "ɑæʌɔaʊɪeiɚoɛuə"

    /** Qué fonemas (índice de palabra, índice de fonema, tipo) evalúa cada sonido. */
    private fun targets(sound: Sound, words: List<Pair<String, List<String>>>): List<Triple<Int, Int, String>> {
        val out = ArrayList<Triple<Int, Int, String>>()
        words.forEachIndexed { wi, (w, ps) ->
            when (sound) {
                Sound.SH -> {
                    ps.forEachIndexed { i, p ->
                        if (p == "ʃ" && !(i >= 2 && ps[i - 1] == "͡")) out.add(Triple(wi, i, "sub"))
                        if (p == "t" && i + 1 < ps.size && ps[i + 1] == "͡") out.add(Triple(wi, i, "sub"))
                    }
                }
                Sound.TH -> ps.forEachIndexed { i, p -> if (p == "θ" || p == "ð") out.add(Triple(wi, i, "sub")) }
                Sound.H -> ps.forEachIndexed { i, p -> if (p == "h") out.add(Triple(wi, i, "sub")) }
                Sound.V -> ps.forEachIndexed { i, p -> if (p == "v") out.add(Triple(wi, i, "sub")) }
                Sound.RL -> ps.forEachIndexed { i, p -> if (p == "ɹ" || p == "l") out.add(Triple(wi, i, "sub")) }
                Sound.FINAL -> if (ps.isNotEmpty() && ps.last() !in vowels) out.add(Triple(wi, ps.size - 1, "sub"))
                Sound.ES -> if (ps.size >= 2 && ps[0] == "s" && ps[1] !in vowels) out.add(Triple(wi, 0, "ins"))
                Sound.ED -> if (w.endsWith("ed") && ps.isNotEmpty() && (ps.last() == "t" || ps.last() == "d")) out.add(Triple(wi, ps.size - 1, "ins"))
                Sound.GENERAL -> {}
            }
        }
        return out
    }

    // ------------------------------------------------------------------------

    /**
     * Puntúa el sonido [sound] en [phrase] sobre el audio ya preparado por
     * [AudioPrep]. Null si el sonido no tiene umbral, si alguna palabra no está
     * en el diccionario, o si el modelo no está.
     */
    fun score(prepared: FloatArray, phrase: String, sound: Sound): SoundReport? {
        val thr = thresholds[sound] ?: return null
        if (!ensureLoaded()) return null
        val words = expectedPhones(phrase) ?: return null
        val targetList = targets(sound, words)
        if (targetList.isEmpty()) return null
        val sess = session ?: return null

        val t0 = System.currentTimeMillis()
        // wav2vec2 espera media 0 y varianza 1
        var mean = 0.0
        for (v in prepared) mean += v
        mean /= prepared.size
        var variance = 0.0
        for (v in prepared) variance += (v - mean) * (v - mean)
        variance /= prepared.size
        val scale = 1.0 / sqrt(variance + 1e-7)
        val x = FloatArray(prepared.size) { ((prepared[it] - mean) * scale).toFloat() }

        val env = OrtEnvironment.getEnvironment()
        val logits: Array<FloatArray>
        OnnxTensor.createTensor(env, FloatBuffer.wrap(x), longArrayOf(1, x.size.toLong())).use { input ->
            sess.run(mapOf("wav" to input)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val out = result[0].value as Array<Array<FloatArray>>
                logits = out[0]
            }
        }
        val logp = Gop.logSoftmax(logits)

        val ids = words.flatMap { it.second }.map { sym2id.getValue(it) }.toIntArray()
        val offsets = IntArray(words.size + 1)
        words.forEachIndexed { i, (_, ps) -> offsets[i + 1] = offsets[i] + ps.size }
        val base = Gop.ctcLogp(logp, ids, blank)

        val items = targetList.map { (wi, pi, _) ->
            val k = offsets[wi] + pi
            val value = when (thr.score) {
                "ins" -> -Gop.ins(logp, ids, k, blank, phoneIds, base)
                else -> Gop.gopAf(logp, ids, k, blank, phoneIds, base)
            }
            val verdict = when {
                value < thr.red -> WordScore.MAL
                value < thr.yellow -> WordScore.DUDOSO
                else -> WordScore.BIEN
            }
            SoundItem(words[wi].first, words[wi].second[pi], value, verdict)
        }
        val ms = System.currentTimeMillis() - t0
        Log.i(TAG, "GOP ${sound.key} en ${ms}ms: " + items.joinToString(" ") { "${it.word}/${it.phone}=%.2f:${it.verdict}".format(java.util.Locale.US, it.score) })
        return SoundReport(sound, items, ms)
    }

    @Synchronized
    fun release() {
        try {
            session?.close()
        } catch (e: Throwable) {
            // sin acción
        }
        session = null
        dict = null
    }

    companion object {
        /**
         * Precisión mínima medida para MOSTRAR una banda de color. Medido en
         * el teléfono de Fero (2026-09-13): con el amarillo de `sh` calibrado
         * al 33 % salieron 24 avisos "dudoso" en 33 intentos, y 9 de cada 10
         * en `th`; una señal que casi siempre suena deja de informar. El
         * proyecto ya había adoptado la barra del 66 % (Silpachai 2024) para
         * lo que se muestra; 0,50 es el mínimo para el amarillo, que es un
         * aviso suave y no un veredicto.
         */
        const val MIN_PRECISION = 0.50

        private const val TAG = "HabloGop"
    }
}
