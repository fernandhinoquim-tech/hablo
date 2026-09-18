package com.ferolabs.hablo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * **Crucigramas** (ver Crucigrama.kt para el porqué). La pantalla, tal como la
 * pidió Cowork (COMO-CONSTRUIR-CRUCIGRAMAS.md) con una diferencia: el teclado
 * es PROPIO (solo letras y ⌫), no el del sistema. El del sistema trae
 * autocorrección, predicciones y el foco que se pierde al tocar una casilla,
 * y no se puede probar sin el teléfono a mano; un teclado de 26 letras dentro
 * de la pantalla es lo que hacen las apps de crucigramas y no falla.
 * - Tocar una casilla selecciona su palabra; en un cruce, tocar otra vez
 *   cambia de dirección. Arriba, la pista: «3 ↓ cocina».
 * - Corrección inmediata por palabra: completa y bien → verde y Piper la dice;
 *   completa y mal → marca suave, sin castigo.
 * - Ayudas: «Una letra» (la palabra ya no cuenta como "sin ayuda») y «Oírla»
 *   (Piper la pronuncia: se vuelve dictado; tampoco cuenta).
 * - Sin reloj. Al terminar: «8 de 8 · 6 sin ayuda»; las que necesitaron ayuda
 *   entran al mazo (`cruci:<banco>|<en>`). El avance se guarda a cada letra.
 */
@Composable
fun CrucigramasScreen(
    teacher: Teacher,
    speaker: Speaker,
    crucigramas: Crucigramas,
    mazo: Mazo,
    progreso: Progreso,
    say: (String, Float) -> Unit,
    onBack: () -> Unit
) {
    val accent = Color(teacher.color)
    var abierto by remember { mutableStateOf<Crucigrama?>(null) }
    var tick by remember { mutableStateOf(0) }
    DisposableEffect(Unit) { onDispose { speaker.stop() } }
    // Con 166 rejillas (B1, 17-09) cada nivel se pliega: abiertos los niveles con algo empezado
    // o hecho y, si no hay nada, A1.
    var niveles by remember {
        val tocados = Course.crucigramas.filter { crucigramas.estado(it.id).let { e -> e.hecho || e.letras.isNotEmpty() } }.map { it.level }.toSet()
        mutableStateOf(tocados.ifEmpty { setOf("A1") })
    }

    val actual = abierto
    // El "atrás" del sistema dentro de un crucigrama vuelve a la lista, no al inicio.
    BackHandler(enabled = actual != null) { speaker.stop(); tick++; abierto = null }
    if (actual == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar("✏️ Crucigramas", onBack = onBack)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f).padding(20.dp)) {
                item {
                    Text(
                        "Pistas en español, respuestas en inglés. Cada crucigrama sale de un tema del vocabulario. " +
                            "Sin reloj: es el descanso. Puedes salir a mitad y volver.",
                        style = MaterialTheme.typography.bodyMedium, color = InkSoft
                    )
                }
                for (nivel in listOf("A1", "A2", "B1", "B2")) {
                    val delNivel = Course.crucigramas.filter { it.level == nivel }
                    if (delNivel.isEmpty()) continue
                    val plegado = nivel !in niveles
                    item(key = "nivel-$nivel") {
                        val hechos = remember(tick) { delNivel.count { crucigramas.estado(it.id).hecho } }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { niveles = if (plegado) niveles + nivel else niveles - nivel }
                                .padding(top = 8.dp, bottom = 4.dp)
                        ) {
                            Pill(nivel, accent, Color(teacher.softColor))
                            Spacer(Modifier.size(10.dp))
                            Text("${delNivel.size} crucigramas", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.weight(1f))
                            Text("$hechos hechos", style = MaterialTheme.typography.labelLarge, color = InkSoft)
                            Spacer(Modifier.size(8.dp))
                            Text(if (plegado) "›" else "⌄", style = MaterialTheme.typography.titleLarge, color = accent)
                        }
                    }
                    if (plegado) continue
                    items(delNivel, key = { it.id }) { c ->
                        val e = remember(tick) { crucigramas.estado(c.id) }
                        val empezado = !e.hecho && e.letras.isNotEmpty()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(16.dp))
                                .border(1.dp, if (e.hecho) GoodGreen.copy(alpha = 0.4f) else if (empezado) accent.copy(alpha = 0.5f) else Line, RoundedCornerShape(16.dp))
                                .clickable { abierto = c }
                                .padding(16.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${c.palabras.size} palabras" + when {
                                        e.hecho -> " · hecho: ${e.sinAyuda} sin ayuda"
                                        empezado -> " · a medias"
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.bodyMedium, color = InkSoft
                                )
                            }
                            if (e.hecho) Text("✓", style = MaterialTheme.typography.titleMedium, color = GoodGreen)
                        }
                    }
                }
            }
        }
        return
    }

    CrucigramaScreen(
        cruci = actual,
        teacher = teacher,
        crucigramas = crucigramas,
        mazo = mazo,
        progreso = progreso,
        say = say,
        onBack = { speaker.stop(); tick++; abierto = null }
    )
}

private val TECLAS = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")

@Composable
private fun CrucigramaScreen(
    cruci: Crucigrama,
    teacher: Teacher,
    crucigramas: Crucigramas,
    mazo: Mazo,
    progreso: Progreso,
    say: (String, Float) -> Unit,
    onBack: () -> Unit
) {
    val accent = Color(teacher.color)
    var estado by remember { mutableStateOf(crucigramas.estado(cruci.id)) }
    var palabra by remember { mutableStateOf<PalabraCruci?>(cruci.palabras.minByOrNull { it.numero }) }
    var cursor by remember { mutableStateOf<Celda?>(palabra?.celdas?.firstOrNull()) }
    /** Palabras que ya se dijeron en voz alta al completarse bien (para no repetirlas a cada letra). */
    val dichas = remember { HashSet<String>() }
    var terminado by remember { mutableStateOf(estado.hecho) }

    fun guardar(nuevo: EstadoCruci) {
        estado = nuevo
        crucigramas.guardar(cruci.id, nuevo)
    }

    /** La siguiente palabra sin resolver después de [desde] (por número, horizontales primero), o null si no queda. */
    fun siguientePendiente(nuevo: EstadoCruci, desde: PalabraCruci?): PalabraCruci? {
        val orden = cruci.palabras.sortedWith(compareBy({ it.numero }, { !it.horizontal }))
        val i = if (desde == null) -1 else orden.indexOf(desde)
        return (orden.drop(i + 1) + orden.take(i + 1)).firstOrNull { !nuevo.correcta(it) }
    }

    /** Después de cada letra: ¿alguna palabra quedó completa y bien? Piper la dice y se pasa a la siguiente. ¿Se acabó todo? */
    fun revisar(nuevo: EstadoCruci) {
        var acertada = false
        for (p in cruci.palabras) {
            if (nuevo.correcta(p) && dichas.add(p.en)) { say(p.en, 1f); acertada = true }
        }
        if (acertada && palabra != null && nuevo.correcta(palabra!!)) {
            val sig = siguientePendiente(nuevo, palabra)
            if (sig != null) { palabra = sig; cursor = sig.celdas.firstOrNull { nuevo.letra(it) == null } ?: sig.celdas[0] }
        }
        if (nuevo.resuelto(cruci) && !nuevo.hecho) {
            val sinAyuda = cruci.palabras.count { it.en !in nuevo.conAyuda }
            val fin = nuevo.copy(hecho = true, sinAyuda = sinAyuda, fecha = crucigramas.hoy())
            guardar(fin)
            // Las que necesitaron ayuda entran al mazo, como el glosario de una historia.
            val pares = cruci.palabras.filter { it.en in fin.conAyuda }.map { Pareja(it.pista, it.en) }
            if (pares.isNotEmpty()) mazo.alimentarPares("cruci:${cruci.banco}", pares)
            progreso.anotarActividad(Progreso.Actividad.EJERCICIO)
            terminado = true
        } else {
            guardar(nuevo)
        }
    }

    fun seleccionar(c: Celda) {
        val p = cruci.seleccionAlTocar(c, palabra, cursor) ?: return
        palabra = p
        cursor = c
    }

    /** Casillas de la palabra seleccionada que se pueden escribir: las que no son de una palabra ya correcta. */
    fun bloqueada(c: Celda): Boolean = cruci.palabrasEn(c).any { estado.correcta(it) }

    fun escribir(letra: Char) {
        val p = palabra ?: return
        val c = cursor ?: return
        if (terminado || bloqueada(c)) return
        val nuevo = estado.copy(letras = estado.letras + (c.clave to letra.lowercaseChar()))
        // avanza a la siguiente casilla libre de la palabra (o se queda al final)
        val i = p.celdas.indexOf(c)
        val siguiente = p.celdas.drop(i + 1).firstOrNull { !bloqueada(it) && nuevo.letra(it) == null }
            ?: p.celdas.drop(i + 1).firstOrNull { !bloqueada(it) }
        if (siguiente != null) cursor = siguiente
        revisar(nuevo)
    }

    fun borrar() {
        val p = palabra ?: return
        val c = cursor ?: return
        if (terminado) return
        if (estado.letra(c) != null && !bloqueada(c)) {
            guardar(estado.copy(letras = estado.letras - c.clave))
            return
        }
        val i = p.celdas.indexOf(c)
        val anterior = p.celdas.take(i).lastOrNull { !bloqueada(it) } ?: return
        cursor = anterior
        guardar(estado.copy(letras = estado.letras - anterior.clave))
    }

    fun unaLetra() {
        val p = palabra ?: return
        if (terminado) return
        // la casilla del cursor si está vacía o mal; si no, la primera de la palabra que falte
        val c = (cursor?.takeIf { it in p.celdas && estado.letra(it) != cruci.solucion[it] })
            ?: p.celdas.firstOrNull { estado.letra(it) != cruci.solucion[it] } ?: return
        val letra = cruci.solucion[c] ?: return
        val ayudadas = estado.conAyuda + cruci.palabrasEn(c).map { it.en }
        revisar(estado.copy(letras = estado.letras + (c.clave to letra), conAyuda = ayudadas))
    }

    fun oirla() {
        val p = palabra ?: return
        say(p.en, 1f)
        if (!terminado && !estado.correcta(p)) guardar(estado.copy(conAyuda = estado.conAyuda + p.en))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(cruci.title, onBack = onBack)
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // La pista de la palabra seleccionada, arriba de la rejilla.
            val p = palabra
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (p == null) "Toca una casilla" else "${p.numero} ${p.flecha}  ${p.pista}",
                        style = MaterialTheme.typography.titleLarge, color = accent
                    )
                    if (p != null) {
                        Text(
                            when {
                                estado.correcta(p) -> "✓ ${p.en}"
                                estado.completa(p) -> "Hay algo que no cuadra: revisa las letras."
                                else -> "${p.en.length} letras"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (estado.correcta(p)) GoodGreen else InkSoft
                        )
                    }
                }
                val bien = cruci.palabras.count { estado.correcta(it) }
                Text("$bien de ${cruci.palabras.size}", style = MaterialTheme.typography.labelLarge, color = InkSoft)
            }

            Rejilla(cruci, estado, palabra, cursor, accent) { seleccionar(it) }

            if (terminado) {
                val e = estado
                Text("¡Listo! ${cruci.palabras.size} de ${cruci.palabras.size} · ${e.sinAyuda} sin ayuda", style = MaterialTheme.typography.titleLarge, color = GoodGreen)
                val conAyuda = cruci.palabras.filter { it.en in e.conAyuda }
                if (conAyuda.isNotEmpty()) {
                    Text(
                        "Con ayuda: " + conAyuda.joinToString(", ") { "${it.en} (${it.pista})" } + ". Entran a tu mazo para repasarlas.",
                        style = MaterialTheme.typography.bodyMedium, color = InkSoft
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) { BigButton("Listo", container = accent) { onBack() } }
                    Box(modifier = Modifier.weight(1f)) {
                        BigButton("Otra vez", container = InkSoft) {
                            crucigramas.reiniciar(cruci.id)
                            estado = EstadoCruci(); dichas.clear(); terminado = false
                            palabra = cruci.palabras.minByOrNull { it.numero }; cursor = palabra?.celdas?.firstOrNull()
                        }
                    }
                }
            } else {
                // Ayudas y las pistas de todas las palabras (tocar una la selecciona).
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Ayuda("💡 Una letra", accent, Modifier.weight(1f)) { unaLetra() }
                    Ayuda("🔊 Oírla", accent, Modifier.weight(1f)) { oirla() }
                }
                Teclado(accent, onLetra = { escribir(it) }, onBorrar = { borrar() })
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                    for (h in listOf(true, false)) {
                        val lista = cruci.palabras.filter { it.horizontal == h }.sortedBy { it.numero }
                        if (lista.isEmpty()) continue
                        Text(if (h) "Horizontales" else "Verticales", style = MaterialTheme.typography.titleSmall, color = InkSoft, modifier = Modifier.padding(top = 6.dp))
                        for (w in lista) {
                            val ok = estado.correcta(w)
                            Text(
                                "${w.numero}. ${w.pista}" + (if (ok) "  ✓ ${w.en}" else ""),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (ok) GoodGreen else if (w == palabra) accent else Ink,
                                modifier = Modifier.clickable { palabra = w; cursor = w.celdas.firstOrNull { estado.letra(it) == null } ?: w.celdas[0] }
                            )
                        }
                    }
                }
                Text("Sin reloj: el crucigrama es el descanso. Lo que pongas se guarda solo.", style = MaterialTheme.typography.labelMedium, color = InkSoft)
            }
        }
    }
}

@Composable
private fun Ayuda(texto: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp)
    ) {
        Text(texto, style = MaterialTheme.typography.labelLarge, color = accent)
    }
}

/** La rejilla: casillas blancas con letra y número, oscuras el resto; la palabra seleccionada resaltada. */
@Composable
private fun Rejilla(
    cruci: Crucigrama,
    estado: EstadoCruci,
    palabra: PalabraCruci?,
    cursor: Celda?,
    accent: Color,
    onTocar: (Celda) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val hueco = 2.dp
        val lado = ((maxWidth - hueco * (cruci.columnas - 1)) / cruci.columnas).coerceAtMost(40.dp)
        val ancho = lado * cruci.columnas + hueco * (cruci.columnas - 1)
        Column(verticalArrangement = Arrangement.spacedBy(hueco), modifier = Modifier.width(ancho).align(Alignment.Center)) {
            for (f in 0 until cruci.filas) {
                Row(horizontalArrangement = Arrangement.spacedBy(hueco)) {
                    for (c in 0 until cruci.columnas) {
                        val celda = Celda(f, c)
                        val blanca = celda in cruci.solucion
                        if (!blanca) {
                            Box(modifier = Modifier.size(lado).background(Ink.copy(alpha = 0.85f), RoundedCornerShape(3.dp)))
                            continue
                        }
                        val letra = estado.letra(celda)
                        val enPalabra = palabra != null && celda in palabra.celdas
                        val correcta = cruci.palabrasEn(celda).any { estado.correcta(it) }
                        val malCompleta = !correcta && cruci.palabrasEn(celda).any { estado.completa(it) && !estado.correcta(it) }
                        val fondo = when {
                            celda == cursor -> accent.copy(alpha = 0.35f)
                            enPalabra -> accent.copy(alpha = 0.15f)
                            correcta -> GoodGreenSoft
                            malCompleta -> BadRedSoft
                            else -> Color.White
                        }
                        Box(
                            modifier = Modifier
                                .size(lado)
                                .background(fondo, RoundedCornerShape(3.dp))
                                .border(if (celda == cursor) 2.dp else 1.dp, if (celda == cursor) accent else Line, RoundedCornerShape(3.dp))
                                .clickable { onTocar(celda) }
                        ) {
                            cruci.numeroEn[celda]?.let { n ->
                                Text(n.toString(), fontSize = 8.sp, color = InkSoft, modifier = Modifier.align(Alignment.TopStart).padding(start = 2.dp))
                            }
                            if (letra != null) {
                                Text(
                                    letra.uppercaseChar().toString(),
                                    fontSize = (lado.value * 0.5f).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (correcta) GoodGreen else Ink,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Teclado propio: 26 letras y ⌫. Nada de autocorrección ni de foco que se pierde. */
@Composable
private fun Teclado(accent: Color, onLetra: (Char) -> Unit, onBorrar: () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val hueco = 4.dp
        val ancho = ((maxWidth - hueco * 9) / 10).coerceAtMost(40.dp)
        val alto = 44.dp
        Column(verticalArrangement = Arrangement.spacedBy(hueco), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            TECLAS.forEachIndexed { fila, letras ->
                Row(horizontalArrangement = Arrangement.spacedBy(hueco)) {
                    for (ch in letras) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .width(ancho).height(alto)
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .border(1.dp, Line, RoundedCornerShape(8.dp))
                                .clickable { onLetra(ch) }
                        ) {
                            Text(ch.toString(), style = MaterialTheme.typography.titleMedium, color = Ink)
                        }
                    }
                    if (fila == TECLAS.size - 1) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .width(ancho * 2 + hueco).height(alto)
                                .background(accent.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .clickable { onBorrar() }
                        ) {
                            Text("⌫", style = MaterialTheme.typography.titleMedium, color = accent)
                        }
                    }
                }
            }
        }
    }
}
