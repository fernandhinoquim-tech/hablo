package com.ferolabs.hablo

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * La profesora respondida por **Claude API** (de pago, la cuenta es de Fero).
 * REST crudo con `HttpURLConnection` + `org.json`, igual que [CloudLlm]: no se
 * añade el SDK de Java para no tocar las versiones fijadas de Gradle/AGP
 * (regla dura 2) ni sumar 3 MB de dependencias a un APK que ya lleva tres
 * motores nativos.
 *
 * Medido en el PC el 2026-09-13 con las frases reales del dictado de Fero
 * ('As model please', 'thoughts with Buddha', 'What nine is it', 'I have 25
 * years', 'My sister is doctor, she work'), 5 turnos seguidos:
 * - **claude-sonnet-5**: 5/5. No corrige lo mal oído, corrige la gramática y,
 *   además, DICE la frase correcta en voz alta ("You can say: …"), que es lo
 *   que Fero quiere oír. Primera palabra en 0,8-1,0 s; turno completo 1,5-2,1 s.
 *   $0,0023 por turno ≈ **$2,06 al mes** a 30 turnos diarios.
 * - **claude-haiku-4-5**: acierta lo mismo, pero se salta el "You can say" y
 *   empuja la escena en vez de enseñar. Primera palabra 0,7-1,6 s.
 *   $0,0008 por turno ≈ **$0,75 al mes**.
 * Por eso el valor por defecto es Sonnet y Haiku queda para ahorrar.
 *
 * La clave vive en files/modelos/claude.key (se copia por USB), nunca en el
 * código ni en el repositorio. Sale solo texto; el audio nunca.
 */
class ClaudeLlm(context: Context) : ChatEngine {

    private val app = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()

    /** Modelo elegido en Ajustes. Ver [MODELOS]. */
    var model: String = DEFAULT_MODEL

    override val label: String get() = nombreDe(model)

    override var busy by mutableStateOf(false)
        private set

    override var status by mutableStateOf("")
        private set

    @Volatile
    private var cancelled = false

    val keyFile: File
        get() = File(app.getExternalFilesDir("modelos"), KEY_FILE)

    fun keyPresent(): Boolean = readKey() != null

    override fun ready() = keyPresent()
    override fun usable() = keyPresent()

    private fun readKey(): String? = try {
        keyFile.readText().trim().takeIf { it.startsWith("sk-ant-") }
    } catch (e: Throwable) {
        null
    }

    override fun stop() {
        cancelled = true
    }

    override fun chat(
        system: String,
        messages: List<Pair<String, String>>,
        onToken: (String) -> Unit,
        onDone: (String?) -> Unit
    ) {
        if (busy) return
        busy = true
        cancelled = false
        worker.execute {
            val key = readKey()
            if (key == null) {
                busy = false
                onDone("No hay clave de Claude en ${keyFile.absolutePath}")
                return@execute
            }
            val t0 = System.currentTimeMillis()
            var chars = 0
            var firstMs = -1L
            try {
                val error = stream(key, requestBody(system, messages)) { piece ->
                    if (firstMs < 0) firstMs = System.currentTimeMillis() - t0
                    chars += piece.length
                    onToken(piece)
                }
                val total = System.currentTimeMillis() - t0
                if (error == null) {
                    status = "${nombreDe(model)}: primera palabra en $firstMs ms, $chars letras en $total ms"
                    Log.i(TAG, status)
                    onDone(null)
                } else {
                    status = "${nombreDe(model)}: $error"
                    Log.e(TAG, status)
                    onDone(error)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Falló Claude", e)
                status = "${e.javaClass.simpleName}: ${e.message}"
                onDone("Sin conexión (${e.javaClass.simpleName})")
            } finally {
                busy = false
            }
        }
    }

    private fun requestBody(system: String, messages: List<Pair<String, String>>): JSONObject {
        val msgs = JSONArray()
        for ((role, text) in messages) {
            msgs.put(
                JSONObject()
                    .put("role", if (role == "assistant") "assistant" else "user")
                    .put("content", text)
            )
        }
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", MAX_TOKENS)
            .put("stream", true)
            // El prompt de sistema como bloque para poder marcarlo como caché:
            // hoy es corto y no llega al mínimo, pero cuando lleve la memoria
            // del alumno sí, y entonces cada turno cuesta la décima parte.
            .put(
                "system", JSONArray().put(
                    JSONObject()
                        .put("type", "text")
                        .put("text", system)
                        .put("cache_control", JSONObject().put("type", "ephemeral"))
                )
            )
            .put("messages", msgs)
        // Sin razonar: en una charla corta pesa más el segundo de espera.
        // Haiku 4.5 no lleva razonamiento salvo que se pida; Sonnet 5 sí, y se apaga.
        if (model.startsWith("claude-sonnet") || model.startsWith("claude-opus")) {
            body.put("thinking", JSONObject().put("type", "disabled"))
        }
        return body
    }

    /** Llama a /v1/messages con stream y va entregando el texto. Devuelve null si respondió. */
    private fun stream(key: String, body: JSONObject, onPiece: (String) -> Unit): String? {
        val conn = (URL(URL_MESSAGES).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("content-type", "application/json; charset=utf-8")
            setRequestProperty("x-api-key", key)
            setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
        }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        if (code != 200) {
            val err = try { conn.errorStream?.bufferedReader()?.readText() ?: "" } catch (e: Throwable) { "" }
            conn.disconnect()
            return explicar(code, err)
        }
        var stopReason: String? = null
        conn.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                if (cancelled) break
                if (!line.startsWith("data:")) continue
                val json = line.removePrefix("data:").trim()
                if (json.isEmpty()) continue
                val obj = JSONObject(json)
                when (obj.optString("type")) {
                    "content_block_delta" -> {
                        val delta = obj.optJSONObject("delta") ?: continue
                        // "thinking_delta" también llega aquí si algún día se enciende: solo texto.
                        if (delta.optString("type") == "text_delta") {
                            val text = delta.optString("text", "")
                            if (text.isNotEmpty()) onPiece(text)
                        }
                    }
                    "message_delta" -> {
                        obj.optJSONObject("delta")?.optString("stop_reason")?.takeIf { it.isNotEmpty() }
                            ?.let { stopReason = it }
                    }
                    "error" -> {
                        val msg = obj.optJSONObject("error")?.optString("message") ?: "error a mitad de la respuesta"
                        conn.disconnect()
                        // salida no local: useLines es inline
                        return msg.take(160)
                    }
                }
            }
        }
        conn.disconnect()
        // "refusal" y "max_tokens" no son fallos de red: se avisan en el log y la
        // pantalla se queda con lo que haya llegado.
        if (stopReason != null && stopReason != "end_turn" && stopReason != "stop_sequence") {
            Log.w(TAG, "$model terminó por $stopReason")
        }
        return null
    }

    /** Traduce el error de la API a algo que Fero pueda leer. */
    private fun explicar(code: Int, body: String): String {
        val detalle = try {
            JSONObject(body).getJSONObject("error").getString("message").take(160)
        } catch (e: Throwable) {
            body.take(160)
        }
        return when (code) {
            401 -> "La clave no sirve o fue revocada ($detalle)"
            400 -> "Petición rechazada ($detalle)"
            403 -> "La cuenta no tiene permiso ($detalle)"
            404 -> "Ese modelo no existe para esta cuenta ($detalle)"
            413 -> "La conversación es demasiado larga ($detalle)"
            429 -> "Demasiadas peticiones seguidas; espera unos segundos ($detalle)"
            in 500..599 -> "Anthropic está fallando ahora mismo ($detalle)"
            else -> "HTTP $code: $detalle"
        }
    }

    /** Prueba la clave con una petición mínima. Devuelve el texto para Ajustes. */
    fun testConnection(onDone: (String) -> Unit) {
        worker.execute {
            val key = readKey()
            if (key == null) {
                onDone("No hay clave en ${keyFile.absolutePath}")
                return@execute
            }
            try {
                val t0 = System.currentTimeMillis()
                val conn = (URL(URL_MESSAGES).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    doOutput = true
                    setRequestProperty("content-type", "application/json; charset=utf-8")
                    setRequestProperty("x-api-key", key)
                    setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
                }
                val body = JSONObject()
                    .put("model", model)
                    .put("max_tokens", 16)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "Say OK")))
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code != 200) {
                    val err = try { conn.errorStream?.bufferedReader()?.readText() ?: "" } catch (e: Throwable) { "" }
                    onDone(explicar(code, err))
                    return@execute
                }
                conn.inputStream.bufferedReader().readText()
                onDone("Clave válida. ${nombreDe(model)} respondió en ${System.currentTimeMillis() - t0} ms.")
            } catch (e: Throwable) {
                onDone("Sin conexión: ${e.javaClass.simpleName} ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "HabloClaude"
        const val KEY_FILE = "claude.key"
        const val DEFAULT_MODEL = "claude-sonnet-5"
        private const val URL_MESSAGES = "https://api.anthropic.com/v1/messages"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val MAX_TOKENS = 400

        /** id → (nombre para la pantalla, cuánto cuesta al mes a 30 turnos diarios). */
        val MODELOS = listOf(
            "claude-sonnet-5" to ("Claude Sonnet 5" to "mejor profesora · ~2 USD al mes"),
            "claude-haiku-4-5" to ("Claude Haiku 4.5" to "más barato · ~0,75 USD al mes")
        )

        fun nombreDe(id: String): String = MODELOS.firstOrNull { it.first == id }?.second?.first ?: id
    }
}
