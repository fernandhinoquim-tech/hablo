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
