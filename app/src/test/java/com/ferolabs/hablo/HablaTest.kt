package com.ferolabs.hablo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Puntaje de hablar contra lo que de verdad escribió el dictado en el teléfono
 * de Fero (grabaciones del 13 y 15 de septiembre de 2026). Cada caso es una
 * transcripción real; el objetivo es que lo bien dicho no salga en rojo.
 */
class HablaTest {

    private fun score(target: String, heard: String) = scorePronunciation(target, heard)
    private fun malas(r: PronunciationResult) = r.words.count { it.score == WordScore.MAL }

    @Test
    fun `twenty-five en cifras cuenta como bien dicho y el chip muestra la palabra del ejercicio`() {
        val r = score("I'm twenty-five years old.", "I'm 25 years old")   // 20260913-011111
        assertEquals(100, r.percent)
        assertEquals(listOf("I'm", "twenty-five", "years", "old"), r.words.map { it.word })
        assertTrue(r.entendida)
    }

    @Test
    fun `I am vale por I'm`() {
        val r = score("I'm twenty-five years old.", "I am 25 years old.")   // 20260913-011257: antes 50 %
        assertEquals(100, r.percent)
        assertEquals(100, score("I'm from Colombia.", "I am from Colombia.").percent)
        assertEquals(100, score("I don't understand.", "I do not understand").percent)
    }

    @Test
    fun `no se cierran contracciones a ciegas`() {
        // "I have the check" no es "I've": las contracciones se igualan a como estén en la frase.
        val r = score("Can I have the check, please?", "Can I have the check please")
        assertEquals(100, r.percent)
        assertEquals(listOf("Can", "I", "have", "the", "check", "please"), r.words.map { it.word })
    }

    @Test
    fun `una palabra cambiada por el dictado no reprueba`() {
        val r = score("I don't understand.", "I then understood")   // 20260915-154649: antes 50 % reprobado
        assertEquals(1, malas(r))
        assertTrue(r.entendida)
    }

    @Test
    fun `dos palabras falladas si reprueban`() {
        val r = score("I'm twenty-five years old.", "925 years old")   // 20260915-003345
        assertEquals(2, malas(r))
        assertFalse(r.entendida)
        assertFalse(score("She studies at home.", "Okay").entendida)   // 20260915-154250
    }

    @Test
    fun `la tarjeta del sonido no dice bien sobre palabras que el dictado no entendio`() {
        val bien = SoundReport(Sound.ES, listOf(SoundItem("studies", "s", 1.26, WordScore.BIEN)), 0L)
        assertNull(bien.fiable(score("She studies at home.", "Okay")))            // era "¡Ese sonido salió bien!"
        assertNotNull(bien.fiable(score("She studies at home.", "She studies at home")))
        // Un "mal" se conserva aunque no se haya entendido la palabra: castigar de más, no aprobar de más.
        val mal = SoundReport(Sound.ES, listOf(SoundItem("studies", "s", -9.0, WordScore.MAL)), 0L)
        assertNotNull(mal.fiable(score("She studies at home.", "Okay")))
        assertNull(mal.fiable(null))
    }

    @Test
    fun `sin oir nada todo queda en rojo`() {
        val r = score("Wash your shoes.", "")
        assertEquals(0, r.percent)
        assertFalse(r.entendida)
    }
}
