package com.ferolabs.hablo

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * El cuaderno de errores. Guarda lo que Hablo SABE de Fero y nadie más puede
 * saber —qué frases dijo mal, qué sonido falla, qué le corrigió la profesora—
 * y lo exporta como un archivo de texto que él se lleva a su chat de Claude
 * para practicar allí (idea suya, 2026-09-13).
 *
 * Vive en `filesDir/progreso.json` (dentro de la app, no en la tarjeta): es su
 * voz y sus errores, no sale del teléfono salvo cuando él toca "descargar".
 * Topes duros por lista para que el archivo no crezca sin fin ni se vuelva
 * ilegible; cuando se llena, se van los más viejos.
 */
class Progreso(context: Context) {

    private val app = context.applicationContext
    private val file = File(app.filesDir, "progreso.json")
    private val dia = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** Un intento de pronunciación: la frase, el sonido y cómo salió. */
    data class Intento(val fecha: String, val frase: String, val sonido: String, val veredicto: String, val palabras: String)

    /** Un ejercicio fallado en una lección: lo que dijo y lo que era. */
    data class Fallo(val fecha: String, val leccion: String, val tipo: String, val tuya: String, val correcta: String)

    /** Una corrección de la profesora en la conversación (la línea en español). */
    data class Correccion(val fecha: String, val texto: String)

    private var intentos = ArrayList<Intento>()
    private var fallos = ArrayList<Fallo>()
    private var correcciones = ArrayList<Correccion>()
    private var cargado = false

    // ------------------------------------------------------------------ leer

    @Synchronized
    private fun cargar() {
        if (cargado) return
        cargado = true
        if (!file.exists()) return
        try {
            val json = JSONObject(file.readText())
            json.optJSONArray("intentos")?.let { a ->
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    intentos.add(
                        Intento(
                            o.optString("f"), o.optString("frase"), o.optString("sonido"),
                            o.optString("veredicto"), o.optString("palabras")
                        )
                    )
                }
            }
            json.optJSONArray("fallos")?.let { a ->
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    fallos.add(
                        Fallo(
                            o.optString("f"), o.optString("leccion"), o.optString("tipo"),
                            o.optString("tuya"), o.optString("correcta")
                        )
                    )
                }
            }
            json.optJSONArray("correcciones")?.let { a ->
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    correcciones.add(Correccion(o.optString("f"), o.optString("texto")))
                }
            }
        } catch (e: Throwable) {
            // Un archivo roto no puede tumbar la app ni borrar el resto: se empieza de cero.
            Log.e(TAG, "progreso.json ilegible; se ignora", e)
        }
    }

    // --------------------------------------------------------------- escribir

    @Synchronized
    private fun guardar() {
        try {
            val json = JSONObject()
                .put("v", 1)
                .put("actualizado", hoy())
                .put("intentos", JSONArray().also { a ->
                    intentos.forEach {
                        a.put(
                            JSONObject().put("f", it.fecha).put("frase", it.frase)
                                .put("sonido", it.sonido).put("veredicto", it.veredicto)
                                .put("palabras", it.palabras)
                        )
                    }
                })
                .put("fallos", JSONArray().also { a ->
                    fallos.forEach {
                        a.put(
                            JSONObject().put("f", it.fecha).put("leccion", it.leccion)
                                .put("tipo", it.tipo).put("tuya", it.tuya).put("correcta", it.correcta)
                        )
                    }
                })
                .put("correcciones", JSONArray().also { a ->
                    correcciones.forEach { a.put(JSONObject().put("f", it.fecha).put("texto", it.texto)) }
                })
            file.writeText(json.toString())
        } catch (e: Throwable) {
            Log.e(TAG, "no se pudo guardar el progreso", e)
        }
    }

    private fun hoy(): String = dia.format(Date())

    private fun <T> recortar(lista: ArrayList<T>, tope: Int) {
        while (lista.size > tope) lista.removeAt(0)
    }

    @Synchronized
    fun anotarIntento(frase: String, sonido: Sound, report: SoundReport?) {
        cargar()
        val peor = report?.worst ?: return
        val malas = report.items.filter { it.verdict != WordScore.BIEN }
            .map { it.word }.distinct().joinToString(" ")
        intentos.add(Intento(hoy(), frase, sonido.key, peor.name.lowercase(Locale.US), malas))
        recortar(intentos, MAX_INTENTOS)
        guardar()
    }

    @Synchronized
    fun anotarFallo(leccion: String, tipo: String, tuya: String, correcta: String) {
        cargar()
        fallos.add(Fallo(hoy(), leccion, tipo, tuya.take(120), correcta.take(120)))
        recortar(fallos, MAX_FALLOS)
        guardar()
    }

    @Synchronized
    fun anotarCorreccion(texto: String) {
        cargar()
        val limpio = texto.trim().take(300)
        if (limpio.isBlank()) return
        // Si la profesora repite la misma corrección, se cuenta una vez (la última).
        correcciones.removeAll { it.texto == limpio }
        correcciones.add(Correccion(hoy(), limpio))
        recortar(correcciones, MAX_CORRECCIONES)
        guardar()
    }

    @Synchronized
    fun borrarTodo() {
        intentos.clear(); fallos.clear(); correcciones.clear()
        cargado = true
        file.delete()
    }

    @Synchronized
    fun hayAlgo(): Boolean {
        cargar()
        return intentos.isNotEmpty() || fallos.isNotEmpty() || correcciones.isNotEmpty()
    }

    // ---------------------------------------------------------------- informe

    /**
     * Arma el informe en Markdown. Está escrito para que lo lea una IA y para
     * que lo entienda Fero: primero lo medido, después lo que hay que
     * practicar, y al final un bloque listo para pegar en su proyecto de Claude.
     * Todo se calcula en el teléfono: no cuesta ni un centavo ni necesita red.
     */
    @Synchronized
    fun informe(store: Store, teacher: Teacher): String {
        cargar()
        val b = StringBuilder()
        val fecha = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es", "CO")).format(Date())
        val desde = intentos.firstOrNull()?.fecha ?: fallos.firstOrNull()?.fecha ?: hoy()

        b.appendLine("# Mi práctica de inglés — informe de Hablo")
        b.appendLine()
        b.appendLine("- Generado el $fecha, con lo registrado desde el $desde.")
        b.appendLine("- Mi lengua materna es español (Colombia). Nivel del contenido que estoy haciendo: A1.")
        b.appendLine("- Profesora en la app: ${teacher.name} (${teacher.accent.label}).")
        b.appendLine("- Progreso: ${store.xp} puntos, racha de ${store.streak} días.")
        b.appendLine()

        // --- 1. Sonidos -------------------------------------------------------
        b.appendLine("## 1. Mis sonidos (medido por la app, no por mí)")
        b.appendLine()
        val conDatos = Sound.entries.filter { it != Sound.GENERAL && store.soundStats(it).tries > 0 }
        if (conDatos.isEmpty()) {
            b.appendLine("Todavía no he practicado pronunciación suficiente para tener datos.")
        } else {
            b.appendLine("| Sonido | Intentos | Mal | Dudoso | Bien |")
            b.appendLine("|---|---:|---:|---:|---:|")
            for (s in conDatos.sortedByDescending {
                val st = store.soundStats(it)
                (st.mal * 2.0 + st.dudoso) / st.tries
            }) {
                val st = store.soundStats(s)
                b.appendLine("| ${s.labelEs} (`${s.key}`) | ${st.tries} | ${st.mal} | ${st.dudoso} | ${st.ok} |")
            }
            b.appendLine()
            b.appendLine("Nota honesta: la app solo sabe juzgar algunos sonidos. Hoy evalúa `sh` y `h` " +
                "con fiabilidad alta y avisa en amarillo de `-ed` y `e+s`; `th`, `v`, la `r/l` y las " +
                "consonantes finales todavía no las puede calificar, así que su ausencia aquí no " +
                "significa que las diga bien.")
        }
        b.appendLine()

        // --- 2. Frases que me cuestan ----------------------------------------
        val malas = intentos.filter { it.veredicto != "bien" }
        b.appendLine("## 2. Frases que dije mal al practicar en voz alta")
        b.appendLine()
        if (malas.isEmpty()) {
            b.appendLine("Ninguna marcada en este periodo.")
        } else {
            val porFrase = malas.groupBy { it.frase }
                .toList().sortedByDescending { it.second.size }.take(20)
            b.appendLine("| Frase | Veces | Sonido | Palabras marcadas |")
            b.appendLine("|---|---:|---|---|")
            for ((frase, lista) in porFrase) {
                val palabras = lista.flatMap { it.palabras.split(" ") }.filter { it.isNotBlank() }
                    .distinct().joinToString(", ")
                b.appendLine("| $frase | ${lista.size} | ${lista.first().sonido} | ${palabras.ifBlank { "—" }} |")
            }
        }
        b.appendLine()

        // --- 3. Errores de lección -------------------------------------------
        b.appendLine("## 3. Errores en los ejercicios escritos")
        b.appendLine()
        if (fallos.isEmpty()) {
            b.appendLine("Ninguno registrado en este periodo.")
        } else {
            b.appendLine("| Yo puse | Era | Ejercicio |")
            b.appendLine("|---|---|---|")
            for (f in fallos.takeLast(25).reversed()) {
                b.appendLine("| ${f.tuya.ifBlank { "(nada)" }} | ${f.correcta} | ${f.tipo} |")
            }
        }
        b.appendLine()

        // --- 4. Correcciones de la profesora ----------------------------------
        b.appendLine("## 4. Lo que la profesora me corrigió al conversar")
        b.appendLine()
        if (correcciones.isEmpty()) {
            b.appendLine("Todavía no hay correcciones registradas de la conversación.")
        } else {
            for (c in correcciones.takeLast(25).reversed()) b.appendLine("- ${c.texto}")
        }
        b.appendLine()

        // --- 5. Qué practicar -------------------------------------------------
        b.appendLine("## 5. Qué me conviene practicar ahora")
        b.appendLine()
        for (linea in sugerencias(store)) b.appendLine("- $linea")
        b.appendLine()

        // --- 6. Para pegar en el chat -----------------------------------------
        b.appendLine("---")
        b.appendLine()
        b.appendLine("## Instrucciones para mi tutor de IA")
        b.appendLine()
        b.appendLine("Eres mi profesor particular de inglés. Yo hablo español (Colombia) y estoy en " +
            "nivel A1-A2. Este archivo trae mis errores REALES, medidos por mi app de práctica.")
        b.appendLine()
        b.appendLine("Cuando practiquemos:")
        b.appendLine()
        b.appendLine("1. Empieza por lo de la sección 5 y por los errores que más se repiten, no por temas sueltos.")
        b.appendLine("2. Hazme producir frases mías, no me des solo explicaciones ni listas para leer.")
        b.appendLine("3. Corrígeme siempre en dos pasos: primero dime la frase correcta completa en inglés, " +
            "y después explícame en español, en una sola frase, por qué estaba mal.")
        b.appendLine("4. Una corrección por turno, la más importante; si lo que dije está bien, sigue la conversación.")
        b.appendLine("5. Vuelve a sacar más adelante los errores que ya corregimos, para ver si se me quedaron.")
        b.appendLine("6. No me corrijas palabras raras que parezcan mal transcritas: pregúntame qué quise decir.")
        b.appendLine()
        b.appendLine("Empieza preguntándome qué quiero practicar hoy y proponme tú dos opciones sacadas de este informe.")
        return b.toString()
    }

    /** Sugerencias calculadas con reglas sobre sus propios datos. Sin IA y sin internet. */
    private fun sugerencias(store: Store): List<String> {
        val out = ArrayList<String>()

        val peor = Sound.entries.filter { it != Sound.GENERAL && store.soundStats(it).tries >= 5 }
            .maxByOrNull {
                val st = store.soundStats(it)
                (st.mal * 2.0 + st.dudoso) / st.tries
            }
        if (peor != null) {
            val st = store.soundStats(peor)
            val pct = ((st.mal + st.dudoso) * 100) / st.tries
            out.add("**${peor.labelEs}** es mi sonido más flojo: falló o quedó dudoso en el $pct % de ${st.tries} intentos. Pídeme palabras con ese sonido y hazme repetirlas en frases, no sueltas.")
        }

        val repetidas = correcciones.groupBy { claveError(it.texto) }
            .filter { it.key.isNotBlank() }
            .toList().sortedByDescending { it.second.size }
            .firstOrNull { it.second.size >= 2 }
        if (repetidas != null) {
            out.add("Este error me lo han corregido ${repetidas.second.size} veces: \"${repetidas.second.last().texto}\". Hazme usarlo bien tres veces seguidas en frases distintas.")
        }

        val frasesDuras = intentos.filter { it.veredicto == "mal" }.groupBy { it.frase }
            .filter { it.value.size >= 2 }.keys.take(3)
        if (frasesDuras.isNotEmpty()) {
            out.add("Frases que ya fallé dos veces o más y quiero dejar limpias: ${frasesDuras.joinToString(" · ") { "\"$it\"" }}.")
        }

        val flojas = Course.allLessons().filter { store.bestScore(it.id) in 1..79 }
        if (flojas.isNotEmpty()) {
            out.add("Lecciones que aprobé raspando (menos de 80): ${flojas.joinToString(", ") { it.title }}. Pregúntame lo de esas lecciones en frases nuevas.")
        }

        if (!store.studiedToday()) {
            out.add("Hoy todavía no he practicado en la app; si el día se me va, al menos hazme hablar diez minutos aquí.")
        }
        if (out.isEmpty()) {
            out.add("Todavía hay pocos datos: hazme una conversación corta de nivel A1 y anota tú lo que falle.")
        }
        return out
    }

    /** Clave tosca para agrupar correcciones parecidas (las primeras palabras en español). */
    private fun claveError(texto: String): String =
        texto.lowercase(Locale("es")).filter { it.isLetter() || it == ' ' }
            .split(" ").filter { it.length > 3 }.take(4).joinToString(" ")

    // --------------------------------------------------------------- descarga

    /**
     * Guarda el informe en la carpeta **Descargas** del teléfono, donde Fero lo
     * puede ver con cualquier gestor de archivos y subirlo a su chat. Devuelve
     * el uri (para compartir) o null si no se pudo.
     */
    fun guardarEnDescargas(texto: String): Uri? {
        val nombre = "hablo-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date()) + ".md"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val valores = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, nombre)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/markdown")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)
                    ?: return null
                app.contentResolver.openOutputStream(uri)?.use { it.write(texto.toByteArray()) }
                uri
            } else {
                // Android 8-9: sin MediaStore de Descargas; queda en la carpeta de la app.
                val destino = File(app.getExternalFilesDir("informes"), nombre)
                destino.parentFile?.mkdirs()
                destino.writeText(texto)
                Uri.fromFile(destino)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "no se pudo guardar el informe", e)
            null
        }
    }

    /** Abre el menú de compartir de Android con el informe ya guardado. */
    fun compartir(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Mi informe de Hablo")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Enviar el informe a…").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    companion object {
        private const val TAG = "HabloProgreso"
        private const val MAX_INTENTOS = 300
        private const val MAX_FALLOS = 200
        private const val MAX_CORRECCIONES = 100
    }
}
