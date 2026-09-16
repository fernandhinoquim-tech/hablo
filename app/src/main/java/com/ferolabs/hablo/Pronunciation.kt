package com.ferolabs.hablo

import kotlin.math.max

enum class WordScore { BIEN, DUDOSO, MAL }

data class ScoredWord(val word: String, val score: WordScore)

data class PronunciationResult(
    val words: List<ScoredWord>,
    val percent: Int,
    val heard: String
) {
    /**
     * ¿Se entendió lo suficiente para dar el ejercicio por dicho? 60 % o más,
     * o bien una sola palabra fallada con el resto al menos dudoso (50 %).
     * Medido el 2026-09-15 sobre 200 grabaciones: el dictado cambia UNA
     * palabra bien dicha ("I don't understand." → "I then understood") y en una
     * frase de tres palabras eso bastaba para reprobar; de 26 reprobadas, 14
     * las oía perfectas otro reconocedor. Dos palabras falladas sí reprueban.
     * El sonido lo juzga aparte el modelo de fonemas; esto es solo "¿se entendió?".
     */
    val entendida: Boolean
        get() = percent >= 60 || (percent >= 50 && words.count { it.score == WordScore.MAL } <= 1)
}

// Los ejercicios de pronunciación (Drill) viven en assets/content/drills.json
// y los carga Course, igual que las lecciones: el contenido va en JSON, no en
// Kotlin, y cada uno lleva su etiqueta "sound" obligatoria.

// ---------------------------------------------------------------------------

/**
 * Fichas para comparar: números en letras y guion como parte de la palabra
 * ("twenty-five"). Sin esto, "25" del dictado contra "twentyfive" salía en
 * rojo 17 de 17 veces (diagnóstico del 2026-09-15).
 */
private fun words(text: String): List<String> =
    Correccion.fichas(text).split(" ").filter { it.isNotBlank() }

/** Las palabras tal como están escritas en el ejercicio, para mostrarlas en el chip. */
private fun originales(text: String): List<String> =
    text.split(Regex("\\s+"))
        .map { it.trim { c -> !c.isLetterOrDigit() && c != '\'' && c != '-' } }
        .filter { it.isNotBlank() }

private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var prev = IntArray(b.length + 1) { it }
    var cur = IntArray(b.length + 1)
    for (i in 1..a.length) {
        cur[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
        }
        val t = prev; prev = cur; cur = t
    }
    return prev[b.length]
}

private fun similar(a: String, b: String): Boolean {
    val d = levenshtein(a, b)
    val len = max(a.length, b.length)
    if (len == 0) return true
    return (1.0 - d.toDouble() / len) >= 0.6
}

/**
 * Compara lo que se pidió decir con lo que se entendió.
 *
 * Usa la subsecuencia común más larga para no castigar el orden, y después
 * marca cada palabra que no calzó: si hay algo parecido en lo escuchado la
 * damos por dudosa, si no, por fallada.
 */
fun scorePronunciation(target: String, heard: String): PronunciationResult {
    val t = words(target)
    // "I am" cuenta como "I'm" si la frase dice "I'm" (y al revés).
    val h = words(Correccion.igualaContracciones(target, heard))

    if (t.isEmpty()) return PronunciationResult(emptyList(), 0, heard)
    if (h.isEmpty()) {
        return PronunciationResult(t.map { ScoredWord(it, WordScore.MAL) }, 0, heard)
    }

    // Subsecuencia común más larga entre lo pedido y lo escuchado.
    val dp = Array(t.size + 1) { IntArray(h.size + 1) }
    for (i in t.size - 1 downTo 0) {
        for (j in h.size - 1 downTo 0) {
            dp[i][j] = if (t[i] == h[j]) dp[i + 1][j + 1] + 1
            else max(dp[i + 1][j], dp[i][j + 1])
        }
    }

    val matched = BooleanArray(t.size)
    var i = 0
    var j = 0
    while (i < t.size && j < h.size) {
        when {
            t[i] == h[j] -> { matched[i] = true; i++; j++ }
            dp[i + 1][j] >= dp[i][j + 1] -> i++
            else -> j++
        }
    }

    // El chip muestra la palabra del ejercicio ("twenty-five"), no la ficha
    // normalizada ("twentyfive"), cuando cuadran una a una.
    val orig = originales(target).let { if (it.size == t.size) it else t }
    val scored = t.mapIndexed { idx, w ->
        val s = when {
            matched[idx] -> WordScore.BIEN
            h.any { similar(it, w) } -> WordScore.DUDOSO
            else -> WordScore.MAL
        }
        ScoredWord(orig[idx], s)
    }

    // Sin sumOf: con literales sueltos Kotlin no distingue la version Int de la Long.
    var puntos = 0
    for (sw in scored) {
        puntos += when (sw.score) {
            WordScore.BIEN -> 100
            WordScore.DUDOSO -> 50
            WordScore.MAL -> 0
        }
    }
    return PronunciationResult(scored, puntos / scored.size, heard)
}
