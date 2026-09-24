package com.ferolabs.hablo

import android.content.ContentUris
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
 * El respaldo del progreso (Fase 6, 2026-09-24). Todo lo que Hablo sabe de
 * Fero vive dentro de la app y se pierde al desinstalarla o al cambiar de
 * celular: puntajes y racha (preferencias), el cuaderno, el mazo de repaso,
 * el Modo Aptis, los crucigramas y la memoria de la charla libre.
 *
 * Un respaldo es UN archivo JSON en **Descargas › Hablo**, que Android no
 * borra al desinstalar la app ("Even after your app is uninstalled, these
 * files remain on the user's device", developer.android.com, Access media
 * files from shared storage). Sale solo en tres momentos:
 * - al salir de la app, una copia por día (`hablo-auto-<día>.json`, quedan 7);
 * - antes de "Borrar todo" y antes de recuperar otro respaldo, para poder
 *   deshacerlo (`hablo-antes-de-…`, quedan 5);
 * - cuando Fero toca "Guardar una copia y enviarla" (se guarda y se abre el
 *   menú de compartir de Android: Drive, WhatsApp… lo elige él).
 *
 * Nunca entran las grabaciones (su voz), los modelos ni las claves
 * (los `.key` de `files/modelos`, regla dura 1). La app no abre ninguna conexión.
 *
 * Tras reinstalar, Android deja de considerar esos archivos "de la app" y no
 * los lista sin permisos (hay que usar el selector de archivos del sistema);
 * por eso "Recuperar" tiene las dos vías.
 */
class Respaldo(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(Store.PREFS, Context.MODE_PRIVATE)

    /** Una copia encontrada en Descargas › Hablo. */
    data class Copia(val uri: Uri, val nombre: String, val modificado: Long)

    /** Los archivos de progreso, por el nombre con que van dentro del respaldo. */
    private fun archivos(): Map<String, File?> = linkedMapOf(
        "progreso.json" to File(app.filesDir, "progreso.json"),
        "mazo.json" to File(app.filesDir, "mazo.json"),
        "aptis.json" to File(app.filesDir, "aptis.json"),
        "crucigramas.json" to File(app.filesDir, "crucigramas.json"),
        "memoria/perfil.json" to app.getExternalFilesDir("memoria")?.let { File(it, "perfil.json") }
    )

    private val version: String by lazy {
        try { app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "?" } catch (e: Throwable) { "?" }
    }

    // ------------------------------------------------------------------ armar

    /** El estado de ahora, listo para escribir. */
    fun armar(): JSONObject {
        val contenidos = LinkedHashMap<String, String?>()
        for ((nombre, f) in archivos()) contenidos[nombre] = f?.takeIf { it.exists() }?.let { leerEstable(it) }
        return RespaldoDatos.armar(
            prefs.all, contenidos, version,
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        )
    }

    /**
     * Lee un archivo que la app podría estar escribiendo en ese instante (el
     * respaldo automático corre en otro hilo): si no parsea, reintenta un poco.
     * Si sigue roto se guarda tal cual; la app ya sabe ignorar un archivo ilegible.
     */
    private fun leerEstable(f: File): String? {
        var texto: String? = null
        repeat(3) { intento ->
            texto = try { f.readText() } catch (e: Throwable) { null }
            if (texto != null && try { JSONObject(texto!!); true } catch (e: Throwable) { false }) return texto
            if (intento < 2) Thread.sleep(150)
        }
        return texto
    }

    // -------------------------------------------------------------- guardar

    /** Copia del día, al salir de la app. Como mucho una vez cada diez minutos. */
    fun automatico() {
        try {
            val ahora = System.currentTimeMillis()
            if (ahora - prefs.getLong(K_AUTO, 0L) < 10 * 60_000L) return
            val json = armar()
            if (!RespaldoDatos.hayAlgo(json)) return
            val dia = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            guardar("$PREFIJO_AUTO$dia.json", json.toString(), sobrescribir = true) ?: return
            prefs.edit().putLong(K_AUTO, ahora).apply()
            podar(PREFIJO_AUTO, 7)
        } catch (e: Throwable) {
            Log.e(TAG, "respaldo automático falló", e)
        }
    }

    /**
     * Copia de seguridad antes de algo que no se deshace. Devuelve false solo si
     * había algo que guardar y no se pudo: en ese caso no se debe seguir.
     */
    fun guardarSeguridad(motivo: String): Boolean {
        val json = armar()
        if (!RespaldoDatos.hayAlgo(json)) return true
        val sello = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
        val ok = guardar("${PREFIJO_ANTES}$motivo-$sello.json", json.toString(), sobrescribir = false) != null
        if (ok) podar(PREFIJO_ANTES, 5)
        return ok
    }

    /** La copia que Fero pide a mano (para enviarla fuera del teléfono). */
    fun guardarManual(): Uri? {
        val sello = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
        return guardar("hablo-respaldo-$sello.json", armar().toString(), sobrescribir = false)
    }

    private fun guardar(nombre: String, texto: String, sobrescribir: Boolean): Uri? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = app.contentResolver
            val existente = if (sobrescribir) copias().firstOrNull { it.nombre == nombre }?.uri else null
            if (existente != null) {
                resolver.openOutputStream(existente, "wt")?.use { it.write(texto.toByteArray()) } ?: error("sin salida")
                existente
            } else {
                val valores = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, nombre)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, CARPETA)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores) ?: error("insert nulo")
                try {
                    resolver.openOutputStream(uri)?.use { it.write(texto.toByteArray()) } ?: error("sin salida")
                } catch (e: Throwable) {
                    resolver.delete(uri, null, null)
                    throw e
                }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                uri
            }
        } else {
            // Android 8-9: sin MediaStore de Descargas; queda en la carpeta de la app.
            val destino = File(app.getExternalFilesDir("respaldos"), nombre)
            destino.parentFile?.mkdirs()
            destino.writeText(texto)
            Uri.fromFile(destino)
        }
    } catch (e: Throwable) {
        Log.e(TAG, "no se pudo guardar $nombre", e)
        null
    }

    /** Deja solo las [cuantas] copias más nuevas con ese prefijo. */
    private fun podar(prefijo: String, cuantas: Int) {
        val viejas = copias().filter { it.nombre.startsWith(prefijo) }.sortedByDescending { it.nombre }.drop(cuantas)
        for (c in viejas) try {
            if (c.uri.scheme == "file") File(c.uri.path ?: continue).delete()
            else app.contentResolver.delete(c.uri, null, null)
        } catch (e: Throwable) {
            Log.e(TAG, "no se pudo borrar ${c.nombre}", e)
        }
    }

    // ---------------------------------------------------------------- listar

    /**
     * Las copias que ESTA instalación guardó, de la más nueva a la más vieja.
     * (Las de una instalación anterior no salen: Android no las deja ver sin
     * permisos; se abren con el selector de archivos.)
     */
    fun copias(): List<Copia> = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val lista = ArrayList<Copia>()
            app.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_MODIFIED),
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                arrayOf("$CARPETA%", "hablo-%.json"),
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0))
                    lista.add(Copia(uri, c.getString(1) ?: "", c.getLong(2) * 1000L))
                }
            }
            lista
        } else {
            (app.getExternalFilesDir("respaldos")?.listFiles() ?: emptyArray())
                .filter { it.name.startsWith("hablo-") && it.name.endsWith(".json") }
                .sortedByDescending { it.lastModified() }
                .map { Copia(Uri.fromFile(it), it.name, it.lastModified()) }
        }
    } catch (e: Throwable) {
        Log.e(TAG, "no se pudieron listar las copias", e)
        emptyList()
    }

    /** Lee un respaldo (propio o elegido con el selector). null si no es un JSON. */
    fun leer(uri: Uri): JSONObject? = try {
        app.contentResolver.openInputStream(uri)?.use { JSONObject(it.reader().readText()) }
    } catch (e: Throwable) {
        Log.e(TAG, "no se pudo leer $uri", e)
        null
    }

    // ------------------------------------------------------------- recuperar

    /**
     * Reemplaza el progreso de ahora por el del respaldo. Antes guarda una
     * copia de lo de ahora. Devuelve null si salió bien, o el motivo en
     * español. Después hay que reiniciar la pantalla (la app tiene copias en
     * memoria del mazo, el cuaderno, etc.): `Activity.recreate()`.
     */
    fun recuperar(json: JSONObject): String? {
        RespaldoDatos.validar(json)?.let { return it }
        if (!guardarSeguridad("recuperar")) return "No pude guardar una copia de lo que tienes ahora, así que no toqué nada."
        return try {
            val e = prefs.edit().clear()
            for ((k, v) in RespaldoDatos.prefsDe(json)) {
                when (v) {
                    is Int -> e.putInt(k, v)
                    is Long -> e.putLong(k, v)
                    is Float -> e.putFloat(k, v)
                    is Boolean -> e.putBoolean(k, v)
                    is String -> e.putString(k, v)
                    is Set<*> -> e.putStringSet(k, v.map { it.toString() }.toSet())
                }
            }
            // Recién recuperado: que la copia automática no tarde en reflejarlo.
            e.remove(K_AUTO)
            if (!e.commit()) return "No se pudieron escribir los ajustes."
            val contenidos = RespaldoDatos.archivosDe(json)
            for ((nombre, f) in archivos()) {
                if (f == null || !contenidos.containsKey(nombre)) continue
                val c = contenidos[nombre]
                if (c == null) f.delete() else escribirAtomico(f, c)
            }
            null
        } catch (t: Throwable) {
            Log.e(TAG, "recuperar falló", t)
            "Algo falló a mitad de camino. Lo de antes quedó guardado en Descargas › Hablo (hablo-antes-de-recuperar…)."
        }
    }

    private fun escribirAtomico(f: File, texto: String) {
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(texto)
        if (!tmp.renameTo(f)) {
            f.writeText(texto)
            tmp.delete()
        }
    }

    /** Abre el menú de compartir de Android con la copia ya guardada. */
    fun compartir(context: Context, uri: Uri) {
        if (uri.scheme != "content") return   // Android 8-9: un file:// no se puede compartir
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Respaldo de Hablo")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Guardar el respaldo en…").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    companion object {
        private const val TAG = "HabloRespaldo"
        private const val K_AUTO = "respaldo_auto_ms"
        const val PREFIJO_AUTO = "hablo-auto-"
        const val PREFIJO_ANTES = "hablo-antes-de-"
        /** Descargas › Hablo (así lo escribe MediaStore en RELATIVE_PATH). */
        val CARPETA: String = Environment.DIRECTORY_DOWNLOADS + "/Hablo/"
    }
}

/**
 * El formato del respaldo, en Kotlin puro (se prueba en el PC):
 * ```
 * {"formato": "hablo-respaldo", "version": 1, "app": "0.9.9", "fecha": "2026-09-24 15:30",
 *  "prefs": {"xp": {"t": "i", "v": 1240}, …},
 *  "archivos": {"mazo.json": "<texto>", "aptis.json": null, …}}
 * ```
 * Un archivo con `null` no existía al respaldar (al recuperar se borra); uno
 * que no aparece en `archivos` no se toca (respaldos de versiones viejas).
 */
object RespaldoDatos {
    const val FORMATO = "hablo-respaldo"
    const val VERSION = 1

    fun armar(prefs: Map<String, *>, archivos: Map<String, String?>, app: String, fecha: String): JSONObject {
        val p = JSONObject()
        for ((k, v) in prefs.toSortedMap()) {
            val (t, valor) = when (v) {
                is Int -> "i" to v
                is Long -> "l" to v
                is Float -> "f" to v.toDouble()
                is Boolean -> "b" to v
                is String -> "s" to v
                is Set<*> -> "ss" to JSONArray(v.map { it.toString() }.sorted())
                else -> continue
            }
            p.put(k, JSONObject().put("t", t).put("v", valor))
        }
        val a = JSONObject()
        for ((nombre, texto) in archivos) a.put(nombre, texto ?: JSONObject.NULL)
        return JSONObject()
            .put("formato", FORMATO)
            .put("version", VERSION)
            .put("app", app)
            .put("fecha", fecha)
            .put("prefs", p)
            .put("archivos", a)
    }

    /** null si el respaldo se puede recuperar; si no, el motivo en español. */
    fun validar(json: JSONObject): String? {
        if (json.optString("formato") != FORMATO) return "Ese archivo no es un respaldo de Hablo."
        val v = json.optInt("version", 0)
        if (v < 1) return "Ese archivo no es un respaldo de Hablo."
        if (v > VERSION) return "Ese respaldo es de una versión más nueva de Hablo. Instala la última versión y vuelve a intentarlo."
        if (json.optJSONObject("prefs") == null || json.optJSONObject("archivos") == null) return "El respaldo está incompleto."
        return try { prefsDe(json); archivosDe(json); null } catch (e: Throwable) { "El respaldo está dañado." }
    }

    fun prefsDe(json: JSONObject): Map<String, Any> {
        val p = json.optJSONObject("prefs") ?: return emptyMap()
        val out = LinkedHashMap<String, Any>()
        for (k in p.keys()) {
            val o = p.getJSONObject(k)
            out[k] = when (o.getString("t")) {
                "i" -> o.getInt("v")
                "l" -> o.getLong("v")
                "f" -> o.getDouble("v").toFloat()
                "b" -> o.getBoolean("v")
                "s" -> o.getString("v")
                "ss" -> o.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                else -> error("tipo desconocido en $k")
            }
        }
        return out
    }

    /** Solo los archivos que el respaldo trae; `null` = no existía. */
    fun archivosDe(json: JSONObject): Map<String, String?> {
        val a = json.optJSONObject("archivos") ?: return emptyMap()
        val out = LinkedHashMap<String, String?>()
        for (k in a.keys()) out[k] = if (a.isNull(k)) null else a.getString(k)
        return out
    }

    /** ¿Hay algo que valga la pena guardar? (Una app recién instalada no.) */
    fun hayAlgo(json: JSONObject): Boolean {
        val r = resumen(json)
        if (r.xp > 0 || r.lecciones > 0) return true
        return archivosDe(json).values.any { it != null }
    }

    /** Lo que se le muestra a Fero para decidir si recupera un respaldo. */
    data class Resumen(val fecha: String, val app: String, val lecciones: Int, val xp: Int, val racha: Int, val frases: Int) {
        fun texto(): String {
            val partes = ArrayList<String>()
            partes.add(if (lecciones == 1) "1 lección aprobada" else "$lecciones lecciones aprobadas")
            partes.add(if (xp == 1) "1 punto" else "${miles(xp)} puntos")
            if (frases > 0) partes.add(if (frases == 1) "1 frase en el repaso" else "${miles(frases)} frases en el repaso")
            return partes.joinToString(" · ")
        }
        private fun miles(n: Int): String = String.format(Locale("es", "CO"), "%,d", n)
    }

    fun resumen(json: JSONObject): Resumen {
        val p = try { prefsDe(json) } catch (e: Throwable) { emptyMap() }
        // El repaso y Aguanta se anotan con puntaje 0: no cuentan como lección.
        val lecciones = p.count { (k, v) -> k.startsWith("score_") && (v as? Int ?: 0) >= 60 }
        val frases = try {
            archivosDe(json)["mazo.json"]?.let { JSONObject(it).optJSONArray("items")?.length() } ?: 0
        } catch (e: Throwable) { 0 }
        return Resumen(
            fecha = json.optString("fecha"),
            app = json.optString("app"),
            lecciones = lecciones,
            xp = p["xp"] as? Int ?: 0,
            racha = p["streak"] as? Int ?: 0,
            frases = frases
        )
    }
}
