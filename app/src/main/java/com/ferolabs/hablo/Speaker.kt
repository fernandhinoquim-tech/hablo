package com.ferolabs.hablo

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** En qué anda el motor de voz. La pantalla de inicio lo muestra. */
enum class VoicePhase {
    /** Primer arranque: copiando los modelos al almacenamiento interno. */
    PREPARANDO,

    /** Todo bien, hablando con las voces propias de la app. */
    LISTO,

    /** Piper no arrancó; se usa el motor de Android como respaldo. */
    RESPALDO,

    /** Ni Piper ni Android: no hay voz. */
    SIN_VOZ
}

/**
 * Motor de voz de la app.
 *
 * Usa Piper (voces neuronales dentro del APK, vía sherpa-onnx). Si Piper falla,
 * cae al motor de Android. Nada de esto necesita internet.
 */
class Speaker(context: Context) {

    private val app = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()
    private val requestId = AtomicLong(0)

    /** Subir este número obliga a reinstalar los modelos. */
    private val assetsVersion = "v1"

    // --- Estado observable por la interfaz ----------------------------------

    var phase by mutableStateOf(VoicePhase.PREPARANDO)
        private set

    /** 0 a 100 mientras copia los modelos la primera vez. */
    var prepareProgress by mutableStateOf(0)
        private set

    /** Detalle del problema, para poder mostrarlo en pantalla. */
    var errorDetail by mutableStateOf<String?>(null)
        private set

    /** true mientras genera o reproduce audio. */
    var busy by mutableStateOf(false)
        private set

    /** Nombre de la voz cargada en memoria, si hay alguna. */
    var loadedVoice by mutableStateOf<String?>(null)
        private set

    // --- Internos ------------------------------------------------------------

    private var piper: OfflineTts? = null
    private var piperBroken = false
    private var track: AudioTrack? = null

    private var androidTts: TextToSpeech? = null

    @Volatile
    private var androidTtsReady = false

    init {
        androidTts = TextToSpeech(app) { status ->
            androidTtsReady = status == TextToSpeech.SUCCESS
        }

        worker.execute {
            try {
                installAssets()
                val problema = verifyInstall()
                if (problema != null) {
                    fail("Los modelos no quedaron completos: $problema")
                } else {
                    phase = VoicePhase.LISTO
                    prepareProgress = 100
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error preparando las voces", e)
                fail("No se pudieron copiar los modelos: ${e.javaClass.simpleName} ${e.message}")
            }
        }
    }

    private fun fail(detail: String) {
        Log.e(TAG, detail)
        piperBroken = true
        errorDetail = detail
        phase = if (anyAndroidEnglishVoice()) VoicePhase.RESPALDO else VoicePhase.SIN_VOZ
    }

    // ------------------------------------------------------------------------
    // Instalación de los modelos: de assets a disco.
    // espeak-ng necesita rutas reales, no puede leer desde el APK.
    // ------------------------------------------------------------------------

    private fun voicesRoot(): File = File(app.filesDir, "tts-$assetsVersion")

    private fun installAssets() {
        val root = voicesRoot()
        if (File(root, ".listo").exists()) {
            prepareProgress = 100
            return
        }

        app.filesDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("tts-") }
            ?.forEach { it.deleteRecursively() }

        root.mkdirs()

        val files = mutableListOf<String>()
        collectAssets("tts", files)
        if (files.isEmpty()) throw IllegalStateException("el APK no trae la carpeta assets/tts")

        var done = 0
        for (assetPath in files) {
            val outFile = File(root, assetPath.removePrefix("tts/"))
            outFile.parentFile?.mkdirs()
            app.assets.open(assetPath).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            done++
            prepareProgress = (done * 100) / files.size
        }

        File(root, ".listo").writeText(assetsVersion)
        Log.i(TAG, "Modelos instalados: ${files.size} archivos")
    }

    private fun collectAssets(path: String, out: MutableList<String>) {
        val children = app.assets.list(path) ?: return
        if (children.isEmpty()) {
            out.add(path)
            return
        }
        for (child in children) collectAssets("$path/$child", out)
    }

    /** Devuelve null si todo está bien, o el nombre de lo que falta. */
    private fun verifyInstall(): String? {
        val root = voicesRoot()
        val espeak = File(root, "espeak-ng-data")
        if (!espeak.isDirectory) return "falta espeak-ng-data"
        for (t in TEACHERS) {
            val m = File(root, "${t.id}/model.onnx")
            val k = File(root, "${t.id}/tokens.txt")
            if (!m.isFile || m.length() < 1_000_000) return "falta el modelo de ${t.name}"
            if (!k.isFile) return "falta tokens.txt de ${t.name}"
        }
        return null
    }

    // ------------------------------------------------------------------------
    // Piper
    // ------------------------------------------------------------------------

    private fun ensureVoiceLoaded(teacher: Teacher): Boolean {
        if (piperBroken) return false
        if (loadedVoice == teacher.id && piper != null) return true

        releasePiper()

        return try {
            val root = voicesRoot()
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = File(root, "${teacher.id}/model.onnx").absolutePath,
                        lexicon = "",
                        tokens = File(root, "${teacher.id}/tokens.txt").absolutePath,
                        dataDir = File(root, "espeak-ng-data").absolutePath
                    ),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                )
            )
            piper = OfflineTts(config = config)
            loadedVoice = teacher.id
            phase = VoicePhase.LISTO
            Log.i(TAG, "Voz cargada: ${teacher.id}")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Piper no arrancó", e)
            fail("El motor de voz no arrancó: ${e.javaClass.simpleName} ${e.message}")
            false
        }
    }

    private fun releasePiper() {
        try {
            piper?.release()
        } catch (e: Throwable) {
            // sin acción
        }
        piper = null
        loadedVoice = null
    }

    // ------------------------------------------------------------------------
    // Hablar
    // ------------------------------------------------------------------------

    fun speak(text: String, teacher: Teacher, rateScale: Float = 1.0f) {
        if (text.isBlank()) return
        val id = requestId.incrementAndGet()
        stopAudio()

        worker.execute {
            if (id != requestId.get()) return@execute
            busy = true
            try {
                val speed = (teacher.rate * rateScale).coerceIn(0.5f, 1.6f)

                if (ensureVoiceLoaded(teacher)) {
                    val engine = piper
                    if (engine != null) {
                        try {
                            val audio = engine.generate(text = text, sid = 0, speed = speed)
                            if (id != requestId.get()) return@execute
                            play(audio.samples, engine.sampleRate(), id)
                            return@execute
                        } catch (e: Throwable) {
                            Log.e(TAG, "Falló al generar audio", e)
                            fail("Falló al generar el audio: ${e.javaClass.simpleName} ${e.message}")
                        }
                    }
                }

                speakWithAndroid(text, teacher, rateScale)
            } finally {
                busy = false
            }
        }
    }

    /** Reproduce la grabación del propio usuario (viene a 16 kHz). */
    fun playRecording(samples: FloatArray) {
        if (samples.isEmpty()) return
        val id = requestId.incrementAndGet()
        stopAudio()
        worker.execute {
            if (id != requestId.get()) return@execute
            busy = true
            try {
                play(samples, 16000, id)
            } finally {
                busy = false
            }
        }
    }

    /**
     * Reproduce las muestras. Se convierten a 16 bits porque es el formato que
     * soporta absolutamente todo, emuladores incluidos.
     */
    private fun play(samples: FloatArray, sampleRate: Int, id: Long) {
        if (samples.isEmpty()) {
            fail("El motor generó audio vacío")
            return
        }
        try {
            val pcm = ShortArray(samples.size) { i ->
                (samples[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            }

            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBuffer, pcm.size * 2)

            val newTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track = newTrack
            newTrack.play()
            newTrack.write(pcm, 0, pcm.size)

            // AudioTrack.write() solo ENCOLA el audio: vuelve antes de que suene.
            // Si soltamos el reproductor aqui, el sonido se corta antes de empezar.
            // Por eso esperamos a que la cabeza de reproduccion llegue al final.
            val durationMs = (pcm.size * 1000L) / sampleRate
            val deadline = System.currentTimeMillis() + durationMs + 1500
            while (System.currentTimeMillis() < deadline) {
                if (id != requestId.get()) break
                if (newTrack.playbackHeadPosition >= pcm.size) break
                Thread.sleep(25)
            }

            newTrack.stop()
            newTrack.release()
            if (track === newTrack) track = null
        } catch (e: Throwable) {
            Log.e(TAG, "Error reproduciendo", e)
            errorDetail = "No se pudo reproducir el audio: ${e.javaClass.simpleName} ${e.message}"
        }
    }

    private fun stopAudio() {
        val t = track ?: return
        try {
            t.pause()
            t.flush()
            t.stop()
            t.release()
        } catch (e: Throwable) {
            // sin acción
        }
        track = null
    }

    // ------------------------------------------------------------------------
    // Respaldo: motor de Android
    // ------------------------------------------------------------------------

    private fun androidOfflineVoices(locale: Locale): List<Voice> {
        val all = try {
            androidTts?.voices
        } catch (e: Exception) {
            null
        } ?: return emptyList()

        return all.filter { v ->
            v.locale?.language == locale.language &&
                v.locale?.country.equals(locale.country, ignoreCase = true) &&
                !v.isNetworkConnectionRequired
        }.sortedBy { it.name }
    }

    private fun anyAndroidEnglishVoice(): Boolean =
        androidOfflineVoices(Locale.US).isNotEmpty() || androidOfflineVoices(Locale.UK).isNotEmpty()

    private fun speakWithAndroid(text: String, teacher: Teacher, rateScale: Float) {
        val engine = androidTts
        if (engine == null || !androidTtsReady) return
        try {
            val locale = if (teacher.accent == Accent.UK) Locale.UK else Locale.US
            engine.setLanguage(locale)
            val candidates = androidOfflineVoices(locale)
            if (candidates.isNotEmpty()) {
                engine.setVoice(candidates[teacher.voiceSlot % candidates.size])
            }
            engine.setPitch(teacher.pitch)
            engine.setSpeechRate((teacher.rate * rateScale).coerceIn(0.4f, 1.6f))
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hablo")
        } catch (e: Throwable) {
            Log.e(TAG, "Tampoco funcionó el motor de Android", e)
        }
    }

    // ------------------------------------------------------------------------

    /** Reporte técnico para la pantalla de ajustes. */
    fun diagnostics(): String {
        val root = voicesRoot()
        val sb = StringBuilder()
        sb.append("Estado: ${phase.name}\n")
        sb.append("Instalación: $prepareProgress%\n")
        sb.append("Voz cargada: ${loadedVoice ?: "ninguna"}\n")
        sb.append("Carpeta: ${root.absolutePath}\n")
        sb.append("espeak-ng-data: ${if (File(root, "espeak-ng-data").isDirectory) "ok" else "FALTA"}\n")
        for (t in TEACHERS) {
            val m = File(root, "${t.id}/model.onnx")
            sb.append("${t.name}: ${if (m.isFile) "${m.length() / 1024 / 1024} MB" else "FALTA"}\n")
        }
        sb.append("Voces de Android: ${androidOfflineVoices(Locale.US).size + androidOfflineVoices(Locale.UK).size}\n")
        errorDetail?.let { sb.append("\nError: $it") }
        return sb.toString()
    }

    fun stop() {
        requestId.incrementAndGet()
        stopAudio()
        try {
            androidTts?.stop()
        } catch (e: Throwable) {
            // sin acción
        }
    }

    fun shutdown() {
        stop()
        worker.execute { releasePiper() }
        worker.shutdown()
        try {
            androidTts?.shutdown()
        } catch (e: Throwable) {
            // sin acción
        }
        androidTts = null
    }

    companion object {
        private const val TAG = "HabloSpeaker"
    }
}
