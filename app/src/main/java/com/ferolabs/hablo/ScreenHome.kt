package com.ferolabs.hablo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    teacher: Teacher,
    store: Store,
    speaker: Speaker,
    refreshKey: Int,
    onOpenLesson: (Lesson) -> Unit,
    onSettings: () -> Unit,
    onPronunciation: () -> Unit,
    onConversation: () -> Unit,
    onGreeting: () -> Unit,
    /** Etapa 3: el mazo y el cuaderno, para las tarjetas de repaso y retos. */
    mazo: Mazo? = null,
    progreso: Progreso? = null,
    onRepaso: () -> Unit = {},
    onContrarreloj: () -> Unit = {},
    onAguanta: () -> Unit = {},
    onHistorias: () -> Unit = {},
    /** Etapa 5: el Modo Aptis va como sección aparte (decisión de Fero). */
    aptis: Aptis? = null,
    onAptis: () -> Unit = {}
) {
    val accent = Color(teacher.color)
    // Repaso de hoy: qué toca del mazo y cuántos errores propios hay para corregir.
    val pendientes = remember(refreshKey) { mazo?.cuantosPendientes() ?: 0 }
    val propios = remember(refreshKey) {
        if (mazo != null && progreso != null) Repaso.propiosErrores(progreso.fallos(), mazo, mazo.hoy()).size else 0
    }
    val enMazo = remember(refreshKey) { mazo?.total() ?: 0 }
    val aprendidas = remember(refreshKey) { mazo?.aprendidos() ?: 0 }
    val marcaAguanta = remember(refreshKey) { mazo?.marcaAguanta() ?: 0 }
    val hayAprobadas = remember(refreshKey) { Course.allLessons().any { store.bestScore(it.id) >= 60 } }

    // La versión sale del paquete instalado, no de un texto fijo que se olvida.
    val context = LocalContext.current
    val appVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (e: Throwable) {
            "?"
        }
    }

    val streak = remember(refreshKey) { store.streak }
    val xp = remember(refreshKey) { store.xp }
    val studiedToday = remember(refreshKey) { store.studiedToday() }
    val scores = remember(refreshKey) {
        Course.allLessons().associate { it.id to store.bestScore(it.id) }
    }
    val units = remember(refreshKey) { Course.allUnits() }
    // Mapa personal de sonidos: qué sonidos fallas de verdad, acumulado entre
    // sesiones. Solo aparece cuando ya hay algo medido.
    val soundMap = remember(refreshKey) {
        Sound.entries.filter { it != Sound.GENERAL }
            .map { it to store.soundStats(it) }
            .filter { it.second.tries > 0 }
            .sortedByDescending { (_, st) -> st.mal.toFloat() / st.tries }
    }
    // Los niveles, cada uno con sus unidades: A1 y A2 se ven separados.
    val levels = remember(refreshKey) { Course.levels }

    // Una unidad se abre cuando la anterior está terminada.
    val unlocked = remember(refreshKey) {
        val out = HashSet<String>()
        var allow = true
        for (u in units) {
            if (allow) out.add(u.id)
            allow = allow && u.lessons.all { (scores[it.id] ?: 0) >= 60 }
        }
        out
    }

    var expanded by remember(refreshKey) {
        mutableStateOf(units.firstOrNull { u -> u.lessons.any { (scores[it.id] ?: 0) < 60 } }?.id)
    }

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeacherAvatar(
                    teacher = teacher,
                    speaking = speaker.busy,
                    size = 64.dp,
                    modifier = Modifier.clickable { onGreeting() },
                    showFace = store.showFaces
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tu profesora", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                    Text(teacher.name, style = MaterialTheme.typography.titleLarge)
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color.White, CircleShape)
                        .border(1.dp, Line, CircleShape)
                        .clickable { onSettings() }
                ) {
                    Text("⚙", style = MaterialTheme.typography.titleMedium, color = InkSoft)
                }
            }
        }

        item { VoiceStatusBanner(speaker, accent) }

        Course.loadError?.let { err ->
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BadRedSoft, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text("No se pudo leer el curso", style = MaterialTheme.typography.labelLarge, color = BadRed)
                    Spacer(Modifier.height(4.dp))
                    Text(err, style = MaterialTheme.typography.bodyMedium, color = Ink)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox("Racha", "$streak", if (streak == 1) "día 🔥" else if (streak > 1) "días 🔥" else "empieza hoy", Modifier.weight(1f))
                StatBox("Puntos", "$xp", "XP", Modifier.weight(1f))
                StatBox(
                    "Hoy",
                    if (studiedToday) "✓" else "—",
                    if (studiedToday) "listo" else "pendiente",
                    Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(teacher.softColor), RoundedCornerShape(16.dp))
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .clickable { onPronunciation() }
                    .padding(16.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(46.dp).background(Color.White, CircleShape)
                ) {
                    Text("🎤", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Practicar pronunciación", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Los sonidos que más nos cuestan a los hispanohablantes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSoft
                    )
                }
                Text("›", style = MaterialTheme.typography.headlineMedium, color = accent)
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .clickable { onConversation() }
                    .padding(16.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(46.dp).background(Color(teacher.softColor), CircleShape)
                ) {
                    Text("💬", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Conversar con ${teacher.name}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Situaciones reales, con corrección en español",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSoft
                    )
                }
                Text("›", style = MaterialTheme.typography.headlineMedium, color = accent)
            }
        }

        // --- Etapa 3: repaso y retos ---------------------------------------
        if (mazo != null) {
            item {
                val hay = pendientes + propios > 0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (hay) Color(teacher.softColor) else Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .clickable(enabled = hay) { onRepaso() }
                        .padding(16.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(46.dp).background(Color.White, CircleShape)
                    ) {
                        Text("🔁", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.size(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Repaso de hoy", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                hay -> listOfNotNull(
                                    if (pendientes > 0) "$pendientes frases que te tocan" else null,
                                    if (propios > 0) "$propios errores tuyos para corregir" else null
                                ).joinToString(" · ")
                                enMazo == 0 -> "Termina una lección y mañana vuelven sus frases."
                                else -> "Nada por hoy. En el mazo: $enMazo frases, $aprendidas aprendidas."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSoft
                        )
                    }
                    if (hay) Text("›", style = MaterialTheme.typography.headlineMedium, color = accent)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            .clickable { onContrarreloj() }
                            .padding(14.dp)
                    ) {
                        Text("⏱ Contrarreloj", style = MaterialTheme.typography.titleMedium)
                        Text("Vocabulario por temas, contra tu propia marca", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            .clickable(enabled = hayAprobadas) { onAguanta() }
                            .padding(14.dp)
                    ) {
                        Text("🏁 Aguanta", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                !hayAprobadas -> "Termina una lección para jugar"
                                marcaAguanta > 0 -> "Todo mezclado · tu marca: $marcaAguanta"
                                else -> "Todo mezclado, hasta tres errores"
                            },
                            style = MaterialTheme.typography.bodyMedium, color = InkSoft
                        )
                    }
                }
            }
        }

        // --- Etapa 4: historias -------------------------------------------------
        if (Course.tandas.isNotEmpty()) {
            item {
                val total = Course.tandas.sumOf { it.historias.size }
                val hechas = Course.tandas.sumOf { t -> t.historias.count { store.historiaHecha(it.id) } }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .clickable { onHistorias() }
                        .padding(16.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(46.dp).background(Color(teacher.softColor), CircleShape)
                    ) {
                        Text("📚", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.size(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Historias", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Léela, responde y cuéntala tú · $hechas de $total",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSoft
                        )
                    }
                    Text("›", style = MaterialTheme.typography.headlineMedium, color = accent)
                }
            }
        }

        // --- Etapa 5: Modo Aptis, sección aparte: la pista de preparación ---------
        Course.aptis?.let { banco ->
            item {
                val tick = aptis?.tick ?: 0
                val subtitulo = remember(tick) {
                    if (aptis == null || banco.pistas.all { aptis.hechas(it) == 0 }) "Cinco pistas de A1 a B2: entrenas y suben solas; el tablero dice cuál va última"
                    else if (banco.cuatro.all { aptis.alcanzado(it) == null }) "Aún sin nivel en las cuatro destrezas" + (aptis.alcanzado(banco.pista("core") ?: banco.pistas[0])?.let { " · Core ${it.etiqueta}" } ?: "") + " · sigue entrenando"
                    else {
                        val piso = aptis.piso(banco)
                        "Tu piso: " + piso.joinToString(", ") { it.skill } + " · " +
                            banco.cuatro.joinToString(" · ") { "${it.skill} ${aptis.alcanzado(it)?.etiqueta ?: "—"}" }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .clickable { onAptis() }
                        .padding(16.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(46.dp).background(Color(teacher.softColor), CircleShape)
                    ) {
                        Text("🎯", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.size(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Modo Aptis · Pista de preparación", style = MaterialTheme.typography.titleMedium)
                        Text(subtitulo, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                    }
                    Text("›", style = MaterialTheme.typography.headlineMedium, color = accent)
                }
            }
        }

        if (soundMap.isNotEmpty()) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Line, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text("Tu mapa de sonidos", style = MaterialTheme.typography.titleMedium)
                    soundMap.forEach { (sound, st) ->
                        val color = when {
                            st.mal * 2 >= st.tries -> BadRed
                            st.mal + st.dudoso > 0 -> Color(0xFF8A5A00)
                            else -> GoodGreen
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(sound.labelEs, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                "${st.ok} de ${st.tries} bien" + if (st.dudoso > 0) " · ${st.dudoso} casi" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = color
                            )
                        }
                    }
                }
            }
        }

        // Un encabezado por nivel y debajo sus unidades. La cadena de desbloqueo
        // sigue de un nivel al siguiente: la primera unidad de A2 se abre al
        // terminar la última de A1.
        for (level in levels) {
            val lessonsOfLevel = level.units.flatMap { it.lessons }
            val done = lessonsOfLevel.count { (scores[it.id] ?: 0) >= 60 }
            item(key = "nivel-${level.id}") {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(level.id, accent, Color(teacher.softColor))
                    Text(level.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text(
                        "$done de ${lessonsOfLevel.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (done == lessonsOfLevel.size) GoodGreen else InkSoft
                    )
                }
                if (level.goal.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(level.goal, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { if (lessonsOfLevel.isEmpty()) 0f else done.toFloat() / lessonsOfLevel.size },
                    color = accent,
                    trackColor = Line,
                    modifier = Modifier.fillMaxWidth().height(5.dp)
                )
            }
            items(level.units, key = { it.id }) { unit ->
                UnitCard(
                    unit = unit,
                    scores = scores,
                    accent = accent,
                    locked = unit.id !in unlocked,
                    isOpen = expanded == unit.id,
                    onToggle = { expanded = if (expanded == unit.id) null else unit.id },
                    onOpenLesson = onOpenLesson
                )
            }
        }

        item {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .border(1.dp, Line, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                // Solo lo que de verdad falta (etapas 2 y 3 del plan): lo que ya existe o
                // se descartó no se promete.
                Text("Lo que viene", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "🎯  Modo Aptis: los bancos de Reading, Listening, Writing y Speaking (los escribe Cowork)\n" +
                        "🧗  Niveles B1 y B2",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Versión $appVersion · ${Course.levels.size} niveles · ${Course.allLessons().size} lecciones · " +
                        "sin internet, salvo la conversación con Claude o Gemini",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSoft
                )
            }
        }
    }
}

@Composable
private fun UnitCard(
    unit: CourseUnit,
    scores: Map<String, Int>,
    accent: Color,
    locked: Boolean,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onOpenLesson: (Lesson) -> Unit
) {
    val done = unit.lessons.count { (scores[it.id] ?: 0) >= 60 }
    val total = unit.lessons.size
    val complete = done == total

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (complete) accent.copy(alpha = 0.45f) else Line,
                RoundedCornerShape(16.dp)
            )
            .clickable(enabled = !locked) { onToggle() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        if (complete) GoodGreenSoft else Color(0xFFF3EDE6),
                        CircleShape
                    )
            ) {
                Text(
                    if (locked) "🔒" else if (complete) "✓" else unit.emoji,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (complete) GoodGreen else Ink
                )
            }

            Spacer(Modifier.size(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    unit.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (locked) InkSoft else Ink
                )
                Text(
                    if (locked) "Termina la unidad anterior para abrirla" else unit.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft
                )
            }

            if (!locked) {
                Text(
                    if (isOpen) "⌃" else "⌄",
                    style = MaterialTheme.typography.titleLarge,
                    color = InkSoft
                )
            }
        }

        if (!locked) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else done.toFloat() / total },
                    color = if (complete) GoodGreen else accent,
                    trackColor = Line,
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                )
                Text(
                    "$done/$total",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSoft
                )
            }
        }

        AnimatedVisibility(visible = isOpen && !locked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 14.dp)) {
                unit.lessons.forEach { lesson ->
                    val score = scores[lesson.id] ?: 0
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFAF8F5), RoundedCornerShape(12.dp))
                            .border(1.dp, Line, RoundedCornerShape(12.dp))
                            .clickable { onOpenLesson(lesson) }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            if (score >= 60) "✓" else "○",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (score >= 60) GoodGreen else InkSoft
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(lesson.title, style = MaterialTheme.typography.bodyLarge)
                            if (score > 0) {
                                Text(
                                    "Mejor puntaje: $score%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (score >= 60) GoodGreen else InkSoft
                                )
                            }
                        }
                        Text("›", style = MaterialTheme.typography.titleLarge, color = InkSoft)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(vertical = 14.dp, horizontal = 12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = InkSoft)
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(sub, style = MaterialTheme.typography.labelMedium, color = InkSoft)
    }
}

@Composable
private fun VoiceStatusBanner(speaker: Speaker, accent: Color) {
    when (speaker.phase) {

        VoicePhase.PREPARANDO -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF6E3), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFF0DFB9), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text(
                    "Preparando las voces… ${speaker.prepareProgress}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF8A5A00)
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { speaker.prepareProgress / 100f },
                    color = Color(0xFF8A5A00),
                    trackColor = Color(0xFFF0DFB9),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Solo pasa la primera vez. Son unos 95 MB que se acomodan dentro " +
                        "del teléfono para que después funcione sin internet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF4A3A12)
                )
            }
        }

        VoicePhase.RESPALDO -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF6E3), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFF0DFB9), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text("Usando la voz de Android", style = MaterialTheme.typography.labelLarge, color = Color(0xFF8A5A00))
                Spacer(Modifier.height(4.dp))
                Text(
                    "Las voces propias no arrancaron. Mira el detalle en Ajustes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF4A3A12)
                )
            }
        }

        VoicePhase.SIN_VOZ -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BadRedSoft, RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFF0C9C4), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text("No hay voz", style = MaterialTheme.typography.labelLarge, color = BadRed)
                Spacer(Modifier.height(4.dp))
                Text(
                    speaker.errorDetail ?: "El motor de voz no pudo arrancar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink
                )
            }
        }

        VoicePhase.LISTO -> {
            if (speaker.busy) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF3EDE6), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text("🔊", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(10.dp))
                    Text("Preparando el audio…", style = MaterialTheme.typography.bodyMedium, color = accent)
                }
            }
        }
    }
}
