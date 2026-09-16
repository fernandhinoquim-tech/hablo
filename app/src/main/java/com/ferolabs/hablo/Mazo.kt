package com.ferolabs.hablo

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * El mazo de repaso (etapa 3, 2026-09-16): lo que convierte 565 ejercicios en
 * práctica que no se acaba. Cada frase que salió en una lección terminada
 * entra como ítem y vuelve según **Leitner 1/3/7/16/35 días**: acierta y sube
 * de caja (más días hasta la próxima), falla y baja a la caja 0 (mañana).
 *
 * Y con la **escalera de dificultad** del mismo ítem: cada vez que vuelve se
 * pregunta un escalón más arriba —elegir → armar → escribir → oír y escribir →
 * decir—, porque un ítem NO está aprendido hasta que se produjo, no solo se
 * reconoció (KATE 30: producir rinde d = 1,38 sobre elegir en pruebas de
 * producción). Un fallo baja un escalón.
 *
 * Aquí también viven las marcas del contrarreloj (el reloj es velocímetro,
 * nunca juez: guarda la curva), la marca de "Aguanta" y qué errores del
 * cuaderno ya corrigió el propio alumno. Todo en `filesDir/mazo.json`.
 * Kotlin puro sobre un [File]: se prueba en el PC.
 */
class Mazo(private val file: File) {

    /** Un ítem del mazo: la frase, en qué caja va, en qué escalón se pregunta y cuándo toca. */
    data class Item(
        val id: String,          // id del ejercicio de origen (clave del mazo)
        val en: String,
        val es: String,
        val caja: Int,           // 0..4 → Leitner INTERVALOS
        val escalon: Int,        // 0..4 → ESCALERA
        val proximo: String,     // yyyy-MM-dd
        val vistas: Int,
        val aciertos: Int,
        val creado: String
    ) {
        /** Aprendido = llegó arriba de la escalera (lo produjo, no solo lo reconoció) y a la última caja. */
        val aprendido: Boolean get() = escalon >= ESCALERA.size - 1 && caja >= INTERVALOS.size - 1
    }

    /** Un tiempo del contrarreloj: fecha, segundos y cuántas parejas. */
    data class Marca(val fecha: String, val segundos: Int, val parejas: Int) {
        val porPareja: Double get() = if (parejas == 0) 0.0 else segundos.toDouble() / parejas
    }

    private val items = LinkedHashMap<String, Item>()
    /** Errores del cuaderno que el alumno ya corrigió él mismo: clave → veces bien. */
    private val corregidos = LinkedHashMap<String, Int>()
    private val marcas = LinkedHashMap<String, ArrayList<Marca>>()
    private var aguantaMarca = 0
    private var cargado = false

    private val dia = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    fun hoy(): String = dia.format(Date())

    // ------------------------------------------------------------------ disco

    @Synchronized
    fun cargar() {
        if (cargado) return
        cargado = true
        if (!file.exists()) return
        try {
            val json = JSONObject(file.readText())
            json.optJSONArray("items")?.let { a ->
                for (i in 0 until a.length()) {
                    val o = a.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    if (id.isBlank()) continue
                    items[id] = Item(
                        id, o.optString("en"), o.optString("es"),
                        o.optInt("caja", 0).coerceIn(0, INTERVALOS.size - 1),
                        o.optInt("escalon", 0).coerceIn(0, ESCALERA.size - 1),
                        o.optString("proximo", hoy()), o.optInt("vistas", 0), o.optInt("aciertos", 0),
                        o.optString("creado", hoy())
                    )
                }
            }
            json.optJSONObject("corregidos")?.let { c -> for (k in c.keys()) corregidos[k] = c.optInt(k, 0) }
            json.optJSONObject("marcas")?.let { m ->
                for (banco in m.keys()) {
                    val a = m.optJSONArray(banco) ?: continue
                    val lista = ArrayList<Marca>()
                    for (i in 0 until a.length()) {
                        val o = a.optJSONObject(i) ?: continue
                        lista.add(Marca(o.optString("f"), o.optInt("seg"), o.optInt("n")))
                    }
                    marcas[banco] = lista
                }
            }
            aguantaMarca = json.optInt("aguanta", 0)
        } catch (e: Throwable) {
            Log.e(TAG, "mazo.json ilegible; se empieza de cero", e)
        }
    }

    @Synchronized
    private fun guardar() {
        try {
            val json = JSONObject()
                .put("v", 1)
                .put("actualizado", hoy())
                .put("items", JSONArray().also { a ->
                    items.values.forEach {
                        a.put(
                            JSONObject().put("id", it.id).put("en", it.en).put("es", it.es)
                                .put("caja", it.caja).put("escalon", it.escalon).put("proximo", it.proximo)
                                .put("vistas", it.vistas).put("aciertos", it.aciertos).put("creado", it.creado)
                        )
                    }
                })
                .put("corregidos", JSONObject().also { c -> corregidos.forEach { (k, v) -> c.put(k, v) } })
                .put("marcas", JSONObject().also { m ->
                    marcas.forEach { (banco, lista) ->
                        m.put(banco, JSONArray().also { a ->
                            lista.forEach { a.put(JSONObject().put("f", it.fecha).put("seg", it.segundos).put("n", it.parejas)) }
                        })
                    }
                })
                .put("aguanta", aguantaMarca)
            file.parentFile?.mkdirs()
            file.writeText(json.toString())
        } catch (e: Throwable) {
            Log.e(TAG, "no se pudo guardar el mazo", e)
        }
    }

    // ------------------------------------------------------------------ ítems

    /**
     * Mete en el mazo las frases de una lección terminada (las que traen inglés
     * y español). Lo que ya estaba no se toca. Entran en la caja 0, escalón 0,
     * para mañana. Devuelve cuántas entraron.
     */
    @Synchronized
    fun alimentar(lesson: Lesson, hoy: String = hoy()): Int {
        cargar()
        var nuevos = 0
        for (ex in lesson.exercises) {
            val (en, es) = parDe(ex) ?: continue
            if (items.containsKey(ex.id)) continue
            if (items.values.any { it.en.equals(en, ignoreCase = true) }) continue   // la misma frase con otro id
            items[ex.id] = Item(ex.id, en, es, 0, 0, sumarDias(hoy, INTERVALOS[0]), 0, 0, hoy)
            nuevos++
        }
        if (nuevos > 0) guardar()
        return nuevos
    }

    /** Lo que toca hoy (o antes): primero lo más atrasado, luego las cajas bajas. Como mucho [max]. */
    @Synchronized
    fun pendientes(hoy: String = hoy(), max: Int = MAX_SESION): List<Item> {
        cargar()
        return items.values.filter { it.proximo <= hoy }
            .sortedWith(compareBy({ it.proximo }, { it.caja }, { it.creado }))
            .take(max)
    }

    @Synchronized
    fun cuantosPendientes(hoy: String = hoy()): Int {
        cargar()
        return items.values.count { it.proximo <= hoy }
    }

    @Synchronized
    fun total(): Int { cargar(); return items.size }

    @Synchronized
    fun aprendidos(): Int { cargar(); return items.values.count { it.aprendido } }

    @Synchronized
    fun item(id: String): Item? { cargar(); return items[id] }

    @Synchronized
    fun todos(): List<Item> { cargar(); return items.values.toList() }

    /**
     * Resultado de un repaso. Acierta: sube de caja (Leitner) y de escalón;
     * falla: caja 0 (vuelve mañana) y un escalón abajo. Solo cuenta el primer
     * intento de la sesión: repetirlo hasta acertar no infla el mazo.
     */
    @Synchronized
    fun registrar(id: String, acierto: Boolean, hoy: String = hoy()) {
        cargar()
        val it = items[id] ?: return
        val caja = if (acierto) minOf(it.caja + 1, INTERVALOS.size - 1) else 0
        val escalon = if (acierto) minOf(it.escalon + 1, ESCALERA.size - 1) else maxOf(it.escalon - 1, 0)
        items[id] = it.copy(
            caja = caja, escalon = escalon,
            proximo = sumarDias(hoy, INTERVALOS[caja]),
            vistas = it.vistas + 1, aciertos = it.aciertos + if (acierto) 1 else 0
        )
        guardar()
    }

    // -------------------------------------------------- corrige tu propio error

    /** Clave de un fallo del cuaderno: ejercicio + fecha + lo que puso. */
    fun claveFallo(ejercicio: String, fecha: String, tuya: String) = "$ejercicio|$fecha|${tuya.trim().lowercase(Locale.US)}"

    /** ¿Ese fallo ya lo corrigió él mismo las veces necesarias? */
    @Synchronized
    fun corregido(clave: String): Boolean { cargar(); return (corregidos[clave] ?: 0) >= VECES_PARA_RETIRAR }

    @Synchronized
    fun registrarCorreccion(clave: String, acierto: Boolean) {
        cargar()
        corregidos[clave] = if (acierto) (corregidos[clave] ?: 0) + 1 else 0
        guardar()
    }

    // ---------------------------------------------------------- contrarreloj

    /** Guarda un tiempo del contrarreloj y devuelve la mejor marca (segundos por pareja) de ese banco. */
    @Synchronized
    fun registrarMarca(banco: String, segundos: Int, parejas: Int): Marca {
        cargar()
        val lista = marcas.getOrPut(banco) { ArrayList() }
        lista.add(Marca(hoy(), segundos, parejas))
        while (lista.size > MAX_MARCAS) lista.removeAt(0)
        guardar()
        return lista.minByOrNull { it.porPareja }!!
    }

    @Synchronized
    fun marcas(banco: String): List<Marca> { cargar(); return marcas[banco]?.toList() ?: emptyList() }

    // ---------------------------------------------------------------- aguanta

    @Synchronized
    fun marcaAguanta(): Int { cargar(); return aguantaMarca }

    /** Devuelve true si fue récord. */
    @Synchronized
    fun registrarAguanta(llegaste: Int): Boolean {
        cargar()
        val record = llegaste > aguantaMarca
        if (record) { aguantaMarca = llegaste; guardar() }
        return record
    }

    @Synchronized
    fun borrarTodo() {
        items.clear(); corregidos.clear(); marcas.clear(); aguantaMarca = 0
        cargado = true
        file.delete()
    }

    companion object {
        private const val TAG = "HabloMazo"
        /** Leitner: días hasta la próxima vez, por caja. */
        val INTERVALOS = intArrayOf(1, 3, 7, 16, 35)
        /** La escalera de dificultad del mismo ítem, por escalón. */
        val ESCALERA = listOf("elegir", "armar", "escribir", "oír y escribir", "decir")
        const val MAX_SESION = 20
        const val MAX_MARCAS = 30
        /** Un error del cuaderno se retira cuando él lo corrige bien dos veces. */
        const val VECES_PARA_RETIRAR = 2

        /** Inglés y español de un ejercicio, si los trae. */
        fun parDe(ex: Exercise): Pair<String, String>? = when (ex) {
            is Exercise.TranslateChoose -> ex.answer to ex.es
            is Exercise.WriteIt -> ex.answer to ex.es
            is Exercise.BuildSentence -> ex.answer to ex.es
            is Exercise.Cloze -> ex.full to ex.es
            is Exercise.TypeWhatYouHear -> ex.audio to ex.meaningEs
            else -> null
        }?.takeIf { it.first.isNotBlank() && it.second.isNotBlank() }

        fun sumarDias(fecha: String, dias: Int): String {
            val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val cal = Calendar.getInstance()
            cal.time = f.parse(fecha) ?: Date()
            cal.add(Calendar.DAY_OF_YEAR, dias)
            return f.format(cal.time)
        }
    }
}
