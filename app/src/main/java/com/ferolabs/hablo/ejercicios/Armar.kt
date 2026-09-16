package com.ferolabs.hablo.ejercicios

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ferolabs.hablo.Exercise
import com.ferolabs.hablo.InkSoft
import com.ferolabs.hablo.Line
import com.ferolabs.hablo.WordChip

/**
 * ARMAR la frase tocando palabras (`build`). Sacado de ScreenLesson.kt tal
 * cual (2026-09-16). [built] guarda posiciones del banco, no palabras, para
 * soportar repetidas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArmaLaFrase(
    ex: Exercise.BuildSentence,
    bank: List<String>,
    built: List<Int>,
    checked: Boolean,
    accent: Color,
    onBuilt: (List<Int>) -> Unit
) {
    Text("Arma la frase tocando las palabras", style = MaterialTheme.typography.titleLarge)
    Text(
        "\"${ex.es}\"",
        style = MaterialTheme.typography.titleMedium,
        color = accent
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 74.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        if (built.isEmpty()) {
            Text(
                "Tu respuesta aparece aquí",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoft
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                built.forEachIndexed { position, bankIndex ->
                    WordChip(bank[bankIndex], accent, filled = true) {
                        if (!checked) {
                            onBuilt(built.toMutableList().also { it.removeAt(position) })
                        }
                    }
                }
            }
        }
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        bank.forEachIndexed { bankIndex, word ->
            val used = built.contains(bankIndex)
            WordChip(word, accent, filled = false, dimmed = used) {
                if (!checked && !used) onBuilt(built + bankIndex)
            }
        }
    }
}
