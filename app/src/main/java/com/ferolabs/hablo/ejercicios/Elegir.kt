package com.ferolabs.hablo.ejercicios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ferolabs.hablo.Exercise
import com.ferolabs.hablo.InkSoft
import com.ferolabs.hablo.OptionRow
import com.ferolabs.hablo.SpeakerButton
import com.ferolabs.hablo.Teacher

/**
 * Los tres ejercicios de ELEGIR entre opciones: escuchar y elegir (`listen`),
 * traducir eligiendo (`translate`) y el par mínimo de oído (`minimalPair`).
 * Sacados de ScreenLesson.kt tal cual (2026-09-16): la pantalla solo les pasa
 * su estado (qué eligió, si ya comprobó) y recibe el toque.
 */

@Composable
fun EscuchaYElige(
    ex: Exercise.ListenChoose,
    pos: Int,
    chosen: Int,
    checked: Boolean,
    accent: Color,
    say: (String, Float) -> Unit,
    onChoose: (Int) -> Unit
) {
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
        OptionRow(ex.options[i], i, chosen, checked, ex.answerIndex, accent) { if (!checked) onChoose(i) }
    }
}

@Composable
fun TraduceEligiendo(
    ex: Exercise.TranslateChoose,
    pos: Int,
    chosen: Int,
    checked: Boolean,
    accent: Color,
    onChoose: (Int) -> Unit
) {
    val order = remember(pos) { ex.options.indices.shuffled() }
    Text("¿Cómo se dice en inglés?", style = MaterialTheme.typography.titleLarge)
    Text(
        "\"${ex.es}\"",
        style = MaterialTheme.typography.headlineMedium,
        color = accent
    )
    order.forEach { i ->
        OptionRow(ex.options[i], i, chosen, checked, ex.answerIndex, accent) { if (!checked) onChoose(i) }
    }
}

@Composable
fun CualPalabraOiste(
    ex: Exercise.MinimalPair,
    pos: Int,
    chosen: Int,
    checked: Boolean,
    teacher: Teacher,
    accent: Color,
    say: (String, Float) -> Unit,
    onChoose: (Int) -> Unit
) {
    val order = remember(pos) { ex.options.indices.shuffled() }
    Text("¿Cuál palabra oíste?", style = MaterialTheme.typography.titleLarge)
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpeakerButton(big = true, tint = accent) { say(ex.spoken, 1f) }
        SpeakerButton(slow = true, tint = accent.copy(alpha = 0.75f)) { say(ex.spoken, 0.7f) }
        Text(
            "${teacher.name} dice UNA de estas. Toca la que oíste.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkSoft
        )
    }
    order.forEach { i ->
        OptionRow(ex.options[i], i, chosen, checked, ex.answerIndex, accent) { if (!checked) onChoose(i) }
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
