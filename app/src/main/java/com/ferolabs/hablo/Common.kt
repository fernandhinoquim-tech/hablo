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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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

/**
 * "¿Te sonó como a la profesora?": la opinión del alumno sobre su propia toma,
 * después de oírse. Alimenta la recalibración en el PC; no cambia nada en el
 * momento. Vale sobre todo para lo que uno sí puede oír (una h que se cae, una
 * sílaba de más); para ship/sheep o v/b el oído del alumno es justo el problema.
 */
@Composable
fun SelfLabelRow(answered: Boolean, onLabel: (String) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        if (answered) {
            Text("Gracias. Eso me ayuda a afinar la evaluación.", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
            return@Column
        }
        Text(
            "Oye a la profesora (🔊) y luego a ti (👤). ¿Te sonó igual?",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Igual" to "igual", "Distinto" to "distinto", "No sé" to "nose").forEach { (label, key) ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = Ink,
                    modifier = Modifier
                        .background(Cream, RoundedCornerShape(10.dp))
                        .border(1.dp, Line, RoundedCornerShape(10.dp))
                        .clickable { onLabel(key) }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }
        }
    }
}

/**
 * Retrato de la profesora, quieto. Mientras [speaking] es true, un halo de su
 * color late alrededor, como en los asistentes de voz: dice "está hablando"
 * sin fingir una boca (el truco de dos cuadros se probó y se veía falso).
 * Con [showFace] en false no hay foto: un círculo con la inicial y el mismo halo.
 */
@Composable
fun TeacherAvatar(
    teacher: Teacher,
    speaking: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    showFace: Boolean = true
) {
    val accent = Color(teacher.color)
    val pulse = rememberInfiniteTransition(label = "halo")
    val t by pulse.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "ring"
    )
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        if (speaking) {
            // dos ondas desfasadas que se expanden y se apagan
            for (phase in 0..1) {
                val p = (t + phase * 0.5f) % 1f
                Box(
                    modifier = Modifier
                        .size(size)
                        .graphicsLayer {
                            scaleX = 1f + 0.32f * p
                            scaleY = 1f + 0.32f * p
                            alpha = (1f - p) * 0.55f
                        }
                        .border(3.dp, accent, CircleShape)
                )
            }
        }
        if (showFace) {
            Image(
                painter = painterResource(teacher.avatar),
                contentDescription = teacher.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .border(2.dp, accent.copy(alpha = if (speaking) 0.9f else 0.45f), CircleShape)
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(size)
                    .background(Color(teacher.softColor), CircleShape)
                    .border(2.dp, accent.copy(alpha = if (speaking) 0.9f else 0.45f), CircleShape)
            ) {
                Text(
                    teacher.name.take(1),
                    style = MaterialTheme.typography.headlineMedium,
                    color = accent
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
