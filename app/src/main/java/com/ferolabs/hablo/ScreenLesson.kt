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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LessonScreen(
    lesson: Lesson,
    teacher: Teacher,
    listener: Listener,
    progreso: Progreso,
    speaking: Boolean,
    /** Cuánto duró lo último que dijo la profesora (para el shadowing). */
    lastSpokenSeconds: () -> Float,
    showFace: Boolean,
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

    // Cola de ejercicios en vez de una lista recorrida de principio a fin: lo
    // que se falla vuelve al final y se repite hasta acertarlo (recuperación
    // con retroalimentación, que es donde está el efecto: g = 0,73 con
    // retroalimentación contra 0,39 sin ella, Rowland 2014).
    val queue = remember { mutableStateListOf<Int>().also { it.addAll(lesson.exercises.indices) } }
    val repeated = remember { HashSet<Int>() }
    var pos by remember { mutableStateOf(0) }
    val index = queue[pos.coerceIn(0, queue.size - 1)]
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
    var speakReport by remember { mutableStateOf<SoundReport?>(null) }
    var speakNotHeard by remember { mutableStateOf<NotHeardReason?>(null) }
    // Shadowing: cuánto tardó él y cuánto ella, como dato, no como nota.
    var studentSeconds by remember { mutableStateOf(0f) }
    var teacherSeconds by remember { mutableStateOf(0f) }

    // El modelo de fonemas solo hace falta si la lección tiene ejercicios de
    // hablar con sonido evaluable; se suelta al salir.
    val needsSounds = lesson.exercises.any { it is Exercise.SpeakIt && it.sound != Sound.GENERAL }
    LaunchedEffect(Unit) { if (needsSounds) listener.prepareSounds() }
    DisposableEffect(Unit) { onDispose { if (needsSounds) listener.releaseSounds() } }

    // Si no se pudo evaluar, speakResult queda en null: "Comprobar" sigue
    // apagado y el intento no cuenta ni a favor ni en contra.
    fun listen(target: String, sound: Sound) {
        listener.startRecording(target, sound) { r ->
            when (r) {
                is ListenResult.Heard -> {
                    speakResult = scorePronunciation(target, r.text)
                    speakReport = r.report
                    speakNotHeard = null
                    studentSeconds = r.speechSeconds
                }
                is ListenResult.NotHeard -> {
                    speakResult = null
                    speakReport = null
                    speakNotHeard = r.reason
                }
            }
        }
    }

    // La frase a evaluar vive en estado, no se captura del ejercicio actual: el
    // lanzador de permisos tiene que declararse antes de cualquier return, y en
    // ese punto todavia no existe 'ex'.
    var speakTarget by remember { mutableStateOf("") }
    var speakSound by remember { mutableStateOf(Sound.GENERAL) }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val target = speakTarget
        if (granted && target.isNotBlank()) listen(target, speakSound)
    }

    if (finished) {
        val score = if (total == 0) 0 else (correctCount * 100) / total
        ResultsScreen(
            teacher = teacher,
            speaking = speaking,
            showFace = showFace,
            score = score,
            correct = correctCount,
            total = total,
            say = say,
            onDone = { onFinish(score, correctCount) }
        )
        return
    }

    val ex = lesson.exercises[index]

    val bank = remember(pos) {
        when (ex) {
            is Exercise.BuildSentence ->
                (ex.answer.split(" ") + ex.extraWords).shuffled()
            else -> emptyList()
        }
    }

    // Al entrar a un ejercicio de escucha, la profesora dice la frase sola.
    LaunchedEffect(pos) {
        when (ex) {
            is Exercise.ListenChoose -> say(ex.audio, 1f)
            is Exercise.TypeWhatYouHear -> say(ex.audio, 1f)
            is Exercise.SpeakIt -> say(ex.text, 1f)
            is Exercise.Shadow -> say(ex.text, 1f)
            // El par mínimo: suena la palabra, NO se muestra cuál fue. Va dentro de
            // una portadora neutra porque Piper con una palabra suelta de medio
            // segundo es inestable: medido con las cuatro voces y el modelo de
            // fonemas (tools/content/oir_pares.py), el fonema distintivo del par
            // aparece en 63 de 96 casos con la palabra sola y en 78 de 96 con
            // "The word is ___.". La portadora no da pistas: es la misma siempre.
            is Exercise.MinimalPair -> say(carrier(ex.answer), 1f)
            else -> {}
        }
    }

    fun reset() {
        chosen = -1
        typed = ""
        built = listOf<Int>()
        speakResult = null
        speakReport = null
        speakNotHeard = null
        studentSeconds = 0f
        teacherSeconds = 0f
        checked = false
        wasCorrect = false
    }

    fun canCheck(): Boolean = when (ex) {
        is Exercise.ListenChoose -> chosen >= 0
        is Exercise.TranslateChoose -> chosen >= 0
        is Exercise.BuildSentence -> built.isNotEmpty()
        is Exercise.TypeWhatYouHear -> typed.isNotBlank()
        is Exercise.SpeakIt -> speakResult != null
        is Exercise.WriteIt -> typed.isNotBlank()
        is Exercise.Cloze -> typed.isNotBlank()
        is Exercise.Shadow -> speakResult != null
        is Exercise.MinimalPair -> chosen >= 0
    }

    fun evaluate(): Boolean = when (ex) {
        is Exercise.ListenChoose -> chosen == ex.answerIndex
        is Exercise.TranslateChoose -> chosen == ex.answerIndex
        is Exercise.BuildSentence ->
            normalizeAnswer(built.joinToString(" ") { bank[it] }) == normalizeAnswer(ex.answer)
        is Exercise.TypeWhatYouHear ->
            normalizeAnswer(typed) == normalizeAnswer(ex.audio)
        is Exercise.SpeakIt -> (speakResult?.percent ?: 0) >= 60
        is Exercise.WriteIt -> Correccion.acepta(typed, ex.answer, ex.accept)
        is Exercise.Cloze -> Correccion.acepta(typed, ex.answer, ex.accept)
        // Shadowing: que salgan las palabras. El ritmo se muestra, no se califica.
        is Exercise.Shadow -> (speakResult?.percent ?: 0) >= 60
        is Exercise.MinimalPair -> chosen == ex.answerIndex
    }

    /** Qué falló, en español, para write y cloze. Null si no hay nada concreto que decir. */
    fun diagnosis(): String? = when (ex) {
        is Exercise.WriteIt -> Correccion.diagnostico(typed, ex.answer, ex.accept)
        is Exercise.Cloze -> Correccion.diagnostico(typed, ex.answer, ex.accept)
        else -> null
    }

    /** Lo que respondió el alumno, para el cuaderno de errores. */
    fun givenText(): String = when (ex) {
        is Exercise.ListenChoose -> ex.options.getOrElse(chosen) { "" }
        is Exercise.TranslateChoose -> ex.options.getOrElse(chosen) { "" }
        is Exercise.BuildSentence -> built.joinToString(" ") { bank[it] }
        is Exercise.TypeWhatYouHear -> typed
        is Exercise.SpeakIt -> speakResult?.heard ?: ""
        is Exercise.WriteIt -> typed
        is Exercise.Cloze -> typed
        is Exercise.Shadow -> speakResult?.heard ?: ""
        is Exercise.MinimalPair -> ex.options.getOrElse(chosen) { "" }
    }

    fun correctText(): String = when (ex) {
        is Exercise.ListenChoose -> ex.answer
        is Exercise.TranslateChoose -> ex.answer
        is Exercise.BuildSentence -> ex.answer
        is Exercise.TypeWhatYouHear -> ex.audio
        is Exercise.SpeakIt -> ex.text
        is Exercise.WriteIt -> ex.answer
        is Exercise.Cloze -> ex.full
        is Exercise.Shadow -> ex.text
        is Exercise.MinimalPair -> ex.answer
    }

    Column(modifier = Modifier.fillMaxSize()) {

        TopBar(
            title = "${lesson.title}  ·  ${pos + 1}/${queue.size}",
            onBack = onExit
        )

        LinearProgressIndicator(
            progress = { pos.toFloat() / queue.size.toFloat() },
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
                    // Orden nuevo en cada ejercicio: si no, se aprueba tocando
                    // siempre la primera casilla sin saber inglés.
                    val order = remember(pos) { ex.options.indices.shuffled() }
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
                    order.forEach { i ->
                        OptionRow(ex.options[i], i, chosen, checked, ex.answerIndex, accent) { if (!checked) chosen = i }
                    }
                }

                is Exercise.TranslateChoose -> {
                    val order = remember(pos) { ex.options.indices.shuffled() }
                    Text("¿Cómo se dice en inglés?", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "\"${ex.es}\"",
                        style = MaterialTheme.typography.headlineMedium,
                        color = accent
                    )
                    order.forEach { i ->
                        OptionRow(ex.options[i], i, chosen, checked, ex.answerIndex, accent) { if (!checked) chosen = i }
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
                                        speakReport = null
                                        speakNotHeard = null
                                        listen(ex.text, ex.sound)
                                    } else {
                                        speakTarget = ex.text
                                        speakSound = ex.sound
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
                                speakResult != null || speakNotHeard != null -> "Toca para intentarlo otra vez"
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

                    speakNotHeard?.let { NotHeardBox(it) }

                    speakReport?.let { SoundVerdictCard(it) }

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
                            "${r.percent}%  ·  entendí: \"${r.heard}\"",
                            style = MaterialTheme.typography.labelMedium,
                            color = InkSoft
                        )
                    }
                }

                is Exercise.WriteIt -> {
                    Text("Escríbelo en inglés", style = MaterialTheme.typography.titleLarge)
                    Text(ex.es, style = MaterialTheme.typography.headlineSmall, color = Ink)
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { if (!checked) typed = it },
                        singleLine = true,
                        readOnly = checked,
                        placeholder = { Text("Escribe la frase en inglés...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Sin opciones ni fichas: escribirlo de memoria es lo que más se queda. " +
                            "No te preocupes por mayúsculas ni puntos.",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkSoft
                    )
                }

                is Exercise.Cloze -> {
                    Text("Completa el hueco", style = MaterialTheme.typography.titleLarge)
                    Text(
                        ex.before + "______" + ex.after,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Ink
                    )
                    Text("Significa: ${ex.es}", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { if (!checked) typed = it },
                        singleLine = true,
                        readOnly = checked,
                        placeholder = { Text("Lo que va en el hueco...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is Exercise.Shadow -> {
                    Text("Repite con ${teacher.name}", style = MaterialTheme.typography.titleLarge)
                    Text(ex.text, style = MaterialTheme.typography.headlineMedium, color = accent)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpeakerButton(big = true, tint = accent) { say(ex.text, 1f) }
                        Text(
                            "Óyela y repítela ENSEGUIDA, siguiendo su ritmo, sin pausas. " +
                                "Aquí no se juzga cada sonido: se practica el ritmo.",
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
                                        speakReport = null
                                        speakNotHeard = null
                                        teacherSeconds = lastSpokenSeconds()
                                        listen(ex.text, Sound.GENERAL)
                                    } else {
                                        speakTarget = ex.text
                                        speakSound = Sound.GENERAL
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
                                speakResult != null || speakNotHeard != null -> "Toca para intentarlo otra vez"
                                else -> "Toca y repítela"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (listener.recording) BadRed else InkSoft
                        )
                    }

                    speakNotHeard?.let { NotHeardBox(it) }

                    speakResult?.let { r ->
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            r.words.forEach { sw ->
                                val ok = sw.score == WordScore.BIEN
                                Text(
                                    sw.word,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (ok) GoodGreen else Color(0xFF8A5A00),
                                    modifier = Modifier
                                        .padding(vertical = 3.dp)
                                        .background(if (ok) GoodGreenSoft else Color(0xFFFFF6E3), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                        // Ritmo: un dato, no una nota. Nada de inventar un porcentaje.
                        if (teacherSeconds > 0f && studentSeconds > 0f) {
                            val ratio = studentSeconds / teacherSeconds
                            Text(
                                "Tú: %.1f s · %s: %.1f s — %s".format(
                                    java.util.Locale("es"),
                                    studentSeconds, teacher.name, teacherSeconds,
                                    when {
                                        ratio <= 1.15f -> "vas a su ritmo."
                                        ratio <= 1.5f -> "vas bien, pégate más a su ritmo."
                                        else -> "más seguido, sin pausas entre palabras."
                                    }
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkSoft
                            )
                        }
                    }
                }

                is Exercise.MinimalPair -> {
                    val order = remember(pos) { ex.options.indices.shuffled() }
                    Text("¿Cuál palabra oíste?", style = MaterialTheme.typography.titleLarge)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpeakerButton(big = true, tint = accent) { say(carrier(ex.answer), 1f) }
                        SpeakerButton(slow = true, tint = accent.copy(alpha = 0.75f)) { say(carrier(ex.answer), 0.7f) }
                        Text(
                            "${teacher.name} dice UNA de estas. Toca la que oíste.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSoft
                        )
                    }
                    order.forEach { i ->
                        OptionRow(ex.options[i], i, chosen, checked, ex.answerIndex, accent) { if (!checked) chosen = i }
                    }
                    if (checked && ex.sentence != null) {
                        Text(
                            "Óyela en una frase →  ${ex.sentence}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = accent,
                            modifier = Modifier.clickable { say(ex.sentence, 1f) }
                        )
                    }
                    if (checked) {
                        Text(
                            "Entrenar el oído mejora la boca, pero no del todo: después de oír, dilo tú.",
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
                    detail = if (wasCorrect) null else diagnosis(),
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
                    progreso.anotarActividad(Progreso.Actividad.EJERCICIO)
                    wasCorrect = evaluate()
                    if (wasCorrect) {
                        // Solo puntúa el primer intento: lo repetido no infla la nota.
                        if (index !in repeated) correctCount += 1
                        say(teacher.encouragement[pos % teacher.encouragement.size], 1f)
                    } else {
                        // Vuelve una sola vez, al final de la lección.
                        if (repeated.add(index)) queue.add(index)
                        progreso.anotarFallo(lesson.id, tipoDe(ex), givenText(), correctText(), ex.id)
                        say(correctText(), 0.85f)
                    }
                    checked = true
                }
            } else {
                BigButton(
                    text = if (pos + 1 < queue.size) "Continuar" else "Ver resultado",
                    container = if (wasCorrect) GoodGreen else accent
                ) {
                    if (pos + 1 < queue.size) {
                        pos += 1
                        reset()
                    } else {
                        progreso.anotarActividad(Progreso.Actividad.LECCION)
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
    /** Qué falló exactamente ("te faltó la -s de works"); es la mitad del efecto de corregir. */
    detail: String? = null,
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
            if (!ok && detail != null) {
                Spacer(Modifier.height(6.dp))
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = BadRed)
            }
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
    speaking: Boolean,
    showFace: Boolean,
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
        TeacherAvatar(teacher = teacher, speaking = speaking, size = 120.dp, showFace = showFace)
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

/** Nombre corto del tipo de ejercicio, para el informe. */
private fun tipoDe(ex: Exercise): String = when (ex) {
    is Exercise.ListenChoose -> "escuchar"
    is Exercise.TranslateChoose -> "traducir"
    is Exercise.BuildSentence -> "armar"
    is Exercise.TypeWhatYouHear -> "dictado"
    is Exercise.SpeakIt -> "hablar"
    is Exercise.WriteIt -> "escribir"
    is Exercise.Cloze -> "completar"
    is Exercise.Shadow -> "repetir"
    is Exercise.MinimalPair -> "oído"
}

/** La frase portadora con la que suena la palabra de un par mínimo. La misma que mide `tools/content/oir_pares.py`. */
private fun carrier(word: String) = "The word is $word."
