package com.ferolabs.hablo

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * La ficha de memoria de la charla libre ("Hablar de todo"): lo que la
 * profesora recuerda de Fero entre una charla y la siguiente. Vive en
 * `files/memoria/perfil.json` y solo la usa la charla libre; los escenarios de
 * práctica arrancan limpios (decisión de Fero, 2026-09-13).
 *
 * Topes duros para que el prompt no crezca sin fin: [MAX_DATOS] datos del
 * alumno, [MAX_ERRORES] errores con contador, [MAX_PALABRAS_RESUMEN] palabras
 * de resumen y [MAX_TOKENS] tokens en total en el bloque que va al prompt.
 * Los errores se anotan gratis desde la línea CORRECCIÓN: de cada turno; los
 * datos y el resumen los saca UNA llamada a Haiku al cerrar la charla. Si esa
 * respuesta no parsea, se conserva la ficha vieja.
 *
 * Kotlin puro sobre un [File]: se prueba en el PC sin Android.
 */
class Memoria(private val file: File) {

    data class Dato(val k: String, val v: String)
    data class Error(val texto: String, val veces: Int, val ultima: String)

    var datos: List<Dato> = emptyList()
        private set
    var errores: List<Error> = emptyList()
        private set
    var resumen: String = ""
        private set
    var actualizado: String = ""
        private set
    private var cargado = false

    private val dia = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun hoy(): String = dia.format(Date())

    @Synchronized
    fun cargar() {
        if (cargado) return
        cargado = true
        if (!file.exists()) return
        try {
            aplicar(JSONObject(file.readText()), reemplazarErrores = true)
        } catch (e: Throwable) {
            Log.e(TAG, "perfil.json ilegible; se empieza de cero", e)
        }
    }

    /** Vuelca [json] sobre la ficha. Con [reemplazarErrores] false los errores no se tocan (respuesta de la IA). */
    private fun aplicar(json: JSONObject, reemplazarErrores: Boolean) {
        val nuevosDatos = ArrayList<Dato>()
        json.optJSONArray("datos")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val k = o.optString("k").trim().take(40)
                val v = o.optString("v").trim().take(80)
                if (k.isNotBlank() && v.isNotBlank() && nuevosDatos.none { it.k.equals(k, ignoreCase = true) }) {
                    nuevosDatos.add(Dato(k, v))
                }
            }
        }
        datos = nuevosDatos.take(MAX_DATOS)
        if (reemplazarErrores) {
            val nuevos = ArrayList<Error>()
            json.optJSONArray("errores")?.let { a ->
                for (i in 0 until a.length()) {
                    val o = a.optJSONObject(i) ?: continue
                    val t = o.optString("texto").trim()
                    if (t.isNotBlank()) nuevos.add(Error(t.take(300), o.optInt("veces", 1).coerceAtLeast(1), o.optString("ultima")))
                }
            }
            errores = nuevos.takeLast(MAX_ERRORES)
        }
        resumen = recortarPalabras(json.optString("resumen").trim(), MAX_PALABRAS_RESUMEN)
        actualizado = json.optString("actualizado")
    }

    @Synchronized
    fun guardar() {
        try {
            file.parentFile?.mkdirs()
            file.writeText(toJson().toString())
        } catch (e: Throwable) {
            Log.e(TAG, "no se pudo guardar la ficha", e)
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("v", 1)
        .put("actualizado", actualizado)
        .put("datos", JSONArray().also { a -> datos.forEach { a.put(JSONObject().put("k", it.k).put("v", it.v)) } })
        .put("errores", JSONArray().also { a ->
            errores.forEach { a.put(JSONObject().put("texto", it.texto).put("veces", it.veces).put("ultima", it.ultima)) }
        })
        .put("resumen", resumen)

    /**
     * Un error nuevo, sacado de la línea CORRECCIÓN: de la profesora. Si ya
     * estaba (misma clave tosca), suma una vez; si no cabe, sale el más viejo.
     * No cuesta nada: no hay llamada a la IA.
     */
    @Synchronized
    fun anotarError(correccion: String) {
        cargar()
        val limpio = correccion.trim().take(300)
        if (limpio.isBlank()) return
        val clave = claveError(limpio)
        val lista = ArrayList(errores)
        val i = lista.indexOfFirst { claveError(it.texto) == clave }
        if (i >= 0) {
            lista[i] = Error(limpio, lista[i].veces + 1, hoy())
        } else {
            lista.add(Error(limpio, 1, hoy()))
        }
        while (lista.size > MAX_ERRORES) lista.removeAt(0)
        errores = lista
        actualizado = hoy()
        guardar()
    }

    /**
     * Aplica lo que devolvió la IA al cerrar: `{"datos":[{"k","v"}], "resumen":"…"}`.
     * Acepta texto con basura alrededor (busca el primer `{` y el último `}`).
     * Devuelve false —y no toca nada— si no parsea o no trae ninguno de los dos.
     */
    @Synchronized
    fun aplicarRespuesta(texto: String): Boolean {
        cargar()
        val a = texto.indexOf('{')
        val b = texto.lastIndexOf('}')
        if (a < 0 || b <= a) return false
        val json = try { JSONObject(texto.substring(a, b + 1)) } catch (e: Throwable) { return false }
        if (!json.has("datos") && !json.has("resumen")) return false
        val datosAntes = datos
        val resumenAntes = resumen
        aplicar(json, reemplazarErrores = false)
        // Un JSON válido pero vacío no borra lo que había.
        if (datos.isEmpty()) datos = datosAntes
        if (resumen.isBlank()) resumen = resumenAntes
        actualizado = hoy()
        guardar()
        return true
    }

    /**
     * Lo que va al prompt de sistema, en inglés y recortado a [MAX_TOKENS]
     * (≈ 4 letras por token). Si sobra, se sueltan primero los errores más
     * viejos y luego el resumen: los datos del alumno son lo más barato y lo
     * que más se nota si falta ("¿cómo me llamo?").
     */
    @Synchronized
    fun bloquePrompt(): String {
        cargar()
        var err = errores.sortedByDescending { it.veces }
        var res = resumen
        while (true) {
            val texto = armar(datos, err, res)
            if (texto.length / 4 <= MAX_TOKENS || (err.isEmpty() && res.isEmpty())) return texto
            if (err.isNotEmpty()) err = err.dropLast(1) else res = ""
        }
    }

    private fun armar(datos: List<Dato>, err: List<Error>, res: String): String {
        if (datos.isEmpty() && err.isEmpty() && res.isBlank()) return ""
        val b = StringBuilder()
        b.appendLine("WHAT YOU REMEMBER ABOUT THE STUDENT (from earlier chats; use it naturally, never recite it):")
        if (datos.isNotEmpty()) b.appendLine("- Facts: " + datos.joinToString("; ") { "${it.k}: ${it.v}" } + ".")
        if (err.isNotEmpty()) {
            b.appendLine("- Mistakes they have made before (watch for them, correct them again if they come back): " +
                err.joinToString(" | ") { "\"${it.texto}\" (${it.veces}x)" })
        }
        if (res.isNotBlank()) b.appendLine("- Last time you talked about: $res")
        return b.toString().trimEnd()
    }

    /** Olvidar todo: borra el archivo y la ficha en memoria. */
    @Synchronized
    fun borrar() {
        datos = emptyList(); errores = emptyList(); resumen = ""; actualizado = ""
        cargado = true
        file.delete()
    }

    /** ¿Hay algo que recordar? Para la apertura de la charla. */
    fun hayAlgo(): Boolean {
        cargar()
        return datos.isNotEmpty() || resumen.isNotBlank()
    }

    /** El nombre del alumno, si la ficha lo tiene. */
    fun nombre(): String? {
        cargar()
        return datos.firstOrNull { it.k.lowercase(Locale.US).let { k -> k == "name" || k == "nombre" } }?.v
    }

    companion object {
        private const val TAG = "HabloMemoria"
        const val MAX_DATOS = 12
        const val MAX_ERRORES = 15
        const val MAX_PALABRAS_RESUMEN = 60
        const val MAX_TOKENS = 400

        fun recortarPalabras(texto: String, max: Int): String {
            val palabras = texto.split(Regex("\\s+")).filter { it.isNotBlank() }
            return if (palabras.size <= max) palabras.joinToString(" ") else palabras.take(max).joinToString(" ") + "…"
        }

        /** Clave tosca para agrupar correcciones parecidas: la frase corregida si viene entre comillas, si no las primeras palabras. */
        fun claveError(texto: String): String {
            val citas = Regex("[\"“«]([^\"”»]{3,80})[\"”»]").findAll(texto).map { it.groupValues[1] }.toList()
            val base = citas.firstOrNull() ?: texto
            return base.lowercase(Locale("es")).filter { it.isLetterOrDigit() || it == ' ' || it == '\'' }
                .split(" ").filter { it.isNotBlank() }.take(6).joinToString(" ")
        }
    }
}
