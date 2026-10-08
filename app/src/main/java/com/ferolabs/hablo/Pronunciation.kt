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

// --- Números dichos ----------------------------------------------------------
// El dictado (Moonshine) escribe lo que oye en cifras y con signos: "It's $12.50.",
// "13:30 15:50", "June 21st", "50,000", "2027". La frase pedida los trae en letras
// ("twelve dollars and fifty cents", "thirteen thirty", "twenty-first"), así que
// un número BIEN dicho salía como no entendido: a1u14l1e3 se quedaba en 16 % con las
// cuatro voces y no se podía pasar nunca (revisión del 08-10, medido con Piper →
// Moonshine). Cada ficha con cifras se cambia por la forma hablada que más se parece
// a la frase pedida (la cifra tal cual también es candidata: "May 3" sigue valiendo).

private val ORDINAL_IRREGULAR = mapOf(
    "one" to "first", "two" to "second", "three" to "third", "five" to "fifth",
    "eight" to "eighth", "nine" to "ninth", "twelve" to "twelfth"
)

/** El ordinal de un cardinal en letras: "twenty-one" → "twenty-first", "thirty" → "thirtieth". */
private fun ordinal(cardinal: String): String {
    val corte = maxOf(cardinal.lastIndexOf(' '), cardinal.lastIndexOf('-'))
    val cabeza = cardinal.substring(0, corte + 1)
    val ultima = cardinal.substring(corte + 1)
    val ord = ORDINAL_IRREGULAR[ultima] ?: if (ultima.endsWith("y")) ultima.dropLast(1) + "ieth" else ultima + "th"
    return cabeza + ord
}

/** Un entero en letras, con las formas que se oyen: "1920" → "one thousand nine hundred twenty" y "nineteen twenty". */
private fun cardinales(n: Long): List<String> {
    fun base(n: Long): String? = when {
        n <= 100 -> Correccion.enLetras(n.toInt())
        n < 1000 -> Correccion.enLetras((n / 100).toInt()) + " hundred" + (if (n % 100 == 0L) "" else " " + Correccion.enLetras((n % 100).toInt()))
        n < 1_000_000 -> base(n / 1000)?.let { it + " thousand" + (if (n % 1000 == 0L) "" else " " + base(n % 1000)) }
        n < 1_000_000_000 -> base(n / 1_000_000)?.let { it + " million" + (if (n % 1_000_000 == 0L) "" else " " + base(n % 1_000_000)) }
        else -> null
    }
    val out = ArrayList<String>()
    base(n)?.let { out.add(it); if (n in 101..999 && n % 100 != 0L) out.add(it.replace(" hundred ", " hundred and ")) }
    // Años y similares en dos mitades: "nineteen twenty", "twenty twenty-seven", "nineteen oh five".
    if (n in 1100..2099 && n % 1000 >= 100 || n in 2010..2099) {
        val alta = Correccion.enLetras((n / 100).toInt())
        val baja = (n % 100).toInt()
        if (alta != null) out.add(alta + " " + when { baja == 0 -> "hundred"; baja < 10 -> "oh " + Correccion.enLetras(baja); else -> Correccion.enLetras(baja) })
    }
    if (n in 2000..2009) out.add("two thousand" + if (n == 2000L) "" else " " + Correccion.enLetras((n % 100).toInt()))
    return out
}

/** Las formas habladas de una ficha con cifras, tal como la escribe el dictado. */
private fun formasHabladas(ficha: String): List<String> {
    val limpia = ficha.trim { !it.isLetterOrDigit() && it != '$' && it != '%' }
    val out = ArrayList<String>()
    Regex("^\\$(\\d[\\d,]*)(?:\\.(\\d{2}))?$").matchEntire(limpia)?.let { m ->
        val d = m.groupValues[1].replace(",", "").toLongOrNull() ?: return@let
        val c = m.groupValues[2].toIntOrNull()
        for (dd in cardinales(d)) {
            val dolares = dd + if (d == 1L) " dollar" else " dollars"
            if (c == null || c == 0) out.add(dolares)
            if (c != null && c > 0) {
                val cc = Correccion.enLetras(c) ?: continue
                out.add("$dolares and $cc " + if (c == 1) "cent" else "cents")
                out.add("$dolares $cc")
                out.add("$dd $cc")
            }
        }
        if (d == 0L && c != null && c > 0) Correccion.enLetras(c)?.let { out.add("$it cents") }
    }
    Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(limpia)?.let { m ->
        val h = Correccion.enLetras(m.groupValues[1].toInt()) ?: return@let
        val min = m.groupValues[2].toInt()
        when {
            min == 0 -> { out.add("$h o'clock"); out.add(h) }
            min < 10 -> out.add("$h oh " + Correccion.enLetras(min))
            else -> out.add("$h " + Correccion.enLetras(min))
        }
    }
    Regex("^(\\d[\\d,]*)(st|nd|rd|th)$").matchEntire(limpia)?.let { m ->
        m.groupValues[1].replace(",", "").toLongOrNull()?.let { n -> cardinales(n).take(1).forEach { out.add(ordinal(it)) } }
    }
    Regex("^(\\d[\\d,]*)%$").matchEntire(limpia)?.let { m ->
        m.groupValues[1].replace(",", "").toLongOrNull()?.let { n -> cardinales(n).forEach { out.add("$it percent") } }
    }
    Regex("^\\d{1,3}(,\\d{3})+$|^\\d{3,}$").matchEntire(limpia)?.let {
        limpia.replace(",", "").toLongOrNull()?.let { n -> out.addAll(cardinales(n)) }
    }
    return out
}

/** Una frase con sus cifras especiales en su forma hablada principal ("It's 12:50." → "It's twelve fifty."). */
internal fun hablado(texto: String): String {
    if (texto.none { it.isDigit() }) return texto
    return texto.split(Regex("\\s+")).joinToString(" ") { ficha ->
        if (ficha.none { it.isDigit() }) ficha else formasHabladas(ficha).firstOrNull() ?: ficha
    }
}

/**
 * Cambia cada ficha con cifras de lo oído por una forma hablada cuyas palabras estén
 * TODAS en la frase pedida (la más larga). Si ninguna cabe entera, se deja la cifra: un
 * "925" oído por "I'm twenty-five" no se convierte en "nine hundred twenty-five" para
 * regalar el "twenty-five" (criterio de Fero: mejor castigar de más que dar por bueno).
 */
internal fun numerosDichos(target: String, heard: String): String {
    if (heard.none { it.isDigit() }) return heard
    val objetivo = words(target).toSet()
    return heard.split(Regex("\\s+")).joinToString(" ") { ficha ->
        if (ficha.none { it.isDigit() }) return@joinToString ficha
        formasHabladas(ficha).filter { c -> words(c).let { w -> w.isNotEmpty() && w.all { it in objetivo } } }
            .maxByOrNull { words(it).size } ?: ficha
    }
}

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
    // La frase pedida también puede traer cifras (el "decir" del mazo: "It's 12:50."): se
    // compara en su forma hablada, y lo oído contra esa forma.
    val pedido = hablado(target)
    val t = words(pedido)
    // "I am" cuenta como "I'm" si la frase dice "I'm" (y al revés); y los números que el
    // dictado escribe en cifras ("$12.50", "13:30", "21st") se leen como se dicen.
    // Primero los números (igualaContracciones ya quita "$", ":" y los puntos).
    val h = words(Correccion.igualaContracciones(target, numerosDichos(pedido, heard)))

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
