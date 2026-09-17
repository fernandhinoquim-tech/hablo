package com.ferolabs.hablo

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import com.ferolabs.hablo.ejercicios.ArmaLaFrase
import com.ferolabs.hablo.ejercicios.CompletaElHueco
import com.ferolabs.hablo.ejercicios.CorrigeTuError
import com.ferolabs.hablo.ejercicios.CualPalabraOiste
import com.ferolabs.hablo.ejercicios.DiloEnVozAlta
import com.ferolabs.hablo.ejercicios.EscribeLoQueEscuchas
import com.ferolabs.hablo.ejercicios.EscribeloEnIngles
import com.ferolabs.hablo.ejercicios.EscuchaYElige
import com.ferolabs.hablo.ejercicios.RepiteCon
import com.ferolabs.hablo.ejercicios.TraduceEligiendo

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LessonScreen(
    lesson: Lesson,
    teacher: Teacher,
    listener: Listener,
    progreso: Progreso,
    store: Store,
    speaking: Boolean,
    /** Cuánto duró lo último que dijo la profesora (para el shadowing). */
    lastSpokenSeconds: () -> Float,
    showFace: Boolean,
    say: (String, Float) -> Unit,
    onFinish: (score: Int, correct: Int) -> Unit,
    onExit: () -> Unit,
    /** Lección del curso, repaso del mazo o "Aguanta" (etapa 3). */
    modo: ModoLeccion = ModoLeccion.LECCION,
    /** Cada respuesta comprobada: el ejercicio, si acertó y si era el primer intento de la sesión. */
    onAnswered: (Exercise, Boolean, Boolean) -> Unit = { _, _, _ -> },
    /** Solo AGUANTA: al llegar a tantos errores se acaba. */
    maxErrores: Int = 3,
    /** Solo AGUANTA: la marca anterior, para "tu marca es 11". */
    marca: Int = 0,
    /** Solo LECCION, la primera vez: "adivina antes de ver". */
    adivinanzas: List<Exercise.WriteIt> = emptyList()
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

    // El juego de Parejas a mitad de la lección, con las frases de la misma
    // lección. No cuenta para la nota; rompe la rutina. Solo si hay parejas.
    val parejas = remember { if (modo == ModoLeccion.LECCION) parejasDe(lesson) else emptyList() }
    val gameAt = remember { if (parejas.size >= 3) lesson.exercises.size / 2 else -1 }
    // Aguanta: se corta al tercer error. "Llegaste a N" son los aciertos hasta ahí.
    var errores by remember { mutableStateOf(0) }
    val terminaAqui = modo == ModoLeccion.AGUANTA && errores >= maxErrores
    // Adivina antes de ver (solo la primera vez que se abre una lección).
    var adIdx by remember { mutableStateOf(0) }
    var adTyped by remember { mutableStateOf("") }
    var adChecked by remember { mutableStateOf(false) }
    val adivinando = modo == ModoLeccion.LECCION && adIdx < adivinanzas.size
    // La ficha de teoría: al abrir la lección (tras las adivinanzas) y cuando él la pida con el 📖.
    var fichaVista by remember { mutableStateOf(false) }
    var fichaAbierta by remember { mutableStateOf(false) }
    val mostrandoFicha = modo == ModoLeccion.LECCION && lesson.theory.body.isNotBlank() && (!fichaVista || fichaAbierta)
    // "¿Por qué?" al fallar: la ficha de la lección de la que sale el ejercicio (también en el
    // repaso y en Aguanta, donde la lección es sintética). Corrección explicada g = 0,73;
    // sin explicación 0,39 (Rowland 2014): estaba en el plan del 13-09 y no se había construido.
    var fichaPorQue by remember { mutableStateOf<Lesson?>(null) }
    var gamePlayed by remember { mutableStateOf(false) }
    var gameDone by remember { mutableStateOf(false) }
    val showingGame = pos == gameAt && !gamePlayed
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
    var showHeard by remember { mutableStateOf(false) }
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
            lesson = lesson,
            teacher = teacher,
            speaking = speaking,
            showFace = showFace,
            score = score,
            correct = correctCount,
            total = total,
            say = say,
            modo = modo,
            marca = marca,
            onDone = { onFinish(score, correctCount) }
        )
        return
    }

    if (!adivinando && mostrandoFicha) {
        FichaScreen(
            lesson = lesson,
            accent = accent,
            primeraVez = !fichaVista,
            onDone = { fichaVista = true; fichaAbierta = false },
            onBack = { if (fichaVista) fichaAbierta = false else onExit() }
        )
        return
    }
    fichaPorQue?.let { l ->
        BackHandler { fichaPorQue = null }
        FichaScreen(lesson = l, accent = accent, primeraVez = false, onDone = { fichaPorQue = null }, onBack = { fichaPorQue = null }, textoBoton = "Volver al ejercicio")
        return
    }

    if (adivinando) {
        AdivinaScreen(
            lesson = lesson,
            ejercicio = adivinanzas[adIdx],
            numero = adIdx + 1,
            total = adivinanzas.size,
            typed = adTyped,
            checked = adChecked,
            accent = accent,
            onTyped = { adTyped = it },
            onCheck = { adChecked = true },
            onNext = { adIdx += 1; adTyped = ""; adChecked = false },
            onExit = onExit
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
    LaunchedEffect(pos, gamePlayed) {
        if (showingGame) return@LaunchedEffect
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
            is Exercise.MinimalPair -> say(ex.spoken, 1f)
            else -> {}
        }
    }

    fun reset() {
        chosen = -1
        typed = ""
        built = listOf<Int>()
        speakResult = null
        speakReport = null
        showHeard = false
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
        is Exercise.FixIt -> typed.isNotBlank()
    }

    fun evaluate(): Boolean = when (ex) {
        is Exercise.ListenChoose -> chosen == ex.answerIndex
        is Exercise.TranslateChoose -> chosen == ex.answerIndex
        // Armar: la variante contraída o larga vale igual (los señuelos pueden traerla).
        is Exercise.BuildSentence -> Correccion.acepta(built.joinToString(" ") { bank[it] }, ex.answer, ex.accept)
        // Dictado: contracciones y números valen igual ("doesn't" = "does not", "4" = "four").
        is Exercise.TypeWhatYouHear -> Correccion.acepta(typed, ex.audio, ex.accept)
        is Exercise.SpeakIt -> speakResult?.entendida == true
        is Exercise.WriteIt -> Correccion.acepta(typed, ex.answer, ex.accept)
        // Vale la palabra del hueco sola o la frase completa (la pantalla la muestra como "la correcta").
        is Exercise.Cloze -> Correccion.aceptaHueco(typed, ex.before, ex.after, ex.answer, ex.accept)
        // Shadowing: que salgan las palabras. El ritmo se muestra, no se califica.
        is Exercise.Shadow -> speakResult?.entendida == true
        is Exercise.MinimalPair -> chosen == ex.answerIndex
        is Exercise.FixIt -> ex.hueco?.let { Correccion.aceptaHueco(typed, it.before, it.after, ex.answer, ex.accept) }
            ?: Correccion.acepta(typed, ex.answer, ex.accept)
    }

    /** Qué falló, en español, para dictado, write y cloze. Null si no hay nada concreto que decir. */
    fun diagnosis(): String? = when (ex) {
        is Exercise.TypeWhatYouHear -> Correccion.diagnostico(typed, ex.audio, ex.accept)
        is Exercise.WriteIt -> Correccion.diagnostico(typed, ex.answer, ex.accept)
        is Exercise.Cloze -> Correccion.diagnosticoHueco(typed, ex.before, ex.after, ex.answer, ex.accept)
        is Exercise.FixIt -> ex.hueco?.let { Correccion.diagnosticoHueco(typed, it.before, it.after, ex.answer, ex.accept) }
            ?: Correccion.diagnostico(typed, ex.answer, ex.accept)
        else -> null
    }

    /** Aviso sin castigo cuando acertó de una forma que conviene comentar. */
    fun note(): String? = when (ex) {
        is Exercise.Cloze ->
            if (Correccion.escribioLaFrase(typed, ex.before, ex.after, ex.answer, ex.accept))
                "Bastaba con escribir «${ex.answer}», pero la frase entera está perfecta."
            else null
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
        is Exercise.FixIt -> typed
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
        is Exercise.FixIt -> ex.hueco?.full ?: ex.answer
    }

    Column(modifier = Modifier.fillMaxSize()) {

        TopBar(
            title = if (modo == ModoLeccion.AGUANTA) "${lesson.title}  ·  llevas $correctCount · errores $errores de $maxErrores"
                else "${lesson.title}  ·  ${pos + 1}/${queue.size}",
            onBack = onExit,
            trailing = if (modo == ModoLeccion.LECCION && lesson.theory.body.isNotBlank()) ({
                Text(
                    "📖",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.clickable { fichaAbierta = true }.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }) else null
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
            if (showingGame) {
                ParejasGame(parejas, accent) { _, _, _ -> gameDone = true }
            } else when (ex) {
                is Exercise.ListenChoose -> EscuchaYElige(ex, pos, chosen, checked, accent, say) { chosen = it }
                is Exercise.TranslateChoose -> TraduceEligiendo(ex, pos, chosen, checked, accent) { chosen = it }
                is Exercise.BuildSentence -> ArmaLaFrase(ex, bank, built, checked, accent) { built = it }
                is Exercise.TypeWhatYouHear -> EscribeLoQueEscuchas(ex, typed, checked, accent, say) { typed = it }
                is Exercise.SpeakIt -> DiloEnVozAlta(
                    ex = ex, teacher = teacher, listener = listener,
                    speakResult = speakResult, speakReport = speakReport, speakNotHeard = speakNotHeard,
                    showHeard = showHeard, checked = checked, accent = accent, say = say,
                    onMic = {
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
                    },
                    onShowHeard = { showHeard = true }
                )
                is Exercise.WriteIt -> EscribeloEnIngles(ex, typed, checked) { typed = it }
                is Exercise.FixIt -> CorrigeTuError(ex, typed, checked) { typed = it }
                is Exercise.Cloze -> CompletaElHueco(ex, typed, checked) { typed = it }
                is Exercise.Shadow -> RepiteCon(
                    ex = ex, teacher = teacher, listener = listener,
                    speakResult = speakResult, speakNotHeard = speakNotHeard, checked = checked,
                    teacherSeconds = teacherSeconds, studentSeconds = studentSeconds, accent = accent, say = say,
                    onMic = {
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
                )
                is Exercise.MinimalPair -> CualPalabraOiste(ex, pos, chosen, checked, teacher, accent, say) { chosen = it }
            }

            if (checked && !showingGame) {
                Spacer(Modifier.height(2.dp))
                val origen = if (modo == ModoLeccion.LECCION) lesson else Course.lessonOfExercise(ex.id)
                FeedbackBox(
                    ok = wasCorrect,
                    correctText = correctText(),
                    detail = if (wasCorrect) note() else diagnosis(),
                    teacher = teacher,
                    tip = ex.tip,
                    speaking = ex is Exercise.SpeakIt || ex is Exercise.Shadow,
                    onReplay = { say(correctText(), 1f) },
                    porQue = if (!wasCorrect && origen != null && origen.theory.body.isNotBlank()) origen else null,
                    onPorQue = { fichaPorQue = it }
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
            if (showingGame) {
                BigButton(
                    text = if (gameDone) "Seguir con la lección" else "Une todas las parejas",
                    enabled = gameDone,
                    container = GoodGreen
                ) {
                    gamePlayed = true
                }
            } else if (!checked) {
                BigButton("Comprobar", enabled = canCheck(), container = accent) {
                    progreso.anotarActividad(Progreso.Actividad.EJERCICIO)
                    // Lo hablado en la lección cuenta igual que en Pronunciación: diario,
                    // informe y mapa de sonidos (antes solo contaban los drills).
                    if (ex is Exercise.SpeakIt || ex is Exercise.Shadow) {
                        progreso.anotarActividad(Progreso.Actividad.INTENTO)
                    }
                    if (ex is Exercise.SpeakIt) {
                        val rep = speakReport?.fiable(speakResult)
                        progreso.anotarIntento(ex.text, ex.sound, rep)
                        rep?.let { store.recordSound(it.sound, it.worst) }
                    }
                    wasCorrect = evaluate()
                    val primerIntento = index !in repeated
                    if (wasCorrect) {
                        // Solo puntúa el primer intento: lo repetido no infla la nota.
                        if (primerIntento) correctCount += 1
                        say(teacher.encouragement[pos % teacher.encouragement.size], 1f)
                    } else {
                        if (modo == ModoLeccion.AGUANTA) {
                            errores += 1
                        } else {
                            // Vuelve una sola vez, al final de la lección.
                            if (repeated.add(index)) queue.add(index)
                        }
                        progreso.anotarFallo(lesson.id, tipoDe(ex), givenText(), correctText(), ex.id)
                        say(correctText(), 0.85f)
                    }
                    onAnswered(ex, wasCorrect, primerIntento)
                    checked = true
                }
            } else {
                BigButton(
                    text = if (pos + 1 < queue.size && !terminaAqui) "Continuar" else "Ver resultado",
                    container = if (wasCorrect) GoodGreen else accent
                ) {
                    if (pos + 1 < queue.size && !terminaAqui) {
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
fun OptionRow(
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
fun WordChip(
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
    /** Ejercicio de hablar: no hay "respuesta correcta" que enseñar, la frase ya está en pantalla. */
    speaking: Boolean = false,
    onReplay: () -> Unit,
    porQue: Lesson? = null,
    onPorQue: (Lesson) -> Unit = {}
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
                    if (ok) "¡Correcto!" else if (speaking) "No se te entendió del todo" else "Casi",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (ok) GoodGreen else BadRed,
                    modifier = Modifier.weight(1f)
                )
                SpeakerButton(tint = if (ok) GoodGreen else BadRed, onClick = onReplay)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    ok -> correctText
                    speaking -> "Óyela otra vez y vuelve a intentarlo: $correctText"
                    else -> "La respuesta correcta es: $correctText"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = Ink
            )
            if (detail != null) {
                Spacer(Modifier.height(6.dp))
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = if (ok) InkSoft else BadRed)
            }
            if (!ok) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${teacher.name} lo repitió despacio para ti.",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSoft
                )
            }
            if (porQue != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "📖 ¿Por qué?  →  ${porQue.theory.title}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF8A5A00),
                    modifier = Modifier
                        .background(Color(0xFFFFF6E3), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFFF0DFB9), RoundedCornerShape(10.dp))
                        .clickable { onPorQue(porQue) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        if (tip != null) TipBox(tip)
    }
}

@Composable
private fun ResultsScreen(
    lesson: Lesson,
    teacher: Teacher,
    speaking: Boolean,
    showFace: Boolean,
    score: Int,
    correct: Int,
    total: Int,
    say: (String, Float) -> Unit,
    modo: ModoLeccion = ModoLeccion.LECCION,
    marca: Int = 0,
    onDone: () -> Unit
) {
    val accent = Color(teacher.color)
    val phrase = when {
        modo == ModoLeccion.AGUANTA && correct > marca && marca > 0 -> "A new record! Well done."
        modo == ModoLeccion.AGUANTA -> "Good effort. Let's see how far you get next time."
        score >= 90 -> "Excellent work! I'm proud of you."
        score >= 60 -> "Good job. You're making progress."
        else -> "That's okay. Let's try it again together."
    }
    // Con 56 lecciones en dos niveles hay que decir cuál fue, si se aprobó y qué sigue.
    val unit = Course.unitOfLesson(lesson.id)
    val level = unit?.let { Course.levelOfUnit(it.id) }
    val todas = Course.allLessons()
    val siguiente = todas.getOrNull(todas.indexOfFirst { it.id == lesson.id } + 1)
    val aprobada = score >= 60

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
            listOfNotNull(level?.id, unit?.title, lesson.title).joinToString(" · "),
            style = MaterialTheme.typography.labelLarge,
            color = InkSoft,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        when (modo) {
            // Aguanta: sin puntos. "Llegaste a 14; tu marca es 11."
            ModoLeccion.AGUANTA -> {
                Text("Llegaste a $correct", style = MaterialTheme.typography.headlineLarge, color = accent)
                Text(
                    when {
                        marca == 0 -> "Es tu primera marca."
                        correct > marca -> "Tu marca era $marca: la superaste."
                        else -> "Tu marca es $marca."
                    },
                    style = MaterialTheme.typography.bodyLarge, color = InkSoft, textAlign = TextAlign.Center
                )
            }
            // Repaso: lo fallado vuelve mañana, lo acertado se aleja.
            ModoLeccion.REPASO -> {
                Text("$correct de $total", style = MaterialTheme.typography.headlineLarge, color = if (aprobada) GoodGreen else accent)
                Text(
                    if (correct == total) "Todo bien: esas frases se alejan unos días." else "Lo que fallaste vuelve mañana; lo demás se aleja unos días.",
                    style = MaterialTheme.typography.bodyLarge, color = InkSoft, textAlign = TextAlign.Center
                )
            }
            ModoLeccion.LECCION -> {
                Text(
                    "$score%",
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (aprobada) GoodGreen else accent
                )
                Text(
                    "$correct de $total correctas · " + (if (aprobada) "lección aprobada ✓" else "se aprueba con 60: vuelve a intentarla"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (aprobada) GoodGreen else InkSoft,
                    textAlign = TextAlign.Center
                )
            }
        }
        if (modo == ModoLeccion.LECCION && aprobada && siguiente != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Sigue: ${siguiente.title}",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoft
            )
        }
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

/**
 * "Adivina antes de ver": antes de la primera vez de una lección, tres frases
 * para intentar decirlas en inglés SIN haberlas visto. Va a fallar y ese es el
 * punto: intentar y errar, con la respuesta después, deja mejor recuerdo que
 * leer la respuesta directa (Kornell, Hays & Bjork 2009; Richland, Kornell &
 * Kao 2009). No puntúa, no va al cuaderno, no se repite.
 */
@Composable
private fun AdivinaScreen(
    lesson: Lesson,
    ejercicio: Exercise.WriteIt,
    numero: Int,
    total: Int,
    typed: String,
    checked: Boolean,
    accent: Color,
    onTyped: (String) -> Unit,
    onCheck: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit
) {
    val acierto = checked && Correccion.acepta(typed, ejercicio.answer, ejercicio.accept)
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = "${lesson.title}  ·  antes de empezar", onBack = onExit)
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text("Adivina antes de ver · $numero de $total", style = MaterialTheme.typography.titleLarge)
            Text(
                "Todavía no lo has estudiado. Intenta decirlo en inglés como creas: fallar ahora ayuda a que después se quede.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoft
            )
            Text(ejercicio.es, style = MaterialTheme.typography.headlineSmall, color = Ink)
            OutlinedTextField(
                value = typed,
                onValueChange = { if (!checked) onTyped(it) },
                singleLine = true,
                readOnly = checked,
                placeholder = { Text("Tu intento en inglés...") },
                modifier = Modifier.fillMaxWidth()
            )
            if (checked) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (acierto) GoodGreenSoft else Color(0xFFFFF6E3), RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        if (acierto) "¡Lo adivinaste!" else "Así se dice:",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (acierto) GoodGreen else Color(0xFF8A5A00)
                    )
                    Text(ejercicio.answer, style = MaterialTheme.typography.bodyLarge, color = Ink)
                    if (!acierto) Text(
                        "No cuenta como error: ahora la lección te lo enseña.",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkSoft
                    )
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().background(Cream).padding(20.dp)) {
            if (!checked) {
                BigButton("Ver cómo se dice", enabled = true, container = accent) { onCheck() }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Saltar las adivinanzas",
                    style = MaterialTheme.typography.labelLarge,
                    color = InkSoft,
                    modifier = Modifier.clickable { repeat(total) { onNext() } }.padding(6.dp)
                )
            } else {
                BigButton(if (numero < total) "Siguiente" else "Empezar la lección", container = GoodGreen) { onNext() }
            }
        }
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
    is Exercise.FixIt -> "corregir"
}

