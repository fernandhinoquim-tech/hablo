package com.ferolabs.hablo

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import java.util.Locale

/**
 * **Contrarreloj** (etapa 3): Parejas con reloj y banco de vocabulario, en
 * rondas de hasta [RONDA] parejas. La regla que importa: **lo fallado vuelve**
 * hasta que salga limpio (una pareja con algún fallo en una ronda entra otra
 * vez en una ronda posterior). El reloj es velocímetro, no juez: guarda la
 * curva de su marca (segundos por pareja, por banco) y nunca reprueba por lento.
 *
 * Bancos: los de `vocabulario.json` (los escribe Cowork: dentro de un banco
 * garantiza que no hay dos ingleses casi sinónimos ni dos españoles
 * equivalentes, así que nunca hay dos respuestas válidas a la vez) y, siempre,
 * "Frases de tus lecciones" con lo que ya vio en las lecciones aprobadas.
 * **Una ronda nunca mezcla bancos**: al mezclar, esa garantía desaparece. Un
 * banco grande se parte en rondas de hasta [RONDA] parejas del mismo banco
 * (un subconjunto conserva la garantía).
 */
@Composable
fun ContrarrelojScreen(
    teacher: Teacher,
    store: Store,
    mazo: Mazo,
    onBack: () -> Unit
) {
    val accent = Color(teacher.color)
    val bancos = remember {
        val hechas = Course.allLessons().filter { store.bestScore(it.id) >= 60 }
        val deLecciones = hechas.flatMap { parejasDe(it, max = 6) }
            .distinctBy { it.en.lowercase(Locale.US) }
            .distinctBy { it.es.lowercase(Locale.US) }
        val propio = if (deLecciones.size >= 3) listOf(Banco(BANCO_LECCIONES, "Frases de tus lecciones", "", deLecciones)) else emptyList()
        propio + Course.bancos
    }
    var banco by remember { mutableStateOf<Banco?>(null) }

    val actual = banco
    if (actual == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar("⏱ Contrarreloj", onBack = onBack)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).padding(20.dp)
            ) {
                item {
                    Text(
                        "Une parejas contra el reloj, en rondas de hasta $RONDA. Lo que falles vuelve hasta que te salga. " +
                            "El reloj no califica: solo te muestra si vas más rápido que tu marca.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSoft
                    )
                }
                if (bancos.isEmpty()) {
                    item {
                        Text(
                            "Todavía no hay parejas: termina una lección y vuelve.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSoft
                        )
                    }
                }
                items(bancos, key = { it.id }) { b ->
                    val marcas = mazo.marcas(b.id)
                    val mejor = marcas.minByOrNull { it.porPareja }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .border(1.dp, Line, RoundedCornerShape(16.dp))
                            .clickable { banco = b }
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(b.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${b.pares.size} parejas" + (if (mejor != null) " · tu marca: ${"%.1f".format(Locale("es"), mejor.porPareja)} s por pareja" else " · sin marca todavía"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkSoft
                            )
                        }
                        if (b.level.isNotBlank()) Pill(b.level, accent, Color(teacher.softColor))
                    }
                }
            }
        }
        return
    }

    Sesion(banco = actual, accent = accent, mazo = mazo, onBack = { banco = null })
}

@Composable
private fun Sesion(banco: Banco, accent: Color, mazo: Mazo, onBack: () -> Unit) {
    // La cola: todas las parejas barajadas; las falladas vuelven al final.
    val cola = remember(banco.id) { java.util.ArrayDeque(banco.pares.shuffled()) }
    var ronda by remember(banco.id) { mutableStateOf(1) }
    var actual by remember(banco.id) { mutableStateOf(cola.take(RONDA).also { repeat(it.size) { cola.pollFirst() } }) }
    var terminado by remember(banco.id) { mutableStateOf(false) }
    var segundosTotal by remember(banco.id) { mutableStateOf(0) }
    var volvieron by remember(banco.id) { mutableStateOf(0) }
    var ultima by remember(banco.id) { mutableStateOf<Mazo.Marca?>(null) }
    var mejor by remember(banco.id) { mutableStateOf<Mazo.Marca?>(mazo.marcas(banco.id).minByOrNull { it.porPareja }) }
    var rondaHecha by remember(banco.id) { mutableStateOf(false) }
    // Cambia en cada ronda y en cada "Otra vez": ParejasGame recuerda su estado por
    // lista, y dos rondas con las mismas parejas nacerían ya "terminadas".
    var vuelta by remember(banco.id) { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar("⏱ ${banco.title}  ·  ronda $ronda", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            if (!terminado) {
                val marcaAntes = mejor
                Text(
                    "${actual.size} parejas · quedan ${cola.size} en la cola" +
                        (if (marcaAntes != null) " · tu marca: ${"%.1f".format(Locale("es"), marcaAntes.porPareja)} s por pareja" else ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSoft
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.runtime.key(vuelta) {
                ParejasGame(parejas = actual, accent = accent, relojGrande = true) { _, segundos, fallidas ->
                    segundosTotal += segundos
                    ultima = Mazo.Marca(mazo.hoy(), segundos, actual.size)
                    mejor = mazo.registrarMarca(banco.id, segundos, actual.size)
                    // Lo fallado vuelve: al final de la cola, para otra ronda.
                    val deVuelta = fallidas.mapNotNull { actual.getOrNull(it) }
                    volvieron += deVuelta.size
                    deVuelta.forEach { cola.addLast(it) }
                    rondaHecha = true
                }
                }
                if (rondaHecha) {
                    val u = ultima
                    val m = mejor
                    Spacer(Modifier.height(10.dp))
                    if (u != null && u.parejas < Mazo.MIN_PAREJAS_MARCA) {
                        Text(
                            "Ronda corta (${u.parejas} parejas): no cuenta para la marca.",
                            style = MaterialTheme.typography.bodyLarge, color = InkSoft
                        )
                    } else if (u != null && m != null) {
                        Text(
                            "Esta ronda: ${"%.1f".format(Locale("es"), u.porPareja)} s por pareja" +
                                (if (u.porPareja <= m.porPareja) " · ¡tu mejor marca!" else " · tu marca: ${"%.1f".format(Locale("es"), m.porPareja)}"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (u.porPareja <= m.porPareja) GoodGreen else InkSoft
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    BigButton(if (cola.isEmpty()) "Ver el resumen" else "Siguiente ronda", container = accent) {
                        if (cola.isEmpty()) {
                            terminado = true
                        } else {
                            actual = cola.take(RONDA).also { repeat(it.size) { cola.pollFirst() } }
                            ronda += 1
                            vuelta += 1
                            rondaHecha = false
                        }
                    }
                }
            } else {
                val curva = mazo.marcas(banco.id).takeLast(6)
                Text("¡Listo!", style = MaterialTheme.typography.headlineMedium, color = GoodGreen)
                Spacer(Modifier.height(8.dp))
                Text(
                    (if (ronda == 1) "1 ronda" else "$ronda rondas") + " · ${banco.pares.size} parejas · $segundosTotal segundos en total" +
                        (if (volvieron > 0) " · $volvieron parejas volvieron hasta salir" else " · ninguna volvió"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink
                )
                Spacer(Modifier.height(8.dp))
                mejor?.let {
                    Text(
                        "Tu marca en este banco: ${"%.1f".format(Locale("es"), it.porPareja)} s por pareja (${it.fecha}).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSoft
                    )
                }
                if (curva.size > 1) {
                    Text(
                        "Tus últimas rondas: " + curva.joinToString(" · ") { "%.1f".format(Locale("es"), it.porPareja) } + " s por pareja",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSoft
                    )
                }
                Spacer(Modifier.height(16.dp))
                BigButton("Otra vez", container = accent) {
                    cola.clear(); cola.addAll(banco.pares.shuffled())
                    actual = cola.take(RONDA).also { repeat(it.size) { cola.pollFirst() } }
                    ronda = 1; vuelta += 1; terminado = false; segundosTotal = 0; volvieron = 0; rondaHecha = false; ultima = null
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Elegir otro banco",
                    style = MaterialTheme.typography.labelLarge,
                    color = InkSoft,
                    modifier = Modifier.clickable { onBack() }.padding(8.dp)
                )
            }
        }
    }
}

private const val RONDA = 8
const val BANCO_LECCIONES = "lecciones"
