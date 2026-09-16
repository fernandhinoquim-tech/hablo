package com.ferolabs.hablo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * La ficha de la charla libre (etapa 2, 2026-09-16): topes, errores gratis
 * desde CORRECCIÓN:, y que una respuesta rota de la IA no borre nada.
 */
class MemoriaTest {

    private fun nueva(): Pair<Memoria, File> {
        val f = Files.createTempFile("perfil", ".json").toFile().also { it.delete() }
        return Memoria(f) to f
    }

    @Test
    fun `los errores se cuentan y se agrupan por la frase corregida`() {
        val (m, _) = nueva()
        m.anotarError("Dijiste \"I have 30 years\"; en inglés la edad va con to be: \"I am 30 years old\".")
        m.anotarError("Dijiste \"I have 30 years\" otra vez; es \"I am 30 years old\".")
        m.anotarError("Dijiste \"she work\"; la tercera persona lleva -s: \"she works\".")
        assertEquals(2, m.errores.size)
        assertEquals(2, m.errores.first { it.texto.contains("30 years") }.veces)
        assertEquals(1, m.errores.first { it.texto.contains("she work") }.veces)
    }

    @Test
    fun `nunca mas de 15 errores ni 12 datos`() {
        val (m, _) = nueva()
        for (i in 1..20) m.anotarError("Dijiste \"error número $i\"; era otra cosa.")
        assertEquals(Memoria.MAX_ERRORES, m.errores.size)
        assertTrue(m.errores.none { it.texto.contains("número 1\"") })   // salió el más viejo
        val datos = (1..20).joinToString(",") { "{\"k\":\"dato$it\",\"v\":\"v$it\"}" }
        assertTrue(m.aplicarRespuesta("{\"datos\":[$datos],\"resumen\":\"x\"}"))
        assertEquals(Memoria.MAX_DATOS, m.datos.size)
    }

    @Test
    fun `el resumen se recorta a 60 palabras y el bloque del prompt cabe en 400 tokens`() {
        val (m, _) = nueva()
        val largo = (1..200).joinToString(" ") { "palabra$it" }
        assertTrue(m.aplicarRespuesta("{\"datos\":[{\"k\":\"nombre\",\"v\":\"Fero\"}],\"resumen\":\"$largo\"}"))
        assertEquals(Memoria.MAX_PALABRAS_RESUMEN, m.resumen.split(" ").size)
        assertTrue(m.resumen.endsWith("…"))
        for (i in 1..15) m.anotarError("Dijiste \"" + "x".repeat(200) + " $i\"; era otra cosa larga.")
        val bloque = m.bloquePrompt()
        assertTrue(bloque.length / 4 <= Memoria.MAX_TOKENS)
        assertTrue(bloque.contains("nombre: Fero"))
    }

    @Test
    fun `una respuesta que no parsea conserva la ficha vieja`() {
        val (m, f) = nueva()
        assertTrue(m.aplicarRespuesta("Here you go: {\"datos\":[{\"k\":\"ciudad\",\"v\":\"Bogotá\"}],\"resumen\":\"Hablamos del trabajo.\"} ok"))
        assertFalse(m.aplicarRespuesta("Sorry, I can't do that."))
        assertFalse(m.aplicarRespuesta("{\"datos\": [broken"))
        assertFalse(m.aplicarRespuesta("{\"otra\": 1}"))
        // JSON válido pero vacío tampoco borra.
        assertTrue(m.aplicarRespuesta("{\"datos\":[],\"resumen\":\"\"}"))
        assertEquals("Bogotá", m.datos.first().v)
        assertEquals("Hablamos del trabajo.", m.resumen)
        // Y lo guardado se vuelve a leer igual.
        val m2 = Memoria(f)
        m2.cargar()
        assertEquals("Bogotá", m2.datos.first().v)
    }

    @Test
    fun `un resumen que llega despues de olvidar no revive la ficha`() {
        val (m, _) = nueva()
        val gen = m.generacion
        m.borrar()
        assertFalse(m.aplicarRespuesta("{\"datos\":[{\"k\":\"nombre\",\"v\":\"Fero\"}],\"resumen\":\"x\"}", gen))
        assertFalse(m.hayAlgo())
        assertTrue(m.aplicarRespuesta("{\"datos\":[{\"k\":\"nombre\",\"v\":\"Fero\"}]}", m.generacion))
        assertTrue(m.hayAlgo())
    }

    @Test
    fun `sin ficha el bloque va vacio y con nombre se sabe el nombre`() {
        val (m, _) = nueva()
        assertEquals("", m.bloquePrompt())
        assertFalse(m.hayAlgo())
        m.aplicarRespuesta("{\"datos\":[{\"k\":\"Nombre\",\"v\":\"Fero\"}]}")
        assertEquals("Fero", m.nombre())
        assertTrue(m.hayAlgo())
        m.borrar()
        assertFalse(m.hayAlgo())
    }
}
