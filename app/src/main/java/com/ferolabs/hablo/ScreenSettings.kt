package com.ferolabs.hablo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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

@Composable
fun SettingsScreen(
    teacher: Teacher,
    store: Store,
    speaker: Speaker,
    llm: Llm,
    cloud: CloudLlm,
    onCloudChange: (Boolean) -> Unit,
    onChangeTeacher: () -> Unit,
    onTestVoice: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    val accent = Color(teacher.color)
    var speed by remember { mutableStateOf(store.speechScale) }
    var confirmReset by remember { mutableStateOf(false) }
    var showFaces by remember { mutableStateOf(store.showFaces) }
    var useCloud by remember { mutableStateOf(store.cloudConversation) }
    var cloudTest by remember { mutableStateOf("") }
    val appVersion = remember {
        try {
            val context = store.context
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (e: Throwable) { "?" }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar("Ajustes", onBack = onBack)

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {

            SettingsCard {
                Text("Tu profesora", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeacherAvatar(teacher = teacher, speaking = speaker.busy, size = 56.dp, showFace = showFaces)
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(teacher.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${teacher.tagline} · ${teacher.accent.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSoft
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                BigButton("Cambiar de profesora", container = accent) { onChangeTeacher() }
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showFaces = !showFaces
                            store.showFaces = showFaces
                        }
                ) {
                    Text(
                        "Mostrar la cara de la profesora",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (showFaces) "Sí" else "Solo la voz",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent
                    )
                }
            }

            SettingsCard {
                Text("Velocidad de la voz", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        speed < 0.8f -> "Muy despacio"
                        speed < 0.95f -> "Despacio"
                        speed < 1.15f -> "Normal"
                        else -> "Rápido"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    onValueChangeFinished = { onSpeedChange(speed) },
                    valueRange = 0.6f..1.4f,
                    steps = 7,
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = Line
                    )
                )
                Spacer(Modifier.height(4.dp))
                BigButton("Probar la voz", container = accent) { onTestVoice(speed) }
            }

            // --- Conversación por internet (Gemini) ------------------------------
            SettingsCard {
                Text("Conversación por internet", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                Spacer(Modifier.height(8.dp))
                val hasKey = cloud.keyPresent()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = hasKey) {
                            useCloud = !useCloud
                            onCloudChange(useCloud)
                        }
                ) {
                    Text(
                        "La profesora responde con Gemini (Google)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hasKey) Ink else InkSoft,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (!hasKey) "Sin clave" else if (useCloud) "Encendido" else "Apagado",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (hasKey) accent else BadRed
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (hasKey)
                        "Solo se envía el texto de la conversación (lo que dijiste, ya transcrito en el " +
                            "teléfono, y lo que ella responde). Nunca tu voz. Lecciones, pronunciación y " +
                            "voces siguen sin internet. Nivel gratuito de Google: unos 100 turnos al día " +
                            "con los modelos buenos y 500 más con el rápido; Google puede usar ese texto " +
                            "para mejorar sus productos."
                    else
                        "Falta la clave de Gemini. Se copia una vez por USB a:\n${cloud.keyFile.absolutePath}",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSoft
                )
                if (hasKey) {
                    Spacer(Modifier.height(10.dp))
                    BigButton("Probar la conexión", container = accent) {
                        cloudTest = "Probando…"
                        cloud.testConnection { cloudTest = it }
                    }
                    if (cloudTest.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(cloudTest, style = MaterialTheme.typography.labelMedium, color = Ink)
                    }
                    if (cloud.status.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Última respuesta: ${cloud.status}", style = MaterialTheme.typography.labelSmall, color = InkSoft)
                    }
                }
            }

            // --- Laboratorio de IA (Fase 3): medir antes de construir ----------
            var reply by remember { mutableStateOf("") }
            SettingsCard {
                Text("Laboratorio de IA (Fase 3)", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                Spacer(Modifier.height(8.dp))
                val present = llm.modelPresent()
                Text(
                    if (present) "Modelo: ${Llm.MODEL_NAME} (${llm.modelFile.length() / 1_000_000_000.0} GB)"
                    else "Falta el modelo. Se copia por USB a:\n${llm.modelFile.absolutePath}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (present) Ink else BadRed
                )
                Spacer(Modifier.height(8.dp))
                if (llm.status.isNotBlank()) {
                    Text(llm.status, style = MaterialTheme.typography.labelMedium, color = InkSoft)
                    Spacer(Modifier.height(8.dp))
                }
                if (!llm.loaded) {
                    BigButton("Cargar el modelo", enabled = present && !llm.busy, container = accent) {
                        speaker.stop()
                        llm.load()
                    }
                } else {
                    BigButton("Probar: preséntate en inglés", enabled = !llm.busy, container = accent) {
                        reply = ""
                        llm.chat(
                            messages = listOf(
                                "system" to "You are ${teacher.name}, a warm English teacher for Spanish speakers. " +
                                    "Reply in simple English (A2 level), two sentences maximum. /no_think",
                                "user" to "Hi! Please introduce yourself and ask me one question."
                            ),
                            onToken = { piece -> reply += piece },
                            // Ciclo de voz completo: lo que escribe la IA lo dice la profesora.
                            onDone = { if (reply.isNotBlank()) speaker.speak(reply.trim(), teacher, speed) }
                        )
                    }
                    if (reply.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(reply.trim(), style = MaterialTheme.typography.bodyLarge, color = Ink)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Liberar el modelo",
                        style = MaterialTheme.typography.labelLarge,
                        color = InkSoft,
                        modifier = Modifier.clickable { llm.release() }
                    )
                }
            }

            SettingsCard {
                Text("Tu progreso", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                Spacer(Modifier.height(8.dp))
                Text("${store.xp} puntos · racha de ${store.streak} días", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                if (!confirmReset) {
                    Text(
                        "Borrar todo mi progreso",
                        style = MaterialTheme.typography.labelLarge,
                        color = BadRed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BadRedSoft, RoundedCornerShape(12.dp))
                            .clickable { confirmReset = true }
                            .padding(14.dp)
                    )
                } else {
                    Text(
                        "¿Seguro? Esto no se puede deshacer.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BadRed
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            BigButton("Sí, borrar", container = BadRed) {
                                onReset()
                                confirmReset = false
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            BigButton("Cancelar", container = InkSoft) { confirmReset = false }
                        }
                    }
                }
            }

            SettingsCard {
                Text("Diagnóstico del motor de voz", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                Spacer(Modifier.height(8.dp))
                Text(
                    speaker.diagnostics(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Si algo dice FALTA o aparece un error, mándame este texto.",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSoft
                )
            }

            SettingsCard {
                Text("Sobre Hablo", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                Spacer(Modifier.height(6.dp))
                Text("Versión $appVersion", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Todo funciona sin internet y tu progreso vive solo en este celular. " +
                        "La única excepción, si la enciendes arriba, es la conversación con Gemini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Voces: Piper (MIT) sobre sherpa-onnx (Apache 2.0). Dictado: Moonshine y " +
                        "Parakeet. Fonemas: wav2vec2. IA en el teléfono: llama.cpp + Qwen3 8B. " +
                        "Todo dentro de la app o en su carpeta.",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSoft
                )
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        content()
    }
}
