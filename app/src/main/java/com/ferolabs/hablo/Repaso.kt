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
        val accept = when (original) {
            is Exercise.WriteIt -> original.accept
            is Exercise.Cloze -> original.accept.map { original.before + it + original.after }
            else -> emptyList()
        }
        val ajenos = otros.filter { it.id != item.id && !it.en.equals(item.en, ignoreCase = true) }
        return when (item.escalon) {
            0 -> {
                // Elegir: dos señuelos de otros ítems, los de largo más parecido.
                val senuelos = ajenos.sortedBy { kotlin.math.abs(it.en.length - item.en.length) }
                    .map { it.en }.distinct().take(2)
                if (senuelos.size < 1) Exercise.WriteIt(item.id, item.es, item.en, accept)
                else Exercise.TranslateChoose(item.id, item.es, (senuelos + item.en).shuffled(rnd), item.en)
            }
            1 -> {
                // Armar: dos o tres palabras de otro ítem que no estén en la frase.
                val propias = item.en.split(" ").map { normalizeAnswer(it) }.toSet()
                val extra = ajenos.shuffled(rnd).flatMap { it.en.split(" ") }
                    .filter { normalizeAnswer(it) !in propias && it.isNotBlank() }
                    .distinctBy { normalizeAnswer(it) }.take(3)
                Exercise.BuildSentence(item.id, item.es, item.en, extra)
            }
            2 -> Exercise.WriteIt(item.id, item.es, item.en, accept)
            3 -> Exercise.TypeWhatYouHear(item.id, item.en, item.es)
            else -> Exercise.SpeakIt(item.id, item.en, Sound.GENERAL)   // decir: sin reloj, sin GOP
        }
    }

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
            if (mazo.corregido(clave) || !vistos.add(f.ejercicio)) continue
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
