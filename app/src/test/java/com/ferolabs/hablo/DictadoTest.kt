package com.ferolabs.hablo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La comparación ESTRICTA del dictado de números (Cowork, 17-09) y los dos
 * archivos nuevos (oido.json, dictado.json) tal como están en el repo.
 */
class DictadoTest {

    @Test
    fun `los falsos bien que encontro Cowork ya no pasan`() {
        // suelta() los daba por buenos; dictado() no.
        assertFalse(Correccion.aceptaDictado("\$650", "\$6.50", emptyList()))
        assertFalse(Correccion.aceptaDictado("\$1200", "\$12", listOf("12 dollars", "\$12.00")))
        assertFalse(Correccion.aceptaDictado("\$999", "\$9.99", emptyList()))
        assertFalse(Correccion.aceptaDictado("1450", "14:50", emptyList()))
        assertFalse(Correccion.aceptaDictado("wademail.com", "wade@mail.com", emptyList()))
        assertFalse(Correccion.aceptaDictado("6:50", "6.50", emptyList()))
        assertFalse(Correccion.aceptaDictado("May three", "May 3", emptyList()))
        assertFalse(Correccion.aceptaDictado("", "13", emptyList()))
    }

    @Test
    fun `lo correcto sigue pasando`() {
        assertTrue(Correccion.aceptaDictado("6.50", "\$6.50", emptyList()))          // el $ es opcional
        assertTrue(Correccion.aceptaDictado("\$6.50.", "\$6.50", emptyList()))       // punto final fuera
        assertTrue(Correccion.aceptaDictado(" 7:30 ", "7:30", emptyList()))
        assertTrue(Correccion.aceptaDictado("07:30", "7:30", emptyList()))          // cero inicial de la hora
        assertTrue(Correccion.aceptaDictado("thirteen", "13", emptyList()))         // 0-100 en letras
        assertTrue(Correccion.aceptaDictado("Thirteen", "13", emptyList()))
        assertTrue(Correccion.aceptaDictado("12", "\$12", listOf("12 dollars")))
        assertTrue(Correccion.aceptaDictado("12 dollars", "\$12", listOf("12 dollars")))
        assertTrue(Correccion.aceptaDictado("june 5", "June 5", listOf("June 5th")))
        assertTrue(Correccion.aceptaDictado("June 5th", "June 5", listOf("June 5th")))
        assertTrue(Correccion.aceptaDictado("555-0142", "555-0142", listOf("5550142")))
        assertTrue(Correccion.aceptaDictado("5550142", "555-0142", listOf("5550142")))
        assertTrue(Correccion.aceptaDictado("Wade@Mail.com", "wade@mail.com", emptyList()))
        assertTrue(Correccion.aceptaDictado("23 oak street", "23 Oak Street", listOf("23 Oak St")))
        assertTrue(Correccion.aceptaDictado("Gina", "Gina", emptyList()))
        assertEquals("twenty-three oak street", Correccion.dictado("23 Oak Street"))
        assertEquals("6.50", Correccion.dictado("\$6.50,"))
        assertEquals("555-0142", Correccion.dictado("555-0142."))
    }

    @Test
    fun `dictado json del repo carga con sus 80 items y oido json con sus bloques`() {
        val fd = File("src/main/assets/content/dictado.json")
        assertTrue("falta " + fd.absolutePath, fd.exists())
        val items = Course.parseDictado(JSONObject(fd.readText()).getJSONArray("items"))
        assertEquals(80, items.size)
        assertEquals(40, items.count { it.escribir })
        assertTrue(items.all { it.audio.none { c -> c.isDigit() } })
        // Ningún ítem de escribir tiene un accept que la regla estricta confunda con la respuesta.
        for (it in items.filter { it.escribir }) {
            val vistas = hashSetOf(Correccion.dictado(it.answer))
            for (a in it.accept) assertTrue("${it.id}: accept «$a» repite", vistas.add(Correccion.dictado(a)))
        }
        val fo = File("src/main/assets/content/oido.json")
        assertTrue("falta " + fo.absolutePath, fo.exists())
        val bloques = Course.parseOido(JSONObject(fo.readText()).getJSONArray("bloques"))
        assertTrue(bloques.size >= 10)
        assertTrue(bloques.all { it.pares.size in 4..10 })
        assertEquals("Now I say ship again.", bloques[0].pares[0].con("ship"))
    }

    @Test
    fun `un bloque de oido sin hueco o un dictado con cifras en el audio revientan`() {
        val malo = """{"bloques":[{"id":"x","title":"t","level":"A1","contraste":"c","explicacion":"e","hablar":"h","sound":"sh",
            "pares":[{"a":"ship","b":"sheep","frase":"I say ship."},{"a":"a","b":"b","frase":"___"},{"a":"c","b":"d","frase":"___"},
            {"a":"e","b":"f","frase":"___"},{"a":"g","b":"h","frase":"___"},{"a":"i","b":"j","frase":"___"}]}]}"""
        try {
            Course.parseOido(JSONObject(malo).getJSONArray("bloques")); throw AssertionError("debía reventar")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!, e.message!!.contains("___"))
        }
        val cifras = """{"items":[{"id":"d1","tipo":"numero","level":"A1","modo":"escribir","audio":"I have 13 cousins.","pregunta_es":"p","answer":"13","tip":"t"}]}"""
        try {
            Course.parseDictado(JSONObject(cifras).getJSONArray("items")); throw AssertionError("debía reventar")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!, e.message!!.contains("cifras"))
        }
    }
}
