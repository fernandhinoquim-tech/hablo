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
    onGreeting: () -> Unit
) {
    val accent = Color(teacher.color)

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
    val level = remember(refreshKey) { Course.levels.firstOrNull() }

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
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(teacher.softColor), CircleShape)
                        .clickable { onGreeting() }
                ) {
                    Text(teacher.emoji, style = MaterialTheme.typography.titleLarge)
                }
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
                StatBox("Racha", "$streak", if (streak > 0) "días 🔥" else "empieza hoy", Modifier.weight(1f))
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

        if (level != null) {
            item {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(level.id, accent, Color(teacher.softColor))
                    Text(level.title, style = MaterialTheme.typography.titleLarge)
                }
                if (level.goal.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(level.goal, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                }
            }
        }

        items(units, key = { it.id }) { unit ->
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

        item {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .border(1.dp, Line, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Text("Lo que viene", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "💬  Conversar con ${teacher.name} de verdad, con IA dentro del celular\n" +
                        "👩  Verla mientras te habla y te corrige\n" +
                        "📚  Vocabulario con repetición espaciada\n" +
                        "✍️  Corrección de tu escritura explicada en español",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Versión $appVersion · ${Course.allLessons().size} lecciones · todo sin internet",
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
