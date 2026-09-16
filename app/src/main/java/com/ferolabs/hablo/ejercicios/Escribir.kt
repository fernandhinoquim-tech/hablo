package com.ferolabs.hablo.ejercicios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ferolabs.hablo.BadRed
import com.ferolabs.hablo.BadRedSoft
import com.ferolabs.hablo.Exercise
import com.ferolabs.hablo.Ink
import com.ferolabs.hablo.InkSoft
import com.ferolabs.hablo.Repaso
import com.ferolabs.hablo.SpeakerButton

/**
 * Los cuatro ejercicios de ESCRIBIR: dictado (`type`), escribir en inglés
 * (`write`), completar el hueco (`cloze`) y "corrige tu propio error"
 * (`FixIt`, del repaso). Sacados de ScreenLesson.kt tal cual (2026-09-16).
 * Los cuatro comparten el mismo estado: lo escrito y si ya comprobó.
 */

@Composable
fun EscribeLoQueEscuchas(
    ex: Exercise.TypeWhatYouHear,
    typed: String,
    checked: Boolean,
    accent: Color,
    say: (String, Float) -> Unit,
    onTyped: (String) -> Unit
) {
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
        onValueChange = { if (!checked) onTyped(it) },
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

@Composable
fun EscribeloEnIngles(
    ex: Exercise.WriteIt,
    typed: String,
    checked: Boolean,
    onTyped: (String) -> Unit
) {
    Text("Escríbelo en inglés", style = MaterialTheme.typography.titleLarge)
    Text(ex.es, style = MaterialTheme.typography.headlineSmall, color = Ink)
    OutlinedTextField(
        value = typed,
        onValueChange = { if (!checked) onTyped(it) },
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

@Composable
fun CompletaElHueco(
    ex: Exercise.Cloze,
    typed: String,
    checked: Boolean,
    onTyped: (String) -> Unit
) {
    Text("Completa el hueco", style = MaterialTheme.typography.titleLarge)
    Text(
        ex.before + "______" + ex.after,
        style = MaterialTheme.typography.headlineSmall,
        color = Ink
    )
    Text("Significa: ${ex.es}", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
    OutlinedTextField(
        value = typed,
        onValueChange = { if (!checked) onTyped(it) },
        singleLine = true,
        readOnly = checked,
        placeholder = { Text("Lo que va en el hueco...") },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun CorrigeTuError(
    ex: Exercise.FixIt,
    typed: String,
    checked: Boolean,
    onTyped: (String) -> Unit
) {
    Text("Corrige tu propio error", style = MaterialTheme.typography.titleLarge)
    Text(
        "Esto lo escribiste tú el ${Repaso.fechaLarga(ex.fecha)}:",
        style = MaterialTheme.typography.bodyMedium,
        color = InkSoft
    )
    if (ex.hueco != null) Text(
        ex.hueco.before + "______" + ex.hueco.after,
        style = MaterialTheme.typography.bodyLarge,
        color = InkSoft
    )
    Text(
        "«${ex.tuya}»",
        style = MaterialTheme.typography.headlineSmall,
        color = BadRed,
        modifier = Modifier
            .fillMaxWidth()
            .background(BadRedSoft, RoundedCornerShape(12.dp))
            .padding(12.dp)
    )
    Text(
        if (ex.hueco != null) "¿Qué iba en el hueco? Escríbelo bien." else "¿Qué le falta o qué le sobra? Escríbela bien.",
        style = MaterialTheme.typography.bodyMedium,
        color = Ink
    )
    OutlinedTextField(
        value = typed,
        onValueChange = { if (!checked) onTyped(it) },
        singleLine = true,
        readOnly = checked,
        placeholder = { Text(if (ex.hueco != null) "Lo que va en el hueco..." else "La frase, ya corregida...") },
        modifier = Modifier.fillMaxWidth()
    )
}
