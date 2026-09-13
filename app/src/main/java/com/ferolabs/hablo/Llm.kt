package com.ferolabs.hablo

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.concurrent.Executors

/**
 * Motor de IA de la Fase 3: llama.cpp compilado dentro de la app (cpp/) con
 * Qwen3 8B en formato GGUF. El modelo (~5 GB) no cabe en el APK: vive al lado
 * de la app, en su carpeta privada del almacenamiento externo
 * (Android/data/com.ferolabs.hablo/files/modelos/), que no pide permisos y se
 * llena una vez por USB con adb push (regla dura 3: nada se descarga en
 * tiempo de ejecución). Ojo: desinstalar la app borra esa carpeta.
 *
 * Pesado (~5,3 GB cargado): nunca convive con el modelo de fonemas ni con
 * Moonshine cargados; se carga al entrar a la conversación y se suelta al
 * salir. Todo corre en un solo hilo de fondo.
 */
class Llm(context: Context) {

    private val app = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()

    var loaded by mutableStateOf(false)
        private set

    var busy by mutableStateOf(false)
        private set

    var status by mutableStateOf("")
        private set

    /** Dónde tiene que estar el modelo. Se muestra tal cual en Ajustes. */
    val modelFile: File
        get() = File(app.getExternalFilesDir("modelos"), MODEL_NAME)

    fun modelPresent(): Boolean = modelFile.exists() && modelFile.length() > 1_000_000_000L

    fun load(onDone: (Boolean) -> Unit = {}) {
        if (loaded || busy) return
        busy = true
        status = "Cargando el modelo…"
        worker.execute {
            val t0 = System.currentTimeMillis()
            val ok = try {
                System.loadLibrary("hablo-llm")
                val threads = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 6)
                nativeLoad(modelFile.absolutePath, N_CTX, threads) == 0
            } catch (e: Throwable) {
                Log.e(TAG, "No cargó el modelo", e)
                status = "No cargó: ${e.javaClass.simpleName} ${e.message}"
                false
            }
            val ms = System.currentTimeMillis() - t0
            loaded = ok
            if (ok) status = "Modelo listo en ${ms / 1000.0} s"
            busy = false
            onDone(ok)
        }
    }

    /**
     * Genera la respuesta a una conversación (lista de (rol, contenido)) y va
     * entregando trozos de texto en [onToken] desde el hilo de fondo. Al final,
     * [onDone] recibe las estadísticas.
     */
    fun chat(
        messages: List<Pair<String, String>>,
        onToken: (String) -> Unit,
        onDone: (Stats) -> Unit
    ) {
        if (!loaded || busy) return
        busy = true
        worker.execute {
            try {
                val prompt = nativeApplyTemplate(
                    messages.map { it.first }.toTypedArray(),
                    messages.map { it.second }.toTypedArray()
                )
                val t0 = System.currentTimeMillis()
                val nPrompt = nativeStart(prompt)
                if (nPrompt < 0) {
                    status = "No se pudo procesar el prompt ($nPrompt)"
                    onDone(Stats(0, 0, 0, 0))
                    return@execute
                }
                val t1 = System.currentTimeMillis()
                var nGen = 0
                while (nGen < MAX_NEW_TOKENS) {
                    val piece = nativeNext() ?: break
                    nGen++
                    if (piece.isNotEmpty()) onToken(piece)
                }
                val t2 = System.currentTimeMillis()
                val stats = Stats(nPrompt, t1 - t0, nGen, t2 - t1)
                status = stats.summary()
                Log.i(TAG, status)
                onDone(stats)
            } catch (e: Throwable) {
                Log.e(TAG, "Falló la generación", e)
                status = "Falló: ${e.javaClass.simpleName} ${e.message}"
                onDone(Stats(0, 0, 0, 0))
            } finally {
                busy = false
            }
        }
    }

    fun stop() = nativeStop()

    fun release() {
        worker.execute {
            if (loaded) {
                nativeFree()
                loaded = false
                status = ""
            }
        }
    }

    data class Stats(val promptTokens: Int, val promptMs: Long, val genTokens: Int, val genMs: Long) {
        fun summary(): String {
            val pp = if (promptMs > 0) promptTokens * 1000.0 / promptMs else 0.0
            val tg = if (genMs > 0) genTokens * 1000.0 / genMs else 0.0
            return "prompt: $promptTokens tokens en ${promptMs} ms (${"%.1f".format(pp)} t/s) · " +
                "respuesta: $genTokens tokens en ${genMs} ms (${"%.1f".format(tg)} t/s)"
        }
    }

    private external fun nativeLoad(path: String, nCtx: Int, nThreads: Int): Int
    private external fun nativeApplyTemplate(roles: Array<String>, contents: Array<String>): String
    private external fun nativeStart(prompt: String): Int
    private external fun nativeNext(): String?
    private external fun nativeStop()
    private external fun nativeFree()

    companion object {
        private const val TAG = "HabloLlm"
        const val MODEL_NAME = "Qwen3-8B-Q4_K_M.gguf"
        private const val N_CTX = 2048
        private const val MAX_NEW_TOKENS = 256
    }
}
