package com.ferolabs.hablo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** El diagnóstico del Modo Aptis (etapa 5): el archivo real, las reglas de estimación y la prueba obligatoria de la IA. */
class AptisTest {

    private fun real(): Diagnostico {
        val f = File("src/main/assets/content/aptis-diagnostico.json")
        assertTrue("falta " + f.absolutePath, f.exists())
        return parseDiagnostico(JSONObject(f.readText()))
    }

    private fun ok(level: String) = level to true
    private fun mal(level: String) = level to false

    @Test
    fun `el diagnostico real carga con sus 5 partes y 23 tareas`() {
        val d = real()
        assertEquals(30, d.duracionMin)
        assertEquals(listOf("core", "reading", "listening", "writing", "speaking"), d.secciones.map { it.id })
        assertEquals(23, d.tareas)
        val core = d.seccion("core")!!
        assertEquals(12, core.core.size)
        assertEquals(30, core.segundosPorItem)
        assertEquals(listOf(3, 4, 5), listOf("A2", "B1", "B2").map { l -> core.core.count { it.level == l } })
        val reading = d.seccion("reading")!!
        assertEquals(listOf("completar", "ordenar", "titulos"), reading.lectura.map { it.tipo })
        val ordenar = reading.lectura[1] as TareaLectura.Ordenar
        assertEquals("I sent my CV to about twenty places.", ordenar.orden[0])
        val titulos = reading.lectura[2] as TareaLectura.Titulos
        assertEquals(3, titulos.parrafos.size); assertEquals(4, titulos.titulos.size); assertEquals(3, titulos.answer.size)
        assertEquals(3, d.seccion("listening")!!.escucha.size)
        assertEquals(2, d.seccion("writing")!!.escritura.size)
        assertEquals(listOf(0, 30, 60), d.seccion("speaking")!!.habla.map { it.prepSeg })
        assertEquals(listOf(30, 60, 90), d.seccion("speaking")!!.habla.map { it.hablarSeg })
        assertTrue(d.seccion("writing")!!.porIa && d.seccion("speaking")!!.porIa && !core.porIa)
    }

    @Test
    fun `un orden que no es permutacion o unos titulos de menos revientan`() {
        val base = JSONObject(File("src/main/assets/content/aptis-diagnostico.json").readText())
        val roto = JSONObject(base.toString())
        roto.getJSONArray("secciones").getJSONObject(1).getJSONArray("tareas").getJSONObject(1).getJSONArray("orden").put(0, "Otra frase.")
        try { parseDiagnostico(roto); fail("debía reventar") } catch (e: IllegalArgumentException) { assertTrue(e.message, e.message!!.contains("permutación")) }
        val roto2 = JSONObject(base.toString())
        roto2.getJSONArray("secciones").getJSONObject(1).getJSONArray("tareas").getJSONObject(2).getJSONArray("answer").remove(2)
        try { parseDiagnostico(roto2); fail("debía reventar") } catch (e: IllegalArgumentException) { assertTrue(e.message, e.message!!.contains("título por párrafo")) }
        val roto3 = JSONObject(base.toString())
        roto3.getJSONArray("secciones").getJSONObject(0).getJSONArray("items").getJSONObject(1).put("id", "d-c-01")
        try { parseDiagnostico(roto3); fail("debía reventar") } catch (e: IllegalArgumentException) { assertTrue(e.message, e.message!!.contains("repetido")) }
    }

    @Test
    fun `core sigue la tabla del JSON`() {
        val a2 = listOf(ok("A2"), ok("A2"), ok("A2"))
        val a2flojo = listOf(ok("A2"), mal("A2"), mal("A2"))
        val b1 = List(4) { ok("B1") }
        val b1mitad = listOf(ok("B1"), ok("B1"), mal("B1"), mal("B1"))
        val b1tres = listOf(ok("B1"), ok("B1"), ok("B1"), mal("B1"))
        val b1uno = listOf(ok("B1"), mal("B1"), mal("B1"), mal("B1"))
        val b2 = List(5) { ok("B2") }
        val b2tres = listOf(ok("B2"), ok("B2"), ok("B2"), mal("B2"), mal("B2"))
        val b2dos = listOf(ok("B2"), ok("B2"), mal("B2"), mal("B2"), mal("B2"))
        val b2nada = List(5) { mal("B2") }
        assertEquals(NivelAptis.B2, EstimacionAptis.core(a2 + b1 + b2))
        assertEquals(NivelAptis.B2, EstimacionAptis.core(a2 + b1tres + b2tres))
        assertEquals(NivelAptis.B1, EstimacionAptis.core(a2 + b1tres + b2dos))       // 3 de 4 en B1 pero solo 2 de 5 en B2
        assertEquals(NivelAptis.B1, EstimacionAptis.core(a2 + b1mitad + b2))         // 2 de 4 en B1: B1 aunque B2 vaya bien
        assertEquals(NivelAptis.A2, EstimacionAptis.core(a2 + b1uno + b2nada))
        assertEquals(NivelAptis.BAJO_A2, EstimacionAptis.core(a2flojo + b1 + b2))    // 1 de 3 en A2: por debajo, sin adornos
    }

    @Test
    fun `reading y listening dan la tarea mas alta resuelta, dos tercios por nivel`() {
        assertEquals(NivelAptis.B1, EstimacionAptis.porDosTercios(listOf(ok("A2"), ok("B1"), mal("B2"))))
        assertEquals(NivelAptis.B2, EstimacionAptis.porDosTercios(listOf(ok("A2"), ok("B1"), ok("B2"))))
        assertEquals(NivelAptis.BAJO_A2, EstimacionAptis.porDosTercios(listOf(mal("A2"), mal("B1"), mal("B2"))))
        assertEquals(NivelAptis.A2, EstimacionAptis.porDosTercios(listOf(ok("A2"), mal("B1"), mal("B2"))))
        // con varios ítems por nivel: 2 de 3 alcanza, 1 de 3 no
        assertEquals(NivelAptis.B1, EstimacionAptis.porDosTercios(listOf(ok("B1"), ok("B1"), mal("B1"))))
        assertEquals(NivelAptis.BAJO_A2, EstimacionAptis.porDosTercios(listOf(ok("B1"), mal("B1"), mal("B1"))))
    }

    @Test
    fun `la IA estima el nivel alcanzado en dos tareas y sin dos juicios no hay estimacion`() {
        assertEquals(NivelAptis.A2, EstimacionAptis.porIa(listOf(NivelAptis.B1, NivelAptis.A2)))
        assertEquals(NivelAptis.B1, EstimacionAptis.porIa(listOf(NivelAptis.B2, NivelAptis.B1, NivelAptis.A2)))
        assertEquals(NivelAptis.B2, EstimacionAptis.porIa(listOf(NivelAptis.B2, NivelAptis.B2)))
        assertEquals(NivelAptis.BAJO_A2, EstimacionAptis.porIa(listOf(NivelAptis.B2, NivelAptis.BAJO_A2, NivelAptis.BAJO_A2)))
        assertNull(EstimacionAptis.porIa(listOf(NivelAptis.B1)))
        assertNull(EstimacionAptis.porIa(emptyList()))
    }

    @Test
    fun `la cita tiene que aparecer textual en lo que escribio el alumno`() {
        val texto = "I prefer Saturday because I don't work. On Sunday I visit my mother, she live far."
        assertTrue(JuezAptis.citaAparece("I prefer Saturday because I don't work", texto))
        assertTrue(JuezAptis.citaAparece("she live far", texto))
        assertTrue(JuezAptis.citaAparece("«On Sunday I visit my mother»", texto))          // comillas y mayúsculas no importan
        assertTrue(JuezAptis.citaAparece("i don’t work. on sunday", texto))                 // apóstrofo tipográfico
        assertFalse(JuezAptis.citaAparece("she lives far", texto))                          // corregida: ya no es su frase
        assertFalse(JuezAptis.citaAparece("Saturday", texto))                               // una palabra no es prueba
        assertFalse(JuezAptis.citaAparece("", texto))
        assertFalse(JuezAptis.citaAparece("I prefer Saturday", ""))
        assertTrue(JuezAptis.citaAparece("no like", "No like"))                             // texto de dos palabras: se cita entero
    }

    @Test
    fun `la respuesta de la IA se lee aunque venga con texto alrededor, y sin JSON no vale`() {
        val v = JuezAptis.interpretar("Aquí va: {\"nivel\":\"B1\",\"cita\":\"I prefer Saturday because I don't work\",\"razon\":\"Conecta y da razón.\",\"practica\":\"La -s de she.\",\"rubrica\":[true,true,true,false]} listo")
        assertNotNull(v)
        assertEquals(NivelAptis.B1, v!!.nivel)
        assertEquals("I prefer Saturday because I don't work", v.cita)
        assertEquals("La -s de she.", v.practica)
        assertEquals(NivelAptis.BAJO_A2, JuezAptis.interpretar("{\"nivel\":\"<A2\",\"cita\":\"no like\"}")!!.nivel)
        assertEquals(NivelAptis.B2, JuezAptis.interpretar("{\"nivel\":\"C1\",\"cita\":\"x y z\"}")!!.nivel)
        assertNull(JuezAptis.interpretar("No puedo evaluar esto."))
        assertNull(JuezAptis.interpretar("{\"nivel\":\"alto\",\"cita\":\"x\"}"))
        assertNull(JuezAptis.interpretar(null))
    }

    @Test
    fun `sin texto no se llama a nadie y cuenta por debajo de A2`() {
        val j = JuezAptis.sinTexto(JuicioIa("d-w-01", "B1", "", 0))
        assertTrue(j.valido)
        assertEquals(NivelAptis.BAJO_A2, j.nivel)
    }

    @Test
    fun `el audio de un dialogo se lee por turnos y el resto frase a frase`() {
        val d = real()
        val dialogo = lineasAudio(d.seccion("listening")!!.escucha[2].audio)
        assertEquals(4, dialogo.size)
        assertTrue(dialogo[0].startsWith("Man: I still think"))
        assertTrue(dialogo[1].startsWith("Woman: Maybe"))
        assertEquals("Woman: Exactly. I'd rather lose the morning than lose the sleep.", dialogo[3])
        val dentista = lineasAudio(d.seccion("listening")!!.escucha[0].audio)
        assertEquals(3, dentista.size)
        assertTrue(dentista[2].startsWith("Please call me back"))
    }

    @Test
    fun `los resultados se guardan por parte, sobreviven al reinicio y dan el piso`() {
        val d = real()
        val f = Files.createTempFile("aptis", ".json").toFile().also { it.delete() }
        val a = Aptis(f)
        assertEquals(0, a.hechas(d))
        assertFalse(a.completo(d))
        val core = d.seccion("core")!!
        a.guardar(ResultadoSeccion("core", "2026-09-16", items = core.core.map { AciertoItem(it.id, it.level, it.answer, it.level != "B2") }))
        a.guardar(ResultadoSeccion("reading", "2026-09-16", items = listOf(AciertoItem("d-r-01", "A2", "closed", true), AciertoItem("d-r-02", "B1", "", false), AciertoItem("d-r-03", "B2", "", false))))
        a.guardar(ResultadoSeccion("listening", "2026-09-16", items = listOf(AciertoItem("d-l-01", "A2", "Half past two", true), AciertoItem("d-l-02", "B1", "x", true), AciertoItem("d-l-03", "B2", "x", true))))
        a.guardar(ResultadoSeccion("writing", "2026-09-16", juicios = listOf(
            JuicioIa("d-w-01", "B1", "I prefer Saturday because I don't work.", 90, NivelAptis.B1, "I prefer Saturday because I don't work", "r", "p"),
            JuicioIa("d-w-02", "B2", "Dear Sir", 30, null, "", "", "", error = "La IA no citó una frase tuya")
        )))
        assertNull("sin dos juicios válidos no hay nivel", a.niveles(d)["writing"])
        assertFalse(a.completo(d))

        val b = Aptis(f)   // otra instancia: lee el archivo
        assertEquals(4, b.hechas(d))
        assertEquals(NivelAptis.B1, b.niveles(d)["core"])
        assertEquals(NivelAptis.A2, b.niveles(d)["reading"])
        assertEquals(NivelAptis.B2, b.niveles(d)["listening"])
        assertEquals(1, b.resultado("writing")!!.pendientesIa.size)
        assertEquals("La IA no citó una frase tuya", b.resultado("writing")!!.pendientesIa[0].error)

        b.guardar(ResultadoSeccion("writing", "2026-09-16", juicios = listOf(
            JuicioIa("d-w-01", "B1", "t", 90, NivelAptis.B1, "una cita", "r", "p"),
            JuicioIa("d-w-02", "B2", "t", 30, NivelAptis.B2, "otra cita", "r", "p")
        )))
        b.guardar(ResultadoSeccion("speaking", "2026-09-16", juicios = listOf(
            JuicioIa("d-s-01", "A2", "t", 25, NivelAptis.A2, "c", "r", "p"),
            JuicioIa("d-s-02", "B1", "t", 50, NivelAptis.A2, "c", "r", "p"),
            JuicioIa("d-s-03", "B2", "t", 80, NivelAptis.B1, "c", "r", "p")
        )))
        assertTrue(b.completo(d))
        assertEquals(NivelAptis.B1, b.niveles(d)["writing"])
        assertEquals(NivelAptis.A2, b.niveles(d)["speaking"])
        assertEquals(listOf("reading", "speaking"), b.piso(d).map { it.id })   // empate en A2: los dos son el piso

        b.borrarTodo()
        assertEquals(0, Aptis(f).hechas(d))
    }

    @Test
    fun `las tarjetas de core y lectura dicen que fallo y que practicar`() {
        val d = real()
        val core = d.seccion("core")!!
        val items = core.core.map { AciertoItem(it.id, it.level, it.answer, it.id != "d-c-05" && it.level != "B2") }
        assertEquals("Acertaste 3 de 3 en A2, 3 de 4 en B1 y 0 de 5 en B2.", TarjetaAptis.justificacion(items))
        assertEquals("La frase que fallaste más abajo (B1): «She has worked here since 2019.»", TarjetaAptis.practica(core, items))
        val p = JuezAptis.promptHablada(d.seccion("speaking")!!.habla[0], "i go to the gym", 12, duracion = 30)
        assertTrue(p, p.contains("la grabación duró 30 segundos, con 12 segundos de voz"))
        val reading = d.seccion("reading")!!
        val lect = listOf(AciertoItem("d-r-01", "A2", "closed", true), AciertoItem("d-r-02", "B1", "", false), AciertoItem("d-r-03", "B2", "", false))
        assertTrue(TarjetaAptis.practica(reading, lect).contains("Ordenar"))
        assertTrue(TarjetaAptis.practica(reading, lect.map { it.copy(ok = true) }).contains("Nada que corregir"))
    }
}
