# Hablo

App Android para aprender inglés. Lecciones, pronunciación y progreso funcionan
**sin internet**; la única excepción es la conversación con la profesora, que
puede salir como texto a Claude o Gemini si se elige en Ajustes (o quedarse en
el teléfono con una IA local).

Hecha específicamente para hispanohablantes: corrige los errores que comete
alguien que piensa en español (*"I have 20 years"*, los falsos amigos, la `-s`
de tercera persona) y trabaja los sonidos que más nos cuestan (*ship* / *sheep*,
la `th`, la `h` aspirada, *speak* sin la `e` delante).

## Estado

**Versión 0.9 — funcionando en el teléfono.**

- [x] Cuatro profesoras para elegir, cada una con su propia voz neuronal
      (Piper), acento americano o británico, dentro del APK
- [x] Botón de "más lento" en cada frase
- [x] Reconocimiento de voz sin conexión (Moonshine base vía sherpa-onnx),
      con limpieza del audio antes de reconocer y "No te entendí, repite"
      cuando el audio viene mudo, cortado o con mucho ruido
- [x] Evaluación de pronunciación **por fonema** (GOP, como las apps
      comerciales): en cada ejercicio se evalúa el sonido que entrena, con
      umbrales calibrados con grabaciones reales; verde / casi / falló, y un
      mapa personal de sonidos. Solo se muestra un veredicto cuando su
      precisión medida llega al 50 %
- [x] Puntaje de palabras ("¿se entendió?") con un reconocedor que nunca ve la
      frase esperada; contracciones y números en cifras o letras valen igual
- [x] Práctica de pronunciación con los sonidos difíciles para
      hispanohablantes, con explicación en español
- [x] **Dos niveles, A1 y A2: 56 lecciones en 19 unidades, 565 ejercicios**,
      con desbloqueo progresivo y una ficha de teoría en español por lección.
      Nueve tipos de ejercicio: escuchar, traducir, armar, dictado, hablar,
      escribir, completar el hueco, repetir (shadowing) y oído (pares mínimos)
- [x] Corrección de lo escrito que dice **qué** falló ("te faltó la -s de
      works", "sobra la palabra am"), en español
- [x] Juego de Parejas a mitad de cada lección
- [x] Conversación con IA en escenarios: Claude API (de pago, la mejor),
      Gemini API (gratis, con cuotas) o Qwen3 8B dentro del celular
      (llama.cpp); corrección en español en texto
- [x] Cuaderno de errores e informe descargable en Markdown para llevar a un
      tutor de IA
- [x] Racha, puntos y progreso guardados en el celular
- [ ] Charla libre con memoria de lo básico (etapa 2)
- [ ] Escenarios de conversación de A2 y B1
- [ ] Repaso espaciado, retos e historias (etapas 3 y 4)
- [ ] Modo Aptis (etapa 5) y contenido B1–B2 (etapa 6)

## Privacidad

Hasta la versión 0.8 la app no pedía permiso de internet. Desde la 0.9 lo pide
**solo** para la conversación, y solo si en Ajustes se elige Claude o Gemini;
sin una clave copiada a mano al teléfono esa opción no funciona. Cuando está
activa sale únicamente el **texto** de la charla (lo que dijiste, ya
transcrito en el teléfono, y lo que responde la profesora); **nunca tu voz**.
Lecciones, pronunciación, voces, dictado y progreso siguen sin tocar la red.
Anthropic no entrena con lo enviado por API; con el nivel gratuito de Google,
ese texto puede usarse para mejorar sus productos (con el nivel pago, no).

## Cómo se compila

Las voces, el reconocedor y el motor sherpa-onnx **no están en el repositorio**:
Gradle los descarga solo la primera vez que se compila y los mete dentro del
APK. Después de eso la app no depende de nada externo.

- En el PC: `gradlew assembleDebug` con JDK 17–21 y luego `adb install` al
  teléfono (los detalles están en `CLAUDE.md`).
- Automático: cada vez que se sube un cambio, GitHub Actions compila el APK y
  lo publica en la pestaña **Releases**. También se puede lanzar a mano desde
  **Actions** → *Construir APK* → **Run workflow**.

## Tecnología

| Pieza | Qué es |
|---|---|
| Kotlin + Jetpack Compose | Lenguaje e interfaz oficiales de Android |
| Piper vía sherpa-onnx (Apache 2.0) | Voces neuronales en inglés, sin conexión |
| Moonshine base v2 vía sherpa-onnx | Voz a texto en lecciones y pronunciación, sin conexión. Elegido con grabaciones reales frente a Whisper y Parakeet porque no "corrige" lo que uno dice mal |
| Parakeet 0.6B vía sherpa-onnx | Voz a texto en la conversación (el que mejor adivina); vive al lado de la app |
| wav2vec2 de fonemas (L2-ARCTIC) vía ONNX Runtime | Evaluación por fonema del sonido de cada ejercicio, con el diccionario CMU |
| Claude API / Gemini API (REST, solo texto) | La profesora de conversación por internet, si se elige |
| llama.cpp (MIT) + Qwen3 8B (Apache 2.0) | La profesora de conversación dentro del celular; el modelo (5 GB) se copia por USB |
| SharedPreferences + `progreso.json` | Progreso y cuaderno de errores, solo en el teléfono |

Requisitos mínimos: Android 8.0 (API 26). El APK pesa unos 600 MB porque lleva
las voces, el dictado y el modelo de fonemas adentro; el modelo de IA local
(5 GB) va aparte.
