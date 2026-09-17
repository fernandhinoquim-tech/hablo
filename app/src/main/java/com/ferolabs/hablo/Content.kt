package com.ferolabs.hablo

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sonido que entrena un ejercicio de hablar. Es **obligatorio** en el
 * contenido: decide qué jurado puntúa la frase (ver CLAUDE.md, "jurado por
 * sonido"). Si un ejercicio queda sin etiqueta, el jurado no dispara y nadie
 * se entera; por eso la carga falla ruidosamente si falta o no se reconoce.
 * `general` es para frases que no trabajan un sonido en particular.
 */
enum class Sound(val key: String, val labelEs: String) {
    SH("sh", "la sh (ship, fish)"),
    TH("th", "el sonido th"),
    H("h", "la h aspirada"),
    V("v", "v contra b"),
    ED("ed", "terminación -ed"),
    FINAL("final", "consonantes al final"),
    ES("es", "s inicial sin e"),
    RL("rl", "la r y la l"),
    GENERAL("general", "la frase completa");

    companion object {
        fun parse(key: String): Sound? = entries.firstOrNull { it.key == key }
        val keys: String get() = entries.joinToString(", ") { it.key }
    }
}

/**
 * Tipos de ejercicio.
 *
 * El contenido vive en assets/content/curriculum.json, no en el código: así se
 * pueden agregar cientos de lecciones sin tocar Kotlin ni recompilar la lógica.
 */
sealed class Exercise {
    /**
     * Único en todo el curso, formato `<lección>e<n>` (p. ej. `a1u1l1e3`).
     * Es la clave del mazo de repaso, del cuaderno de errores y del informe:
     * sin id, lo que se falla no se puede volver a preguntar mañana.
     */
    abstract val id: String
    abstract val tip: String?

    /**
     * Escuchas una frase y eliges cuál fue. [answer] es el TEXTO de la opción
     * correcta, no su posición: así se verifica solo al cargar (tiene que
     * estar en [options]) y sobrevive a que las opciones se barajen.
     */
    data class ListenChoose(
        override val id: String,
        val audio: String,
        val options: List<String>,
        val answer: String,
        override val tip: String? = null
    ) : Exercise() {
        val answerIndex: Int get() = options.indexOf(answer)
    }

    /**
     * Ves español y eliges la traducción correcta. Mismo contrato que [ListenChoose].
     * [accept]: otras formas válidas de la respuesta; aquí no se usan (se elige),
     * pero el mazo, Aguanta y "adivina antes de ver" convierten este ejercicio
     * en "escribir" y sin ellas marcaban mal "I'm 25" o "That's it" (auditoría
     * de Cowork, 2026-09-16).
     */
    data class TranslateChoose(
        override val id: String,
        val es: String,
        val options: List<String>,
        val answer: String,
        override val tip: String? = null,
        val accept: List<String> = emptyList()
    ) : Exercise() {
        val answerIndex: Int get() = options.indexOf(answer)
    }

    /**
     * Armas la frase tocando palabras. [accept]: otros órdenes correctos con las
     * mismas fichas (a2u2l4e6, a2u4l3e7, a2u5l1e6 arman otra frase válida) y las
     * formas que valen cuando el mazo lo convierte en "escribir".
     */
    data class BuildSentence(
        override val id: String,
        val es: String,
        val answer: String,
        val extraWords: List<String> = emptyList(),
        override val tip: String? = null,
        val accept: List<String> = emptyList()
    ) : Exercise()

    /** Escribes lo que escuchaste. */
    data class TypeWhatYouHear(
        override val id: String,
        val audio: String,
        val meaningEs: String,
        override val tip: String? = null,
        /** Otras formas válidas (el mazo las pone: la otra traducción del mismo español). */
        val accept: List<String> = emptyList()
    ) : Exercise()

    /** Lo dices en voz alta y se te puntúa palabra por palabra. */
    data class SpeakIt(
        override val id: String,
        val text: String,
        val sound: Sound,
        override val tip: String? = null
    ) : Exercise()

    /**
     * Ves el español y ESCRIBES el inglés, sin opciones ni fichas: recuperación
     * productiva pura (d = 1,38 sobre elegir entre opciones en pruebas de
     * producción, y empata en las receptivas: KATE Journal 30). [accept] son
     * las otras respuestas válidas; marcar mal una buena es el peor fallo.
     */
    data class WriteIt(
        override val id: String,
        val es: String,
        val answer: String,
        val accept: List<String> = emptyList(),
        override val tip: String? = null
    ) : Exercise()

    /**
     * Frase en inglés con un hueco `___` que se ESCRIBE (no se elige): apunta
     * al punto de gramática exacto de la lección.
     */
    data class Cloze(
        override val id: String,
        val text: String,
        val answer: String,
        val es: String,
        val accept: List<String> = emptyList(),
        override val tip: String? = null
    ) : Exercise() {
        val before: String get() = text.substringBefore(BLANK)
        val after: String get() = text.substringAfter(BLANK)
        /** La frase completa con la respuesta puesta. */
        val full: String get() = text.replace(BLANK, answer)

        companion object {
            const val BLANK = "___"
        }
    }

    /**
     * La profesora dice la frase y el alumno la repite enseguida, siguiendo el
     * ritmo. Se mide que salgan las palabras y cuánto tardó él contra ella;
     * NUNCA por fonema: la revisión sistemática de shadowing (2025) lo da
     * probado para fluidez y prosodia e inconcluso para sonidos sueltos.
     */
    data class Shadow(
        override val id: String,
        val text: String,
        override val tip: String? = null
    ) : Exercise()

    /**
     * Pares mínimos: suena UNA palabra y se identifica cuál fue (ship/sheep).
     * Identificar (g = 0,95) rinde casi el doble que "¿son iguales?" (g = 0,57),
     * Uchihara, Karas & Thomson 2025. La corrección es exacta: la app sabe
     * qué palabra sintetizó. [sentence] es opcional, para oírla en contexto.
     */
    /**
     * "Corrige tu propio error" (etapa 3): una frase que ÉL escribió mal en el
     * cuaderno, semanas atrás, devuelta para que la arregle. Nunca viene del
     * JSON: la arma Repaso.kt desde progreso.json. Solo errores reales.
     */
    data class FixIt(
        override val id: String,
        /** Lo que él escribió. */
        val tuya: String,
        /** Cuándo (yyyy-MM-dd). */
        val fecha: String,
        val answer: String,
        val accept: List<String> = emptyList(),
        /** Si el ejercicio original era un hueco: la frase con ___, para mostrarla y aceptar solo el hueco. */
        val hueco: Cloze? = null,
        override val tip: String? = null
    ) : Exercise()

    data class MinimalPair(
        override val id: String,
        val options: List<String>,
        val answer: String,
        val sentence: String? = null,
        /**
         * `"play": "sentence"`: suena la frase entera en vez de "The word is X.".
         * Medido el 2026-09-15 (tools/content/oir_pares.py, 4 voces x 3 corridas):
         * en la portadora Piper no saca el fonema de three (7/12), think (2/12),
         * since (3/12) ni van (5/12); dentro de su frase sí (10, 10, 11 y 9 de 12).
         */
        val playSentence: Boolean = false,
        override val tip: String? = null
    ) : Exercise() {
        val answerIndex: Int get() = options.indexOf(answer)
        /** Lo que de verdad suena al tocar el altavoz. */
        val spoken: String get() = if (playSentence && sentence != null) sentence else "The word is $answer."
    }
}

/**
 * La ficha de teoría de una lección: qué se usa y cómo, en español, corto.
 * [trap] es el sello de la casa: el error concreto que comete quien piensa en
 * español, y por qué. Obligatoria en toda lección (el parser y `checkContent`
 * revientan si falta un campo): con 200 lecciones, una sin explicación es una
 * lección a ciegas.
 */
data class Theory(
    val title: String,
    /** Puede llevar **negrita** con dobles asteriscos. */
    val body: String,
    val trap: String
)

/**
 * Escenario de conversación con la IA (Fase 3): una situación cerrada donde
 * la profesora hace un papel y el alumno tiene metas. Vive en scenarios.json.
 */
/**
 * Una ayuda de gramática para la conversación: el patrón en inglés y, en
 * español, CUÁNDO se usa y qué trampa tiene. Nunca vocabulario: Fero decidió
 * el 2026-09-13 que las palabras las pone él ("se supone que el vocabulario
 * lo debo llevar"); la app pone la estructura.
 */
data class Ayuda(val en: String, val es: String)

data class Scenario(
    val id: String,
    val title: String,
    val emoji: String,
    val level: String,
    val goalEs: String,
    /** Papel de la profesora, en inglés, para el system prompt. */
    val role: String,
    /** Primera frase de la profesora; {teacher} se reemplaza por su nombre. */
    val opening: String,
    /** Frases que el alumno debería intentar usar. */
    val targets: List<String>,
    /** Cómo preguntar y cómo responder en esta situación. */
    val help: List<Ayuda>,
    /** Trampas típicas del hispanohablante en esta situación, en español. */
    val watch: List<String>
) {
    companion object {
        /**
         * La charla libre ("Hablar de todo"): no vive en scenarios.json porque no
         * es un escenario (sin papel, sin nivel, sin metas) y es la única con
         * memoria entre charlas (etapa 2, 2026-09-16). El prompt lo arma
         * ScreenConversation con la ficha de [Memoria].
         */
        val LIBRE = Scenario(
            id = "libre",
            title = "Hablar de todo",
            emoji = "💬",
            level = "",
            goalEs = "De lo que quieras, sin escenario ni nivel. Ella se acuerda de lo básico entre charlas.",
            role = "",
            opening = "",
            targets = emptyList(),
            help = emptyList(),
            watch = emptyList()
        )
    }
}

/** Un ejercicio de pronunciación suelto: frase objetivo + por qué es difícil. */
data class Drill(
    val text: String,
    val sound: Sound,
    val focusEs: String,
    val tipEs: String
)

data class Lesson(
    val id: String,
    val title: String,
    val theory: Theory,
    val exercises: List<Exercise>
)

data class CourseUnit(
    val id: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val lessons: List<Lesson>
)

data class Level(
    val id: String,
    val title: String,
    val goal: String,
    val units: List<CourseUnit>
)

/**
 * Un banco de vocabulario para el contrarreloj (assets/content/vocabulario.json,
 * etapa 3): parejas palabra/frase en inglés ↔ español. Lo escribe Cowork.
 */
data class Banco(val id: String, val title: String, val level: String, val pares: List<Pareja>)

/** Una pregunta de comprensión de una historia: 3 opciones, [answer] es el texto de la correcta. */
data class Pregunta(val q: String, val options: List<String>, val answer: String)

/**
 * Una historia corta (etapa 4): la profesora la lee y está en pantalla → 3
 * preguntas → el alumno la cuenta de vuelta EN VOZ ALTA con las pistas
 * (el retell no es opcional: sin él es comprensión lectora y se pierde la
 * mitad del efecto) → el glosario pasa al mazo. Vive en historias.json.
 */
data class Historia(
    val id: String,
    val level: String,
    val title: String,
    val titleEs: String,
    val text: List<String>,
    val glosario: List<Pareja>,
    val preguntas: List<Pregunta>,
    val retellPromptEs: String,
    val pistas: List<String>,
    val trap: String
) {
    val texto: String get() = text.joinToString(" ")
}

/** Una tanda de historias (3 a 12: donde el efecto es máximo, d = 2,53). */
data class Tanda(val id: String, val level: String, val title: String, val historias: List<Historia>)

/**
 * Un par mínimo de la pantalla de Oído (assets/content/oido.json, Cowork
 * 17-09): dos palabras que solo cambian en un sonido y una frase NEUTRA con
 * un hueco `___` donde caben las dos, para que el sentido no delate cuál sonó.
 */
data class ParOido(val a: String, val b: String, val frase: String) {
    fun con(palabra: String): String = frase.replace("___", palabra)
}

/**
 * Un bloque de Oído: un contraste (ship/sheep, b/v, la h…), su explicación en
 * español, de 6 a 10 pares y una frase para DECIR al final con el jurado de
 * [sound]: oír la diferencia no garantiza decirla (r = 0,31), por eso cada
 * bloque cierra hablando.
 */
data class BloqueOido(
    val id: String,
    val title: String,
    val level: String,
    val contraste: String,
    val explicacion: String,
    val hablar: String,
    val sound: Sound,
    val pares: List<ParOido>
)

/**
 * Un ítem del dictado de números (assets/content/dictado.json): la tarea
 * literal de Aptis Listening parte 1. [modo] "escribir" (se compara con
 * [answer] y [accept] con la regla ESTRICTA de [Correccion.dictado]: «$6.50»
 * no es «$650») o "elegir" (3 [options], las confusiones clásicas: 13/30,
 * quarter to/past, fecha americana). El [audio] va en palabras para que
 * Piper lo lea como se dice.
 */
data class ItemDictado(
    val id: String,
    val tipo: String,
    val level: String,
    val modo: String,
    val audio: String,
    val preguntaEs: String,
    val answer: String,
    val accept: List<String>,
    val options: List<String>,
    val tip: String
) {
    val escribir: Boolean get() = modo == "escribir"

    companion object {
        /** Los tipos en el orden en que se listan, con su nombre en pantalla. */
        val TIPOS = listOf(
            "numero" to "Números", "telefono" to "Teléfonos", "hora" to "Horas", "fecha" to "Fechas",
            "precio" to "Precios", "deletreo" to "Deletreos", "direccion" to "Direcciones"
        )
        fun nombreTipo(tipo: String): String = TIPOS.firstOrNull { it.first == tipo }?.second ?: tipo
    }
}

/**
 * Carga y guarda el curso. Es un objeto único porque el contenido no cambia
 * durante la sesión: se lee una vez al arrancar la app.
 */
object Course {

    /** Las tandas de historias (assets/content/historias.json; opcional). */
    var tandas: List<Tanda> = emptyList()
        private set

    /** Bancos de vocabulario del contrarreloj (opcional: sin el archivo, la lista queda vacía). */
    var bancos: List<Banco> = emptyList()
        private set

    /** Los bloques de la pantalla de Oído (assets/content/oido.json; opcional). */
    var oido: List<BloqueOido> = emptyList()
        private set

    /** El dictado de números (assets/content/dictado.json; opcional). */
    var dictado: List<ItemDictado> = emptyList()
        private set

    /** Los crucigramas (assets/content/crucigramas.json; opcional). Ver Crucigrama.kt. */
    var crucigramas: List<Crucigrama> = emptyList()
        private set

    /**
     * Fichas de REFERENCIA de la sección Gramática (assets/content/referencia.json;
     * opcional; las escribe Cowork: verbos irregulares, pronombres, números y
     * fechas, in/on/at). Van como lecciones sin ejercicios para reusar FichaScreen.
     */
    var referencia: List<Lesson> = emptyList()
        private set

    /** El Modo Aptis (assets/content/aptis-*.json; sin aptis-pistas.json no hay modo). Ver Aptis.kt. */
    var aptis: BancoAptis? = null
        private set

    var levels: List<Level> = emptyList()
        private set

    /** Los ejercicios de "Practicar pronunciación" (assets/content/drills.json). */
    var drills: List<Drill> = emptyList()
        private set

    /** Los escenarios de conversación (assets/content/scenarios.json). */
    var scenarios: List<Scenario> = emptyList()
        private set

    var loaded by mutableStateOf(false)
        private set

    var loadError by mutableStateOf<String?>(null)
        private set

    fun load(context: Context) {
        if (loaded) return
        try {
            levels = parseCurriculum(readAsset(context, "content/curriculum.json"))
            drills = parseDrills(JSONObject(readAsset(context, "content/drills.json")).getJSONArray("drills"))
            val scenariosJson = JSONObject(readAsset(context, "content/scenarios.json"))
            helpCommon = parseAyudas(scenariosJson.optJSONArray("helpCommon"), "scenarios.json, helpCommon")
            scenarios = parseScenarios(scenariosJson.getJSONArray("scenarios"))
            bancos = try {
                parseBancos(JSONObject(readAsset(context, "content/vocabulario.json")).getJSONArray("bancos"))
            } catch (e: java.io.FileNotFoundException) {
                emptyList()   // todavía no hay banco: el contrarreloj usa las frases de las lecciones
            }
            tandas = try {
                parseTandas(JSONObject(readAsset(context, "content/historias.json")).getJSONArray("tandas"))
            } catch (e: java.io.FileNotFoundException) {
                emptyList()
            }
            oido = try {
                parseOido(JSONObject(readAsset(context, "content/oido.json")).getJSONArray("bloques"))
            } catch (e: java.io.FileNotFoundException) {
                emptyList()
            }
            dictado = try {
                parseDictado(JSONObject(readAsset(context, "content/dictado.json")).getJSONArray("items"))
            } catch (e: java.io.FileNotFoundException) {
                emptyList()
            }
            crucigramas = try {
                parseCrucigramas(JSONObject(readAsset(context, "content/crucigramas.json")).getJSONArray("crucigramas"))
            } catch (e: java.io.FileNotFoundException) {
                emptyList()
            }
            referencia = try {
                parseReferencia(JSONObject(readAsset(context, "content/referencia.json")).getJSONArray("fichas"))
            } catch (e: java.io.FileNotFoundException) {
                emptyList()
            }
            aptis = try {
                parseBancoAptis { nombre ->
                    try { readAsset(context, "content/$nombre") } catch (e: java.io.FileNotFoundException) { null }
                }
            } catch (e: IllegalArgumentException) {
                if (e.message?.startsWith("falta aptis-pistas.json") == true) null else throw e
            }
            loaded = true
            Log.i(TAG, "Curso cargado: ${levels.size} niveles, ${allLessons().size} lecciones, ${drills.size} drills, ${scenarios.size} escenarios")
        } catch (e: Throwable) {
            // Se muestra en la pantalla de inicio. Un contenido mal formado no
            // se esconde: mejor una app que dice "arregla la lección X" que una
            // que puntúa mal en silencio.
            Log.e(TAG, "No se pudo cargar el curso", e)
            loadError = e.message ?: e.javaClass.simpleName
            levels = emptyList()
            drills = emptyList()
            scenarios = emptyList()
            loaded = true
        }
    }

    private fun readAsset(context: Context, path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    fun historiaById(id: String): Historia? =
        tandas.asSequence().flatMap { it.historias.asSequence() }.firstOrNull { it.id == id }

    /** historias.json: tandas de 3 a 12 historias; cada una con texto, glosario, 3 preguntas y retell. */
    fun parseTandas(arr: JSONArray): List<Tanda> {
        val ids = HashSet<String>()
        return (0 until arr.length()).map { t ->
            val to = arr.getJSONObject(t)
            val whereT = "historias.json, tanda ${t + 1}"
            val ha = to.optJSONArray("historias") ?: throw IllegalArgumentException("$whereT: falta \"historias\"")
            if (ha.length() < 3 || ha.length() > 12) throw IllegalArgumentException("$whereT: ${ha.length()} historias (el efecto máximo está entre 3 y 12)")
            val historias = (0 until ha.length()).map { i ->
                val o = ha.getJSONObject(i)
                val where = "$whereT, historia ${i + 1}"
                val id = req(o, "id", where)
                if (!ids.add(id)) throw IllegalArgumentException("$where: id \"$id\" repetido")
                val text = strings(o, "text")
                if (text.size < 6 || text.size > 10) throw IllegalArgumentException("$where: ${text.size} frases (se pidió 6-10)")
                val glosario = (o.optJSONArray("glosario") ?: JSONArray()).let { g ->
                    (0 until g.length()).map { j ->
                        val p = g.getJSONObject(j)
                        val en = p.optString("en").trim(); val es = p.optString("es").trim()
                        if (en.isBlank() || es.isBlank()) throw IllegalArgumentException("$where: glosario ${j + 1} sin \"en\" o sin \"es\"")
                        Pareja(es, en)
                    }
                }
                if (glosario.size < 2) throw IllegalArgumentException("$where: al menos 2 palabras de glosario (van al mazo)")
                val pa = o.optJSONArray("preguntas") ?: JSONArray()
                if (pa.length() != 3) throw IllegalArgumentException("$where: hacen falta 3 preguntas")
                val preguntas = (0 until pa.length()).map { j ->
                    val q = pa.getJSONObject(j)
                    val options = strings(q, "options")
                    val answer = q.optString("answer")
                    checkChoice("$where, pregunta ${j + 1}", options, answer)
                    Pregunta(req(q, "q", "$where, pregunta ${j + 1}"), options, answer)
                }
                val r = o.optJSONObject("retell") ?: throw IllegalArgumentException("$where: falta el retell (es la mitad del efecto)")
                val pistas = strings(r, "pistas")
                if (pistas.size < 2) throw IllegalArgumentException("$where: el retell necesita al menos 2 pistas")
                Historia(
                    id = id, level = req(o, "level", where), title = req(o, "title", where), titleEs = req(o, "titleEs", where),
                    text = text, glosario = glosario, preguntas = preguntas,
                    retellPromptEs = req(r, "prompt_es", "$where, retell"), pistas = pistas, trap = req(o, "trap", where)
                )
            }
            Tanda(req(to, "id", whereT), req(to, "level", whereT), req(to, "title", whereT), historias)
        }
    }

    /**
     * oido.json: bloques con 6-10 pares de palabras SUELTAS distintas y una frase
     * con exactamente un `___`; `sound` como en los drills (regla dura 5: decide
     * el jurado del "hablar" final). Mismas reglas que validar_extra.py de Cowork.
     */
    fun parseOido(arr: JSONArray): List<BloqueOido> {
        val ids = HashSet<String>()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val where = "oido.json, bloque ${i + 1}"
            val id = req(o, "id", where)
            if (!ids.add(id)) throw IllegalArgumentException("$where: id repetido \"$id\"")
            val pa = o.optJSONArray("pares") ?: throw IllegalArgumentException("$where: falta \"pares\"")
            // Cowork pidió 6-10; el mínimo baja a 4 porque la medición con Piper (oir_oido.py) descarta
            // pares y Cowork los repone después: un bloque corto vale más que uno que miente.
            if (pa.length() < 4 || pa.length() > 10) throw IllegalArgumentException("$where: ${pa.length()} pares (se pidió 6-10; mínimo 4 mientras se reponen los descartados)")
            val vistos = HashSet<String>()
            val pares = (0 until pa.length()).map { j ->
                val p = pa.getJSONObject(j)
                val a = p.optString("a").trim(); val b = p.optString("b").trim(); val frase = p.optString("frase").trim()
                if (a.isBlank() || b.isBlank() || a.equals(b, true) || a.contains(' ') || b.contains(' ')) {
                    throw IllegalArgumentException("$where, par ${j + 1}: mal formado (\"$a\" / \"$b\": dos palabras sueltas y distintas)")
                }
                if (!vistos.add(a.lowercase() + "/" + b.lowercase())) throw IllegalArgumentException("$where: par repetido $a/$b")
                if (frase.split("___").size != 2) throw IllegalArgumentException("$where, par $a/$b: la frase necesita exactamente un ___")
                ParOido(a, b, frase)
            }
            BloqueOido(
                id, req(o, "title", where), req(o, "level", where), req(o, "contraste", where),
                req(o, "explicacion", where), req(o, "hablar", where), requireSound(o, where), pares
            )
        }
    }

    /** dictado.json: ítems `escribir` (answer + accept) o `elegir` (3 options distintas con answer entre ellas). */
    fun parseDictado(arr: JSONArray): List<ItemDictado> {
        val ids = HashSet<String>()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val where = "dictado.json, ítem ${i + 1}"
            val id = req(o, "id", where)
            if (!ids.add(id)) throw IllegalArgumentException("$where: id repetido \"$id\"")
            val modo = req(o, "modo", where)
            val audio = req(o, "audio", where)
            if (audio.any { it.isDigit() }) throw IllegalArgumentException("$where: el audio lleva cifras; escríbelo en palabras para que Piper lo lea como se dice")
            val answer = req(o, "answer", where)
            val accept = o.optJSONArray("accept")?.let { a -> (0 until a.length()).map { a.getString(it).trim() } } ?: emptyList()
            val options = o.optJSONArray("options")?.let { a -> (0 until a.length()).map { a.getString(it).trim() } } ?: emptyList()
            when (modo) {
                "escribir" -> {
                    val vistas = hashSetOf(Correccion.dictado(answer))
                    for (a in accept) if (a.isBlank() || !vistas.add(Correccion.dictado(a))) throw IllegalArgumentException("$where: accept repite \"$a\"")
                }
                "elegir" -> {
                    if (options.size != 3 || options.toSet().size != 3) throw IllegalArgumentException("$where: hacen falta 3 opciones distintas")
                    if (answer !in options) throw IllegalArgumentException("$where: answer fuera de options")
                }
                else -> throw IllegalArgumentException("$where: modo \"$modo\" (escribir o elegir)")
            }
            ItemDictado(
                id, req(o, "tipo", where), req(o, "level", where), modo, audio,
                req(o, "pregunta_es", where), answer, accept, options, req(o, "tip", where)
            )
        }
    }

    /**
     * crucigramas.json: rejillas de hasta 10 × 10 con 5-8 palabras de un solo
     * banco. Las mismas reglas que validar_cruci.py de Cowork: ninguna casilla
     * con dos letras distintas, nada fuera de la rejilla, toda corrida de dos o
     * más letras es una palabra de la lista (sin "fantasmas"), y la numeración
     * es la clásica (por orden de lectura de las casillas de inicio).
     */
    fun parseCrucigramas(arr: JSONArray): List<Crucigrama> {
        val ids = HashSet<String>()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val where = "crucigramas.json, crucigrama ${i + 1}"
            val id = req(o, "id", where)
            if (!ids.add(id)) throw IllegalArgumentException("$where: id repetido \"$id\"")
            val filas = o.optInt("filas"); val columnas = o.optInt("columnas")
            if (filas !in 2..10 || columnas !in 2..10) throw IllegalArgumentException("$where: rejilla de $filas × $columnas (como mucho 10 × 10)")
            val pa = o.optJSONArray("palabras") ?: throw IllegalArgumentException("$where: falta \"palabras\"")
            if (pa.length() < 5 || pa.length() > 8) throw IllegalArgumentException("$where: ${pa.length()} palabras (se pidió 5-8)")
            val palabras = (0 until pa.length()).map { j ->
                val p = pa.getJSONObject(j)
                val en = p.optString("en").trim(); val pista = p.optString("pista").trim()
                val dir = p.optString("dir").trim().uppercase()
                if (en.isBlank() || pista.isBlank() || dir !in listOf("H", "V")) throw IllegalArgumentException("$where, palabra ${j + 1}: mal formada")
                if (!en.all { it.isLetter() }) throw IllegalArgumentException("$where: «$en» tiene algo que no es letra")
                PalabraCruci(p.optInt("numero"), dir[0], p.optInt("fila", -1), p.optInt("col", -1), en, pista)
            }
            val cruci = Crucigrama(id, req(o, "title", where), req(o, "level", where), req(o, "banco", where), filas, columnas, palabras)
            val rejilla = HashMap<Celda, Char>()
            val inicios = HashMap<Celda, Int>()
            for (p in palabras) {
                p.celdas.forEachIndexed { k, c ->
                    if (c.fila !in 0 until filas || c.col !in 0 until columnas) throw IllegalArgumentException("$where: «${p.en}» se sale de la rejilla")
                    val ch = p.en[k].lowercaseChar()
                    val previa = rejilla[c]
                    if (previa != null && previa != ch) throw IllegalArgumentException("$where: choque de letras en ${c.clave} («${p.en}»)")
                    rejilla[c] = ch
                }
                val ini = Celda(p.fila, p.col)
                val n = inicios[ini]
                if (n != null && n != p.numero) throw IllegalArgumentException("$where: numeración inconsistente en ${ini.clave}")
                inicios[ini] = p.numero
            }
            if (palabras.map { it.en.lowercase() }.toSet().size != palabras.size) throw IllegalArgumentException("$where: palabra repetida")
            // numeración clásica: 1, 2, 3… por orden de lectura de las casillas de inicio
            val ordenadas = inicios.keys.sortedWith(compareBy({ it.fila }, { it.col }))
            ordenadas.forEachIndexed { k, c -> if (inicios[c] != k + 1) throw IllegalArgumentException("$where: numeración no clásica (${c.clave} debía ser ${k + 1})") }
            // corridas de ≥ 2 letras = exactamente las palabras (sin fantasmas)
            val corridas = HashSet<String>()
            for ((c, _) in rejilla) {
                for (h in listOf(true, false)) {
                    val antes = if (h) Celda(c.fila, c.col - 1) else Celda(c.fila - 1, c.col)
                    if (antes in rejilla) continue
                    val sb = StringBuilder(); var cur = c
                    while (cur in rejilla) { sb.append(rejilla[cur]); cur = if (h) Celda(cur.fila, cur.col + 1) else Celda(cur.fila + 1, cur.col) }
                    if (sb.length >= 2) corridas.add("${sb}@${c.clave}${if (h) "H" else "V"}")
                }
            }
            val esperadas = palabras.map { "${it.en.lowercase()}@${it.fila},${it.col}${it.dir}" }.toSet()
            if (corridas != esperadas) throw IllegalArgumentException("$where: hay corridas que no son palabras (fantasmas) o palabras que no forman corrida: ${(corridas - esperadas) + (esperadas - corridas)}")
            cruci
        }
    }

    /** vocabulario.json: bancos con id, title, level y pares {en, es} sin repetidos. */
    fun parseBancos(arr: JSONArray): List<Banco> =
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val where = "vocabulario.json, banco ${i + 1}"
            val id = req(o, "id", where)
            val pares = ArrayList<Pareja>()
            val pa = o.optJSONArray("pares") ?: throw IllegalArgumentException("$where: falta \"pares\"")
            for (j in 0 until pa.length()) {
                val p = pa.getJSONObject(j)
                val en = p.optString("en").trim()
                val es = p.optString("es").trim()
                if (en.isBlank() || es.isBlank()) throw IllegalArgumentException("$where: pareja ${j + 1} sin \"en\" o sin \"es\"")
                if (pares.any { it.en.equals(en, true) || it.es.equals(es, true) }) throw IllegalArgumentException("$where: pareja repetida: \"$en\" / \"$es\"")
                pares.add(Pareja(es, en))
            }
            if (pares.size < 3) throw IllegalArgumentException("$where: un banco necesita al menos 3 parejas")
            Banco(id, req(o, "title", where), req(o, "level", where), pares)
        }.also { lista ->
            val ids = lista.map { it.id }
            if (ids.size != ids.toSet().size) throw IllegalArgumentException("vocabulario.json: id de banco repetido")
        }

    /**
     * Lee el campo "sound" y revienta con un mensaje que dice exactamente dónde.
     * `where` es algo como "lección a1u1l2, ejercicio 3".
     */
    private fun requireSound(o: JSONObject, where: String): Sound {
        if (!o.has("sound") || o.isNull("sound")) {
            throw IllegalArgumentException(
                "$where: falta \"sound\" (el sonido que entrena). Valores válidos: ${Sound.keys}"
            )
        }
        val key = o.getString("sound")
        return Sound.parse(key) ?: throw IllegalArgumentException(
            "$where: \"sound\": \"$key\" no existe. Valores válidos: ${Sound.keys}"
        )
    }

    private fun parseScenarios(arr: JSONArray): List<Scenario> =
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val where = "scenarios.json, escenario ${i + 1}"
            fun req(key: String): String {
                if (!o.has(key) || o.isNull(key) || o.getString(key).isBlank()) {
                    throw IllegalArgumentException("$where: falta \"$key\"")
                }
                return o.getString(key)
            }
            val targets = strings(o, "targets")
            val watch = strings(o, "watch")
            if (targets.isEmpty()) throw IllegalArgumentException("$where: \"targets\" vacío")
            if (watch.isEmpty()) throw IllegalArgumentException("$where: \"watch\" vacío")
            Scenario(
                id = req("id"), title = req("title"), emoji = o.optString("emoji", "💬"),
                level = req("level"), goalEs = req("goalEs"), role = req("role"),
                opening = req("opening"), targets = targets,
                help = parseAyudas(o.optJSONArray("help"), where), watch = watch
            )
        }

    private fun parseAyudas(arr: JSONArray?, where: String): List<Ayuda> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val en = o.optString("en")
            val es = o.optString("es")
            if (en.isBlank() || es.isBlank()) {
                throw IllegalArgumentException("$where: ayuda ${i + 1} sin \"en\" o sin \"es\"")
            }
            Ayuda(en, es)
        }
    }

    private fun parseDrills(arr: JSONArray): List<Drill> =
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val where = "drills.json, drill ${i + 1}"
            Drill(
                text = o.getString("text"),
                sound = requireSound(o, where),
                focusEs = o.getString("focus"),
                tipEs = o.getString("tip")
            )
        }

    /**
     * Lo que se comprueba al cargar. Vive aquí (Kotlin puro, sin Android) para
     * poder probarlo en el PC con `./gradlew test`; los ids que ya se vieron se
     * pasan de lección en lección para exigir que sean únicos en todo el curso.
     */
    private class Seen {
        val lessons = HashSet<String>()
        val exercises = HashSet<String>()
    }

    /** Parsea el texto de `curriculum.json`. Revienta con un mensaje que dice dónde. */
    fun parseCurriculum(text: String): List<Level> =
        parseLevels(JSONObject(text).getJSONArray("levels"), Seen())

    private fun parseLevels(arr: JSONArray, seen: Seen): List<Level> =
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Level(
                id = o.getString("id"),
                title = o.getString("title"),
                goal = o.optString("goal", ""),
                units = parseUnits(o.getJSONArray("units"), seen)
            )
        }

    private fun parseUnits(arr: JSONArray, seen: Seen): List<CourseUnit> =
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CourseUnit(
                id = o.getString("id"),
                emoji = o.optString("emoji", "📘"),
                title = o.getString("title"),
                subtitle = o.optString("subtitle", ""),
                lessons = parseLessons(o.getJSONArray("lessons"), seen)
            )
        }

    private fun parseLessons(arr: JSONArray, seen: Seen): List<Lesson> =
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val id = o.getString("id")
            if (!seen.lessons.add(id)) throw IllegalArgumentException("lección $id: id repetido")
            Lesson(
                id = id,
                title = o.getString("title"),
                theory = parseTheory(o.optJSONObject("theory"), "lección $id"),
                exercises = parseExercises(o.getJSONArray("exercises"), id, seen)
            )
        }

    private fun parseTheory(o: JSONObject?, where: String): Theory {
        if (o == null) throw IllegalArgumentException("$where: falta \"theory\" (title, body y trap)")
        fun req(key: String): String {
            val v = if (o.isNull(key)) "" else o.optString(key, "")
            if (v.isBlank()) throw IllegalArgumentException("$where: \"theory\" sin \"$key\"")
            return v
        }
        return Theory(title = req("title"), body = req("body"), trap = req("trap"))
    }

    private fun req(o: JSONObject, key: String, where: String): String {
        val v = if (o.isNull(key)) "" else o.optString(key, "")
        if (v.isBlank()) throw IllegalArgumentException("$where: falta \"$key\"")
        return v
    }

    /** Un ejercicio de producir texto: respuesta no vacía y alternativas que no la repitan. */
    private fun checkProduced(where: String, answer: String, accept: List<String>) {
        if (answer.isBlank()) throw IllegalArgumentException("$where: falta \"answer\"")
        // Con contracciones expandidas: "I'm fine" y "I am fine" son la misma respuesta.
        val vistas = HashSet<String>()
        vistas.add(Correccion.sueltaEstricta(answer))
        for (a in accept) {
            if (a.isBlank()) throw IllegalArgumentException("$where: \"accept\" tiene una entrada vacía")
            if (!vistas.add(Correccion.sueltaEstricta(a))) throw IllegalArgumentException("$where: \"accept\" repite la respuesta o se repite: \"$a\"")
        }
    }

    /** Las opciones de un ejercicio de elegir: al menos dos, sin repetidas, y la respuesta entre ellas. */
    private fun checkChoice(where: String, options: List<String>, answer: String) {
        if (options.size < 2) throw IllegalArgumentException("$where: \"options\" necesita al menos 2 opciones")
        val repetidas = options.groupBy { it.trim() }.filter { it.value.size > 1 }.keys
        if (repetidas.isNotEmpty()) throw IllegalArgumentException("$where: opciones repetidas: ${repetidas.joinToString(" | ")}")
        if (answer.isBlank()) throw IllegalArgumentException("$where: falta \"answer\" (el texto de la opción correcta)")
        if (answer !in options) throw IllegalArgumentException("$where: \"answer\" no está entre las opciones: \"$answer\"")
    }

    private fun strings(o: JSONObject, key: String): List<String> {
        val a = o.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { a.getString(it) }
    }

    private fun parseExercises(arr: JSONArray, lessonId: String, seen: Seen): List<Exercise> {
        val out = ArrayList<Exercise>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val where = "lección $lessonId, ejercicio ${i + 1}"
            val tip = if (o.isNull("tip")) null else o.optString("tip", "").ifBlank { null }
            val id = o.optString("id", "").trim()
            if (id.isBlank()) throw IllegalArgumentException("$where: falta \"id\" (formato ${lessonId}e${i + 1})")
            if (!seen.exercises.add(id)) throw IllegalArgumentException("$where: id \"$id\" repetido en el curso")
            val ex = when (val type = o.getString("type")) {
                "listen" -> {
                    val options = strings(o, "options")
                    val answer = o.optString("answer", "")
                    checkChoice(where, options, answer)
                    val audio = o.getString("audio")
                    // Lo que suena tiene que ser la opción correcta; si no, el audio no es ninguna.
                    if (audio != answer) throw IllegalArgumentException("$where: en listen, \"audio\" y \"answer\" deben ser iguales")
                    Exercise.ListenChoose(id = id, audio = audio, options = options, answer = answer, tip = tip)
                }
                "translate" -> {
                    val options = strings(o, "options")
                    val answer = o.optString("answer", "")
                    checkChoice(where, options, answer)
                    val accept = strings(o, "accept")
                    checkProduced(where, answer, accept)
                    Exercise.TranslateChoose(id = id, es = o.getString("es"), options = options, answer = answer, tip = tip, accept = accept)
                }
                "build" -> {
                    val answer = o.getString("answer")
                    val extra = strings(o, "extra")
                    // Una palabra "extra" que ya está en la frase no distrae a nadie.
                    val propias = answer.split(" ").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
                    val repetidas = extra.filter { it.trim().lowercase() in propias }
                    if (repetidas.isNotEmpty()) {
                        throw IllegalArgumentException("$where: \"extra\" repite palabras de la respuesta: ${repetidas.joinToString(", ")}")
                    }
                    if (extra.map { it.trim().lowercase() }.toSet().size != extra.size) {
                        throw IllegalArgumentException("$where: \"extra\" tiene palabras repetidas")
                    }
                    val accept = strings(o, "accept")
                    checkProduced(where, answer, accept)
                    Exercise.BuildSentence(id = id, es = o.getString("es"), answer = answer, extraWords = extra, tip = tip, accept = accept)
                }
                "type" -> {
                    val meaning = o.optString("meaning", "")
                    if (meaning.isBlank()) throw IllegalArgumentException("$where: falta \"meaning\" (qué significa lo que se escribe)")
                    val audio = o.getString("audio")
                    val accept = strings(o, "accept")
                    checkProduced(where, audio, accept)
                    Exercise.TypeWhatYouHear(id = id, audio = audio, meaningEs = meaning, tip = tip, accept = accept)
                }
                "speak" -> Exercise.SpeakIt(
                    id = id,
                    text = o.getString("text"),
                    sound = requireSound(o, where),
                    tip = tip
                )
                "write" -> {
                    val answer = o.optString("answer", "")
                    val accept = strings(o, "accept")
                    checkProduced(where, answer, accept)
                    Exercise.WriteIt(id = id, es = req(o, "es", where), answer = answer, accept = accept, tip = tip)
                }
                "cloze" -> {
                    val text = req(o, "text", where)
                    val answer = o.optString("answer", "")
                    val accept = strings(o, "accept")
                    checkProduced(where, answer, accept)
                    val huecos = text.windowed(Exercise.Cloze.BLANK.length).count { it == Exercise.Cloze.BLANK }
                    if (huecos != 1) throw IllegalArgumentException("$where: \"text\" tiene que tener exactamente un hueco ${Exercise.Cloze.BLANK} (tiene $huecos)")
                    Exercise.Cloze(id = id, text = text, answer = answer, es = req(o, "es", where), accept = accept, tip = tip)
                }
                "shadow" -> Exercise.Shadow(id = id, text = req(o, "text", where), tip = tip)
                "minimalPair" -> {
                    val options = strings(o, "options")
                    val answer = o.optString("answer", "")
                    checkChoice(where, options, answer)
                    for (w in options) {
                        if (w.trim().contains(' ')) throw IllegalArgumentException("$where: las opciones de un par mínimo son palabras sueltas: \"$w\"")
                    }
                    val sentence = o.optString("sentence", "").ifBlank { null }
                    if (sentence != null && !sentence.lowercase().contains(answer.lowercase())) {
                        throw IllegalArgumentException("$where: \"sentence\" no contiene la palabra \"$answer\"")
                    }
                    val play = o.optString("play", "").ifBlank { null }
                    if (play != null && play != "sentence") throw IllegalArgumentException("$where: \"play\" solo admite \"sentence\"")
                    if (play == "sentence" && sentence == null) throw IllegalArgumentException("$where: \"play\": \"sentence\" sin \"sentence\"")
                    Exercise.MinimalPair(id = id, options = options, answer = answer, sentence = sentence,
                        playSentence = play == "sentence", tip = tip)
                }
                // Antes un tipo desconocido se saltaba en silencio: un error de
                // dedo en el JSON hacía desaparecer el ejercicio sin aviso.
                else -> throw IllegalArgumentException(
                    "$where: tipo \"$type\" desconocido (listen, translate, build, type, speak, write, cloze, shadow, minimalPair)"
                )
            }
            out.add(ex)
        }
        return out
    }

    // --- Consultas -----------------------------------------------------------

    fun allUnits(): List<CourseUnit> = levels.flatMap { it.units }

    fun allLessons(): List<Lesson> = allUnits().flatMap { it.lessons }

    fun lessonById(id: String): Lesson? = allLessons().firstOrNull { it.id == id }

    fun exerciseById(id: String): Exercise? =
        allLessons().asSequence().flatMap { it.exercises.asSequence() }.firstOrNull { it.id == id }

    /**
     * La lección de la que sale un ejercicio, por su id (`a1u4l2e5` → `a1u4l2`),
     * también cuando el id viene envuelto por el mazo o el cuaderno
     * (`fix|a1u4l2e5`). Null para lo que no nace de una lección (historias,
     * crucigramas). Es lo que abre el botón "¿Por qué?" al fallar.
     */
    fun lessonOfExercise(exerciseId: String, lessons: List<Lesson> = allLessons()): Lesson? {
        val m = Regex("([a-z]\\d+u\\d+l\\d+)e\\d+").find(exerciseId) ?: return null
        return lessons.firstOrNull { it.id == m.groupValues[1] }
    }

    /** referencia.json: fichas sueltas y planas {id, title, body, trap}, mismas reglas que la teoría de una lección. */
    fun parseReferencia(arr: JSONArray): List<Lesson> {
        val ids = HashSet<String>()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val where = "referencia.json, ficha ${i + 1}"
            val id = req(o, "id", where)
            if (!ids.add(id)) throw IllegalArgumentException("$where: id repetido \"$id\"")
            Lesson("ref-$id", req(o, "title", where), parseTheory(o, where), emptyList())
        }
    }

    fun scenarioById(id: String): Scenario? =
        if (id == Scenario.LIBRE.id) Scenario.LIBRE else scenarios.firstOrNull { it.id == id }

    /** Ayudas que sirven en cualquier charla (pedir que repita, preguntar una palabra…). */
    var helpCommon: List<Ayuda> = emptyList()
        private set

    fun unitOfLesson(lessonId: String): CourseUnit? =
        allUnits().firstOrNull { u -> u.lessons.any { it.id == lessonId } }

    fun levelOfUnit(unitId: String): Level? =
        levels.firstOrNull { lv -> lv.units.any { it.id == unitId } }

    private const val TAG = "HabloCourse"
}

/** Normaliza texto para comparar respuestas escritas o habladas. Sin tildes: "Bogota" = "Bogotá". */
fun normalizeAnswer(text: String): String =
    java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace("’", "'")
        .filter { it.isLetterOrDigit() || it == ' ' || it == '\'' }
        .trim()
        .replace(Regex("\\s+"), " ")
