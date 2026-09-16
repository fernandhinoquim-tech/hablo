package com.ferolabs.hablo

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Un globo de la conversación. */
private data class Bubble(
    val fromTeacher: Boolean,
    val text: String,
    /** Corrección en español, solo en globos de la profesora. */
    val correctionEs: String? = null,
    val streaming: Boolean = false
)

private const val CORRECTION_MARK = "CORRECCIÓN:"

/**
 * Conversación con la IA en un escenario cerrado (Fase 3). El ciclo completo:
 * la profesora abre hablando (Piper) → el alumno habla (Parakeet transcribe)
 * o escribe → la IA responde en su papel y, si hubo un error típico de
 * hispanohablante, lo corrige en español → Piper lee la parte en inglés.
 *
 * El motor lo elige Ajustes ([ChatEngine]): Claude, Gemini o la IA del
 * teléfono. Si el de internet falla a mitad de charla, se sigue con el local
 * sin perder el hilo.
 *
 * Memoria: al entrar se suelta el modelo de fonemas y, en modo local, se
 * carga Qwen (~5 GB); al salir se suelta. Parakeet y Piper sí conviven con él.
 */
@Composable
fun ConversationScreen(
    scenario: Scenario,
    teacher: Teacher,
    speaker: Speaker,
    listener: Listener,
    engine: ChatEngine,
    /** Respaldo si el de internet se cae a mitad de charla. */
    local: LocalEngine,
    showFace: Boolean,
    say: (String, Float) -> Unit,
    /** Lee sin interrumpir lo que ya suena: para ir leyendo frase por frase. */
    sayQueued: (String) -> Unit,
    /** Cuaderno de errores: guarda lo que la profesora corrige. */
    progreso: Progreso,
    /**
     * Solo en la charla libre ("Hablar de todo"): la ficha que la profesora
     * recuerda entre charlas. En los escenarios va null: arrancan limpios.
     */
    memoria: Memoria? = null,
    /** Para el resumen de la charla libre al cerrar (UNA llamada a Haiku). */
    claude: ClaudeLlm? = null,
    onBack: () -> Unit
) {
    val accent = Color(teacher.color)
    val libre = memoria != null
    val bubbles = remember { mutableStateListOf<Bubble>() }
    val history = remember { mutableStateListOf<Pair<String, String>>() }
    var draft by remember { mutableStateOf("") }
    var notHeard by remember { mutableStateOf<NotHeardReason?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    // El motor puede cambiar a mitad de charla si el de internet se cae.
    var actual by remember { mutableStateOf(if (engine.usable()) engine else local) }
    var engineNote by remember { mutableStateOf<String?>(null) }
    var showHelp by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val opening = remember(scenario.id) {
        if (memoria != null) aperturaLibre(teacher, memoria) else scenario.opening.replace("{teacher}", teacher.name)
    }
    val basePrompt = remember(scenario.id, teacher.id) {
        if (memoria != null) buildFreePrompt(teacher, memoria) else buildSystemPrompt(scenario, teacher)
    }
    val ready = actual.ready()
    val engineBusy = actual.busy

    // Preparar el motor al entrar y soltarlo al salir. Nunca con el modelo de
    // fonemas cargado: el de la conversación puede pesar 5 GB.
    LaunchedEffect(Unit) {
        listener.releaseSounds()
        listener.prepareConversation()
        startConversation(bubbles, history, opening, say)
        actual.start(actual.systemPrompt(basePrompt), history.toList()) { ok -> if (!ok) loadFailed = true }
    }
    DisposableEffect(Unit) {
        onDispose {
            speaker.stop()
            listener.stopRecording()
            listener.releaseConversation()
            actual.stop()
            actual.release()
            local.release()
            // Charla libre: UNA llamada a Haiku con la charla para actualizar la
            // ficha (datos y resumen). Los errores ya se anotaron gratis en cada
            // turno. Sin clave o sin charla de verdad, no se llama.
            if (memoria != null && claude != null) cerrarCharlaLibre(memoria, claude, history.toList())
        }
    }

    // Bajar al último globo cuando llega algo nuevo.
    LaunchedEffect(bubbles.size, bubbles.lastOrNull()?.text?.length) {
        if (bubbles.isNotEmpty()) listState.animateScrollToItem(bubbles.size - 1)
    }

    /** Pide la respuesta al último mensaje del historial con el motor que toque. */
    fun ask() {
        bubbles.add(Bubble(fromTeacher = true, text = "", streaming = true))
        val idx = bubbles.size - 1
        var partial = ""
        var spokenUpTo = 0   // hasta dónde de la parte en inglés ya se mandó a leer
        val onToken: (String) -> Unit = { piece ->
            partial += piece
            val english = visibleEnglish(partial)
            bubbles[idx] = Bubble(fromTeacher = true, text = english, streaming = true)
            // Leer frase por frase apenas termina cada una: la profesora
            // empieza a hablar mientras la IA sigue escribiendo.
            val end = lastSentenceEnd(english, spokenUpTo)
            if (end > spokenUpTo) {
                sayQueued(english.substring(spokenUpTo, end).trim())
                spokenUpTo = end
            }
        }
        val finish = {
            val (english, correction) = splitReply(partial)
            bubbles[idx] = Bubble(fromTeacher = true, text = english, correctionEs = correction)
            // exactamente lo generado (sin recortar): la memoria del modelo
            // local tiene esos tokens y el siguiente turno se apoya en ellos
            history.add("assistant" to partial)
            val rest = if (spokenUpTo < english.length) english.substring(spokenUpTo).trim() else ""
            if (rest.isNotBlank()) sayQueued(rest)
            // La corrección se LEE, no se oye: Fero probó la voz española y la
            // rechazó ("no me gusta el cambio de voz"). Se anota para el informe.
            if (!correction.isNullOrBlank()) {
                progreso.anotarCorreccion(correction)
                memoria?.anotarError(correction)
            }
        }
        val motor = actual
        motor.chat(
            system = motor.systemPrompt(basePrompt),
            messages = history.toList(),
            onToken = onToken
        ) { error ->
            if (error == null || partial.any { it.isLetterOrDigit() }) {
                finish()
            } else if (motor !== local && local.usable()) {
                // El de internet no dijo nada: el resto de la charla sigue en el teléfono.
                bubbles.removeAt(idx)
                actual = local
                engineNote = "${motor.label} no respondió ($error). Sigo con la IA del teléfono."
                local.start(local.systemPrompt(basePrompt), history.toList()) { ok ->
                    if (ok) ask() else loadFailed = true
                }
            } else {
                bubbles.removeAt(idx)
                engineNote = error
            }
        }
    }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || engineBusy || !ready) return
        notHeard = null
        draft = ""
        bubbles.add(Bubble(fromTeacher = false, text = clean))
        history.add("user" to clean)
        progreso.anotarActividad(Progreso.Actividad.TURNO)
        ask()
    }

    fun listen() {
        listener.startRecording(target = "", sound = Sound.GENERAL, conversation = true) { r ->
            when (r) {
                is ListenResult.Heard -> send(r.text)
                is ListenResult.NotHeard -> notHeard = r.reason
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) listen() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar("${scenario.emoji} ${scenario.title}", onBack = onBack) {
            TeacherAvatar(teacher = teacher, speaking = speaker.busy, size = 40.dp, showFace = showFace)
            Spacer(Modifier.size(8.dp))
        }

        // Meta del escenario, siempre a la vista (la charla libre no tiene metas).
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(scenario.goalEs, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
            if (scenario.targets.isNotEmpty()) Text(
                "Intenta usar: " + scenario.targets.joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
            if (libre && memoria.hayAlgo()) Text(
                "${teacher.name} se acuerda de lo básico de tus charlas anteriores.",
                style = MaterialTheme.typography.labelSmall,
                color = InkSoft
            )
            Text(
                actual.label,
                style = MaterialTheme.typography.labelSmall,
                color = InkSoft
            )
        }

        if (!ready && !loadFailed) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Despertando a ${teacher.name}…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(color = accent, trackColor = Line, modifier = Modifier.fillMaxWidth())
            }
        }
        if (loadFailed) {
            Text(
                actual.status.ifBlank { "No se pudo cargar el modelo de IA." },
                style = MaterialTheme.typography.bodyMedium,
                color = BadRed,
                modifier = Modifier.padding(20.dp)
            )
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            items(bubbles) { b -> BubbleView(b, teacher, accent) }
        }

        notHeard?.let { NotHeardBox(it) }
        engineNote?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF8A5A00),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        // --- Ayudas de gramática ------------------------------------------
        // Fero (2026-09-13): "ayudas para saber qué y cómo preguntar, solo
        // gramática porque se supone que el vocabulario lo debo llevar". Por eso
        // son patrones con hueco y la explicación dice CUÁNDO se usa, no qué
        // significa: las palabras las pone él.
        val ayudas = remember(scenario.id) { scenario.help + Course.helpCommon }   // libre: help vacío, solo comunes
        if (ayudas.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Cream)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    if (showHelp) "Ocultar ayudas ▾" else "💡 ¿Cómo lo digo?",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    modifier = Modifier
                        .clickable { showHelp = !showHelp }
                        .padding(vertical = 8.dp)
                )
                if (showHelp) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .border(1.dp, Line, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        ayudas.forEachIndexed { i, a ->
                            if (i == scenario.help.size && scenario.help.isNotEmpty()) {
                                Text(
                                    "Para cualquier momento",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = InkSoft,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        // Tocar la frase la escribe en la caja: se completa el
                                        // hueco y se envía, en vez de escribirla entera.
                                        .clickable { draft = a.en.replace("___", "").replace("  ", " ").trim() }
                                        .padding(vertical = 6.dp)
                                ) {
                                    Text(a.en, style = MaterialTheme.typography.bodyLarge, color = Ink)
                                    Text(a.es, style = MaterialTheme.typography.labelMedium, color = InkSoft)
                                }
                                Text(
                                    "🔊",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier
                                        .clickable { say(a.en.replace("___", "something"), 1f) }
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // --- Entrada: micrófono o teclado ----------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Cream)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (listener.recording) {
                LinearProgressIndicator(
                    progress = { listener.level },
                    color = BadRed,
                    trackColor = Line,
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val canTalk = ready && !engineBusy && !listener.thinking
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .background(if (listener.recording) BadRed else if (canTalk) accent else Line, CircleShape)
                        .clickable(enabled = canTalk || listener.recording) {
                            when {
                                listener.recording -> listener.stopRecording()
                                listener.hasMicPermission() -> { speaker.stop(); listen() }
                                else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                ) {
                    Text(if (listener.recording) "■" else "🎤", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    enabled = canTalk,
                    placeholder = { Text(if (listener.recording) "Grabando…" else "O escribe aquí…") },
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Enviar",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (draft.isNotBlank() && canTalk) accent else Line,
                    modifier = Modifier
                        .clickable(enabled = draft.isNotBlank() && canTalk) { speaker.stop(); send(draft) }
                        .padding(8.dp)
                )
            }
            Text(
                when {
                    listener.recording -> "Habla; cuando te calles, se envía solo."
                    listener.thinking -> "Escuchando lo que dijiste…"
                    engineBusy && ready -> "${teacher.name} está pensando…"
                    else -> "Toca el micrófono y habla."
                },
                style = MaterialTheme.typography.labelMedium,
                color = InkSoft,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/** Índice justo después del último cierre de frase (. ! ?) completo a partir de [from]. */
private fun lastSentenceEnd(text: String, from: Int): Int {
    var end = from
    var i = from
    while (i < text.length) {
        val c = text[i]
        if ((c == '.' || c == '!' || c == '?') && (i + 1 == text.length || text[i + 1].isWhitespace())) {
            // un punto seguido de espacio ya es frase completa; el final del texto
            // todavía puede crecer (p. ej. "3." de "3.50"), así que se exige espacio
            if (i + 1 < text.length) end = i + 1
        }
        i++
    }
    return end
}

private fun startConversation(
    bubbles: MutableList<Bubble>,
    history: MutableList<Pair<String, String>>,
    opening: String,
    say: (String, Float) -> Unit
) {
    if (bubbles.isNotEmpty()) return
    bubbles.add(Bubble(fromTeacher = true, text = opening))
    history.add("assistant" to opening)
    say(opening, 1f)
}

/**
 * Lo que la IA tiene que hacer. En inglés porque el modelo obedece mejor así;
 * la corrección al alumno va en español. El "sello de la casa" (los errores de
 * quien piensa en español) va explícito, más las trampas del escenario.
 */
private fun buildSystemPrompt(scenario: Scenario, teacher: Teacher): String {
    val origin = if (teacher.accent == Accent.UK) "from England" else "from the United States"
    return buildString {
        // El vocabulario sube con el nivel del escenario (desde el 16-09 hay A2 y B1).
        val vocab = when (scenario.level) { "B1" -> "A2-B1 vocabulary, a bit richer"; "A2" -> "A2 vocabulary"; else -> "A1-A2 vocabulary" }
        appendLine("You are ${teacher.name}, a warm English teacher $origin. You are role-playing with a Spanish-speaking student (level ${scenario.level}).")
        appendLine("Situation: ${scenario.role}")
        appendLine("Rules:")
        appendLine("- Stay in character and REPLY to what the student said, as the character would. Write simple English ($vocab), at most two short sentences, and end with a question or an invitation so the student keeps talking.")
        append(reglasComunes())
        appendLine("- The student's goals: ${scenario.targets.joinToString("; ")}. Gently steer the conversation so they get to use them.")
        appendLine("- Traps to watch in this situation: ${scenario.watch.joinToString(" | ")}.")
        appendLine("- Never use lists, emojis, or the word CORRECCIÓN inside the English part. Do not translate your English into Spanish.")
        appendLine()
        append(protocoloCorreccion())
    }
}

/**
 * La charla libre ("Hablar de todo"): sin papel, sin nivel, sin metas, y con
 * la ficha de memoria delante. Mismas reglas del dictado y mismo protocolo de
 * corrección que los escenarios: no se duplican, se comparten.
 */
private fun buildFreePrompt(teacher: Teacher, memoria: Memoria): String {
    val origin = if (teacher.accent == Accent.UK) "from England" else "from the United States"
    return buildString {
        appendLine("You are ${teacher.name}, a warm English teacher $origin, having a relaxed one-to-one chat with a Spanish-speaking student (level A1-B1). This is a FREE conversation: no role-play, no fixed topic.")
        appendLine("Rules:")
        appendLine("- Talk about whatever the student wants: their day, plans, family, opinions, questions about English. REPLY to what they said, show interest, and end with a question so they keep talking. Simple English (A2 vocabulary), at most two short sentences.")
        append(reglasComunes())
        appendLine("- If they ask you something about English (a word, a rule), answer briefly in English and, if the explanation needs Spanish, put it in the $CORRECTION_MARK line.")
        appendLine("- Never use lists, emojis, or the word CORRECCIÓN inside the English part. Do not translate your English into Spanish.")
        appendLine()
        val ficha = memoria.bloquePrompt()
        if (ficha.isNotBlank()) {
            appendLine(ficha)
            appendLine()
        }
        append(protocoloCorreccion())
    }
}

/** Las reglas que no dependen del escenario: qué hacer con el dictado y qué NO corregir. */
private fun reglasComunes(): String {
    return buildString {
        appendLine("- The student's messages come from a speech recognizer, which often mishears a Spanish accent: 'As model please' means 'a small, please'; 'Marion' means 'medium'; 'thoughts with Buddha' means 'toast with butter'; 'What nine is it' means 'what time is it'. ALWAYS guess the most likely meaning from the context and answer THAT, in character, confidently. Never say you didn't understand unless it is truly impossible, and even then offer a guess: 'Do you mean ...?'.")
        appendLine("- NEVER correct a strange word, a misspelling or a word that does not fit the sentence: those are the recognizer's mistakes, not the student's. Correct ONLY grammar mistakes typical of Spanish speakers that the student clearly produced: verb forms ('he work'), a missing article ('my sister is doctor'), 'I have 25 years' (I'm 25 years old), 'I'm agree' (I agree), word order, false friends ('actually' does not mean 'actualmente'). If in doubt, do not correct.")
        appendLine("- Correct only what is WRONG, never what is merely different from how you would say it. These are all CORRECT and must not be corrected: 'I am forty' or 'I'm 40' (age without 'years old'), long forms instead of contractions ('I do not like tea', 'I am from Colombia'), British or American variants, informal but correct answers ('A coffee, please'). Never rewrite a correct sentence to make it shorter or more natural: that is not a correction.")
        appendLine("- ALWAYS answer with at least one complete English sentence. Never answer with nothing. Vary your wording: never reuse a sentence you already said in this conversation.")
        appendLine("- If the student talks about something else, follow them naturally (answer their question, react), and bring the conversation back to the situation a little later if there is one. Do not ignore what they say.")
    }
}

/** El protocolo de corrección, con ejemplos. Compartido por escenarios y charla libre. */
private fun protocoloCorreccion(): String {
    return buildString {
        // El protocolo de corrección va aparte y con ejemplo, no como una regla
        // más de la lista: medido el 2026-09-13 con seis turnos reales, Haiku
        // 4.5 decía la frase correcta en voz alta 0 de 3 veces con la regla
        // enterrada y 3 de 3 con este protocolo (Sonnet 5 la decía 3 de 3 con
        // las dos versiones). Fero la oye, no solo la lee: por eso es obligatoria.
        appendLine("HOW TO CORRECT (follow exactly):")
        appendLine("When the student's sentence has a real grammar mistake, your reply has THREE parts, in this order:")
        appendLine("1. One short sentence reacting in character.")
        appendLine("2. The sentence \"You can say: '<the corrected sentence>'.\" — this part is MANDATORY and is read out loud to the student, so it must be inside your English reply, never only in the Spanish line.")
        appendLine("3. A final separate line: $CORRECTION_MARK <one short sentence in Spanish: what they said, the correct form, and why>.")
        appendLine("Only one correction per turn, the most important one. When there is NO real grammar mistake, reply normally, with no correction and no $CORRECTION_MARK line.")
        appendLine()
        // Ojo con el ejemplo: con "I have 30 years" como único modelo, Haiku
        // corrigió "I am forty" (correcto) por "la edad siempre va con years old"
        // (progreso.json, 2026-09-13). Por eso el ejemplo es otro error y hay un
        // ejemplo sin error que se parece a uno.
        appendLine("Example WITH a mistake:")
        appendLine("  Student: My sister is doctor and she work in a hospital.")
        appendLine("  You: Oh, a doctor, how nice! You can say: 'My sister is a doctor and she works in a hospital.' Do you work too?")
        appendLine("  $CORRECTION_MARK Dijiste \"is doctor\" y \"she work\"; las profesiones llevan artículo y la tercera persona lleva -s: \"My sister is a doctor and she works in a hospital\".")
        appendLine()
        appendLine("Example WITHOUT a mistake (do not invent one):")
        appendLine("  Student: I am forty and I do not like coffee.")
        appendLine("  You: Forty is a great age, and no coffee, noted! Would you like some tea instead?")
        appendLine()
        appendLine("Example WITHOUT a mistake:")
        appendLine("  Student: A small coffee please.")
        append("  You: One small coffee, coming right up! Would you like anything to eat?")
    }
}

/** La primera frase de la charla libre: con nombre y tema anterior si la ficha los tiene. */
private fun aperturaLibre(teacher: Teacher, memoria: Memoria): String {
    val nombre = memoria.nombre()
    val saludo = if (nombre != null) "Hi, $nombre!" else "Hi! I'm ${teacher.name}."
    return if (memoria.resumen.isNotBlank()) {
        "$saludo Nice to see you again. What's new since we last talked?"
    } else {
        "$saludo We can talk about anything you like today. What's on your mind?"
    }
}

/**
 * Al cerrar la charla libre: UNA llamada a Haiku con la charla y la ficha
 * actual, que devuelve `{"datos": [...], "resumen": "..."}`. Si no parsea,
 * [Memoria.aplicarRespuesta] deja la ficha como estaba. Con menos de dos
 * turnos del alumno no hay nada que resumir y no se gasta.
 */
private fun cerrarCharlaLibre(memoria: Memoria, claude: ClaudeLlm, history: List<Pair<String, String>>) {
    val turnos = history.count { it.first == "user" }
    if (turnos < 2 || !claude.keyPresent()) return
    memoria.cargar()
    val system = buildString {
        appendLine("You maintain a short memory card about a Spanish-speaking English student, for his teacher to use in the next chat.")
        appendLine("Reply with ONLY a JSON object, no prose: {\"datos\": [{\"k\": \"...\", \"v\": \"...\"}], \"resumen\": \"...\"}")
        appendLine("- datos: up to ${Memoria.MAX_DATOS} stable facts about the student (name, city, job, family, likes, plans), each as a short key and a short value, in Spanish. Start from the existing facts, keep the ones still true, add new ones, drop the least useful if over ${Memoria.MAX_DATOS}. Never invent.")
        appendLine("- resumen: what you talked about THIS time, in Spanish, at most ${Memoria.MAX_PALABRAS_RESUMEN} words, so the teacher can bring it up next time.")
        append("Do not include the student's mistakes: those are tracked elsewhere.")
    }
    val user = buildString {
        appendLine("Existing card:")
        appendLine(memoria.toJson().toString())
        appendLine()
        appendLine("Transcript of today's chat (Teacher / Student):")
        for ((role, text) in history) {
            val quien = if (role == "assistant") "Teacher" else "Student"
            appendLine("$quien: ${text.take(400)}")
        }
    }
    claude.resumir(system, user) { texto ->
        if (texto == null || !memoria.aplicarRespuesta(texto)) {
            android.util.Log.w("HabloMemoria", "resumen no aplicado; se conserva la ficha anterior")
        }
    }
}

/**
 * Parte en inglés (lo que se lee en voz alta) y corrección en español, si la
 * hay. La corrección es la línea que empieza con el marcador, esté donde esté
 * (el modelo a veces la pone antes de responder); todo lo demás es la respuesta.
 */
private fun splitReply(raw: String): Pair<String, String?> {
    val lines = stripThinking(raw).lines()
    val corrections = ArrayList<String>()
    val english = StringBuilder()
    for (line in lines) {
        val t = line.trim()
        val idx = t.indexOf(CORRECTION_MARK, ignoreCase = true)
        if (idx >= 0) {
            corrections.add(t.substring(idx + CORRECTION_MARK.length).trim())
        } else if (t.isNotEmpty()) {
            if (english.isNotEmpty()) english.append(' ')
            english.append(t)
        }
    }
    return english.toString().trim() to corrections.joinToString(" ").ifBlank { null }
}

/** Mientras llega el texto: lo mismo, pero la línea en curso todavía puede ser una corrección a medias. */
private fun visibleEnglish(partial: String): String = splitReply(partial).first

private fun stripThinking(text: String): String =
    text.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
        .replace("<think>", "").replace("</think>", "")

@Composable
private fun BubbleView(b: Bubble, teacher: Teacher, accent: Color) {
    Column(
        horizontalAlignment = if (b.fromTeacher) Alignment.Start else Alignment.End,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (b.text.isNotBlank() || b.streaming) {
            Text(
                if (b.text.isBlank()) "…" else b.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (b.fromTeacher) Ink else Color.White,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .background(
                        if (b.fromTeacher) Color.White else accent,
                        RoundedCornerShape(16.dp)
                    )
                    .border(1.dp, if (b.fromTeacher) Line else accent, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
        b.correctionEs?.let { c ->
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .background(Color(0xFFFFF6E3), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFF0DFB9), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text("✎ ${teacher.name} te corrige", style = MaterialTheme.typography.labelMedium, color = Color(0xFF8A5A00))
                Text(c, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4A3A12))
            }
        }
    }
}

/** Lista de escenarios para elegir con quién y de qué hablar. */
@Composable
fun ScenariosScreen(
    teacher: Teacher,
    engine: ChatEngine,
    local: LocalEngine,
    onPick: (Scenario) -> Unit,
    onBack: () -> Unit
) {
    val accent = Color(teacher.color)
    val canTalk = engine.usable() || local.usable()
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar("Conversar con ${teacher.name}", onBack = onBack)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f).padding(20.dp)
        ) {
            if (!canTalk) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BadRedSoft, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Text("Falta quien haga de profesora", style = MaterialTheme.typography.labelLarge, color = BadRed)
                        Text(
                            "Elige en Ajustes quién responde: Claude o Gemini por internet, " +
                                "o copia el modelo de IA al teléfono por USB.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink
                        )
                    }
                }
            }
            item {
                Text(
                    "Hoy responde: ${if (engine.usable()) engine.label else local.label}. Se cambia en Ajustes.",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
            }
            // La charla libre: sin escenario ni nivel, y con memoria (etapa 2).
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(teacher.softColor), RoundedCornerShape(16.dp))
                        .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .clickable(enabled = canTalk) { onPick(Scenario.LIBRE) }
                        .padding(16.dp)
                ) {
                    Text(Scenario.LIBRE.emoji, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.size(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(Scenario.LIBRE.title, style = MaterialTheme.typography.titleMedium)
                        Text(Scenario.LIBRE.goalEs, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                    }
                }
            }
            item {
                Text(
                    "Situaciones cortas y cerradas, por nivel. ${teacher.name} hace un papel, tú hablas, y ella te corrige en español lo que se te escapa. Cada una arranca de cero.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            // Agrupados por nivel, en el orden del JSON (A1, A2, B1…).
            val porNivel = Course.scenarios.groupBy { it.level }
            for ((nivel, lista) in porNivel) {
                item(key = "nivel-$nivel") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Pill(nivel, accent, Color(teacher.softColor))
                        Spacer(Modifier.size(10.dp))
                        Text(
                            when (nivel) { "A1" -> "Para empezar"; "A2" -> "Ya con lo básico"; "B1" -> "Para defenderte"; else -> nivel },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                items(lista, key = { it.id }) { sc ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .border(1.dp, Line, RoundedCornerShape(16.dp))
                            .clickable(enabled = canTalk) { onPick(sc) }
                            .padding(16.dp)
                    ) {
                        Text(sc.emoji, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.size(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sc.title, style = MaterialTheme.typography.titleMedium)
                            Text(sc.goalEs, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                        }
                    }
                }
            }
        }
    }
}
