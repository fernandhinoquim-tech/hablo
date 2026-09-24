package com.ferolabs.hablo

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * B2 trajo 6.074 `accept` (hasta 559 en un ejercicio, 24-09). Aquí se mide
 * con el curso real que cargar el curso y corregir el peor ejercicio sigan
 * siendo cosa de milisegundos. Los topes son holgados a propósito (el S25
 * corre a 1,2-1,8× el PC): lo que se quiere atrapar es una explosión
 * combinatoria, no ruido.
 */
class RendimientoTest {

    @Test
    fun cargarYCorregirElCursoRealEsRapido() {
        val texto = File("src/main/assets/content/curriculum.json").readText()
        Course.parseCurriculum(texto)   // calentar la JVM
        val t0 = System.nanoTime()
        val niveles = Course.parseCurriculum(texto)
        val cargaMs = (System.nanoTime() - t0) / 1e6

        var peorMs = 0.0
        var peorId = ""
        for (lv in niveles) for (u in lv.units) for (l in u.lessons) for (ex in l.exercises) {
            val mal = "this is certainly not the answer at all"
            val t = System.nanoTime()
            when (ex) {
                is Exercise.WriteIt -> { Correccion.acepta(mal, ex.answer, ex.accept); Correccion.diagnostico(mal, ex.answer, ex.accept) }
                is Exercise.Cloze -> {
                    Correccion.aceptaHueco(mal, ex.before, ex.after, ex.answer, ex.accept)
                    Correccion.diagnosticoHueco(mal, ex.before, ex.after, ex.answer, ex.accept)
                }
                is Exercise.BuildSentence -> { Correccion.acepta(mal, ex.answer, ex.accept); Correccion.diagnostico(mal, ex.answer, ex.accept) }
                is Exercise.TranslateChoose -> { Correccion.acepta(mal, ex.answer, ex.accept); Correccion.diagnostico(mal, ex.answer, ex.accept) }
                is Exercise.TypeWhatYouHear -> { Correccion.acepta(mal, ex.audio, ex.accept); Correccion.diagnostico(mal, ex.audio, ex.accept) }
                else -> {}
            }
            val ms = (System.nanoTime() - t) / 1e6
            if (ms > peorMs) { peorMs = ms; peorId = ex.id }
        }
        println("RENDIMIENTO carga=%.0f ms  peor corrección=%.1f ms (%s)".format(java.util.Locale.US, cargaMs, peorMs, peorId))
        assertTrue("cargar el curso tardó $cargaMs ms", cargaMs < 1500)
        assertTrue("corregir $peorId tardó $peorMs ms", peorMs < 150)
    }
}
