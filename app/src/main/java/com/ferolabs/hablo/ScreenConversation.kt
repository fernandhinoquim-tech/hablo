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
 * la profesora abre hablando (Piper) → el alumno habla (Moonshine transcribe)
 * o escribe → Qwen responde en su papel y, si hubo un error típico de
 * hispanohablante, lo corrige en español → Piper lee la parte en inglés.
 *
 * Memoria: al entrar se suelta el modelo de fonemas y se carga Qwen (~5 GB);
 * al salir se suelta Qwen. Moonshine y Piper sí conviven con él.
 */
@Composable
fun ConversationScreen(
    scenario: Scenario,
    teacher: Teacher,
    speaker: Speaker,
    listener: Listener,
    llm: Llm,
    showFace: Boolean,
    say: (String, Float) -> Unit,
    /** Lee sin interrumpir lo que ya suena: para ir leyendo frase por frase. */
    sayQueued: (String) -> Unit,
    onBack: () -> Unit
) {
    val accent = Color(teacher.color)
    val bubbles = remember { mutableStateListOf<Bubble>() }
    val history = remember { mutableStateListOf<Pair<String, String>>() }
    var draft by remember { mutableStateOf("") }
    var notHeard by remember { mutableStateOf<NotHeardReason?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val opening = scenario.opening.replace("{teacher}", teacher.name)
    val systemPrompt = remember(scenario.id, teacher.id) { buildSystemPrompt(scenario, teacher) }

    // Cargar la IA al entrar; soltarla al salir. Nunca con el modelo de fonemas
    // cargado. Mientras la profesora dice la apertura, el modelo ya procesa el
    // prompt de sistema: el primer turno del alumno no paga esos ~250 tokens.
    LaunchedEffect(Unit) {
        listener.releaseSounds()
        listener.prepareConversation()
        val begin = {
            startConversation(bubbles, history, opening, say)
            llm.startConversation(listOf("system" to systemPrompt) + history.toList())
        }
        if (llm.loaded) begin() else llm.load { ok -> if (ok) begin() else loadFailed = true }
    }
    DisposableEffect(Unit) {
        onDispose {
            speaker.stop()
            listener.stopRecording()
            listener.releaseConversation()
            llm.stop()
            llm.release()
        }
    }

    // Bajar al último globo cuando llega algo nuevo.
    LaunchedEffect(bubbles.size, bubbles.lastOrNull()?.text?.length) {
        if (bubbles.isNotEmpty()) listState.animateScrollToItem(bubbles.size - 1)
    }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || llm.busy || !llm.loaded) return
        notHeard = null
        draft = ""
        bubbles.add(Bubble(fromTeacher = false, text = clean))
        history.add("user" to clean)
        bubbles.add(Bubble(fromTeacher = true, text = "", streaming = true))
        val idx = bubbles.size - 1
        var partial = ""
        var spokenUpTo = 0   // hasta dónde de la parte en inglés ya se mandó a leer
        llm.chat(
            messages = listOf("system" to systemPrompt) + history.toList(),
            onToken = { piece ->
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
            },
            onDone = {
                val (english, correction) = splitReply(partial)
                bubbles[idx] = Bubble(fromTeacher = true, text = english, correctionEs = correction)
                // exactamente lo generado (sin recortar): la memoria del modelo
                // tiene esos tokens y el siguiente turno se apoya en ellos
                history.add("assistant" to partial)
                val rest = if (spokenUpTo < english.length) english.substring(spokenUpTo).trim() else ""
                if (rest.isNotBlank()) sayQueued(rest)
            }
        )
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

        // Meta del escenario, siempre a la vista.
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(scenario.goalEs, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
            Text(
                "Intenta usar: " + scenario.targets.joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
        }

        if (!llm.loaded && !loadFailed) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Despertando a ${teacher.name}… (carga 5 GB, unos segundos)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(color = accent, trackColor = Line, modifier = Modifier.fillMaxWidth())
            }
        }
        if (loadFailed) {
            Text(
                llm.status.ifBlank { "No se pudo cargar el modelo de IA." },
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
                val canTalk = llm.loaded && !llm.busy && !listener.thinking
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
                    llm.busy && llm.loaded -> "${teacher.name} está pensando…"
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
        appendLine("You are ${teacher.name}, a warm English teacher $origin. You are role-playing with a Spanish-speaking beginner (level ${scenario.level}).")
        appendLine("Situation: ${scenario.role}")
        appendLine("Rules:")
        appendLine("- Stay in character and REPLY to what the student said, as the character would. Write simple English (A1-A2 vocabulary), at most two short sentences, and end with a question or an invitation so the student keeps talking.")
        appendLine("- ALWAYS answer with at least one complete English sentence, even if the student's message is unclear: then ask them to repeat or clarify, in character. Never answer with nothing, and never repeat a sentence you already said.")
        appendLine("- If the student talks about something else, follow them naturally (answer their question, react), and bring the conversation back to the situation a little later. Do not ignore what they say.")
        appendLine("- The student's goals: ${scenario.targets.joinToString("; ")}. Gently steer the conversation so they get to use them.")
        appendLine("- If the student's message has a mistake typical of Spanish speakers, do two things: (1) inside your English reply, say the correct sentence out loud in a friendly way, like: You can say: '...'. (2) Then, on a separate final line starting with \"$CORRECTION_MARK\", explain it in Spanish in one short sentence (what they said, the correct form, why). Never put the correction line first, and never use the corrected sentence as your own reply. Only one correction per turn; if there is no real mistake, add nothing.")
        appendLine("- Traps to watch in this situation: ${scenario.watch.joinToString(" | ")}.")
        appendLine("- General traps: 'I have 25 years' (say 'I'm 25 years old'); 'he work' (he works); a missing article ('my sister is doctor'); 'I'm agree' (I agree); 'actually' does not mean 'actualmente'.")
        appendLine("- Never use lists, emojis, or the word CORRECCIÓN inside the English part. Do not translate your English into Spanish.")
        append("/no_think")
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
    llm: Llm,
    onPick: (Scenario) -> Unit,
    onBack: () -> Unit
) {
    val accent = Color(teacher.color)
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar("Conversar con ${teacher.name}", onBack = onBack)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f).padding(20.dp)
        ) {
            if (!llm.modelPresent()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BadRedSoft, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Text("Falta el modelo de IA", style = MaterialTheme.typography.labelLarge, color = BadRed)
                        Text(
                            "Se copia una vez por USB a:\n${llm.modelFile.absolutePath}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink
                        )
                    }
                }
            }
            item {
                Text(
                    "Situaciones cortas y cerradas. ${teacher.name} hace un papel, tú hablas, y ella te corrige en español lo que se te escapa.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft
                )
            }
            items(Course.scenarios) { sc ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Line, RoundedCornerShape(16.dp))
                        .clickable(enabled = llm.modelPresent()) { onPick(sc) }
                        .padding(16.dp)
                ) {
                    Text(sc.emoji, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.size(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(sc.title, style = MaterialTheme.typography.titleMedium)
                        Text(sc.goalEs, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                    }
                    Pill(sc.level, accent, Color(teacher.softColor))
                }
            }
        }
    }
}
