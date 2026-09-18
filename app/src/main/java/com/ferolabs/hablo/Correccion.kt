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
        "let's" to "let us",
        // B1 (17-09): los modales del pasado y los negativos de have que faltaban.
        "must've" to "must have", "should've" to "should have", "would've" to "would have",
        "could've" to "could have", "might've" to "might have",
        "hadn't" to "had not", "hasn't" to "has not", "haven't" to "have not"
    )

    /**
     * Contracciones AMBIGUAS que solo se igualan con sujeto delante: 's = is o
     * has y 'd = would o had únicamente tras pronombre, wh- o there/here/that,
     * nunca tras un nombre ("my brother's car" no se toca). Solo valen para
     * ACEPTAR una respuesta; el diagnóstico y el chequeo de duplicados usan
     * [sueltaEstricta]. Hallazgo de la revisión del 2026-09-16: el mazo pedía
     * "What's your name?" en "escribir" y marcaba mal "What is your name?" (24
     * frases del curso).
     * **'d = would Y had (B1, 17-09):** en el tercer condicional y el past
     * perfect "I'd" es "I had" ("If I'd known", "I'd already left"). **'s = is Y
     * has (18-09):** con el present perfect y "have got" de B1, "She's got a
     * sister" / "He's been here" son has, y con la lectura fija de is "She has
     * got a sister" se comparaba contra "she is got" y fallaba. [variantes]
     * prueba las dos expansiones POR OCURRENCIA ("If I'd known, I'd have left" =
     * had + would; "It's late and she's gone" = is + has) y [acepta] da por
     * buena la respuesta si alguna coincide. Lo que sigue fijo: "'d have" solo
     * es would y "'s been / 's got" solo es has, así "I had have left" y "she is
     * got" no cuelan.
     */
    private val SUJETOS = listOf("i", "you", "he", "she", "it", "we", "they", "what", "who", "where", "when", "how", "why", "that", "there", "here")
    private val CON_S = SUJETOS.filter { it != "i" }.map { "$it's" }
    private val CON_D = SUJETOS.map { "$it'd" }
    private const val MAX_BIF = 4   // 2^4 variantes como mucho; más 's/'d ambiguas en una frase no existe en el curso
    /** Tras 's, estas palabras solo caben con has. */
    private val SOLO_HAS = setOf("been", "got", "gotten")

    private val UNIDADES = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"
    )
    private val DECENAS = mapOf(
        20 to "twenty", 30 to "thirty", 40 to "forty", 50 to "fifty",
        60 to "sixty", 70 to "seventy", 80 to "eighty", 90 to "ninety"
    )

    /** 0..100 en letras, como se escribe en inglés ("twenty-five", "one hundred"); null fuera de rango. */
    fun enLetras(n: Int): String? = when {
        n < 0 || n > 100 -> null
        n < 20 -> UNIDADES[n]
        n == 100 -> "one hundred"
        n % 10 == 0 -> DECENAS[n]
        else -> DECENAS[n / 10 * 10] + "-" + UNIDADES[n % 10]
    }

    /**
     * Números a una sola forma: "8" = "eight", "25" = "twenty five" = "twenty-five",
     * "a hundred" = "one hundred". Las cifras se pasan a letras porque así está
     * escrito el curso y así se lee mejor la corrección («eight», no «8»).
     * El dictado (Moonshine) escribe siempre cifras: sin esto, "twenty-five"
     * bien dicho salía en rojo 17 de 17 veces (diagnóstico del 2026-09-15).
     */
    /** Un número pegado a un mes es una fecha ("May 3") y se queda en cifras: "May three" es justo el error que enseña a1u12l2. */
    private val MESES = setOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")

    private fun numeros(tokens: List<String>): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.all { it.isDigit() } && (i == 0 || tokens[i - 1] !in MESES)) {
                val letras = t.toIntOrNull()?.let { enLetras(it) }
                if (letras != null) { out += letras.split(" "); i++; continue }
            }
            if (DECENAS.containsValue(t) && i + 1 < tokens.size && UNIDADES.indexOf(tokens[i + 1]) in 1..9) {
                out += t + "-" + tokens[i + 1]; i += 2; continue
            }
            if (t == "a" && i + 1 < tokens.size && tokens[i + 1] == "hundred") { out += "one"; i++; continue }
            out += t; i++
        }
        return out
    }

    /**
     * Como [normalizeAnswer], y además el guion cuenta como espacio y los
     * números van en letras ("8" = "eight", "25" = "twenty-five"), salvo el
     * que sigue a un mes ("May 3" se queda; ver [MESES]). Las contracciones
     * se dejan como están.
     */
    fun fichas(text: String): String =
        numeros(normalizeAnswer(text.replace('-', ' ').replace('\u2013', ' ')).split(" ").filter { it.isNotEmpty() })
            .joinToString(" ")

    /** Como [fichas], y además expande las contracciones seguras ("I'm" = "I am"). Para diagnósticos y duplicados. */
    fun sueltaEstricta(text: String): String {
        var t = " " + fichas(text) + " "
        for ((corta, larga) in CONTRACCIONES) t = t.replace(" $corta ", " $larga ")
        return t.trim()
    }

    /** La lectura por defecto de una ficha ambigua: 's → is (o has delante de been/got), 'd → would. */
    private fun lecturaFija(ficha: String, siguiente: String?): String = when {
        ficha in CON_S -> ficha.dropLast(2) + (if (siguiente in SOLO_HAS) " has" else " is")
        ficha in CON_D -> ficha.dropLast(2) + " would"
        else -> ficha
    }

    /** Como [sueltaEstricta], y además la lectura por defecto de 's (is) y 'd (would) tras sujeto. Para ACEPTAR, ver [variantes]. */
    fun suelta(text: String): String {
        val fichas = sueltaEstricta(text).split(" ").filter { it.isNotEmpty() }
        return fichas.indices.joinToString(" ") { lecturaFija(fichas[it], fichas.getOrNull(it + 1)) }
    }

    /**
     * Todas las lecturas de un texto: [suelta] y, si lleva 's o 'd tras sujeto,
     * cada combinación is/has y would/had por ocurrencia (hasta [MAX_BIF]
     * bifurcaciones). Para comparar dos textos se mira si sus conjuntos se cruzan.
     */
    fun variantes(text: String): Set<String> {
        val fichas = sueltaEstricta(text).split(" ").filter { it.isNotEmpty() }
        // Bifurcan: 'd salvo delante de "have" (solo would) y 's salvo delante de been/got (solo has).
        val posiciones = fichas.indices.filter {
            (fichas[it] in CON_D && fichas.getOrNull(it + 1) != "have") ||
                (fichas[it] in CON_S && fichas.getOrNull(it + 1) !in SOLO_HAS)
        }.take(MAX_BIF)
        if (posiciones.isEmpty()) return setOf(suelta(text))
        val out = LinkedHashSet<String>()
        for (mascara in 0 until (1 shl posiciones.size)) {
            val f = fichas.toMutableList()
            posiciones.forEachIndexed { k, pos ->
                val sujeto = fichas[pos].dropLast(2)
                val segunda = mascara and (1 shl k) != 0
                f[pos] = sujeto + " " + if (fichas[pos] in CON_D) (if (segunda) "had" else "would") else (if (segunda) "has" else "is")
            }
            // lo que quedó sin elegir (delante de have/been/got, o más de MAX_BIF) toma su lectura fija
            out.add(f.indices.joinToString(" ") { lecturaFija(f[it], f.getOrNull(it + 1)) })
        }
        return out
    }

    /** ¿Dos textos son la misma respuesta, con 'd leído como would o had donde haga falta? */
    fun mismaRespuesta(a: String, b: String): Boolean {
        val va = variantes(a)
        if (va.size == 1) return va.first() in variantes(b)
        return variantes(b).any { it in va }
    }

    /**
     * Deja las contracciones de [text] como están en [modelo]: si el modelo
     * dice "I'm", "i am" pasa a "i'm"; si dice "I am", "i'm" pasa a "i am".
     * Para puntuar el habla palabra por palabra, donde "I am" y "I'm" tienen
     * que ser las mismas fichas o la frase de tres palabras pierde un tercio
     * por decirla en forma larga (20260913-011257: "I am 25 years old." → 50 %).
     * No se cierran contracciones a ciegas: "I have the check" no es "I've".
     */
    fun igualaContracciones(modelo: String, text: String): String {
        val m = " " + fichas(modelo) + " "
        var t = " " + fichas(text) + " "
        for ((corta, larga) in CONTRACCIONES) {
            if (m.contains(" $corta ")) t = t.replace(" $larga ", " $corta ")
            else if (m.contains(" $larga ")) t = t.replace(" $corta ", " $larga ")
        }
        // 's y 'd: si el modelo las lleva, cualquiera de sus dos lecturas se cierra a la contracción;
        // si el modelo va largo, la contracción se abre a la lectura que use el modelo.
        for (corta in CON_S + CON_D) {
            val sujeto = corta.dropLast(2)
            val largas = if (corta in CON_S) listOf("$sujeto is", "$sujeto has") else listOf("$sujeto would", "$sujeto had")
            if (m.contains(" $corta ")) for (l in largas) t = t.replace(" $l ", " $corta ")
            else largas.firstOrNull { m.contains(" $it ") }?.let { l -> t = t.replace(" $corta ", " $l ") }
        }
        return t.trim()
    }

    /**
     * Normalización ESTRICTA para el dictado de números (Cowork, 17-09): [suelta]
     * quita `.`, `:`, `$` y `@`, y por eso daba por buenas «$650» por «$6.50»,
     * «$1200» por «$12», «14:50» por «1450» y «wademail.com» por «wade@mail.com»;
     * en el dictado un falso «bien» es justo lo que enseña mal. Aquí se conserva
     * todo signo que va DENTRO de una ficha (6.50, 7:30, 555-0142, wade@mail.com,
     * 5th); solo se quitan los de los bordes (la coma, el punto final) y el `$`
     * inicial («6.50» es tan correcto como «$6.50»: lo que se mide es el oído).
     * Lo demás como [fichas]: minúsculas, sin tildes, espacios, números 0-100
     * en letras (así «13» = «thirteen»), el número tras un mes en cifras, y el
     * cero inicial de la hora («07:30» = «7:30»). «6.50» y «6:50» siguen
     * siendo distintos entre sí y de «650», y «May three» sigue sin ser «May 3».
     */
    fun dictado(text: String): String {
        val base = java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace("\u2019", "'")
        val fichas = base.split(Regex("\\s+")).filter { it.isNotEmpty() }.mapNotNull { cruda ->
            var t = cruda.trim { !it.isLetterOrDigit() && it != '$' }
            t = t.removePrefix("$")
            t = t.trim { !it.isLetterOrDigit() }
            // el cero inicial de la hora: 07:30 = 7:30
            Regex("^0(\\d:\\d\\d)$").matchEntire(t)?.let { t = it.groupValues[1] }
            t.ifEmpty { null }
        }
        return numeros(fichas).joinToString(" ")
    }

    /** ¿Vale la respuesta del dictado? Con la regla estricta de [dictado]. */
    fun aceptaDictado(given: String, answer: String, accept: List<String>): Boolean {
        val g = dictado(given)
        if (g.isBlank()) return false
        return g == dictado(answer) || accept.any { g == dictado(it) }
    }

    /** ¿La respuesta dada vale? Contra la esperada o cualquiera de las alternativas (hasta 143 en B1: se calcula una vez la del alumno). */
    fun acepta(given: String, answer: String, accept: List<String>): Boolean {
        val vg = variantes(given)
        if (vg.first().isBlank()) return false
        fun cruza(x: String): Boolean { val vx = variantes(x); return vg.any { it in vx } }
        return cruza(answer) || accept.any { cruza(it) }
    }

    /**
     * Completar el hueco: vale la palabra del hueco sola O la frase completa con
     * el hueco lleno (con la respuesta o con cualquier alternativa). La pantalla
     * muestra la frase completa como "la correcta", así que castigar por
     * escribirla entera era indefendible (a1u4l1e9, dos veces, 2026-09-15).
     */
    fun aceptaHueco(given: String, before: String, after: String, answer: String, accept: List<String>): Boolean {
        if (acepta(given, answer, accept)) return true
        val huecos = listOf(answer) + accept
        // La frase completa, o el hueco con lo que le sigue, o con lo que le precede.
        val frases = huecos.map { before + it + after } + huecos.map { it + after } + huecos.map { before + it }
        return acepta(given, frases[0], frases.drop(1))
    }

    /** ¿Escribió la frase entera (correcta) en vez de solo el hueco? Para avisárselo sin castigar. */
    fun escribioLaFrase(given: String, before: String, after: String, answer: String, accept: List<String>): Boolean =
        !acepta(given, answer, accept) && aceptaHueco(given, before, after, answer, accept)

    /**
     * Diagnóstico del hueco: si escribió solo el hueco, se compara con la
     * palabra; si escribió la frase (o un trozo), con la frase completa, y si
     * ni así hay algo concreto que decir, se le recuerda qué se pedía.
     */
    fun diagnosticoHueco(given: String, before: String, after: String, answer: String, accept: List<String>): String? {
        val g = suelta(given).split(" ").filter { it.isNotEmpty() }
        if (g.isEmpty()) return null
        val nHueco = suelta(answer).split(" ").count { it.isNotEmpty() }
        if (g.size <= nHueco + 1) return diagnostico(given, answer, accept)
        val frases = (listOf(answer) + accept).map { before + it + after }
        return diagnostico(given, frases[0], frases.drop(1))
            ?: "Solo hacía falta la palabra del hueco: «$answer»."
    }

    /**
     * Explica en español qué falló, comparando con la respuesta correcta más
     * parecida. Devuelve null si no hay nada concreto que decir (la pantalla
     * muestra entonces solo la respuesta correcta).
     */
    fun diagnostico(given: String, answer: String, accept: List<String> = emptyList()): String? {
        val g = sueltaEstricta(given).split(" ").filter { it.isNotEmpty() }
        if (g.isEmpty()) return null
        val candidatas = listOf(answer) + accept
        val e = candidatas.map { sueltaEstricta(it).split(" ").filter { w -> w.isNotEmpty() } }
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
