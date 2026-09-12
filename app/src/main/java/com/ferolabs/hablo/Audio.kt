package com.ferolabs.hablo

import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Lo que devuelve el reconocedor: o entendió algo, o no se pudo ni intentar.
 * [Heard.report] es el veredicto por fonema del sonido del ejercicio (GOP);
 * null si ese sonido todavía no tiene umbral calibrado o el modelo no está.
 */
sealed class ListenResult {
    data class Heard(val text: String, val report: SoundReport? = null) : ListenResult()
    data class NotHeard(val reason: NotHeardReason) : ListenResult()
}

/**
 * Por qué no se puntuó. Se distingue "pronunciaste mal" de "no te escuché":
 * un 0 % afirma que todas las palabras estuvieron mal, y eso no se sabe si el
 * audio venía mudo, cortado o enterrado en ruido.
 */
enum class NotHeardReason(val hintEs: String) {
    TOO_SHORT("Fue muy corto. Toca el micrófono y di la frase completa."),
    TOO_QUIET("Te escuché muy bajito. Acércate al micrófono y repite."),
    TOO_NOISY("Hay mucho ruido de fondo. Busca un sitio más silencioso y repite."),
    NOTHING("No entendí ninguna palabra. Inténtalo otra vez, un poco más despacio.")
}

/** Medidas de una grabación, ya recortada y normalizada. */
class AudioAnalysis(
    /** Audio listo para el modelo: sin DC, recortado y con el percentil 99 en TARGET_PEAK. */
    val prepared: FloatArray,
    val totalSeconds: Float,
    val speechSeconds: Float,
    /** Percentil 99 de |x| del audio crudo (sin DC): el "pico" que ignora un golpe suelto. */
    val peak: Float,
    /** Máximo absoluto de verdad, solo para el log. */
    val maxAbs: Float,
    val speechRms: Float,
    val noiseRms: Float,
    val snrDb: Float,
    /** False cuando casi toda la grabación es voz: el piso salió de la voz y el SNR no vale. */
    val snrReliable: Boolean,
    val gain: Float
) {
    /** Con punto decimal siempre (Locale.US), para poder leerlo desde el PC. */
    fun summary(): String =
        "dur=%.2fs voz=%.2fs pico=%.3f max=%.3f rmsVoz=%.4f piso=%.4f snr=%.1fdB%s ganancia=x%.1f".format(
            Locale.US, totalSeconds, speechSeconds, peak, maxAbs, speechRms, noiseRms, snrDb,
            if (snrReliable) "" else "(no fiable)", gain
        )
}

/**
 * Prepara el audio del micrófono antes de dárselo al reconocedor.
 *
 * Es matemática pura a propósito: `tools/asr-bench/bench.py` hace exactamente
 * lo mismo en Python para poder probar modelos en el PC con las grabaciones
 * reales del teléfono. Si se cambia algo aquí, cambiarlo allá también.
 *
 * Nada de esto mira la frase esperada. Solo limpia la señal.
 */
object AudioPrep {

    const val SAMPLE_RATE = 16000

    /** Tramas de 20 ms. */
    private const val FRAME = SAMPLE_RATE / 50

    /** El piso de ruido es el percentil 15 de la energía por trama. */
    private const val NOISE_PERCENTILE = 0.15f

    /** Una trama tiene voz si supera piso × 3 (unos +9.5 dB) y este mínimo absoluto. */
    private const val SPEECH_RATIO = 3f
    private const val SPEECH_MIN_RMS = 0.004f

    /** Aire que se deja alrededor de la voz al recortar. */
    private const val PAD_BEFORE_SECONDS = 0.25f
    private const val PAD_AFTER_SECONDS = 0.35f

    /**
     * Puertas de calidad. Están flojas a propósito hasta que el corpus real
     * diga otra cosa: un "no te entendí" equivocado sobre audio bueno frustra
     * más que un puntaje bajo ocasional. Mejor intentar y fallar que rechazar.
     */
    const val MIN_SPEECH_SECONDS = 0.15f
    const val MIN_PEAK = 0.005f
    const val MIN_SPEECH_RMS = 0.0015f
    const val MIN_SNR_DB = 5f

    /**
     * El piso de ruido sale de la misma grabación. Si el usuario habla desde
     * el primer instante hasta el último, el percentil cae sobre voz, el piso
     * queda inflado y el SNR miente. Solo se confía en él cuando al menos esta
     * fracción de las tramas quedó por debajo del umbral de voz.
     */
    private const val MIN_SILENT_FRACTION = 0.25f

    /**
     * El pico se toma del percentil 99 de |x|, no del máximo: un golpe seco
     * (el dedo en la pantalla, el teléfono rozando algo) dispara el máximo,
     * deja la ganancia baja y la voz queda callada.
     *
     * Ese percentil se lleva a 0.5, no a 0.9: en voz normal el máximo real
     * está 3-4 veces por encima del percentil 99, y con 0.9 se recortaría
     * ~0.7 % de las muestras (picos de vocales). Con 0.5 el recorte es ~0.04 %
     * y el nivel sigue sobrando (medido con los wav de prueba de sherpa-onnx).
     */
    private const val PEAK_PERCENTILE = 0.99f
    private const val TARGET_PEAK = 0.5f
    private const val MAX_GAIN = 30f

    fun analyze(raw: FloatArray): AudioAnalysis {
        if (raw.isEmpty()) {
            return AudioAnalysis(FloatArray(0), 0f, 0f, 0f, 0f, 0f, 0f, 0f, false, 1f)
        }

        // 1. Quitar el offset DC.
        var sum = 0.0
        for (v in raw) sum += v
        val mean = (sum / raw.size).toFloat()
        val x = FloatArray(raw.size) { raw[it] - mean }

        val peak = percentileAbs(x, PEAK_PERCENTILE)
        var maxAbs = 0f
        for (v in x) {
            val a = abs(v)
            if (a > maxAbs) maxAbs = a
        }

        // 2. Energía por trama.
        val frames = max(1, x.size / FRAME)
        val rms = FloatArray(frames)
        for (f in 0 until frames) {
            val start = f * FRAME
            val end = min(x.size, start + FRAME)
            var acc = 0.0
            for (i in start until end) acc += (x[i] * x[i]).toDouble()
            rms[f] = sqrt(acc / max(1, end - start)).toFloat()
        }

        val sorted = rms.copyOf().also { it.sort() }
        val noiseRms = max(1e-5f, sorted[min(frames - 1, (frames * NOISE_PERCENTILE).toInt())])
        val threshold = max(noiseRms * SPEECH_RATIO, SPEECH_MIN_RMS)

        // 3. Primera y última trama con voz.
        var first = -1
        var last = -1
        var speechFrames = 0
        var speechEnergy = 0.0
        for (f in 0 until frames) {
            if (rms[f] > threshold) {
                if (first < 0) first = f
                last = f
                speechFrames++
                speechEnergy += (rms[f] * rms[f]).toDouble()
            }
        }

        val totalSeconds = x.size.toFloat() / SAMPLE_RATE
        if (first < 0) {
            return AudioAnalysis(x, totalSeconds, 0f, peak, maxAbs, 0f, noiseRms, 0f, false, 1f)
        }

        val speechRms = sqrt(speechEnergy / speechFrames).toFloat()
        val snrDb = 20f * log10(speechRms / noiseRms)
        val silentFraction = (frames - speechFrames).toFloat() / frames
        val snrReliable = silentFraction >= MIN_SILENT_FRACTION

        // 4. Recortar con un poco de aire a cada lado.
        val from = max(0, first * FRAME - (PAD_BEFORE_SECONDS * SAMPLE_RATE).toInt())
        val to = min(x.size, (last + 1) * FRAME + (PAD_AFTER_SECONDS * SAMPLE_RATE).toInt())
        val cut = x.copyOfRange(from, to)

        // 5. Normalizar por el percentil 99 de |x|. Lo poco que quede por
        //    encima se recorta a ±1 (es el golpe, no la voz). La ganancia se
        //    limita para no convertir un susurro lejano en ruido a todo volumen.
        val cutPeak = percentileAbs(cut, PEAK_PERCENTILE)
        val gain = if (cutPeak > 0f) min(MAX_GAIN, TARGET_PEAK / cutPeak) else 1f
        val prepared = FloatArray(cut.size) { (cut[it] * gain).coerceIn(-1f, 1f) }

        return AudioAnalysis(
            prepared = prepared,
            totalSeconds = totalSeconds,
            speechSeconds = speechFrames.toFloat() * FRAME / SAMPLE_RATE,
            peak = peak,
            maxAbs = maxAbs,
            speechRms = speechRms,
            noiseRms = noiseRms,
            snrDb = snrDb,
            snrReliable = snrReliable,
            gain = gain
        )
    }

    /** Null si el audio es apto para reconocer; si no, por qué no. */
    fun gate(a: AudioAnalysis): NotHeardReason? = when {
        a.speechSeconds < MIN_SPEECH_SECONDS -> NotHeardReason.TOO_SHORT
        a.peak < MIN_PEAK || a.speechRms < MIN_SPEECH_RMS -> NotHeardReason.TOO_QUIET
        a.snrReliable && a.snrDb < MIN_SNR_DB -> NotHeardReason.TOO_NOISY
        else -> null
    }

    /** Valor de |x| por debajo del cual queda la fracción [p] de las muestras. */
    private fun percentileAbs(x: FloatArray, p: Float): Float {
        if (x.isEmpty()) return 0f
        val a = FloatArray(x.size) { abs(x[it]) }
        a.sort()
        return a[min(x.size - 1, (x.size * p).toInt())]
    }
}
