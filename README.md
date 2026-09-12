# Hablo

App Android para aprender inglés que funciona **100% sin internet**.

Hecha específicamente para hispanohablantes: corrige los errores que comete
alguien que piensa en español (*"I have 20 years"*, los falsos amigos, la `-s`
de tercera persona) y trabaja los sonidos que más nos cuestan (*ship* / *sheep*,
la `th`, la `h` aspirada, *speak* sin la `e` delante).

## Estado

**Versión 0.6 — funcionando en el teléfono.**

- [x] Cuatro profesoras para elegir, cada una con su propia voz neuronal
      (Piper), acento americano o británico, dentro del APK
- [x] Botón de "más lento" en cada frase
- [x] Reconocimiento de voz sin conexión (Moonshine vía sherpa-onnx)
- [x] Puntaje de pronunciación palabra por palabra. Es honesto: baja cuando
      uno pronuncia mal a propósito, porque el reconocedor nunca sabe qué
      frase se esperaba
- [x] Práctica de pronunciación con los sonidos difíciles para
      hispanohablantes, con explicación en español
- [x] 8 lecciones de nivel A1 en 3 unidades con desbloqueo progresivo y
      5 tipos de ejercicio (escuchar, traducir, armar, escribir, hablar)
- [x] Racha, puntos y progreso guardados en el celular
- [ ] Fase 3 — conversación con IA dentro del celular (llama.cpp + Qwen 3)
- [ ] Fase 4 — corrección de escritura explicada en español
- [ ] Fase 5 — contenido completo de A1 a B2

**En curso (0.7):** mejorar el reconocimiento de voz. Se limpia el audio antes
de reconocer (silencio, volumen, ruido), se distingue "pronunciaste mal" de
"no te escuché" y se está evaluando subir de modelo con grabaciones reales.

## Privacidad

La app **no pide permiso de internet** en el `AndroidManifest.xml`. No es que
prometa no conectarse: técnicamente no puede. Todo el progreso vive en el
teléfono y ningún audio sale de él.

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
| Moonshine tiny int8 vía sherpa-onnx | Voz a texto, sin conexión, pensado para frases cortas |
| SharedPreferences | Guarda el progreso localmente |

En la Fase 3 se suma **llama.cpp** (MIT) con **Qwen 3** (Apache 2.0) para la
conversación con IA.

Requisitos mínimos: Android 8.0 (API 26). El APK pesa unos 350 MB porque lleva
las voces y el reconocedor adentro.
