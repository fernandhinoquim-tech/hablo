# Hablo

App Android para aprender inglés que funciona **sin internet** (con una sola
excepción opcional: la conversación con Gemini, apagada por defecto).

Hecha específicamente para hispanohablantes: corrige los errores que comete
alguien que piensa en español (*"I have 20 years"*, los falsos amigos, la `-s`
de tercera persona) y trabaja los sonidos que más nos cuestan (*ship* / *sheep*,
la `th`, la `h` aspirada, *speak* sin la `e` delante).

## Estado

**Versión 0.8 — funcionando en el teléfono.**

- [x] Cuatro profesoras para elegir, cada una con su propia voz neuronal
      (Piper), acento americano o británico, dentro del APK
- [x] Botón de "más lento" en cada frase
- [x] Reconocimiento de voz sin conexión (Moonshine base vía sherpa-onnx),
      con limpieza del audio antes de reconocer y "No te entendí, repite"
      cuando el audio viene mudo, cortado o con mucho ruido
- [x] Evaluación de pronunciación **por fonema** (GOP, como las apps
      comerciales): en cada ejercicio se evalúa el sonido que entrena, con
      umbrales calibrados con grabaciones reales; verde / casi / falló, y un
      mapa personal de sonidos. Por ahora `sh` y `h`; los demás sonidos se van
      sumando a medida que se calibran
- [x] Puntaje de palabras ("¿se entendió?") con un reconocedor que nunca ve la
      frase esperada
- [x] Práctica de pronunciación con los sonidos difíciles para
      hispanohablantes, con explicación en español
- [x] A1 completo: 27 lecciones en 9 unidades con desbloqueo progresivo,
      5 tipos de ejercicio (escuchar, traducir, armar, escribir, hablar) y una
      ficha de teoría en español por lección
- [x] Racha, puntos y progreso guardados en el celular
- [x] Fase 3 — conversación con IA en escenarios: por internet con Gemini
      (si se enciende en Ajustes) o dentro del celular (llama.cpp + Qwen 3)
- [ ] Fase 4 — corrección de escritura explicada en español
- [ ] Fase 5 — contenido completo de A1 a B2

## Privacidad

Hasta la versión 0.8 la app no pedía permiso de internet. Desde la 0.9 lo
pide **solo** para la conversación con Gemini, que está apagada por defecto y
no funciona sin una clave que se copia a mano al teléfono. Cuando está
encendida sale únicamente el **texto** de la charla (lo que dijiste, ya
transcrito en el teléfono, y lo que responde la profesora); **nunca tu voz**.
Lecciones, pronunciación, voces, dictado y progreso siguen sin tocar la red.
Con el nivel gratuito de Google, ese texto puede usarse para mejorar sus
productos; con el nivel pago, no.

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
| Moonshine base v2 vía sherpa-onnx | Voz a texto, sin conexión, pensado para frases cortas. Elegido con grabaciones reales frente a Whisper y Parakeet porque no "corrige" lo que uno dice mal |
| wav2vec2 de fonemas (L2-ARCTIC) vía ONNX Runtime | Evaluación por fonema del sonido de cada ejercicio, con el diccionario CMU |
| SharedPreferences | Guarda el progreso localmente |

En la Fase 3 se suma **llama.cpp** (MIT) con **Qwen 3** (Apache 2.0) para la
conversación con IA.

Requisitos mínimos: Android 8.0 (API 26). El APK pesa unos 350 MB porque lleva
las voces y el reconocedor adentro.
