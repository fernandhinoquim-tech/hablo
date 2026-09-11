package com.ferolabs.hablo

/** Acento con el que habla la profesora. */
enum class Accent(val label: String) {
    US("Estados Unidos"),
    UK("Reino Unido")
}

/**
 * Una profesora. Todas tienen voz femenina y un tono amable.
 *
 * [pitch] y [rate] ajustan el motor de voz del celular para que cada una suene
 * distinta. [voiceSlot] elige entre las voces femeninas que tenga instaladas el
 * teléfono, así dos profesoras del mismo acento no suenan idénticas.
 */
data class Teacher(
    val id: String,
    val name: String,
    val emoji: String,
    val accent: Accent,
    val tagline: String,
    val description: String,
    val greeting: String,
    val encouragement: List<String>,
    val pitch: Float,
    val rate: Float,
    val voiceSlot: Int,
    val color: Long,
    val softColor: Long
)

val TEACHERS: List<Teacher> = listOf(
    Teacher(
        id = "emma",
        name = "Emma",
        emoji = "🌷",
        accent = Accent.US,
        tagline = "Cálida y muy paciente",
        description = "Habla despacio y repite sin quejarse. Si nunca has estudiado " +
            "inglés en serio, empieza con ella.",
        greeting = "Hi! I'm Emma. Take your time, there's no rush. Let's learn together.",
        encouragement = listOf(
            "Very good!",
            "That's right. Nice work.",
            "Perfect. You're getting it.",
            "Yes! Well done."
        ),
        pitch = 1.02f,
        rate = 0.80f,
        voiceSlot = 0,
        color = 0xFFD1604A,
        softColor = 0xFFFBEDE9
    ),
    Teacher(
        id = "sophie",
        name = "Sophie",
        emoji = "🌿",
        accent = Accent.UK,
        tagline = "Dulce y muy clara",
        description = "Acento británico suave. Pronuncia cada palabra con nitidez, " +
            "ideal si te cuesta distinguir los sonidos.",
        greeting = "Hello, I'm Sophie. Lovely to meet you. Shall we begin?",
        encouragement = listOf(
            "Lovely!",
            "That's exactly right.",
            "Beautiful. Keep going.",
            "Well done indeed."
        ),
        pitch = 1.06f,
        rate = 0.86f,
        voiceSlot = 1,
        color = 0xFF3E7D62,
        softColor = 0xFFE9F3EE
    ),
    Teacher(
        id = "mia",
        name = "Mia",
        emoji = "☀️",
        accent = Accent.US,
        tagline = "Alegre y motivadora",
        description = "Enérgica y positiva. Habla a velocidad más natural, buena " +
            "cuando ya te sientas con algo de confianza.",
        greeting = "Hey there! I'm Mia. You're gonna do great today, I can tell!",
        encouragement = listOf(
            "Yes! Nailed it!",
            "Awesome, keep it up!",
            "Look at you go!",
            "That's my student!"
        ),
        pitch = 1.14f,
        rate = 0.95f,
        voiceSlot = 2,
        color = 0xFFC2760F,
        softColor = 0xFFFCF2E1
    ),
    Teacher(
        id = "grace",
        name = "Grace",
        emoji = "🕊️",
        accent = Accent.UK,
        tagline = "Tranquila, sin presión",
        description = "La más pausada de todas. Nunca te apura y hace pausas largas " +
            "para que puedas repetir en voz alta.",
        greeting = "Hello. I'm Grace. Breathe. We'll go as slowly as you need.",
        encouragement = listOf(
            "That's it. Well done.",
            "Very nice.",
            "Good. You're doing well.",
            "Exactly right."
        ),
        pitch = 0.98f,
        rate = 0.72f,
        voiceSlot = 3,
        color = 0xFF4A5D8A,
        softColor = 0xFFEBEEF6
    )
)

fun teacherById(id: String?): Teacher =
    TEACHERS.firstOrNull { it.id == id } ?: TEACHERS[0]
