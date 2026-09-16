package com.ferolabs.hablo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * El juego de la lección: **Parejas**. A mitad de la lección, sus propias
 * frases —español a un lado, inglés al otro, barajadas— y hay que unirlas
 * contra el reloj. Fero (2026-09-14): "agrega algún juego en las lecciones
 * para que no sea repetitivo".
 *
 * Es repaso de lo que la misma lección acaba de enseñar (reconocimiento, no
 * producción: por eso no cuenta para la nota). Sin contenido nuevo: las
 * parejas salen de los ejercicios que traen español e inglés.
 */
data class Pareja(val es: String, val en: String)

/** Hasta [max] parejas distintas sacadas de los ejercicios de la lección. */
fun parejasDe(lesson: Lesson, max: Int = 5): List<Pareja> {
    val out = LinkedHashMap<String, Pareja>()
    for (ex in lesson.exercises) {
        val p = when (ex) {
            is Exercise.TranslateChoose -> Pareja(ex.es, ex.answer)
            is Exercise.WriteIt -> Pareja(ex.es, ex.answer)
            is Exercise.BuildSentence -> Pareja(ex.es, ex.answer)
            is Exercise.Cloze -> Pareja(ex.es, ex.full)
            is Exercise.TypeWhatYouHear -> Pareja(ex.meaningEs, ex.audio)
            else -> null
        } ?: continue
        if (p.es.isBlank() || p.en.isBlank()) continue
        // dos parejas con el mismo inglés (o el mismo español) serían ambiguas
        if (out.values.any { it.en == p.en || it.es == p.es }) continue
        out[p.en] = p
        if (out.size >= max) break
    }
    return out.values.toList()
}

@Composable
fun ParejasGame(
    parejas: List<Pareja>,
    accent: Color,
    /** Contrarreloj: el reloj grande. Es velocímetro, no juez: nunca reprueba por lento. */
    relojGrande: Boolean = false,
    /** Al terminar: intentos, segundos y qué parejas tuvieron algún fallo (para que vuelvan). */
    onDone: (intentos: Int, segundos: Int, fallidas: Set<Int>) -> Unit
) {
    val izquierda = remember(parejas) { parejas.indices.shuffled() }
    val derecha = remember(parejas) { parejas.indices.shuffled() }
    var selEs by remember(parejas) { mutableStateOf(-1) }
    var selEn by remember(parejas) { mutableStateOf(-1) }
    var hechas by remember(parejas) { mutableStateOf(setOf<Int>()) }
    var intentos by remember(parejas) { mutableStateOf(0) }
    var fallo by remember(parejas) { mutableStateOf<Pair<Int, Int>?>(null) }
    var fallidas by remember(parejas) { mutableStateOf(setOf<Int>()) }
    var segundos by remember(parejas) { mutableStateOf(0) }
    var terminado by remember(parejas) { mutableStateOf(false) }

    // Reloj: un segundo cada segundo mientras no se haya terminado.
    LaunchedEffect(parejas, terminado) {
        while (!terminado) {
            delay(1000)
            segundos++
        }
    }
    // Un fallo se ve en rojo medio segundo y se suelta.
    LaunchedEffect(fallo) {
        if (fallo != null) {
            delay(500)
            fallo = null
            selEs = -1
            selEn = -1
        }
    }

    fun intentar() {
        if (selEs < 0 || selEn < 0 || fallo != null) return
        intentos++
        if (selEs == selEn) {
            hechas = hechas + selEs
            selEs = -1
            selEn = -1
            if (hechas.size == parejas.size && !terminado) {
                terminado = true
                onDone(intentos, segundos, fallidas)
            }
        } else {
            fallo = selEs to selEn
            fallidas = fallidas + selEs + selEn
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("🎯 Parejas", style = MaterialTheme.typography.titleLarge)
        Text(
            "Une cada frase con su traducción. Toca una de cada lado.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkSoft
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                "⏱ ${segundos}s",
                style = if (relojGrande) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.labelLarge,
                color = accent,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${hechas.size} de ${parejas.size} · $intentos intentos",
                style = MaterialTheme.typography.labelLarge,
                color = InkSoft
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                izquierda.forEach { i ->
                    Carta(
                        text = parejas[i].es,
                        hecha = i in hechas,
                        elegida = selEs == i,
                        fallida = fallo?.first == i,
                        accent = accent
                    ) {
                        if (i !in hechas && fallo == null) {
                            selEs = if (selEs == i) -1 else i
                            intentar()
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                derecha.forEach { i ->
                    Carta(
                        text = parejas[i].en,
                        hecha = i in hechas,
                        elegida = selEn == i,
                        fallida = fallo?.second == i,
                        accent = accent
                    ) {
                        if (i !in hechas && fallo == null) {
                            selEn = if (selEn == i) -1 else i
                            intentar()
                        }
                    }
                }
            }
        }

        if (terminado) {
            Spacer(Modifier.height(4.dp))
            Text(
                "¡Listo! ${parejas.size} parejas en $intentos intentos y $segundos segundos." +
                    if (intentos == parejas.size) " Sin fallar." else "",
                style = MaterialTheme.typography.titleMedium,
                color = GoodGreen
            )
        }
    }
}

@Composable
private fun Carta(
    text: String,
    hecha: Boolean,
    elegida: Boolean,
    fallida: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val bg = when {
        hecha -> GoodGreenSoft
        fallida -> BadRedSoft
        elegida -> accent.copy(alpha = 0.12f)
        else -> Color.White
    }
    val border = when {
        hecha -> GoodGreen.copy(alpha = 0.5f)
        fallida -> BadRed
        elegida -> accent
        else -> Line
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (hecha) GoodGreen else Ink,
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .border(if (elegida || fallida) 2.dp else 1.dp, border, RoundedCornerShape(12.dp))
            .clickable(enabled = !hecha, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    )
}
