package com.ferolabs.hablo

import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Lo que devuelve el reconocedor: o entendió algo, o no se pudo ni intentar. */
sealed class ListenResult {
    data class Heard(val text: String) : ListenResult()
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
    /** Audio listo para el modelo: sin DC, recortado y con el pico en TARGET_PEAK. */
    val prepared: FloatArray,
    val totalSeconds: Float,
    val speechSeconds: Float,
    /** Pico del audio crudo (sin DC), antes de normalizar. */
    val peak: Float,
    val speechRms: Float,
    val noiseRms: Float,
    val snrDb: Float,
    val gain: Float
) {
    /** Con punto decimal siempre (Locale.US), para poder leerlo desde el PC. */
    fun summary(): String =
        "dur=%.2fs voz=%.2fs pico=%.3f rmsVoz=%.4f piso=%.4f snr=%.1fdB ganancia=x%.1f".format(
            Locale.US, totalSeconds, speechSeconds, peak, speechRms, noiseRms, snrDb, gain
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

    /** Puertas de calidad. Se afinan con el corpus real; ver bench.py. */
    const val MIN_SPEECH_SECONDS = 0.20f
    const val MIN_PEAK = 0.010f
    const val MIN_SPEECH_RMS = 0.003f
    const val MIN_SNR_DB = 8f

    private const val TARGET_PEAK = 0.9f
    private const val MAX_GAIN = 30f

    fun analyze(raw: FloatArray): AudioAnalysis {
        if (raw.isEmpty()) {
            return AudioAnalysis(FloatArray(0), 0f, 0f, 0f, 0f, 0f, 0f, 1f)
        }

        // 1. Quitar el offset DC.
        var sum = 0.0
        for (v in raw) sum += v
        val mean = (sum / raw.size).toFloat()
        val x = FloatArray(raw.size) { raw[it] - mean }

        var peak = 0f
        for (v in x) {
            val a = abs(v)
            if (a > peak) peak = a
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
            return AudioAnalysis(x, totalSeconds, 0f, peak, 0f, noiseRms, 0f, 1f)
        }

        val speechRms = sqrt(speechEnergy / speechFrames).toFloat()
        val snrDb = 20f * log10(speechRms / noiseRms)

        // 4. Recortar con un poco de aire a cada lado.
        val from = max(0, first * FRAME - (PAD_BEFORE_SECONDS * SAMPLE_RATE).toInt())
        val to = min(x.size, (last + 1) * FRAME + (PAD_AFTER_SECONDS * SAMPLE_RATE).toInt())
        val cut = x.copyOfRange(from, to)

        // 5. Normalizar el pico. La ganancia se limita para no convertir un
        //    susurro lejano en ruido a todo volumen; eso lo atrapa la puerta.
        var cutPeak = 0f
        for (v in cut) {
            val a = abs(v)
            if (a > cutPeak) cutPeak = a
        }
        val gain = if (cutPeak > 0f) min(MAX_GAIN, TARGET_PEAK / cutPeak) else 1f
        val prepared = FloatArray(cut.size) { (cut[it] * gain).coerceIn(-1f, 1f) }

        return AudioAnalysis(
            prepared = prepared,
            totalSeconds = totalSeconds,
            speechSeconds = speechFrames.toFloat() * FRAME / SAMPLE_RATE,
            peak = peak,
            speechRms = speechRms,
            noiseRms = noiseRms,
            snrDb = snrDb,
            gain = gain
        )
    }

    /** Null si el audio es apto para reconocer; si no, por qué no. */
    fun gate(a: AudioAnalysis): NotHeardReason? = when {
        a.speechSeconds < MIN_SPEECH_SECONDS -> NotHeardReason.TOO_SHORT
        a.peak < MIN_PEAK || a.speechRms < MIN_SPEECH_RMS -> NotHeardReason.TOO_QUIET
        a.snrDb < MIN_SNR_DB -> NotHeardReason.TOO_NOISY
        else -> null
    }
}
