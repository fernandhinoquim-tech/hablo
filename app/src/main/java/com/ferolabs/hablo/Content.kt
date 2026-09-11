package com.ferolabs.hablo

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

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
        override val tip: String? = null
    ) : Exercise()
}

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

    var loaded by mutableStateOf(false)
        private set

    var loadError by mutableStateOf<String?>(null)
        private set

    fun load(context: Context) {
        if (loaded) return
        try {
            val json = context.assets.open("content/curriculum.json")
                .bufferedReader()
                .use { it.readText() }
            levels = parseLevels(JSONObject(json).getJSONArray("levels"))
            loaded = true
            Log.i(TAG, "Curso cargado: ${levels.size} niveles, ${allLessons().size} lecciones")
        } catch (e: Throwable) {
            Log.e(TAG, "No se pudo cargar el curso", e)
            loadError = "${e.javaClass.simpleName}: ${e.message}"
            levels = emptyList()
            loaded = true
        }
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
            Lesson(
                id = o.getString("id"),
                title = o.getString("title"),
                exercises = parseExercises(o.getJSONArray("exercises"))
            )
        }

    private fun strings(o: JSONObject, key: String): List<String> {
        val a = o.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { a.getString(it) }
    }

    private fun parseExercises(arr: JSONArray): List<Exercise> {
        val out = ArrayList<Exercise>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val tip = if (o.isNull("tip")) null else o.optString("tip", "").ifBlank { null }
            val ex = when (o.getString("type")) {
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
                    tip = tip
                )
                else -> null
            }
            if (ex != null) out.add(ex)
        }
        return out
    }

    // --- Consultas -----------------------------------------------------------

    fun allUnits(): List<CourseUnit> = levels.flatMap { it.units }

    fun allLessons(): List<Lesson> = allUnits().flatMap { it.lessons }

    fun lessonById(id: String): Lesson? = allLessons().firstOrNull { it.id == id }

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
