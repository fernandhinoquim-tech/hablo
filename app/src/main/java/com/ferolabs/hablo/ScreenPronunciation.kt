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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PronunciationScreen(
    teacher: Teacher,
    speaker: Speaker,
    listener: Listener,
    store: Store,
    progreso: Progreso,
    say: (String, Float) -> Unit,
    onBack: () -> Unit
) {
    val accent = Color(teacher.color)
    val drills = Course.drills

    // El modelo de fonemas se carga al entrar (tarda un par de segundos) y se
    // suelta al salir: son ~400 MB que no deben quedarse en memoria.
    LaunchedEffect(Unit) { listener.prepareSounds() }
    DisposableEffect(Unit) { onDispose { listener.releaseSounds() } }

    // Sin drills no hay pantalla: el motivo (contenido mal formado) ya está en
    // la pantalla de inicio, aquí solo se evita reventar con una lista vacía.
    if (drills.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar("Pronunciación", onBack = onBack)
            Text(
                Course.loadError?.let { "No se pudo leer drills.json:\n$it" }
                    ?: "No hay ejercicios de pronunciación en drills.json.",
                style = MaterialTheme.typography.bodyLarge,
                color = BadRed,
                modifier = Modifier.padding(20.dp)
            )
        }
        return
    }

    // Arranca ya con el reparto ponderado, no siempre con el primero de la lista.
    var index by remember { mutableStateOf(-1) }
    var result by remember { mutableStateOf<PronunciationResult?>(null) }
    var report by remember { mutableStateOf<SoundReport?>(null) }
    /** Hubo veredicto por fonema pero se descarto entero: el dictado no entendio esas palabras. */
    var reportDescartado by remember { mutableStateOf(false) }
    var notHeard by remember { mutableStateOf<NotHeardReason?>(null) }
    var showTip by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(false) }
    var labeled by remember { mutableStateOf(false) }
    var permissionAsked by remember { mutableStateOf(false) }

    // "Hoy": un solo mensaje por sesión, el sonido que más falló.
    val sessionTries = remember { mutableStateMapOf<Sound, Int>() }
    val sessionFails = remember { mutableStateMapOf<Sound, Int>() }

    /**
     * Siguiente frase. Antes era `(index + 1) % 40`: siempre el mismo orden, y
     * en una sesión Fero repitió 15 frases 96 veces. Ahora se sortea entre las
     * que no ha trabajado hoy, dando más peso al sonido que peor le va según su
     * mapa personal, y nunca sale la misma dos veces seguidas.
     */
    fun pickNext(current: Int): Int {
        val frescas = drills.indices.filter {
            it != current && store.drillTriesToday(drills[it].text) < MAX_TRIES_PER_DAY
        }
        val pool = frescas.ifEmpty { drills.indices.filter { it != current } }
        if (pool.isEmpty()) return current
        fun peso(i: Int): Double {
            val st = store.soundStats(drills[i].sound)
            if (st.tries == 0) return 1.5                       // sin datos: vale la pena probarlo
            return 0.5 + (st.mal * 2.0 + st.dudoso) / st.tries  // cuanto peor va, más sale
        }
        var r = Random.nextDouble() * pool.sumOf { peso(it) }
        for (i in pool) {
            r -= peso(i)
            if (r <= 0) return i
        }
        return pool.last()
    }

    if (index < 0) index = pickNext(-1)
    val drill = drills[index % drills.size]
    val triesToday = store.drillTriesToday(drill.text)

    // El porcentaje sale de lo que el reconocedor de palabras entendió; el
    // veredicto del sonido, de la evaluación por fonema.
    fun listen(target: String, sound: Sound) {
        listener.startRecording(target, sound) { r ->
            when (r) {
                is ListenResult.Heard -> {
                    store.recordDrillTry(target)
                    val res = scorePronunciation(target, r.text)
                    result = res
                    // Sin el "bien" de palabras que el dictado no entendio (falso "bien").
                    report = r.report?.fiable(res)
                    reportDescartado = r.report != null && report == null
                    notHeard = null
                    progreso.anotarIntento(target, sound, report)
                    progreso.anotarActividad(Progreso.Actividad.INTENTO)
                    report?.let { rep ->
                        store.recordSound(rep.sound, rep.worst)
                        sessionTries[rep.sound] = (sessionTries[rep.sound] ?: 0) + 1
                        if (rep.worst == WordScore.MAL) sessionFails[rep.sound] = (sessionFails[rep.sound] ?: 0) + 1
                    }
                }
                is ListenResult.NotHeard -> {
                    result = null
                    report = null
                    notHeard = r.reason
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionAsked = true
        if (granted) listen(drill.text, drill.sound)
    }

    fun record() {
        if (listener.recording) {
            listener.stopRecording()
            return
        }
        result = null
        report = null
        notHeard = null
        showDetail = false
        labeled = false
        if (listener.hasMicPermission()) {
            listen(drill.text, drill.sound)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        TopBar("Pronunciación", onBack = onBack)

        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {

            val worstToday = sessionFails.entries.maxByOrNull { it.value }
            if (worstToday != null && worstToday.value > 0) {
                Text(
                    "Hoy: ${worstToday.key.labelEs} falló ${worstToday.value} de ${sessionTries[worstToday.key] ?: worstToday.value}",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSoft
                )
            }

            Pill("Enfoque: ${drill.focusEs}", accent, Color(teacher.softColor))

            if (triesToday >= MAX_TRIES_PER_DAY) {
                Text(
                    "Esta frase ya la trabajaste $triesToday veces hoy. Repetirla más hoy " +
                        "rinde la mitad que volver a ella mañana: toca \"Siguiente frase\".",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF8A5A00)
                )
            }

            Text(
                drill.text,
                style = MaterialTheme.typography.headlineMedium,
                color = Ink
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                SpeakerButton(big = true, tint = accent) { say(drill.text, 1f) }
                SpeakerButton(slow = true, tint = accent.copy(alpha = 0.75f)) { say(drill.text, 0.6f) }
                Text(
                    "Escucha a ${teacher.name} y después repítelo tú.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft
                )
            }

            // --- Botón de grabar -------------------------------------------
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(96.dp)
                        .background(
                            if (listener.recording) BadRed else accent,
                            CircleShape
                        )
                        .clickable(enabled = !listener.thinking) { record() }
                ) {
                    Text(
                        if (listener.recording) "■" else "🎤",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    when {
                        listener.thinking -> "Analizando lo que dijiste…"
                        listener.recording -> "Grabando… toca para terminar"
                        else -> "Toca y di la frase"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (listener.recording) BadRed else InkSoft
                )

                if (listener.recording) {
                    Spacer(Modifier.height(10.dp))
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

            listener.errorDetail?.let { detail ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BadRedSoft, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text("Problema con el micrófono", style = MaterialTheme.typography.labelLarge, color = BadRed)
                    Spacer(Modifier.height(4.dp))
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = Ink)
                }
            }

            // --- No se pudo evaluar ------------------------------------------
            notHeard?.let { NotHeardBox(it) }

            // --- Resultado --------------------------------------------------
            // Primero el sonido del ejercicio (GOP). El porcentaje de palabras
            // queda como "¿se entendió?", y la transcripción cruda, escondida:
            // es la salida de un modelo de dictado, no un veredicto.
            report?.let { SoundVerdictCard(it) }

            result?.let { r ->
                val hasReport = report != null
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Line, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Este número mide si el DICTADO entendió las palabras, no
                        // cómo se pronunciaron: el propio banco de pruebas midió que
                        // "arregla" 14 de 35 errores puestos a propósito. Se muestra
                        // por lo que es y nunca como nota grande de pronunciación.
                        Text(
                            "Se te entendió: ${r.percent} % de las palabras",
                            style = MaterialTheme.typography.titleMedium,
                            color = InkSoft,
                            modifier = Modifier.weight(1f)
                        )
                        if (listener.lastRecording != null) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(Color(0xFFF3EDE6), CircleShape)
                                    .clickable { speaker.playRecording(listener.lastRecording!!) }
                            ) {
                                Text("👤", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }

                    if (hasReport && !showDetail) {
                        Text(
                            "Ver lo que oyó el dictado →",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                            modifier = Modifier.clickable { showDetail = true }
                        )
                    }

                    if (!hasReport || showDetail) {
                    Text(
                        "Palabra por palabra:",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkSoft
                    )

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
                        "Entendí: \"${r.heard}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSoft
                    )
                    }

                    Text(
                        "El botón 👤 reproduce tu propia grabación. Compárala con la de ${teacher.name}.",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkSoft
                    )
                }
            }

            if (result != null) {
                SelfLabelRow(answered = labeled) { key ->
                    listener.labelLastRecording(key)
                    labeled = true
                }
            }

            // Sonido sin umbral todavía, o modelo ausente: se dice, no se calla.
            if (result != null && report == null && drill.sound != Sound.GENERAL) {
                Text(
                    if (reportDescartado) "El sonido no se pudo juzgar: el dictado no entendió la palabra que lo lleva."
                    else listener.sounds.unavailableReason
                        ?: "El sonido \"${drill.sound.labelEs}\" todavía no tiene evaluación por fonema calibrada.",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSoft
                )
            }

            if (showTip) {
                TipBox(drill.tipEs)
            } else {
                Text(
                    "¿Cómo se pronuncia bien? →",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    modifier = Modifier.clickable { showTip = true }
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
            BigButton(
                text = if (result == null) "Saltar esta frase" else "Siguiente frase",
                container = accent
            ) {
                index = pickNext(index)
                result = null
                report = null
                notHeard = null
                showTip = false
                showDetail = false
                labeled = false
            }
        }
    }
}

/** Tope de intentos por frase y día antes de que la app empuje a cambiar de frase. */
private const val MAX_TRIES_PER_DAY = 5
