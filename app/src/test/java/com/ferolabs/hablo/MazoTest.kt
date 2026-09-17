package com.ferolabs.hablo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.random.Random

/** El mazo de repaso (etapa 3): Leitner 1/3/7/16/35, la escalera y las sesiones que arma Repaso. */
class MazoTest {

    private fun nuevo(): Mazo = Mazo(Files.createTempFile("mazo", ".json").toFile().also { it.delete() })

    private val leccion = Lesson(
        "t1l1", "Prueba", Theory("t", "b", "x"),
        listOf(
            Exercise.TranslateChoose("t1l1e1", "Ella trabaja en un hospital.", listOf("She works in a hospital.", "She work in a hospital."), "She works in a hospital."),
            Exercise.WriteIt("t1l1e2", "Mi hermano estudia inglés.", "My brother studies English.", listOf("My brother studies English every day.")),
            Exercise.Cloze("t1l1e3", "He ___ on Mondays.", "works", "Él trabaja los lunes."),
            Exercise.TypeWhatYouHear("t1l1e4", "I don't have a car.", "No tengo carro."),
            Exercise.SpeakIt("t1l1e5", "Wash your shoes.", Sound.SH),          // sin español: no entra
            Exercise.ListenChoose("t1l1e6", "Good morning.", listOf("Good morning.", "Good evening."), "Good morning.")   // no entra
        )
    )

    @Test
    fun `una leccion aprobada alimenta el mazo con lo que trae ingles y espanol, para manana`() {
        val m = nuevo()
        assertEquals(4, m.alimentar(leccion, "2026-09-16"))
        assertEquals(0, m.alimentar(leccion, "2026-09-16"))         // idempotente
        assertEquals(0, m.cuantosPendientes("2026-09-16"))
        assertEquals(4, m.cuantosPendientes("2026-09-17"))
        assertEquals("He works on Mondays.", m.item("t1l1e3")!!.en)
    }

    @Test
    fun `acertar sube de caja y de escalon segun Leitner, fallar baja a cero`() {
        val m = nuevo()
        m.alimentar(leccion)
        m.registrar("t1l1e1", true, "2026-09-17")
        var it = m.item("t1l1e1")!!
        assertEquals(1, it.caja); assertEquals(1, it.escalon); assertEquals("2026-09-20", it.proximo)   // +3
        m.registrar("t1l1e1", true, "2026-09-20")
        it = m.item("t1l1e1")!!
        assertEquals(2, it.caja); assertEquals("2026-09-27", it.proximo)                              // +7
        m.registrar("t1l1e1", false, "2026-09-27")
        it = m.item("t1l1e1")!!
        assertEquals(0, it.caja); assertEquals(1, it.escalon); assertEquals("2026-09-28", it.proximo)   // mañana, un escalón abajo
        assertFalse(it.aprendido)
        repeat(5) { k -> m.registrar("t1l1e1", true, "2026-10-0${k + 1}") }
        assertTrue(m.item("t1l1e1")!!.aprendido)
        assertEquals(1, m.aprendidos())
    }

    @Test
    fun `la escalera va de elegir a armar, escribir, oir y escribir y decir`() {
        val m = nuevo()
        m.alimentar(leccion)
        val todos = m.todos()
        val it = m.item("t1l1e2")!!
        val rnd = Random(1)
        assertTrue(Repaso.ejercicioDe(it.copy(escalon = 0), todos, null, rnd) is Exercise.TranslateChoose)
        val armar = Repaso.ejercicioDe(it.copy(escalon = 1), todos, null, rnd) as Exercise.BuildSentence
        assertEquals("My brother studies English.", armar.answer)
        assertTrue(armar.extraWords.none { normalizeAnswer(it) in armar.answer.lowercase().split(" ").map { w -> normalizeAnswer(w) } })
        val escribir = Repaso.ejercicioDe(it.copy(escalon = 2), todos, Course.exerciseById("nope") ?: leccion.exercises[1], rnd) as Exercise.WriteIt
        assertEquals(listOf("My brother studies English every day."), escribir.accept)   // conserva las alternativas del original
        // un translate o un build original también entregan su accept al escribir y al armar (auditoría del 16-09)
        val t = Exercise.TranslateChoose("t1l1e1", "Ella trabaja en un hospital.", listOf("She works in a hospital.", "She work in a hospital."), "She works in a hospital.", accept = listOf("She works at a hospital."))
        val it1 = m.item("t1l1e1")!!
        assertEquals(listOf("She works at a hospital."), (Repaso.ejercicioDe(it1.copy(escalon = 2), todos, t, rnd) as Exercise.WriteIt).accept)
        assertEquals(listOf("She works at a hospital."), (Repaso.ejercicioDe(it1.copy(escalon = 1), todos, t, rnd) as Exercise.BuildSentence).accept)
        val b = Exercise.BuildSentence("t1l1e1", "Ella trabaja en un hospital.", "She works in a hospital.", listOf("at"), accept = listOf("She works at a hospital."))
        assertEquals(listOf("She works at a hospital."), (Repaso.ejercicioDe(it1.copy(escalon = 3), todos, b, rnd) as Exercise.TypeWhatYouHear).accept)
        assertTrue(Repaso.ejercicioDe(it.copy(escalon = 3), todos, null, rnd) is Exercise.TypeWhatYouHear)
        val decir = Repaso.ejercicioDe(it.copy(escalon = 4), todos, null, rnd) as Exercise.SpeakIt
        assertEquals(Sound.GENERAL, decir.sound)
        // El elegir trae la respuesta entre las opciones y ningún señuelo igual.
        val elegir = Repaso.ejercicioDe(it.copy(escalon = 0), todos, null, rnd) as Exercise.TranslateChoose
        assertTrue(elegir.options.contains(elegir.answer))
        assertEquals(elegir.options.size, elegir.options.toSet().size)
    }

    @Test
    fun `corrige tu propio error solo con escritos de dias anteriores y se retira a las dos`() {
        val m = nuevo()
        val hoy = "2026-09-16"
        val fallos = listOf(
            Progreso.Fallo("2026-09-12", "a1u4l1", "escribir", "My brother study English", "My brother studies English.", "t1l1e2"),
            Progreso.Fallo("2026-09-13", "a1u4l1", "traducir", "He work on Mondays.", "He works on Mondays.", "t1l1e1"),   // elegido, no escrito
            Progreso.Fallo("2026-09-16", "a1u4l1", "dictado", "fourt", "four", "x"),                                        // hoy: todavía no
            Progreso.Fallo("2026-09-14", "a1u4l1", "completar", "work", "He works on Mondays.", "t1l1e3")
        )
        val buscar: (String) -> Exercise? = { id -> leccion.exercises.firstOrNull { it.id == id } }
        val fixes = Repaso.propiosErrores(fallos, m, hoy, buscar = buscar)
        assertEquals(2, fixes.size)
        val hueco = fixes.first { it.hueco != null }
        assertEquals("works", hueco.answer)
        assertEquals("work", hueco.tuya)
        val escrito = fixes.first { it.hueco == null }
        assertEquals("My brother studies English.", escrito.answer)
        assertEquals("12 de septiembre", Repaso.fechaLarga(escrito.fecha))
        // Dos veces bien y se retira.
        m.registrarCorreccion(Repaso.claveDe(escrito), true)
        assertEquals(2, Repaso.propiosErrores(fallos, m, hoy, buscar = buscar).size)
        m.registrarCorreccion(Repaso.claveDe(escrito), true)
        assertEquals(1, Repaso.propiosErrores(fallos, m, hoy, buscar = buscar).size)
        // Un fallo en medio reinicia la cuenta.
        m.registrarCorreccion(Repaso.claveDe(hueco), true)
        m.registrarCorreccion(Repaso.claveDe(hueco), false)
        assertEquals(1, Repaso.propiosErrores(fallos, m, hoy, buscar = buscar).size)
        // Si el ejercicio cambió de contenido después del fallo (parche de la auditoría), el error viejo no se muestra.
        val cambiado: (String) -> Exercise? = { id ->
            if (id == "t1l1e3") Exercise.Cloze("t1l1e3", "He ___ on Mondays.", "goes", "Él va los lunes.") else buscar(id)
        }
        assertEquals(0, Repaso.propiosErrores(fallos, m, hoy, buscar = cambiado).size)
    }

    @Test
    fun `si el contenido cambia el mazo toma ingles y espanol del curso por id y conserva caja y escalon`() {
        val m = nuevo()
        m.alimentar(leccion, "2026-09-16")
        m.registrar("t1l1e2", true, "2026-09-17")
        val antes = m.item("t1l1e2")!!
        // t1l1e2 cambia de frase; t1l1e3 pasa a ser un ejercicio sin español (se retira); t1l1e4 se queda igual
        val nuevo: (String) -> Exercise? = { id ->
            when (id) {
                "t1l1e2" -> Exercise.WriteIt("t1l1e2", "Mi hermano aprende inglés.", "My brother learns English.")
                "t1l1e3" -> Exercise.SpeakIt("t1l1e3", "He works on Mondays.", Sound.GENERAL)
                else -> leccion.exercises.firstOrNull { it.id == id }
            }
        }
        assertEquals(2, m.refrescar(nuevo))
        val despues = m.item("t1l1e2")!!
        assertEquals("My brother learns English.", despues.en)
        assertEquals("Mi hermano aprende inglés.", despues.es)
        assertEquals(antes.caja, despues.caja)
        assertEquals(antes.escalon, despues.escalon)
        assertEquals(antes.proximo, despues.proximo)
        assertNull(m.item("t1l1e3"))
        assertEquals("I don't have a car.", m.item("t1l1e4")!!.en)
        assertEquals(0, m.refrescar(nuevo))   // idempotente
        // si la frase nueva ya está en el mazo con otro id, el ítem se retira en vez de duplicarse
        val duplica: (String) -> Exercise? = { id -> if (id == "t1l1e2") Exercise.WriteIt("t1l1e2", "No tengo carro.", "I don't have a car.") else nuevo(id) }
        assertEquals(1, m.refrescar(duplica))
        assertNull(m.item("t1l1e2"))
        assertEquals(1, m.todos().count { it.id == "t1l1e4" })
    }

    @Test
    fun `marcas del contrarreloj y de aguanta`() {
        val m = nuevo()
        val a = m.registrarMarca("b1", 30, 6)!!
        assertEquals(5.0, a.porPareja, 0.001)
        val b = m.registrarMarca("b1", 24, 6)!!
        assertEquals(4.0, b.porPareja, 0.001)
        val c = m.registrarMarca("b1", 40, 6)!!
        assertEquals(4.0, c.porPareja, 0.001)                 // la mejor sigue siendo la de 24 s
        assertEquals(3, m.marcas("b1").size)
        assertTrue(m.registrarAguanta(11))
        assertFalse(m.registrarAguanta(9))
        assertEquals(11, m.marcaAguanta())
    }

    @Test
    fun `adivina antes de ver saca hasta tres frases distintas de la leccion`() {
        val ad = Repaso.adivinanzas(leccion, rnd = Random(3))
        assertEquals(3, ad.size)
        assertEquals(3, ad.map { normalizeAnswer(it.answer) }.toSet().size)
        assertTrue(ad.all { it.es.isNotBlank() })
    }

    @Test
    fun `la misma frase con otra puntuacion no entra dos veces y el mismo espanol da alternativas`() {
        val m = nuevo()
        val l = Lesson(
            "t2l1", "Dos", Theory("t", "b", "x"),
            listOf(
                Exercise.TranslateChoose("t2l1e1", "Me llamo Fernando.", listOf("My name is Fernando", "I am Fernando"), "My name is Fernando"),
                Exercise.WriteIt("t2l1e2", "Me llamo Fernando.", "My name is Fernando."),          // solo cambia el punto: no entra
                Exercise.TranslateChoose("t2l1e3", "Trabajo en un banco.", listOf("I work at a bank", "I work a bank"), "I work at a bank"),
                Exercise.WriteIt("t2l1e4", "Trabajo en un banco.", "I work in a bank."),           // mismo español, otro inglés: entra
                Exercise.WriteIt("t2l1e5", "Tengo hambre.", "I am hungry.")
            )
        )
        assertEquals(4, m.alimentar(l, "2026-09-16"))
        val todos = m.todos()
        val banco = m.item("t2l1e3")!!
        // Escribir: la otra traducción del mismo español también vale.
        val escribir = Repaso.ejercicioDe(banco.copy(escalon = 2), todos, null, Random(1)) as Exercise.WriteIt
        assertTrue(escribir.accept.contains("I work in a bank."))
        val oir = Repaso.ejercicioDe(banco.copy(escalon = 3), todos, null, Random(1)) as Exercise.TypeWhatYouHear
        assertTrue(oir.accept.contains("I work in a bank."))
        // Elegir: el gemelo nunca es señuelo.
        val elegir = Repaso.ejercicioDe(banco.copy(escalon = 0), todos, null, Random(1)) as Exercise.TranslateChoose
        assertFalse(elegir.options.contains("I work in a bank."))
        // Armar: "I am" no sale como señuelo de "I am hungry." (ni "I'm").
        val hambre = m.item("t2l1e5")!!
        val armar = Repaso.ejercicioDe(hambre.copy(escalon = 1), todos, null, Random(1)) as Exercise.BuildSentence
        assertTrue(armar.extraWords.none { normalizeAnswer(it) in setOf("i", "am", "i'm", "hungry") })
    }

    @Test
    fun `aprendido exige haberlo dicho y las marcas cortas no cuentan`() {
        val m = nuevo()
        m.alimentar(leccion, "2026-09-16")
        repeat(4) { k -> m.registrar("t1l1e1", true, "2026-09-2$k") }
        assertEquals(4, m.item("t1l1e1")!!.caja)
        assertFalse(m.item("t1l1e1")!!.aprendido)            // caja 4 pero todavía no lo dijo
        m.registrar("t1l1e1", true, "2026-10-30")             // acierto estando en escalón 4: dicho
        assertTrue(m.item("t1l1e1")!!.aprendido)
        assertNull(m.registrarMarca("b", 3, 2))               // ronda corta: no es marca
        assertEquals(0, m.marcas("b").size)
        assertEquals(5.0, m.registrarMarca("b", 30, 6)!!.porPareja, 0.001)
        // Un error del cuaderno se retira por ejercicio, no por día.
        m.registrarCorreccion(m.claveFallo("a1u4l1e10", "2026-09-12", "x"), true)
        m.registrarCorreccion(m.claveFallo("a1u4l1e10", "2026-09-14", "y"), true)
        assertTrue(m.corregido(m.claveFallo("a1u4l1e10", "2026-09-15", "z")))
    }

    @Test
    fun `elegir usa los senuelos del ejercicio original y si no los que comparten palabras`() {
        val m = nuevo()
        val l = Lesson(
            "t3l1", "Tres", Theory("t", "b", "x"),
            listOf(
                Exercise.TranslateChoose("t3l1e1", "Él trabaja los lunes.", listOf("He works on Mondays.", "He work on Mondays.", "He works in Mondays."), "He works on Mondays."),
                Exercise.WriteIt("t3l1e2", "Ella trabaja en un hospital.", "She works in a hospital."),
                Exercise.WriteIt("t3l1e3", "Buenos días.", "Good morning."),
                Exercise.WriteIt("t3l1e4", "Hasta mañana.", "See you tomorrow.")
            )
        )
        m.alimentar(l, "2026-09-16")
        val todos = m.todos()
        // Con original: sus propias opciones, hechas a mano.
        val conOriginal = Repaso.ejercicioDe(m.item("t3l1e1")!!, todos, l.exercises[0], Random(1)) as Exercise.TranslateChoose
        assertTrue(conOriginal.options.containsAll(listOf("He work on Mondays.", "He works in Mondays.", "He works on Mondays.")))
        // Sin original: el que comparte palabras ("works") antes que el del largo parecido ("See you tomorrow.").
        val sinOriginal = Repaso.ejercicioDe(m.item("t3l1e2")!!, todos, null, Random(1)) as Exercise.TranslateChoose
        assertTrue(sinOriginal.options.contains("He works on Mondays."))
        assertEquals(3, sinOriginal.options.size)
    }

    @Test
    fun `lo guardado se vuelve a leer igual`() {
        val f = Files.createTempFile("mazo", ".json").toFile().also { it.delete() }
        val m = Mazo(f)
        m.alimentar(leccion)
        m.registrar("t1l1e1", true, "2026-09-17")
        m.registrarMarca("b1", 30, 6)
        val m2 = Mazo(f)
        assertEquals(4, m2.total())
        assertEquals(1, m2.item("t1l1e1")!!.caja)
        assertEquals(1, m2.marcas("b1").size)
    }
}
