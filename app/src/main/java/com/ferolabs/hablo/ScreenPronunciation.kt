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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PronunciationScreen(
    teacher: Teacher,
    speaker: Speaker,
    listener: Listener,
    say: (String, Float) -> Unit,
    onBack: () -> Unit
) {
    val accent = Color(teacher.color)

    var index by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<PronunciationResult?>(null) }
    var showTip by remember { mutableStateOf(false) }
    var permissionAsked by remember { mutableStateOf(false) }

    val drill = DRILLS[index % DRILLS.size]

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionAsked = true
        if (granted) {
            listener.startRecording { heard ->
                result = scorePronunciation(drill.text, heard)
            }
        }
    }

    fun record() {
        if (listener.recording) {
            listener.stopRecording()
            return
        }
        result = null
        if (listener.hasMicPermission()) {
            listener.startRecording { heard ->
                result = scorePronunciation(drill.text, heard)
            }
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        TopBar("Pronunciación  ·  ${index + 1}/${DRILLS.size}", onBack = onBack)

        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {

            Pill("Enfoque: ${drill.focusEs}", accent, Color(teacher.softColor))

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

            // --- Resultado --------------------------------------------------
            result?.let { r ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Line, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${r.percent}%",
                            style = MaterialTheme.typography.headlineLarge,
                            color = when {
                                r.percent >= 80 -> GoodGreen
                                r.percent >= 50 -> Color(0xFF8A5A00)
                                else -> BadRed
                            },
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
                        if (r.heard.isBlank()) "No entendí nada. ¿Estabas muy lejos del micrófono?"
                        else "Entendí: \"${r.heard}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSoft
                    )

                    Text(
                        "El botón 👤 reproduce tu propia grabación. Compárala con la de ${teacher.name}.",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkSoft
                    )
                }
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
                index = (index + 1) % DRILLS.size
                result = null
                showTip = false
            }
        }
    }
}
