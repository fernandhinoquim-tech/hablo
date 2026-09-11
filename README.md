# Hablo

App para aprender inglés que funciona **100% sin internet**.

Hecha específicamente para hispanohablantes: corrige los errores que comete
alguien que piensa en español (*"I have 20 years"*, los falsos amigos, la `-s`
de tercera persona, la diferencia entre *ship* y *sheep*).

## Estado

**Versión 0.1 — Fase 1: escuchar y escribir.**

- [x] Elegir profesora (4 opciones, todas con voz femenina y amable)
- [x] Voz en inglés sin conexión, con acento americano o británico
- [x] Botón de "más lento" en cada frase
- [x] 3 lecciones de nivel A1 con 4 tipos de ejercicio
- [x] Racha, puntos y progreso guardados en el celular
- [ ] Fase 2 — hablar al micrófono, con puntaje de pronunciación
- [ ] Fase 3 — conversación con IA dentro del celular
- [ ] Fase 4 — corrección de escritura explicada en español

## Privacidad

La app **no pide permiso de internet** en el `AndroidManifest.xml`. No es que
prometa no conectarse: técnicamente no puede. Todo el progreso vive en el
teléfono y nada se envía a ningún servidor.

## Cómo se compila

No hay que instalar nada. Cada vez que se sube un cambio, GitHub Actions
compila el APK solo y lo publica en la pestaña **Releases**.

También se puede lanzar a mano desde la pestaña **Actions** → *Construir APK*
→ botón **Run workflow**.

## Tecnología

| Pieza | Qué es |
|---|---|
| Kotlin + Jetpack Compose | Lenguaje e interfaz oficiales de Android |
| Motor de voz de Android | Lee el inglés en voz alta, sin conexión |
| SharedPreferences | Guarda el progreso localmente |

En la Fase 2 se agregan **sherpa-onnx** (Apache 2.0) para reconocimiento de voz
y **Piper** para voces neuronales propias. En la Fase 3, **llama.cpp** (MIT) con
**Qwen 3** (Apache 2.0) para la conversación con IA.

Requisitos mínimos: Android 8.0 (API 26).
