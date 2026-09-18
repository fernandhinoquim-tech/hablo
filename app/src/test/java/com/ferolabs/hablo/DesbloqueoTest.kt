package com.ferolabs.hablo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La cadena de desbloqueo con unidades intercaladas (A1 ampliado, 16-09) y las fechas en Correccion. */
class DesbloqueoTest {

    private fun unidad(id: String, vararg lecciones: String) = CourseUnit(
        id, "📘", id, "", lecciones.map { Lesson(it, it, Theory("t", "b", "x"), emptyList()) }
    )

    // El orden del A1 ampliado: las nuevas (u10, u11, u12) van intercaladas entre las viejas.
    private val units = listOf(
        unidad("a1u1", "a1u1l1", "a1u1l2"),
        unidad("a1u10", "a1u10l1", "a1u10l2"),   // nueva, después de u1
        unidad("a1u2", "a1u2l1", "a1u2l2"),
        unidad("a1u3", "a1u3l1"),
        unidad("a1u11", "a1u11l1"),               // nueva, después de u3
        unidad("a1u4", "a1u4l1", "a1u4l2"),
        unidad("a1u12", "a1u12l1"),               // nueva, después de u4
        unidad("a1u5", "a1u5l1", "a1u5l2"),
        unidad("a1u6", "a1u6l1"),
        unidad("a1u13", "a1u13l1"),               // nueva, después de u6
        unidad("a1u7", "a1u7l1")
    )

    @Test
    fun `lo que Fero ya aprobo sigue abierto, las nuevas se abren junto a la siguiente vieja, y dos sin empezar cierran`() {
        // Fero aprobó u1..u4 antes de que entraran las nuevas.
        val aprobadas = setOf("a1u1", "a1u2", "a1u3", "a1u4")
        val score: (String) -> Int = { id -> if (aprobadas.any { id.startsWith(it + "l") }) 80 else 0 }
        val abiertas = desbloqueadas(units, score)
        for (u in listOf("a1u1", "a1u10", "a1u2", "a1u3", "a1u11", "a1u4", "a1u12", "a1u5")) assertTrue(u, u in abiertas)
        for (u in listOf("a1u6", "a1u13", "a1u7")) assertFalse(u, u in abiertas)
        // aprueba u5 sin tocar u12: se abren u6 y, dos por delante, u13; u7 sigue cerrada
        val score2: (String) -> Int = { id -> if ((aprobadas + "a1u5").any { id.startsWith(it + "l") }) 80 else 0 }
        val abiertas2 = desbloqueadas(units, score2)
        assertTrue("a1u6" in abiertas2)
        assertTrue("a1u12" in abiertas2)
        assertTrue("a1u13" in abiertas2)
        assertFalse("a1u7" in abiertas2)
        // aprueba u6: se abre u7 (vieja) aunque u13 (nueva) siga sin empezar
        val score3: (String) -> Int = { id -> if ((aprobadas + "a1u5" + "a1u6").any { id.startsWith(it + "l") }) 80 else 0 }
        val abiertas3 = desbloqueadas(units, score3)
        assertTrue("a1u13" in abiertas3)
        assertTrue("a1u7" in abiertas3)
    }

    @Test
    fun `sin progreso solo esta abierta la primera, y la cadena vieja sigue igual`() {
        assertEquals(setOf("a1u1"), desbloqueadas(units, { 0 }))
        val viejas = listOf(unidad("u1", "u1l1"), unidad("u2", "u2l1"), unidad("u3", "u3l1"))
        assertEquals(setOf("u1"), desbloqueadas(viejas, { 0 }))
        // u1 aprobada: se abre u2 y, dos por delante, u3
        assertEquals(setOf("u1", "u2", "u3"), desbloqueadas(viejas, { if (it == "u1l1") 70 else 0 }))
        // u2 a medias no cierra u3 (nada de candados que aparecen), pero tampoco abre u4
        assertEquals(setOf("u1", "u2", "u3"), desbloqueadas(viejas + unidad("u4", "u4l1"), { if (it == "u1l1") 70 else if (it == "u2l1") 30 else 0 }))
    }

    @Test
    fun `la primera unidad de cada nivel se abre sin terminar el anterior, y la cadena sigue dentro del nivel`() {
        val a2 = listOf(unidad("a2u1", "a2u1l1"), unidad("a2u2", "a2u2l1"), unidad("a2u3", "a2u3l1"))
        val b1 = listOf(unidad("b1u1", "b1u1l1"), unidad("b1u2", "b1u2l1"), unidad("b1u3", "b1u3l1"))
        val todas = units + a2 + b1
        val primeras = setOf("a1u1", "a2u1", "b1u1")
        val sinNada = desbloqueadas(todas, { 0 }, primeras)
        assertEquals(setOf("a1u1", "a2u1", "b1u1"), sinNada)
        // aprobar b1u1 abre b1u2 y, dos por delante, b1u3; A2 sigue cerrado salvo su primera
        val conB1 = desbloqueadas(todas, { if (it == "b1u1l1") 80 else 0 }, primeras)
        assertTrue("b1u2" in conB1 && "b1u3" in conB1)
        assertFalse("a2u2" in conB1)
        // sin `primeras` (la regla vieja) B1 sigue cerrado
        assertFalse("b1u1" in desbloqueadas(todas, { 0 }))
    }

    @Test
    fun `un numero pegado a un mes es una fecha y no se pasa a letras`() {
        assertEquals("may 3", Correccion.fichas("May 3"))
        assertEquals("i have three cats", Correccion.fichas("I have 3 cats"))
        assertEquals("my birthday is on june 25", Correccion.fichas("My birthday is on June 25"))
        assertEquals("twenty-five june", Correccion.fichas("25 June"))   // solo el número que SIGUE al mes se queda en cifras
        assertEquals("310 555 2468", Correccion.fichas("310-555-2468"))
        assertFalse(Correccion.acepta("May three", "May 3", emptyList()))           // el error que enseña a1u12l2 no cuela
        assertTrue(Correccion.acepta("may 3", "May 3", emptyList()))
        assertTrue(Correccion.acepta("May 3rd", "May 3", listOf("May 3rd", "May third")))
    }
}
