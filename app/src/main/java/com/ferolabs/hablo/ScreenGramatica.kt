package com.ferolabs.hablo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp

/**
 * **Gramática**: la biblioteca de fichas de teoría para consultarlas sin
 * entrar a la lección (auditoría de Cowork, 16-09, "falta 1": para releer
 * *used to* había que abrir la lección). Instrucción explícita de gramática:
 * d = 0,96 (Norris & Ortega 2000). Arriba, las fichas de REFERENCIA que no son
 * de ninguna lección (`assets/content/referencia.json`, las escribe Cowork:
 * verbos irregulares, pronombres, números y fechas, in/on/at); debajo, las 88
 * fichas del curso por nivel y unidad, con ✓ en las lecciones aprobadas.
 * Tocar una abre la misma [FichaScreen] de la lección.
 */
@Composable
fun GramaticaScreen(
    teacher: Teacher,
    store: Store,
    onBack: () -> Unit
) {
    val accent = Color(teacher.color)
    var abierta by remember { mutableStateOf<Lesson?>(null) }
    BackHandler(enabled = abierta != null) { abierta = null }

    val actual = abierta
    if (actual != null) {
        FichaScreen(lesson = actual, accent = accent, primeraVez = false, onDone = { abierta = null }, onBack = { abierta = null }, textoBoton = "Volver a la lista")
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar("📖 Gramática", onBack = onBack)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f).padding(20.dp)) {
            item {
                Text(
                    "Todas las fichas de teoría, para releerlas cuando quieras sin entrar a la lección. " +
                        "Cada una lleva la trampa del que piensa en español.",
                    style = MaterialTheme.typography.bodyMedium, color = InkSoft
                )
            }
            if (Course.referencia.isNotEmpty()) {
                item(key = "ref") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Pill("Referencia", accent, Color(teacher.softColor))
                        Spacer(Modifier.size(10.dp))
                        Text("Tablas para consultar", style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(Course.referencia, key = { "ref-" + it.id }) { l -> FilaFicha(l, false, accent) { abierta = l } }
            }
            for (level in Course.levels) {
                item(key = "nivel-" + level.id) {
                    val hechas = level.units.sumOf { u -> u.lessons.count { store.isCompleted(it.id) } }
                    val total = level.units.sumOf { it.lessons.size }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                        Pill(level.id.uppercase(), accent, Color(teacher.softColor))
                        Spacer(Modifier.size(10.dp))
                        Text(level.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("$hechas de $total", style = MaterialTheme.typography.labelLarge, color = InkSoft)
                    }
                }
                for (unit in level.units) {
                    item(key = "unidad-" + unit.id) {
                        Text(
                            "${unit.emoji} ${unit.title}",
                            style = MaterialTheme.typography.titleSmall, color = InkSoft,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    items(unit.lessons.filter { it.theory.body.isNotBlank() }, key = { it.id }) { l ->
                        FilaFicha(l, store.isCompleted(l.id), accent) { abierta = l }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaFicha(l: Lesson, aprobada: Boolean, accent: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, if (aprobada) GoodGreen.copy(alpha = 0.4f) else Line, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(l.theory.title, style = MaterialTheme.typography.titleMedium)
            Text(l.title, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
        }
        if (aprobada) Text("✓", style = MaterialTheme.typography.titleMedium, color = GoodGreen)
        else Text("›", style = MaterialTheme.typography.titleLarge, color = accent)
    }
}
