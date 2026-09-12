package com.ferolabs.hablo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.min

/**
 * Escucha al usuario y convierte su voz en texto, sin conexión.
 *
 * Los modelos viajan dentro del APK y se copian al almacenamiento interno igual
 * que las voces. Nada de audio sale nunca del teléfono.
 *
 * Antes de reconocer, el audio pasa por [AudioPrep]: se recorta el silencio,
 * se normaliza el volumen y se revisa que haya algo que valga la pena
 * escuchar. El reconocedor nunca sabe qué frase se esperaba.
 */
class Listener(context: Context) {

    private val app = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()

    private val sampleRate = AudioPrep.SAMPLE_RATE
    private val maxSeconds = 12
    private val assetsVersion = "v2"

    var recording by mutableStateOf(false)
        private set

    /** 0f a 1f, para pintar la barra de nivel mientras hablas. */
    var level by mutableStateOf(0f)
        private set

    var thinking by mutableStateOf(false)
        private set

    var errorDetail by mutableStateOf<String?>(null)
        private set

    private var recognizer: OfflineRecognizer? = null
    private var recorder: AudioRecord? = null

    @Volatile
    private var shouldStop = false

    /** Última grabación ya recortada y normalizada: lo mismo que oyó el modelo. */
    var lastRecording: FloatArray? = null
        private set

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    // ------------------------------------------------------------------------

    private fun modelsRoot(): File = File(app.filesDir, "asr-$assetsVersion")

    private fun installAssets() {
        val root = modelsRoot()
        if (File(root, ".listo").exists()) return
        // Versiones anteriores del reconocedor ya no sirven: se borran para no
        // dejar cientos de MB muertos en el teléfono.
        app.filesDir.listFiles { f -> f.isDirectory && f.name.startsWith("asr-") && f != root }
            ?.forEach { it.deleteRecursively() }
        root.deleteRecursively()
        root.mkdirs()
        val names = app.assets.list("asr") ?: emptyArray()
        if (names.isEmpty()) throw IllegalStateException("el APK no trae assets/asr")
        for (name in names) {
            app.assets.open("asr/$name").use { input ->
                File(root, name).outputStream().use { output -> input.copyTo(output) }
            }
        }
        File(root, ".listo").writeText(assetsVersion)
        Log.i(TAG, "Reconocedor instalado: ${names.size} archivos")
    }

    private fun ensureRecognizer(): OfflineRecognizer? {
        recognizer?.let { return it }
        return try {
            installAssets()
            val root = modelsRoot()
            // Moonshine base v2: encoder + decoder fusionado (.ort). sherpa-onnx
            // detecta la variante por los campos que vienen llenos; el
            // modelType se deja vacío igual que hace su propio binding de Python.
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = sampleRate, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    moonshine = OfflineMoonshineModelConfig(
                        encoder = File(root, "encoder_model.ort").absolutePath,
                        mergedDecoder = File(root, "decoder_model_merged.ort").absolutePath
                    ),
                    tokens = File(root, "tokens.txt").absolutePath,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                )
            )
            val r = OfflineRecognizer(config = config)
            recognizer = r
            Log.i(TAG, "Reconocedor listo")
            r
        } catch (e: Throwable) {
            Log.e(TAG, "No arrancó el reconocedor", e)
            errorDetail = "El reconocedor de voz no arrancó: ${e.javaClass.simpleName} ${e.message}"
            null
        }
    }

    // ------------------------------------------------------------------------

    /**
     * Graba hasta que se llame a [stopRecording] o se cumplan 12 segundos,
     * y luego entrega el resultado en [onResult] (hilo de fondo).
     *
     * [target] es solo para el registro de diagnóstico: el reconocedor no lo
     * ve. Sesgarlo hacia la frase esperada haría mentir al puntaje.
     */
    fun startRecording(target: String, onResult: (ListenResult) -> Unit) {
        if (recording) return
        if (!hasMicPermission()) {
            errorDetail = "Falta el permiso del micrófono."
            return
        }
        shouldStop = false
        errorDetail = null
        recording = true

        worker.execute {
            var captured: FloatArray? = null
            try {
                captured = capture()
            } catch (e: Throwable) {
                Log.e(TAG, "Error grabando", e)
                errorDetail = "No se pudo grabar: ${e.javaClass.simpleName} ${e.message}"
            } finally {
                recording = false
                level = 0f
            }

            val raw = captured
            if (raw == null || raw.isEmpty()) {
                onResult(ListenResult.NotHeard(NotHeardReason.TOO_SHORT))
                return@execute
            }

            thinking = true
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            var analysis: AudioAnalysis? = null
            var outcome = "?"
            try {
                saveRawWav(stamp, raw)

                val a = AudioPrep.analyze(raw)
                analysis = a
                if (a.prepared.isNotEmpty()) lastRecording = a.prepared

                val reason = AudioPrep.gate(a)
                if (reason != null) {
                    outcome = "NO_OIDO $reason"
                    Log.i(TAG, "audio: ${a.summary()} -> $reason")
                    onResult(ListenResult.NotHeard(reason))
                    return@execute
                }

                val r = ensureRecognizer()
                if (r == null) {
                    outcome = "sin reconocedor"
                    onResult(ListenResult.NotHeard(NotHeardReason.NOTHING))
                    return@execute
                }
                val t0 = System.currentTimeMillis()
                val stream = r.createStream()
                stream.acceptWaveform(a.prepared, sampleRate)
                r.decode(stream)
                val text = r.getResult(stream).text.trim()
                stream.release()
                val ms = System.currentTimeMillis() - t0
                Log.i(TAG, "audio: ${a.summary()} | reconocido en ${ms}ms: '$text'")

                if (text.isBlank()) {
                    outcome = "NO_OIDO NOTHING"
                    onResult(ListenResult.NotHeard(NotHeardReason.NOTHING))
                } else {
                    outcome = "OIDO $text"
                    onResult(ListenResult.Heard(text))
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error reconociendo", e)
                errorDetail = "Falló el reconocimiento: ${e.javaClass.simpleName} ${e.message}"
                outcome = "ERROR ${e.javaClass.simpleName}"
                onResult(ListenResult.NotHeard(NotHeardReason.NOTHING))
            } finally {
                thinking = false
                saveSidecar(stamp, target, analysis, outcome)
            }
        }
    }

    fun stopRecording() {
        shouldStop = true
    }

    private fun capture(): FloatArray {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBuffer, sampleRate / 2)

        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )
        recorder = rec

        // Supresión de ruido y control de ganancia del propio Android, si el
        // teléfono los ofrece. En algunos equipos existen pero no hacen nada;
        // por eso se registra en el log lo que quedó activo de verdad.
        val ns = attachEffect("NoiseSuppressor", NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(rec.audioSessionId)
        }
        val agc = attachEffect("AutomaticGainControl", AutomaticGainControl.isAvailable()) {
            AutomaticGainControl.create(rec.audioSessionId)
        }

        val limit = sampleRate * maxSeconds
        val samples = FloatArray(limit)
        var count = 0
        val chunk = ShortArray(bufferSize / 2)

        try {
            rec.startRecording()
            while (!shouldStop && count < limit) {
                val n = rec.read(chunk, 0, min(chunk.size, limit - count))
                if (n <= 0) continue
                var peak = 0f
                for (i in 0 until n) {
                    val v = chunk[i] / 32768f
                    samples[count++] = v
                    val a = abs(v)
                    if (a > peak) peak = a
                }
                level = min(1f, peak * 3f)
            }
        } finally {
            try {
                rec.stop()
            } catch (e: Throwable) {
                // sin acción
            }
            rec.release()
            recorder = null
            ns?.release()
            agc?.release()
        }

        return samples.copyOf(count)
    }

    private fun <T : AudioEffect> attachEffect(
        name: String,
        available: Boolean,
        create: () -> T?
    ): T? {
        if (!available) {
            Log.i(TAG, "$name: no disponible en este teléfono")
            return null
        }
        return try {
            val effect = create()
            if (effect == null) {
                Log.i(TAG, "$name: create() devolvió null")
                null
            } else {
                effect.setEnabled(true)
                Log.i(TAG, "$name: activo=${effect.enabled}")
                effect
            }
        } catch (e: Throwable) {
            Log.w(TAG, "$name: falló", e)
            null
        }
    }

    // ------------------------------------------------------------------------
    // Corpus de diagnóstico. Cada grabación cruda se guarda como WAV junto con
    // un .txt con la frase, lo reconocido y las medidas. Vive en la carpeta
    // privada de la app (Android/data/com.ferolabs.hablo/files/grabaciones),
    // se lee por USB con adb y sirve para probar modelos y afinar umbrales en
    // el PC sin recompilar. Se guardan solo las últimas KEEP_RECORDINGS.
    // ------------------------------------------------------------------------

    private fun recordingsDir(): File? = try {
        app.getExternalFilesDir("grabaciones")
    } catch (e: Throwable) {
        null
    }

    private fun saveRawWav(stamp: String, raw: FloatArray) {
        val dir = recordingsDir() ?: return
        try {
            dir.mkdirs()
            val pcmBytes = raw.size * 2
            val buf = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
            buf.put("RIFF".toByteArray(Charsets.US_ASCII))
            buf.putInt(36 + pcmBytes)
            buf.put("WAVE".toByteArray(Charsets.US_ASCII))
            buf.put("fmt ".toByteArray(Charsets.US_ASCII))
            buf.putInt(16)
            buf.putShort(1)                       // PCM
            buf.putShort(1)                       // mono
            buf.putInt(sampleRate)
            buf.putInt(sampleRate * 2)            // bytes por segundo
            buf.putShort(2)                       // bytes por muestra
            buf.putShort(16)                      // bits
            buf.put("data".toByteArray(Charsets.US_ASCII))
            buf.putInt(pcmBytes)
            for (v in raw) {
                buf.putShort((v.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            }
            File(dir, "$stamp.wav").writeBytes(buf.array())
            pruneRecordings(dir)
        } catch (e: Throwable) {
            Log.w(TAG, "No se pudo guardar la grabación de diagnóstico", e)
        }
    }

    private fun saveSidecar(stamp: String, target: String, a: AudioAnalysis?, outcome: String) {
        val dir = recordingsDir() ?: return
        try {
            val text = buildString {
                appendLine("frase: $target")
                appendLine("resultado: $outcome")
                appendLine("medidas: ${a?.summary() ?: "-"}")
            }
            File(dir, "$stamp.txt").writeText(text)
        } catch (e: Throwable) {
            // sin acción: es solo diagnóstico
        }
    }

    private fun pruneRecordings(dir: File) {
        val wavs = dir.listFiles { f -> f.name.endsWith(".wav") }?.sortedBy { it.name } ?: return
        val extra = wavs.size - KEEP_RECORDINGS
        if (extra <= 0) return
        for (f in wavs.take(extra)) {
            f.delete()
            File(dir, f.name.removeSuffix(".wav") + ".txt").delete()
        }
    }

    fun release() {
        shouldStop = true
        worker.execute {
            try {
                recognizer?.release()
            } catch (e: Throwable) {
                // sin acción
            }
            recognizer = null
        }
        worker.shutdown()
    }

    companion object {
        private const val TAG = "HabloListener"
        private const val KEEP_RECORDINGS = 40
    }
}
