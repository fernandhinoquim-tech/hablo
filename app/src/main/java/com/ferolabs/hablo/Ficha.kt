package com.ferolabs.hablo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * La ficha de teoría de la lección, en español: el título, el cuerpo (con
 * **negrita** y *cursiva*) y, aparte y destacada, la trampa del que piensa en
 * español. Las 56 fichas existían en el JSON desde el 14-09 y nunca se
 * mostraban (revisión de Cowork, 2026-09-16); Fero las pidió dos veces
 * ("falta una parte de explicar"). La instrucción explícita de gramática da
 * d = 0,96 (Norris & Ortega 2000): tenerla escrita y no mostrarla era tirar
 * ese efecto. Se muestra al abrir la lección (después de "adivina antes de
 * ver", que es el intento; esto es la explicación) y se puede volver a abrir
 * desde el 📖 de la barra durante la lección.
 */
@Composable
fun FichaScreen(lesson: Lesson, accent: Color, primeraVez: Boolean, onDone: () -> Unit, onBack: () -> Unit) {
    val t = lesson.theory
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = "${lesson.title}  ·  la ficha", onBack = onBack)
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text("📖 " + t.title, style = MaterialTheme.typography.headlineSmall, color = Ink)
            for (parrafo in t.body.split(Regex("\\n\\s*\\n"))) {
                if (parrafo.isBlank()) continue
                Text(
                    markdownLite(parrafo.trim()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink
                )
            }
            if (t.trap.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF6E3), RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0xFFF0DFB9), RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        "OJO: LA TRAMPA DEL QUE PIENSA EN ESPAÑOL",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF8A5A00)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(markdownLite(t.trap), style = MaterialTheme.typography.bodyLarge, color = Color(0xFF4A3A12))
                }
            }
            Text(
                "Puedes volver a esta ficha en cualquier momento con el 📖 de arriba.",
                style = MaterialTheme.typography.labelMedium,
                color = InkSoft
            )
        }
        Column(modifier = Modifier.fillMaxWidth().background(Cream).padding(20.dp)) {
            BigButton(if (primeraVez) "Empezar los ejercicios" else "Volver a la lección", container = accent) { onDone() }
            if (primeraVez) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Saltar la ficha",
                    style = MaterialTheme.typography.labelLarge,
                    color = InkSoft,
                    modifier = Modifier.clickable { onDone() }.padding(6.dp)
                )
            }
        }
    }
}

/**
 * Markdown mínimo, el que usan las fichas: `**negrita**` y `*cursiva*`
 * (anidados como *He work**s***). Los saltos de línea sueltos se respetan.
 */
fun markdownLite(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    var bold = false
    var italic = false
    fun push() {
        var style = SpanStyle()
        if (bold) style = style.merge(SpanStyle(fontWeight = FontWeight.Bold))
        if (italic) style = style.merge(SpanStyle(fontStyle = FontStyle.Italic))
        pushStyle(style)
    }
    push()
    while (i < text.length) {
        if (text.startsWith("**", i)) {
            pop(); bold = !bold; push(); i += 2
        } else if (text[i] == '*') {
            pop(); italic = !italic; push(); i += 1
        } else {
            append(text[i]); i += 1
        }
    }
    pop()
}
