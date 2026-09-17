package com.ferolabs.hablo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/**
 * **Dictado de números** (horas, fechas, precios, teléfonos, deletreos,
 * direcciones; datos de Cowork, 17-09, `dictado.json`): la tarea literal de
 * Aptis Listening parte 1. La profesora lee el audio (en palabras, como se
 * dice), hasta DOS escuchas como en el examen, y el alumno escribe o elige.
 * La comparación es la ESTRICTA de [Correccion.dictado]: «$650» no vale por
 * «$6.50» ni «14:50» por «1450» (con [Correccion.suelta] sí valían, y un
 * falso «bien» aquí es justo lo que enseña mal). Sin reloj. Los fallos entran
 * al cuaderno (tipo "números"). Nada toca la red.
 *
 * **Deletreos y Grace (medido el 17-09, scratchpad oir_deletreo*.py, con
 * Moonshine y Parakeet):** las letras sueltas de Grace no se entienden con
 * ninguna forma de escribirlas («K, Y, L, E» → "case of white's L. E.";
 * «kay, why, el, ee» → "Carl Kate Wise"); Sophie las dice limpias 8 de 8,
 * Emma y Mia bien casi siempre. Por eso, si la profesora es Grace, los
 * deletreos los lee Sophie y la pantalla lo dice. La «A,» NO se lee como el
 * artículo (era el temor de Cowork): sale "ay" en las cuatro voces.
 */
@Composable
fun DictadoScreen(
    teacher: Teacher,
    speaker: Speaker,
    store: Store,
    progreso: Progreso,
    speechScale: Float,
    onBack: () -> Unit
) {
    val accent = Color(teacher.color)
    var tipo by remember { mutableStateOf<String?>(null) }   // "" = mezclado
    var tick by remember { mutableStateOf(0) }
    DisposableEffect(Unit) { onDispose { speaker.stop() } }

    val elegido = tipo
    // El "atrás" del sistema dentro de una ronda vuelve a la lista, no al inicio.
    BackHandler(enabled = elegido != null) { speaker.stop(); tick++; tipo = null }
    if (elegido == null) {
        val todos = Course.dictado
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar("🔢 Dictado de números", onBack = onBack)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f).padding(20.dp)) {
                item {
                    Text(
                        "La profesora dice un número, una hora, un precio, una fecha, un teléfono, un nombre deletreado " +
                            "o una dirección, y tú lo escribes o lo eliges. Hasta dos escuchas, como en el examen Aptis. " +
                            "Sin reloj; lo que falles queda en tu cuaderno.",
                        style = MaterialTheme.typography.bodyMedium, color = InkSoft
                    )
                }
                val grupos = listOf("" to "Mezclado") + ItemDictado.TIPOS.filter { t -> todos.any { it.tipo == t.first } }
                for ((clave, nombre) in grupos) {
                    item(key = "tipo-$clave") {
                        val del = if (clave.isEmpty()) todos else todos.filter { it.tipo == clave }
                        val hechos = remember(tick) { del.count { store.dictadoHecho(it.id) } }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(16.dp))
                                .border(1.dp, if (hechos == del.size && del.isNotEmpty()) GoodGreen.copy(alpha = 0.4f) else Line, RoundedCornerShape(16.dp))
                                .clickable { tipo = clave }
                                .padding(16.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(if (clave.isEmpty()) "🎲 Mezclado" else nombre, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${del.size} ítems · $hechos ya salieron bien" +
                                        (if (clave.isEmpty()) "" else " · " + del.map { it.level }.distinct().sorted().joinToString("/")),
                                    style = MaterialTheme.typography.bodyMedium, color = InkSoft
                                )
                            }
                            Text("›", style = MaterialTheme.typography.headlineMedium, color = accent)
                        }
                    }
                }
            }
        }
        return
    }

    RondaDictado(
        items = remember(elegido) { rondaDictado(if (elegido.isEmpty()) Course.dictado else Course.dictado.filter { it.tipo == elegido }, store) },
        titulo = if (elegido.isEmpty()) "Mezclado" else ItemDictado.nombreTipo(elegido),
        teacher = teacher,
        speaker = speaker,
        store = store,
        progreso = progreso,
        speechScale = speechScale,
        onDone = { tick++; speaker.stop(); tipo = null },
        onExit = { speaker.stop(); tipo = null }
    )
}

/** Una ronda de hasta [RONDA_DICTADO] ítems: primero los que nunca salieron bien, barajados; luego los demás. */
fun rondaDictado(candidatos: List<ItemDictado>, store: Store, max: Int = RONDA_DICTADO): List<ItemDictado> {
    val (nuevos, hechos) = candidatos.partition { !store.dictadoHecho(it.id) }
    return (nuevos.shuffled() + hechos.shuffled()).take(max)
}

const val RONDA_DICTADO = 10
private const val ESCUCHAS_MAX = 2

@Composable
private fun RondaDictado(
    items: List<ItemDictado>,
    titulo: String,
    teacher: Teacher,
    speaker: Speaker,
    store: Store,
    progreso: Progreso,
    speechScale: Float,
    onDone: () -> Unit,
    onExit: () -> Unit
) {
    val accent = Color(teacher.color)
    /** Quién lee el ítem: la profesora, salvo los deletreos con Grace (sus letras no se entienden; medido). */
    fun vozDe(it: ItemDictado): Teacher =
        if (it.tipo == "deletreo" && teacher.id == "grace") TEACHERS.first { t -> t.id == "sophie" } else teacher
    fun say(it: ItemDictado) = speaker.speak(it.audio, vozDe(it), speechScale)
    var pos by remember { mutableStateOf(0) }
    var escrito by remember { mutableStateOf("") }
    var elegida by remember { mutableStateOf(-1) }
    var checked by remember { mutableStateOf(false) }
    var correcto by remember { mutableStateOf(false) }
    var escuchas by remember { mutableStateOf(0) }
    var bien by remember { mutableStateOf(0) }
    val item = items.getOrNull(pos)

    // Al llegar a un ítem suena una vez sola; la segunda la pide el alumno (dos como en el examen).
    LaunchedEffect(pos) {
        if (item != null) { escuchas = 1; say(item) }
    }

    fun comprobar() {
        val it = item ?: return
        if (checked) return
        val tuya = if (it.escribir) escrito.trim() else it.options.getOrElse(elegida) { "" }
        if (tuya.isBlank()) return
        correcto = if (it.escribir) Correccion.aceptaDictado(tuya, it.answer, it.accept) else tuya == it.answer
        checked = true
        progreso.anotarActividad(Progreso.Actividad.EJERCICIO)
        if (correcto) { bien++; store.marcarDictado(it.id) }
        else progreso.anotarFallo("Dictado · ${ItemDictado.nombreTipo(it.tipo)}", "números", tuya, it.answer, it.id)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar("🔢 $titulo", onBack = onExit)
        LinearProgressIndicator(
            progress = { (pos + (if (checked) 1 else 0)).toFloat() / items.size.coerceAtLeast(1) },
            color = accent, trackColor = Line, modifier = Modifier.fillMaxWidth().height(6.dp)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)
        ) {
            if (item == null) {
                Text("Ronda terminada", style = MaterialTheme.typography.titleLarge)
                Text(
                    "$bien de ${items.size} bien.",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (bien * 100 >= items.size * 80) GoodGreen else accent
                )
                Text(
                    if (bien == items.size) "Todo. Prueba otro tipo, o el mezclado."
                    else "Lo que fallaste quedó en tu cuaderno y vuelve a salir primero la próxima ronda.",
                    style = MaterialTheme.typography.bodyMedium, color = InkSoft
                )
                BigButton("Listo", container = accent) { onDone() }
                return@Column
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(ItemDictado.nombreTipo(item.tipo), accent, Color(teacher.softColor))
                Pill(item.level, InkSoft, Line)
                Spacer(Modifier.weight(1f))
                Text("${pos + 1} de ${items.size}", style = MaterialTheme.typography.labelMedium, color = InkSoft)
            }
            Text(item.preguntaEs, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val quedan = ESCUCHAS_MAX - escuchas
                SpeakerButton(big = true, tint = if (quedan > 0 || checked) accent else Line) {
                    if (checked || escuchas < ESCUCHAS_MAX) { escuchas++; say(item) }
                }
                Text(
                    when {
                        checked -> "Ya puedes reoírlo las veces que quieras."
                        quedan > 0 -> "Te queda $quedan escucha más, como en el examen."
                        else -> "Ya la oíste dos veces: responde con lo que tienes."
                    } + (if (vozDe(item) != teacher) " Deletrea ${vozDe(item).name}: a ${teacher.name} no se le entienden las letras sueltas." else ""),
                    style = MaterialTheme.typography.bodyMedium, color = InkSoft
                )
            }

            if (item.escribir) {
                OutlinedTextField(
                    value = escrito,
                    onValueChange = { if (!checked) escrito = it },
                    enabled = !checked,
                    singleLine = true,
                    placeholder = { Text("Escribe aquí…") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { comprobar() }),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                item.options.forEachIndexed { i, opt ->
                    OptionRow(opt, i, elegida, checked, item.options.indexOf(item.answer), accent) { if (!checked) elegida = i }
                }
            }

            if (!checked) {
                BigButton(
                    "Comprobar",
                    enabled = if (item.escribir) escrito.isNotBlank() else elegida >= 0,
                    container = accent
                ) { comprobar() }
            } else {
                Text(
                    if (correcto) "✓ Bien." else "✕ Era «${item.answer}»." +
                        (if (item.escribir && escrito.isNotBlank()) " Tú pusiste «${escrito.trim()}»." else ""),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (correcto) GoodGreen else BadRed
                )
                Text("Decía: “${item.audio}”", style = MaterialTheme.typography.bodyMedium, color = accent)
                TipBox(item.tip)
                BigButton(if (pos + 1 < items.size) "Siguiente" else "Ver el resultado", container = accent) {
                    pos++; escrito = ""; elegida = -1; checked = false; correcto = false
                }
            }
        }
    }
}
