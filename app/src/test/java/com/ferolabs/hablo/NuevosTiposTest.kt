package com.ferolabs.hablo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Los cuatro tipos de producción (2026-09-14): write, cloze, shadow y
 * minimalPair — su parseo, sus reglas y la corrección que dice QUÉ falló.
 */
class NuevosTiposTest {

    // ------------------------------------------------------------ parseo

    @Test
    fun `write bien formado carga con sus alternativas`() {
        val ex = uno("""{"id":"t1l1e1","type":"write","es":"Ella trabaja en un hospital.","answer":"She works in a hospital.","accept":["She works at a hospital."]}""")
        val w = ex as Exercise.WriteIt
        assertEquals("She works in a hospital.", w.answer)
        assertEquals(listOf("She works at a hospital."), w.accept)
    }

    @Test
    fun `write sin es o sin answer revienta`() {
        assertTrue(falla("""{"id":"t1l1e1","type":"write","answer":"Hi."}""").contains("es"))
        assertTrue(falla("""{"id":"t1l1e1","type":"write","es":"Hola."}""").contains("answer"))
    }

    @Test
    fun `accept que repite la respuesta revienta`() {
        val e = falla("""{"id":"t1l1e1","type":"write","es":"Hola.","answer":"I am fine.","accept":["I'm fine."]}""")
        assertTrue(e, e.contains("accept"))
    }

    @Test
    fun `cloze carga y arma la frase completa`() {
        val c = uno("""{"id":"t1l1e1","type":"cloze","text":"She ___ in a hospital.","answer":"works","es":"Ella trabaja en un hospital."}""") as Exercise.Cloze
        assertEquals("She ", c.before)
        assertEquals(" in a hospital.", c.after)
        assertEquals("She works in a hospital.", c.full)
    }

    @Test
    fun `cloze sin hueco o con dos huecos revienta`() {
        assertTrue(falla("""{"id":"t1l1e1","type":"cloze","text":"She works here.","answer":"works","es":"x"}""").contains("hueco"))
        assertTrue(falla("""{"id":"t1l1e1","type":"cloze","text":"___ works ___.","answer":"She","es":"x"}""").contains("hueco"))
    }

    @Test
    fun `shadow solo necesita el texto`() {
        val s = uno("""{"id":"t1l1e1","type":"shadow","text":"I would like a coffee, please."}""") as Exercise.Shadow
        assertEquals("I would like a coffee, please.", s.text)
        assertTrue(falla("""{"id":"t1l1e1","type":"shadow"}""").contains("text"))
    }

    @Test
    fun `minimalPair identifica una palabra entre varias`() {
        val m = uno("""{"id":"t1l1e1","type":"minimalPair","options":["ship","sheep"],"answer":"ship","sentence":"My ship is very big."}""") as Exercise.MinimalPair
        assertEquals(0, m.answerIndex)
        assertEquals("My ship is very big.", m.sentence)
    }

    @Test
    fun `minimalPair con frases en vez de palabras o con sentence ajena revienta`() {
        assertTrue(falla("""{"id":"t1l1e1","type":"minimalPair","options":["a ship","sheep"],"answer":"sheep"}""").contains("palabras sueltas"))
        assertTrue(falla("""{"id":"t1l1e1","type":"minimalPair","options":["ship","sheep"],"answer":"ship","sentence":"I like cheese."}""").contains("sentence"))
        assertTrue(falla("""{"id":"t1l1e1","type":"minimalPair","options":["ship","sheep"],"answer":"chip"}""").contains("opciones"))
    }

    // ------------------------------------------------------------ corrección

    @Test
    fun `acepta contracciones y alternativas`() {
        assertTrue(Correccion.acepta("i am fine", "I'm fine.", emptyList()))
        assertTrue(Correccion.acepta("She works at a hospital", "She works in a hospital.", listOf("She works at a hospital.")))
        assertFalse(Correccion.acepta("She work in a hospital", "She works in a hospital.", emptyList()))
        assertFalse(Correccion.acepta("", "Hi.", emptyList()))
    }

    @Test
    fun `dice que falto la -s de works`() {
        val d = Correccion.diagnostico("She work in a hospital", "She works in a hospital.")
        assertTrue(d, d!!.contains("-s") && d.contains("works"))
    }

    @Test
    fun `dice que falto una palabra`() {
        val d = Correccion.diagnostico("My sister is doctor", "My sister is a doctor.")
        assertEquals("Te faltó la palabra «a».", d)
    }

    @Test
    fun `dice que sobra una palabra`() {
        val d = Correccion.diagnostico("I am agree", "I agree.")
        assertEquals("Sobra la palabra «am».", d)
    }

    @Test
    fun `dice que una palabra debia ser otra`() {
        val d = Correccion.diagnostico("I have 25 years", "I am 25 years old.")
        // dos cambios (have→am, y falta old): no hay una sola cosa que decir, y no inventa
        assertTrue(d == null || d.contains("debía") || d.contains("Dos cosas"))
        val d2 = Correccion.diagnostico("She works on a hospital", "She works in a hospital.")
        // on/in están a una letra: vale el "casi" con las dos palabras nombradas
        assertTrue(d2, d2!!.contains("«in»") && d2.contains("«on»"))
    }

    @Test
    fun `el orden cambiado se dice como tal`() {
        val d = Correccion.diagnostico("Works she in a hospital", "She works in a hospital.")
        assertEquals("Las palabras están todas, pero en otro orden.", d)
    }

    @Test
    fun `sin nada concreto que decir devuelve null`() {
        assertNull(Correccion.diagnostico("She works in a hospital", "She works in a hospital."))
        assertNull(Correccion.diagnostico("banana", "She works in a hospital."))
    }

    @Test
    fun `compara contra la alternativa mas parecida`() {
        val d = Correccion.diagnostico("She work at a hospital", "She works in a hospital.", listOf("She works at a hospital."))
        assertTrue(d, d!!.contains("works"))
        assertFalse(d, d.contains("«in»"))
    }

    @Test
    fun `minimalPair con play sentence suena la frase y sin play la portadora`() {
        val conFrase = uno("""{"id":"t1l1e1","type":"minimalPair","options":["three","tree"],"answer":"three","sentence":"I have three books.","play":"sentence"}""") as Exercise.MinimalPair
        assertEquals("I have three books.", conFrase.spoken)
        val portadora = uno("""{"id":"t1l1e1","type":"minimalPair","options":["ship","sheep"],"answer":"ship","sentence":"The ship is big."}""") as Exercise.MinimalPair
        assertEquals("The word is ship.", portadora.spoken)
        assertTrue(falla("""{"id":"t1l1e1","type":"minimalPair","options":["three","tree"],"answer":"three","play":"sentence"}""").contains("play"))
        assertTrue(falla("""{"id":"t1l1e1","type":"minimalPair","options":["three","tree"],"answer":"three","sentence":"I have three books.","play":"word"}""").contains("play"))
    }

    // ------------------------------------------------------------ hueco y números (diagnóstico 2026-09-15)

    @Test
    fun `hueco acepta la palabra sola y la frase completa`() {
        val before = "She "; val after = " in a hospital."
        assertTrue(Correccion.aceptaHueco("works", before, after, "works", emptyList()))
        // Lo que escribió Fero dos veces el 15-09 y se marcó mal.
        assertTrue(Correccion.aceptaHueco("she works in a hospital ", before, after, "works", emptyList()))
        assertTrue(Correccion.aceptaHueco("She works in a hospital", before, after, "works", emptyList()))
        assertFalse(Correccion.aceptaHueco("she work in a hospital", before, after, "works", emptyList()))
        assertFalse(Correccion.aceptaHueco("work", before, after, "works", emptyList()))
        assertTrue(Correccion.escribioLaFrase("she works in a hospital", before, after, "works", emptyList()))
        assertFalse(Correccion.escribioLaFrase("works", before, after, "works", emptyList()))
    }

    @Test
    fun `hueco con alternativa vale tambien en la frase completa`() {
        assertTrue(Correccion.aceptaHueco("She is working in a hospital", "She ", " in a hospital.", "works", listOf("is working")))
    }

    @Test
    fun `hueco explica la palabra aunque escriba la frase entera`() {
        val d = Correccion.diagnosticoHueco("she work in a hospital", "She ", " in a hospital.", "works", emptyList())
        assertTrue(d, d!!.contains("works"))
        val d2 = Correccion.diagnosticoHueco("works in the hospital yes", "She ", " in a hospital.", "works", emptyList())
        assertTrue(d2, d2!!.contains("hueco"))
        val d3 = Correccion.diagnosticoHueco("work", "She ", " in a hospital.", "works", emptyList())
        assertTrue(d3, d3!!.contains("-s"))
    }

    @Test
    fun `numeros en cifras y en letras son lo mismo`() {
        assertEquals(Correccion.suelta("at eight"), Correccion.suelta("at 8"))
        assertEquals(Correccion.suelta("I'm twenty-five years old."), Correccion.suelta("I am 25 years old"))
        assertEquals(Correccion.suelta("twenty five"), Correccion.suelta("twenty-five"))
        assertEquals("one hundred", Correccion.suelta("a hundred"))
        assertEquals("one hundred", Correccion.suelta("100"))
        assertEquals("forty", Correccion.suelta("40"))
        assertTrue(Correccion.acepta("What were you doing at 8?", "What were you doing at eight?", emptyList()))
        assertTrue(Correccion.acepta("There are 4 people in my family", "There are four people in my family.", emptyList()))
        // Fuera de rango o con letras pegadas no se toca.
        assertEquals("room 101", Correccion.suelta("Room 101"))
        assertEquals("twenty-five", Correccion.enLetras(25))
        assertNull(Correccion.enLetras(101))
    }

    @Test
    fun `dictado con contraccion en forma larga vale`() {
        assertTrue(Correccion.acepta("She does not live here", "She doesn't live here.", emptyList()))
        val d = Correccion.diagnostico("he speaks espanish and english", "He speaks Spanish and English.")
        assertTrue(d, d!!.contains("espanish"))
    }

    // ------------------------------------------------------------ utilidades

    private fun curso(ejercicio: String) =
        """{"version":2,"levels":[{"id":"T","title":"Test","units":[{"id":"t1","title":"U","lessons":[
           {"id":"t1l1","title":"L","theory":{"title":"t","body":"b","trap":"x"},"exercises":[$ejercicio]}]}]}]}"""

    private fun uno(ejercicio: String): Exercise =
        Course.parseCurriculum(curso(ejercicio))[0].units[0].lessons[0].exercises[0]

    private fun falla(ejercicio: String): String {
        try {
            Course.parseCurriculum(curso(ejercicio))
        } catch (e: IllegalArgumentException) {
            return e.message ?: ""
        }
        fail("debía reventar y no lo hizo: $ejercicio")
        return ""
    }
}
