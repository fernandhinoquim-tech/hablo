package com.ferolabs.hablo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** El modelo de los crucigramas (17-09): rejilla, selección al tocar, corrección por palabra y lo guardado. */
class CrucigramaTest {

    // C A T      cat (1→), cow (1↓), tea (3↓ desde la t)... rejilla mínima con un cruce:
    //   O   E    cat H en (0,0); cow V en (0,0); tea V en (0,2)
    //   W   A
    private val cruci = Crucigrama(
        "x", "t", "A1", "b", 3, 3,
        listOf(
            PalabraCruci(1, 'H', 0, 0, "cat", "gato"),
            PalabraCruci(1, 'V', 0, 0, "cow", "vaca"),
            PalabraCruci(2, 'V', 0, 2, "tea", "té")
        )
    )

    @Test
    fun `la rejilla sabe que casillas son blancas y que numero llevan`() {
        assertEquals('c', cruci.solucion[Celda(0, 0)])
        assertEquals('a', cruci.solucion[Celda(2, 2)])
        assertNull(cruci.solucion[Celda(1, 1)])
        assertEquals(1, cruci.numeroEn[Celda(0, 0)])
        assertEquals(2, cruci.numeroEn[Celda(0, 2)])
        assertEquals(2, cruci.palabrasEn(Celda(0, 0)).size)
        assertEquals(listOf("tea"), cruci.palabrasEn(Celda(1, 2)).map { it.en })
    }

    @Test
    fun `tocar una casilla elige la horizontal, tocar el cursor en un cruce cambia de direccion`() {
        val cat = cruci.palabras[0]; val cow = cruci.palabras[1]; val tea = cruci.palabras[2]
        assertEquals(cat, cruci.seleccionAlTocar(Celda(0, 0), null, null))
        assertEquals(cow, cruci.seleccionAlTocar(Celda(0, 0), cat, Celda(0, 0)))       // mismo sitio: gira
        assertEquals(cat, cruci.seleccionAlTocar(Celda(0, 1), cat, Celda(0, 0)))       // otra casilla de la misma: sigue
        assertEquals(tea, cruci.seleccionAlTocar(Celda(2, 2), cat, Celda(0, 1)))       // solo vertical ahí
        assertEquals(cow, cruci.seleccionAlTocar(Celda(2, 0), cat, Celda(0, 1)))
        assertNull(cruci.seleccionAlTocar(Celda(1, 1), cat, null))                      // oscura
    }

    @Test
    fun `una palabra completa y bien es correcta, completa y mal no, y el crucigrama se resuelve`() {
        var e = EstadoCruci(letras = mapOf("0,0" to 'c', "0,1" to 'a', "0,2" to 't'))
        assertTrue(e.correcta(cruci.palabras[0]))
        assertFalse(e.completa(cruci.palabras[1]))
        e = e.copy(letras = e.letras + mapOf("1,0" to 'a', "2,0" to 'w'))
        assertTrue(e.completa(cruci.palabras[1]))
        assertFalse(e.correcta(cruci.palabras[1]))
        e = e.copy(letras = e.letras + mapOf("1,0" to 'o', "1,2" to 'e', "2,2" to 'a'))
        assertTrue(e.resuelto(cruci))
    }

    @Test
    fun `lo guardado sobrevive a recargar, y reiniciar lo borra`() {
        val f = File.createTempFile("cruci", ".json"); f.delete()
        val c = Crucigramas(f)
        c.guardar("x", EstadoCruci(mapOf("0,0" to 'c'), setOf("cat"), false, 0, ""))
        c.guardar("y", EstadoCruci(emptyMap(), emptySet(), true, 5, "2026-09-17"))
        val c2 = Crucigramas(f)
        assertEquals('c', c2.estado("x").letra(Celda(0, 0)))
        assertEquals(setOf("cat"), c2.estado("x").conAyuda)
        assertTrue(c2.estado("y").hecho)
        assertEquals(5, c2.estado("y").sinAyuda)
        assertEquals(1, c2.hechos())
        c2.reiniciar("y")
        assertFalse(Crucigramas(f).estado("y").hecho)
        f.delete()
    }

    @Test
    fun `el crucigramas json del repo carga con sus 210 rejillas y las pistas son las del banco`() {
        val f = File("src/main/assets/content/crucigramas.json")
        assertTrue("falta " + f.absolutePath, f.exists())
        val lista = Course.parseCrucigramas(JSONObject(f.readText()).getJSONArray("crucigramas"))
        assertEquals(210, lista.size)
        assertTrue(lista.all { it.palabras.size in 5..8 && it.filas <= 10 && it.columnas <= 10 })
        val bancos = Course.parseBancos(JSONObject(File("src/main/assets/content/vocabulario.json").readText()).getJSONArray("bancos"))
            .associateBy { it.id }
        for (c in lista) {
            val b = bancos[c.banco] ?: throw AssertionError("${c.id}: banco ${c.banco} no existe")
            for (p in c.palabras) assertEquals("${c.id} ${p.en}", b.pares.first { it.en == p.en }.es, p.pista)
        }
    }

    @Test
    fun `una corrida fantasma o un choque de letras revientan`() {
        val fantasma = """{"crucigramas":[{"id":"f","title":"t","level":"A1","banco":"b","filas":3,"columnas":3,"palabras":[
            {"numero":1,"dir":"H","fila":0,"col":0,"en":"cat","pista":"gato"},{"numero":1,"dir":"V","fila":0,"col":0,"en":"cow","pista":"vaca"},
            {"numero":2,"dir":"V","fila":0,"col":2,"en":"tea","pista":"té"},{"numero":3,"dir":"H","fila":1,"col":0,"en":"oe","pista":"x"},
            {"numero":4,"dir":"H","fila":2,"col":0,"en":"wa","pista":"y"}]}]}"""
        try {
            Course.parseCrucigramas(JSONObject(fantasma).getJSONArray("crucigramas")); throw AssertionError("debía reventar")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!, e.message!!.contains("fantasmas") || e.message!!.contains("numeración"))
        }
        val choque = """{"crucigramas":[{"id":"c","title":"t","level":"A1","banco":"b","filas":3,"columnas":3,"palabras":[
            {"numero":1,"dir":"H","fila":0,"col":0,"en":"cat","pista":"gato"},{"numero":1,"dir":"V","fila":0,"col":0,"en":"dog","pista":"perro"},
            {"numero":2,"dir":"V","fila":0,"col":2,"en":"tea","pista":"té"},{"numero":3,"dir":"H","fila":2,"col":0,"en":"gap","pista":"x"},
            {"numero":4,"dir":"H","fila":1,"col":0,"en":"oe","pista":"y"}]}]}"""
        try {
            Course.parseCrucigramas(JSONObject(choque).getJSONArray("crucigramas")); throw AssertionError("debía reventar")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!, e.message!!.contains("choque"))
        }
    }
}
