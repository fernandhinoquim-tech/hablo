package com.ferolabs.hablo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Botón principal, grande y fácil de tocar. */
@Composable
fun BigButton(
    text: String,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = Color.White,
            disabledContainerColor = Line,
            disabledContentColor = InkSoft
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Botón redondo con el altavoz, para volver a escuchar una frase. */
@Composable
fun SpeakerButton(
    big: Boolean = false,
    slow: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    val d = if (big) 76.dp else 46.dp
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(d)
            .background(tint, CircleShape)
            .clickable { onClick() }
    ) {
        Text(
            text = if (slow) "🐢" else "🔊",
            style = if (big) MaterialTheme.typography.headlineMedium
            else MaterialTheme.typography.titleMedium
        )
    }
}

/** Tarjetita informativa con el consejo para hispanohablantes. */
@Composable
fun TipBox(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF6E3), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFF0DFB9), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(
            "OJO CON ESTO",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF8A5A00)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4A3A12)
        )
    }
}

/**
 * Aviso de "no te entendí": el audio no sirvió para evaluar. No es un error
 * del alumno y por eso no lleva porcentaje ni palabras en rojo.
 */
@Composable
fun NotHeardBox(reason: NotHeardReason) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF6E3), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFF0DFB9), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(
            "No te entendí, repite",
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF8A5A00)
        )
        Text(
            reason.hintEs,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4A3A12)
        )
        Text(
            "Esto no cuenta como error.",
            style = MaterialTheme.typography.labelMedium,
            color = InkSoft
        )
    }
}

/**
 * Veredicto del sonido que entrena el ejercicio, fonema por fonema (GOP).
 * Es lo primero que se ve: una palabra por fonema evaluado, verde / amarilla
 * / roja, y una sola frase de resumen. Nunca la transcripción cruda.
 */
@Composable
fun SoundVerdictCard(report: SoundReport) {
    val worst = report.worst
    val bg = when (worst) {
        WordScore.BIEN -> GoodGreenSoft
        WordScore.DUDOSO -> Color(0xFFFFF6E3)
        WordScore.MAL -> BadRedSoft
    }
    val fg = when (worst) {
        WordScore.BIEN -> GoodGreen
        WordScore.DUDOSO -> Color(0xFF8A5A00)
        WordScore.MAL -> BadRed
    }
    val failed = report.items.filter { it.verdict == WordScore.MAL }.map { it.word }.distinct()
    val doubtful = report.items.filter { it.verdict == WordScore.DUDOSO }.map { it.word }.distinct()
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(14.dp))
            .border(1.dp, fg.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(
            "Sonido: ${report.sound.labelEs}",
            style = MaterialTheme.typography.labelMedium,
            color = InkSoft
        )
        Text(
            when {
                failed.isNotEmpty() -> "Se te fue en: ${failed.joinToString(", ")}"
                doubtful.isNotEmpty() -> "Casi. Revisa: ${doubtful.joinToString(", ")}"
                else -> "¡Ese sonido salió bien!"
            },
            style = MaterialTheme.typography.titleMedium,
            color = fg
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            report.items.forEach { item ->
                val (ibg, ifg, mark) = when (item.verdict) {
                    WordScore.BIEN -> Triple(Color.White, GoodGreen, "✓")
                    WordScore.DUDOSO -> Triple(Color.White, Color(0xFF8A5A00), "~")
                    WordScore.MAL -> Triple(Color.White, BadRed, "✗")
                }
                Text(
                    "${item.word} $mark",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ifg,
                    modifier = Modifier
                        .background(ibg, RoundedCornerShape(8.dp))
                        .border(1.dp, ifg.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/** Etiqueta pequeña tipo píldora. */
@Composable
fun Pill(text: String, fg: Color, bg: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = Modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** Encabezado simple con botón de volver. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        if (onBack != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onBack() }
            ) {
                Text("←", style = MaterialTheme.typography.titleLarge, color = InkSoft)
            }
        } else {
            Box(modifier = Modifier.size(8.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) trailing()
    }
}
