package com.ferolabs.hablo

import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.random.Random

/**
 * Arma las sesiones de la etapa 3 a partir de lo que ya existe: el mazo
 * ([Mazo]), el cuaderno ([Progreso]) y las lecciones terminadas. Todo sale
 * como una [Lesson] sintética que corre en la misma pantalla de lección, en el
 * modo que toque ([ModoLeccion]). Kotlin puro, con tests.
 */
object Repaso {

    const val ID_REPASO = "repaso"
    const val ID_AGUANTA = "aguanta"
    private val TEORIA = Theory("Repaso", "Lo que ya viste, de vuelta.", "")

    // ------------------------------------------------------------ el mazo

    /** El ejercicio de un ítem en su escalón. [otros] dan opciones y señuelos; [original] las alternativas válidas. */
    fun ejercicioDe(item: Mazo.Item, otros: List<Mazo.Item>, original: Exercise?, rnd: Random = Random.Default): Exercise {
        val enClave = Correccion.sueltaEstricta(item.en)
        val esClave = normalizeAnswer(item.es)
        // Otro ítem con el MISMO español ("Trabajo en un banco." → "I work at a bank" / "I work in a bank."):
        // su inglés también vale, y nunca sirve de señuelo.
        val gemelos = otros.filter { it.id != item.id && normalizeAnswer(it.es) == esClave && Correccion.sueltaEstricta(it.en) != enClave }
        val accept = (when (original) {
            is Exercise.WriteIt -> original.accept
            is Exercise.Cloze -> original.accept.map { original.before + it + original.after }
            else -> emptyList()
        } + gemelos.map { it.en }).distinctBy { Correccion.sueltaEstricta(it) }.filter { Correccion.sueltaEstricta(it) != enClave }
        val ajenos = otros.filter {
            it.id != item.id && Correccion.sueltaEstricta(it.en) != enClave && normalizeAnswer(it.es) != esClave
        }
        return when (item.escalon) {
            0 -> {
                // Elegir. Primero los señuelos del ejercicio ORIGINAL (listen/translate): están
                // hechos y revisados a mano para esa frase ("He work on Mondays" contra "He works
                // on Mondays"). Sin original, otros ítems que COMPARTAN PALABRAS con la frase:
                // parecido de contenido, no de largo (largo parecido no es significado parecido;
                // por eso "las incorrectas se notan mucho", Fero, 2026-09-16).
                val propios = when (original) {
                    is Exercise.TranslateChoose -> original.options
                    is Exercise.ListenChoose -> original.options
                    else -> emptyList()
                }.filter { Correccion.sueltaEstricta(it) != enClave }.distinctBy { Correccion.sueltaEstricta(it) }
                val senuelos = if (propios.size >= 2) propios.take(3) else {
                    val palabras = palabrasDe(item.en)
                    // Los que comparten más palabras primero; si nadie comparte nada, los de
                    // largo parecido para no quedarse sin opciones.
                    val parecidos = ajenos.map { it to compartidas(palabras, palabrasDe(it.en)) }
                        .sortedWith(compareByDescending<Pair<Mazo.Item, Int>> { it.second }.thenBy { kotlin.math.abs(it.first.en.length - item.en.length) })
                        .map { it.first.en }
                    (propios + parecidos).distinctBy { Correccion.sueltaEstricta(it) }.take(2)
                }
                if (senuelos.size < 1) Exercise.WriteIt(item.id, item.es, item.en, accept)
                else Exercise.TranslateChoose(item.id, item.es, (senuelos + item.en).shuffled(rnd), item.en)
            }
            1 -> {
                // Armar: dos o tres palabras de otro ítem que no estén en la frase (ni como
                // contracción ni como forma larga: "I'm" no es señuelo de "I am").
                val propias = Correccion.suelta(item.en).split(" ").toSet() + item.en.split(" ").map { normalizeAnswer(it) }
                val extra = ajenos.shuffled(rnd).flatMap { it.en.split(" ") }
                    .filter { w -> w.isNotBlank() && normalizeAnswer(w) !in propias && Correccion.suelta(w).split(" ").none { it in propias } }
                    .distinctBy { normalizeAnswer(it) }.take(3)
                Exercise.BuildSentence(item.id, item.es, item.en, extra)
            }
            2 -> Exercise.WriteIt(item.id, item.es, item.en, accept)
            3 -> Exercise.TypeWhatYouHear(item.id, item.en, item.es, accept = accept)
            else -> Exercise.SpeakIt(item.id, item.en, Sound.GENERAL)   // decir: sin reloj, sin GOP
        }
    }

    private val VACIAS = setOf("i", "you", "he", "she", "it", "we", "they", "a", "an", "the", "to", "of", "in", "on", "at", "is", "are", "am", "do", "does", "my", "your", "and", "for", "with", "this", "that", "not", "me", "us")

    /** Palabras con contenido de una frase (sin artículos, pronombres ni auxiliares). */
    private fun palabrasDe(en: String): Set<String> =
        Correccion.sueltaEstricta(en).split(" ").filter { it.isNotBlank() && it !in VACIAS }.toSet()

    private fun compartidas(a: Set<String>, b: Set<String>): Int = a.count { it in b }

    // ------------------------------------------- corrige tu propio error

    /**
     * Fallos del cuaderno que él escribió (no eligió ni dijo), de días
     * anteriores, todavía no corregidos dos veces por él mismo. Como mucho [max].
     */
    fun propiosErrores(
        fallos: List<Progreso.Fallo>, mazo: Mazo, hoy: String, max: Int = 3,
        buscar: (String) -> Exercise? = { Course.exerciseById(it) }
    ): List<Exercise.FixIt> {
        val out = ArrayList<Exercise.FixIt>()
        val vistos = HashSet<String>()
        for (f in fallos.asReversed()) {
            if (f.tipo !in TIPOS_ESCRITOS) continue
            if (f.fecha >= hoy) continue                       // hoy no: "semanas atrás"
            if (f.tuya.isBlank() || f.correcta.isBlank()) continue
            if (Correccion.acepta(f.tuya, f.correcta, emptyList())) continue   // no era error
            val clave = mazo.claveFallo(f.ejercicio, f.fecha, f.tuya)
            if (mazo.corregido(clave) || !vistos.add(clave)) continue
            val orig = if (f.ejercicio.isNotBlank()) buscar(f.ejercicio) else null
            val fix = when (orig) {
                is Exercise.Cloze -> Exercise.FixIt(
                    id = "fix|$clave", tuya = f.tuya.trim(), fecha = f.fecha, answer = orig.answer,
                    accept = orig.accept, hueco = orig, tip = orig.tip
                )
                is Exercise.WriteIt -> Exercise.FixIt("fix|$clave", f.tuya.trim(), f.fecha, orig.answer, orig.accept, tip = orig.tip)
                is Exercise.TypeWhatYouHear -> Exercise.FixIt("fix|$clave", f.tuya.trim(), f.fecha, orig.audio, tip = orig.tip)
                else -> Exercise.FixIt("fix|$clave", f.tuya.trim(), f.fecha, f.correcta)
            }
            out.add(fix)
            if (out.size >= max) break
        }
        return out
    }

    /** La clave del fallo que hay dentro del id de un [Exercise.FixIt]. */
    fun claveDe(fix: Exercise.FixIt): String = fix.id.removePrefix("fix|")

    // ---------------------------------------------------------- sesiones

    /** La sesión de hoy: los ítems que tocan, cada uno en su escalón, más hasta tres errores propios. */
    fun leccionDeHoy(mazo: Mazo, fallos: List<Progreso.Fallo>, hoy: String = mazo.hoy(), rnd: Random = Random.Default): Lesson {
        val pendientes = mazo.pendientes(hoy)
        val todos = mazo.todos()
        val ejercicios = ArrayList<Exercise>()
        for (it in pendientes) ejercicios.add(ejercicioDe(it, todos, Course.exerciseById(it.id), rnd))
        ejercicios.addAll(propiosErrores(fallos, mazo, hoy))
        return Lesson(ID_REPASO, "Repaso de hoy", TEORIA, ejercicios)
    }

    /**
     * "Aguanta": una ronda con todo lo visto, mezclado, sin reloj y sin puntos;
     * la pantalla la corta al tercer error. Salen los ejercicios de las
     * lecciones aprobadas (hasta [max]); si hay mazo, sus ítems entran en el
     * escalón que tengan (así también repasa produciendo).
     */
    fun leccionAguanta(lessonsHechas: List<Lesson>, mazo: Mazo, max: Int = 60, rnd: Random = Random.Default): Lesson {
        val pool = ArrayList<Exercise>()
        for (l in lessonsHechas) for (ex in l.exercises) {
            if (ex is Exercise.Shadow) continue   // el ritmo no se juzga; aquí se juzga
            pool.add(ex)
        }
        val todos = mazo.todos()
        for (it in todos) if (it.escalon > 0) pool.add(ejercicioDe(it, todos, Course.exerciseById(it.id), rnd))
        val vistos = HashSet<String>()
        val elegidos = pool.shuffled(rnd).filter { vistos.add(it.id) }.take(max)
        return Lesson(ID_AGUANTA, "Aguanta", TEORIA, elegidos)
    }

    // ------------------------------------------------------- adivina antes

    /**
     * "Adivina antes de ver": hasta [max] frases de una lección nueva para
     * intentar adivinarlas ANTES de estudiarlas. Va a fallar: ese es el punto
     * (intentar y errar con retroalimentación deja mejor recuerdo que estudiar
     * la respuesta directa: Kornell, Hays & Bjork 2009; Richland, Kornell &
     * Kao 2009). No puntúa ni va al cuaderno.
     */
    fun adivinanzas(lesson: Lesson, max: Int = 3, rnd: Random = Random.Default): List<Exercise.WriteIt> {
        val candidatas = lesson.exercises.mapNotNull { ex ->
            when (ex) {
                is Exercise.WriteIt -> Exercise.WriteIt(ex.id, ex.es, ex.answer, ex.accept)
                is Exercise.BuildSentence -> Exercise.WriteIt(ex.id, ex.es, ex.answer)
                is Exercise.TranslateChoose -> Exercise.WriteIt(ex.id, ex.es, ex.answer)
                is Exercise.Cloze -> Exercise.WriteIt(ex.id, ex.es, ex.full, ex.accept.map { ex.before + it + ex.after })
                is Exercise.TypeWhatYouHear -> Exercise.WriteIt(ex.id, ex.meaningEs, ex.audio)
                else -> null
            }
        }
        val vistas = HashSet<String>()
        return candidatas.shuffled(rnd).filter { vistas.add(normalizeAnswer(it.answer)) }.take(max)
    }

    /** "12 de septiembre" a partir de yyyy-MM-dd. */
    fun fechaLarga(fecha: String): String = try {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(fecha)!!
        SimpleDateFormat("d 'de' MMMM", Locale("es", "CO")).format(d)
    } catch (e: Throwable) {
        fecha
    }

    private val TIPOS_ESCRITOS = setOf("escribir", "completar", "dictado")
}

/** En qué modo corre la pantalla de lección. */
enum class ModoLeccion {
    /** Una lección del curso: nota, Parejas a mitad, adivina antes de ver la primera vez. */
    LECCION,
    /** El repaso de hoy: cada acierto o fallo va al mazo; sin Parejas ni adivinanzas. */
    REPASO,
    /** Aguanta: se corta al tercer error; sin puntos, solo "llegaste a N". */
    AGUANTA
}
