package com.ferolabs.hablo

/**
 * Corrección de respuestas ESCRITAS (write y cloze): no solo "mal", sino qué
 * falló. Recuperación con corrección rinde g = 0,73; sin ella, 0,39 (Rowland
 * 2014): la explicación es la mitad del efecto. Kotlin puro, con tests.
 */
object Correccion {

    /**
     * Contracciones que se aceptan como iguales a su forma larga. Solo las que
     * no son ambiguas: "'s" puede ser is/has/posesivo y "'d" would/had, así
     * que esas no se tocan.
     */
    private val CONTRACCIONES = listOf(
        "i'm" to "i am", "you're" to "you are", "we're" to "we are", "they're" to "they are",
        "isn't" to "is not", "aren't" to "are not", "wasn't" to "was not", "weren't" to "were not",
        "don't" to "do not", "doesn't" to "does not", "didn't" to "did not",
        "can't" to "can not", "cannot" to "can not", "couldn't" to "could not",
        "won't" to "will not", "wouldn't" to "would not", "shouldn't" to "should not",
        "i'll" to "i will", "you'll" to "you will", "he'll" to "he will", "she'll" to "she will",
        "it'll" to "it will", "we'll" to "we will", "they'll" to "they will",
        "i've" to "i have", "you've" to "you have", "we've" to "we have", "they've" to "they have",
        "let's" to "let us"
    )

    /** Como [normalizeAnswer], y además expande contracciones: "I'm" = "I am". */
    fun suelta(text: String): String {
        var t = " " + normalizeAnswer(text) + " "
        for ((corta, larga) in CONTRACCIONES) t = t.replace(" $corta ", " $larga ")
        return t.trim()
    }

    /** ¿La respuesta dada vale? Contra la esperada o cualquiera de las alternativas. */
    fun acepta(given: String, answer: String, accept: List<String>): Boolean {
        val g = suelta(given)
        if (g.isBlank()) return false
        return g == suelta(answer) || accept.any { g == suelta(it) }
    }

    /**
     * Explica en español qué falló, comparando con la respuesta correcta más
     * parecida. Devuelve null si no hay nada concreto que decir (la pantalla
     * muestra entonces solo la respuesta correcta).
     */
    fun diagnostico(given: String, answer: String, accept: List<String> = emptyList()): String? {
        val g = suelta(given).split(" ").filter { it.isNotEmpty() }
        if (g.isEmpty()) return null
        val candidatas = listOf(answer) + accept
        val e = candidatas.map { suelta(it).split(" ").filter { w -> w.isNotEmpty() } }
            .minByOrNull { distancia(g, it) } ?: return null
        if (g == e) return null

        // Mismas palabras en otro orden.
        if (g.size == e.size && g.sorted() == e.sorted()) {
            return "Las palabras están todas, pero en otro orden."
        }

        if (g.size == e.size) {
            val diff = g.indices.filter { g[it] != e[it] }
            if (diff.size == 1) return unaPalabra(g[diff[0]], e[diff[0]])
            if (diff.size == 2) {
                return "Dos cosas: " + unaPalabra(g[diff[0]], e[diff[0]]).lowercaseFirst() +
                    " Y " + unaPalabra(g[diff[1]], e[diff[1]]).lowercaseFirst()
            }
            return null
        }

        if (g.size == e.size - 1) {
            val falta = faltante(g, e)
            if (falta != null) return "Te faltó la palabra «$falta»."
        }
        if (g.size == e.size + 1) {
            val sobra = faltante(e, g)
            if (sobra != null) return "Sobra la palabra «$sobra»."
        }
        return null
    }

    /** Qué pasó entre la palabra que puso y la que era. */
    private fun unaPalabra(dada: String, correcta: String): String {
        if (correcta.startsWith(dada) && correcta.length - dada.length <= 3) {
            val cola = correcta.removePrefix(dada)
            return "Te faltó la terminación «-$cola»: es «$correcta», no «$dada»."
        }
        if (dada.startsWith(correcta) && dada.length - correcta.length <= 3) {
            val cola = dada.removePrefix(correcta)
            return "Sobra la terminación «-$cola»: es «$correcta», no «$dada»."
        }
        if (dada.endsWith(correcta) && dada.length - correcta.length <= 2) {
            return "Sobra «${dada.removeSuffix(correcta)}» al principio: es «$correcta», no «$dada»."
        }
        if (letras(dada, correcta) <= 2) {
            return "Casi: es «$correcta», no «$dada»."
        }
        return "«$dada» debía ser «$correcta»."
    }

    /** La palabra de [larga] que no está en [corta], por alineación de izquierda a derecha. */
    private fun faltante(corta: List<String>, larga: List<String>): String? {
        var i = 0
        while (i < corta.size && corta[i] == larga[i]) i++
        // el resto tiene que coincidir desplazado en uno
        for (k in i until corta.size) if (corta[k] != larga[k + 1]) return null
        return larga[i]
    }

    /** Distancia de edición entre listas de palabras. */
    private fun distancia(a: List<String>, b: List<String>): Int {
        val d = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in 0..a.size) d[i][0] = i
        for (j in 0..b.size) d[0][j] = j
        for (i in 1..a.size) for (j in 1..b.size) {
            val costo = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + costo)
        }
        return d[a.size][b.size]
    }

    /** Distancia de edición entre dos palabras, letra a letra. */
    private fun letras(a: String, b: String): Int =
        distancia(a.map { it.toString() }, b.map { it.toString() })

    private fun String.lowercaseFirst() = if (isEmpty()) this else this[0].lowercaseChar() + substring(1)
}
