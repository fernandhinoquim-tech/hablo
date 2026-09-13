package com.ferolabs.hablo

import android.content.Context
import java.util.Calendar

/** Guarda el progreso en el propio celular. Nada de esto sale del teléfono. */
class Store(context: Context) {

    val context: Context = context.applicationContext

    private val prefs = this.context
        .getSharedPreferences("hablo_progress", Context.MODE_PRIVATE)

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
     * Migra solo desde el interruptor de la 0.9.
     */
    var conversationEngine: String
        get() = prefs.getString("conversation_engine", null)
            ?: if (prefs.getBoolean("cloud_conversation", false)) ENGINE_GEMINI else ENGINE_LOCAL
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
    }

    fun resetEverything() {
        prefs.edit().clear().apply()
    }
}
