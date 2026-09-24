package com.ferolabs.hablo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {

    private var speaker: Speaker? = null
    private var listener: Listener? = null
    private var llm: Llm? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sp = Speaker(this)
        speaker = sp
        val li = Listener(this)
        listener = li
        val ai = Llm(this)
        llm = ai
        val cloud = CloudLlm(this)
        val claude = ClaudeLlm(this)
        val store = Store(this)
        val prog = Progreso(this)
        // La ficha de la charla libre: files/memoria/perfil.json, al lado de la app.
        val mem = Memoria(java.io.File(getExternalFilesDir("memoria"), "perfil.json"))
        // El mazo de repaso (etapa 3): al lado del cuaderno, dentro de la app.
        val mazo = Mazo(java.io.File(filesDir, "mazo.json"))
        // El diagnóstico del Modo Aptis (etapa 5): sus cinco partes, al lado del mazo.
        val aptis = Aptis(java.io.File(filesDir, "aptis.json"))
        // Los crucigramas a medias y terminados (17-09), al lado del mazo.
        val crucigramas = Crucigramas(java.io.File(filesDir, "crucigramas.json"))
        Course.load(this)
        // Si el contenido cambió (parche de Cowork), el mazo toma en/es del curso por id.
        mazo.refrescar { Course.exerciseById(it) }

        setContent {
            HabloApp(speaker = sp, listener = li, llm = ai, cloud = cloud, claude = claude, store = store, progreso = prog, memoria = mem, mazo = mazo, aptis = aptis, crucigramas = crucigramas)
        }
    }

    override fun onStop() {
        speaker?.stop()
        // Copia del día en Descargas › Hablo (sobrevive a desinstalar la app).
        val respaldo = Respaldo(this)
        Thread { respaldo.automatico() }.start()
        super.onStop()
    }

    override fun onDestroy() {
        speaker?.shutdown()
        speaker = null
        listener?.release()
        listener = null
        llm?.release()
        llm = null
        super.onDestroy()
    }
}

private sealed class Route {
    data object PickTeacher : Route()
    data object Home : Route()
    data class Running(val lessonId: String) : Route()
    data object Settings : Route()
    data object Pronunciation : Route()
    data object Scenarios : Route()
    data class Talking(val scenarioId: String) : Route()
    /** Etapa 3: el repaso del mazo, el contrarreloj y "Aguanta". */
    data object Repaso : Route()
    data object Contrarreloj : Route()
    data object Aguanta : Route()
    /** Etapa 4: historias cortas con retell. */
    data object Historias : Route()
    data class Cuento(val historiaId: String) : Route()
    /** Oído (pares mínimos con las cuatro voces) y dictado de números (17-09). */
    data object Oido : Route()
    data object Dictado : Route()
    data object Crucigramas : Route()
    /** La biblioteca de fichas de teoría (auditoría del 16-09). */
    data object Gramatica : Route()
    /** Etapa 5: el Modo Aptis (tablero), una pista de entrenamiento y el simulacro completo. */
    data object Aptis : Route()
    data class AptisPista(val pistaId: String) : Route()
    data object AptisSimulacro : Route()
}

@Composable
fun HabloApp(
    speaker: Speaker,
    listener: Listener,
    llm: Llm,
    cloud: CloudLlm,
    claude: ClaudeLlm,
    store: Store,
    progreso: Progreso,
    memoria: Memoria,
    mazo: Mazo,
    aptis: Aptis,
    crucigramas: Crucigramas
) {

    var teacherId by remember { mutableStateOf(store.teacherId) }
    var speechScale by remember { mutableStateOf(store.speechScale) }
    // Quién responde en la conversación; se puede cambiar en Ajustes sin reiniciar.
    var engineId by remember { mutableStateOf(store.conversationEngine) }
    val local = remember { LocalEngine(llm) }
    val engine: ChatEngine = remember(engineId) {
        when {
            engineId.startsWith("claude") -> claude.also { it.model = engineId }
            engineId == Store.ENGINE_GEMINI -> cloud
            else -> local
        }
    }
    var progressTick by remember { mutableStateOf(0) }

    var route by remember {
        mutableStateOf<Route>(if (store.teacherId == null) Route.PickTeacher else Route.Home)
    }

    val teacher = teacherById(teacherId)

    val say: (String, Float) -> Unit = { text, extra ->
        speaker.speak(text, teacher, speechScale * extra)
    }

    HabloTheme(accent = Color(teacher.color)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Cream)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .imePadding()
            ) {
                when (val current = route) {

                    is Route.PickTeacher -> {
                        val first = store.teacherId == null
                        BackHandler(enabled = !first) {
                            speaker.stop()
                            route = Route.Home
                        }
                        TeacherPickerScreen(
                            currentId = teacherId,
                            isFirstTime = first,
                            speaking = speaker.busy,
                            onPreview = { t -> speaker.speak(t.greeting, t, speechScale) },
                            onChoose = { t ->
                                speaker.stop()
                                store.teacherId = t.id
                                teacherId = t.id
                                route = Route.Home
                            },
                            onBack = {
                                speaker.stop()
                                route = Route.Home
                            }
                        )
                    }

                    is Route.Home -> {
                        // Mazo vacío con lecciones ya aprobadas (la primera vez tras la
                        // etapa 3): entran sus frases, para que haya qué repasar mañana.
                        LaunchedEffect(progressTick) {
                            if (mazo.total() == 0) {
                                var n = 0
                                for (l in Course.allLessons()) if (store.bestScore(l.id) >= 60) n += mazo.alimentar(l)
                                if (n > 0) progressTick += 1
                            }
                        }
                        HomeScreen(
                            teacher = teacher,
                            store = store,
                            refreshKey = progressTick,
                            speaker = speaker,
                            onOpenLesson = { lesson -> route = Route.Running(lesson.id) },
                            onSettings = { route = Route.Settings },
                            onPronunciation = { llm.release(); route = Route.Pronunciation },
                            onConversation = { route = Route.Scenarios },
                            onGreeting = { say(teacher.greeting, 1f) },
                            mazo = mazo,
                            progreso = progreso,
                            onRepaso = { llm.release(); route = Route.Repaso },
                            onContrarreloj = { route = Route.Contrarreloj },
                            onAguanta = { llm.release(); route = Route.Aguanta },
                            onHistorias = { llm.release(); route = Route.Historias },
                            aptis = aptis,
                            onAptis = { llm.release(); route = Route.Aptis },
                            onOido = { llm.release(); route = Route.Oido },
                            onDictado = { route = Route.Dictado },
                            crucigramas = crucigramas,
                            onCrucigramas = { route = Route.Crucigramas },
                            onGramatica = { route = Route.Gramatica }
                        )
                    }

                    is Route.Aptis -> {
                        val banco = Course.aptis
                        BackHandler { speaker.stop(); route = Route.Home }
                        if (banco == null) {
                            route = Route.Home
                        } else {
                            AptisScreen(
                                banco = banco,
                                aptis = aptis,
                                teacher = teacher,
                                claude = claude,
                                onPista = { id -> llm.release(); route = Route.AptisPista(id) },
                                onSimulacro = { llm.release(); route = Route.AptisSimulacro },
                                onBack = { speaker.stop(); route = Route.Home }
                            )
                        }
                    }

                    is Route.AptisPista -> {
                        val pista = Course.aptis?.pista(current.pistaId)
                        BackHandler { speaker.stop(); listener.stopRecording(); route = Route.Aptis }
                        if (pista == null) {
                            route = Route.Aptis
                        } else {
                            PistaScreen(
                                pista = pista,
                                aptis = aptis,
                                teacher = teacher,
                                speaker = speaker,
                                listener = listener,
                                claude = claude,
                                say = say,
                                sayQueued = { text -> speaker.speakQueued(text, teacher, speechScale) },
                                onBack = { speaker.stop(); listener.stopRecording(); route = Route.Aptis }
                            )
                        }
                    }

                    is Route.AptisSimulacro -> {
                        val diag = Course.aptis?.simulacro
                        BackHandler { speaker.stop(); listener.stopRecording(); route = Route.Aptis }
                        if (diag == null) {
                            route = Route.Aptis
                        } else {
                            SimulacroScreen(
                                diag = diag,
                                aptis = aptis,
                                teacher = teacher,
                                speaker = speaker,
                                listener = listener,
                                claude = claude,
                                say = say,
                                sayQueued = { text -> speaker.speakQueued(text, teacher, speechScale) },
                                onBack = { speaker.stop(); listener.stopRecording(); route = Route.Aptis }
                            )
                        }
                    }

                    is Route.Oido -> {
                        BackHandler { speaker.stop(); listener.stopRecording(); progressTick += 1; route = Route.Home }
                        OidoScreen(
                            teacher = teacher,
                            speaker = speaker,
                            listener = listener,
                            store = store,
                            progreso = progreso,
                            speechScale = speechScale,
                            onBack = { speaker.stop(); listener.stopRecording(); progressTick += 1; route = Route.Home }
                        )
                    }

                    is Route.Gramatica -> {
                        BackHandler { route = Route.Home }
                        GramaticaScreen(teacher = teacher, store = store, onBack = { route = Route.Home })
                    }

                    is Route.Crucigramas -> {
                        BackHandler { speaker.stop(); progressTick += 1; route = Route.Home }
                        CrucigramasScreen(
                            teacher = teacher,
                            speaker = speaker,
                            crucigramas = crucigramas,
                            mazo = mazo,
                            progreso = progreso,
                            say = say,
                            onBack = { speaker.stop(); progressTick += 1; route = Route.Home }
                        )
                    }

                    is Route.Dictado -> {
                        BackHandler { speaker.stop(); progressTick += 1; route = Route.Home }
                        DictadoScreen(
                            teacher = teacher,
                            speaker = speaker,
                            store = store,
                            progreso = progreso,
                            speechScale = speechScale,
                            onBack = { speaker.stop(); progressTick += 1; route = Route.Home }
                        )
                    }

                    is Route.Historias -> {
                        BackHandler { speaker.stop(); route = Route.Home }
                        HistoriasScreen(
                            teacher = teacher,
                            store = store,
                            onPick = { h -> listener.releaseSounds(); route = Route.Cuento(h.id) },
                            onBack = { speaker.stop(); route = Route.Home }
                        )
                    }

                    is Route.Cuento -> {
                        val historia = Course.historiaById(current.historiaId)
                        BackHandler { speaker.stop(); listener.stopRecording(); route = Route.Historias }
                        if (historia == null) {
                            route = Route.Historias
                        } else {
                            HistoriaScreen(
                                historia = historia,
                                teacher = teacher,
                                speaker = speaker,
                                listener = listener,
                                store = store,
                                mazo = mazo,
                                progreso = progreso,
                                say = say,
                                sayQueued = { text -> speaker.speakQueued(text, teacher, speechScale) },
                                onDone = { progressTick += 1; speaker.stop(); route = Route.Historias },
                                onExit = { speaker.stop(); listener.stopRecording(); route = Route.Historias }
                            )
                        }
                    }

                    is Route.Repaso -> {
                        val leccion = remember { Repaso.leccionDeHoy(mazo, progreso.fallos()) }
                        BackHandler { speaker.stop(); listener.stopRecording(); route = Route.Home }
                        if (leccion.exercises.isEmpty()) {
                            route = Route.Home
                        } else {
                            LessonScreen(
                                lesson = leccion,
                                teacher = teacher,
                                listener = listener,
                                progreso = progreso,
                                store = store,
                                speaking = speaker.busy,
                                lastSpokenSeconds = { speaker.lastSpokenSeconds },
                                showFace = store.showFaces,
                                say = say,
                                modo = ModoLeccion.REPASO,
                                onAnswered = { ex, ok, primero ->
                                    // Solo el primer intento mueve el mazo; repetirlo hasta acertar no lo infla.
                                    if (!primero) return@LessonScreen
                                    if (ex is Exercise.FixIt) mazo.registrarCorreccion(Repaso.claveDe(ex), ok)
                                    else mazo.registrar(ex.id, ok)
                                },
                                onFinish = { _, correct ->
                                    store.recordLesson(Repaso.ID_REPASO, 0, correct * 5)   // XP, sin nota
                                    progressTick += 1
                                    speaker.stop(); listener.stopRecording()
                                    route = Route.Home
                                },
                                onExit = { speaker.stop(); listener.stopRecording(); progressTick += 1; route = Route.Home }
                            )
                        }
                    }

                    is Route.Aguanta -> {
                        val hechas = remember { Course.allLessons().filter { store.bestScore(it.id) >= 60 } }
                        val leccion = remember { Repaso.leccionAguanta(hechas, mazo) }
                        val marca = remember { mazo.marcaAguanta() }
                        BackHandler { speaker.stop(); listener.stopRecording(); route = Route.Home }
                        if (leccion.exercises.size < 5) {
                            route = Route.Home
                        } else {
                            LessonScreen(
                                lesson = leccion,
                                teacher = teacher,
                                listener = listener,
                                progreso = progreso,
                                store = store,
                                speaking = speaker.busy,
                                lastSpokenSeconds = { speaker.lastSpokenSeconds },
                                showFace = store.showFaces,
                                say = say,
                                modo = ModoLeccion.AGUANTA,
                                maxErrores = 3,
                                marca = marca,
                                onFinish = { _, correct ->
                                    mazo.registrarAguanta(correct)
                                    store.recordLesson(Repaso.ID_AGUANTA, 0, correct * 5)
                                    progressTick += 1
                                    speaker.stop(); listener.stopRecording()
                                    route = Route.Home
                                },
                                onExit = { speaker.stop(); listener.stopRecording(); route = Route.Home }
                            )
                        }
                    }

                    is Route.Contrarreloj -> {
                        BackHandler { speaker.stop(); route = Route.Home }
                        ContrarrelojScreen(
                            teacher = teacher,
                            store = store,
                            mazo = mazo,
                            onBack = { speaker.stop(); progressTick += 1; route = Route.Home }
                        )
                    }

                    is Route.Running -> {
                        llm.release()
                        val lesson = Course.lessonById(current.lessonId)
                        BackHandler {
                            speaker.stop()
                            listener.stopRecording()
                            route = Route.Home
                        }
                        if (lesson == null) {
                            route = Route.Home
                        } else {
                            // Adivina antes de ver: solo la primera vez que se abre la lección.
                            val adivinanzas = remember(lesson.id) {
                                if (store.bestScore(lesson.id) == 0) Repaso.adivinanzas(lesson) else emptyList()
                            }
                            LessonScreen(
                                lesson = lesson,
                                teacher = teacher,
                                listener = listener,
                                progreso = progreso,
                                store = store,
                                speaking = speaker.busy,
                                lastSpokenSeconds = { speaker.lastSpokenSeconds },
                                showFace = store.showFaces,
                                say = say,
                                adivinanzas = adivinanzas,
                                onFinish = { score, correct ->
                                    store.recordLesson(lesson.id, score, correct * 10)
                                    // Lección aprobada: sus frases entran al mazo y vuelven mañana.
                                    if (score >= 60) mazo.alimentar(lesson)
                                    progressTick += 1
                                    speaker.stop()
                                    listener.stopRecording()
                                    route = Route.Home
                                },
                                onExit = {
                                    speaker.stop()
                                    listener.stopRecording()
                                    route = Route.Home
                                }
                            )
                        }
                    }

                    is Route.Scenarios -> {
                        BackHandler {
                            speaker.stop()
                            route = Route.Home
                        }
                        ScenariosScreen(
                            teacher = teacher,
                            engine = engine,
                            local = local,
                            onPick = { sc -> listener.releaseSounds(); route = Route.Talking(sc.id) },
                            onBack = { speaker.stop(); route = Route.Home }
                        )
                    }

                    is Route.Talking -> {
                        val scenario = Course.scenarioById(current.scenarioId)
                        BackHandler {
                            speaker.stop()
                            listener.stopRecording()
                            route = Route.Scenarios
                        }
                        if (scenario == null) {
                            route = Route.Scenarios
                        } else {
                            ConversationScreen(
                                scenario = scenario,
                                teacher = teacher,
                                speaker = speaker,
                                listener = listener,
                                engine = engine,
                                local = local,
                                showFace = store.showFaces,
                                say = say,
                                sayQueued = { text -> speaker.speakQueued(text, teacher, speechScale) },
                                progreso = progreso,
                                // Solo la charla libre lleva memoria; los escenarios arrancan limpios.
                                memoria = if (scenario.id == Scenario.LIBRE.id) memoria else null,
                                // El resumen va por Claude solo si Fero eligió Claude: con Gemini o la IA
                                // del teléfono no se manda nada a Anthropic (regla dura 1).
                                claude = if (engineId.startsWith("claude")) claude else null,
                                onBack = {
                                    speaker.stop()
                                    listener.stopRecording()
                                    route = Route.Scenarios
                                }
                            )
                        }
                    }

                    is Route.Pronunciation -> {
                        BackHandler {
                            speaker.stop()
                            listener.stopRecording()
                            route = Route.Home
                        }
                        PronunciationScreen(
                            teacher = teacher,
                            speaker = speaker,
                            listener = listener,
                            store = store,
                            progreso = progreso,
                            say = say,
                            onBack = {
                                speaker.stop()
                                listener.stopRecording()
                                route = Route.Home
                            }
                        )
                    }

                    is Route.Settings -> {
                        BackHandler {
                            speaker.stop()
                            route = Route.Home
                        }
                        SettingsScreen(
                            teacher = teacher,
                            store = store,
                            speaker = speaker,
                            llm = llm,
                            cloud = cloud,
                            claude = claude,
                            progreso = progreso,
                            memoria = memoria,
                            mazo = mazo,
                            engineId = engineId,
                            onEngineChange = { id ->
                                engineId = id
                                store.conversationEngine = id
                            },
                            onChangeTeacher = { route = Route.PickTeacher },
                            onTestVoice = { s ->
                                speaker.speak(teacher.greeting, teacher, s)
                            },
                            onSpeedChange = { s ->
                                speechScale = s
                                store.speechScale = s
                            },
                            onReset = {
                                store.resetEverything()
                                aptis.borrarTodo()
                                crucigramas.borrarTodo()
                                progressTick += 1
                                teacherId = null
                                route = Route.PickTeacher
                            },
                            onBack = {
                                speaker.stop()
                                route = Route.Home
                            }
                        )
                    }
                }
            }
        }
    }
}
