package com.ferolabs.hablo

import android.Manifest
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * **Oído** (pares mínimos con las cuatro voces; datos de Cowork, 17-09,
 * `oido.json`). Por qué así, con las cifras que lo sostienen:
 * - Identificar CUÁL palabra sonó (g = 0,95) rinde mucho más que "¿son
 *   iguales?" (g = 0,57): por eso hay dos botones y nunca "¿igual o distinto?".
 * - Oír varias voces ayuda (la variabilidad de hablantes es un moderador): una
 *   voz POR BLOQUE, rotando entre las cuatro profesoras (`Speaker` tiene una
 *   sola voz cargada; cambiar por ítem serían 20 recargas).
 * - Oír bien y decir bien van sueltos (r = 0,31): cada bloque CIERRA hablando
 *   la frase `hablar` con el jurado de su `sound`, como un drill, y la
 *   pantalla lo dice tal cual.
 * Las frases son neutras (las dos palabras caben igual), así que el sentido
 * no delata la respuesta. Sin reloj. Nada toca la red.
 */
@Composable
fun OidoScreen(
    teacher: Teacher,
    speaker: Speaker,
    listener: Listener,
    store: Store,
    progreso: Progreso,
    speechScale: Float,
    onBack: () -> Unit
) {
    val accent = Color(teacher.color)
    var bloque by remember { mutableStateOf<BloqueOido?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    // El modelo de fonemas solo hace falta al final de un bloque (el "hablar");
    // se suelta al salir de la pantalla: ~400 MB que no deben quedarse.
    DisposableEffect(Unit) { onDispose { listener.stopRecording(); listener.releaseSounds(); speaker.stop() } }

    val actual = bloque
    // El "atrás" del sistema dentro de un bloque vuelve a la lista, no al inicio.
    BackHandler(enabled = actual != null) { speaker.stop(); listener.stopRecording(); bloque = null }
    if (actual == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar("👂 Oído", onBack = onBack)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).padding(20.dp)
            ) {
                item {
                    Text(
                        "Dos palabras que solo cambian en un sonido. Una profesora dice una de las dos y tú " +
                            "tocas cuál oíste. Cada bloque lo dice una voz distinta, y al final lo dices tú: " +
                            "oír la diferencia no garantiza decirla.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSoft
                    )
                }
                for (nivel in listOf("A1", "A2", "B1", "B2")) {
                    val delNivel = Course.oido.filter { it.level == nivel }
                    if (delNivel.isEmpty()) continue
                    item(key = "nivel-$nivel") {
                        val hechos = delNivel.count { store.oidoResultado(it.id) != null }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Pill(nivel, accent, Color(teacher.softColor))
                            Spacer(Modifier.size(10.dp))
                            Text("${delNivel.size} bloques", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.weight(1f))
                            Text("$hechos hechos", style = MaterialTheme.typography.labelLarge, color = InkSoft)
                        }
                    }
                    items(delNivel, key = { it.id }) { b ->
                        val res = remember(tick) { store.oidoResultado(b.id) }
                        val bien = res != null && (res.first * 100 >= res.second * 80)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(16.dp))
                                .border(1.dp, if (bien) GoodGreen.copy(alpha = 0.4f) else Line, RoundedCornerShape(16.dp))
                                .clickable { bloque = b }
                                .padding(16.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(b.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    b.contraste + " · " + b.pares.size + " pares" +
                                        (if (res != null) " · última vez: ${res.first} de ${res.second}" else ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = InkSoft
                                )
                            }
                            if (bien) Text("✓", style = MaterialTheme.typography.titleMedium, color = GoodGreen)
                        }
                    }
                }
            }
        }
        return
    }

    BloqueOidoScreen(
        bloque = actual,
        teacher = teacher,
        speaker = speaker,
        listener = listener,
        store = store,
        progreso = progreso,
        speechScale = speechScale,
        onDone = { tick++; bloque = null },
        onExit = { speaker.stop(); listener.stopRecording(); bloque = null }
    )
}

private enum class FaseOido { INTRO, PARES, HABLAR, FIN }

/** Un par ya tocado en este bloque: cuál sonó, cuál tocó. */
private data class Toque(val par: ParOido, val sono: String, val toco: String) {
    val bien: Boolean get() = sono == toco
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BloqueOidoScreen(
    bloque: BloqueOido,
    teacher: Teacher,
    speaker: Speaker,
    listener: Listener,
    store: Store,
    progreso: Progreso,
    speechScale: Float,
    onDone: () -> Unit,
    onExit: () -> Unit
) {
    // Una voz por bloque, rotando entre las cuatro; la de la lección es una más.
    val voz = remember { TEACHERS[store.siguienteVozOido() % TEACHERS.size] }
    val accent = Color(teacher.color)
    val orden = remember { bloque.pares.shuffled() }
    // Para cada par, al azar, cuál de las dos suena.
    val suena = remember { orden.map { if (kotlin.random.Random.nextBoolean()) it.a else it.b } }
    var fase by remember { mutableStateOf(FaseOido.INTRO) }
    var pos by remember { mutableIntStateOf(0) }
    var toque by remember { mutableStateOf<Toque?>(null) }
    val toques = remember { ArrayList<Toque>() }
    var escuchas by remember { mutableIntStateOf(0) }

    // El "hablar" final: como un drill de pronunciación, con el jurado del sonido del bloque.
    var result by remember { mutableStateOf<PronunciationResult?>(null) }
    var report by remember { mutableStateOf<SoundReport?>(null) }
    var notHeard by remember { mutableStateOf<NotHeardReason?>(null) }
    var showHeard by remember { mutableStateOf(false) }
    var dicho by remember { mutableStateOf(false) }

    fun di(texto: String, extra: Float = 1f) = speaker.speak(texto, voz, speechScale * extra)

    val par = orden.getOrNull(pos)
    val palabraQueSuena = suena.getOrNull(pos)

    // Al llegar a un par, suena solo (una vez); "oír otra vez" es libre.
    LaunchedEffect(fase, pos) {
        if (fase == FaseOido.PARES && par != null && palabraQueSuena != null) {
            escuchas = 1
            di(par.con(palabraQueSuena))
        }
        if (fase == FaseOido.HABLAR) listener.prepareSounds()
    }

    fun listen() {
        listener.startRecording(bloque.hablar, bloque.sound) { r ->
            when (r) {
                is ListenResult.Heard -> {
                    val res = scorePronunciation(bloque.hablar, r.text)
                    result = res
                    report = r.report?.fiable(res)
                    notHeard = null
                    dicho = true
                    progreso.anotarIntento(bloque.hablar, bloque.sound, report)
                    progreso.anotarActividad(Progreso.Actividad.INTENTO)
                    report?.let { store.recordSound(it.sound, it.worst) }
                }
                is ListenResult.NotHeard -> { result = null; report = null; notHeard = r.reason }
            }
        }
    }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) listen() }
    fun grabar() {
        if (listener.recording) { listener.stopRecording(); return }
        result = null; report = null; notHeard = null; showHeard = false
        if (listener.hasMicPermission()) listen() else permiso.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun terminar() {
        val bien = toques.count { it.bien }
        store.registrarOido(bloque.id, bien, toques.size)
        onDone()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(bloque.title, onBack = onExit)
        if (fase == FaseOido.PARES) {
            LinearProgressIndicator(
                progress = { (pos + (if (toque != null) 1 else 0)).toFloat() / orden.size },
                color = accent, trackColor = Line,
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)
        ) {
            when (fase) {
                FaseOido.INTRO -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill(bloque.level, accent, Color(teacher.softColor))
                        Pill(bloque.contraste, InkSoft, Line)
                    }
                    Text(bloque.explicacion, style = MaterialTheme.typography.bodyLarge)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TeacherAvatar(voz, speaking = speaker.busy, size = 46.dp, showFace = store.showFaces)
                        Text(
                            "Este bloque lo dice ${voz.name} (${voz.accent.label}). Son ${bloque.pares.size} pares: " +
                                "oyes una frase y tocas cuál de las dos palabras sonó. Si fallas, oyes la otra para comparar.",
                            style = MaterialTheme.typography.bodyMedium, color = InkSoft
                        )
                    }
                    Text(
                        "Oír la diferencia no garantiza decirla: por eso cierras hablando.",
                        style = MaterialTheme.typography.labelLarge, color = InkSoft
                    )
                    BigButton("Empezar", container = accent) { fase = FaseOido.PARES }
                }

                FaseOido.PARES -> {
                    if (par == null || palabraQueSuena == null) {
                        fase = FaseOido.HABLAR
                    } else {
                        val t = toque
                        Text("¿Cuál palabra oíste?", style = MaterialTheme.typography.titleLarge)
                        Text("Par ${pos + 1} de ${orden.size} · ${voz.name}", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            SpeakerButton(big = true, tint = accent) { escuchas++; di(par.con(palabraQueSuena)) }
                            SpeakerButton(slow = true, tint = accent.copy(alpha = 0.75f)) { escuchas++; di(par.con(palabraQueSuena), 0.7f) }
                            Text(
                                if (t == null) "${voz.name} dice UNA de estas dos dentro de una frase. Vuelve a oírla las veces que quieras."
                                else "Frase: “${par.con(palabraQueSuena)}”",
                                style = MaterialTheme.typography.bodyMedium, color = InkSoft
                            )
                        }
                        // La frase con el hueco: el sentido no delata la respuesta.
                        Text(par.frase.replace("___", "＿＿"), style = MaterialTheme.typography.titleMedium, color = accent)
                        val opciones = listOf(par.a, par.b)
                        val answer = opciones.indexOf(palabraQueSuena)
                        val chosen = t?.let { opciones.indexOf(it.toco) } ?: -1
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            opciones.forEachIndexed { i, w ->
                                Box(modifier = Modifier.weight(1f)) {
                                    OptionRow(w, i, chosen, t != null, answer, accent) {
                                        if (toque == null) {
                                            val nuevo = Toque(par, palabraQueSuena, w)
                                            toque = nuevo
                                            toques.add(nuevo)
                                            if (!nuevo.bien) {
                                                progreso.anotarFallo(bloque.title, "oído", w, palabraQueSuena, bloque.id)
                                                // Suena la otra enseguida, para comparar.
                                                di(par.con(w))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (t != null) {
                            Text(
                                if (t.bien) "✓ Era «${t.sono}»."
                                else "✕ Era «${t.sono}», no «${t.toco}». Acabas de oír «${t.toco}» en la misma frase: compáralas.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (t.bien) GoodGreen else BadRed
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                for (w in opciones) {
                                    Text(
                                        "🔊 $w",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = accent,
                                        modifier = Modifier
                                            .background(Color.White, RoundedCornerShape(10.dp))
                                            .border(1.dp, Line, RoundedCornerShape(10.dp))
                                            .clickable { di(par.con(w)) }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            BigButton(if (pos + 1 < orden.size) "Siguiente" else "Ahora dilo tú", container = accent) {
                                toque = null
                                if (pos + 1 < orden.size) pos++ else fase = FaseOido.HABLAR
                            }
                        }
                    }
                }

                FaseOido.HABLAR -> {
                    val bien = toques.count { it.bien }
                    Text("Oíste bien $bien de ${toques.size}.", style = MaterialTheme.typography.titleMedium, color = if (bien * 100 >= toques.size * 80) GoodGreen else InkSoft)
                    Text("Ahora dilo tú", style = MaterialTheme.typography.titleLarge)
                    Text(bloque.hablar, style = MaterialTheme.typography.headlineMedium, color = accent)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        SpeakerButton(big = true, tint = accent) { di(bloque.hablar) }
                        SpeakerButton(slow = true, tint = accent.copy(alpha = 0.75f)) { di(bloque.hablar, 0.6f) }
                        Text(
                            "Oír la diferencia no garantiza decirla: por eso cierras hablando. Escucha a ${voz.name} y dilo tú.",
                            style = MaterialTheme.typography.bodyMedium, color = InkSoft
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(84.dp)
                                .background(if (listener.recording) BadRed else accent, CircleShape)
                                .clickable(enabled = !listener.thinking) { grabar() }
                        ) {
                            Text(if (listener.recording) "■" else "🎤", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when {
                                listener.thinking -> "Analizando…"
                                listener.recording -> "Grabando… toca para terminar"
                                result != null || notHeard != null -> "Toca para intentarlo otra vez"
                                else -> "Toca y dilo"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (listener.recording) BadRed else InkSoft
                        )
                        if (listener.recording) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { listener.level }, color = BadRed, trackColor = Line, modifier = Modifier.fillMaxWidth().height(6.dp))
                        }
                    }
                    notHeard?.let { NotHeardBox(it) }
                    report?.let { SoundVerdictCard(it) }
                    result?.let { r ->
                        if (report == null && bloque.sound != Sound.GENERAL) {
                            Text(
                                listener.sounds.unavailableReason
                                    ?: "El sonido \"${bloque.sound.labelEs}\" todavía no tiene evaluación por fonema calibrada: aquí solo se mira si se te entendió.",
                                style = MaterialTheme.typography.labelMedium, color = InkSoft
                            )
                        }
                        Text(
                            if (r.entendida) "Se te entendió: ${r.percent} % de las palabras"
                            else "No se te entendió del todo: ${r.percent} % de las palabras",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (r.entendida) GoodGreen else Color(0xFF8A5A00)
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            r.words.forEach { sw ->
                                val (bg, fg) = when (sw.score) {
                                    WordScore.BIEN -> GoodGreenSoft to GoodGreen
                                    WordScore.DUDOSO -> Color(0xFFFFF6E3) to Color(0xFF8A5A00)
                                    WordScore.MAL -> BadRedSoft to BadRed
                                }
                                Text(
                                    sw.word, style = MaterialTheme.typography.bodyLarge, color = fg,
                                    modifier = Modifier.padding(vertical = 3.dp).background(bg, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                        if (!showHeard) Text("Ver lo que oyó el dictado →", style = MaterialTheme.typography.labelMedium, color = accent, modifier = Modifier.clickable { showHeard = true })
                        else Text("El dictado oyó: \"${r.heard}\"", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                    }
                    if (dicho) BigButton("Terminar el bloque", container = accent) { fase = FaseOido.FIN }
                    else Text(
                        "El bloque termina cuando lo digas (una vez basta, no hay nota).",
                        style = MaterialTheme.typography.labelMedium, color = InkSoft
                    )
                }

                FaseOido.FIN -> {
                    val bien = toques.count { it.bien }
                    Text("Bloque terminado", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Oíste bien $bien de ${toques.size} pares con la voz de ${voz.name}.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (bien * 100 >= toques.size * 80) GoodGreen else InkSoft
                    )
                    val fallados = toques.filter { !it.bien }
                    if (fallados.isNotEmpty()) {
                        Text("Los que se te escaparon:", style = MaterialTheme.typography.titleSmall)
                        for (f in fallados) {
                            Text(
                                "• sonó «${f.sono}», tocaste «${f.toco}»  🔊",
                                style = MaterialTheme.typography.bodyMedium, color = accent,
                                modifier = Modifier.clickable { di(f.par.con(f.sono)) }
                            )
                        }
                        Text(
                            "Vuelve a hacer el bloque otro día: con otra voz suena distinto, y eso es justo lo que entrena.",
                            style = MaterialTheme.typography.bodyMedium, color = InkSoft
                        )
                    }
                    BigButton("Listo", container = accent) { terminar() }
                }
            }
        }
    }
}
