package com.ferolabs.hablo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.min

/**
 * Escucha al usuario y convierte su voz en texto, sin conexión.
 *
 * Los modelos viajan dentro del APK y se copian al almacenamiento interno igual
 * que las voces. Nada de audio sale nunca del teléfono.
 */
class Listener(context: Context) {

    private val app = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()

    private val sampleRate = 16000
    private val maxSeconds = 12
    private val assetsVersion = "v1"

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

    /** Última grabación, para poder reproducírsela al usuario. */
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
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = sampleRate, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    moonshine = OfflineMoonshineModelConfig(
                        preprocessor = File(root, "preprocess.onnx").absolutePath,
                        encoder = File(root, "encode.int8.onnx").absolutePath,
                        uncachedDecoder = File(root, "uncached_decode.int8.onnx").absolutePath,
                        cachedDecoder = File(root, "cached_decode.int8.onnx").absolutePath
                    ),
                    tokens = File(root, "tokens.txt").absolutePath,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                    modelType = "moonshine"
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
     * y luego entrega el texto reconocido en [onResult] (hilo de fondo).
     */
    fun startRecording(onResult: (String) -> Unit) {
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

            val audio = captured
            if (audio == null || audio.isEmpty()) {
                onResult("")
                return@execute
            }
            lastRecording = audio

            thinking = true
            try {
                val r = ensureRecognizer()
                if (r == null) {
                    onResult("")
                    return@execute
                }
                val stream = r.createStream()
                stream.acceptWaveform(audio, sampleRate)
                r.decode(stream)
                val text = r.getResult(stream).text
                stream.release()
                Log.i(TAG, "Reconocido: '$text'")
                onResult(text)
            } catch (e: Throwable) {
                Log.e(TAG, "Error reconociendo", e)
                errorDetail = "Falló el reconocimiento: ${e.javaClass.simpleName} ${e.message}"
                onResult("")
            } finally {
                thinking = false
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

        val collected = ArrayList<Float>(sampleRate * maxSeconds)
        val chunk = ShortArray(bufferSize / 2)

        try {
            rec.startRecording()
            val limit = sampleRate * maxSeconds
            while (!shouldStop && collected.size < limit) {
                val n = rec.read(chunk, 0, chunk.size)
                if (n <= 0) continue
                var peak = 0f
                for (i in 0 until n) {
                    val v = chunk[i] / 32768f
                    collected.add(v)
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
        }

        return FloatArray(collected.size) { collected[it] }
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
    }
}
