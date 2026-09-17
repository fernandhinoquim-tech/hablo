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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/*
 * Pantallas del Modo Aptis (etapa 5). Ver Aptis.kt para las reglas.
 * - AptisScreen: el TABLERO (una barra por destreza con su nivel; arriba, el piso).
 * - PistaScreen: una ronda de entrenamiento con corrección tras cada tarea; la
 *   promoción la decide Aptis.registrar.
 * - SimulacroScreen + AptisParteScreen: el simulacro completo cronometrado (el
 *   antiguo diagnóstico), de una sentada, cuando las cinco pistas están en B1.
 */

private val AvisoFondo = Color(0xFFFFF1DB)
private val AvisoBorde = Color(0xFFE6B970)
private val AvisoTinta = Color(0xFF6B3F00)

/** El aviso obligatorio: en el tablero, en las pistas y partes que estima la IA y en el resultado del simulacro. */
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

/** Por debajo de B1 (el piso que exige Aptis) es rojo; B1 y B2, verde. */
private fun colorNivel(n: NivelAptis?): Color = when (n) {
    null -> InkSoft
    NivelAptis.A1, NivelAptis.A2 -> BadRed
    NivelAptis.B1, NivelAptis.B2 -> GoodGreen
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

/**
 * La barra de una pista: cuatro tramos, llenos hasta el nivel ALCANZADO; el
 * que se entrena va marcado con el borde; B1 lleva la raya del examen.
 */
@Composable
private fun BarraNivel(alcanzado: NivelAptis?, entrenando: NivelAptis) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        for (n in NivelAptis.entries) {
            val lleno = alcanzado != null && n <= alcanzado
            val borde = when {
                n == entrenando -> Ink
                n == NivelAptis.B1 -> Ink.copy(alpha = 0.35f)
                else -> Color.Transparent
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(if (lleno) colorNivel(alcanzado) else Line, RoundedCornerShape(5.dp))
                        .border(if (n == entrenando || n == NivelAptis.B1) 2.dp else 0.dp, borde, RoundedCornerShape(5.dp))
                )
                Text(
                    if (n == entrenando) "${n.etiqueta} ·" else n.etiqueta,
                    style = MaterialTheme.typography.labelMedium, color = if (n == entrenando) Ink else InkSoft
                )
            }
        }
    }
}

private fun etiquetaNivel(n: NivelAptis?): String = n?.etiqueta ?: "sin nivel"

/** "Reading, Listening y Writing". */
private fun juntar(nombres: List<String>): String =
    if (nombres.size <= 1) nombres.joinToString("") else nombres.dropLast(1).joinToString(", ") + " y " + nombres.last()

// ---------------------------------------------------------------------------
// El tablero
// ---------------------------------------------------------------------------

@Composable
fun AptisScreen(
    banco: BancoAptis,
    aptis: Aptis,
    teacher: Teacher,
    claude: ClaudeLlm,
    onPista: (String) -> Unit,
    onSimulacro: () -> Unit,
    onBack: () -> Unit
) {
    val accent = Color(teacher.color)
    val tick = aptis.tick
    val niveles = remember(tick) { aptis.niveles(banco) }
    val piso = remember(tick) { aptis.piso(banco) }
    val nivelPiso = piso.firstOrNull()?.let { niveles[it.id] }
    val desbloqueado = remember(tick) { aptis.simulacroDesbloqueado(banco) }
    val hechas = remember(tick) { banco.pistas.sumOf { aptis.hechas(it) } }
    var confirmarBorrado by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar("🎯 Modo Aptis", onBack = onBack)
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Arriba, en grande: cuál de las cuatro va última. Ahí se estudia.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .border(2.dp, colorNivel(nivelPiso), RoundedCornerShape(18.dp))
                    .padding(20.dp)
            ) {
                Text("TU PISO", style = MaterialTheme.typography.labelLarge, color = InkSoft)
                Text(juntar(piso.map { it.skill }), style = MaterialTheme.typography.headlineLarge, color = colorNivel(nivelPiso), textAlign = TextAlign.Center)
                Text(if (hechas == 0) "sin empezar" else etiquetaNivel(nivelPiso), style = MaterialTheme.typography.headlineMedium, color = colorNivel(nivelPiso))
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        hechas == 0 -> "Aptis pide B1 en las cuatro destrezas. Es un piso, no un promedio: la más floja manda. Entrena y las pistas suben solas."
                        nivelPiso == null -> "Todavía no has alcanzado ningún nivel en estas: entrena ahí primero. Aptis pide B1 en las cuatro destrezas."
                        nivelPiso < NivelAptis.B1 -> "Aptis pide B1 en las cuatro destrezas y aquí no llegas todavía: ahí van tus horas, no al promedio."
                        else -> "Las cuatro ya están en B1 o más. Para seguir subiendo, empieza por esta: la más floja manda."
                    },
                    style = MaterialTheme.typography.bodyMedium, color = Ink, textAlign = TextAlign.Center
                )
            }
            AvisoAptis()
            Text(
                "Cinco pistas de A1 a B2. Cada ronda cuenta: cuando aciertas las que pide el nivel, la pista sube sola. " +
                    "Si una racha va floja no bajas: la siguiente ronda mezcla el nivel anterior para afianzar.",
                style = MaterialTheme.typography.bodyMedium, color = InkSoft
            )
            if (!claude.keyPresent()) Text(
                "Writing y Speaking los estima Claude por internet: hace falta la clave de Claude en el teléfono. Las otras tres pistas no necesitan nada.",
                style = MaterialTheme.typography.bodyMedium, color = BadRed
            )
            for (p in banco.pistas) {
                val alcanzado = niveles[p.id]
                val entrenando = aptis.nivel(p)
                val n = p.promocion.de
                val ultimos = aptis.ultimos(p, entrenando, n)
                val esPiso = piso.any { it.id == p.id }
                val disponibles = p.de(entrenando).size
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(if (esPiso) 2.dp else 1.dp, if (esPiso) BadRed else Line, RoundedCornerShape(16.dp))
                        .clickable { onPista(p.id) }
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp).background(Color(teacher.softColor), CircleShape)) {
                            Text(p.emoji, style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${p.skill} · ${p.title}", style = MaterialTheme.typography.titleMedium)
                            if (p.id == "core") Text("El desempate del examen: no es una de las cuatro, pero rescata a las otras", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                            else if (esPiso) Text("Tu piso: aquí primero", style = MaterialTheme.typography.labelMedium, color = BadRed)
                        }
                        Pill(etiquetaNivel(alcanzado), Color.White, colorNivel(alcanzado))
                    }
                    BarraNivel(alcanzado, entrenando)
                    Text(
                        when {
                            disponibles == 0 -> "Todavía no hay tareas de ${entrenando.etiqueta} en esta pista (las escribe Cowork)."
                            ultimos.isEmpty() -> "Entrena ${entrenando.etiqueta} · ${p.tareas.size} tareas en el banco · alcanzas ${entrenando.etiqueta} con ${p.promocion.bien} de ${p.promocion.de} bien"
                            else -> "Entrena ${entrenando.etiqueta} · últimas ${ultimos.size}: ${ultimos.count { it.ok }} bien (hacen falta ${p.promocion.bien} de ${p.promocion.de}) · " + aptis.hechas(p).let { if (it == 1) "1 tarea hecha" else "$it tareas hechas" }
                        },
                        style = MaterialTheme.typography.bodyMedium, color = InkSoft
                    )
                    Text("Entrenar ›", style = MaterialTheme.typography.labelLarge, color = accent)
                }
            }

            // El simulacro completo, de una sentada, solo con las cinco en B1.
            banco.simulacro?.let { diag ->
                val faltan = aptis.faltanParaSimulacro(banco)
                val completo = remember(tick) { aptis.simulacroCompleto(diag) }
                val pisoSim = remember(tick) { aptis.pisoSimulacro(diag) }
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (desbloqueado) Color.White else Color(0xFFF6F2ED), RoundedCornerShape(16.dp))
                        .border(1.dp, if (desbloqueado) accent.copy(alpha = 0.5f) else Line, RoundedCornerShape(16.dp))
                        .clickable(enabled = desbloqueado) { onSimulacro() }
                        .padding(16.dp)
                ) {
                    Text(if (desbloqueado) "⏱ Simulacro completo" else "🔒 Simulacro completo", style = MaterialTheme.typography.titleMedium, color = if (desbloqueado) Ink else InkSoft)
                    Text(
                        when {
                            !desbloqueado -> "Se abre cuando las cinco pistas hayan alcanzado B1 o más. Te falta: " + juntar(faltan.map { "${it.skill} (${etiquetaNivel(aptis.alcanzado(it))})" }) + "."
                            completo -> "Último simulacro: piso " + juntar(pisoSim.map { it.skill }) + " · " + (pisoSim.firstOrNull()?.let { aptis.nivelesSimulacro(diag)[it.id]?.etiqueta } ?: "") + ". Toca para verlo o repetirlo."
                            else -> "${diag.duracionMin} minutos, ${diag.tareas} tareas, con el reloj de verdad y de una sentada."
                        },
                        style = MaterialTheme.typography.bodyMedium, color = InkSoft
                    )
                }
            }

            if (hechas > 0 || aptis.hechasSimulacro(banco.simulacro ?: Diagnostico(0, emptyList())) > 0) {
                Spacer(Modifier.height(8.dp))
                if (!confirmarBorrado) Text(
                    "Empezar el Modo Aptis de cero",
                    style = MaterialTheme.typography.labelLarge, color = InkSoft,
                    modifier = Modifier.clickable { confirmarBorrado = true }.padding(vertical = 6.dp)
                ) else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("¿Seguro? Se pierden los niveles y el historial de las cinco pistas.", style = MaterialTheme.typography.bodyMedium, color = BadRed, modifier = Modifier.weight(1f))
                    Text("Sí, borrar", style = MaterialTheme.typography.labelLarge, color = BadRed, modifier = Modifier.clickable { aptis.borrarTodo(); confirmarBorrado = false })
                    Text("No", style = MaterialTheme.typography.labelLarge, color = InkSoft, modifier = Modifier.clickable { confirmarBorrado = false })
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Una ronda de entrenamiento en una pista
// ---------------------------------------------------------------------------

private enum class FasePista { INTRO, TAREA, JUZGANDO, CORRECCION, FIN }

@Composable
fun PistaScreen(
    pista: PistaAptis,
    aptis: Aptis,
    teacher: Teacher,
    speaker: Speaker,
    listener: Listener,
    claude: ClaudeLlm,
    say: (String, Float) -> Unit,
    sayQueued: (String) -> Unit,
    onBack: () -> Unit
) {
    val accent = Color(teacher.color)
    val main = remember { Handler(Looper.getMainLooper()) }
    var fase by remember { mutableStateOf(FasePista.INTRO) }
    var cola by remember { mutableStateOf(listOf<TareaAptis>()) }
    var idx by remember { mutableStateOf(0) }
    val hechos = remember { mutableStateListOf<Intento>() }
    var actual by remember { mutableStateOf<Intento?>(null) }
    var juicioFallido by remember { mutableStateOf<JuicioIa?>(null) }
    var promocion by remember { mutableStateOf<Promocion?>(null) }
    var promocionEn by remember { mutableStateOf(-1) }
    val esHabla = pista.id == "speaking"
    val nivelActual = remember(aptis.tick) { aptis.nivel(pista) }
    val alcanzado = remember(aptis.tick) { aptis.alcanzado(pista) }
    val disponibles = remember(aptis.tick) { pista.de(aptis.nivel(pista)).size }
    val mezcla = remember(aptis.tick) { aptis.flojo(pista) }

    fun armar() {
        cola = aptis.ronda(pista)
        idx = 0
        hechos.clear()
        actual = null
        juicioFallido = null
        fase = if (cola.isEmpty()) FasePista.FIN else FasePista.TAREA
    }

    fun registrar(intento: Intento) {
        val p = aptis.registrar(pista, intento)
        hechos.add(intento)
        if (p != null) { promocion = p; promocionEn = hechos.size }
        actual = intento
        juicioFallido = null
        fase = FasePista.CORRECCION
    }

    fun responder(puesto: String, ok: Boolean) {
        val t = cola[idx]
        registrar(Intento(t.id, t.level, ok, aptis.hoy(), puesto))
    }

    fun juzgar(j: JuicioIa) {
        fase = FasePista.JUZGANDO
        val t = cola[idx]
        JuezAptis.juzgar(claude, t, j) { r ->
            main.post {
                if (r.valido) registrar(Intento(r.id, r.level, r.nivel!! >= t.nivel, aptis.hoy(), "", r))
                else { juicioFallido = r; actual = null; fase = FasePista.CORRECCION }
            }
        }
    }

    fun siguiente() {
        actual = null
        juicioFallido = null
        if (idx + 1 < cola.size) { idx += 1; fase = FasePista.TAREA } else fase = FasePista.FIN
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
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) armar() }
    fun empezar() { if (esHabla && !listener.hasMicPermission()) permiso.launch(Manifest.permission.RECORD_AUDIO) else armar() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = when (fase) {
                FasePista.INTRO -> "${pista.emoji} ${pista.skill} · entrena ${nivelActual.etiqueta}"
                FasePista.TAREA, FasePista.JUZGANDO, FasePista.CORRECCION -> "${pista.emoji} ${pista.skill} · ${idx + 1} de ${cola.size}"
                FasePista.FIN -> "${pista.emoji} ${pista.skill} · ronda hecha"
            },
            onBack = onBack
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            when (fase) {
                FasePista.INTRO -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(pista.title, style = MaterialTheme.typography.headlineSmall, color = accent, modifier = Modifier.weight(1f))
                        Pill(etiquetaNivel(alcanzado), Color.White, colorNivel(alcanzado))
                    }
                    BarraNivel(alcanzado, nivelActual)
                    val ultimos = aptis.ultimos(pista, nivelActual, pista.promocion.de)
                    Text(
                        if (ultimos.isEmpty()) "Entrenas ${nivelActual.etiqueta}. Lo alcanzas con ${pista.promocion.bien} de ${pista.promocion.de} bien en las últimas ${pista.promocion.de}."
                        else "Entrenas ${nivelActual.etiqueta}. Últimas ${ultimos.size}: ${ultimos.count { it.ok }} bien; hacen falta ${pista.promocion.bien} de ${pista.promocion.de}.",
                        style = MaterialTheme.typography.bodyLarge, color = Ink
                    )
                    if (mezcla) Text("Las últimas van flojas: esta ronda mezcla tareas de ${pista.anteriorCon(nivelActual)?.etiqueta ?: nivelActual.etiqueta} para afianzar. No bajas de nivel.", style = MaterialTheme.typography.bodyMedium, color = AvisoTinta)
                    Text(
                        when (pista.id) {
                            "core" -> "Ronda de ${pista.ronda}: un ítem, tres opciones, 30 segundos cada uno, como en el examen. Después de cada uno ves la corrección y el porqué."
                            "reading" -> "Ronda de ${pista.ronda} tareas de lectura: completar, ordenar frases y poner títulos. Después de cada una ves la corrección."
                            "listening" -> "Ronda de ${pista.ronda}: ${teacher.name} lee cada audio (hasta dos veces) y respondes en español. Después de cada una ves lo que decía."
                            "writing" -> "Escribes en inglés con reloj y Claude estima el nivel citando una frase tuya. Cuenta como acierto si llega al nivel de la tarea."
                            else -> "${teacher.name} te lee la pregunta, preparas si toca y hablas hasta que se acabe el reloj. Claude estima el nivel de lo que dices (no cómo suena) citando una frase tuya."
                        },
                        style = MaterialTheme.typography.bodyMedium, color = InkSoft
                    )
                    if (pista.porIa) {
                        AvisoAptis()
                        if (!claude.keyPresent()) Text("Sin la clave de Claude en el teléfono esta pista no se puede estimar.", style = MaterialTheme.typography.bodyMedium, color = BadRed)
                    }
                    if (disponibles == 0) Text("Todavía no hay tareas de ${nivelActual.etiqueta} en esta pista. Cowork está escribiendo los bancos.", style = MaterialTheme.typography.bodyMedium, color = BadRed)
                }

                FasePista.TAREA -> key(idx) {
                    val t = cola[idx]
                    TareaUi(t, idx + 1, cola.size, if (t is ItemCore) pista.segundosPorItem else 0, accent, teacher, speaker, listener, say, sayQueued,
                        onRespuesta = { puesto, ok -> responder(puesto, ok) },
                        onEntrega = { j -> juzgar(j) })
                }

                FasePista.JUZGANDO -> {
                    Text("Estimando con ${ClaudeLlm.nombreDe(JuezAptis.MODELO)}…", style = MaterialTheme.typography.headlineSmall, color = accent)
                    Text("Solo sale el texto, nunca el audio. Unos segundos.", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                    LinearProgressIndicator(color = accent, trackColor = Line, modifier = Modifier.fillMaxWidth().height(6.dp))
                }

                FasePista.CORRECCION -> {
                    val t = cola[idx]
                    CorreccionAptis(t, actual, juicioFallido, accent, speaker, sayQueued)
                    if (promocion != null && promocionEn == hechos.size) PromocionBanner(pista, promocion!!)
                }

                FasePista.FIN -> {
                    if (cola.isEmpty()) {
                        Text("Sin tareas", style = MaterialTheme.typography.headlineSmall, color = accent)
                        Text("Todavía no hay tareas de ${nivelActual.etiqueta} en esta pista. Cowork está escribiendo los bancos.", style = MaterialTheme.typography.bodyLarge, color = Ink)
                    } else {
                        Text("Ronda hecha", style = MaterialTheme.typography.headlineSmall, color = accent)
                        Text("${hechos.count { it.ok }} de ${hechos.size} bien.", style = MaterialTheme.typography.headlineMedium, color = Ink)
                        if (promocion != null) PromocionBanner(pista, promocion!!)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${pista.skill} ahora:", style = MaterialTheme.typography.titleMedium)
                            Pill(etiquetaNivel(alcanzado), Color.White, colorNivel(alcanzado))
                        }
                        BarraNivel(alcanzado, nivelActual)
                        val ultimos = aptis.ultimos(pista, nivelActual, pista.promocion.de)
                        Text(
                            "Entrenas ${nivelActual.etiqueta}" + (if (ultimos.isEmpty()) " · todavía sin tareas de ${nivelActual.etiqueta}. " else " · últimas ${ultimos.size}: ${ultimos.count { it.ok }} bien. ") +
                                (if (alcanzado == null || alcanzado < nivelActual) "Lo alcanzas con ${pista.promocion.bien} de ${pista.promocion.de}." else "Es el nivel más alto de la pista."),
                            style = MaterialTheme.typography.bodyLarge, color = Ink
                        )
                        if (mezcla) Text("Van flojas: la próxima ronda mezcla ${pista.anteriorCon(nivelActual)?.etiqueta ?: ""} para afianzar.", style = MaterialTheme.typography.bodyMedium, color = AvisoTinta)
                        if (pista.porIa) AvisoAptis()
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().background(Cream).padding(20.dp)) {
            when (fase) {
                FasePista.INTRO -> BigButton("Empezar la ronda", enabled = disponibles > 0, container = accent) { empezar() }
                FasePista.TAREA -> {}
                FasePista.JUZGANDO -> Text("Espera unos segundos…", style = MaterialTheme.typography.labelMedium, color = InkSoft, modifier = Modifier.fillMaxWidth())
                FasePista.CORRECCION -> {
                    val fallido = juicioFallido
                    if (fallido != null) {
                        BigButton("Reintentar la estimación", container = accent) { juzgar(fallido) }
                        BigButton("Saltar esta tarea (no cuenta)", container = InkSoft) { siguiente() }
                    } else BigButton(if (idx + 1 < cola.size) "Siguiente" else "Ver la ronda", container = accent) { speaker.stop(); siguiente() }
                }
                FasePista.FIN -> {
                    if (cola.isNotEmpty()) BigButton("Otra ronda", container = accent) { empezar() }
                    BigButton("Volver al tablero", container = if (cola.isNotEmpty()) InkSoft else accent) { onBack() }
                }
            }
        }
    }
}

@Composable
private fun PromocionBanner(pista: PistaAptis, p: Promocion) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .background(GoodGreenSoft, RoundedCornerShape(14.dp))
            .border(2.dp, GoodGreen, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Text(
            if (p.a != null) "🎉 ${pista.skill}: alcanzas ${p.alcanzado.etiqueta} y pasas a ${p.a.etiqueta}" else "🎉 ${pista.skill}: alcanzas ${p.alcanzado.etiqueta}, lo más alto de la pista",
            style = MaterialTheme.typography.titleLarge, color = GoodGreen, textAlign = TextAlign.Center
        )
        Text(
            if (p.alcanzado >= NivelAptis.B1) "Ya estás en el piso que pide Aptis en esta destreza." else "Sigue: Aptis pide B1.",
            style = MaterialTheme.typography.bodyMedium, color = Ink
        )
    }
}

/** La corrección de una tarea de la pista: qué era, por qué, y en Writing/Speaking el juicio de la IA con su cita. */
@Composable
private fun CorreccionAptis(t: TareaAptis, intento: Intento?, fallido: JuicioIa?, accent: Color, speaker: Speaker, sayQueued: (String) -> Unit) {
    val ok = intento?.ok == true
    if (fallido != null) {
        Text("Sin juicio", style = MaterialTheme.typography.headlineSmall, color = BadRed)
        Text(fallido.error ?: "La IA no respondió.", style = MaterialTheme.typography.bodyLarge, color = Ink)
        Text("Tu texto no se pierde: puedes reintentar la estimación cuando haya conexión, o saltar esta tarea.", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
        return
    }
    when (t) {
        is ItemCore -> {
            Text(
                when { ok -> "✓ Correcto"; intento?.puesto.isNullOrBlank() -> "✗ Se acabó el tiempo: era «${t.answer}»"; else -> "✗ Era «${t.answer}», no «${intento?.puesto}»" },
                style = MaterialTheme.typography.headlineSmall, color = if (ok) GoodGreen else BadRed
            )
            Text(t.resuelto, style = MaterialTheme.typography.bodyLarge, color = Ink)
            if (t.why.isNotBlank()) Tarjeta {
                Text(if (t.point.isNotBlank()) "POR QUÉ · ${t.point}" else "POR QUÉ", style = MaterialTheme.typography.labelMedium, color = AvisoTinta)
                Text(t.why, style = MaterialTheme.typography.bodyMedium, color = Ink)
            }
        }
        is TareaLectura.Completar -> {
            Text(if (ok) "✓ Correcto" else "✗ Era «${t.answer}»", style = MaterialTheme.typography.headlineSmall, color = if (ok) GoodGreen else BadRed)
            Text(t.text.replace("___", t.answer), style = MaterialTheme.typography.bodyLarge, color = Ink)
            if (t.why.isNotBlank()) Tarjeta { Text("POR QUÉ", style = MaterialTheme.typography.labelMedium, color = AvisoTinta); Text(t.why, style = MaterialTheme.typography.bodyMedium, color = Ink) }
        }
        is TareaLectura.Ordenar -> {
            Text(if (ok) "✓ En orden" else "✗ El orden era este", style = MaterialTheme.typography.headlineSmall, color = if (ok) GoodGreen else BadRed)
            Tarjeta {
                Text("1. ${t.primera}", style = MaterialTheme.typography.bodyLarge, color = InkSoft)
                t.orden.forEachIndexed { i, f -> Text("${i + 2}. $f", style = MaterialTheme.typography.bodyLarge, color = Ink) }
            }
            if (t.why.isNotBlank()) Tarjeta { Text("POR QUÉ", style = MaterialTheme.typography.labelMedium, color = AvisoTinta); Text(t.why, style = MaterialTheme.typography.bodyMedium, color = Ink) }
        }
        is TareaLectura.Titulos -> {
            Text(if (ok) "✓ Los tres títulos" else "✗ Los títulos eran estos", style = MaterialTheme.typography.headlineSmall, color = if (ok) GoodGreen else BadRed)
            t.parrafos.forEachIndexed { i, p ->
                Tarjeta {
                    Text(t.answer[i], style = MaterialTheme.typography.titleMedium, color = Ink)
                    Text(p, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                }
            }
            if (t.why.isNotBlank()) Tarjeta { Text("POR QUÉ", style = MaterialTheme.typography.labelMedium, color = AvisoTinta); Text(t.why, style = MaterialTheme.typography.bodyMedium, color = Ink) }
        }
        is TareaEscucha -> {
            Text(if (ok) "✓ Correcto" else "✗ Era «${t.answer}»", style = MaterialTheme.typography.headlineSmall, color = if (ok) GoodGreen else BadRed)
            Text(t.pregunta, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SpeakerButton(tint = accent) { speaker.stop(); for (l in lineasAudio(t.audio)) sayQueued(l) }
                Text("Lo que decía:", style = MaterialTheme.typography.labelLarge, color = Ink)
            }
            Tarjeta { for (l in lineasAudio(t.audio)) Text(l, style = MaterialTheme.typography.bodyLarge, color = Ink) }
            if (t.why.isNotBlank()) Tarjeta { Text("POR QUÉ", style = MaterialTheme.typography.labelMedium, color = AvisoTinta); Text(t.why, style = MaterialTheme.typography.bodyMedium, color = Ink) }
        }
        is TareaEscrita, is TareaHablada -> {
            val j = intento?.juicio
            Text(
                if (ok) "✓ Cuenta como ${t.level}" else "✗ No llega a ${t.level}",
                style = MaterialTheme.typography.headlineSmall, color = if (ok) GoodGreen else BadRed
            )
            if (j != null) {
                TarjetaJuicio(t.level, j, accent)
                Text(
                    "La tarea era de ${t.level} y Claude estimó ${j.nivel?.etiqueta}: " + (if (ok) "vale para subir." else "no vale para subir; sí cuenta como práctica."),
                    style = MaterialTheme.typography.bodyMedium, color = InkSoft
                )
            }
        }
    }
}

/** Una tarea cualquiera del Modo Aptis, con el encabezado "Tarea i de N · nivel". */
@Composable
private fun TareaUi(
    t: TareaAptis, n: Int, total: Int, seg: Int, accent: Color, teacher: Teacher, speaker: Speaker, listener: Listener,
    say: (String, Float) -> Unit, sayQueued: (String) -> Unit,
    onRespuesta: (String, Boolean) -> Unit, onEntrega: (JuicioIa) -> Unit
) {
    when (t) {
        is ItemCore -> TareaCoreUi(t, seg, n, total, accent, onRespuesta)
        is TareaLectura -> {
            Text("Tarea $n de $total · ${t.level}", style = MaterialTheme.typography.labelMedium, color = InkSoft)
            when (t) {
                is TareaLectura.Completar -> TareaCompletarUi(t, accent, onRespuesta)
                is TareaLectura.Ordenar -> TareaOrdenarUi(t, accent, onRespuesta)
                is TareaLectura.Titulos -> TareaTitulosUi(t, accent, onRespuesta)
            }
        }
        is TareaEscucha -> TareaEscuchaUi(t, n, total, accent, teacher, speaker, sayQueued, onRespuesta)
        is TareaEscrita -> TareaEscrituraUi(t, n, total, accent, onEntrega)
        is TareaHablada -> TareaHablaUi(t, n, total, accent, listener, speaker, say, onEntrega)
    }
}

// ---------------------------------------------------------------------------
// El simulacro completo: las partes en fila y el resultado
// ---------------------------------------------------------------------------

@Composable
fun SimulacroScreen(
    diag: Diagnostico,
    aptis: Aptis,
    teacher: Teacher,
    speaker: Speaker,
    listener: Listener,
    claude: ClaudeLlm,
    say: (String, Float) -> Unit,
    sayQueued: (String) -> Unit,
    onBack: () -> Unit
) {
    val accent = Color(teacher.color)
    var repitiendo by remember { mutableStateOf(false) }
    val completo = aptis.tick.let { aptis.simulacroCompleto(diag) }
    var idxSec by remember {
        mutableStateOf(diag.secciones.indexOfFirst { aptis.resultadoSimulacro(it.id)?.nivel(it) == null }.coerceAtLeast(0))
    }
    var verResultado by remember { mutableStateOf(completo) }

    if (verResultado && completo) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar("⏱ Simulacro · resultado", onBack = onBack)
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)
            ) {
                ResultadoSimulacro(diag, aptis, accent)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().background(Cream).padding(20.dp)) {
                BigButton("Repetir el simulacro", container = accent) { aptis.borrarSimulacro(); repitiendo = true; idxSec = 0; verResultado = false }
                BigButton("Volver al tablero", container = InkSoft) { onBack() }
            }
        }
        return
    }
    val seccion = diag.secciones[idxSec]
    val ultima = idxSec + 1 >= diag.secciones.size
    key(seccion.id, repitiendo) {
        AptisParteScreen(
            seccion = seccion,
            aptis = aptis,
            teacher = teacher,
            speaker = speaker,
            listener = listener,
            claude = claude,
            say = say,
            sayQueued = sayQueued,
            textoFin = if (ultima) "Ver el resultado" else "Siguiente parte: ${diag.secciones[idxSec + 1].skill}",
            onDone = { if (ultima) verResultado = true else idxSec += 1 },
            onExit = onBack
        )
    }
}

/** Arriba en grande el piso del simulacro; debajo, una tarjeta por destreza con nivel, prueba y UNA cosa que practicar. */
@Composable
private fun ResultadoSimulacro(diag: Diagnostico, aptis: Aptis, accent: Color) {
    val niveles = aptis.nivelesSimulacro(diag)
    val piso = aptis.pisoSimulacro(diag)
    val nivelPiso = piso.firstOrNull()?.let { niveles[it.id] }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(18.dp))
            .border(2.dp, colorNivel(nivelPiso), RoundedCornerShape(18.dp))
            .padding(20.dp)
    ) {
        Text("TU PISO EN EL SIMULACRO", style = MaterialTheme.typography.labelLarge, color = InkSoft)
        Text(juntar(piso.map { it.skill }), style = MaterialTheme.typography.headlineLarge, color = colorNivel(nivelPiso), textAlign = TextAlign.Center)
        if (nivelPiso != null) Text(nivelPiso.etiqueta, style = MaterialTheme.typography.headlineMedium, color = colorNivel(nivelPiso))
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                nivelPiso == null -> ""
                nivelPiso < NivelAptis.B1 -> "Aptis pide B1 en las cuatro destrezas y aquí no llegas todavía: ahí van tus horas, no al promedio."
                else -> "Ya estás en B1 o más en las cuatro. Para subir, empieza por esta: la más floja manda."
            },
            style = MaterialTheme.typography.bodyMedium, color = Ink, textAlign = TextAlign.Center
        )
    }
    AvisoAptis()
    for (s in diag.secciones) {
        val r = aptis.resultadoSimulacro(s.id) ?: continue
        val n = niveles[s.id]
        Tarjeta {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${s.emoji} ${s.skill}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Pill(n?.etiqueta ?: "sin estimación", Color.White, colorNivel(n))
            }
            if (s.porIa) {
                for (j in r.juicios) {
                    val t = s.tareas.firstOrNull { it.id == j.id }?.level ?: j.level
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
// Una parte del simulacro (sin corrección hasta el final, con el reloj)
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
    textoFin: String,
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
    // Lo que ya estaba guardado de esta parte (para reintentar la estimación sin repetirla); si el contenido cambió, no cuenta.
    val previo = remember { aptis.resultadoSimulacro(seccion.id)?.takeIf { it.vigente(seccion) } }
    val esHabla = seccion.habla.isNotEmpty()
    val tareas = remember { seccion.tareas }

    fun guardarIa() = aptis.guardarSimulacro(ResultadoSeccion(seccion.id, aptis.hoy(), juicios = juicios.toList()))

    fun terminarTareas() {
        if (seccion.porIa) {
            guardarIa()
            fase = FaseParte.JUZGAR
        } else {
            aptis.guardarSimulacro(ResultadoSeccion(seccion.id, aptis.hoy(), items = items.toList()))
            fase = FaseParte.FIN
        }
    }

    fun responder(a: AciertoItem) {
        items.add(a)
        if (idx + 1 < tareas.size) idx += 1 else terminarTareas()
    }

    fun entregar(j: JuicioIa) {
        juicios.add(j)
        if (idx + 1 < tareas.size) idx += 1 else terminarTareas()
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
                FaseParte.TAREAS -> "${seccion.emoji} ${seccion.skill} · ${idx + 1} de ${tareas.size}"
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
                    val t = tareas[idx]
                    TareaUi(t, idx + 1, tareas.size, if (t is ItemCore) seccion.segundosPorItem else 0, accent, teacher, speaker, listener, say, sayQueued,
                        onRespuesta = { puesto, ok -> responder(AciertoItem(t.id, t.level, puesto, ok)) },
                        onEntrega = { j -> entregar(j) })
                }

                FaseParte.JUZGAR -> {
                    val hechos = juicios.count { it.valido || it.error != null }
                    Text("Estimando con ${ClaudeLlm.nombreDe(JuezAptis.MODELO)}…", style = MaterialTheme.typography.headlineSmall, color = accent)
                    Text("Tarea ${minOf(hechos + 1, juicios.size)} de ${juicios.size}. Solo sale el texto, nunca el audio.", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                    LinearProgressIndicator(progress = { if (juicios.isEmpty()) 0f else hechos / juicios.size.toFloat() }, color = accent, trackColor = Line, modifier = Modifier.fillMaxWidth().height(6.dp))
                    for (j in juicios) TarjetaJuicio(nivelTareaDe(seccion, j), j, accent)
                }

                FaseParte.FIN -> {
                    val r = aptis.resultadoSimulacro(seccion.id)
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
                        for (j in r.juicios) TarjetaJuicio(nivelTareaDe(seccion, j), j, accent)
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
                    BigButton(textoFin, container = if (seccion.porIa && juicios.any { !it.valido }) InkSoft else accent) { onDone() }
                }
            }
        }
    }
}

private fun nivelTareaDe(seccion: SeccionAptis, j: JuicioIa): String = seccion.tareas.firstOrNull { it.id == j.id }?.level ?: j.level

@Composable
private fun TarjetaJuicio(nivelTarea: String, j: JuicioIa, accent: Color) {
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

/** Fila de opción para elegir, sin corrección (la corrección viene después, aparte). */
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

/** Un ítem del Core; con [seg] > 0 lleva reloj y pasa sola al vencer, como en el examen. */
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
    if (seg > 0) LaunchedEffect(Unit) {
        while (restante > 0) { delay(1000); restante -= 1 }
        entregar()
    }
    Text("Pregunta $n de $total · ${item.level}", style = MaterialTheme.typography.labelMedium, color = InkSoft)
    if (seg > 0) CuentaAtras(restante, seg, accent)
    if (item.instruccion.isNotBlank()) Text(item.instruccion, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
    Text(item.enunciado, style = MaterialTheme.typography.headlineSmall, color = Ink)
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
                    // Sin las muletillas que Parakeet inventa al final del silencio: lo que se ve es lo que se juzga.
                    val limpio = remember(r.text) { JuezAptis.sinMuletillas(r.text) }
                    Text("Lo que oyó el dictado (%.0f s de voz en %.0f s):".format(Locale.US, r.speechSeconds, r.totalSeconds), style = MaterialTheme.typography.labelMedium, color = InkSoft)
                    Text(
                        limpio, style = MaterialTheme.typography.bodyLarge, color = Ink,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(14.dp))
                            .border(1.dp, Line, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    )
                    Text("Si el dictado oyó mal alguna palabra, la IA ya sabe que puede pasar: no cuenta como error tuyo.", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                    BigButton("Siguiente", container = accent) {
                        if (!entregado) { entregado = true; onEntrega(JuicioIa(t.id, t.level, limpio, r.speechSeconds.roundToInt(), duracion = r.totalSeconds.roundToInt())) }
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
