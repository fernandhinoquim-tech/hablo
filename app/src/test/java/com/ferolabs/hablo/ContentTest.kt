package com.ferolabs.hablo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Primeros tests del proyecto (2026-09-14). Corren en el PC con
 * `./gradlew test`, sin teléfono: Kotlin puro sobre el parser del contenido.
 * Lo que aquí se prueba es lo que, si falla en silencio, le enseña inglés
 * incorrecto a Fero: la respuesta correcta que no está entre las opciones, un
 * id repetido que rompe el repaso, una lección sin su ficha de teoría.
 */
class ContentTest {

    // ------------------------------------------------------------ el curso real

    @Test
    fun `el curriculum real carga entero y con ids unicos`() {
        val levels = Course.parseCurriculum(realCurriculum())
        val lessons = levels.flatMap { it.units }.flatMap { it.lessons }
        val exercises = lessons.flatMap { it.exercises }
        assertTrue("hay lecciones", lessons.size >= 27)
        assertTrue("hay ejercicios", exercises.size >= 207)
        assertEquals("ids de ejercicio únicos", exercises.size, exercises.map { it.id }.toSet().size)
        assertEquals("ids de lección únicos", lessons.size, lessons.map { it.id }.toSet().size)
        for (l in lessons) {
            assertTrue("${l.id} tiene teoría", l.theory.title.isNotBlank() && l.theory.body.isNotBlank() && l.theory.trap.isNotBlank())
        }
        // Formato del id: <lección>e<n>
        for (l in lessons) l.exercises.forEachIndexed { i, e ->
            assertEquals("${l.id}e${i + 1}", e.id)
        }
    }

    @Test
    fun `en el curso real la respuesta correcta siempre esta entre las opciones`() {
        val exercises = Course.parseCurriculum(realCurriculum())
            .flatMap { it.units }.flatMap { it.lessons }.flatMap { it.exercises }
        for (e in exercises) when (e) {
            is Exercise.ListenChoose -> assertTrue(e.id, e.answerIndex in e.options.indices)
            is Exercise.TranslateChoose -> assertTrue(e.id, e.answerIndex in e.options.indices)
            else -> {}
        }
    }

    // ------------------------------------------------------------ lo que debe reventar

    @Test
    fun `answer fuera de las opciones revienta y dice donde`() {
        val e = falla(curso(listen(answer = "Good night.")))
        assertTrue(e, e.contains("lección t1l1, ejercicio 1"))
        assertTrue(e, e.contains("no está entre las opciones"))
    }

    @Test
    fun `answer como numero ya no vale`() {
        val e = falla(curso("""{"id":"t1l1e1","type":"listen","audio":"Hello.","options":["Hello.","Hi."],"answer":0}"""))
        assertTrue(e, e.contains("answer"))
    }

    @Test
    fun `opciones repetidas revientan`() {
        val e = falla(curso(listen(options = """["Hello.","Hello.","Hi."]""")))
        assertTrue(e, e.contains("opciones repetidas"))
    }

    @Test
    fun `menos de dos opciones revienta`() {
        val e = falla(curso(listen(options = """["Hello."]""")))
        assertTrue(e, e.contains("al menos 2"))
    }

    @Test
    fun `en listen el audio tiene que ser la respuesta`() {
        val e = falla(curso(listen(audio = "Hi.")))
        assertTrue(e, e.contains("audio"))
    }

    @Test
    fun `id de ejercicio repetido en otra leccion revienta`() {
        val e = falla(
            curso(
                lecciones = """
                    ${leccion("t1l1", listen())},
                    ${leccion("t1l2", listen())}
                """
            )
        )
        assertTrue(e, e.contains("repetido"))
    }

    @Test
    fun `ejercicio sin id revienta con el formato esperado`() {
        val e = falla(curso("""{"type":"listen","audio":"Hello.","options":["Hello.","Hi."],"answer":"Hello."}"""))
        assertTrue(e, e.contains("falta \"id\""))
        assertTrue(e, e.contains("t1l1e1"))
    }

    @Test
    fun `theory incompleta revienta`() {
        val sinTrap = """{"title":"Algo","body":"Algo más","trap":""}"""
        val e = falla(curso(listen(), theory = sinTrap))
        assertTrue(e, e.contains("theory"))
        assertTrue(e, e.contains("trap"))
        val e2 = falla(curso(listen(), theory = null))
        assertTrue(e2, e2.contains("falta \"theory\""))
    }

    @Test
    fun `build con una palabra extra que ya esta en la frase revienta`() {
        val e = falla(curso("""{"id":"t1l1e1","type":"build","es":"Yo trabajo","answer":"I work","extra":["Work","the"]}"""))
        assertTrue(e, e.contains("repite palabras de la respuesta"))
    }

    @Test
    fun `speak sin sound revienta`() {
        val e = falla(curso("""{"id":"t1l1e1","type":"speak","text":"Hello."}"""))
        assertTrue(e, e.contains("sound"))
    }

    @Test
    fun `un ejercicio bien formado pasa`() {
        val levels = Course.parseCurriculum(curso(listen()))
        val ex = levels[0].units[0].lessons[0].exercises[0] as Exercise.ListenChoose
        assertEquals("t1l1e1", ex.id)
        assertEquals(0, ex.answerIndex)
    }

    // ------------------------------------------------------------ utilidades

    private fun realCurriculum(): String {
        // Los tests corren con el módulo app como directorio de trabajo.
        val f = File("src/main/assets/content/curriculum.json")
        assertTrue("existe ${f.absolutePath}", f.exists())
        return f.readText()
    }

    private fun listen(
        audio: String = "Hello.",
        options: String = """["Hello.","Hi.","Bye."]""",
        answer: String = "Hello."
    ) = """{"id":"t1l1e1","type":"listen","audio":"$audio","options":$options,"answer":"$answer"}"""

    private fun leccion(id: String, ejercicios: String, theory: String? = TEORIA_OK) =
        """{"id":"$id","title":"Prueba"${if (theory != null) ",\"theory\":$theory" else ""},"exercises":[$ejercicios]}"""

    private fun curso(ejercicios: String = listen(), theory: String? = TEORIA_OK, lecciones: String? = null) =
        """{"version":2,"levels":[{"id":"T","title":"Test","units":[{"id":"t1","title":"Unidad",
           "lessons":[${lecciones ?: leccion("t1l1", ejercicios, theory)}]}]}]}"""

    /** Devuelve el mensaje de la excepción; falla el test si NO revienta. */
    private fun falla(json: String): String {
        try {
            Course.parseCurriculum(json)
        } catch (e: IllegalArgumentException) {
            return e.message ?: ""
        }
        fail("debía reventar y no lo hizo")
        return ""
    }

    companion object {
        private const val TEORIA_OK = """{"title":"Título","body":"Cuerpo","trap":"Trampa"}"""
    }
}
