package com.ferolabs.hablo

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * **Crucigramas** (Cowork, 17-09, `crucigramas.json`): pista en español,
 * respuesta en inglés (la dirección difícil), cada rejilla sale de UN banco de
 * `vocabulario.json` (dentro de un banco no hay casi sinónimos, así que la
 * pista apunta a una sola palabra). Producir la palabra desde una pista es
 * recuperación productiva (d = 1,38) y la rejilla se corrige sola. Sin reloj:
 * el crucigrama es el descanso; la tensión la ponen Aguanta y el contrarreloj.
 *
 * Aquí va el modelo (Kotlin puro, con tests) y lo guardado en
 * `filesDir/crucigramas.json`: las letras puestas, qué palabras necesitaron
 * ayuda y cuáles quedaron terminadas, para poder salir a mitad y volver.
 */
data class PalabraCruci(val numero: Int, val dir: Char, val fila: Int, val col: Int, val en: String, val pista: String) {
    val horizontal: Boolean get() = dir == 'H'
    /** Las casillas de la palabra, en orden. */
    val celdas: List<Celda> = en.indices.map { i -> if (horizontal) Celda(fila, col + i) else Celda(fila + i, col) }
    val flecha: String get() = if (horizontal) "→" else "↓"
}

/** Una casilla: fila y columna desde 0. */
data class Celda(val fila: Int, val col: Int) {
    val clave: String get() = "$fila,$col"
}

data class Crucigrama(
    val id: String,
    val title: String,
    val level: String,
    val banco: String,
    val filas: Int,
    val columnas: Int,
    val palabras: List<PalabraCruci>
) {
    /** Letra correcta de cada casilla blanca (minúscula); las demás casillas son oscuras. */
    val solucion: Map<Celda, Char> = HashMap<Celda, Char>().also { m ->
        for (p in palabras) p.celdas.forEachIndexed { i, c -> m[c] = p.en[i].lowercaseChar() }
    }
    /** Número que va en la esquina de una casilla de inicio. */
    val numeroEn: Map<Celda, Int> = palabras.associate { Celda(it.fila, it.col) to it.numero }

    /** Las palabras (una o dos) que pasan por una casilla. */
    fun palabrasEn(c: Celda): List<PalabraCruci> = palabras.filter { c in it.celdas }

    /**
     * Qué palabra se selecciona al tocar [c] teniendo seleccionada [actual] con
     * el cursor en [cursor]: tocar la casilla del cursor en un cruce cambia de
     * dirección; tocar otra casilla de la misma palabra solo mueve el cursor;
     * en cualquier otra, la horizontal primero. Null si la casilla es oscura.
     */
    fun seleccionAlTocar(c: Celda, actual: PalabraCruci?, cursor: Celda?): PalabraCruci? {
        val lista = palabrasEn(c)
        if (lista.isEmpty()) return null
        if (actual != null && actual in lista) {
            return if (c == cursor && lista.size > 1) lista.first { it != actual } else actual
        }
        return lista.firstOrNull { it.horizontal } ?: lista[0]
    }
}

/** El estado de un crucigrama en curso o terminado. */
data class EstadoCruci(
    val letras: Map<String, Char> = emptyMap(),   // clave de casilla → letra puesta
    val conAyuda: Set<String> = emptySet(),        // palabras (en) que pidieron una letra o la oyeron
    val hecho: Boolean = false,
    val sinAyuda: Int = 0,                         // al terminar: palabras resueltas sin ayuda
    val fecha: String = ""
) {
    fun letra(c: Celda): Char? = letras[c.clave]

    /** ¿Están todas las casillas de la palabra escritas y bien? */
    fun correcta(p: PalabraCruci): Boolean = p.celdas.withIndex().all { (i, c) -> letra(c) == p.en[i].lowercaseChar() }

    /** ¿Están todas escritas (bien o mal)? */
    fun completa(p: PalabraCruci): Boolean = p.celdas.all { letra(it) != null }

    fun resuelto(cruci: Crucigrama): Boolean = cruci.palabras.all { correcta(it) }
}

class Crucigramas(private val file: File) {

    private val estados = LinkedHashMap<String, EstadoCruci>()
    private var cargado = false
    private val dia = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    fun hoy(): String = dia.format(Date())

    @Synchronized
    fun cargar() {
        if (cargado) return
        cargado = true
        if (!file.exists()) return
        try {
            val json = JSONObject(file.readText())
            val c = json.optJSONObject("c") ?: return
            for (id in c.keys()) {
                val o = c.getJSONObject(id)
                val letras = HashMap<String, Char>()
                o.optJSONObject("letras")?.let { l -> for (k in l.keys()) l.optString(k).firstOrNull()?.let { ch -> letras[k] = ch } }
                val ayuda = HashSet<String>()
                o.optJSONArray("ayuda")?.let { a -> for (i in 0 until a.length()) ayuda.add(a.getString(i)) }
                estados[id] = EstadoCruci(letras, ayuda, o.optBoolean("hecho"), o.optInt("sinAyuda"), o.optString("fecha"))
            }
        } catch (e: Throwable) {
            Log.e(TAG, "crucigramas.json ilegible; se ignora", e)
        }
    }

    @Synchronized
    private fun guardar() {
        try {
            val c = JSONObject()
            for ((id, e) in estados) {
                c.put(id, JSONObject()
                    .put("letras", JSONObject().also { l -> e.letras.forEach { (k, v) -> l.put(k, v.toString()) } })
                    .put("ayuda", JSONArray().also { a -> e.conAyuda.forEach { a.put(it) } })
                    .put("hecho", e.hecho).put("sinAyuda", e.sinAyuda).put("fecha", e.fecha))
            }
            file.writeText(JSONObject().put("v", 1).put("c", c).toString())
        } catch (e: Throwable) {
            Log.e(TAG, "no se pudo guardar crucigramas.json", e)
        }
    }

    @Synchronized
    fun estado(id: String): EstadoCruci { cargar(); return estados[id] ?: EstadoCruci() }

    @Synchronized
    fun guardar(id: String, estado: EstadoCruci) { cargar(); estados[id] = estado; guardar() }

    @Synchronized
    fun hechos(): Int { cargar(); return estados.values.count { it.hecho } }

    @Synchronized
    fun reiniciar(id: String) { cargar(); estados.remove(id); guardar() }

    @Synchronized
    fun borrarTodo() { cargar(); estados.clear(); file.delete() }

    companion object {
        private const val TAG = "HabloCruci"
    }
}
