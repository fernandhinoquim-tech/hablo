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
   - Gradle 8.11.1 · AGP 8.7.3 · Kotlin 2.0.21 · Compose BOM 2024.10.01 ·
     sherpa-onnx 1.13.8 (el AAR sí se puede subir: no es parte de esta regla,
     pero hay que borrar el AAR viejo de `app/libs/` y probar las voces)
   - compileSdk 35 · minSdk 26 · JDK 17 (el JDK del sistema es 25 y Gradle
     8.11.1 no lo soporta; Android Studio usa el 21)
   - **Ya pasó (2026-09-11):** el asistente quedó aceptado y cambió AGP a
     8.13.2, Gradle a 8.13 y agregó el plugin foojay en `settings.gradle.kts`.
     Se revirtió con `git checkout`. Si `git status` muestra cambios en
     `build.gradle.kts`, `settings.gradle.kts` o `gradle-wrapper.properties`
     que nadie pidió, es eso: revertirlos, no comitearlos.
3. **Todo modelo o dato va empaquetado dentro del APK**, o al lado de la app en
   el teléfono. Nada se descarga en tiempo de ejecución.
4. **El contenido vive en JSON**, no en Kotlin. Ver
   `app/src/main/assets/content/curriculum.json`.

---

## Repositorio y herramientas

- **Repo:** https://github.com/fernandhinoquim-tech/hablo (privado, rama `main`).
  Existe también un `fchavesv/hablo` vacío que **no se usa**: el navegador y
  GitHub Desktop de Fero están con la cuenta fernandhinoquim-tech.
- **Un commit y push cada vez que algo quede funcionando.** Cada push a `main`
  dispara GitHub Actions (`.github/workflows/build.yml`), que compila el APK y
  lo publica como Release `build-N`; si aparece el tag, la compilación pasó.
- **git no está en el PATH.** Se usa el de GitHub Desktop:
  `C:\Users\ferna\AppData\Local\GitHubDesktop\app-3.6.4\resources\app\git\cmd\git.exe`
  (si GitHub Desktop se actualiza, cambia `app-3.6.4`). Para push hace falta,
  en la misma shell: agregar `...\git\cmd` y `...\git\mingw64\bin` al PATH,
  `GCM_INTERACTIVE=always` y `GIT_TERMINAL_PROMPT=0`. El token ya está guardado
  en el Administrador de credenciales de Windows; no vuelve a pedir login.
- **Compilar:** `$env:JAVA_HOME="C:\Users\ferna\.jdks\jbr-21.0.11"; .\gradlew assembleDebug`
  (el `java` del PATH es 1.8 y el jbr de Android Studio es 25: ninguno sirve).
- **Instalar:** `C:\Users\ferna\AppData\Local\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk`.
  Teléfono: serial `R5GL11XGA6F`. Logs del reconocedor: `adb logcat -s HabloListener`.
- **Banco de pruebas de voz:** `tools/asr-bench/` (ver abajo, "Corpus de voz").

---

## Arquitectura y por qué

| Pieza | Qué es | Por qué esa |
|---|---|---|
| Voces | **Piper** vía sherpa-onnx (Apache 2.0) | Cuatro voces femeninas reales dentro del APK. Antes se usaba el TTS de Android y obligaba al usuario a descargar paquetes de voz por fuera. |
| Voz → texto | **Moonshine base v2** (`moonshine-base-en-quantized-2026-02-27`, 2 archivos `.ort`, 140 MB) vía sherpa-onnx **1.13.8** | Elegido el 2026-09-12 con `tools/asr-bench` sobre 26 grabaciones reales de Fero. Tiny devolvía texto vacío en 4 de 26 con audio bueno y confundía palabras fáciles ("seatsbooks", "Can't you"); base no, y mantiene la honestidad (delata "chip", "espeak", "wok-ed"). Whisper base.en quedó descartado: **corrige** "espeak Espanish" a "speak Spanish" al 100 %. Parakeet 110m es el más honesto pero castiga también lo bien dicho. Límite conocido: "tink" por "think" lo corrigen los cinco modelos. Antes de reconocer, `Audio.kt` (`AudioPrep`) quita el DC, recorta el silencio, normaliza el pico y rechaza audio mudo/corto/ruidoso con "No te entendí, repite" en vez de dar 0 %. **Regla que no se negocia:** el reconocedor nunca ve la frase esperada (nada de hotwords ni sesgos); el objetivo solo se usa para puntuar después. |
| IA (Fase 3) | **llama.cpp + Qwen 3 8B Q4** | Qwen es Apache 2.0 sin letra chica. Se descartó Gemma 2/3 porque sus "Gemma Terms of Use" permiten a Google cambiar las condiciones después. |
| Interfaz | Kotlin + Jetpack Compose | Menos capas intermedias con tres motores nativos encima. |
| Progreso | SharedPreferences | Suficiente; nada sale del teléfono. |

Las voces y el motor sherpa-onnx **se descargan solos al compilar** — hay tareas
Gradle (`fetchSherpaAar`, `fetchVoices`, `fetchAsr`) que los bajan la primera
vez y los meten en `assets/`. No están en el repositorio.

El modelo de 8B (~5 GB) **no cabe dentro de un APK**: tiene que vivir como
archivo aparte en el teléfono, copiado una vez por USB.

**Corpus de voz y banco de pruebas.** La app guarda cada grabación cruda como
WAV + un `.txt` (frase, resultado, medidas) en
`Android/data/com.ferolabs.hablo/files/grabaciones` (últimas 40). Se bajan con
`adb pull` a `tools/asr-bench/corpus/` (ignorado en git: es la voz de Fero) y
`python tools/asr-bench/bench.py` corre varios modelos sobre ellas en el PC,
replicando `AudioPrep` en Python. Así se comparan modelos y se afinan umbrales
sin recompilar ni gastar ciclos de Fero. `fetch_models.ps1` baja los candidatos
a `tools/asr-bench/models/` (también ignorado). Si se cambia `AudioPrep.analyze`,
cambiar `prep()` en `bench.py` igual.

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
- `AudioEffect.setEnabled()` devuelve `int`: Kotlin no lo expone como
  propiedad; hay que llamar `setEnabled(true)`, no `enabled = true`.
- `"%.2f".format(x)` usa la configuración regional (coma decimal en español).
  Para logs que se leen desde el PC, pasar `Locale.US`.
- Fero graba con el teléfono desconectado del cable: `adb` se queda colgado
  si el teléfono no está. Revisar `adb devices` antes de cualquier `adb shell`.
- El `when` sobre `Exercise` tiene **cinco** subclases. Fácil olvidar una.

---

## Estado y plan

**Versión actual: 0.7.** Funcionando en el teléfono de Fero (2026-09-12).

Hecho: cuatro profesoras con voces propias · reconocimiento de voz con
Moonshine base v2 · pipeline de audio (`Audio.kt`: DC, recorte, normalización
por percentil, puertas de calidad) · "No te entendí, repite" en vez de 0 % ·
puntaje de pronunciación palabra por palabra (verificado honesto con
grabaciones a propósito mal dichas) · corpus de diagnóstico + banco de pruebas
en el PC · 8 lecciones A1 en 3 unidades con desbloqueo progresivo · ejercicios
de hablar dentro de las lecciones · racha y puntos.

Medido en el S25 Ultra: el reconocedor arranca en 0,4 s y reconoce en 50–90 ms
por frase. NoiseSuppressor se activa; AutomaticGainControl no existe en ese
teléfono (lo cubre la normalización). Umbrales de las puertas: cero rechazos
falsos en 26 grabaciones; silencio → TOO_SHORT.

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
