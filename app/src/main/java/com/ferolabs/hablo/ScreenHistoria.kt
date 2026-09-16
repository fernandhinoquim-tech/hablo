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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * **Historias cortas** (etapa 4). Intervención narrativa oral: d = 1,36 global
 * (lectura 1,87 · gramática 1,71 · habla 1,38 · vocabulario 1,31), y máxima en
 * tandas de 3 a 12 sesiones (d = 2,53). Flujo, en este orden y sin saltar
 * ninguno: la profesora LEE la historia y está en pantalla → 3 preguntas →
 * **él la cuenta de vuelta en voz alta** con las pistas (el retell NO es
 * opcional: sin él es comprensión lectora y se pierde la mitad del efecto) →
 * el glosario pasa al mazo. El retell se puntúa como `shadow`: cobertura de
 * palabras y soltura, nunca por fonema, y nunca reprueba.
 */
@Composable
fun HistoriasScreen(
    teacher: Teacher,
    store: Store,
    onPick: (Historia) -> Unit,
    onBack: () -> Unit
) {
    val accent = Color(teacher.color)
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar("📚 Historias", onBack = onBack)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f).padding(20.dp)
        ) {
            item {
                Text(
                    "${teacher.name} te lee una historia corta, le respondes tres preguntas y después SE LA CUENTAS tú, " +
                        "en voz alta, con unas pistas. Contarla es la parte que enseña: no te la saltes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft
                )
            }
            if (Course.tandas.isEmpty()) {
                item { Text("Todavía no hay historias.", style = MaterialTheme.typography.bodyMedium, color = InkSoft) }
            }
            for (tanda in Course.tandas) {
                item(key = "tanda-${tanda.id}") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Pill(tanda.level, accent, Color(teacher.softColor))
                        Spacer(Modifier.size(10.dp))
                        Text(tanda.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${tanda.historias.count { store.historiaHecha(it.id) }} de ${tanda.historias.size}",
                            style = MaterialTheme.typography.labelLarge, color = InkSoft
                        )
                    }
                }
                items(tanda.historias, key = { it.id }) { h ->
                    val hecha = store.historiaHecha(h.id)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .border(1.dp, if (hecha) GoodGreen.copy(alpha = 0.4f) else Line, RoundedCornerShape(16.dp))
                            .clickable { onPick(h) }
                            .padding(16.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(40.dp).background(if (hecha) GoodGreenSoft else Color(teacher.softColor), CircleShape)
                        ) {
                            Text(if (hecha) "✓" else "📖", style = MaterialTheme.typography.titleMedium, color = if (hecha) GoodGreen else Ink)
                        }
                        Spacer(Modifier.size(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(h.title, style = MaterialTheme.typography.titleMedium)
                            Text(h.titleEs + " · ${h.texto.split(" ").size} palabras", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                        }
                    }
                }
            }
        }
    }
}

private enum class Fase { LEER, PREGUNTAS, RETELL, FIN }

@Composable
fun HistoriaScreen(
    historia: Historia,
    teacher: Teacher,
    speaker: Speaker,
    listener: Listener,
    store: Store,
    mazo: Mazo,
    progreso: Progreso,
    say: (String, Float) -> Unit,
    sayQueued: (String) -> Unit,
    onDone: () -> Unit,
    onExit: () -> Unit
) {
    val accent = Color(teacher.color)
    var fase by remember { mutableStateOf(Fase.LEER) }
    // Preguntas
    var qIdx by remember { mutableStateOf(0) }
    var chosen by remember { mutableStateOf(-1) }
    var checked by remember { mutableStateOf(false) }
    var aciertos by remember { mutableStateOf(0) }
    val orden = remember(qIdx) { historia.preguntas[qIdx.coerceIn(0, historia.preguntas.size - 1)].options.indices.shuffled() }
    // Retell: una grabación por pista (cada una cabe en 20 s), y se juntan.
    var parte by remember { mutableStateOf(0) }
    val oidos = remember { mutableStateOf(listOf<String>()) }
    var segundos by remember { mutableStateOf(0f) }
    var notHeard by remember { mutableStateOf<NotHeardReason?>(null) }
    var alMazo by remember { mutableStateOf(-1) }

    fun leer() {
        speaker.stop()
        for (frase in historia.text) sayQueued(frase)
    }

    LaunchedEffect(Unit) { leer() }
    DisposableEffect(Unit) { onDispose { speaker.stop(); listener.stopRecording() } }

    fun grabar() {
        listener.startRecording(target = historia.texto, sound = Sound.GENERAL, maxSeconds = 20) { r ->
            when (r) {
                is ListenResult.Heard -> {
                    oidos.value = oidos.value + r.text
                    segundos += r.speechSeconds
                    notHeard = null
                    progreso.anotarActividad(Progreso.Actividad.INTENTO)
                    if (parte + 1 < historia.pistas.size) parte += 1 else fase = Fase.FIN
                }
                is ListenResult.NotHeard -> notHeard = r.reason
            }
        }
    }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) grabar() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = when (fase) {
                Fase.LEER -> "${historia.title}  ·  leer"
                Fase.PREGUNTAS -> "${historia.title}  ·  pregunta ${qIdx + 1}/3"
                Fase.RETELL -> "${historia.title}  ·  cuéntala ${parte + 1}/${historia.pistas.size}"
                Fase.FIN -> "${historia.title}  ·  listo"
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
                Fase.LEER -> {
                    Text(historia.title, style = MaterialTheme.typography.headlineSmall, color = accent)
                    Text(historia.titleEs, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SpeakerButton(big = true, tint = accent) { leer() }
                        SpeakerButton(slow = true, tint = accent.copy(alpha = 0.75f)) {
                            speaker.stop(); for (f in historia.text) speaker.speakQueued(f, teacher, 0.8f * store.speechScale)
                        }
                        Text("Sigue el texto mientras ${teacher.name} lo lee. Óyela las veces que quieras.", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(14.dp))
                            .border(1.dp, Line, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        for (frase in historia.text) {
                            Text(
                                frase, style = MaterialTheme.typography.bodyLarge, color = Ink,
                                modifier = Modifier.clickable { speaker.stop(); say(frase, 1f) }
                            )
                        }
                    }
                    Text("Palabras que van a tu repaso", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                    for (p in historia.glosario) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.en, style = MaterialTheme.typography.bodyLarge, color = Ink, modifier = Modifier.weight(1f).clickable { say(p.en, 1f) })
                            Text(p.es, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                        }
                    }
                    if (historia.trap.isNotBlank()) TipBox(historia.trap)
                }

                Fase.PREGUNTAS -> {
                    val q = historia.preguntas[qIdx]
                    Text(q.q, style = MaterialTheme.typography.headlineSmall, color = Ink)
                    Text("Elige según la historia (sin volver a leerla).", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                    orden.forEach { i ->
                        val opt = q.options[i]
                        val esCorrecta = opt == q.answer
                        val bg = when {
                            checked && esCorrecta -> GoodGreenSoft
                            checked && chosen == i -> BadRedSoft
                            chosen == i -> accent.copy(alpha = 0.12f)
                            else -> Color.White
                        }
                        Text(
                            opt, style = MaterialTheme.typography.bodyLarge, color = Ink,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bg, RoundedCornerShape(12.dp))
                                .border(1.dp, if (chosen == i) accent else Line, RoundedCornerShape(12.dp))
                                .clickable(enabled = !checked) { chosen = i }
                                .padding(14.dp)
                        )
                    }
                    if (checked) {
                        val bien = q.options[chosen] == q.answer
                        Text(
                            if (bien) "¡Correcto!" else "Casi: era «${q.answer}».",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (bien) GoodGreen else BadRed
                        )
                    }
                }

                Fase.RETELL -> {
                    Text("Ahora cuéntala tú", style = MaterialTheme.typography.headlineSmall, color = accent)
                    Text(historia.retellPromptEs + " En voz alta, por partes: una pista cada vez.", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(14.dp))
                            .border(1.dp, Line, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        historia.pistas.forEachIndexed { i, pista ->
                            Text(
                                "${i + 1}. $pista",
                                style = if (i == parte) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                                color = when { i < parte -> GoodGreen; i == parte -> Ink; else -> InkSoft }
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(96.dp)
                                .background(if (listener.recording) BadRed else accent, CircleShape)
                                .clickable(enabled = !listener.thinking) {
                                    when {
                                        listener.recording -> listener.stopRecording()
                                        listener.hasMicPermission() -> { notHeard = null; grabar() }
                                        else -> permiso.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                        ) {
                            Text(if (listener.recording) "■" else "🎤", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when {
                                listener.thinking -> "Escuchando…"
                                listener.recording -> "Grabando (hasta 20 s)… toca para terminar"
                                else -> "Toca y cuenta la parte ${parte + 1}"
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
                    Text(
                        "Aquí no se juzga cada sonido: cuenta que la historia salga con tus palabras. No hay reloj.",
                        style = MaterialTheme.typography.labelMedium, color = InkSoft
                    )
                    Text(
                        "🔊 Oírla otra vez antes de contar esta parte",
                        style = MaterialTheme.typography.labelLarge, color = accent,
                        modifier = Modifier.clickable { if (!listener.recording) leer() }.padding(vertical = 4.dp)
                    )
                }

                Fase.FIN -> {
                    val cobertura = remember { coberturaRetell(historia, oidos.value) }
                    if (alMazo < 0) {
                        alMazo = mazo.alimentarPares("historia:${historia.id}", historia.glosario)
                        store.marcarHistoria(historia.id)
                        progreso.anotarActividad(Progreso.Actividad.LECCION)
                    }
                    Text("¡Contada!", style = MaterialTheme.typography.headlineSmall, color = GoodGreen)
                    Text(
                        "Preguntas: $aciertos de ${historia.preguntas.size}.",
                        style = MaterialTheme.typography.bodyLarge, color = Ink
                    )
                    Text(
                        "Al contarla usaste ${cobertura.first} de ${cobertura.second} palabras clave de la historia" +
                            (if (segundos > 0f) ", hablando %.0f segundos.".format(Locale("es"), segundos) else "."),
                        style = MaterialTheme.typography.bodyLarge, color = Ink
                    )
                    Text(
                        "Es un dato, no una nota: lo que enseña es contarla. La próxima vez intenta meter las que faltaron.",
                        style = MaterialTheme.typography.bodyMedium, color = InkSoft
                    )
                    if (alMazo > 0) Text("$alMazo palabras del glosario pasaron a tu repaso.", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                    val faltaron = remember { cobertura.third }
                    if (faltaron.isNotEmpty()) Text("Te faltaron: " + faltaron.take(8).joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().background(Cream).padding(20.dp)) {
            when (fase) {
                Fase.LEER -> BigButton("Ya la leí: a las preguntas", container = accent) { speaker.stop(); fase = Fase.PREGUNTAS }
                Fase.PREGUNTAS -> {
                    if (!checked) BigButton("Comprobar", enabled = chosen >= 0, container = accent) {
                        checked = true
                        val q = historia.preguntas[qIdx]
                        progreso.anotarActividad(Progreso.Actividad.EJERCICIO)
                        if (q.options[chosen] == q.answer) { aciertos += 1; say(teacher.encouragement[qIdx % teacher.encouragement.size], 1f) }
                        else say(q.answer, 0.9f)
                    } else BigButton(if (qIdx + 1 < historia.preguntas.size) "Siguiente" else "Ahora cuéntala tú", container = GoodGreen) {
                        speaker.stop()
                        if (qIdx + 1 < historia.preguntas.size) { qIdx += 1; chosen = -1; checked = false } else fase = Fase.RETELL
                    }
                }
                Fase.RETELL -> Text(
                    "Sin contarla no termina: es la parte que más enseña.",
                    style = MaterialTheme.typography.labelMedium, color = InkSoft, modifier = Modifier.fillMaxWidth()
                )
                Fase.FIN -> BigButton("Listo", container = accent) { onDone() }
            }
        }
    }
}

/** (usadas, total, faltaron): palabras de contenido de la historia que aparecieron en lo que se oyó. */
private fun coberturaRetell(historia: Historia, oidos: List<String>): Triple<Int, Int, List<String>> {
    val vacias = setOf("the", "a", "an", "to", "of", "in", "on", "at", "and", "was", "were", "is", "are", "he", "she", "it", "they", "his", "her", "him", "them", "i", "you", "we", "my", "for", "with", "that", "this", "had", "have", "has", "did", "do", "not", "but", "so", "from", "by", "as", "be", "there", "then", "than", "up", "out", "into", "who", "what", "when", "where", "very", "just", "about", "next", "before", "after", "same", "all", "no", "yes", "or", "if", "because", "said", "got", "get", "went", "go", "came", "come", "took", "take", "made", "make", "one", "two", "three", "day", "back", "way", "much", "more", "some", "any", "him")
    val clave = Correccion.sueltaEstricta(historia.texto).split(" ").filter { it.length > 2 && it !in vacias }.distinct()
    val dichas = oidos.joinToString(" ").let { Correccion.sueltaEstricta(it) }.split(" ").toSet()
    val usadas = clave.filter { it in dichas }
    return Triple(usadas.size, clave.size, clave.filter { it !in dichas })
}
