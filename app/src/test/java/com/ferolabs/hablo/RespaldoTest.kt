package com.ferolabs.hablo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RespaldoTest {

    private val prefs: Map<String, Any> = mapOf(
        "xp" to 1240,
        "streak" to 3,
        "score_a1u1l1" to 90,
        "score_a1u1l2" to 60,
        "score_a1u2l1" to 40,
        "score_repaso" to 0,
        "last_day" to 20720,
        "respaldo_auto_ms" to 1_790_000_000_000L,
        "speech_scale" to 0.9f,
        "show_faces" to false,
        "teacher_id" to "sophie",
        "algo_set" to setOf("b", "a")
    )

    private val archivos: Map<String, String?> = linkedMapOf(
        "progreso.json" to """{"fallos":[]}""",
        "mazo.json" to """{"items":[{"id":"a"},{"id":"b"},{"id":"c"}]}""",
        "aptis.json" to null,
        "memoria/perfil.json" to """{"datos":[]}"""
    )

    private fun respaldo() = RespaldoDatos.armar(prefs, archivos, "0.9.9", "2026-09-24 15:30")

    @Test
    fun lasPreferenciasVuelvenConSuTipo() {
        // Pasa por texto, como en el archivo real.
        val leido = JSONObject(respaldo().toString())
        assertNull(RespaldoDatos.validar(leido))
        val p = RespaldoDatos.prefsDe(leido)
        assertEquals(prefs.keys, p.keys)
        assertEquals(1240, p["xp"])
        assertEquals(1_790_000_000_000L, p["respaldo_auto_ms"])
        assertEquals(0.9f, p["speech_scale"])
        assertEquals(false, p["show_faces"])
        assertEquals("sophie", p["teacher_id"])
        assertEquals(setOf("a", "b"), p["algo_set"])
        assertTrue(p["xp"] is Int)
        assertTrue(p["respaldo_auto_ms"] is Long)
    }

    @Test
    fun losArchivosVuelvenIgualesYElQueNoExistiaQuedaNulo() {
        val a = RespaldoDatos.archivosDe(JSONObject(respaldo().toString()))
        assertEquals(archivos.keys, a.keys)
        assertEquals(archivos["mazo.json"], a["mazo.json"])
        assertTrue(a.containsKey("aptis.json"))
        assertNull(a["aptis.json"])
        // crucigramas.json no venía: no se toca al recuperar.
        assertFalse(a.containsKey("crucigramas.json"))
    }

    @Test
    fun elResumenCuentaLeccionesAprobadasPuntosYFrases() {
        val r = RespaldoDatos.resumen(respaldo())
        assertEquals(2, r.lecciones)          // 90 y 60; el 40 y el repaso no
        assertEquals(1240, r.xp)
        assertEquals(3, r.racha)
        assertEquals(3, r.frases)
        assertEquals("2026-09-24 15:30", r.fecha)
        assertEquals("2 lecciones aprobadas · 1.240 puntos · 3 frases en el repaso", r.texto())
    }

    @Test
    fun rechazaLoQueNoEsUnRespaldo() {
        assertNotNull(RespaldoDatos.validar(JSONObject("""{"hola": 1}""")))
        val nuevo = respaldo().put("version", RespaldoDatos.VERSION + 1)
        assertTrue(RespaldoDatos.validar(nuevo)!!.contains("más nueva"))
        val sinPrefs = respaldo().apply { remove("prefs") }
        assertNotNull(RespaldoDatos.validar(sinPrefs))
        val tipoRaro = respaldo().apply { getJSONObject("prefs").put("x", JSONObject().put("t", "zz").put("v", 1)) }
        assertNotNull(RespaldoDatos.validar(tipoRaro))
    }

    @Test
    fun unaAppRecienInstaladaNoTieneNadaQueGuardar() {
        val vacio = RespaldoDatos.armar(
            mapOf("teacher_id" to "emma", "speech_scale" to 1.0f),
            mapOf("progreso.json" to null, "mazo.json" to null),
            "0.9.9", "2026-09-24 15:30"
        )
        assertFalse(RespaldoDatos.hayAlgo(vacio))
        assertTrue(RespaldoDatos.hayAlgo(respaldo()))
        // Solo el cuaderno, sin puntos: también vale la pena.
        val soloCuaderno = RespaldoDatos.armar(emptyMap<String, Any>(), mapOf("progreso.json" to "{}"), "0.9.9", "x")
        assertTrue(RespaldoDatos.hayAlgo(soloCuaderno))
    }
}
