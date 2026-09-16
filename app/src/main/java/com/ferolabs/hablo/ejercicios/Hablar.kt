package com.ferolabs.hablo.ejercicios

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ferolabs.hablo.BadRed
import com.ferolabs.hablo.BadRedSoft
import com.ferolabs.hablo.Exercise
import com.ferolabs.hablo.GoodGreen
import com.ferolabs.hablo.GoodGreenSoft
import com.ferolabs.hablo.InkSoft
import com.ferolabs.hablo.Line
import com.ferolabs.hablo.Listener
import com.ferolabs.hablo.NotHeardBox
import com.ferolabs.hablo.NotHeardReason
import com.ferolabs.hablo.PronunciationResult
import com.ferolabs.hablo.SoundReport
import com.ferolabs.hablo.SoundVerdictCard
import com.ferolabs.hablo.SpeakerButton
import com.ferolabs.hablo.Teacher
import com.ferolabs.hablo.WordScore

/**
 * Los dos ejercicios de HABLAR: decirlo en voz alta (`speak`, con veredicto
 * por fonema del sonido del ejercicio) y repetir con la profesora (`shadow`,
 * solo ritmo y palabras, nunca fonemas). Sacados de ScreenLesson.kt tal cual
 * (2026-09-16). La grabación y el permiso del micrófono siguen en la
 * pantalla: aquí solo llega el botón ([onMic]) y el resultado.
 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiloEnVozAlta(
    ex: Exercise.SpeakIt,
    teacher: Teacher,
    listener: Listener,
    speakResult: PronunciationResult?,
    speakReport: SoundReport?,
    speakNotHeard: NotHeardReason?,
    showHeard: Boolean,
    checked: Boolean,
    accent: Color,
    say: (String, Float) -> Unit,
    onMic: () -> Unit,
    onShowHeard: () -> Unit
) {
    Text("Dilo en voz alta", style = MaterialTheme.typography.titleLarge)
    Text(
        ex.text,
        style = MaterialTheme.typography.headlineMedium,
        color = accent
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpeakerButton(big = true, tint = accent) { say(ex.text, 1f) }
        SpeakerButton(slow = true, tint = accent.copy(alpha = 0.75f)) { say(ex.text, 0.6f) }
        Text(
            "Escucha a ${teacher.name} y después dilo tú.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkSoft
        )
    }

    BotonMicrofono(
        listener = listener,
        enabled = !listener.thinking && !checked,
        accent = accent,
        idle = "Toca y dilo",
        retry = speakResult != null || speakNotHeard != null,
        conBarra = true,
        onMic = onMic
    )

    speakNotHeard?.let { NotHeardBox(it) }

    // El "bien" del sonido solo vale sobre palabras que el dictado entendió.
    speakReport?.fiable(speakResult)?.let { SoundVerdictCard(it) }

    speakResult?.let { r ->
        Text(
            if (r.entendida) "Se te entendió: ${r.percent} % de las palabras"
            else "No se te entendió del todo: ${r.percent} % de las palabras",
            style = MaterialTheme.typography.titleSmall,
            color = if (r.entendida) GoodGreen else Color(0xFF8A5A00)
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
        // La transcripción cruda va plegada (decisión de 0.8): verla junto
        // a una palabra en rojo es lo que hacía visible la contradicción.
        if (!showHeard) {
            Text(
                "Ver lo que oyó el dictado →",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                modifier = Modifier.clickable { onShowHeard() }
            )
        } else {
            Text(
                "El dictado oyó: \"${r.heard}\"",
                style = MaterialTheme.typography.labelMedium,
                color = InkSoft
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepiteCon(
    ex: Exercise.Shadow,
    teacher: Teacher,
    listener: Listener,
    speakResult: PronunciationResult?,
    speakNotHeard: NotHeardReason?,
    checked: Boolean,
    teacherSeconds: Float,
    studentSeconds: Float,
    accent: Color,
    say: (String, Float) -> Unit,
    onMic: () -> Unit
) {
    Text("Repite con ${teacher.name}", style = MaterialTheme.typography.titleLarge)
    Text(ex.text, style = MaterialTheme.typography.headlineMedium, color = accent)
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpeakerButton(big = true, tint = accent) { say(ex.text, 1f) }
        Text(
            "Óyela y repítela ENSEGUIDA, siguiendo su ritmo, sin pausas. " +
                "Aquí no se juzga cada sonido: se practica el ritmo.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkSoft
        )
    }
    BotonMicrofono(
        listener = listener,
        enabled = !listener.thinking && !checked,
        accent = accent,
        idle = "Toca y repítela",
        retry = speakResult != null || speakNotHeard != null,
        conBarra = false,
        onMic = onMic
    )

    speakNotHeard?.let { NotHeardBox(it) }

    speakResult?.let { r ->
        Text(
            if (r.entendida) "Se te entendió: ${r.percent} % de las palabras"
            else "No se te entendió del todo: ${r.percent} % de las palabras",
            style = MaterialTheme.typography.titleSmall,
            color = if (r.entendida) GoodGreen else Color(0xFF8A5A00)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            r.words.forEach { sw ->
                val ok = sw.score == WordScore.BIEN
                Text(
                    sw.word,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (ok) GoodGreen else Color(0xFF8A5A00),
                    modifier = Modifier
                        .padding(vertical = 3.dp)
                        .background(if (ok) GoodGreenSoft else Color(0xFFFFF6E3), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        // Ritmo: un dato, no una nota. Nada de inventar un porcentaje.
        if (teacherSeconds > 0f && studentSeconds > 0f) {
            val ratio = studentSeconds / teacherSeconds
            Text(
                "Tú: %.1f s · %s: %.1f s — %s".format(
                    java.util.Locale("es"),
                    studentSeconds, teacher.name, teacherSeconds,
                    when {
                        ratio <= 1.15f -> "vas a su ritmo."
                        ratio <= 1.5f -> "vas bien, pégate más a su ritmo."
                        else -> "más seguido, sin pausas entre palabras."
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoft
            )
        }
    }
}

/** El micrófono grande con su rótulo (y la barra de nivel mientras graba, si [conBarra]). */
@Composable
private fun BotonMicrofono(
    listener: Listener,
    enabled: Boolean,
    accent: Color,
    idle: String,
    retry: Boolean,
    conBarra: Boolean,
    onMic: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(84.dp)
                .background(if (listener.recording) BadRed else accent, CircleShape)
                .clickable(enabled = enabled) { onMic() }
        ) {
            Text(
                if (listener.recording) "■" else "🎤",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                listener.thinking -> "Analizando…"
                listener.recording -> "Grabando… toca para terminar"
                retry -> "Toca para intentarlo otra vez"
                else -> idle
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (listener.recording) BadRed else InkSoft
        )
        if (conBarra && listener.recording) {
            Spacer(Modifier.height(8.dp))
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
}
