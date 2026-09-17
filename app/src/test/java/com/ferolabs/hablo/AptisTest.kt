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
import kotlin.random.Random

/** El Modo Aptis (etapa 5): los bancos reales, la regla de promoción de las pistas, el simulacro y la prueba obligatoria de la IA. */
class AptisTest {

    private val contenido = File("src/main/assets/content")

    private fun leerAsset(nombre: String): String? = File(contenido, nombre).takeIf { it.exists() }?.readText()

    private fun banco(): BancoAptis = parseBancoAptis(::leerAsset)

    private fun simulacro(): Diagnostico = banco().simulacro!!

    private fun nuevo(): Aptis = Aptis(Files.createTempFile("aptis", ".json").toFile().also { it.delete() })

    private fun ok(level: String) = level to true
    private fun mal(level: String) = level to false

    // --- contenido -------------------------------------------------------------

    @Test
    fun `los bancos reales cargan y el Core trae los 120 de Cowork mas los del simulacro que no repiten`() {
        val b = banco()
        assertEquals(listOf("core", "reading", "listening", "writing", "speaking"), b.pistas.map { it.id })
        val core = b.pista("core")!!
        assertTrue("core: ${core.tareas.size}", core.tareas.size in 121..135)   // 120 + los 15 del simulacro menos los repetidos
        assertTrue(core.tareas.all { it is ItemCore })
        assertEquals(30, core.segundosPorItem)
        assertEquals(Condicion(4, 5), core.promocion)
        assertEquals(10, core.ronda)
        // el vocabulario viene con subtipo y prompt; la gramática con hueco y point
        val vocab = core.tareas.filterIsInstance<ItemCore>().filter { it.sub.isNotBlank() }
        assertEquals(60, vocab.size)
        assertEquals(setOf("synonym", "definition", "usage", "collocation"), vocab.map { it.sub }.toSet())
        assertTrue(vocab.filter { it.sub != "usage" }.all { it.prompt.isNotBlank() && it.text.isBlank() })
        assertTrue(core.tareas.filterIsInstance<ItemCore>().filter { it.sub.isBlank() }.all { it.text.contains("___") })
        // los C1 del banco entran como B2: la pista no distingue más arriba
        assertEquals(listOf(NivelAptis.A2, NivelAptis.B1, NivelAptis.B2), core.niveles)
        assertEquals(NivelAptis.A2, core.nivelInicial)
        // las otras pistas, por ahora, solo con lo reciclado del simulacro
        assertEquals(4, b.pista("reading")!!.tareas.size)
        assertEquals(6, b.pista("listening")!!.tareas.size)
        assertEquals(2, b.pista("writing")!!.tareas.size)
        assertEquals(3, b.pista("speaking")!!.tareas.size)
        assertEquals(Condicion(2, 3), b.pista("writing")!!.promocion)
        assertTrue(b.pista("writing")!!.porIa && b.pista("speaking")!!.porIa && !core.porIa)
        assertEquals(4, b.cuatro.size)
        // el simulacro sigue entero
        val d = b.simulacro!!
        assertEquals(36, d.duracionMin)
        assertEquals(30, d.tareas)
        assertEquals(listOf(Condicion(4, 5, "A2"), Condicion(4, 5, "B1")), d.seccion("core")!!.umbrales["B1"])
    }

    @Test
    fun `el enunciado del vocabulario sale del prompt y la correccion muestra la respuesta puesta`() {
        val sin = ItemCore("v1", "A2", "", listOf("tiny", "huge", "common"), "huge", "Enormous y huge son lo mismo.", "synonym", "enormous")
        assertEquals("enormous", sin.enunciado)
        assertEquals("¿Cuál significa lo mismo?", sin.instruccion)
        assertEquals("enormous → huge", sin.resuelto)
        val col = ItemCore("v2", "B2", "", listOf("make", "do", "carry"), "do", "Do research.", "collocation", "___ research")
        assertEquals("______ research", col.enunciado)
        assertEquals("do research", col.resuelto)
        val gra = ItemCore("g1", "A2", "My sister ___ in a hospital.", listOf("works", "work", "working"), "works", "He/she/it llevan -s.", point = "presente simple")
        assertEquals("My sister works in a hospital.", gra.resuelto)
        assertEquals("", gra.instruccion)
    }

    @Test
    fun `un id repetido entre archivos, un orden que no es permutacion o una promocion mal escrita revientan`() {
        val base = leerAsset("aptis-diagnostico.json")!!
        val roto = JSONObject(base)
        roto.getJSONArray("secciones").getJSONObject(1).getJSONArray("tareas").getJSONObject(2).getJSONArray("orden").put(0, "Otra frase.")
        try { parseDiagnostico(roto); fail("debía reventar") } catch (e: IllegalArgumentException) { assertTrue(e.message, e.message!!.contains("permutación")) }
        val roto2 = JSONObject(base)
        roto2.getJSONArray("secciones").getJSONObject(0).getJSONArray("items").getJSONObject(1).put("id", "g001")
        try { parseBancoAptis { n -> if (n == "aptis-diagnostico.json") roto2.toString() else leerAsset(n) }; fail("debía reventar") } catch (e: IllegalArgumentException) { assertTrue(e.message, e.message!!.contains("repetido")) }
        val cfg = JSONObject(leerAsset("aptis-pistas.json")!!)
        cfg.getJSONObject("promocion").put("reading", "casi todas")
        try { parseBancoAptis { n -> if (n == "aptis-pistas.json") cfg.toString() else leerAsset(n) }; fail("debía reventar") } catch (e: IllegalArgumentException) { assertTrue(e.message, e.message!!.contains("promocion.reading")) }
        try { parseBancoAptis { n -> if (n == "aptis-pistas.json") null else leerAsset(n) }; fail("debía reventar") } catch (e: IllegalArgumentException) { assertTrue(e.message, e.message!!.contains("aptis-pistas.json")) }
        assertEquals(listOf(Condicion(4, 5)), parseCondicion("x", "4 de 5"))
        assertEquals(listOf(Condicion(4, 5, "A2"), Condicion(4, 5, "B1")), parseCondicion("x", "4 de 5 en A2 y 4 de 5 en B1"))
    }

    // --- la pista: promoción, flojo, ronda ---------------------------------------

    private fun pistaDePrueba(promocion: Condicion = Condicion(4, 5), ronda: Int = 5): PistaAptis {
        val tareas = ArrayList<TareaAptis>()
        for (n in listOf("A2", "B1", "B2")) for (i in 1..8) tareas.add(ItemCore("$n-$i", n, "x ___ y", listOf("a", "b", "c"), "a"))
        return PistaAptis("core", "Core", "Gramática", tareas, promocion, ronda, 30)
    }

    private fun intento(t: TareaAptis, ok: Boolean) = Intento(t.id, t.level, ok, "2026-09-16", if (ok) "a" else "b")

    @Test
    fun `la pista arranca en el nivel mas bajo con tareas y sube al cumplir la condicion sobre las ultimas N`() {
        val p = pistaDePrueba()
        val a = nuevo()
        assertEquals(NivelAptis.A2, a.nivel(p))
        val a2 = p.de(NivelAptis.A2)
        // 3 bien, 1 mal, 1 bien: 4 de 5 → sube
        assertNull(a.registrar(p, intento(a2[0], true)))
        assertNull(a.registrar(p, intento(a2[1], true)))
        assertNull(a.registrar(p, intento(a2[2], true)))
        assertNull(a.registrar(p, intento(a2[3], false)))
        assertEquals(NivelAptis.A2, a.nivel(p))
        assertNull("nada alcanzado todavia", a.alcanzado(p))
        val promo = a.registrar(p, intento(a2[4], true))
        assertEquals(Promocion(NivelAptis.A2, NivelAptis.B1), promo)
        assertEquals(NivelAptis.B1, a.nivel(p))
        assertEquals(NivelAptis.A2, a.alcanzado(p))
        // en B1, 3 de 5 no alcanza; una racha posterior de 4 de 5 sí
        val b1 = p.de(NivelAptis.B1)
        for (i in 0 until 5) a.registrar(p, intento(b1[i], i < 3))
        assertEquals(NivelAptis.B1, a.nivel(p))
        for (i in 0 until 4) a.registrar(p, intento(b1[i], true))
        assertEquals(NivelAptis.B2, a.nivel(p))
        assertEquals(NivelAptis.B1, a.alcanzado(p))
        // arriba del todo: B2 se alcanza (una sola vez) y no hay adónde pasar
        val b2 = p.de(NivelAptis.B2)
        for (i in 0 until 4) assertNull(a.registrar(p, intento(b2[i], true)))
        assertEquals(Promocion(NivelAptis.B2, null), a.registrar(p, intento(b2[4], true)))
        assertEquals(NivelAptis.B2, a.alcanzado(p))
        assertNull(a.registrar(p, intento(b2[5], true)))
        assertEquals(NivelAptis.B2, a.nivel(p))
        assertEquals(20, a.hechas(p))
    }

    @Test
    fun `una racha floja no baja de nivel pero la ronda mezcla el nivel anterior`() {
        val p = pistaDePrueba(ronda = 6)
        val a = nuevo()
        val a2 = p.de(NivelAptis.A2)
        for (i in 0 until 5) a.registrar(p, intento(a2[i], true))
        assertEquals(NivelAptis.B1, a.nivel(p))
        assertFalse(a.flojo(p))
        val b1 = p.de(NivelAptis.B1)
        for (i in 0 until 5) a.registrar(p, intento(b1[i], i == 0))   // 1 de 5
        assertEquals("no se degrada", NivelAptis.B1, a.nivel(p))
        assertTrue(a.flojo(p))
        val ronda = a.ronda(p, Random(1))
        assertEquals(6, ronda.size)
        assertEquals(3, ronda.count { it.nivel == NivelAptis.B1 })
        assertEquals(3, ronda.count { it.nivel == NivelAptis.A2 })
        assertEquals("la primera es del nivel actual", NivelAptis.B1, ronda[0].nivel)
        // las tareas de A2 que salen son las menos recientes: primero las nunca vistas
        assertTrue(ronda.filter { it.nivel == NivelAptis.A2 }.all { it.id in setOf("A2-6", "A2-7", "A2-8") })
        // las de B1 que salen son las nunca vistas (6, 7, 8) antes que las ya hechas
        assertEquals(setOf("B1-6", "B1-7", "B1-8"), ronda.filter { it.nivel == NivelAptis.B1 }.map { it.id }.toSet())
    }

    @Test
    fun `la ronda sirve primero lo nunca visto y despues lo mas viejo, y no repite dentro de la ronda`() {
        val p = pistaDePrueba(ronda = 5)
        val a = nuevo()
        val r1 = a.ronda(p, Random(7))
        assertEquals(5, r1.size)
        assertEquals(5, r1.map { it.id }.toSet().size)
        assertTrue(r1.all { it.nivel == NivelAptis.A2 })
        for (t in r1) a.registrar(p, intento(t, false))   // todo mal: sigue en A2 (0 de 5) y va flojo, pero no hay nivel anterior
        assertEquals(NivelAptis.A2, a.nivel(p))
        assertTrue(a.flojo(p))
        val r2 = a.ronda(p, Random(7))
        assertEquals(5, r2.size)
        assertEquals("primero las 3 nunca vistas", 3, r2.take(3).count { it.id !in r1.map { x -> x.id } })
        assertEquals("luego las más viejas", listOf(r1[0].id, r1[1].id), r2.drop(3).map { it.id })
        // sin tareas en el nivel: ronda vacía, no revienta
        val vacia = PistaAptis("reading", "Reading", "Lectura", emptyList(), Condicion(4, 5), 5)
        assertTrue(a.ronda(vacia).isEmpty())
        assertEquals(NivelAptis.A1, a.nivel(vacia))
    }

    @Test
    fun `en Writing y Speaking un juicio cuenta como acierto si llega al nivel de la tarea`() {
        val j = JuicioIa("d-w-01", "B1", "texto", 30, NivelAptis.B1, "una cita", "r", "p")
        assertTrue(j.valido)
        assertTrue(j.nivel!! >= NivelAptis.de("B1")!!)
        assertFalse(JuicioIa("d-w-02", "B2", "t", 30, NivelAptis.B1, "c").nivel!! >= NivelAptis.B2)
        assertEquals(NivelAptis.A1, JuezAptis.sinTexto(JuicioIa("d-w-01", "B1", "", 0)).nivel)
    }

    // --- el tablero -------------------------------------------------------------

    @Test
    fun `el piso es la mas floja de las cuatro, el Core no cuenta, y el simulacro se abre con las cinco en B1`() {
        val b = banco()
        val a = nuevo()
        assertEquals(listOf("reading", "listening", "writing", "speaking"), a.piso(b).map { it.id })   // todas empatan abajo: sin nivel
        assertFalse(a.simulacroDesbloqueado(b))
        assertEquals(5, a.faltanParaSimulacro(b).size)
        // Writing solo trae B1 y B2: entrena B1 desde el arranque, pero NO lo ha alcanzado
        assertEquals(NivelAptis.B1, a.nivel(b.pista("writing")!!))
        assertNull(a.alcanzado(b.pista("writing")!!))
        // subir Reading, Listening y Speaking hasta alcanzar B1 a mano; Writing se queda
        for (id in listOf("reading", "listening", "speaking")) {
            val p = b.pista(id)!!
            var vueltas = 0
            while ((a.alcanzado(p) ?: NivelAptis.A1) < NivelAptis.B1 && vueltas++ < 4) {
                val tareas = p.de(a.nivel(p))
                for (i in 0 until p.promocion.de) a.registrar(p, intento(tareas[i % tareas.size], true))
            }
            assertEquals(id, NivelAptis.B1, a.alcanzado(p))
        }
        assertEquals(listOf("writing"), a.piso(b).map { it.id })
        assertFalse(a.simulacroDesbloqueado(b))
        assertEquals(setOf("core", "writing"), a.faltanParaSimulacro(b).map { it.id }.toSet())
    }

    @Test
    fun `lo guardado sobrevive al reinicio y un archivo del diagnostico viejo se ignora`() {
        val f = Files.createTempFile("aptis", ".json").toFile().also { it.delete() }
        f.writeText("""{"version":1,"secciones":{"core":{"fecha":"2026-09-16","items":[{"id":"d-c-01","level":"A2","puesto":"works","ok":true}],"juicios":[]}}}""")
        val p = pistaDePrueba()
        val a = Aptis(f)
        assertEquals(0, a.hechas(p))
        val a2 = p.de(NivelAptis.A2)
        for (i in 0 until 5) a.registrar(p, intento(a2[i], true))
        a.registrar(p, Intento("d-w-01", "B1", true, "2026-09-16", "", JuicioIa("d-w-01", "B1", "texto", 30, NivelAptis.B1, "una cita", "razón", "práctica")))
        val b = Aptis(f)
        assertEquals(NivelAptis.B1, b.nivel(p))
        assertEquals(NivelAptis.A2, b.alcanzado(p))
        assertEquals(6, b.hechas(p))
        val ultimo = b.historial(p).last()
        assertEquals("una cita", ultimo.juicio!!.cita)
        assertTrue(ultimo.juicio!!.valido)
        b.borrarTodo()
        assertEquals(0, Aptis(f).hechas(p))
    }

    // --- el simulacro (el antiguo diagnóstico) ---------------------------------

    @Test
    fun `core del simulacro sigue la tabla del JSON, 4 de 5 por nivel y encadenada`() {
        val u = simulacro().seccion("core")!!.umbrales
        fun de(level: String, bien: Int) = List(5) { i -> level to (i < bien) }
        assertEquals(NivelAptis.B2, EstimacionAptis.core(de("A2", 5) + de("B1", 5) + de("B2", 5), u))
        assertEquals(NivelAptis.B2, EstimacionAptis.core(de("A2", 4) + de("B1", 4) + de("B2", 4), u))
        assertEquals(NivelAptis.B1, EstimacionAptis.core(de("A2", 5) + de("B1", 4) + de("B2", 3), u))
        assertEquals(NivelAptis.A2, EstimacionAptis.core(de("A2", 5) + de("B1", 3) + de("B2", 5), u))
        assertEquals(NivelAptis.A1, EstimacionAptis.core(de("A2", 3) + de("B1", 0) + de("B2", 0), u))
        assertEquals(NivelAptis.A2, EstimacionAptis.core(List(10) { i -> "A2" to (i < 8) } + List(10) { i -> "B1" to (i < 7) }, u))
        assertEquals(NivelAptis.A1, EstimacionAptis.core(emptyList(), u))
    }

    @Test
    fun `reading y listening del simulacro exigen todos los items del nivel y dan el mas alto alcanzado`() {
        assertEquals(NivelAptis.B1, EstimacionAptis.porTodos(listOf(ok("A2"), ok("A2"), ok("B1"), ok("B1"), ok("B2"), mal("B2"))))
        assertEquals(NivelAptis.B2, EstimacionAptis.porTodos(listOf(ok("A2"), ok("A2"), ok("B1"), ok("B1"), ok("B2"), ok("B2"))))
        assertEquals(NivelAptis.A1, EstimacionAptis.porTodos(listOf(ok("A2"), mal("A2"), ok("B1"), mal("B1"), mal("B2"), mal("B2"))))
        assertEquals(NivelAptis.A2, EstimacionAptis.porTodos(listOf(ok("A2"), ok("A2"), mal("B1"), mal("B2"))))
        assertEquals(NivelAptis.A1, EstimacionAptis.porTodos(emptyList()))
    }

    @Test
    fun `la IA estima el nivel alcanzado en dos tareas y sin dos juicios no hay estimacion`() {
        assertEquals(NivelAptis.A2, EstimacionAptis.porIa(listOf(NivelAptis.B1, NivelAptis.A2)))
        assertEquals(NivelAptis.B1, EstimacionAptis.porIa(listOf(NivelAptis.B2, NivelAptis.B1, NivelAptis.A2)))
        assertEquals(NivelAptis.B2, EstimacionAptis.porIa(listOf(NivelAptis.B2, NivelAptis.B2)))
        assertEquals(NivelAptis.A1, EstimacionAptis.porIa(listOf(NivelAptis.B2, NivelAptis.A1, NivelAptis.A1)))
        assertNull(EstimacionAptis.porIa(listOf(NivelAptis.B1)))
        assertNull(EstimacionAptis.porIa(emptyList()))
    }

    @Test
    fun `los resultados del simulacro se guardan por parte, dan el piso y se invalidan si cambia el contenido`() {
        val d = simulacro()
        val a = nuevo()
        val core = d.seccion("core")!!
        a.guardarSimulacro(ResultadoSeccion("core", "2026-09-16", items = core.core.map { AciertoItem(it.id, it.level, it.answer, it.level != "B2") }))
        a.guardarSimulacro(ResultadoSeccion("reading", "2026-09-16", items = listOf(AciertoItem("d-r-01", "A2", "closed", true), AciertoItem("d-r-04", "A2", "but", true), AciertoItem("d-r-02", "B1", "", false), AciertoItem("d-r-03", "B2", "", false))))
        a.guardarSimulacro(ResultadoSeccion("listening", "2026-09-16", items = d.seccion("listening")!!.escucha.map { AciertoItem(it.id, it.level, it.answer, true) }))
        a.guardarSimulacro(ResultadoSeccion("writing", "2026-09-16", juicios = listOf(
            JuicioIa("d-w-01", "B1", "t", 90, NivelAptis.B1, "una cita", "r", "p"),
            JuicioIa("d-w-02", "B2", "t", 30, null, "", "", "", error = "La IA no citó una frase tuya")
        )))
        assertNull(a.nivelesSimulacro(d)["writing"])
        assertFalse(a.simulacroCompleto(d))
        assertEquals(NivelAptis.B1, a.nivelesSimulacro(d)["core"])
        assertEquals(NivelAptis.A2, a.nivelesSimulacro(d)["reading"])
        assertEquals(NivelAptis.B2, a.nivelesSimulacro(d)["listening"])
        a.guardarSimulacro(ResultadoSeccion("writing", "2026-09-16", juicios = listOf(
            JuicioIa("d-w-01", "B1", "t", 90, NivelAptis.B1, "una cita", "r", "p"),
            JuicioIa("d-w-02", "B2", "t", 30, NivelAptis.B2, "otra cita", "r", "p")
        )))
        a.guardarSimulacro(ResultadoSeccion("speaking", "2026-09-16", juicios = listOf(
            JuicioIa("d-s-01", "A2", "t", 25, NivelAptis.A2, "c", "r", "p"),
            JuicioIa("d-s-02", "B1", "t", 50, NivelAptis.A2, "c", "r", "p"),
            JuicioIa("d-s-03", "B2", "t", 80, NivelAptis.B1, "c", "r", "p")
        )))
        assertTrue(a.simulacroCompleto(d))
        assertEquals(listOf("reading", "speaking"), a.pisoSimulacro(d).map { it.id })
        val viejo = ResultadoSeccion("core", "2026-09-16", items = listOf(AciertoItem("d-c-99", "A2", "x", true)))
        assertFalse(viejo.vigente(core))
        assertNull(viejo.nivel(core))
        assertEquals("Acertaste 5 de 5 en A2, 5 de 5 en B1 y 0 de 5 en B2.", TarjetaAptis.justificacion(a.resultadoSimulacro("core")!!.items))
        a.borrarSimulacro()
        assertEquals(0, a.hechasSimulacro(d))
    }

    // --- el juez ---------------------------------------------------------------

    @Test
    fun `las muletillas que Parakeet inventa al final se cortan antes de juzgar`() {
        assertEquals("She cook very good food. In the night I see the TV with my wife.", JuezAptis.sinMuletillas("She cook very good food. In the night I see the TV with my wife. Mm-hmm."))
        assertEquals("it depends on the person.", JuezAptis.sinMuletillas("it depends on the person. Okay. Mm-hmm. Okay."))
        assertEquals("I think it is", JuezAptis.sinMuletillas("I think it is okay"))     // un "okay" final se pierde aunque fuera suyo: no hay cómo saberlo y no cambia el nivel
        assertEquals("okay is the word I like", JuezAptis.sinMuletillas("okay is the word I like"))   // en medio, no se toca
        assertEquals("", JuezAptis.sinMuletillas("Mm-hmm."))
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
        assertEquals(NivelAptis.A1, JuezAptis.interpretar("{\"nivel\":\"<A2\",\"cita\":\"no like\"}")!!.nivel)
        assertEquals(NivelAptis.B2, JuezAptis.interpretar("{\"nivel\":\"C1\",\"cita\":\"x y z\"}")!!.nivel)
        assertNull(JuezAptis.interpretar("No puedo evaluar esto."))
        assertNull(JuezAptis.interpretar("{\"nivel\":\"alto\",\"cita\":\"x\"}"))
        assertNull(JuezAptis.interpretar(null))
        val p = JuezAptis.promptHablada(simulacro().seccion("speaking")!!.habla[0], "i go to the gym", 12, duracion = 30)
        assertTrue(p, p.contains("la grabación duró 30 segundos, con 12 segundos de voz"))
    }

    @Test
    fun `el audio de un dialogo se lee por turnos y el resto frase a frase`() {
        val d = simulacro()
        val dialogo = lineasAudio(d.seccion("listening")!!.escucha[2].audio)
        assertEquals(4, dialogo.size)
        assertTrue(dialogo[0].startsWith("Man: I still think"))
        assertTrue(dialogo[1].startsWith("Woman: Maybe"))
        assertEquals("Woman: Exactly. I'd rather lose the morning than lose the sleep.", dialogo[3])
        val dentista = lineasAudio(d.seccion("listening")!!.escucha[0].audio)
        assertEquals(3, dentista.size)
        assertTrue(dentista[2].startsWith("Please call me back"))
    }
}
