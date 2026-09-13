package com.ferolabs.hablo

/**
 * Lo que la pantalla de conversación necesita de quien hace de profesora, sin
 * saber si vive en el teléfono o en internet. Tres implementaciones:
 * [LocalEngine] (Qwen3 8B con llama.cpp), [CloudLlm] (Gemini, gratis) y
 * [ClaudeLlm] (Claude API, de pago).
 *
 * Así la pantalla tiene un solo camino y cambiar de motor —incluso a mitad de
 * charla, cuando el de internet falla— es cambiar de objeto.
 */
interface ChatEngine {

    /** Lo que se muestra arriba de la charla: "Claude Sonnet 5", "IA del teléfono"… */
    val label: String

    /** Está generando una respuesta. */
    val busy: Boolean

    /** Última medida o último error, para Ajustes. */
    val status: String

    /** ¿Puede responder ya? (modelo cargado, clave presente…) */
    fun ready(): Boolean

    /** ¿Tiene lo que necesita para funcionar? (el archivo del modelo, la clave…) */
    fun usable(): Boolean

    /**
     * Cada motor puede necesitar algo propio en el prompt (el local, `/no_think`).
     * El texto común lo arma la pantalla.
     */
    fun systemPrompt(base: String): String = base

    /**
     * Prepara una charla nueva. [history] es lo dicho hasta ahora (la apertura de
     * la profesora). [onReady] recibe false si no se pudo preparar.
     */
    fun start(system: String, history: List<Pair<String, String>>, onReady: (Boolean) -> Unit) = onReady(true)

    /**
     * Responde al último mensaje. [messages] son pares (rol, texto) con roles
     * "user"/"assistant"; el sistema va aparte. [onDone] recibe null si todo
     * fue bien, o el error que hay que mostrar.
     */
    fun chat(
        system: String,
        messages: List<Pair<String, String>>,
        onToken: (String) -> Unit,
        onDone: (String?) -> Unit
    )

    /** Corta la generación en curso. */
    fun stop() {}

    /** Suelta la memoria (solo importa en el local: son 5 GB). */
    fun release() {}
}

/** La IA que corre dentro del teléfono. Envuelve a [Llm] para que la pantalla no lo distinga. */
class LocalEngine(private val llm: Llm) : ChatEngine {

    override val label = "IA del teléfono · sin internet"
    override val busy: Boolean get() = llm.busy
    override val status: String get() = llm.status

    override fun ready() = llm.loaded
    override fun usable() = llm.modelPresent()

    /** Qwen3 razona por defecto y eso cuesta 5-15 s por turno. */
    override fun systemPrompt(base: String) = "$base\n/no_think"

    override fun start(system: String, history: List<Pair<String, String>>, onReady: (Boolean) -> Unit) {
        val begin = {
            // Procesa el prompt de sistema mientras la profesora dice la apertura:
            // el primer turno del alumno no paga esos ~400 tokens.
            llm.startConversation(listOf("system" to system) + history)
            onReady(true)
        }
        if (llm.loaded) begin() else llm.load { ok -> if (ok) begin() else onReady(false) }
    }

    override fun chat(
        system: String,
        messages: List<Pair<String, String>>,
        onToken: (String) -> Unit,
        onDone: (String?) -> Unit
    ) {
        llm.chat(
            messages = listOf("system" to system) + messages,
            onToken = onToken,
            onDone = { onDone(null) }
        )
    }

    override fun stop() = llm.stop()
    override fun release() = llm.release()
}
