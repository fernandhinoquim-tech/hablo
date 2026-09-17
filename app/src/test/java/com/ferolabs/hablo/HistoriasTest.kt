package com.ferolabs.hablo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/** Las historias de la etapa 4: el archivo real carga, y la ficha de teoría se lee con su negrita. */
class HistoriasTest {

    @Test
    fun `el historias json del repo carga con sus 12 historias`() {
        val f = File("src/main/assets/content/historias.json")
        assertTrue("falta " + f.absolutePath, f.exists())
        val tandas = Course.parseTandas(JSONObject(f.readText()).getJSONArray("tandas"))
        assertEquals(2, tandas.size)
        assertEquals(12, tandas.sumOf { it.historias.size })
        val h = tandas[0].historias[0]
        assertEquals("h-a2-01", h.id)
        assertEquals(3, h.preguntas.size)
        assertTrue(h.pistas.size >= 2)
        assertTrue(h.glosario.size >= 2)
        assertTrue(h.texto.startsWith("Miguel finished work"))
        assertEquals("neighbor", h.glosario[0].en)
    }

    @Test
    fun `una historia sin retell revienta`() {
        val json = """{"tandas":[{"id":"t","level":"A2","title":"T","historias":[
            {"id":"h1","level":"A2","title":"A","titleEs":"a","text":["1.","2.","3.","4.","5.","6."],
             "glosario":[{"en":"cat","es":"gato"},{"en":"dog","es":"perro"}],
             "preguntas":[{"q":"?","options":["a","b","c"],"answer":"a"},{"q":"?","options":["a","b","c"],"answer":"b"},{"q":"?","options":["a","b","c"],"answer":"c"}],
             "trap":"x"},
            {"id":"h2","level":"A2","title":"B","titleEs":"b","text":["1.","2.","3.","4.","5.","6."],"glosario":[{"en":"cat","es":"gato"},{"en":"dog","es":"perro"}],"preguntas":[],"retell":{"prompt_es":"c","pistas":["1","2"]},"trap":"x"},
            {"id":"h3","level":"A2","title":"C","titleEs":"c","text":["1.","2.","3.","4.","5.","6."],"glosario":[{"en":"cat","es":"gato"},{"en":"dog","es":"perro"}],"preguntas":[],"retell":{"prompt_es":"c","pistas":["1","2"]},"trap":"x"}
        ]}]}"""
        try {
            Course.parseTandas(JSONObject(json).getJSONArray("tandas"))
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message, e.message!!.contains("retell"))
            return
        }
        fail("debía reventar sin retell")
    }

    @Test
    fun `una tabla markdown de la ficha se parte en filas sin la linea separadora`() {
        val filas = listOf("| | cerca | lejos |", "|---|---|---|", "| **uno** | this | that |", "| **varios** | these | those |")
        val t = tablaMarkdown(filas.joinToString("\n"))
        assertEquals(3, t!!.size)
        assertEquals(listOf("", "cerca", "lejos"), t[0])
        assertEquals(listOf("**varios**", "these", "those"), t[2])
        assertEquals(null, tablaMarkdown("Solo hay dos preguntas: ¿está cerca o lejos?"))
        assertEquals(null, tablaMarkdown("| una sola línea |"))
    }

    @Test
    fun `markdownLite conserva el texto y quita los asteriscos`() {
        val a = markdownLite("En inglés **he, she, it** lleva *-s*: *He work**s***.")
        assertEquals("En inglés he, she, it lleva -s: He works.", a.text)
        assertTrue(a.spanStyles.any { it.item.fontWeight != null })
    }
}
