# Hablo — reglas del proyecto

App Android para aprender inglés que funciona **100% sin internet**, hecha
específicamente para hispanohablantes. Es un proyecto personal de Fero
(fernandhinoquim@gmail.com), no un producto comercial. Meta: llevarlo de A1 a
B2 leyendo, escribiendo, escuchando y hablando.

---

## Cómo trabajar con Fero

- **Habla siempre en español.** Es colombiano.
- **Fero no programa.** No le pidas que corra comandos, que edite archivos ni
  que interprete stack traces. Corre tú los comandos, lee tú los errores.
  Cuando necesites algo de él, dale clics exactos, no instrucciones técnicas.
- **Minimiza los ciclos fallidos.** Cada compilación rota le cuesta tiempo y
  paciencia. Ya abandonó un proyecto anterior por fricción con una API. Prefiere
  verificar dos veces antes que hacerle probar tres.
- **Sé honesto sobre los límites.** Prefiere una advertencia incómoda a una
  promesa que no se cumple. Si algo no va a funcionar bien, dilo antes.
- Compila con `gradlew` e instala en su **Samsung Galaxy S25 Ultra** por USB
  (`adb`). El emulador tiene el disco lleno y no sirve para el micrófono.

---

## Reglas duras — no romper

1. **Sin permiso de internet.** El `AndroidManifest.xml` no declara
   `INTERNET` a propósito. No es una promesa: es imposibilidad técnica. No lo
   agregues por ninguna razón.
2. **No actualizar el Android Gradle Plugin.** Android Studio va a ofrecer el
   "AGP Upgrade Assistant" cada vez. Decir que no. Las versiones están fijadas
   porque se sabe que funcionan juntas:
   - Gradle 8.11.1 · AGP 8.7.3 · Kotlin 2.0.21 · Compose BOM 2024.10.01
   - compileSdk 35 · minSdk 26 · JDK 17 (el JDK del sistema es 25 y Gradle
     8.11.1 no lo soporta; Android Studio usa el 21)
3. **Todo modelo o dato va empaquetado dentro del APK**, o al lado de la app en
   el teléfono. Nada se descarga en tiempo de ejecución.
4. **El contenido vive en JSON**, no en Kotlin. Ver
   `app/src/main/assets/content/curriculum.json`.

---

## Arquitectura y por qué

| Pieza | Qué es | Por qué esa |
|---|---|---|
| Voces | **Piper** vía sherpa-onnx (Apache 2.0) | Cuatro voces femeninas reales dentro del APK. Antes se usaba el TTS de Android y obligaba al usuario a descargar paquetes de voz por fuera. |
| Voz → texto | **Moonshine tiny int8** vía sherpa-onnx | Diseñado para frases cortas. Verificado: **no** "corrige" la pronunciación del usuario, así que el puntaje es honesto. |
| IA (Fase 3) | **llama.cpp + Qwen 3 8B Q4** | Qwen es Apache 2.0 sin letra chica. Se descartó Gemma 2/3 porque sus "Gemma Terms of Use" permiten a Google cambiar las condiciones después. |
| Interfaz | Kotlin + Jetpack Compose | Menos capas intermedias con tres motores nativos encima. |
| Progreso | SharedPreferences | Suficiente; nada sale del teléfono. |

Las voces y el motor sherpa-onnx **se descargan solos al compilar** — hay tareas
Gradle (`fetchSherpaAar`, `fetchVoices`, `fetchAsr`) que los bajan la primera
vez y los meten en `assets/`. No están en el repositorio.

El modelo de 8B (~5 GB) **no cabe dentro de un APK**: tiene que vivir como
archivo aparte en el teléfono, copiado una vez por USB.

---

## Trampas ya pisadas — no repetirlas

- **`AudioTrack` con `MODE_STREAM`:** `write()` solo encola. Si sueltas el track
  justo después, el audio nunca suena. Hay que esperar a que
  `playbackHeadPosition` llegue al final. Costó dos sesiones encontrarlo.
- **Reproducir en 16 bits**, no en coma flotante. Más compatible.
- **`espeak-ng-data` no se puede leer desde `assets`:** hay que copiarlo al
  almacenamiento interno primero. Son ~95 MB y tarda; por eso la pantalla de
  inicio muestra "Preparando las voces… %" en el primer arranque.
- En `build.gradle.kts`, `java` choca con la extensión de Gradle: importar
  `java.net.URI` arriba en vez de escribir `java.net.URI(...)` inline.
- `sumOf { }` con literales enteros es ambiguo entre `Int` y `Long`. Usar un
  bucle explícito.
- Un `object` de Kotlin no puede tener `companion object`.
- El `when` sobre `Exercise` tiene **cinco** subclases. Fácil olvidar una.

---

## Estado y plan

**Versión actual: 0.6.** Funcionando en el teléfono de Fero.

Hecho: cuatro profesoras con voces propias · reconocimiento de voz · puntaje de
pronunciación palabra por palabra (verificado honesto) · 8 lecciones A1 en 3
unidades con desbloqueo progresivo · ejercicios de hablar dentro de las
lecciones · racha y puntos.

Siguiente:

- **Fase 3 — Conversación con IA.** llama.cpp + Qwen3 8B, ~50 escenarios
  cerrados, ciclo completo de voz. Incluye el avatar animado de la profesora:
  Fero va a generar cuatro retratos con una herramienta de imágenes y hay que
  integrarlos.
- **Fase 4 — Escritura y corrección**, con motor de reglas de errores de
  hispanohablante (no dejarlo todo a la IA: las reglas no alucinan).
- **Fase 5 — Contenido A1→B2.** 160 lecciones, 4.200 palabras, 50 escenarios,
  120 reglas de error, 100 textos. Es la montaña real del proyecto.
- **Fase 6 — Pulido.** Fonemas, respaldo del progreso, modo oscuro.

## El sello de la casa

Lo que diferencia a Hablo de cualquier app grande: corrige los errores que comete
**quien piensa en español**. *I have 25 years* · *He work on Monday* · *My sister
is doctor* · *I'm agree* · *actually* por *currently* · decir *espeak* y
*estudent*. Cada vez que agregues contenido, piensa en eso primero.

Y la pronunciación se enfoca en los sonidos que nos cuestan: `ship`/`sheep`, la
`th`, la `h` aspirada, `v` contra `b`, la `-ed` final, las consonantes al cierre.
