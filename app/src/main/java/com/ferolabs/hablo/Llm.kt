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

    /**
     * Trabajos en la cola del hilo del modelo. Antes, un pedido que llegaba con el modelo
     * ocupado (p. ej. el primer turno mientras procesaba el prompt de sistema, o al pasar a
     * la IA del teléfono a mitad de charla) se descartaba en silencio y la burbuja quedaba en
     * "…" para siempre (08-10). Ahora se encola: el hilo es uno solo y va en orden.
     */
    private val pendientes = java.util.concurrent.atomic.AtomicInteger(0)
    private fun encolar(trabajo: () -> Unit) {
        pendientes.incrementAndGet(); busy = true
        worker.execute {
            try { trabajo() } finally { busy = pendientes.decrementAndGet() > 0 }
        }
    }

    /** Turnos viejos que ya no caben en la memoria del modelo (se quitan los primeros, nunca el sistema). */
    private var recorte = 0

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
     * Texto ya procesado por el modelo en esta conversación (con la plantilla
     * aplicada). La memoria del modelo (KV cache) contiene exactamente estos
     * tokens más lo que él mismo generó; cada turno solo se procesa lo que
     * se agrega al final. Así un turno cuesta 10-20 tokens, no 400-600.
     */
    private var formattedSoFar = ""

    private fun template(messages: List<Pair<String, String>>, addAssistant: Boolean): String =
        nativeApplyTemplate(
            messages.map { it.first }.toTypedArray(),
            messages.map { it.second }.toTypedArray(),
            addAssistant
        )

    /** Empieza una conversación: borra la memoria y procesa el prompt de sistema + apertura, antes de que el alumno hable. */
    fun startConversation(messages: List<Pair<String, String>>, onDone: (Boolean) -> Unit = {}) {
        if (!loaded) { onDone(false); return }
        encolar {
            var ok = false
            try {
                nativeReset()
                formattedSoFar = ""
                recorte = 0
                val formatted = template(messages, false)
                val t0 = System.currentTimeMillis()
                val n = nativeFeed(formatted)
                // Si no cupo, la memoria queda vacía: el primer turno lo reprocesa (y recorta) todo.
                if (n >= 0) { formattedSoFar = formatted; ok = true }
                status = if (ok) "listo: $n tokens de contexto en ${System.currentTimeMillis() - t0} ms" else "el inicio no cupo ($n)"
                Log.i(TAG, status)
            } catch (e: Throwable) {
                Log.e(TAG, "No se pudo preparar la conversación", e)
            } finally {
                onDone(ok)
            }
        }
    }

    /**
     * Genera la respuesta al último mensaje del alumno. [messages] es la
     * conversación completa (sistema incluido); solo se procesa lo nuevo. Va
     * entregando trozos en [onToken] (hilo de fondo); [onDone] recibe las
     * estadísticas. Si el modelo responde vacío, se reintenta hasta dos veces.
     */
    fun chat(
        messages: List<Pair<String, String>>,
        onToken: (String) -> Unit,
        onDone: (Stats) -> Unit
    ) {
        if (!loaded) { status = "La IA del teléfono no está cargada"; onDone(Stats(0, 0, 0, 0)); return }
        encolar {
            try {
                // El sistema siempre; de la charla, lo que quepa (sin los [recorte] turnos más viejos).
                fun ventana() = messages.take(1) + messages.drop(1).drop(recorte)
                var formatted = template(ventana(), true)
                // Lo nuevo = lo que la plantilla agrega después de lo ya procesado.
                // Si no es un prefijo (p. ej. se recortó el historial), se reprocesa todo.
                val delta = if (formatted.startsWith(formattedSoFar)) formatted.substring(formattedSoFar.length) else {
                    nativeReset(); formatted
                }
                val t0 = System.currentTimeMillis()
                var nPrompt = nativeFeed(delta)
                // No cabe: se quitan los dos turnos más viejos y se reprocesa, hasta que quepa.
                // Antes se reprocesaba igual de largo, fallaba otra vez y la profesora se callaba;
                // y el turno siguiente salía sin el prompt de sistema (08-10).
                while (nPrompt < 0 && messages.size - 1 - recorte > 2) {
                    recorte += 2
                    formatted = template(ventana(), true)
                    nativeReset()
                    nPrompt = nativeFeed(formatted)
                    Log.w(TAG, "memoria llena: se quitan los turnos más viejos (recorte $recorte)")
                }
                if (nPrompt < 0) {
                    nativeReset(); formattedSoFar = ""
                    status = "No cupo en la memoria de la IA del teléfono ($nPrompt)"
                    onDone(Stats(0, 0, 0, 0))
                    return@encolar
                }
                val t1 = System.currentTimeMillis()
                var nGen = 0
                var visible = StringBuilder()
                var attempt = 0
                while (true) {
                    nativeBeginAnswer()
                    while (nGen < MAX_NEW_TOKENS) {
                        val piece = nativeNext() ?: break
                        nGen++
                        if (piece.isNotEmpty()) {
                            visible.append(piece)
                            onToken(piece)
                        }
                    }
                    // "Vacía" = sin una sola letra o número. Un modelo que solo
                    // suelta "\n\n" o un signo se queda callado; y si eso entra al
                    // historial, aprende a callarse en cada turno (bucle degenerado).
                    if (visible.any { it.isLetterOrDigit() } || attempt >= 2) break
                    attempt++
                    Log.w(TAG, "respuesta vacía ('${escape(visible)}'); reintento $attempt")
                    nativeDiscardAnswer()
                    nGen = 0
                    visible = StringBuilder()
                }
                if (!visible.any { it.isLetterOrDigit() }) {
                    // Tres intentos callado: se descarta y se le pone en la boca una
                    // frase de recuperación, que también queda en su memoria.
                    nativeDiscardAnswer()
                    nativeFeed(FALLBACK_REPLY)
                    visible = StringBuilder(FALLBACK_REPLY)
                    onToken(FALLBACK_REPLY)
                    Log.w(TAG, "respuesta de recuperación")
                }
                val t2 = System.currentTimeMillis()
                // lo generado ya está en la memoria del modelo: el historial que
                // manda la pantalla debe coincidir con el texto visible
                formattedSoFar = formatted + visible.toString()
                val stats = Stats(nPrompt, t1 - t0, nGen, t2 - t1)
                status = stats.summary()
                Log.i(TAG, "$status | '${escape(visible.take(160))}'")
                onDone(stats)
            } catch (e: Throwable) {
                Log.e(TAG, "Falló la generación", e)
                status = "Falló: ${e.javaClass.simpleName} ${e.message}"
                onDone(Stats(0, 0, 0, 0))
            }
        }
    }

    /** Corta la generación en curso. Sin modelo cargado no hay librería nativa: no tocar. */
    fun stop() {
        if (loaded) nativeStop()
    }

    fun release() {
        worker.execute {
            if (loaded) {
                nativeFree()
                loaded = false
                status = ""
                formattedSoFar = ""
            }
        }
    }

    private fun escape(s: CharSequence): String = s.toString().replace("\n", "⏎")

    data class Stats(val promptTokens: Int, val promptMs: Long, val genTokens: Int, val genMs: Long) {
        fun summary(): String {
            val pp = if (promptMs > 0) promptTokens * 1000.0 / promptMs else 0.0
            val tg = if (genMs > 0) genTokens * 1000.0 / genMs else 0.0
            return "prompt: $promptTokens tokens en ${promptMs} ms (${"%.1f".format(pp)} t/s) · " +
                "respuesta: $genTokens tokens en ${genMs} ms (${"%.1f".format(tg)} t/s)"
        }
    }

    private external fun nativeLoad(path: String, nCtx: Int, nThreads: Int): Int
    private external fun nativeApplyTemplate(roles: Array<String>, contents: Array<String>, addAssistant: Boolean): String
    private external fun nativeReset()
    private external fun nativeFeed(text: String): Int
    private external fun nativeBeginAnswer()
    private external fun nativeDiscardAnswer()
    private external fun nativeNext(): String?
    private external fun nativeStop()
    private external fun nativeFree()

    companion object {
        private const val TAG = "HabloLlm"
        const val MODEL_NAME = "Qwen3-8B-Q4_K_M.gguf"
        /**
         * 4096 desde el 08-10: el prompt de sistema creció a ~1.000-1.300 tokens (más ~400 de
         * memoria en la charla libre) y con 2048 la memoria se llenaba a los 5-9 turnos.
         * KV de Qwen3 8B ≈ 147 KB por token: ~600 MB (antes ~300).
         */
        private const val N_CTX = 4096
        private const val MAX_NEW_TOKENS = 256
        private const val FALLBACK_REPLY = "Sorry, I didn't catch that. Could you say it again, please?"
    }
}
