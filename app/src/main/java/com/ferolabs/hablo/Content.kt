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
    abstract val tip: String?

    /** Escuchas una frase y eliges cuál fue. */
    data class ListenChoose(
        val audio: String,
        val options: List<String>,
        val answer: Int,
        override val tip: String? = null
    ) : Exercise()

    /** Ves español y eliges la traducción correcta. */
    data class TranslateChoose(
        val es: String,
        val options: List<String>,
        val answer: Int,
        override val tip: String? = null
    ) : Exercise()

    /** Armas la frase tocando palabras. */
    data class BuildSentence(
        val es: String,
        val answer: String,
        val extraWords: List<String> = emptyList(),
        override val tip: String? = null
    ) : Exercise()

    /** Escribes lo que escuchaste. */
    data class TypeWhatYouHear(
        val audio: String,
        val meaningEs: String,
        override val tip: String? = null
    ) : Exercise()

    /** Lo dices en voz alta y se te puntúa palabra por palabra. */
    data class SpeakIt(
        val text: String,
        val sound: Sound,
        override val tip: String? = null
    ) : Exercise()
}

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
)

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
 * Carga y guarda el curso. Es un objeto único porque el contenido no cambia
 * durante la sesión: se lee una vez al arrancar la app.
 */
object Course {

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
            levels = parseLevels(JSONObject(readAsset(context, "content/curriculum.json")).getJSONArray("levels"))
            drills = parseDrills(JSONObject(readAsset(context, "content/drills.json")).getJSONArray("drills"))
            val scenariosJson = JSONObject(readAsset(context, "content/scenarios.json"))
            helpCommon = parseAyudas(scenariosJson.optJSONArray("helpCommon"), "scenarios.json, helpCommon")
            scenarios = parseScenarios(scenariosJson.getJSONArray("scenarios"))
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

    private fun parseLevels(arr: JSONArray): List<Level> =
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Level(
                id = o.getString("id"),
                title = o.getString("title"),
                goal = o.optString("goal", ""),
                units = parseUnits(o.getJSONArray("units"))
            )
        }

    private fun parseUnits(arr: JSONArray): List<CourseUnit> =
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CourseUnit(
                id = o.getString("id"),
                emoji = o.optString("emoji", "📘"),
                title = o.getString("title"),
                subtitle = o.optString("subtitle", ""),
                lessons = parseLessons(o.getJSONArray("lessons"))
            )
        }

    private fun parseLessons(arr: JSONArray): List<Lesson> =
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val id = o.getString("id")
            Lesson(
                id = id,
                title = o.getString("title"),
                exercises = parseExercises(o.getJSONArray("exercises"), id)
            )
        }

    private fun strings(o: JSONObject, key: String): List<String> {
        val a = o.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { a.getString(it) }
    }

    private fun parseExercises(arr: JSONArray, lessonId: String): List<Exercise> {
        val out = ArrayList<Exercise>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val where = "lección $lessonId, ejercicio ${i + 1}"
            val tip = if (o.isNull("tip")) null else o.optString("tip", "").ifBlank { null }
            val ex = when (val type = o.getString("type")) {
                "listen" -> Exercise.ListenChoose(
                    audio = o.getString("audio"),
                    options = strings(o, "options"),
                    answer = o.getInt("answer"),
                    tip = tip
                )
                "translate" -> Exercise.TranslateChoose(
                    es = o.getString("es"),
                    options = strings(o, "options"),
                    answer = o.getInt("answer"),
                    tip = tip
                )
                "build" -> Exercise.BuildSentence(
                    es = o.getString("es"),
                    answer = o.getString("answer"),
                    extraWords = strings(o, "extra"),
                    tip = tip
                )
                "type" -> Exercise.TypeWhatYouHear(
                    audio = o.getString("audio"),
                    meaningEs = o.optString("meaning", ""),
                    tip = tip
                )
                "speak" -> Exercise.SpeakIt(
                    text = o.getString("text"),
                    sound = requireSound(o, where),
                    tip = tip
                )
                // Antes un tipo desconocido se saltaba en silencio: un error de
                // dedo en el JSON hacía desaparecer el ejercicio sin aviso.
                else -> throw IllegalArgumentException(
                    "$where: tipo \"$type\" desconocido (listen, translate, build, type, speak)"
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

    fun scenarioById(id: String): Scenario? = scenarios.firstOrNull { it.id == id }

    /** Ayudas que sirven en cualquier charla (pedir que repita, preguntar una palabra…). */
    var helpCommon: List<Ayuda> = emptyList()
        private set

    fun unitOfLesson(lessonId: String): CourseUnit? =
        allUnits().firstOrNull { u -> u.lessons.any { it.id == lessonId } }

    fun levelOfUnit(unitId: String): Level? =
        levels.firstOrNull { lv -> lv.units.any { it.id == unitId } }

    private const val TAG = "HabloCourse"
}

/** Normaliza texto para comparar respuestas escritas o habladas. */
fun normalizeAnswer(text: String): String =
    text.lowercase()
        .replace("’", "'")
        .filter { it.isLetterOrDigit() || it == ' ' || it == '\'' }
        .trim()
        .replace(Regex("\\s+"), " ")
