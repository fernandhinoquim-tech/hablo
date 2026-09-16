package com.ferolabs.hablo

import android.content.Context
import java.io.File
import java.util.Calendar

/** Guarda el progreso en el propio celular. Nada de esto sale del teléfono. */
class Store(context: Context) {

    val context: Context = context.applicationContext

    private val prefs = this.context
        .getSharedPreferences("hablo_progress", Context.MODE_PRIVATE)

    init {
        // Contadores del mapa de sonidos anteriores a la calibracion vigente:
        // se contaron con umbrales que el proyecto ya descarto (2 de cada 3
        // avisos eran falsa alarma) y nada los reiniciaba; el inicio mostraba
        // "sh 24 casi de 35" y "th 9 casi de 10" para siempre. Se borran una
        // sola vez por epoca; SUBIR SOUND_STATS_EPOCH cada vez que cambie
        // thresholds.json o PhonemeScorer.MIN_PRECISION.
        if (prefs.getInt("sound_stats_epoch", 1) < SOUND_STATS_EPOCH) {
            val e = prefs.edit()
            prefs.all.keys.filter { it.startsWith("sound_") }.forEach { e.remove(it) }
            e.putInt("sound_stats_epoch", SOUND_STATS_EPOCH).apply()
        }
    }

    var teacherId: String?
        get() = prefs.getString("teacher_id", null)
        set(value) = prefs.edit().putString("teacher_id", value).apply()

    /** Mostrar el retrato de la profesora (si no, solo la voz y el halo). */
    var showFaces: Boolean
        get() = prefs.getBoolean("show_faces", true)
        set(value) = prefs.edit().putBoolean("show_faces", value).apply()

    /**
     * Quién hace de profesora en la conversación: [ENGINE_LOCAL] (la IA del
     * teléfono, sin internet), [ENGINE_GEMINI] (gratis, con altibajos) o un id
     * de modelo de Claude (de pago). Por internet solo va el texto.
     * Sin elección guardada: Claude si la clave está en el teléfono (es el
     * motor por defecto desde el 2026-09-13), si no el interruptor viejo de
     * la 0.9, si no la IA del teléfono.
     */
    var conversationEngine: String
        get() = prefs.getString("conversation_engine", null)
            ?: if (File(context.getExternalFilesDir("modelos"), ClaudeLlm.KEY_FILE).exists()) ClaudeLlm.MODELOS[0].first
            else if (prefs.getBoolean("cloud_conversation", false)) ENGINE_GEMINI else ENGINE_LOCAL
        set(value) = prefs.edit().putString("conversation_engine", value).apply()

    /** Multiplicador global de velocidad de la voz (0.6 = lento, 1.4 = rápido). */
    var speechScale: Float
        get() = prefs.getFloat("speech_scale", 1.0f)
        set(value) = prefs.edit().putFloat("speech_scale", value).apply()

    var xp: Int
        get() = prefs.getInt("xp", 0)
        private set(value) = prefs.edit().putInt("xp", value).apply()

    var streak: Int
        get() = prefs.getInt("streak", 0)
        private set(value) = prefs.edit().putInt("streak", value).apply()

    /** Mejor puntaje obtenido en una lección, de 0 a 100. */
    fun bestScore(lessonId: String): Int = prefs.getInt("score_$lessonId", 0)

    fun recordLesson(lessonId: String, score: Int, earnedXp: Int) {
        if (score > bestScore(lessonId)) {
            prefs.edit().putInt("score_$lessonId", score).apply()
        }
        xp += earnedXp
        touchStreak()
    }

    fun isCompleted(lessonId: String): Boolean = bestScore(lessonId) >= 60

    /** Día absoluto desde 1970. Así el cambio de año no rompe la racha. */
    private fun todayKey(): Int {
        val c = Calendar.getInstance()
        val millis = c.timeInMillis + c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)
        return (millis / 86_400_000L).toInt()
    }

    /** Actualiza la racha: +1 si es un día nuevo consecutivo, reinicia si se saltó días. */
    private fun touchStreak() {
        val today = todayKey()
        val last = prefs.getInt("last_day", 0)
        if (last == today) return

        val newStreak = when {
            last == 0 -> 1
            today - last == 1 -> streak + 1
            else -> 1
        }
        prefs.edit()
            .putInt("last_day", today)
            .putInt("streak", newStreak)
            .apply()
    }

    fun studiedToday(): Boolean = prefs.getInt("last_day", 0) == todayKey()

    // --- Historias (etapa 4) -------------------------------------------------
    fun historiaHecha(id: String): Boolean = prefs.getBoolean("historia_$id", false)
    fun marcarHistoria(id: String) = prefs.edit().putBoolean("historia_$id", true).apply()

    // --- Mapa personal de sonidos ---------------------------------------------
    // Lo valioso no es la frase de hoy: es "estos son los sonidos que fallo de
    // verdad, medido en muchas frases". Se acumula entre sesiones.

    data class SoundStats(val tries: Int, val mal: Int, val dudoso: Int) {
        val ok: Int get() = tries - mal - dudoso
    }

    fun soundStats(sound: Sound): SoundStats = SoundStats(
        tries = prefs.getInt("sound_${sound.key}_tries", 0),
        mal = prefs.getInt("sound_${sound.key}_mal", 0),
        dudoso = prefs.getInt("sound_${sound.key}_dudoso", 0)
    )

    fun recordSound(sound: Sound, verdict: WordScore) {
        val st = soundStats(sound)
        prefs.edit()
            .putInt("sound_${sound.key}_tries", st.tries + 1)
            .putInt("sound_${sound.key}_mal", st.mal + if (verdict == WordScore.MAL) 1 else 0)
            .putInt("sound_${sound.key}_dudoso", st.dudoso + if (verdict == WordScore.DUDOSO) 1 else 0)
            .apply()
    }

    companion object {
        const val ENGINE_LOCAL = "local"
        const val ENGINE_GEMINI = "gemini"
        /** Epoca de los contadores de sonidos: 2 = MIN_PRECISION 0,50 del 2026-09-13 (commit c4ee744). */
        const val SOUND_STATS_EPOCH = 2
    }

    // --- Intentos de hoy por frase de pronunciación ---------------------------
    // Machacar la misma frase quince veces en una noche vale la mitad que
    // repartirla en días: en entrenamiento de sonidos, la misma práctica
    // espaciada rinde aproximadamente el doble que amontonada. La app no lo
    // prohíbe, pero deja de proponer la frase cuando ya se trabajó hoy.

    fun drillTriesToday(key: String): Int =
        if (prefs.getInt("drill_day", 0) != todayKey()) 0 else prefs.getInt("drill_$key", 0)

    fun recordDrillTry(key: String) {
        val e = prefs.edit()
        val previas = if (prefs.getInt("drill_day", 0) == todayKey()) {
            prefs.getInt("drill_$key", 0)
        } else {
            // Día nuevo: se borran los contadores del día anterior.
            prefs.all.keys.filter { it.startsWith("drill_") }.forEach { e.remove(it) }
            0
        }
        e.putInt("drill_day", todayKey()).putInt("drill_$key", previas + 1).apply()
    }

    /** Borra el progreso (puntos, racha, lecciones, sonidos) pero conserva los ajustes. */
    fun resetEverything() {
        val motor = prefs.getString("conversation_engine", null)
        val caras = prefs.getBoolean("show_faces", true)
        val velocidad = prefs.getFloat("speech_scale", 1.0f)
        val e = prefs.edit().clear()
        if (motor != null) e.putString("conversation_engine", motor)
        e.putBoolean("show_faces", caras)
            .putFloat("speech_scale", velocidad)
            .putInt("sound_stats_epoch", SOUND_STATS_EPOCH)
            .apply()
    }
}
