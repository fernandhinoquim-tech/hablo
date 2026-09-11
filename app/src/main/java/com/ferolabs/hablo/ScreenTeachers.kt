package com.ferolabs.hablo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun TeacherPickerScreen(
    currentId: String?,
    isFirstTime: Boolean,
    onPreview: (Teacher) -> Unit,
    onChoose: (Teacher) -> Unit,
    onBack: (() -> Unit)?
) {
    var selected by remember(currentId) { mutableStateOf(currentId ?: TEACHERS[0].id) }
    val selectedTeacher = teacherById(selected)

    Column(modifier = Modifier.fillMaxSize()) {

        if (!isFirstTime) TopBar("Cambiar de profesora", onBack = onBack)

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = if (isFirstTime) 36.dp else 4.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (isFirstTime) {
                item {
                    Column {
                        Text(
                            "Hablo",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Aprende inglés sin internet. Elige quién te va a enseñar.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = InkSoft
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }

            items(TEACHERS, key = { it.id }) { t ->
                TeacherCard(
                    teacher = t,
                    isSelected = t.id == selected,
                    onSelect = { selected = t.id },
                    onPreview = {
                        selected = t.id
                        onPreview(t)
                    }
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Todas hablan despacio y con paciencia. Puedes cambiar de profesora " +
                        "cuando quieras, sin perder tu progreso.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Cream)
                .padding(20.dp)
        ) {
            BigButton(
                text = if (isFirstTime) "Empezar con ${selectedTeacher.name}"
                else "Quedarme con ${selectedTeacher.name}",
                container = Color(selectedTeacher.color)
            ) {
                onChoose(selectedTeacher)
            }
        }
    }
}

@Composable
private fun TeacherCard(
    teacher: Teacher,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit
) {
    val accent = Color(teacher.color)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) Color(teacher.softColor) else Color.White,
                RoundedCornerShape(16.dp)
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) accent else Line,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onSelect() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .background(Color(teacher.softColor), CircleShape)
                    .border(1.dp, accent.copy(alpha = 0.35f), CircleShape)
            ) {
                Text(teacher.emoji, style = MaterialTheme.typography.headlineMedium)
            }

            Spacer(Modifier.size(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(teacher.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    teacher.tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = accent
                )
            }

            SpeakerButton(tint = accent, onClick = onPreview)
        }

        Spacer(Modifier.height(10.dp))

        Text(
            teacher.description,
            style = MaterialTheme.typography.bodyMedium,
            color = InkSoft
        )

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(teacher.accent.label, accent, Color(teacher.softColor))
            Pill("Voz femenina", InkSoft, Color(0xFFF3EDE6))
        }
    }
}
