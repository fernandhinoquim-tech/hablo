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

/**
 * **Modo Aptis, primer paso: el diagnóstico** (etapa 5). Aptis ESOL General le
 * exige a Fero B1 o superior en las CUATRO destrezas: es un piso, no un
 * promedio, así que lo que manda es la más floja. Este diagnóstico
 * (`assets/content/aptis-diagnostico.json`, escrito por Cowork: ~30 minutos,
 * 23 tareas, cinco partes que se hacen POR SEPARADO) estima el nivel de cada
 * destreza y dice cuál es el piso.
 *
 * Lo que no se negocia (pedido de Cowork, 2026-09-16):
 * - En pantalla, siempre: [AVISO]. Prometer una nota exacta sería mentir.
 * - Writing y Speaking los estima la IA contra la rúbrica de cada tarea y
 *   tiene que CITAR una frase del alumno como prueba de cada juicio; una cita
 *   que no aparece textual en lo que escribió o dijo invalida el juicio
 *   ([JuezAptis.citaAparece]).
 * - La salida es una tarjeta por destreza y, arriba en grande, el piso.
 */
const val AVISO_APTIS = "Esto es una estimación, no tu nota de Aptis. Sirve para saber por dónde empezar."

/** Nivel estimado. El orden de declaración es el orden real: el piso es el mínimo. */
enum class NivelAptis(val etiqueta: String) {
    BAJO_A2("por debajo de A2"), A2("A2"), B1("B1"), B2("B2");

    companion object {
        /** Lee lo que devuelve la IA o lo guardado. C1/C2 se reportan como B2: el diagnóstico no distingue más arriba. */
        fun de(texto: String?): NivelAptis? = when (texto?.trim()?.uppercase(Locale.US)?.replace(" ", "")) {
            "A2" -> A2
            "B1" -> B1
            "B2", "C1", "C2" -> B2
            "<A2", "A1", "A0", "BAJO_A2", "PORDEBAJODEA2", "MENOSA2" -> BAJO_A2
            else -> null
        }
    }
}

// ---------------------------------------------------------------------------
// Contenido (aptis-diagnostico.json)
// ---------------------------------------------------------------------------

data class ItemCore(val id: String, val level: String, val text: String, val options: List<String>, val answer: String)

sealed class TareaLectura(val id: String, val level: String, val tipo: String) {
    class Completar(id: String, level: String, val text: String, val options: List<String>, val answer: String) :
        TareaLectura(id, level, "completar")

    class Ordenar(id: String, level: String, val instruccion: String, val primera: String, val desordenadas: List<String>, val orden: List<String>) :
        TareaLectura(id, level, "ordenar")

    class Titulos(id: String, level: String, val instruccion: String, val parrafos: List<String>, val titulos: List<String>, val answer: List<String>) :
        TareaLectura(id, level, "titulos")
}

data class TareaEscucha(val id: String, val tipo: String, val level: String, val audio: String, val pregunta: String, val options: List<String>, val answer: String)

data class TareaEscrita(val id: String, val level: String, val palabras: String, val segundos: Int, val promptEn: String, val promptEs: String, val rubrica: List<String>)

data class TareaHablada(val id: String, val level: String, val prepSeg: Int, val hablarSeg: Int, val promptEn: String, val promptEs: String, val rubrica: List<String>)

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
    val habla: List<TareaHablada> = emptyList()
) {
    /** Writing y Speaking: los juzga la IA contra la rúbrica. */
    val porIa: Boolean get() = escritura.isNotEmpty() || habla.isNotEmpty()
    val cuantas: Int get() = core.size + lectura.size + escucha.size + escritura.size + habla.size
    val emoji: String
        get() = when (id) {
            "core" -> "🧩"; "reading" -> "📖"; "listening" -> "🎧"; "writing" -> "✍️"; "speaking" -> "🎤"; else -> "🎯"
        }
}

class Diagnostico(val duracionMin: Int, val secciones: List<SeccionAptis>) {
    fun seccion(id: String): SeccionAptis? = secciones.firstOrNull { it.id == id }
    val tareas: Int get() = secciones.sumOf { it.cuantas }
}

private val NIVELES_ITEM = setOf("A2", "B1", "B2")

/** Lee y valida el JSON. Cualquier defecto revienta con la ruta exacta, como el resto del contenido. */
fun parseDiagnostico(json: JSONObject): Diagnostico {
    val secciones = ArrayList<SeccionAptis>()
    val ids = HashSet<String>()
    val idsTarea = HashSet<String>()
    fun idNuevo(where: String, id: String) {
        if (id.isBlank()) throw IllegalArgumentException("$where: falta \"id\"")
        if (!idsTarea.add(id)) throw IllegalArgumentException("$where: id repetido \"$id\"")
    }
    fun nivel(where: String, o: JSONObject): String {
        val l = o.optString("level")
        if (l !in NIVELES_ITEM) throw IllegalArgumentException("$where: level \"$l\" no es A2, B1 ni B2")
        return l
    }
    fun opciones(where: String, o: JSONObject, campo: String = "options"): List<String> {
        val opts = jsonStrings(o.optJSONArray(campo))
        if (opts.size < 2) throw IllegalArgumentException("$where: hacen falta al menos 2 opciones")
        if (opts.toSet().size != opts.size) throw IllegalArgumentException("$where: opciones repetidas")
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

    val arr = json.optJSONArray("secciones") ?: throw IllegalArgumentException("aptis-diagnostico.json: falta \"secciones\"")
    for (s in 0 until arr.length()) {
        val o = arr.getJSONObject(s)
        val whereS = "aptis-diagnostico.json, sección ${s + 1}"
        val id = o.optString("id")
        if (id.isBlank() || !ids.add(id)) throw IllegalArgumentException("$whereS: id vacío o repetido")
        val skill = o.optString("skill").ifBlank { throw IllegalArgumentException("$whereS: falta \"skill\"") }
        val title = o.optString("title").ifBlank { throw IllegalArgumentException("$whereS: falta \"title\"") }
        val minutos = o.optInt("minutos", 0)
        if (minutos <= 0) throw IllegalArgumentException("$whereS: \"minutos\" tiene que ser mayor que 0")
        val instruccion = o.optString("instruccion")

        when (id) {
            "core" -> {
                val seg = o.optInt("segundos_por_item", 0)
                if (seg <= 0) throw IllegalArgumentException("$whereS: falta \"segundos_por_item\"")
                val items = o.optJSONArray("items") ?: JSONArray()
                val core = ArrayList<ItemCore>()
                for (i in 0 until items.length()) {
                    val it = items.getJSONObject(i)
                    val where = "$whereS, ítem ${i + 1}"
                    idNuevo(where, it.optString("id"))
                    val text = it.optString("text")
                    hueco(where, text)
                    val opts = opciones(where, it)
                    core.add(ItemCore(it.getString("id"), nivel(where, it), text, opts, respuesta(where, it, opts)))
                }
                for (l in NIVELES_ITEM) if (core.none { it.level == l }) throw IllegalArgumentException("$whereS: no hay ítems de $l (la estimación los necesita)")
                secciones.add(SeccionAptis(id, skill, title, minutos, instruccion, segundosPorItem = seg, core = core))
            }
            "reading" -> {
                val tareas = o.optJSONArray("tareas") ?: JSONArray()
                val lectura = ArrayList<TareaLectura>()
                for (i in 0 until tareas.length()) {
                    val t = tareas.getJSONObject(i)
                    val where = "$whereS, tarea ${i + 1}"
                    idNuevo(where, t.optString("id"))
                    val tid = t.getString("id")
                    val lvl = nivel(where, t)
                    when (val tipo = t.optString("tipo")) {
                        "completar" -> {
                            val text = t.optString("text"); hueco(where, text)
                            val opts = opciones(where, t)
                            lectura.add(TareaLectura.Completar(tid, lvl, text, opts, respuesta(where, t, opts)))
                        }
                        "ordenar" -> {
                            val primera = t.optString("primera").ifBlank { throw IllegalArgumentException("$where: falta \"primera\"") }
                            val des = jsonStrings(t.optJSONArray("desordenadas"))
                            val orden = jsonStrings(t.optJSONArray("orden"))
                            if (des.size < 2) throw IllegalArgumentException("$where: hacen falta al menos 2 frases desordenadas")
                            if (des.toSet().size != des.size) throw IllegalArgumentException("$where: frases repetidas")
                            if (orden.sorted() != des.sorted()) throw IllegalArgumentException("$where: \"orden\" no es una permutación de \"desordenadas\"")
                            lectura.add(TareaLectura.Ordenar(tid, lvl, t.optString("instruccion"), primera, des, orden))
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
                            lectura.add(TareaLectura.Titulos(tid, lvl, t.optString("instruccion"), parrafos, titulos, answer))
                        }
                        else -> throw IllegalArgumentException("$where: tipo desconocido \"$tipo\"")
                    }
                }
                if (lectura.isEmpty()) throw IllegalArgumentException("$whereS: sin tareas")
                secciones.add(SeccionAptis(id, skill, title, minutos, instruccion, lectura = lectura))
            }
            "listening" -> {
                val tareas = o.optJSONArray("tareas") ?: JSONArray()
                val escucha = ArrayList<TareaEscucha>()
                for (i in 0 until tareas.length()) {
                    val t = tareas.getJSONObject(i)
                    val where = "$whereS, tarea ${i + 1}"
                    idNuevo(where, t.optString("id"))
                    val audio = t.optString("audio").ifBlank { throw IllegalArgumentException("$where: falta \"audio\"") }
                    val pregunta = t.optString("pregunta").ifBlank { throw IllegalArgumentException("$where: falta \"pregunta\"") }
                    val opts = opciones(where, t)
                    escucha.add(TareaEscucha(t.getString("id"), t.optString("tipo"), nivel(where, t), audio, pregunta, opts, respuesta(where, t, opts)))
                }
                if (escucha.isEmpty()) throw IllegalArgumentException("$whereS: sin tareas")
                secciones.add(SeccionAptis(id, skill, title, minutos, instruccion, escucha = escucha))
            }
            "writing" -> {
                val tareas = o.optJSONArray("tareas") ?: JSONArray()
                val escritura = ArrayList<TareaEscrita>()
                for (i in 0 until tareas.length()) {
                    val t = tareas.getJSONObject(i)
                    val where = "$whereS, tarea ${i + 1}"
                    idNuevo(where, t.optString("id"))
                    val seg = t.optInt("segundos", 0)
                    if (seg <= 0) throw IllegalArgumentException("$where: falta \"segundos\"")
                    val rub = jsonStrings(t.optJSONArray("rubrica"))
                    if (rub.isEmpty()) throw IllegalArgumentException("$where: falta la rúbrica")
                    escritura.add(
                        TareaEscrita(
                            t.getString("id"), nivel(where, t), t.optString("palabras"), seg,
                            t.optString("prompt_en").ifBlank { throw IllegalArgumentException("$where: falta \"prompt_en\"") },
                            t.optString("prompt_es").ifBlank { throw IllegalArgumentException("$where: falta \"prompt_es\"") },
                            rub
                        )
                    )
                }
                if (escritura.size < 2) throw IllegalArgumentException("$whereS: la estimación por IA necesita al menos 2 tareas")
                secciones.add(SeccionAptis(id, skill, title, minutos, instruccion, escritura = escritura))
            }
            "speaking" -> {
                val tareas = o.optJSONArray("tareas") ?: JSONArray()
                val habla = ArrayList<TareaHablada>()
                for (i in 0 until tareas.length()) {
                    val t = tareas.getJSONObject(i)
                    val where = "$whereS, tarea ${i + 1}"
                    idNuevo(where, t.optString("id"))
                    val hablar = t.optInt("hablar_seg", 0)
                    if (hablar <= 0) throw IllegalArgumentException("$where: falta \"hablar_seg\"")
                    val rub = jsonStrings(t.optJSONArray("rubrica"))
                    if (rub.isEmpty()) throw IllegalArgumentException("$where: falta la rúbrica")
                    habla.add(
                        TareaHablada(
                            t.getString("id"), nivel(where, t), t.optInt("prep_seg", 0).coerceAtLeast(0), hablar,
                            t.optString("prompt_en").ifBlank { throw IllegalArgumentException("$where: falta \"prompt_en\"") },
                            t.optString("prompt_es").ifBlank { throw IllegalArgumentException("$where: falta \"prompt_es\"") },
                            rub
                        )
                    )
                }
                if (habla.size < 2) throw IllegalArgumentException("$whereS: la estimación por IA necesita al menos 2 tareas")
                secciones.add(SeccionAptis(id, skill, title, minutos, instruccion, habla = habla))
            }
            else -> throw IllegalArgumentException("$whereS: sección desconocida \"$id\"")
        }
    }
    if (secciones.isEmpty()) throw IllegalArgumentException("aptis-diagnostico.json: sin secciones")
    return Diagnostico(json.optInt("duracion_min", 30), secciones)
}

private fun jsonStrings(a: JSONArray?): List<String> {
    if (a == null) return emptyList()
    val out = ArrayList<String>(a.length())
    for (i in 0 until a.length()) out.add(a.getString(i))
    return out
}

// ---------------------------------------------------------------------------
// Estimación (bloque "estimacion" del JSON)
// ---------------------------------------------------------------------------

object EstimacionAptis {

    private class Cuenta(val bien: Int, val total: Int)

    private fun cuenta(aciertos: List<Pair<String, Boolean>>, level: String): Cuenta {
        val de = aciertos.filter { it.first == level }
        return Cuenta(de.count { it.second }, de.size)
    }

    /**
     * Regla general (`_regla`): el nivel MÁS ALTO en el que acierta al menos dos
     * tercios de sus ítems; si ninguno, "por debajo de A2". Reading y Listening
     * traen una tarea por nivel, así que es "la tarea más alta que resolvió".
     */
    fun porDosTercios(aciertos: List<Pair<String, Boolean>>): NivelAptis {
        for (n in listOf(NivelAptis.B2, NivelAptis.B1, NivelAptis.A2)) {
            val c = cuenta(aciertos, n.name)
            if (c.total > 0 && c.bien * 3 >= c.total * 2) return n
        }
        return NivelAptis.BAJO_A2
    }

    /**
     * Core (`estimacion.core`): "A2: 1 de 3 o menos" = por debajo de A2 con un
     * tercio o menos de los de A2; "B1: 3 de 4 en A2 y 2 de 4 en B1" = A2 sólido
     * (el archivo trae 3 ítems de A2, no 4: se toma como ≥ 2/3) y la mitad de B1;
     * "B2: 3 de 4 en B1 y 3 de 5 en B2". Escrito en proporciones para que siga
     * valiendo si Cowork cambia el número de ítems.
     */
    fun core(aciertos: List<Pair<String, Boolean>>): NivelAptis {
        val a2 = cuenta(aciertos, "A2")
        val b1 = cuenta(aciertos, "B1")
        val b2 = cuenta(aciertos, "B2")
        if (a2.total == 0 || a2.bien * 3 < a2.total * 2) return NivelAptis.BAJO_A2
        val b1Mitad = b1.total > 0 && b1.bien * 2 >= b1.total
        val b1Solido = b1.total > 0 && b1.bien * 4 >= b1.total * 3
        val b2Mayoria = b2.total > 0 && b2.bien * 5 >= b2.total * 3
        return when {
            b1Solido && b2Mayoria -> NivelAptis.B2
            b1Mitad -> NivelAptis.B1
            else -> NivelAptis.A2
        }
    }

    /**
     * Writing y Speaking (`reglas_ia`): la IA da un nivel por tarea y el estimado
     * es "el más alto alcanzado en DOS tareas": con dos tareas, el menor de los
     * dos; con tres, el del medio. Con menos de dos juicios válidos no hay
     * estimación (null): sin prueba el juicio no vale, y sin dos juicios no hay
     * "alcanzado en dos tareas".
     */
    fun porIa(niveles: List<NivelAptis>): NivelAptis? {
        if (niveles.size < 2) return null
        return niveles.sortedDescending()[1]
    }
}

// ---------------------------------------------------------------------------
// Resultados guardados (filesDir/aptis.json)
// ---------------------------------------------------------------------------

/** Un ítem u opción de Core, Reading o Listening: qué puso y si acertó. */
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
}

data class ResultadoSeccion(
    val id: String,
    val fecha: String,
    val items: List<AciertoItem> = emptyList(),
    val juicios: List<JuicioIa> = emptyList()
) {
    /** El nivel que sale de lo guardado; null si a la IA le faltan juicios válidos. */
    fun nivel(seccion: SeccionAptis): NivelAptis? = when {
        seccion.porIa -> EstimacionAptis.porIa(juicios.mapNotNull { if (it.valido) it.nivel else null })
        seccion.id == "core" -> EstimacionAptis.core(items.map { it.level to it.ok })
        else -> EstimacionAptis.porDosTercios(items.map { it.level to it.ok })
    }

    val pendientesIa: List<JuicioIa> get() = juicios.filter { !it.valido }
}

class Aptis(private val file: File) {

    private val resultados = HashMap<String, ResultadoSeccion>()
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
            val secs = root.optJSONObject("secciones") ?: return
            for (id in secs.keys()) {
                val o = secs.getJSONObject(id)
                val items = ArrayList<AciertoItem>()
                val ia = o.optJSONArray("items") ?: JSONArray()
                for (i in 0 until ia.length()) {
                    val it = ia.getJSONObject(i)
                    items.add(AciertoItem(it.getString("id"), it.optString("level"), it.optString("puesto"), it.optBoolean("ok")))
                }
                val juicios = ArrayList<JuicioIa>()
                val ja = o.optJSONArray("juicios") ?: JSONArray()
                for (i in 0 until ja.length()) {
                    val j = ja.getJSONObject(i)
                    juicios.add(
                        JuicioIa(
                            j.getString("id"), j.optString("level"), j.optString("texto"), j.optInt("segundos"),
                            NivelAptis.de(j.optString("nivel", "")), j.optString("cita"), j.optString("razon"), j.optString("practica"),
                            j.optString("error").ifBlank { null }, j.optInt("duracion")
                        )
                    )
                }
                resultados[id] = ResultadoSeccion(id, o.optString("fecha"), items, juicios)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "No se pudo leer ${file.name}; se empieza de cero", e)
            resultados.clear()
        }
    }

    private fun escribir() {
        val secs = JSONObject()
        for ((id, r) in resultados) {
            val items = JSONArray()
            for (it in r.items) items.put(JSONObject().put("id", it.id).put("level", it.level).put("puesto", it.puesto).put("ok", it.ok))
            val juicios = JSONArray()
            for (j in r.juicios) juicios.put(
                JSONObject().put("id", j.id).put("level", j.level).put("texto", j.texto).put("segundos", j.segundos)
                    .put("nivel", j.nivel?.name ?: "").put("cita", j.cita).put("razon", j.razon).put("practica", j.practica)
                    .put("error", j.error ?: "").put("duracion", j.duracion)
            )
            secs.put(id, JSONObject().put("fecha", r.fecha).put("items", items).put("juicios", juicios))
        }
        try {
            file.parentFile?.mkdirs()
            file.writeText(JSONObject().put("version", 1).put("secciones", secs).toString())
        } catch (e: Throwable) {
            Log.e(TAG, "No se pudo guardar ${file.name}", e)
        }
        tick += 1
    }

    fun resultado(id: String): ResultadoSeccion? { cargar(); return resultados[id] }

    fun guardar(r: ResultadoSeccion) { cargar(); resultados[r.id] = r; escribir() }

    fun borrarTodo() { cargar(); resultados.clear(); if (file.exists()) file.delete(); tick += 1 }

    fun hechas(diag: Diagnostico): Int { cargar(); return diag.secciones.count { resultados[it.id] != null } }

    /** Nivel por destreza; null donde falta hacer la parte o la IA no dio pruebas. */
    fun niveles(diag: Diagnostico): Map<String, NivelAptis?> {
        cargar()
        return diag.secciones.associate { s -> s.id to resultados[s.id]?.nivel(s) }
    }

    fun completo(diag: Diagnostico): Boolean = niveles(diag).values.all { it != null }

    /** Las destrezas más flojas (varias si empatan). Solo tiene sentido con el diagnóstico completo. */
    fun piso(diag: Diagnostico): List<SeccionAptis> {
        val n = niveles(diag)
        val minimo = n.values.filterNotNull().minOrNull() ?: return emptyList()
        return diag.secciones.filter { n[it.id] == minimo }
    }

    companion object {
        private const val TAG = "HabloAptis"
    }
}

// ---------------------------------------------------------------------------
// Frases de las tarjetas (Core, Reading, Listening; las de IA vienen del juicio)
// ---------------------------------------------------------------------------

object TarjetaAptis {

    /** "Acertaste 3 de 3 en A2, 2 de 4 en B1 y 1 de 5 en B2." */
    fun justificacion(items: List<AciertoItem>): String {
        val partes = listOf("A2", "B1", "B2").mapNotNull { l ->
            val de = items.filter { it.level == l }
            if (de.isEmpty()) null else "${de.count { it.ok }} de ${de.size} en $l"
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
            return "La frase que fallaste más abajo (${item.level}): «${item.text.replace("___", item.answer)}»"
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

    /** Sonnet: es el que mejor juzga y son cinco llamadas por diagnóstico (~$0,02 en total). */
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
        sb.append("TAREA de Writing (nivel objetivo ${t.level}): ${t.promptEn}\n")
        sb.append("Se pedían ${t.palabras} palabras en ${t.segundos} segundos. El alumno escribió ${palabras(texto)} palabras")
        if (segundos > 0) sb.append(" en $segundos segundos")
        sb.append(".\n")
        sb.append(rubrica(t.rubrica))
        sb.append(bloqueTexto(texto, citaMala))
        return sb.toString()
    }

    fun promptHablada(t: TareaHablada, transcripcion: String, segundosVoz: Int, citaMala: String? = null, duracion: Int = 0): String {
        val sb = StringBuilder()
        sb.append("TAREA de Speaking (nivel objetivo ${t.level}): ${t.promptEn}\n")
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
        nivel = NivelAptis.BAJO_A2,
        cita = "(no hubo texto)",
        razon = if (j.segundos > 0) "No se entendió nada de lo grabado." else "No escribiste ni dijiste nada en esta tarea.",
        practica = "Responde aunque sea con dos frases: en blanco no se puede estimar nada.",
        error = null
    )

    /**
     * Juzga una tarea. Si la cita no aparece en el texto, se le reclama UNA vez;
     * si vuelve a fallar, el juicio queda sin nivel y con el motivo en [JuicioIa.error].
     */
    fun juzgar(claude: ClaudeLlm, seccion: SeccionAptis, j: JuicioIa, onDone: (JuicioIa) -> Unit) {
        if (j.texto.isBlank()) { onDone(sinTexto(j)); return }
        val escrita = seccion.escritura.firstOrNull { it.id == j.id }
        val hablada = seccion.habla.firstOrNull { it.id == j.id }
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

    /** Juzga en fila las tareas pendientes de una sección, avisando cada una. */
    fun juzgarPendientes(claude: ClaudeLlm, seccion: SeccionAptis, juicios: List<JuicioIa>, onCada: (List<JuicioIa>) -> Unit, onFin: (List<JuicioIa>) -> Unit) {
        val lista = ArrayList(juicios)
        fun siguiente(i: Int) {
            val idx = (i until lista.size).firstOrNull { !lista[it].valido } ?: run { onFin(lista.toList()); return }
            juzgar(claude, seccion, lista[idx]) { nuevo ->
                lista[idx] = nuevo
                onCada(lista.toList())
                siguiente(idx + 1)
            }
        }
        siguiente(0)
    }

    private const val TAG = "HabloAptis"
}
