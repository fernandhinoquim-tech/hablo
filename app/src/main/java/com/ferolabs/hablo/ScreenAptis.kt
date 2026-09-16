package com.ferolabs.hablo

import android.Manifest
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/*
 * Pantallas del diagnóstico del Modo Aptis (etapa 5, primer paso). Ver Aptis.kt
 * para las reglas. Cinco partes que se hacen por separado; el resultado (una
 * tarjeta por destreza y el piso arriba en grande) aparece cuando están las cinco.
 */

private val AvisoFondo = Color(0xFFFFF1DB)
private val AvisoBorde = Color(0xFFE6B970)
private val AvisoTinta = Color(0xFF6B3F00)

/** El aviso obligatorio: en la portada, en cada parte estimada por IA y en el resultado. */
@Composable
fun AvisoAptis() {
    Text(
        AVISO_APTIS,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        color = AvisoTinta,
        modifier = Modifier
            .fillMaxWidth()
            .background(AvisoFondo, RoundedCornerShape(12.dp))
            .border(1.dp, AvisoBorde, RoundedCornerShape(12.dp))
            .padding(14.dp)
    )
}

private fun mmss(seg: Int): String = "%d:%02d".format(Locale.US, seg / 60, seg % 60)

private fun colorNivel(n: NivelAptis?): Color = when (n) {
    null -> InkSoft
    NivelAptis.BAJO_A2, NivelAptis.A2 -> BadRed
    NivelAptis.B1 -> GoodGreen
    NivelAptis.B2 -> GoodGreen
}

@Composable
private fun Tarjeta(content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) { content() }
}

// ---------------------------------------------------------------------------
// Portada: las cinco partes y, cuando están todas, el resultado
// ---------------------------------------------------------------------------

@Composable
fun AptisScreen(
    diag: Diagnostico,
    aptis: Aptis,
    teacher: Teacher,
    claude: ClaudeLlm,
    onParte: (String) -> Unit,
    onBack: () -> Unit
) {
    val accent = Color(teacher.color)
    val tick = aptis.tick
    val niveles = remember(tick) { aptis.niveles(diag) }
    val completo = remember(tick) { aptis.completo(diag) }
    var confirmarBorrado by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar("🎯 Modo Aptis · Diagnóstico", onBack = onBack)
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            if (completo) {
                ResultadoAptis(diag, aptis, accent)
                Spacer(Modifier.height(4.dp))
                Text("Las partes, por si quieres repetir alguna", style = MaterialTheme.typography.labelMedium, color = InkSoft)
            } else {
                AvisoAptis()
                Text(
                    "Unos ${diag.duracionMin} minutos en total, en ${diag.secciones.size} partes que puedes hacer por separado " +
                        "(${diag.tareas} tareas). Aptis exige B1 en las cuatro destrezas: lo que importa es cuál es tu piso, " +
                        "y ahí van las horas de estudio.",
                    style = MaterialTheme.typography.bodyMedium, color = InkSoft
                )
                if (!claude.keyPresent()) Text(
                    "Escritura y Habla las estima Claude por internet: hace falta la clave de Claude en el teléfono. " +
                        "Las otras tres partes no necesitan nada.",
                    style = MaterialTheme.typography.bodyMedium, color = BadRed
                )
            }
            for (s in diag.secciones) {
                val r = aptis.resultado(s.id)
                val nivel = niveles[s.id]
                val estado = when {
                    r == null -> "Pendiente · ${s.cuantas} tareas · ${s.minutos} min"
                    nivel != null -> "Hecha el ${r.fecha} · ${nivel.etiqueta} · toca para repetirla"
                    r.juicios.any { it.error != null } -> "Falta estimar: " + (r.juicios.firstOrNull { it.error != null }?.error ?: "") + " · toca para reintentar"
                    else -> "Falta estimar · toca para reintentar"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, if (nivel != null) GoodGreen.copy(alpha = 0.4f) else Line, RoundedCornerShape(16.dp))
                        .clickable { onParte(s.id) }
                        .padding(16.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(44.dp).background(Color(teacher.softColor), CircleShape)
                    ) { Text(s.emoji, style = MaterialTheme.typography.titleLarge) }
                    Spacer(Modifier.size(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${s.skill} · ${s.title}", style = MaterialTheme.typography.titleMedium)
                        Text(estado, style = MaterialTheme.typography.bodyMedium, color = if (r != null && nivel == null) BadRed else InkSoft)
                    }
                    if (nivel != null) Pill(nivel.etiqueta, Color.White, colorNivel(nivel))
                    else Text("›", style = MaterialTheme.typography.headlineMedium, color = accent)
                }
            }
            if (aptis.hechas(diag) > 0) {
                Spacer(Modifier.height(8.dp))
                if (!confirmarBorrado) Text(
                    "Borrar el diagnóstico y empezar de cero",
                    style = MaterialTheme.typography.labelLarge, color = InkSoft,
                    modifier = Modifier.clickable { confirmarBorrado = true }.padding(vertical = 6.dp)
                ) else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("¿Seguro? Se pierden las cinco partes.", style = MaterialTheme.typography.bodyMedium, color = BadRed, modifier = Modifier.weight(1f))
                    Text("Sí, borrar", style = MaterialTheme.typography.labelLarge, color = BadRed, modifier = Modifier.clickable { aptis.borrarTodo(); confirmarBorrado = false })
                    Text("No", style = MaterialTheme.typography.labelLarge, color = InkSoft, modifier = Modifier.clickable { confirmarBorrado = false })
                }
            }
        }
    }
}

/** Arriba en grande el piso; debajo, una tarjeta por destreza con nivel, prueba y UNA cosa que practicar. */
@Composable
private fun ResultadoAptis(diag: Diagnostico, aptis: Aptis, accent: Color) {
    val niveles = aptis.niveles(diag)
    val piso = aptis.piso(diag)
    val nivelPiso = piso.firstOrNull()?.let { niveles[it.id] }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(18.dp))
            .border(2.dp, colorNivel(nivelPiso), RoundedCornerShape(18.dp))
            .padding(20.dp)
    ) {
        Text("TU PISO", style = MaterialTheme.typography.labelLarge, color = InkSoft)
        Text(
            piso.joinToString(" y ") { it.skill },
            style = MaterialTheme.typography.headlineLarge, color = colorNivel(nivelPiso)
        )
        if (nivelPiso != null) Text(nivelPiso.etiqueta, style = MaterialTheme.typography.headlineMedium, color = colorNivel(nivelPiso))
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                nivelPiso == null -> ""
                nivelPiso < NivelAptis.B1 -> "Aptis pide B1 en las cuatro destrezas y aquí no llegas todavía: ahí van tus horas, no al promedio."
                else -> "Ya estás en B1 o más en las cuatro. Para subir, empieza por esta: la más floja manda."
            },
            style = MaterialTheme.typography.bodyMedium, color = Ink
        )
    }
    AvisoAptis()
    for (s in diag.secciones) {
        val r = aptis.resultado(s.id) ?: continue
        val n = niveles[s.id]
        Tarjeta {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${s.emoji} ${s.skill}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Pill(n?.etiqueta ?: "sin estimación", Color.White, colorNivel(n))
            }
            if (s.porIa) {
                for (j in r.juicios) {
                    val t = s.escritura.firstOrNull { it.id == j.id }?.level ?: s.habla.firstOrNull { it.id == j.id }?.level ?: j.level
                    if (j.valido) {
                        Text("Tarea de $t → ${j.nivel?.etiqueta}: «${j.cita}»", style = MaterialTheme.typography.bodyMedium, color = Ink)
                        if (j.razon.isNotBlank()) Text(j.razon, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                    } else Text("Tarea de $t: sin juicio (${j.error ?: "pendiente"})", style = MaterialTheme.typography.bodyMedium, color = BadRed)
                }
                val practica = r.juicios.filter { it.valido && it.practica.isNotBlank() }.minByOrNull { it.nivel!!.ordinal }?.practica
                if (practica != null) Text("Practica: $practica", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = accent)
            } else {
                Text(TarjetaAptis.justificacion(r.items), style = MaterialTheme.typography.bodyMedium, color = Ink)
                Text("Practica: " + TarjetaAptis.practica(s, r.items), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = accent)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Una parte
// ---------------------------------------------------------------------------

private enum class FaseParte { INTRO, TAREAS, JUZGAR, FIN }

@Composable
fun AptisParteScreen(
    seccion: SeccionAptis,
    aptis: Aptis,
    teacher: Teacher,
    speaker: Speaker,
    listener: Listener,
    claude: ClaudeLlm,
    say: (String, Float) -> Unit,
    sayQueued: (String) -> Unit,
    onDone: () -> Unit,
    onExit: () -> Unit
) {
    val accent = Color(teacher.color)
    val main = remember { Handler(Looper.getMainLooper()) }
    var fase by remember { mutableStateOf(FaseParte.INTRO) }
    var idx by remember { mutableStateOf(0) }
    val items = remember { mutableStateListOf<AciertoItem>() }
    val juicios = remember { mutableStateListOf<JuicioIa>() }
    var juzgando by remember { mutableStateOf(false) }
    // Lo que ya estaba guardado de esta parte (para reintentar la estimación sin repetirla).
    val previo = remember { aptis.resultado(seccion.id) }
    val esHabla = seccion.habla.isNotEmpty()

    fun guardarIa() = aptis.guardar(ResultadoSeccion(seccion.id, aptis.hoy(), juicios = juicios.toList()))

    fun terminarTareas() {
        if (seccion.porIa) {
            guardarIa()
            fase = FaseParte.JUZGAR
        } else {
            aptis.guardar(ResultadoSeccion(seccion.id, aptis.hoy(), items = items.toList()))
            fase = FaseParte.FIN
        }
    }

    fun responder(a: AciertoItem) {
        items.add(a)
        if (idx + 1 < seccion.cuantas) idx += 1 else terminarTareas()
    }

    fun entregar(j: JuicioIa) {
        juicios.add(j)
        if (idx + 1 < seccion.cuantas) idx += 1 else terminarTareas()
    }

    // Speaking: Parakeet (el que mejor adivina) y nunca el modelo de fonemas al lado.
    LaunchedEffect(Unit) {
        if (esHabla) { listener.releaseSounds(); listener.prepareConversation() }
    }
    DisposableEffect(Unit) {
        onDispose {
            speaker.stop()
            listener.stopRecording()
            if (esHabla) listener.releaseConversation()
        }
    }

    // La estimación por IA: en fila, guardando cada juicio al llegar.
    LaunchedEffect(fase) {
        if (fase == FaseParte.JUZGAR && !juzgando) {
            juzgando = true
            JuezAptis.juzgarPendientes(
                claude, seccion, juicios.toList(),
                onCada = { lista -> main.post { juicios.clear(); juicios.addAll(lista); guardarIa() } },
                onFin = { lista -> main.post { juicios.clear(); juicios.addAll(lista); guardarIa(); juzgando = false; fase = FaseParte.FIN } }
            )
        }
    }

    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { idx = 0; fase = FaseParte.TAREAS }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = when (fase) {
                FaseParte.INTRO -> "${seccion.emoji} ${seccion.skill} · ${seccion.title}"
                FaseParte.TAREAS -> "${seccion.emoji} ${seccion.skill} · ${idx + 1} de ${seccion.cuantas}"
                FaseParte.JUZGAR -> "${seccion.emoji} ${seccion.skill} · estimando"
                FaseParte.FIN -> "${seccion.emoji} ${seccion.skill} · listo"
            },
            onBack = onExit
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            when (fase) {
                FaseParte.INTRO -> {
                    Text(seccion.title, style = MaterialTheme.typography.headlineSmall, color = accent)
                    Text("${seccion.cuantas} tareas · unos ${seccion.minutos} minutos", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                    if (seccion.instruccion.isNotBlank()) Text(seccion.instruccion, style = MaterialTheme.typography.bodyLarge, color = Ink)
                    when (seccion.id) {
                        "core" -> Text("Elige una opción y toca «Siguiente». Si se acaba el tiempo, pasa sola. No hay corrección hasta el final.", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                        "listening" -> Text("No se muestra el texto: solo la voz de ${teacher.name}. La pregunta va en español.", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                        "writing" -> Text("Escribes en inglés con reloj. Cuando se acaba, se entrega lo que haya. Sin diccionario ni traductor: es para saber dónde estás.", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                        "speaking" -> Text("${teacher.name} te lee la pregunta; en las dos últimas tienes tiempo para preparar. Luego se graba hasta que se acabe el reloj o toques ■. Se mira lo que dices, no cómo suena.", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                    }
                    if (seccion.porIa) {
                        AvisoAptis()
                        if (!claude.keyPresent()) Text("Sin la clave de Claude en el teléfono esta parte no se puede estimar.", style = MaterialTheme.typography.bodyMedium, color = BadRed)
                    }
                    if (previo != null && seccion.porIa && previo.pendientesIa.isNotEmpty()) {
                        Text(
                            "Ya hiciste esta parte el ${previo.fecha}, pero falta la estimación de ${previo.pendientesIa.size} tarea(s)" +
                                (previo.pendientesIa.firstOrNull { it.error != null }?.error?.let { ": $it" } ?: "") + ".",
                            style = MaterialTheme.typography.bodyMedium, color = BadRed
                        )
                    }
                }

                FaseParte.TAREAS -> key(idx) {
                    when {
                        seccion.core.isNotEmpty() -> TareaCoreUi(seccion.core[idx], seccion.segundosPorItem, idx + 1, seccion.cuantas, accent) { puesto, ok ->
                            responder(AciertoItem(seccion.core[idx].id, seccion.core[idx].level, puesto, ok))
                        }
                        seccion.lectura.isNotEmpty() -> {
                            val t = seccion.lectura[idx]
                            Text("Tarea ${idx + 1} de ${seccion.cuantas} · ${t.level}", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                            when (t) {
                                is TareaLectura.Completar -> TareaCompletarUi(t, accent) { puesto, ok -> responder(AciertoItem(t.id, t.level, puesto, ok)) }
                                is TareaLectura.Ordenar -> TareaOrdenarUi(t, accent) { puesto, ok -> responder(AciertoItem(t.id, t.level, puesto, ok)) }
                                is TareaLectura.Titulos -> TareaTitulosUi(t, accent) { puesto, ok -> responder(AciertoItem(t.id, t.level, puesto, ok)) }
                            }
                        }
                        seccion.escucha.isNotEmpty() -> {
                            val t = seccion.escucha[idx]
                            TareaEscuchaUi(t, idx + 1, seccion.cuantas, accent, teacher, speaker, sayQueued) { puesto, ok -> responder(AciertoItem(t.id, t.level, puesto, ok)) }
                        }
                        seccion.escritura.isNotEmpty() -> TareaEscrituraUi(seccion.escritura[idx], idx + 1, seccion.cuantas, accent) { entregar(it) }
                        seccion.habla.isNotEmpty() -> TareaHablaUi(seccion.habla[idx], idx + 1, seccion.cuantas, accent, listener, speaker, say) { entregar(it) }
                    }
                }

                FaseParte.JUZGAR -> {
                    val hechos = juicios.count { it.valido || it.error != null }
                    Text("Estimando con ${ClaudeLlm.nombreDe(JuezAptis.MODELO)}…", style = MaterialTheme.typography.headlineSmall, color = accent)
                    Text("Tarea ${minOf(hechos + 1, juicios.size)} de ${juicios.size}. Solo sale el texto, nunca el audio.", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                    LinearProgressIndicator(progress = { if (juicios.isEmpty()) 0f else hechos / juicios.size.toFloat() }, color = accent, trackColor = Line, modifier = Modifier.fillMaxWidth().height(6.dp))
                    for (j in juicios) TarjetaJuicio(seccion, j, accent)
                }

                FaseParte.FIN -> {
                    val r = aptis.resultado(seccion.id)
                    val nivel = r?.nivel(seccion)
                    Text("Parte hecha", style = MaterialTheme.typography.headlineSmall, color = accent)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${seccion.skill}:", style = MaterialTheme.typography.titleMedium)
                        Pill(nivel?.etiqueta ?: "sin estimación", Color.White, colorNivel(nivel))
                    }
                    if (r != null && !seccion.porIa) {
                        Text(TarjetaAptis.justificacion(r.items), style = MaterialTheme.typography.bodyLarge, color = Ink)
                        Text("Practica: " + TarjetaAptis.practica(seccion, r.items), style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                    }
                    if (r != null && seccion.porIa) {
                        for (j in r.juicios) TarjetaJuicio(seccion, j, accent)
                        if (nivel == null) Text(
                            "Sin dos juicios con prueba no hay estimación. Puedes reintentar la estimación o repetir la parte.",
                            style = MaterialTheme.typography.bodyMedium, color = BadRed
                        )
                    }
                    AvisoAptis()
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().background(Cream).padding(20.dp)) {
            when (fase) {
                FaseParte.INTRO -> {
                    if (previo != null && seccion.porIa && previo.pendientesIa.isNotEmpty()) {
                        BigButton("Reintentar la estimación de lo que ya hice", container = accent) {
                            juicios.clear(); juicios.addAll(previo.juicios); fase = FaseParte.JUZGAR
                        }
                        BigButton("Repetir la parte desde cero", container = InkSoft) {
                            if (esHabla && !listener.hasMicPermission()) permiso.launch(Manifest.permission.RECORD_AUDIO) else { idx = 0; fase = FaseParte.TAREAS }
                        }
                    } else BigButton(if (previo != null) "Repetir esta parte" else "Empezar", container = accent) {
                        if (esHabla && !listener.hasMicPermission()) permiso.launch(Manifest.permission.RECORD_AUDIO) else { idx = 0; fase = FaseParte.TAREAS }
                    }
                }
                FaseParte.TAREAS -> {}
                FaseParte.JUZGAR -> Text("Espera unos segundos…", style = MaterialTheme.typography.labelMedium, color = InkSoft, modifier = Modifier.fillMaxWidth())
                FaseParte.FIN -> {
                    if (seccion.porIa && juicios.any { !it.valido }) BigButton("Reintentar la estimación", container = accent) { fase = FaseParte.JUZGAR }
                    BigButton("Volver al diagnóstico", container = if (seccion.porIa && juicios.any { !it.valido }) InkSoft else accent) { onDone() }
                }
            }
        }
    }
}

@Composable
private fun TarjetaJuicio(seccion: SeccionAptis, j: JuicioIa, accent: Color) {
    val nivelTarea = seccion.escritura.firstOrNull { it.id == j.id }?.level ?: seccion.habla.firstOrNull { it.id == j.id }?.level ?: j.level
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tarea de $nivelTarea · ${JuezAptis.palabras(j.texto)} palabras", style = MaterialTheme.typography.labelMedium, color = InkSoft, modifier = Modifier.weight(1f))
            when {
                j.valido -> Pill(j.nivel!!.etiqueta, Color.White, colorNivel(j.nivel))
                j.error != null -> Pill("sin juicio", Color.White, BadRed)
                else -> Pill("…", InkSoft, Line)
            }
        }
        if (j.valido) {
            Text("«${j.cita}»", style = MaterialTheme.typography.bodyLarge, color = Ink)
            if (j.razon.isNotBlank()) Text(j.razon, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
            if (j.practica.isNotBlank()) Text("Practica: ${j.practica}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = accent)
        } else if (j.error != null) {
            Text(j.error, style = MaterialTheme.typography.bodyMedium, color = BadRed)
        }
    }
}

@Composable
private fun CuentaAtras(restante: Int, total: Int, accent: Color) {
    val color = if (restante <= 5) BadRed else accent
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("⏱ " + mmss(restante), style = MaterialTheme.typography.titleMedium, color = color)
        LinearProgressIndicator(
            progress = { if (total <= 0) 0f else restante / total.toFloat() },
            color = color, trackColor = Line,
            modifier = Modifier.weight(1f).height(8.dp)
        )
    }
}

/** Fila de opción para elegir, sin corrección (el diagnóstico no corrige hasta el final). */
@Composable
private fun Opcion(text: String, selected: Boolean, accent: Color, dimmed: Boolean = false, onClick: () -> Unit) {
    Text(
        text, style = MaterialTheme.typography.bodyLarge, color = if (dimmed) Line else Ink,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) accent.copy(alpha = 0.12f) else Color.White, RoundedCornerShape(14.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else Line, RoundedCornerShape(14.dp))
            .clickable(enabled = !dimmed) { onClick() }
            .padding(horizontal = 16.dp, vertical = 15.dp)
    )
}

// --- Core -------------------------------------------------------------------

@Composable
private fun TareaCoreUi(item: ItemCore, seg: Int, n: Int, total: Int, accent: Color, onRespuesta: (String, Boolean) -> Unit) {
    val opciones = remember { item.options.shuffled() }
    var chosen by remember { mutableStateOf(-1) }
    var restante by remember { mutableStateOf(seg) }
    var entregado by remember { mutableStateOf(false) }
    fun entregar() {
        if (entregado) return
        entregado = true
        val puesto = if (chosen >= 0) opciones[chosen] else ""
        onRespuesta(puesto, puesto == item.answer)
    }
    // Se acaba el tiempo y pasa sola, como en el examen: sin volver atrás.
    LaunchedEffect(Unit) {
        while (restante > 0) { delay(1000); restante -= 1 }
        entregar()
    }
    Text("Pregunta $n de $total · ${item.level}", style = MaterialTheme.typography.labelMedium, color = InkSoft)
    CuentaAtras(restante, seg, accent)
    Text(item.text.replace("___", "______"), style = MaterialTheme.typography.headlineSmall, color = Ink)
    opciones.forEachIndexed { i, opt -> Opcion(opt, chosen == i, accent) { chosen = i } }
    BigButton("Siguiente", enabled = chosen >= 0, container = accent) { entregar() }
}

// --- Reading ----------------------------------------------------------------

@Composable
private fun TareaCompletarUi(t: TareaLectura.Completar, accent: Color, onRespuesta: (String, Boolean) -> Unit) {
    val opciones = remember { t.options.shuffled() }
    var chosen by remember { mutableStateOf(-1) }
    Text("Completa la frase con la palabra que va.", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
    Text(t.text.replace("___", "______"), style = MaterialTheme.typography.headlineSmall, color = Ink)
    opciones.forEachIndexed { i, opt -> Opcion(opt, chosen == i, accent) { chosen = i } }
    BigButton("Siguiente", enabled = chosen >= 0, container = accent) { onRespuesta(opciones[chosen], opciones[chosen] == t.answer) }
}

@Composable
private fun TareaOrdenarUi(t: TareaLectura.Ordenar, accent: Color, onRespuesta: (String, Boolean) -> Unit) {
    val sueltas = remember { t.desordenadas.shuffled() }
    var puestas by remember { mutableStateOf(listOf<String>()) }
    Text(t.instruccion, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text("1. ${t.primera}", style = MaterialTheme.typography.bodyLarge, color = InkSoft)
        puestas.forEachIndexed { i, f ->
            Text(
                "${i + 2}. $f", style = MaterialTheme.typography.bodyLarge, color = Ink,
                modifier = Modifier.fillMaxWidth().clickable { puestas = puestas.filter { it != f } }
            )
        }
        if (puestas.size < t.desordenadas.size) Text("${puestas.size + 2}. …", style = MaterialTheme.typography.bodyLarge, color = Line)
    }
    Text(
        if (puestas.isEmpty()) "Toca la frase que sigue." else "Toca una frase de arriba para quitarla.",
        style = MaterialTheme.typography.labelMedium, color = InkSoft
    )
    for (f in sueltas) if (f !in puestas) Opcion(f, false, accent) { puestas = puestas + f }
    BigButton("Siguiente", enabled = puestas.size == t.desordenadas.size, container = accent) {
        onRespuesta(puestas.joinToString(" | "), puestas == t.orden)
    }
}

@Composable
private fun TareaTitulosUi(t: TareaLectura.Titulos, accent: Color, onRespuesta: (String, Boolean) -> Unit) {
    var p by remember { mutableStateOf(0) }
    var asignados by remember { mutableStateOf(listOf<String>()) }
    var chosen by remember { mutableStateOf(-1) }
    Text(t.instruccion, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
    Text("Párrafo ${p + 1} de ${t.parrafos.size}", style = MaterialTheme.typography.labelMedium, color = InkSoft)
    Text(
        t.parrafos[p], style = MaterialTheme.typography.bodyLarge, color = Ink,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(14.dp)
    )
    Text("¿Cuál es su título?", style = MaterialTheme.typography.titleMedium, color = Ink)
    t.titulos.forEachIndexed { i, tit -> Opcion(tit, chosen == i, accent, dimmed = tit in asignados) { chosen = i } }
    BigButton(if (p + 1 < t.parrafos.size) "Siguiente párrafo" else "Siguiente", enabled = chosen >= 0, container = accent) {
        val nuevos = asignados + t.titulos[chosen]
        if (p + 1 < t.parrafos.size) { asignados = nuevos; p += 1; chosen = -1 }
        else onRespuesta(nuevos.joinToString(" | "), nuevos == t.answer)
    }
}

// --- Listening --------------------------------------------------------------

/** El audio por turnos: "MAN: … WOMAN: …" se lee como "Man: …" y "Woman: …"; lo demás, frase a frase. */
fun lineasAudio(audio: String): List<String> {
    val turnos = Regex("\\b(MAN|WOMAN):").findAll(audio).toList()
    if (turnos.isNotEmpty()) {
        val out = ArrayList<String>()
        for ((i, m) in turnos.withIndex()) {
            val fin = if (i + 1 < turnos.size) turnos[i + 1].range.first else audio.length
            val quien = if (m.groupValues[1] == "MAN") "Man" else "Woman"
            out.add("$quien: " + audio.substring(m.range.last + 1, fin).trim())
        }
        return out
    }
    return audio.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotBlank() }
}

@Composable
private fun TareaEscuchaUi(
    t: TareaEscucha, n: Int, total: Int, accent: Color, teacher: Teacher, speaker: Speaker,
    sayQueued: (String) -> Unit, onRespuesta: (String, Boolean) -> Unit
) {
    var veces by remember { mutableStateOf(0) }
    var chosen by remember { mutableStateOf(-1) }
    val quedan = 2 - veces
    Text("Tarea $n de $total · ${t.level}", style = MaterialTheme.typography.labelMedium, color = InkSoft)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        SpeakerButton(big = true, tint = if (quedan > 0) accent else Line) {
            if (quedan > 0 && !speaker.busy) {
                speaker.stop()
                for (l in lineasAudio(t.audio)) sayQueued(l)
                veces += 1
            }
        }
        Text(
            when {
                veces == 0 -> "Toca para oír a ${teacher.name}. Se puede oír dos veces."
                quedan == 1 -> "Puedes oírlo una vez más."
                else -> "Ya lo oíste dos veces."
            },
            style = MaterialTheme.typography.bodyMedium, color = InkSoft
        )
    }
    Text(t.pregunta, style = MaterialTheme.typography.headlineSmall, color = Ink)
    t.options.forEachIndexed { i, opt -> Opcion(opt, chosen == i, accent) { chosen = i } }
    BigButton("Siguiente", enabled = chosen >= 0 && veces >= 1, container = accent) {
        speaker.stop()
        onRespuesta(t.options[chosen], t.options[chosen] == t.answer)
    }
}

// --- Writing ----------------------------------------------------------------

@Composable
private fun TareaEscrituraUi(t: TareaEscrita, n: Int, total: Int, accent: Color, onEntrega: (JuicioIa) -> Unit) {
    var texto by remember { mutableStateOf("") }
    var restante by remember { mutableStateOf(t.segundos) }
    var entregado by remember { mutableStateOf(false) }
    fun entregar() {
        if (entregado) return
        entregado = true
        onEntrega(JuicioIa(t.id, t.level, texto.trim(), t.segundos - restante))
    }
    LaunchedEffect(Unit) {
        while (restante > 0) { delay(1000); restante -= 1 }
        entregar()
    }
    Text("Tarea $n de $total · nivel ${t.level} · ${t.palabras} palabras", style = MaterialTheme.typography.labelMedium, color = InkSoft)
    CuentaAtras(restante, t.segundos, accent)
    Text(t.promptEn, style = MaterialTheme.typography.bodyLarge, color = Ink)
    Text(t.promptEs, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
    OutlinedTextField(
        value = texto,
        onValueChange = { if (!entregado) texto = it },
        minLines = 6,
        placeholder = { Text("Escribe aquí, en inglés…") },
        modifier = Modifier.fillMaxWidth()
    )
    Text("${JuezAptis.palabras(texto)} palabras", style = MaterialTheme.typography.labelMedium, color = InkSoft)
    BigButton("Entregar", container = accent) { entregar() }
}

// --- Speaking ---------------------------------------------------------------

private enum class SubHabla { PREP, LISTO, GRABANDO, OIDO }

@Composable
private fun TareaHablaUi(
    t: TareaHablada, n: Int, total: Int, accent: Color, listener: Listener, speaker: Speaker,
    say: (String, Float) -> Unit, onEntrega: (JuicioIa) -> Unit
) {
    val main = remember { Handler(Looper.getMainLooper()) }
    var sub by remember { mutableStateOf(if (t.prepSeg > 0) SubHabla.PREP else SubHabla.LISTO) }
    var restante by remember { mutableStateOf(if (t.prepSeg > 0) t.prepSeg else t.hablarSeg) }
    var oido by remember { mutableStateOf<ListenResult?>(null) }
    var entregado by remember { mutableStateOf(false) }

    fun empezar() {
        if (sub == SubHabla.GRABANDO) return
        if (!listener.hasMicPermission()) { sub = SubHabla.LISTO; return }
        speaker.stop()
        oido = null
        sub = SubHabla.GRABANDO
        restante = t.hablarSeg
        // Parakeet (conversation = true) porque aquí se quiere el que mejor adivina; sin corte
        // por silencio: pensar callado es parte de hablar un minuto; sin GOP (Sound.GENERAL).
        listener.startRecording(target = "", sound = Sound.GENERAL, conversation = true, maxSeconds = t.hablarSeg, cortarSolo = false) { r ->
            main.post { oido = r; sub = SubHabla.OIDO }
        }
    }

    // La examinadora lee la pregunta; en las tareas con preparación, mientras corre el reloj.
    LaunchedEffect(Unit) { speaker.stop(); say(t.promptEn, 1f) }
    LaunchedEffect(sub) {
        when (sub) {
            SubHabla.PREP -> {
                while (restante > 0 && sub == SubHabla.PREP) { delay(1000); restante -= 1 }
                if (sub == SubHabla.PREP) empezar()
            }
            SubHabla.GRABANDO -> {
                while (restante > 0 && listener.recording) { delay(1000); restante -= 1 }
                if (listener.recording) listener.stopRecording()
            }
            else -> {}
        }
    }

    Text("Tarea $n de $total · nivel ${t.level} · habla ${t.hablarSeg} s", style = MaterialTheme.typography.labelMedium, color = InkSoft)
    Text(t.promptEn, style = MaterialTheme.typography.headlineSmall, color = Ink)
    Text(t.promptEs, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SpeakerButton(tint = accent) { if (sub != SubHabla.GRABANDO) { speaker.stop(); say(t.promptEn, 1f) } }
        Text("Oír la pregunta otra vez", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
    }
    when (sub) {
        SubHabla.PREP -> {
            Text("Prepara lo que vas a decir", style = MaterialTheme.typography.titleMedium, color = accent)
            CuentaAtras(restante, t.prepSeg, accent)
            BigButton("Empezar ya", container = accent) { empezar() }
        }
        SubHabla.LISTO -> {
            BigButton("🎤 Empezar a hablar (${t.hablarSeg} s)", container = accent) { empezar() }
        }
        SubHabla.GRABANDO -> {
            if (listener.recording) CuentaAtras(restante, t.hablarSeg, accent)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(96.dp)
                        .background(if (listener.recording) BadRed else Line, CircleShape)
                        .clickable(enabled = listener.recording) { listener.stopRecording() }
                ) { Text(if (listener.recording) "■" else "…", style = MaterialTheme.typography.headlineMedium, color = Color.White) }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (listener.recording) "Habla hasta que se acabe el reloj, o toca ■ si ya terminaste." else "Escuchando lo que dijiste… (unos segundos)",
                    style = MaterialTheme.typography.bodyMedium, color = if (listener.recording) BadRed else InkSoft
                )
                if (listener.recording) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { listener.level }, color = BadRed, trackColor = Line, modifier = Modifier.fillMaxWidth().height(6.dp))
                }
            }
        }
        SubHabla.OIDO -> {
            when (val r = oido) {
                is ListenResult.Heard -> {
                    Text("Lo que oyó el dictado (%.0f s de voz en %.0f s):".format(Locale.US, r.speechSeconds, r.totalSeconds), style = MaterialTheme.typography.labelMedium, color = InkSoft)
                    Text(
                        r.text, style = MaterialTheme.typography.bodyLarge, color = Ink,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(14.dp))
                            .border(1.dp, Line, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    )
                    Text("Si el dictado oyó mal alguna palabra, la IA ya sabe que puede pasar: no cuenta como error tuyo.", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                    BigButton("Siguiente", container = accent) {
                        if (!entregado) { entregado = true; onEntrega(JuicioIa(t.id, t.level, r.text, r.speechSeconds.roundToInt(), duracion = r.totalSeconds.roundToInt())) }
                    }
                }
                is ListenResult.NotHeard -> {
                    NotHeardBox(r.reason)
                    BigButton("Grabar otra vez", container = accent) { sub = SubHabla.LISTO }
                    Text(
                        "Seguir sin esta tarea (cuenta como no respondida)",
                        style = MaterialTheme.typography.labelLarge, color = InkSoft,
                        modifier = Modifier.clickable { if (!entregado) { entregado = true; onEntrega(JuicioIa(t.id, t.level, "", 0)) } }.padding(vertical = 6.dp)
                    )
                }
                null -> {}
            }
        }
    }
}
