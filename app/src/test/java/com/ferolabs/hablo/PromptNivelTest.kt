package com.ferolabs.hablo

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * El estilo de la profesora sube con el nivel del escenario. En B2 la regla
 * fija NO puede pedir "simple English": contradice el papel de Cowork
 * ("natural B2 level… no simplifying") y gana la regla (24-09).
 * Deja los prompts en build/prompts/ para probarlos con la API desde el PC.
 */
class PromptNivelTest {

    private fun escenario(id: String): Scenario {
        val arr = JSONObject(File("src/main/assets/content/scenarios.json").readText()).getJSONArray("scenarios")
        val o = (0 until arr.length()).map { arr.getJSONObject(it) }.first { it.getString("id") == id }
        fun lista(k: String) = o.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) } }
        return Scenario(
            id = id, title = o.getString("title"), emoji = o.optString("emoji"), level = o.getString("level"),
            goalEs = o.getString("goalEs"), role = o.getString("role"), opening = o.getString("opening"),
            targets = lista("targets"), help = emptyList(), watch = lista("watch")
        )
    }

    @Test
    fun enB2NoSePideInglesSimple() {
        val b2 = buildSystemPrompt(escenario("defender_investigacion"), teacherById("sophie"))
        assertFalse(b2.contains("simple English"))
        assertTrue(b2.contains("do not simplify"))
        for (nivel in listOf("A1", "A2", "B1")) assertTrue(estiloPorNivel(nivel).contains("simple English"))

        val dir = File("build/prompts").apply { mkdirs() }
        for (id in listOf("defender_investigacion", "negociar_b2", "debate_b2")) {
            val sc = escenario(id)
            File(dir, "$id.txt").writeText(buildSystemPrompt(sc, teacherById("sophie")))
            File(dir, "$id.opening.txt").writeText(sc.opening)
        }
    }
}
