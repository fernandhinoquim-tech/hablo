package com.ferolabs.hablo

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * **Modo Aptis: la pista de preparación** (etapa 5). Aptis ESOL General le
 * exige a Fero B1 o superior en las CUATRO destrezas: es un piso, no un
 * promedio, así que lo que manda es la más floja.
 *
 * Cambio de diseño (Fero, 2026-09-16): no es un test fijo sino **cinco pistas**
 * (Core, Reading, Listening, Writing, Speaking) que arrancan abajo y suben de
 * nivel mientras entrenan. Un test de 30 tareas da una foto con margen de
 * error; una pista graduada mide con cada tarea, y subir rápido ES el
 * diagnóstico: si ya es B1 leyendo, barre el A2 en una sesión y la pista lo
 * promueve sola.
 *
 * - [PistaAptis]: el banco de tareas de una destreza, por nivel (A1 → B2).
 * - [Condicion.cumple] es la **regla de promoción**: se sube de nivel al
 *   cumplirla sobre las últimas N tareas de ese nivel (`aptis-pistas.json`).
 *   Si las últimas van flojas no se degrada: la ronda siguiente mezcla tareas
 *   del nivel anterior ([Aptis.flojo], [Aptis.ronda]).
 * - El tablero ([Aptis.piso]) dice cuál de las cuatro va última: ahí se estudia.
 * - El **simulacro** completo cronometrado (el antiguo diagnóstico, [Diagnostico])
 *   se desbloquea cuando las cinco pistas están en B1 o más
 *   ([Aptis.simulacroDesbloqueado]); ese sí es de una sentada.
 *
 * Lo que no se negocia (Cowork, 2026-09-16), se conserva: [AVISO_APTIS] en
 * pantalla, y la IA cita una frase del alumno como prueba de cada juicio
 * ([JuezAptis.citaAparece]).
 */
const val AVISO_APTIS = "Esto es una estimación, no tu nota de Aptis. Sirve para saber por dónde empezar."

/** Nivel de una pista o de una estimación. El orden de declaración es el orden real: el piso es el mínimo. */
enum class NivelAptis(val etiqueta: String) {
    A1("A1"), A2("A2"), B1("B1"), B2("B2");

    val siguiente: NivelAptis? get() = entries.getOrNull(ordinal + 1)
    val anterior: NivelAptis? get() = entries.getOrNull(ordinal - 1)

    companion object {
        /** Lee lo que devuelve la IA, el contenido o lo guardado. Por debajo de A2 es A1; C1/C2 se reportan como B2: la pista no distingue más arriba. */
        fun de(texto: String?): NivelAptis? = when (texto?.trim()?.uppercase(Locale.US)?.replace(" ", "")) {
            "A1", "<A2", "A0", "BAJO_A2", "PORDEBAJODEA2", "MENOSA2" -> A1
            "A2" -> A2
            "B1" -> B1
            "B2", "C1", "C2" -> B2
            else -> null
        }
    }
}

// ---------------------------------------------------------------------------
// Tareas (el contenido: aptis-core-*.json, aptis-diagnostico.json y, cuando lleguen, aptis-<destreza>.json)
// ---------------------------------------------------------------------------

sealed interface TareaAptis {
    val id: String
    /** "A1", "A2", "B1" o "B2" (C1 del banco del Core ya viene convertido a B2). */
    val level: String
    val nivel: NivelAptis get() = NivelAptis.de(level) ?: NivelAptis.A1
}

/**
 * Un ítem del Core: gramática (`text` con hueco) o vocabulario (`sub` = synonym,
 * definition, collocation con `prompt`; usage con `text`). `why` se muestra al
 * corregir, acierte o no (g = 0,73 con explicación contra 0,39 sin ella).
 */
data class ItemCore(
    override val id: String,
    override val level: String,
    val text: String,
    val options: List<String>,
    val answer: String,
    val why: String = "",
    val sub: String = "",
    val prompt: String = "",
    val point: String = ""
) : TareaAptis {
    /** Lo que se le pregunta, según el subtipo. */
    val instruccion: String
        get() = when (sub) {
            "synonym" -> "¿Cuál significa lo mismo?"
            "definition" -> "¿A qué palabra corresponde esta definición?"
            "collocation" -> "Completa la combinación."
            "usage" -> "Completa la frase."
            else -> ""
        }
    /** El enunciado en pantalla: el texto con hueco, o el prompt del vocabulario. */
    val enunciado: String get() = (if (text.isNotBlank()) text else prompt).replace("___", "______")
    /** El enunciado con la respuesta puesta, para la corrección. */
    val resuelto: String get() = if (text.isNotBlank()) text.replace("___", answer) else if (prompt.contains("___")) prompt.replace("___", answer) else "$prompt → $answer"
}

sealed class TareaLectura(override val id: String, override val level: String, val tipo: String, val why: String) : TareaAptis {
    class Completar(id: String, level: String, val text: String, val options: List<String>, val answer: String, why: String = "") :
        TareaLectura(id, level, "completar", why)

    class Ordenar(id: String, level: String, val instruccion: String, val primera: String, val desordenadas: List<String>, val orden: List<String>, why: String = "") :
        TareaLectura(id, level, "ordenar", why)

    class Titulos(id: String, level: String, val instruccion: String, val parrafos: List<String>, val titulos: List<String>, val answer: List<String>, why: String = "") :
        TareaLectura(id, level, "titulos", why)
}

data class TareaEscucha(
    override val id: String, val tipo: String, override val level: String, val audio: String, val pregunta: String,
    val options: List<String>, val answer: String, val why: String = ""
) : TareaAptis

/**
 * Una tarea de Writing. [mensajes]: lo que hay que contestar (parte 1: cinco
 * mensajes con una palabra cada uno; parte 3: tres personas en un grupo);
 * [parte]: "Parte 1 · una palabra", la parte del examen a la que imita.
 */
data class TareaEscrita(
    override val id: String, override val level: String, val palabras: String, val segundos: Int,
    val promptEn: String, val promptEs: String, val rubrica: List<String>,
    val mensajes: List<String> = emptyList(), val parte: String = ""
) : TareaAptis

/**
 * Speaking. [foto] es la descripción en inglés de la(s) foto(s) de las partes 2 y 3
 * (Cowork, 17-09): mientras no haya imagen, `promptEs` la describe y la tarea funciona
 * igual; el juez la recibe para saber de qué se hablaba. [imagenes] son rutas dentro de
 * `assets/` (`images/speaking/s-009.webp`; la parte 3 lleva dos) cuando Fero traiga las
 * 20 fotos generadas con Gemini; checkContent exige que existan, .webp/.jpg y ≤ 400 KB.
 */
data class TareaHablada(
    override val id: String, override val level: String, val prepSeg: Int, val hablarSeg: Int,
    val promptEn: String, val promptEs: String, val rubrica: List<String>, val parte: String = "",
    val foto: String = "", val imagenes: List<String> = emptyList()
) : TareaAptis

fun emojiAptis(id: String): String = when (id) {
    "core" -> "🧩"; "reading" -> "📖"; "listening" -> "🎧"; "writing" -> "✍️"; "speaking" -> "🎤"; else -> "🎯"
}

/** Una parte del simulacro (el antiguo diagnóstico): tareas fijas, minutos y reloj. */
class SeccionAptis(
    val id: String,
    val skill: String,
    val title: String,
    val minutos: Int,
    val instruccion: String,
    val segundosPorItem: Int = 0,
    val core: List<ItemCore> = emptyList(),
    val lectura: List<TareaLectura> = emptyList(),
    val escucha: List<TareaEscucha> = emptyList(),
    val escritura: List<TareaEscrita> = emptyList(),
    val habla: List<TareaHablada> = emptyList(),
    /** Solo Core: `estimacion.core` del JSON ("4 de 5 en A2 y 4 de 5 en B1"), ya leído. */
    val umbrales: Map<String, List<Condicion>> = emptyMap()
) {
    /** Writing y Speaking: los juzga la IA contra la rúbrica. */
    val porIa: Boolean get() = escritura.isNotEmpty() || habla.isNotEmpty()
    val cuantas: Int get() = core.size + lectura.size + escucha.size + escritura.size + habla.size
    val emoji: String get() = emojiAptis(id)
    val tareas: List<TareaAptis> get() = core + lectura + escucha + escritura + habla
}

/** El simulacro completo: `aptis-diagnostico.json` tal cual (secciones, reloj, umbrales de estimación). */
class Diagnostico(val duracionMin: Int, val secciones: List<SeccionAptis>) {
    fun seccion(id: String): SeccionAptis? = secciones.firstOrNull { it.id == id }
    val tareas: Int get() = secciones.sumOf { it.cuantas }
}

/**
 * Una pista: todas las tareas de una destreza, de todos los niveles, más su
 * regla de promoción y el tamaño de la ronda (`aptis-pistas.json`).
 */
class PistaAptis(
    val id: String,
    val skill: String,
    val title: String,
    val tareas: List<TareaAptis>,
    val promocion: Condicion,
    val ronda: Int,
    /** Core: 30 s por ítem, como en el examen (25 min para 50). 0 = sin reloj por ítem. */
    val segundosPorItem: Int = 0
) {
    val porIa: Boolean get() = tareas.any { it is TareaEscrita || it is TareaHablada }
    val emoji: String get() = emojiAptis(id)
    fun tarea(id: String): TareaAptis? = tareas.firstOrNull { it.id == id }
    fun de(nivel: NivelAptis): List<TareaAptis> = tareas.filter { it.nivel == nivel }
    /** Los niveles que tienen tareas, de abajo arriba. */
    val niveles: List<NivelAptis> get() = NivelAptis.entries.filter { n -> tareas.any { it.nivel == n } }
    /** Donde arranca la pista: el nivel más bajo con tareas (A1 cuando Cowork mande A1). */
    val nivelInicial: NivelAptis get() = niveles.firstOrNull() ?: NivelAptis.A1
    /** El nivel siguiente CON tareas, o null si ya está arriba de todo. */
    fun siguienteCon(nivel: NivelAptis): NivelAptis? = niveles.firstOrNull { it > nivel }
    fun anteriorCon(nivel: NivelAptis): NivelAptis? = niveles.lastOrNull { it < nivel }
}

/** Todo el Modo Aptis cargado: las cinco pistas y el simulacro. */
class BancoAptis(val pistas: List<PistaAptis>, val simulacro: Diagnostico?) {
    fun pista(id: String): PistaAptis? = pistas.firstOrNull { it.id == id }
    /** Las cuatro destrezas que Aptis exige en B1: todas menos el Core, que es el desempate. */
    val cuatro: List<PistaAptis> get() = pistas.filter { it.id != "core" }
}

private val NIVELES_ITEM = setOf("A1", "A2", "B1", "B2", "C1")
val PISTAS_APTIS = listOf("core" to "Core", "reading" to "Reading", "listening" to "Listening", "writing" to "Writing", "speaking" to "Speaking")
private val TITULOS_PISTA = mapOf("core" to "Gramática y vocabulario", "reading" to "Lectura", "listening" to "Escucha", "writing" to "Escritura", "speaking" to "Habla")

/**
 * "4 de 5" o "4 de 5 en B1": hace falta acertar [bien] de [de] ítems (en
 * proporción, por si cambia el número). Es la regla de promoción de las pistas
 * y el umbral de estimación del Core en el simulacro.
 */
data class Condicion(val bien: Int, val de: Int, val level: String = "") {
    fun cumple(aciertos: Int, total: Int): Boolean = total > 0 && aciertos * de >= total * bien
}

private val CONDICION = Regex("(\\d+) de (\\d+)(?: en (A1|A2|B1|B2))?")

/** Lee "N de M[ en nivel]"; revienta si no tiene esa forma. */
fun parseCondicion(where: String, texto: String): List<Condicion> {
    val conds = CONDICION.findAll(texto).map { m ->
        val bien = m.groupValues[1].toInt(); val de = m.groupValues[2].toInt()
        if (de <= 0 || bien > de) throw IllegalArgumentException("$where: \"${m.value}\" no tiene sentido")
        Condicion(bien, de, m.groupValues[3])
    }.toList()
    if (conds.isEmpty()) throw IllegalArgumentException("$where tiene que decir \"N de M\" (dice \"$texto\")")
    return conds
}

/**
 * `estimacion.core` del simulacro: por nivel, la lista de condiciones ("4 de 5
 * en A2 y 4 de 5 en B1"). Los umbrales viven en el contenido (regla dura 4).
 */
fun parseUmbralesCore(estimacion: JSONObject?): Map<String, List<Condicion>> {
    val core = estimacion?.optJSONObject("core") ?: throw IllegalArgumentException("aptis-diagnostico.json: falta \"estimacion.core\"")
    val out = LinkedHashMap<String, List<Condicion>>()
    for (nivel in listOf("A2", "B1", "B2")) {
        val conds = parseCondicion("aptis-diagnostico.json: estimacion.core.$nivel", core.optString(nivel))
        if (conds.any { it.level.isBlank() }) throw IllegalArgumentException("aptis-diagnostico.json: estimacion.core.$nivel tiene que decir en qué nivel (\"N de M en A2\")")
        out[nivel] = conds
    }
    return out
}

private fun jsonStrings(a: JSONArray?): List<String> {
    if (a == null) return emptyList()
    val out = ArrayList<String>(a.length())
    for (i in 0 until a.length()) out.add(a.getString(i))
    return out
}

/** Lo que comparten los parsers: ids únicos en todo el Modo Aptis, niveles válidos, opciones sanas. */
internal class Lector(val idsTarea: HashSet<String>) {
    fun idNuevo(where: String, id: String) {
        if (id.isBlank()) throw IllegalArgumentException("$where: falta \"id\"")
        if (!idsTarea.add(id)) throw IllegalArgumentException("$where: id repetido \"$id\"")
    }
    fun nivel(where: String, o: JSONObject): String {
        val l = o.optString("level")
        if (l !in NIVELES_ITEM) throw IllegalArgumentException("$where: level \"$l\" no es A1, A2, B1 ni B2")
        return if (l == "C1") "B2" else l
    }
    fun opciones(where: String, o: JSONObject): List<String> {
        val opts = jsonStrings(o.optJSONArray("options"))
        if (opts.size < 2) throw IllegalArgumentException("$where: hacen falta al menos 2 opciones")
        if (opts.map { it.trim().lowercase(Locale.US) }.toSet().size != opts.size) throw IllegalArgumentException("$where: opciones repetidas")
        return opts
    }
    fun respuesta(where: String, o: JSONObject, opts: List<String>): String {
        val a = o.optString("answer")
        if (a !in opts) throw IllegalArgumentException("$where: answer \"$a\" no está en options")
        return a
    }
    fun hueco(where: String, text: String) {
        if (text.split("___").size != 2) throw IllegalArgumentException("$where: el texto necesita exactamente un hueco ___")
    }

    fun itemGramatica(where: String, it: JSONObject): ItemCore {
        idNuevo(where, it.optString("id"))
        val text = it.optString("text"); hueco(where, text)
        val opts = opciones(where, it)
        return ItemCore(it.getString("id"), nivel(where, it), text, opts, respuesta(where, it, opts), it.optString("why"), "", "", it.optString("point"))
    }

    fun itemVocabulario(where: String, it: JSONObject): ItemCore {
        idNuevo(where, it.optString("id"))
        val sub = it.optString("sub")
        if (sub !in setOf("synonym", "definition", "usage", "collocation")) throw IllegalArgumentException("$where: sub \"$sub\" desconocido")
        val text = it.optString("text"); val prompt = it.optString("prompt")
        if (sub == "usage") hueco(where, text) else if (prompt.isBlank()) throw IllegalArgumentException("$where: falta \"prompt\"")
        val opts = opciones(where, it)
        if (it.optString("why").isBlank()) throw IllegalArgumentException("$where: falta \"why\" (la explicación en español)")
        return ItemCore(it.getString("id"), nivel(where, it), if (sub == "usage") text else "", opts, respuesta(where, it, opts), it.optString("why"), sub, prompt)
    }

    fun lectura(where: String, t: JSONObject): TareaLectura {
        idNuevo(where, t.optString("id"))
        val tid = t.getString("id")
        val lvl = nivel(where, t)
        val why = t.optString("why")
        return when (val tipo = t.optString("tipo")) {
            "completar" -> {
                val text = t.optString("text"); hueco(where, text)
                val opts = opciones(where, t)
                TareaLectura.Completar(tid, lvl, text, opts, respuesta(where, t, opts), why)
            }
            "ordenar" -> {
                val primera = t.optString("primera").ifBlank { throw IllegalArgumentException("$where: falta \"primera\"") }
                val des = jsonStrings(t.optJSONArray("desordenadas"))
                val orden = jsonStrings(t.optJSONArray("orden"))
                if (des.size < 2) throw IllegalArgumentException("$where: hacen falta al menos 2 frases desordenadas")
                if (des.toSet().size != des.size) throw IllegalArgumentException("$where: frases repetidas")
                if (orden.sorted() != des.sorted()) throw IllegalArgumentException("$where: \"orden\" no es una permutación de \"desordenadas\"")
                TareaLectura.Ordenar(tid, lvl, t.optString("instruccion"), primera, des, orden, why)
            }
            "titulos" -> {
                val parrafos = jsonStrings(t.optJSONArray("parrafos"))
                val titulos = jsonStrings(t.optJSONArray("titulos"))
                val answer = jsonStrings(t.optJSONArray("answer"))
                if (parrafos.size < 2) throw IllegalArgumentException("$where: hacen falta al menos 2 párrafos")
                if (titulos.size <= parrafos.size) throw IllegalArgumentException("$where: tiene que sobrar al menos un título")
                if (titulos.toSet().size != titulos.size) throw IllegalArgumentException("$where: títulos repetidos")
                if (answer.size != parrafos.size) throw IllegalArgumentException("$where: \"answer\" necesita un título por párrafo")
                if (answer.toSet().size != answer.size || answer.any { it !in titulos }) throw IllegalArgumentException("$where: \"answer\" con títulos repetidos o fuera de \"titulos\"")
                TareaLectura.Titulos(tid, lvl, t.optString("instruccion"), parrafos, titulos, answer, why)
            }
            else -> throw IllegalArgumentException("$where: tipo desconocido \"$tipo\"")
        }
    }

    fun escucha(where: String, t: JSONObject): TareaEscucha {
        idNuevo(where, t.optString("id"))
        val audio = t.optString("audio").ifBlank { throw IllegalArgumentException("$where: falta \"audio\"") }
        val pregunta = t.optString("pregunta").ifBlank { throw IllegalArgumentException("$where: falta \"pregunta\"") }
        val opts = opciones(where, t)
        return TareaEscucha(t.getString("id"), t.optString("tipo"), nivel(where, t), audio, pregunta, opts, respuesta(where, t, opts), t.optString("why"))
    }

    /** El simulacro escribe `prompt_en`; las pistas de Cowork, `promptEn`. Valen las dos. */
    private fun prompt(where: String, t: JSONObject, idioma: String): String {
        val camel = "prompt" + idioma.replaceFirstChar { it.uppercase() }
        return t.optString("prompt_$idioma").ifBlank { t.optString(camel) }
            .ifBlank { throw IllegalArgumentException("$where: falta \"prompt_$idioma\"") }
    }

    fun escrita(where: String, t: JSONObject, parte: String = ""): TareaEscrita {
        idNuevo(where, t.optString("id"))
        val seg = t.optInt("segundos", 0)
        if (seg <= 0) throw IllegalArgumentException("$where: falta \"segundos\"")
        val rub = jsonStrings(t.optJSONArray("rubrica"))
        if (rub.isEmpty()) throw IllegalArgumentException("$where: falta la rúbrica")
        return TareaEscrita(
            t.getString("id"), nivel(where, t), t.optString("palabras"), seg,
            prompt(where, t, "en"), prompt(where, t, "es"), rub,
            jsonStrings(t.optJSONArray("mensajes")), parte
        )
    }

    fun hablada(where: String, t: JSONObject, parte: String = ""): TareaHablada {
        idNuevo(where, t.optString("id"))
        val hablar = t.optInt("hablar_seg", 0).let { if (it > 0) it else t.optInt("hablarSeg", 0) }
        if (hablar <= 0) throw IllegalArgumentException("$where: falta \"hablar_seg\"")
        val rub = jsonStrings(t.optJSONArray("rubrica"))
        if (rub.isEmpty()) throw IllegalArgumentException("$where: falta la rúbrica")
        val prep = t.optInt("prep_seg", 0).let { if (it > 0) it else t.optInt("prepSeg", 0) }
        val imagenes = jsonStrings(t.optJSONArray("imagenes"))
        for (img in imagenes) {
            if (!Regex("^images/[a-z0-9_/-]+\\.(webp|jpg)$").matches(img)) throw IllegalArgumentException("$where: imagen \"$img\" (ruta dentro de assets, .webp o .jpg)")
        }
        return TareaHablada(
            t.getString("id"), nivel(where, t), prep.coerceAtLeast(0), hablar,
            prompt(where, t, "en"), prompt(where, t, "es"), rub, parte, t.optString("foto"), imagenes
        )
    }

    /** Las tareas de una destreza en un array ("tareas" o "items"), según la pista. */
    fun tareasDe(pistaId: String, where: String, arr: JSONArray?, parte: String = ""): List<TareaAptis> {
        val out = ArrayList<TareaAptis>()
        val a = arr ?: JSONArray()
        for (i in 0 until a.length()) {
            val t = a.getJSONObject(i)
            val w = "$where, tarea ${i + 1}"
            out.add(
                when (pistaId) {
                    "core" -> if (t.has("sub")) itemVocabulario(w, t) else itemGramatica(w, t)
                    "reading" -> lectura(w, t)
                    "listening" -> escucha(w, t)
                    "writing" -> escrita(w, t, parte)
                    "speaking" -> hablada(w, t, parte)
                    else -> throw IllegalArgumentException("$w: pista desconocida \"$pistaId\"")
                }
            )
        }
        return out
    }

    /**
     * Un banco de pista de Cowork (`aptis-<pista>.json`): o un array plano
     * "tareas"/"items", o "partes" (las partes del examen, cada una con su
     * "aptis" —"Parte 1 · una palabra"—, "title", "descripcion" y "tareas").
     */
    fun tareasDeArchivo(pistaId: String, where: String, json: JSONObject): List<TareaAptis> {
        val partes = json.optJSONArray("partes")
        if (partes == null) return tareasDe(pistaId, where, json.optJSONArray("tareas") ?: json.optJSONArray("items"))
        val out = ArrayList<TareaAptis>()
        for (i in 0 until partes.length()) {
            val p = partes.getJSONObject(i)
            val etiqueta = p.optString("aptis").ifBlank { p.optString("title") }
            if (etiqueta.isBlank()) throw IllegalArgumentException("$where, parte ${i + 1}: falta \"aptis\" o \"title\"")
            out.addAll(tareasDe(pistaId, "$where, parte ${i + 1}", p.optJSONArray("tareas"), etiqueta))
        }
        return out
    }
}

/** Lee y valida el simulacro (`aptis-diagnostico.json`). Cualquier defecto revienta con la ruta exacta. */
fun parseDiagnostico(json: JSONObject): Diagnostico = parseDiagnosticoCon(json, Lector(HashSet()))

internal fun parseDiagnosticoCon(json: JSONObject, lector: Lector): Diagnostico {
    val umbrales = parseUmbralesCore(json.optJSONObject("estimacion"))
    val secciones = ArrayList<SeccionAptis>()
    val ids = HashSet<String>()
    val arr = json.optJSONArray("secciones") ?: throw IllegalArgumentException("aptis-diagnostico.json: falta \"secciones\"")
    for (s in 0 until arr.length()) {
        val o = arr.getJSONObject(s)
        val whereS = "aptis-diagnostico.json, sección ${s + 1}"
        val id = o.optString("id")
        if (id.isBlank() || !ids.add(id)) throw IllegalArgumentException("$whereS: id vacío o repetido")
        if (PISTAS_APTIS.none { it.first == id }) throw IllegalArgumentException("$whereS: sección desconocida \"$id\"")
        val skill = o.optString("skill").ifBlank { throw IllegalArgumentException("$whereS: falta \"skill\"") }
        val title = o.optString("title").ifBlank { throw IllegalArgumentException("$whereS: falta \"title\"") }
        val minutos = o.optInt("minutos", 0)
        if (minutos <= 0) throw IllegalArgumentException("$whereS: \"minutos\" tiene que ser mayor que 0")
        val instruccion = o.optString("instruccion")
        val tareas = lector.tareasDe(id, whereS, o.optJSONArray(if (id == "core") "items" else "tareas"))
        if (tareas.isEmpty()) throw IllegalArgumentException("$whereS: sin tareas")
        var seg = 0
        if (id == "core") {
            seg = o.optInt("segundos_por_item", 0)
            if (seg <= 0) throw IllegalArgumentException("$whereS: falta \"segundos_por_item\"")
            for (l in listOf("A2", "B1", "B2")) if (tareas.none { it.level == l }) throw IllegalArgumentException("$whereS: no hay ítems de $l (la estimación los necesita)")
        }
        if ((id == "writing" || id == "speaking") && tareas.size < 2) throw IllegalArgumentException("$whereS: la estimación por IA necesita al menos 2 tareas")
        secciones.add(
            SeccionAptis(
                id, skill, title, minutos, instruccion, segundosPorItem = seg,
                core = tareas.filterIsInstance<ItemCore>(), lectura = tareas.filterIsInstance<TareaLectura>(),
                escucha = tareas.filterIsInstance<TareaEscucha>(), escritura = tareas.filterIsInstance<TareaEscrita>(),
                habla = tareas.filterIsInstance<TareaHablada>(), umbrales = umbrales
            )
        )
    }
    if (secciones.isEmpty()) throw IllegalArgumentException("aptis-diagnostico.json: sin secciones")
    return Diagnostico(json.optInt("duracion_min", 30), secciones)
}

/**
 * Arma el Modo Aptis entero a partir de los assets (se le pasa cómo leer cada
 * uno; null si no existe):
 * - `aptis-pistas.json` (obligatorio): promoción y tamaño de ronda por pista.
 * - `aptis-core-gramatica.json` y `aptis-core-vocabulario.json`: el banco del
 *   Core de Cowork (120 ítems), tal cual sus archivos.
 * - `aptis-<pista>.json` (opcional, `{"tareas": [...]}`): los bancos de las
 *   otras cuatro destrezas, cuando Cowork los mande.
 * - `aptis-diagnostico.json` (opcional): el simulacro; sus tareas se reciclan
 *   como primeras tareas de sus pistas.
 */
fun parseBancoAptis(leer: (String) -> String?): BancoAptis {
    val config = JSONObject(leer("aptis-pistas.json") ?: throw IllegalArgumentException("falta aptis-pistas.json"))
    val promocion = config.optJSONObject("promocion") ?: throw IllegalArgumentException("aptis-pistas.json: falta \"promocion\"")
    val ronda = config.optJSONObject("ronda") ?: throw IllegalArgumentException("aptis-pistas.json: falta \"ronda\"")
    val lector = Lector(HashSet())
    val simulacro = leer("aptis-diagnostico.json")?.let { parseDiagnosticoCon(JSONObject(it), lector) }
    val pistas = ArrayList<PistaAptis>()
    for ((id, skill) in PISTAS_APTIS) {
        val tareas = ArrayList<TareaAptis>()
        if (id == "core") {
            for (archivo in listOf("aptis-core-gramatica.json", "aptis-core-vocabulario.json")) {
                val json = leer(archivo)?.let { JSONObject(it) } ?: continue
                tareas.addAll(lector.tareasDe("core", archivo, json.optJSONArray("items")))
            }
        }
        leer("aptis-$id.json")?.let { JSONObject(it) }?.let { json ->
            tareas.addAll(lector.tareasDeArchivo(id, "aptis-$id.json", json))
        }
        // Las tareas del simulacro se reciclan en su pista, salvo las que repiten un ítem del banco (mismo enunciado).
        simulacro?.seccion(id)?.let { sec ->
            val vistos = tareas.filterIsInstance<ItemCore>().map { it.enunciado.lowercase(Locale.US) }.toHashSet()
            for (t in sec.tareas) {
                if (t is ItemCore && !vistos.add(t.enunciado.lowercase(Locale.US))) continue
                tareas.add(t)
            }
        }
        val cond = parseCondicion("aptis-pistas.json: promocion.$id", promocion.optString(id))
        if (cond.size != 1 || cond[0].level.isNotBlank()) throw IllegalArgumentException("aptis-pistas.json: promocion.$id tiene que ser una sola condición \"N de M\"")
        val n = ronda.optInt(id, 0)
        if (n <= 0) throw IllegalArgumentException("aptis-pistas.json: falta ronda.$id")
        val seg = if (id == "core") config.optInt("segundos_por_item_core", 30) else 0
        pistas.add(PistaAptis(id, skill, TITULOS_PISTA[id] ?: skill, tareas, cond[0], n, seg))
    }
    return BancoAptis(pistas, simulacro)
}

// ---------------------------------------------------------------------------
// Estimación (el simulacro: bloque "estimacion" del JSON)
// ---------------------------------------------------------------------------

object EstimacionAptis {

    private class Cuenta(val bien: Int, val total: Int)

    private fun cuenta(aciertos: List<Pair<String, Boolean>>, level: String): Cuenta {
        val de = aciertos.filter { it.first == level }
        return Cuenta(de.count { it.second }, de.size)
    }

    /**
     * Reading y Listening del simulacro (`_regla` v2): un nivel se da por
     * alcanzado solo si acierta TODOS sus ítems, y el estimado es el más alto
     * alcanzado; si ninguno, A1. Estricto a propósito (`_por_que_estricto`).
     * Le da igual si la lista viene del simulacro o de práctica acumulada.
     */
    fun porTodos(aciertos: List<Pair<String, Boolean>>): NivelAptis {
        for (n in NivelAptis.entries.reversed()) {
            val c = cuenta(aciertos, n.name)
            if (c.total > 0 && c.bien == c.total) return n
        }
        return NivelAptis.A1
    }

    /**
     * Core del simulacro: la tabla `estimacion.core` del JSON ("4 de 5 en A2 y 4
     * de 5 en B1"…), en proporciones: el nivel es el más alto cuyas condiciones
     * se cumplen todas; si ni A2, A1.
     */
    fun core(aciertos: List<Pair<String, Boolean>>, umbrales: Map<String, List<Condicion>>): NivelAptis {
        for (n in NivelAptis.entries.reversed()) {
            val conds = umbrales[n.name] ?: continue
            if (conds.all { c -> cuenta(aciertos, c.level).let { c.cumple(it.bien, it.total) } }) return n
        }
        return NivelAptis.A1
    }

    /**
     * Writing y Speaking (`reglas_ia`): la IA da un nivel por tarea y el estimado
     * es "el más alto alcanzado en DOS tareas": con dos tareas, el menor; con
     * tres, el del medio. Con menos de dos juicios válidos no hay estimación.
     */
    fun porIa(niveles: List<NivelAptis>): NivelAptis? {
        if (niveles.size < 2) return null
        return niveles.sortedDescending()[1]
    }
}

// ---------------------------------------------------------------------------
// Lo guardado (filesDir/aptis.json): las pistas y el simulacro
// ---------------------------------------------------------------------------

/** Un ítem del simulacro (Core, Reading, Listening): qué puso y si acertó. */
data class AciertoItem(val id: String, val level: String, val puesto: String, val ok: Boolean)

/** Una tarea de Writing o Speaking: lo del alumno y, cuando llega, el juicio de la IA con su prueba. */
data class JuicioIa(
    val id: String,
    val level: String,
    val texto: String,
    /** Writing: segundos que tardó. Speaking: segundos de voz medidos (sin pausas). */
    val segundos: Int,
    val nivel: NivelAptis? = null,
    val cita: String = "",
    val razon: String = "",
    val practica: String = "",
    /** Por qué no hay juicio todavía (sin conexión, sin clave, sin cita válida). */
    val error: String? = null,
    /** Speaking: segundos que duró la grabación entera, pausas incluidas. */
    val duracion: Int = 0
) {
    val valido: Boolean get() = nivel != null && cita.isNotBlank()

    fun aJson(): JSONObject = JSONObject().put("id", id).put("level", level).put("texto", texto).put("segundos", segundos)
        .put("nivel", nivel?.name ?: "").put("cita", cita).put("razon", razon).put("practica", practica)
        .put("error", error ?: "").put("duracion", duracion)

    companion object {
        fun deJson(j: JSONObject): JuicioIa = JuicioIa(
            j.getString("id"), j.optString("level"), j.optString("texto"), j.optInt("segundos"),
            NivelAptis.de(j.optString("nivel", "")), j.optString("cita"), j.optString("razon"), j.optString("practica"),
            j.optString("error").ifBlank { null }, j.optInt("duracion")
        )
    }
}

/** Una parte del simulacro ya hecha. */
data class ResultadoSeccion(
    val id: String,
    val fecha: String,
    val items: List<AciertoItem> = emptyList(),
    val juicios: List<JuicioIa> = emptyList()
) {
    /** Si Cowork cambia el contenido (otros ids), lo guardado ya no describe esta parte: hay que repetirla. */
    fun vigente(seccion: SeccionAptis): Boolean {
        val guardados = (if (seccion.porIa) juicios.map { it.id } else items.map { it.id }).toSet()
        return guardados == seccion.tareas.map { it.id }.toSet()
    }

    /** El nivel que sale de lo guardado; null si a la IA le faltan juicios válidos o si el contenido cambió. */
    fun nivel(seccion: SeccionAptis): NivelAptis? = when {
        !vigente(seccion) -> null
        seccion.porIa -> EstimacionAptis.porIa(juicios.mapNotNull { if (it.valido) it.nivel else null })
        seccion.id == "core" -> EstimacionAptis.core(items.map { it.level to it.ok }, seccion.umbrales)
        else -> EstimacionAptis.porTodos(items.map { it.level to it.ok })
    }

    val pendientesIa: List<JuicioIa> get() = juicios.filter { !it.valido }
}

/** Un intento en una pista: la tarea, su nivel, si contó como acierto y, en Writing/Speaking, el juicio. */
data class Intento(
    val id: String,
    val level: String,
    val ok: Boolean,
    val fecha: String,
    val puesto: String = "",
    val juicio: JuicioIa? = null
)

/**
 * Lo que pasó al registrar un intento: la pista dio por ALCANZADO un nivel
 * ([alcanzado]) y, si hay uno más arriba con tareas, pasa a entrenarlo ([a]).
 */
data class Promocion(val alcanzado: NivelAptis, val a: NivelAptis?)

class Aptis(private val file: File) {

    /**
     * Dos niveles por pista, a propósito: [nivel] es el que se ENTRENA (arranca
     * donde el banco tenga tareas) y [alcanzado] el más alto cuya condición ya
     * se cumplió (null hasta la primera promoción). El tablero y el piso miran
     * el alcanzado: si un banco solo trae B1 y B2 (Writing hoy), entrenar B1 no
     * es haber llegado a B1.
     */
    private class EstadoPista(var nivel: NivelAptis?, var alcanzado: NivelAptis?, val historial: ArrayList<Intento>)

    private val pistas = HashMap<String, EstadoPista>()
    private val simulacro = HashMap<String, ResultadoSeccion>()
    private var cargado = false
    private val dia = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** Cambia con cada guardado, para que las pantallas se refresquen. */
    var tick by mutableStateOf(0)
        private set

    fun hoy(): String = dia.format(Date())

    private fun cargar() {
        if (cargado) return
        cargado = true
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText())
            if (root.optInt("version", 0) < 2) {
                Log.i(TAG, "${file.name} es del diagnóstico viejo; se empieza de cero")
                return
            }
            val ps = root.optJSONObject("pistas") ?: JSONObject()
            for (id in ps.keys()) {
                val o = ps.getJSONObject(id)
                val hist = ArrayList<Intento>()
                val ha = o.optJSONArray("historial") ?: JSONArray()
                for (i in 0 until ha.length()) {
                    val it = ha.getJSONObject(i)
                    hist.add(Intento(it.getString("id"), it.optString("level"), it.optBoolean("ok"), it.optString("fecha"), it.optString("puesto"), it.optJSONObject("juicio")?.let { j -> JuicioIa.deJson(j) }))
                }
                pistas[id] = EstadoPista(NivelAptis.de(o.optString("nivel")), NivelAptis.de(o.optString("alcanzado")), hist)
            }
            val ss = root.optJSONObject("simulacro") ?: JSONObject()
            for (id in ss.keys()) {
                val o = ss.getJSONObject(id)
                val items = ArrayList<AciertoItem>()
                val ia = o.optJSONArray("items") ?: JSONArray()
                for (i in 0 until ia.length()) {
                    val it = ia.getJSONObject(i)
                    items.add(AciertoItem(it.getString("id"), it.optString("level"), it.optString("puesto"), it.optBoolean("ok")))
                }
                val juicios = ArrayList<JuicioIa>()
                val ja = o.optJSONArray("juicios") ?: JSONArray()
                for (i in 0 until ja.length()) juicios.add(JuicioIa.deJson(ja.getJSONObject(i)))
                simulacro[id] = ResultadoSeccion(id, o.optString("fecha"), items, juicios)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "No se pudo leer ${file.name}; se empieza de cero", e)
            pistas.clear(); simulacro.clear()
        }
    }

    private fun escribir() {
        val ps = JSONObject()
        for ((id, p) in pistas) {
            val hist = JSONArray()
            for (it in p.historial) {
                val o = JSONObject().put("id", it.id).put("level", it.level).put("ok", it.ok).put("fecha", it.fecha).put("puesto", it.puesto)
                if (it.juicio != null) o.put("juicio", it.juicio.aJson())
                hist.put(o)
            }
            ps.put(id, JSONObject().put("nivel", p.nivel?.name ?: "").put("alcanzado", p.alcanzado?.name ?: "").put("historial", hist))
        }
        val ss = JSONObject()
        for ((id, r) in simulacro) {
            val items = JSONArray()
            for (it in r.items) items.put(JSONObject().put("id", it.id).put("level", it.level).put("puesto", it.puesto).put("ok", it.ok))
            val juicios = JSONArray()
            for (j in r.juicios) juicios.put(j.aJson())
            ss.put(id, JSONObject().put("fecha", r.fecha).put("items", items).put("juicios", juicios))
        }
        try {
            file.parentFile?.mkdirs()
            file.writeText(JSONObject().put("version", 2).put("pistas", ps).put("simulacro", ss).toString())
        } catch (e: Throwable) {
            Log.e(TAG, "No se pudo guardar ${file.name}", e)
        }
        tick += 1
    }

    private fun estado(pista: PistaAptis): EstadoPista {
        cargar()
        return pistas.getOrPut(pista.id) { EstadoPista(null, null, ArrayList()) }
    }

    // --- pistas ---------------------------------------------------------------

    /** El nivel en que está entrenando la pista: donde arranca el banco hasta que la promoción la suba. */
    fun nivel(pista: PistaAptis): NivelAptis {
        val e = estado(pista)
        val n = e.nivel ?: return pista.nivelInicial
        // si el banco perdió ese nivel (contenido cambiado), se entrena el más cercano que exista
        return if (pista.de(n).isNotEmpty()) n else pista.niveles.lastOrNull { it <= n } ?: pista.nivelInicial
    }

    /** El nivel más alto que la pista ya dio por alcanzado; null si todavía ninguno. Es lo que mira el tablero. */
    fun alcanzado(pista: PistaAptis): NivelAptis? = estado(pista).alcanzado

    fun historial(pista: PistaAptis): List<Intento> = estado(pista).historial.toList()

    fun hechas(pista: PistaAptis): Int = estado(pista).historial.size

    /** Los últimos [n] intentos en ese nivel (del más viejo al más nuevo). */
    fun ultimos(pista: PistaAptis, nivel: NivelAptis, n: Int): List<Intento> =
        estado(pista).historial.filter { it.level == nivel.name }.takeLast(n)

    /**
     * Registra un intento y aplica la regla de promoción: con al menos N
     * intentos en el nivel que se entrena (N = "de" de la condición), si los
     * últimos N la cumplen, ese nivel queda ALCANZADO y la pista pasa al
     * siguiente con tareas (si lo hay). Solo sube, nunca baja (para lo flojo
     * está [flojo]). Devuelve la promoción solo cuando el alcanzado cambia.
     */
    fun registrar(pista: PistaAptis, intento: Intento): Promocion? {
        val e = estado(pista)
        e.historial.add(intento)
        while (e.historial.size > MAX_HISTORIAL) e.historial.removeAt(0)
        val actual = nivel(pista)
        if (e.nivel == null) e.nivel = actual
        var promocion: Promocion? = null
        if (intento.level == actual.name) {
            val n = pista.promocion.de
            val ultimos = ultimos(pista, actual, n)
            if (ultimos.size >= n && pista.promocion.cumple(ultimos.count { it.ok }, ultimos.size)) {
                val sig = pista.siguienteCon(actual)
                if (sig != null) e.nivel = sig
                if (e.alcanzado == null || actual > e.alcanzado!!) {
                    e.alcanzado = actual
                    promocion = Promocion(actual, sig)
                    Log.i(TAG, "${pista.id}: alcanza $actual (${ultimos.count { it.ok }} de ${ultimos.size})" + (sig?.let { ", entrena $it" } ?: ""))
                }
            }
        }
        escribir()
        return promocion
    }

    /** Las últimas N del nivel actual van flojas (menos de la mitad bien): la ronda mezcla el nivel anterior. */
    fun flojo(pista: PistaAptis): Boolean {
        val n = pista.promocion.de
        val u = ultimos(pista, nivel(pista), n)
        return u.size >= n && u.count { it.ok } * 2 < u.size
    }

    /** Cuántas veces se ha hecho cada tarea, para servir primero las nunca vistas. */
    private fun ordenados(pista: PistaAptis, nivel: NivelAptis, rnd: Random): List<TareaAptis> {
        val hist = estado(pista).historial
        val ultimaVez = HashMap<String, Int>()
        hist.forEachIndexed { i, it -> ultimaVez[it.id] = i }
        val (vistas, nuevas) = pista.de(nivel).partition { it.id in ultimaVez }
        return nuevas.shuffled(rnd) + vistas.sortedBy { ultimaVez[it.id] }
    }

    /**
     * Arma una ronda: tareas del nivel actual, primero las nunca vistas y luego
     * las más viejas. Si el nivel va flojo, la mitad sale del nivel anterior
     * (repasar sin degradar). Vacía si el banco no tiene tareas ahí.
     */
    fun ronda(pista: PistaAptis, rnd: Random = Random.Default): List<TareaAptis> {
        val actual = nivel(pista)
        val principal = ordenados(pista, actual, rnd)
        val n = pista.ronda
        val previo = if (flojo(pista)) pista.anteriorCon(actual) else null
        if (previo == null) return principal.take(n)
        val apoyo = ordenados(pista, previo, rnd)
        val out = ArrayList<TareaAptis>()
        var i = 0; var j = 0
        while (out.size < n && (i < principal.size || j < apoyo.size)) {
            if (i < principal.size) out.add(principal[i++])
            if (out.size < n && j < apoyo.size) out.add(apoyo[j++])
        }
        return out
    }

    /** Nivel alcanzado de todas las pistas (null donde todavía ninguno). */
    fun niveles(banco: BancoAptis): Map<String, NivelAptis?> = banco.pistas.associate { it.id to alcanzado(it) }

    private fun orden(pista: PistaAptis): Int = alcanzado(pista)?.ordinal ?: -1

    /** Las destrezas más flojas de las CUATRO que Aptis exige (varias si empatan; sin nivel cuenta como lo más bajo). El Core no entra: es el desempate. */
    fun piso(banco: BancoAptis): List<PistaAptis> {
        val minimo = banco.cuatro.minOfOrNull { orden(it) } ?: return emptyList()
        return banco.cuatro.filter { orden(it) == minimo }
    }

    /** El simulacro completo se abre cuando las cinco pistas han ALCANZADO B1 o más. */
    fun simulacroDesbloqueado(banco: BancoAptis): Boolean = banco.pistas.all { (alcanzado(it) ?: NivelAptis.A1) >= NivelAptis.B1 && alcanzado(it) != null }

    fun faltanParaSimulacro(banco: BancoAptis): List<PistaAptis> = banco.pistas.filter { alcanzado(it)?.let { n -> n < NivelAptis.B1 } ?: true }

    // --- simulacro ------------------------------------------------------------

    fun resultadoSimulacro(id: String): ResultadoSeccion? { cargar(); return simulacro[id] }

    fun guardarSimulacro(r: ResultadoSeccion) { cargar(); simulacro[r.id] = r; escribir() }

    fun borrarSimulacro() { cargar(); simulacro.clear(); escribir() }

    fun hechasSimulacro(diag: Diagnostico): Int { cargar(); return diag.secciones.count { simulacro[it.id] != null } }

    /** Nivel estimado por parte del simulacro; null donde falta hacerla o la IA no dio pruebas. */
    fun nivelesSimulacro(diag: Diagnostico): Map<String, NivelAptis?> {
        cargar()
        return diag.secciones.associate { s -> s.id to simulacro[s.id]?.nivel(s) }
    }

    fun simulacroCompleto(diag: Diagnostico): Boolean = nivelesSimulacro(diag).values.all { it != null }

    fun pisoSimulacro(diag: Diagnostico): List<SeccionAptis> {
        val n = nivelesSimulacro(diag)
        val minimo = n.values.filterNotNull().minOrNull() ?: return emptyList()
        return diag.secciones.filter { n[it.id] == minimo }
    }

    fun borrarTodo() { cargar(); pistas.clear(); simulacro.clear(); if (file.exists()) file.delete(); tick += 1 }

    companion object {
        private const val TAG = "HabloAptis"
        private const val MAX_HISTORIAL = 600
    }
}

// ---------------------------------------------------------------------------
// Frases de las tarjetas del simulacro (Core, Reading, Listening; las de IA vienen del juicio)
// ---------------------------------------------------------------------------

object TarjetaAptis {

    /** "Acertaste 3 de 3 en A2, 2 de 4 en B1 y 1 de 5 en B2." */
    fun justificacion(items: List<AciertoItem>): String {
        val partes = NivelAptis.entries.mapNotNull { n ->
            val de = items.filter { it.level == n.name }
            if (de.isEmpty()) null else "${de.count { it.ok }} de ${de.size} en ${n.name}"
        }
        if (partes.isEmpty()) return ""
        val texto = if (partes.size == 1) partes[0] else partes.dropLast(1).joinToString(", ") + " y " + partes.last()
        return "Acertaste $texto."
    }

    /** UNA cosa que practicar, sacada de lo que falló más abajo. */
    fun practica(seccion: SeccionAptis, items: List<AciertoItem>): String {
        val fallado = items.filter { !it.ok }.minByOrNull { NivelAptis.de(it.level)?.ordinal ?: 9 }
            ?: return "Nada que corregir en esta parte: sigue con lo que quede más flojo."
        if (seccion.id == "core") {
            val item = seccion.core.firstOrNull { it.id == fallado.id } ?: return "Repasa la gramática de ${fallado.level}."
            return "La frase que fallaste más abajo (${item.level}): «${item.resuelto}»"
        }
        val lectura = seccion.lectura.firstOrNull { it.id == fallado.id }
        if (lectura != null) return when (lectura.tipo) {
            "ordenar" -> "Ordenar las frases de un relato (parte 2 de Reading): fíjate en los conectores y en el tiempo."
            "titulos" -> "Elegir el título de cada párrafo (parte 4 de Reading): busca la idea principal, no una palabra suelta."
            else -> "Completar frases con la palabra exacta (parte 1 de Reading): vocabulario de ${lectura.level}."
        }
        val escucha = seccion.escucha.firstOrNull { it.id == fallado.id }
        if (escucha != null) return when (escucha.tipo) {
            "quien" -> "Distinguir quién dice qué en un diálogo (parte 3 de Listening): opiniones y acuerdos."
            else -> "Captar horas, números y datos al oído (parte 1 de Listening): dictado de números y horas."
        }
        return "Repite las tareas de ${fallado.level} de esta parte."
    }
}

// ---------------------------------------------------------------------------
// El juez: Claude contra la rúbrica, con cita obligatoria
// ---------------------------------------------------------------------------

object JuezAptis {

    /** Sonnet: es el que mejor juzga; una llamada por tarea de Writing o Speaking (~$0,004). */
    const val MODELO = ClaudeLlm.DEFAULT_MODEL
    const val MAX_TOKENS = 600

    val SYSTEM: String = """
Eres examinadora de inglés para hispanohablantes. Estimas en qué nivel del MCER está UNA respuesta de un alumno a una tarea tipo Aptis, comparándola con la rúbrica de esa tarea. Es una estimación para saber por dónde empezar a estudiar, no una nota oficial.

Niveles que puedes dar (solo estos cuatro):
- "<A2": no llega a A2: palabras sueltas, frases que no se entienden, o casi nada escrito.
- "A2": frases simples y cortas, unidas con and/but/because; presente y pasado básicos; errores frecuentes, pero se entiende lo esencial.
- "B1": texto seguido y conectado; describe, explica y da razones; controla razonablemente los tiempos verbales; los errores no impiden entender.
- "B2": argumenta o expone con claridad y orden; registro adecuado a la tarea; conectores variados; pocos errores y ninguno estorba. (C1 o más también se reporta como "B2".)

Reglas:
1. Juzga SOLO lo que hay en el texto. No supongas lo que el alumno "sabría".
2. La PRUEBA es obligatoria: "cita" es un fragmento COPIADO EXACTO del texto del alumno (mismas palabras, mismos errores, sin corregir, sin traducir, sin puntos suspensivos), de al menos tres palabras seguidas si el texto las tiene. Sin cita exacta el juicio no vale y se descarta.
3. La cita tiene que sostener el nivel: si dices B1, la cita muestra una frase conectada que explica o da razones; si dices A2, la cita muestra la limitación (frase suelta, tiempo verbal fallido, lista sin unir…).
4. Sé exigente pero justa: no regales un nivel que la cita no demuestra, y no bajes un nivel por un error aislado si el resto lo sostiene. Aptis exige B1 en cada destreza.
5. Si el texto es una TRANSCRIPCIÓN de voz (se te avisa), la puntuación la puso el reconocedor y puede traer palabras mal oídas, palabras cambiadas por otras parecidas o repetidas ("so make maybe"), nombres raros y muletillas inventadas al final ("Mm-hmm", "Okay"): nada de eso es error del alumno, y NO se juzga la pronunciación. Sí se juzgan gramática, vocabulario, conexión entre ideas, cuánto habló y si cumplió la tarea.
6. "practica" es UNA sola cosa concreta y pequeña que el alumno puede practicar esta semana, en español, en una frase.
7. "razon" es una frase en español que explica el nivel A PARTIR de la cita.

Responde SOLO con un JSON así, sin nada antes ni después:
{"nivel":"<A2"|"A2"|"B1"|"B2","cita":"…","razon":"…","practica":"…","rubrica":[true,false,…]}
"rubrica" lleva un true/false por cada pregunta de la rúbrica, en su orden.
""".trim()

    fun promptEscrita(t: TareaEscrita, texto: String, segundos: Int, citaMala: String? = null): String {
        val sb = StringBuilder()
        sb.append("TAREA de Writing (nivel objetivo ${t.level}" + (if (t.parte.isNotBlank()) ", ${t.parte}" else "") + "): ${t.promptEn}\n")
        if (t.mensajes.isNotEmpty()) {
            sb.append("MENSAJES A LOS QUE RESPONDE, en orden:\n")
            t.mensajes.forEachIndexed { i, m -> sb.append("${i + 1}. $m\n") }
        }
        sb.append(if (t.palabras.isNotBlank()) "Se pedían ${t.palabras} palabras" else "Se pedía lo que dice la tarea")
        sb.append(" en ${t.segundos} segundos. El alumno escribió ${palabras(texto)} palabras")
        if (segundos > 0) sb.append(" en $segundos segundos")
        sb.append(".\n")
        sb.append(rubrica(t.rubrica))
        sb.append(bloqueTexto(texto, citaMala))
        return sb.toString()
    }

    fun promptHablada(t: TareaHablada, transcripcion: String, segundosVoz: Int, citaMala: String? = null, duracion: Int = 0): String {
        val sb = StringBuilder()
        sb.append("TAREA de Speaking (nivel objetivo ${t.level}): ${t.promptEn}\n")
        if (t.foto.isNotBlank()) sb.append("LA FOTO (o fotos) que el alumno tenía delante: ${t.foto}\n")
        sb.append("Tenía que hablar ${t.hablarSeg} segundos")
        if (duracion > 0) sb.append("; la grabación duró $duracion segundos, con $segundosVoz segundos de voz (el resto, pausas). ")
        else sb.append("; se midieron $segundosVoz segundos de voz. ")
        sb.append("Lo que sigue es la TRANSCRIPCIÓN automática del reconocedor de voz (${palabras(transcripcion)} palabras), sin puntuación.\n")
        sb.append(rubrica(t.rubrica))
        sb.append(bloqueTexto(transcripcion, citaMala))
        return sb.toString()
    }

    private fun rubrica(r: List<String>): String =
        "RÚBRICA:\n" + r.mapIndexed { i, q -> "${i + 1}. $q" }.joinToString("\n") + "\n"

    private fun bloqueTexto(texto: String, citaMala: String?): String {
        val sb = StringBuilder()
        if (citaMala != null) {
            sb.append("ATENCIÓN: en tu respuesta anterior la cita «$citaMala» NO aparece textual en el texto del alumno. ")
            sb.append("Copia un fragmento EXACTO, letra por letra, o el juicio se descarta.\n")
        }
        sb.append("TEXTO DEL ALUMNO (entre las líneas de ---):\n---\n").append(texto.trim()).append("\n---")
        return sb.toString()
    }

    fun palabras(texto: String): Int = texto.trim().split(Regex("\\s+")).count { it.isNotBlank() }

    /** Lo que Parakeet inventa cuando una ventana queda en silencio (medido el 16-09: "Mm-hmm.", "Okay."). */
    private val MULETILLAS = setOf("mm-hmm", "mmhmm", "mhm", "hmm", "hm", "mm", "uh-huh", "uh", "um", "okay", "ok", "yeah", "yep")

    /**
     * Corta las muletillas que el reconocedor inventa AL FINAL de la transcripción
     * (una por ventana de silencio, así que pueden venir varias seguidas). Regla
     * de Cowork: un juicio de nivel no puede apoyarse en algo que el alumno no
     * dijo. Solo al final: un "okay" en medio sí puede ser suyo.
     */
    fun sinMuletillas(texto: String): String {
        var t = texto.trim()
        while (true) {
            val m = Regex("(?i)(?:^|\\s)([a-z-]+)[.!?,]*$").find(t) ?: break
            if (m.groupValues[1].lowercase(Locale.US) !in MULETILLAS) break
            t = t.substring(0, m.range.first).trim()
        }
        return t
    }

    /** Lo que devolvió la IA, ya leído; [cita] puede no valer: ver [citaAparece]. */
    class Veredicto(val nivel: NivelAptis, val cita: String, val razon: String, val practica: String)

    /** Saca el JSON de la respuesta (aunque venga con texto alrededor). Null si no se puede leer. */
    fun interpretar(respuesta: String?): Veredicto? {
        if (respuesta == null) return null
        val ini = respuesta.indexOf('{')
        val fin = respuesta.lastIndexOf('}')
        if (ini < 0 || fin <= ini) return null
        return try {
            val o = JSONObject(respuesta.substring(ini, fin + 1))
            val nivel = NivelAptis.de(o.optString("nivel")) ?: return null
            Veredicto(nivel, o.optString("cita").trim(), o.optString("razon").trim(), o.optString("practica").trim())
        } catch (e: Throwable) {
            null
        }
    }

    private fun llano(s: String): String =
        s.lowercase(Locale.US)
            .replace('’', '\'').replace('‘', '\'').replace('“', '"').replace('”', '"')
            .replace('…', ' ')
            .filter { it.isLetterOrDigit() || it == ' ' || it == '\'' }
            .split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ")

    /**
     * La regla de Cowork: sin evidencia el juicio no vale. La cita tiene que
     * estar, tal cual (ignorando mayúsculas y puntuación), dentro de lo que el
     * alumno escribió o dijo, y tener al menos tres palabras si el texto las tiene.
     */
    fun citaAparece(cita: String, texto: String): Boolean {
        val c = llano(cita)
        val t = llano(texto)
        if (c.isBlank() || t.isBlank()) return false
        val minimo = minOf(3, t.split(" ").size)
        if (c.split(" ").size < minimo) return false
        return t.contains(c)
    }

    /** Sin texto no hay nada que estimar, y no hace falta llamar a nadie. */
    fun sinTexto(j: JuicioIa): JuicioIa = j.copy(
        nivel = NivelAptis.A1,
        cita = "(no hubo texto)",
        razon = if (j.segundos > 0) "No se entendió nada de lo grabado." else "No escribiste ni dijiste nada en esta tarea.",
        practica = "Responde aunque sea con dos frases: en blanco no se puede estimar nada.",
        error = null
    )

    /**
     * Juzga una tarea. Si la cita no aparece en el texto, se le reclama UNA vez;
     * si vuelve a fallar, el juicio queda sin nivel y con el motivo en [JuicioIa.error].
     */
    fun juzgar(claude: ClaudeLlm, tarea: TareaAptis?, j: JuicioIa, onDone: (JuicioIa) -> Unit) {
        if (j.texto.isBlank()) { onDone(sinTexto(j)); return }
        val escrita = tarea as? TareaEscrita
        val hablada = tarea as? TareaHablada
        if (escrita == null && hablada == null) { onDone(j.copy(error = "Tarea desconocida ${j.id}")); return }
        fun prompt(citaMala: String?): String =
            if (escrita != null) promptEscrita(escrita, j.texto, j.segundos, citaMala) else promptHablada(hablada!!, j.texto, j.segundos, citaMala, j.duracion)

        fun intento(citaMala: String?) {
            claude.preguntar(MODELO, SYSTEM, prompt(citaMala), MAX_TOKENS) { respuesta, error ->
                if (respuesta == null) {
                    onDone(j.copy(error = error ?: "Sin respuesta de Claude"))
                    return@preguntar
                }
                val v = interpretar(respuesta)
                if (v == null) {
                    Log.w(TAG, "respuesta ilegible para ${j.id}: ${respuesta.take(200)}")
                    onDone(j.copy(error = "La IA no respondió en el formato esperado"))
                    return@preguntar
                }
                if (citaAparece(v.cita, j.texto)) {
                    Log.i(TAG, "${j.id}: ${v.nivel} por «${v.cita}»")
                    onDone(j.copy(nivel = v.nivel, cita = v.cita, razon = v.razon, practica = v.practica, error = null))
                } else if (citaMala == null) {
                    Log.w(TAG, "${j.id}: cita «${v.cita}» no aparece en el texto; se reclama")
                    intento(v.cita)
                } else {
                    Log.w(TAG, "${j.id}: segunda cita «${v.cita}» tampoco aparece; juicio descartado")
                    onDone(j.copy(nivel = null, cita = "", razon = "", practica = "", error = "La IA no citó una frase tuya como prueba: el juicio no vale"))
                }
            }
        }
        intento(null)
    }

    /** Juzga en fila las tareas pendientes de una parte del simulacro, avisando cada una. */
    fun juzgarPendientes(claude: ClaudeLlm, seccion: SeccionAptis, juicios: List<JuicioIa>, onCada: (List<JuicioIa>) -> Unit, onFin: (List<JuicioIa>) -> Unit) {
        val lista = ArrayList(juicios)
        fun siguiente(i: Int) {
            val idx = (i until lista.size).firstOrNull { !lista[it].valido } ?: run { onFin(lista.toList()); return }
            juzgar(claude, seccion.tareas.firstOrNull { it.id == lista[idx].id }, lista[idx]) { nuevo ->
                lista[idx] = nuevo
                onCada(lista.toList())
                siguiente(idx + 1)
            }
        }
        siguiente(0)
    }

    private const val TAG = "HabloAptis"
}
