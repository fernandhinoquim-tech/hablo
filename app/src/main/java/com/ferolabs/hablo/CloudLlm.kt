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
 * Conversación por internet con el API de Gemini (Google AI Studio). Es la
 * ÚNICA pieza de Hablo que usa la red, y solo cuando el interruptor de
 * Ajustes está encendido y existe la clave. Lo que sale del teléfono: el
 * prompt del escenario y el TEXTO de la charla (lo que el alumno dijo, ya
 * transcrito aquí por Parakeet, y lo que respondió el modelo). Nunca audio;
 * la voz de la profesora sigue siendo la de Piper, local.
 *
 * Decisión de Fero (2026-09-12): el 8B local no distingue "el dictado se
 * equivocó" de "el alumno se equivocó" y corregía errores que no existían.
 * Medido en el PC con las frases reales de su sesión ('As model please',
 * 'thoughts with Buddha', 'What nine is it', 'I have 25 years', 'My sister
 * is doctor, she work'): gemini-3.8-flash acertó 5/5 (no corrige lo mal
 * oído, sí corrige la gramática) en 1-2,5 s; gemini-3.5-flash-lite 6/7 en
 * 0,6-1,1 s; gemma-4-31b piensa en voz alta 17-30 s (descartado).
 *
 * Nivel gratuito (leído en aistudio.google.com/rate-limit el 2026-09-12):
 * cada Flash 3.x tiene 5 peticiones/minuto y **20 al día**; Flash-Lite
 * 15/minuto y 500 al día. Por eso hay una ESCALERA de modelos: cuando uno
 * devuelve 429 (cuota), 404 o 503 (saturado), se pasa al siguiente en esta
 * misma conversación. Cinco Flash × 20 = 100 turnos/día con la mejor
 * calidad, y después Lite. Google puede usar el texto del nivel gratuito
 * para mejorar sus productos (en el nivel pago, no).
 *
 * La clave vive en files/modelos/gemini.key (se copia por USB), nunca en el
 * código ni en el repositorio.
 */
class CloudLlm(context: Context) : ChatEngine {

    private val app = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()

    override var busy by mutableStateOf(false)
        private set

    override var status by mutableStateOf("")
        private set

    override val label: String get() = "$model · gratis"

    override fun ready() = keyPresent()
    override fun usable() = keyPresent()

    /** Peldaño actual de la escalera; se reinicia con cada conversación. */
    private var rung by mutableStateOf(0)

    val model: String get() = LADDER[rung.coerceIn(0, LADDER.size - 1)]

    val keyFile: File
        get() = File(app.getExternalFilesDir("modelos"), KEY_FILE)

    fun keyPresent(): Boolean = readKey() != null

    private fun readKey(): String? = try {
        keyFile.readText().trim().takeIf { it.length > 20 }
    } catch (e: Throwable) {
        null
    }

    override fun start(system: String, history: List<Pair<String, String>>, onReady: (Boolean) -> Unit) {
        rung = 0
        onReady(true)
    }

    /**
     * Responde al último mensaje. [messages] son pares (rol, texto) con roles
     * "user" / "assistant"; el system prompt va aparte. Los trozos llegan en
     * [onToken] (hilo de fondo); [onDone] recibe null si todo salió bien o el
     * mensaje de error para mostrar.
     */
    override fun chat(
        system: String,
        messages: List<Pair<String, String>>,
        onToken: (String) -> Unit,
        onDone: (String?) -> Unit
    ) {
        if (busy) return
        busy = true
        worker.execute {
            val key = readKey()
            if (key == null) {
                busy = false
                onDone("No hay clave de Gemini en ${keyFile.absolutePath}")
                return@execute
            }
            val t0 = System.currentTimeMillis()
            var chars = 0
            var firstMs = -1L
            try {
                val body = requestBody(system, messages)
                var error: String? = null
                // Sube la escalera hasta que un modelo responda.
                while (true) {
                    val outcome = stream(model, key, body) { piece ->
                        if (firstMs < 0) firstMs = System.currentTimeMillis() - t0
                        chars += piece.length
                        onToken(piece)
                    }
                    if (outcome == null) break
                    Log.w(TAG, "$model: $outcome")
                    if (chars > 0 || !outcome.retryable || rung >= LADDER.size - 1) {
                        error = outcome.message
                        break
                    }
                    rung++
                    status = "cambio a $model ($outcome)"
                }
                val total = System.currentTimeMillis() - t0
                if (error == null) {
                    status = "$model: primera palabra en $firstMs ms, $chars letras en $total ms"
                    Log.i(TAG, status)
                    onDone(null)
                } else {
                    status = "$model: $error"
                    Log.e(TAG, status)
                    onDone(error)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Falló la nube", e)
                status = "${e.javaClass.simpleName}: ${e.message}"
                onDone("Sin conexión (${e.javaClass.simpleName})")
            } finally {
                busy = false
            }
        }
    }

    private fun requestBody(system: String, messages: List<Pair<String, String>>): JSONObject {
        val contents = JSONArray()
        for ((role, text) in messages) {
            contents.put(
                JSONObject()
                    .put("role", if (role == "assistant") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", text)))
            )
        }
        return JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put(
                "generationConfig", JSONObject()
                    .put("temperature", 0.7)
                    .put("maxOutputTokens", 300)
                    // Sin "pensar": en una charla corta pesa más el segundo de espera.
                    .put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            )
    }

    /** Por qué falló una llamada y si vale la pena probar con el siguiente modelo. */
    private class Failure(val message: String, val retryable: Boolean) {
        override fun toString() = message
    }

    /** Llama a streamGenerateContent (SSE) y va entregando el texto. Devuelve null si respondió. */
    private fun stream(model: String, key: String, body: JSONObject, onPiece: (String) -> Unit): Failure? {
        var payload = body
        for (attempt in 0..1) {
            val url = URL("$BASE/models/$model:streamGenerateContent?alt=sse&key=$key")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code != 200) {
                val err = try { conn.errorStream?.bufferedReader()?.readText() ?: "" } catch (e: Throwable) { "" }
                conn.disconnect()
                // Un 400 casi siempre es que ese modelo no acepta thinkingConfig
                // (gemini-3.6-flash lo rechazó con "invalid argument" a secas, sin
                // nombrarlo): se reintenta una vez sin él antes de darlo por perdido.
                if (attempt == 0 && code == 400) {
                    Log.w(TAG, "$model: HTTP 400 (${shortError(err)}); reintento sin thinkingConfig")
                    payload = JSONObject(body.toString()).also {
                        it.getJSONObject("generationConfig").remove("thinkingConfig")
                    }
                    continue
                }
                val msg = shortError(err)
                return when (code) {
                    429 -> Failure("cuota agotada ($msg)", retryable = true)
                    404 -> Failure("modelo no disponible ($msg)", retryable = true)
                    503 -> Failure("Google saturado ($msg)", retryable = true)
                    // el 400 que sobrevive al reintento es de ese modelo: el siguiente puede servir
                    400 -> Failure("petición rechazada ($msg)", retryable = true)
                    else -> Failure("HTTP $code: $msg", retryable = false)
                }
            }
            var finish: String? = null
            conn.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    if (!line.startsWith("data:")) continue
                    val json = line.removePrefix("data:").trim()
                    if (json.isEmpty()) continue
                    val obj = JSONObject(json)
                    val candidates = obj.optJSONArray("candidates") ?: continue
                    if (candidates.length() == 0) continue
                    val cand = candidates.getJSONObject(0)
                    cand.optString("finishReason", "").takeIf { it.isNotEmpty() }?.let { finish = it }
                    val parts = cand.optJSONObject("content")?.optJSONArray("parts") ?: continue
                    for (i in 0 until parts.length()) {
                        val p = parts.getJSONObject(i)
                        if (p.optBoolean("thought", false)) continue
                        val text = p.optString("text", "")
                        if (text.isNotEmpty()) onPiece(text)
                    }
                }
            }
            conn.disconnect()
            if (finish != null && finish != "STOP") Log.w(TAG, "$model terminó por $finish")
            return null
        }
        return Failure("sin respuesta", retryable = false)
    }

    private fun shortError(body: String): String = try {
        JSONObject(body).getJSONObject("error").getString("message")
            .substringBefore("\n").take(160)
    } catch (e: Throwable) {
        body.take(160)
    }

    /** Prueba la clave: lista los modelos que acepta (para "Probar conexión" en Ajustes). */
    fun testConnection(onDone: (String) -> Unit) {
        worker.execute {
            val key = readKey()
            if (key == null) {
                onDone("No hay clave en ${keyFile.absolutePath}")
                return@execute
            }
            try {
                val t0 = System.currentTimeMillis()
                val conn = (URL("$BASE/models?key=$key&pageSize=200").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                val code = conn.responseCode
                if (code != 200) {
                    val err = try { conn.errorStream?.bufferedReader()?.readText() ?: "" } catch (e: Throwable) { "" }
                    onDone("La clave no sirve (HTTP $code: ${shortError(err)})")
                    return@execute
                }
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val arr = json.optJSONArray("models") ?: JSONArray()
                val names = HashSet<String>()
                for (i in 0 until arr.length()) names.add(arr.getJSONObject(i).getString("name").removePrefix("models/"))
                val missing = LADDER.filterNot { it in names }
                val ms = System.currentTimeMillis() - t0
                onDone(
                    "Clave válida (${ms} ms). " +
                        if (missing.isEmpty()) "Los ${LADDER.size} modelos de la escalera están disponibles."
                        else "Faltan: ${missing.joinToString()}."
                )
            } catch (e: Throwable) {
                onDone("Sin conexión: ${e.javaClass.simpleName} ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "HabloNube"
        const val KEY_FILE = "gemini.key"
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta"

        /**
         * De mejor a peor. Los Flash 3.x son equivalentes en criterio; cada
         * uno trae su propia cuota gratuita (5/min, 20/día). Lite al final:
         * 500/día, más rápido, un poco menos de criterio.
         */
        val LADDER = listOf(
            "gemini-3.8-flash",
            "gemini-3.7-flash",
            "gemini-3.6-flash",
            "gemini-3.5-flash",
            "gemini-3-flash-preview",
            "gemini-3.5-flash-lite"
        )
    }
}
