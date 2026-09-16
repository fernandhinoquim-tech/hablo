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
    claude: ClaudeLlm,
    progreso: Progreso,
    memoria: Memoria,
    engineId: String,
    onEngineChange: (String) -> Unit,
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
    var engine by remember { mutableStateOf(engineId) }
    val context = androidx.compose.ui.platform.LocalContext.current
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

            // --- El informe para practicar fuera de la app ------------------------
            // Idea de Fero (2026-09-13): que la app le entregue un archivo con lo
            // que hace bien y mal para subirlo a su proyecto de Claude y practicar
            // allí. Se genera entero en el teléfono: no cuesta nada ni usa la red.
            var informeUri by remember { mutableStateOf<android.net.Uri?>(null) }
            var informeAviso by remember { mutableStateOf("") }
            SettingsCard {
                Text("Tu informe para practicar", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Un archivo con tus sonidos flojos, las frases que fallaste, lo que la " +
                        "profesora te corrigió y qué te conviene practicar. Se guarda en " +
                        "Descargas y lo puedes subir a tu chat de Claude para seguir ahí.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink
                )
                Spacer(Modifier.height(12.dp))
                BigButton("Descargar mi informe", container = accent) {
                    val texto = progreso.informe(store, teacher)
                    val uri = progreso.guardarEnDescargas(texto)
                    informeUri = uri
                    informeAviso = if (uri != null) {
                        "Guardado en Descargas. Búscalo como hablo-…md"
                    } else {
                        "No se pudo guardar el archivo."
                    }
                }
                if (informeAviso.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(informeAviso, style = MaterialTheme.typography.labelMedium, color = InkSoft)
                }
                informeUri?.let { uri ->
                    Spacer(Modifier.height(10.dp))
                    BigButton("Enviarlo a Claude", container = InkSoft) {
                        progreso.compartir(context, uri)
                    }
                }
                if (!progreso.hayAlgo()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Todavía no hay errores anotados: practica pronunciación o haz una " +
                            "lección y el informe se llena solo.",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkSoft
                    )
                }
            }

            // --- Quién responde en la conversación --------------------------------
            SettingsCard {
                Text("Tu profesora de conversación", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Quién contesta cuando hablas con ella. Solo viaja el texto de la charla; " +
                        "tu voz nunca sale del teléfono, y las lecciones y la pronunciación " +
                        "funcionan igual sin internet.",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSoft
                )
                Spacer(Modifier.height(10.dp))

                val opciones = buildList {
                    ClaudeLlm.MODELOS.forEach { (id, texto) ->
                        add(EngineOption(id, texto.first, texto.second, claude.keyPresent(), "Falta la clave de Claude"))
                    }
                    add(EngineOption(Store.ENGINE_GEMINI, "Gemini (Google)", "gratis · a veces tarda o se agota", cloud.keyPresent(), "Falta la clave de Gemini"))
                    add(EngineOption(Store.ENGINE_LOCAL, "IA del teléfono", "sin internet · más lenta y más torpe", llm.modelPresent(), "Falta el modelo en el teléfono"))
                }
                opciones.forEach { op ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = op.disponible) {
                                engine = op.id
                                onEngineChange(op.id)
                                cloudTest = ""
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            if (engine == op.id) "●" else "○",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (op.disponible) accent else Line
                        )
                        Spacer(Modifier.size(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                op.nombre,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (op.disponible) Ink else InkSoft
                            )
                            Text(
                                if (op.disponible) op.detalle else op.falta,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (op.disponible) InkSoft else BadRed
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                BigButton("Probar la conexión", enabled = engine != Store.ENGINE_LOCAL, container = accent) {
                    cloudTest = "Probando…"
                    if (engine.startsWith("claude")) {
                        claude.model = engine
                        claude.testConnection { cloudTest = it }
                    } else {
                        cloud.testConnection { cloudTest = it }
                    }
                }
                if (cloudTest.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(cloudTest, style = MaterialTheme.typography.labelMedium, color = Ink)
                }
                val ultima = if (engine.startsWith("claude")) claude.status else cloud.status
                if (ultima.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Última respuesta: $ultima", style = MaterialTheme.typography.labelSmall, color = InkSoft)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Claude se paga por uso con tu clave; el precio de arriba es a 30 turnos " +
                        "diarios. Gemini es gratis pero con tope diario, y Google puede usar ese " +
                        "texto para mejorar sus productos.",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSoft
                )
            }

            SettingsCard {
                Text("Tu progreso", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${store.xp} puntos · " + (if (store.streak == 1) "racha de 1 día" else "racha de ${store.streak} días"),
                    style = MaterialTheme.typography.bodyLarge
                )
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
                                progreso.borrarTodo()
                                memoria.borrar()
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

            // --- Lo que la profesora recuerda (charla libre) ---------------------
            var memoriaTick by remember { mutableStateOf(0) }
            SettingsCard {
                Text("Lo que ${teacher.name} recuerda de ti", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                Spacer(Modifier.height(8.dp))
                val hay = remember(memoriaTick) { memoria.cargar(); memoria.hayAlgo() || memoria.errores.isNotEmpty() }
                if (!hay) {
                    Text(
                        "Todavía nada. En \"Hablar de todo\" va guardando lo básico: tu nombre, de qué hablaron y los errores que te corrigió.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSoft
                    )
                } else {
                    val d = memoria.datos
                    val e = memoria.errores
                    Text(
                        (if (d.size == 1) "1 dato" else "${d.size} datos") + " · " +
                            (if (e.size == 1) "1 error que vigila" else "${e.size} errores que vigila") +
                            (if (memoria.actualizado.isNotBlank()) " · al día ${memoria.actualizado}" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink
                    )
                    if (d.isNotEmpty()) Text(
                        d.joinToString(" · ") { "${it.k}: ${it.v}" },
                        style = MaterialTheme.typography.labelMedium,
                        color = InkSoft
                    )
                    if (memoria.resumen.isNotBlank()) Text(
                        "Última charla: ${memoria.resumen}",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkSoft
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Que lo olvide todo",
                        style = MaterialTheme.typography.labelLarge,
                        color = BadRed,
                        modifier = Modifier.clickable { memoria.borrar(); memoriaTick += 1 }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Solo la charla libre usa esta memoria; los escenarios arrancan de cero. Vive en tu celular.",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSoft
                )
            }

            SettingsCard {
                Text("Diagnóstico", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                Spacer(Modifier.height(8.dp))
                Text(
                    speaker.diagnostics(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink
                )
                Spacer(Modifier.height(6.dp))
                // El antiguo "Laboratorio de IA (Fase 3)" (cargar/probar/liberar a mano)
                // se quitó: la conversación carga y suelta el modelo sola.
                val presente = llm.modelPresent()
                Text(
                    if (presente) "IA del teléfono: modelo ${Llm.MODEL_NAME} presente (%.1f GB).".format(
                        java.util.Locale("es"), llm.modelFile.length() / 1_000_000_000.0
                    ) else "IA del teléfono: falta el modelo. Se copia por USB a:\n${llm.modelFile.absolutePath}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (presente) Ink else BadRed
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
                    "Las lecciones, la pronunciación y tu progreso viven solo en este celular, sin internet. " +
                        "La única excepción es la conversación: si arriba eliges Claude o Gemini, sale por " +
                        "internet solo el texto de la charla (nunca tu voz); con la IA del teléfono, nada sale.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Voces: Piper (MIT) sobre sherpa-onnx (Apache 2.0). Dictado: Moonshine y " +
                        "Parakeet. Fonemas: wav2vec2. Conversación: Claude API o Gemini API (texto), " +
                        "o llama.cpp + Qwen3 8B en el teléfono. Todo dentro de la app o en su carpeta.",
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

/** Una opción de la lista "quién te responde". */
private data class EngineOption(
    val id: String,
    val nombre: String,
    val detalle: String,
    val disponible: Boolean,
    val falta: String
)
