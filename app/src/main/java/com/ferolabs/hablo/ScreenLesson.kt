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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LessonScreen(
    lesson: Lesson,
    teacher: Teacher,
    listener: Listener,
    say: (String, Float) -> Unit,
    onFinish: (score: Int, correct: Int) -> Unit,
    onExit: () -> Unit
) {
    val accent = Color(teacher.color)
    val total = lesson.exercises.size

    // Red de seguridad: una lección vacía nunca debe reventar la app.
    if (total == 0) {
        LaunchedEffect(Unit) { onExit() }
        return
    }

    var index by remember { mutableStateOf(0) }
    var correctCount by remember { mutableStateOf(0) }
    var checked by remember { mutableStateOf(false) }
    var wasCorrect by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    // Estado de respuesta del ejercicio actual
    var chosen by remember { mutableStateOf(-1) }
    var typed by remember { mutableStateOf("") }
    // Guarda posiciones del banco de palabras, no las palabras, para soportar repetidas.
    var built by remember { mutableStateOf(listOf<Int>()) }
    var speakResult by remember { mutableStateOf<PronunciationResult?>(null) }

    // La frase a evaluar vive en estado, no se captura del ejercicio actual: el
    // lanzador de permisos tiene que declararse antes de cualquier return, y en
    // ese punto todavia no existe 'ex'.
    var speakTarget by remember { mutableStateOf("") }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val target = speakTarget
        if (granted && target.isNotBlank()) {
            listener.startRecording { heard ->
                speakResult = scorePronunciation(target, heard)
            }
        }
    }

    if (finished) {
        val score = if (total == 0) 0 else (correctCount * 100) / total
        ResultsScreen(
            teacher = teacher,
            score = score,
            correct = correctCount,
            total = total,
            say = say,
            onDone = { onFinish(score, correctCount) }
        )
        return
    }

    val ex = lesson.exercises[index]

    val bank = remember(index) {
        when (ex) {
            is Exercise.BuildSentence ->
                (ex.answer.split(" ") + ex.extraWords).shuffled()
            else -> emptyList()
        }
    }

    // Al entrar a un ejercicio de escucha, la profesora dice la frase sola.
    LaunchedEffect(index) {
        when (ex) {
            is Exercise.ListenChoose -> say(ex.audio, 1f)
            is Exercise.TypeWhatYouHear -> say(ex.audio, 1f)
            is Exercise.SpeakIt -> say(ex.text, 1f)
            else -> {}
        }
    }

    fun reset() {
        chosen = -1
        typed = ""
        built = listOf<Int>()
        speakResult = null
        checked = false
        wasCorrect = false
    }

    fun canCheck(): Boolean = when (ex) {
        is Exercise.ListenChoose -> chosen >= 0
        is Exercise.TranslateChoose -> chosen >= 0
        is Exercise.BuildSentence -> built.isNotEmpty()
        is Exercise.TypeWhatYouHear -> typed.isNotBlank()
        is Exercise.SpeakIt -> speakResult != null
    }

    fun evaluate(): Boolean = when (ex) {
        is Exercise.ListenChoose -> chosen == ex.answer
        is Exercise.TranslateChoose -> chosen == ex.answer
        is Exercise.BuildSentence ->
            normalizeAnswer(built.joinToString(" ") { bank[it] }) == normalizeAnswer(ex.answer)
        is Exercise.TypeWhatYouHear ->
            normalizeAnswer(typed) == normalizeAnswer(ex.audio)
        is Exercise.SpeakIt -> (speakResult?.percent ?: 0) >= 60
    }

    fun correctText(): String = when (ex) {
        is Exercise.ListenChoose -> ex.options[ex.answer]
        is Exercise.TranslateChoose -> ex.options[ex.answer]
        is Exercise.BuildSentence -> ex.answer
        is Exercise.TypeWhatYouHear -> ex.audio
        is Exercise.SpeakIt -> ex.text
    }

    Column(modifier = Modifier.fillMaxSize()) {

        TopBar(
            title = "${lesson.title}  ·  ${index + 1}/$total",
            onBack = onExit
        )

        LinearProgressIndicator(
            progress = { (index.toFloat()) / total.toFloat() },
            color = accent,
            trackColor = Line,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(7.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            when (ex) {

                is Exercise.ListenChoose -> {
                    Text("Escucha y elige lo que oíste", style = MaterialTheme.typography.titleLarge)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SpeakerButton(big = true, tint = accent) { say(ex.audio, 1f) }
                        SpeakerButton(slow = true, tint = accent.copy(alpha = 0.75f)) { say(ex.audio, 0.6f) }
                        Text(
                            "Toca para repetir.\nLa tortuga lo dice más lento.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSoft
                        )
                    }
                    ex.options.forEachIndexed { i, opt ->
                        OptionRow(opt, i, chosen, checked, ex.answer, accent) { if (!checked) chosen = i }
                    }
                }

                is Exercise.TranslateChoose -> {
                    Text("¿Cómo se dice en inglés?", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "\"${ex.es}\"",
                        style = MaterialTheme.typography.headlineMedium,
                        color = accent
                    )
                    ex.options.forEachIndexed { i, opt ->
                        OptionRow(opt, i, chosen, checked, ex.answer, accent) { if (!checked) chosen = i }
                    }
                }

                is Exercise.BuildSentence -> {
                    Text("Arma la frase tocando las palabras", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "\"${ex.es}\"",
                        style = MaterialTheme.typography.titleMedium,
                        color = accent
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 74.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .border(1.dp, Line, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        if (built.isEmpty()) {
                            Text(
                                "Tu respuesta aparece aquí",
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkSoft
                            )
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                built.forEachIndexed { position, bankIndex ->
                                    WordChip(bank[bankIndex], accent, filled = true) {
                                        if (!checked) {
                                            built = built.toMutableList()
                                                .also { it.removeAt(position) }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        bank.forEachIndexed { bankIndex, word ->
                            val used = built.contains(bankIndex)
                            WordChip(word, accent, filled = false, dimmed = used) {
                                if (!checked && !used) built = built + bankIndex
                            }
                        }
                    }
                }

                is Exercise.TypeWhatYouHear -> {
                    Text("Escribe lo que escuchas", style = MaterialTheme.typography.titleLarge)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpeakerButton(big = true, tint = accent) { say(ex.audio, 1f) }
                        SpeakerButton(slow = true, tint = accent.copy(alpha = 0.75f)) { say(ex.audio, 0.55f) }
                        Text(
                            "Significa: ${ex.meaningEs}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSoft
                        )
                    }
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { if (!checked) typed = it },
                        singleLine = true,
                        readOnly = checked,
                        placeholder = { Text("Escribe en inglés...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "No te preocupes por mayúsculas ni puntos.",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkSoft
                    )
                }

                is Exercise.SpeakIt -> {
                    Text("Dilo en voz alta", style = MaterialTheme.typography.titleLarge)
                    Text(
                        ex.text,
                        style = MaterialTheme.typography.headlineMedium,
                        color = accent
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpeakerButton(big = true, tint = accent) { say(ex.text, 1f) }
                        SpeakerButton(slow = true, tint = accent.copy(alpha = 0.75f)) { say(ex.text, 0.6f) }
                        Text(
                            "Escucha a ${teacher.name} y después dilo tú.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSoft
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(84.dp)
                                .background(if (listener.recording) BadRed else accent, CircleShape)
                                .clickable(enabled = !listener.thinking && !checked) {
                                    if (listener.recording) {
                                        listener.stopRecording()
                                    } else if (listener.hasMicPermission()) {
                                        speakResult = null
                                        listener.startRecording { heard ->
                                            speakResult = scorePronunciation(ex.text, heard)
                                        }
                                    } else {
                                        speakTarget = ex.text
                                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                        ) {
                            Text(
                                if (listener.recording) "■" else "🎤",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when {
                                listener.thinking -> "Analizando…"
                                listener.recording -> "Grabando… toca para terminar"
                                speakResult != null -> "Toca para intentarlo otra vez"
                                else -> "Toca y dilo"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (listener.recording) BadRed else InkSoft
                        )
                        if (listener.recording) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { listener.level },
                                color = BadRed,
                                trackColor = Line,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                            )
                        }
                    }

                    speakResult?.let { r ->
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            r.words.forEach { sw ->
                                val bg = when (sw.score) {
                                    WordScore.BIEN -> GoodGreenSoft
                                    WordScore.DUDOSO -> Color(0xFFFFF6E3)
                                    WordScore.MAL -> BadRedSoft
                                }
                                val fg = when (sw.score) {
                                    WordScore.BIEN -> GoodGreen
                                    WordScore.DUDOSO -> Color(0xFF8A5A00)
                                    WordScore.MAL -> BadRed
                                }
                                Text(
                                    sw.word,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = fg,
                                    modifier = Modifier
                                        .padding(vertical = 3.dp)
                                        .background(bg, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                        Text(
                            "${r.percent}%  ·  " + if (r.heard.isBlank()) "no te escuché"
                            else "entendí: \"${r.heard}\"",
                            style = MaterialTheme.typography.labelMedium,
                            color = InkSoft
                        )
                    }
                }
            }

            if (checked) {
                Spacer(Modifier.height(2.dp))
                FeedbackBox(
                    ok = wasCorrect,
                    correctText = correctText(),
                    teacher = teacher,
                    tip = ex.tip,
                    onReplay = { say(correctText(), 1f) }
                )
            }

            Spacer(Modifier.height(8.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Cream)
                .padding(20.dp)
        ) {
            if (!checked) {
                BigButton("Comprobar", enabled = canCheck(), container = accent) {
                    wasCorrect = evaluate()
                    if (wasCorrect) {
                        correctCount += 1
                        say(teacher.encouragement[index % teacher.encouragement.size], 1f)
                    } else {
                        say(correctText(), 0.85f)
                    }
                    checked = true
                }
            } else {
                BigButton(
                    text = if (index + 1 < total) "Continuar" else "Ver resultado",
                    container = if (wasCorrect) GoodGreen else accent
                ) {
                    if (index + 1 < total) {
                        index += 1
                        reset()
                    } else {
                        finished = true
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    text: String,
    i: Int,
    chosen: Int,
    checked: Boolean,
    answer: Int,
    accent: Color,
    onClick: () -> Unit
) {
    val isChosen = chosen == i
    val bg: Color
    val border: Color
    if (checked) {
        when {
            i == answer -> { bg = GoodGreenSoft; border = GoodGreen }
            isChosen -> { bg = BadRedSoft; border = BadRed }
            else -> { bg = Color.White; border = Line }
        }
    } else {
        bg = if (isChosen) accent.copy(alpha = 0.10f) else Color.White
        border = if (isChosen) accent else Line
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(14.dp))
            .border(if (isChosen || (checked && i == answer)) 2.dp else 1.dp, border, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 15.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (checked && i == answer) Text("✓", color = GoodGreen, style = MaterialTheme.typography.titleMedium)
        if (checked && isChosen && i != answer) Text("✕", color = BadRed, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun WordChip(
    word: String,
    accent: Color,
    filled: Boolean,
    dimmed: Boolean = false,
    onClick: () -> Unit
) {
    Text(
        text = word,
        style = MaterialTheme.typography.bodyLarge,
        color = when {
            dimmed -> Line
            filled -> Color.White
            else -> Ink
        },
        modifier = Modifier
            .padding(vertical = 4.dp)
            .background(
                when {
                    dimmed -> Color(0xFFF6F2ED)
                    filled -> accent
                    else -> Color.White
                },
                RoundedCornerShape(10.dp)
            )
            .border(1.dp, if (filled) accent else Line, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 9.dp)
    )
}

@Composable
private fun FeedbackBox(
    ok: Boolean,
    correctText: String,
    teacher: Teacher,
    tip: String?,
    onReplay: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (ok) GoodGreenSoft else BadRedSoft, RoundedCornerShape(14.dp))
                .border(1.dp, if (ok) GoodGreen.copy(alpha = 0.4f) else BadRed.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (ok) "¡Correcto!" else "Casi",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (ok) GoodGreen else BadRed,
                    modifier = Modifier.weight(1f)
                )
                SpeakerButton(tint = if (ok) GoodGreen else BadRed, onClick = onReplay)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (ok) correctText else "La respuesta correcta es: $correctText",
                style = MaterialTheme.typography.bodyLarge,
                color = Ink
            )
            if (!ok) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${teacher.name} lo repitió despacio para ti.",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSoft
                )
            }
        }
        if (tip != null) TipBox(tip)
    }
}

@Composable
private fun ResultsScreen(
    teacher: Teacher,
    score: Int,
    correct: Int,
    total: Int,
    say: (String, Float) -> Unit,
    onDone: () -> Unit
) {
    val accent = Color(teacher.color)
    val phrase = when {
        score >= 90 -> "Excellent work! I'm proud of you."
        score >= 60 -> "Good job. You're making progress."
        else -> "That's okay. Let's try it again together."
    }

    LaunchedEffect(Unit) { say(phrase, 1f) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp)
    ) {
        Text(teacher.emoji, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "$score%",
            style = MaterialTheme.typography.headlineLarge,
            color = if (score >= 60) GoodGreen else accent
        )
        Text(
            "$correct de $total correctas",
            style = MaterialTheme.typography.bodyLarge,
            color = InkSoft
        )
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(teacher.softColor), RoundedCornerShape(14.dp))
                .padding(18.dp)
        ) {
            Text(
                "\"$phrase\"\n— ${teacher.name}",
                style = MaterialTheme.typography.bodyLarge,
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(24.dp))
        BigButton("Volver al inicio", container = accent) { onDone() }
    }
}
