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
   `app/src/main/assets/content/curriculum.json` (lecciones) y
   `drills.json` (práctica de pronunciación).
5. **Todo ejercicio de hablar lleva `"sound"` obligatorio** (`sh`, `th`, `h`,
   `v`, `ed`, `final`, `es`, `rl` o `general`): es lo que decide qué jurado
   lo puntúa. Si falta o está mal escrito, **la compilación se cae**
   (`gradlew checkContent`, corre antes de `preBuild`) y, si el JSON se
   editó por fuera, **la app lo dice en la pantalla de inicio** con lección y
   número de ejercicio (`Content.kt`, `requireSound`). Nada de esto falla en
   silencio a propósito: con 160 lecciones en la Fase 5, un ejercicio sin
   etiqueta haría que el jurado no dispare y nadie se enteraría. Un tipo de
   ejercicio desconocido también revienta (antes se saltaba callado).

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
| Voz → texto | **Moonshine base v2** (`moonshine-base-en-quantized-2026-02-27`, 2 archivos `.ort`, 140 MB) vía sherpa-onnx **1.13.8** | Elegido el 2026-09-12 con `tools/asr-bench` sobre 26 grabaciones reales de Fero. Tiny devolvía texto vacío en 4 de 26 con audio bueno y confundía palabras fáciles ("seatsbooks", "Can't you"); base no, y mantiene la honestidad (delata "chip", "espeak", "wok-ed"). Whisper base.en quedó descartado: **corrige** "espeak Espanish" a "speak Spanish" al 100 %. Parakeet 110m es el más honesto pero castiga también lo bien dicho. También se probaron los grandes (Fero dijo que el peso no importa): **Whisper small.en** (74 % promedio, el mejor adivinando) y **Parakeet 0.6B v2** (70 %) corrigen los dos "espeak Espanish" → "speak Spanish" al 100 %; Parakeet 0.6B es el único que delata "tink/tird", pero pierde "six books" dos veces. Se quedó Moonshine base: el único que delata "espeak" (el error insignia). **Sobrecorrección medida** (errores plantados a propósito que el modelo "arregla" y puntúa BIEN, `tools/asr-bench/planted.txt`, 11 casos): Moonshine base **5/11 = 45 %** (think ×2, third, asked, spanish) · Whisper base/small 4/11 · Parakeet 0.6B 3/11 · Parakeet 110m 2/11. Con 11 casos esas cifras no se distinguen estadísticamente; lo que sí se ve es que cada modelo es ciego a sonidos distintos: Moonshine al `th` (3 de 4), Parakeet y Whisper a la `e` delante de `s` (espeak/Espanish, 2 de 2). Antes de reconocer, `Audio.kt` (`AudioPrep`) quita el DC, recorta el silencio, normaliza el pico y rechaza audio mudo/corto/ruidoso con "No te entendí, repite" en vez de dar 0 %. **Regla que no se negocia:** el reconocedor nunca ve la frase esperada (nada de hotwords ni sesgos); el objetivo solo se usa para puntuar después. |
| IA (Fase 3) | **llama.cpp + Qwen 3 8B Q4** | Qwen es Apache 2.0 sin letra chica. Se descartó Gemma 2/3 porque sus "Gemma Terms of Use" permiten a Google cambiar las condiciones después. |
| Interfaz | Kotlin + Jetpack Compose | Menos capas intermedias con tres motores nativos encima. |
| Progreso | SharedPreferences | Suficiente; nada sale del teléfono. |

Las voces y el motor sherpa-onnx **se descargan solos al compilar** — hay tareas
Gradle (`fetchSherpaAar`, `fetchVoices`, `fetchAsr`) que los bajan la primera
vez y los meten en `assets/`. No están en el repositorio.

El modelo de 8B (~5 GB) **no cabe dentro de un APK**: tiene que vivir como
archivo aparte en el teléfono, copiado una vez por USB.

**Esquema del contenido.** `curriculum.json`: niveles → unidades → lecciones
→ ejercicios (`listen`, `translate`, `build`, `type`, `speak`). `drills.json`:
lista de `drills` con `text`, `sound`, `focus`, `tip`. Los `speak` y los
drills llevan `sound` obligatorio (regla dura 5). `bench.py` lee el sonido de
cada frase de esos mismos JSON: no hay lista duplicada en ningún lado.

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

### Pendientes con condición (no se olvidan)

- **Reafinar los umbrales de `AudioPrep` cuando el corpus llegue a ~100
  grabaciones.** Hoy (2026-09-12) son 26, de un solo día, un solo cuarto, una
  sola voz; los umbrales están afinados a eso. La app guarda las últimas 200
  en el teléfono. Procedimiento: revisar `adb devices` → `adb pull
  /sdcard/Android/data/com.ferolabs.hablo/files/grabaciones tools/asr-bench/corpus`
  (acumula: lo que ya está en el PC no se pierde) → `python tools/asr-bench/bench.py --only quantized-2026`
  → leer la sección **PUERTAS** (rechazos por puerta y mín/mediana/máx de
  pico, rmsVoz, voz y SNR de las que pasaron) → mover las constantes en
  `Audio.kt` **y** en `bench.py` para que las buenas pasen con margen y las
  rechazadas sean solo las que de verdad no se oyen. Revisar también en los
  `.txt` cuántas salieron `NO_OIDO NOTHING` con medidas buenas: eso es el
  modelo fallando, no la puerta.
- **Medir la sobrecorrección con más de 11 casos y decidir el jurado por
  sonido.** Una sesión de errores a propósito por fenómeno (5 grabaciones
  cada uno: `sh`/`ch`, `th`, `-ed` como sílaba, `e` delante de `s`, `v`/`b`,
  `h` muda, consonante final), anotarlas en `tools/asr-bench/planted.txt` y
  correr `bench.py --only quantized-2026 parakeet-tdt-0.6b whisper-small`:
  la sección **JURADOS** compara las políticas solas.

  **Criterio de Fero (2026-09-12), vale para todo lo de puntaje:** decir
  "bien" cuando se dijo mal es peor que decir "mal" cuando se dijo bien. El
  falso "bien" deja el error puesto para siempre; el falso "mal" solo hace
  repetir. Si hay que elegir, que castigue de más. Por eso un jurado toma,
  palabra por palabra, el veredicto **más duro** de sus miembros.

  **Diseño elegido para evaluar: jurado por sonido, no simétrico ni
  permanente.** Cada drill lleva el sonido que entrena; el segundo modelo
  corre **solo** en los drills donde Moonshine es ciego, se carga al entrar
  a ese drill y se suelta al salir de la pantalla (no convive con los 5 GB
  del modelo de IA de la Fase 3). Simulado sobre las 26 grabaciones de hoy:

  | Política | arregló (de 11) | promedio en las buenas |
  |---|---|---|
  | Moonshine solo | 5 | 61,4 % |
  | Jurado total Moonshine+Parakeet 0.6B en todo | 2 | 54,9 % (baja ship, six ×2, very, good ×2, world sin ganar honestidad) |
  | Por sonido: `th` → +Parakeet 0.6B | 2 | 61,4 % (cero daño colateral) |
  | Por sonido: `th` → +Parakeet, `ed` → +Whisper small | 1 | 61,4 % |
  | Por sonido: `th` → +Parakeet **110m** | 4 | 61,4 % |

  El jurado por sonido da toda la honestidad del total sin su daño: el
  segundo modelo solo opina donde sabe. **Parakeet 110m no sirve de juez de
  `th`**: oye "tink" como *think* igual que Moonshine (2 de 2) y solo agrega
  "tird"; su 2/11 general venía de `espeak` y `-ed`, donde no opinaría. El
  0.6B es el único que oye la diferencia t/th.

  **Camino alternativo medido el 2026-09-12: puntuar FONEMAS (GOP) en vez de
  palabras.** Idea de Fero: la sobrecorrección existe porque el reconocedor
  tiene modelo de lenguaje; un reconocedor de fonemas no sabe qué es una
  palabra. Se exportaron a ONNX (`tools/asr-bench/phoneme_export.py`) y se
  corrieron sobre el mismo corpus (`phoneme_eval.py`):
  `mrrubino/wav2vec2-large-xlsr-53-l2-arctic-phoneme` (Apache 2.0, entrenado
  con transcripciones de lo que DE VERDAD pronuncian hablantes no nativos,
  incluidos hispanohablantes; 40 símbolos IPA) y
  `vitouphy/wav2vec2-xls-r-300m-timit-phoneme` (nativos, TIMIT). Ambos 315 M
  parámetros: 1,26 GB fp32 → **317 MB int8**; **~0,5 s por frase en el PC**
  (el S25 corre Moonshine a 1,2–1,8× el PC → ~0,6–1 s; falta medirlo en el
  teléfono). Resultado L2-ARCTIC: atrapa **9 de 11** errores plantados en el
  fonema exacto (θ→t **4 de 4**, ʃ→tʃ 2/2, -ed 3/3 como palabra; falla
  "Espanish" como todos), es decir la honestidad del jurado con **un solo
  modelo para todos los sonidos**. El costo: marca algún fonema en **66 de 88
  palabras bien dichas** (v→b, ɪ→i, ð→d, -d y -z finales caídos, h de home):
  casi todo es acento real que Moonshine tapa, pero en crudo sería una
  pantalla llena de rojo. Hallazgo clave: **los dos modelos de fonemas oyen
  una vocal antes de "speak" en la toma que Fero dio por buena** (ɛspik /
  ɪspik) — el enfoque por palabras estaba ocultando el error insignia.
  Expected phones: CMUdict (`pip install cmudict`) + tabla ARPAbet→IPA en
  `phoneme_eval.py`; para la app se precalcularían al construir y se
  guardarían en el JSON. Para correrlo en el teléfono hace falta la API Java
  de ONNX Runtime (`com.microsoft.onnxruntime:onnxruntime-android:1.28.0`,
  45 MB, dependencia normal de Gradle; el AAR de sherpa-onnx trae el motor
  1.28.2 sin API Java; misma versión de API, se comparte el `.so` con
  `pickFirst`). **Diseño propuesto si se adopta:** porcentaje por palabras
  (Moonshine) para "¿se entendió?" + veredicto por fonema **solo sobre el
  sonido que entrena el drill** (la etiqueta `sound`), con puntaje por
  posterior (GOP) para que lo dudoso salga DUDOSO y no MAL. Eso haría
  innecesario el jurado. Decisión pendiente de la sesión de ~35 errores
  plantados (`tools/asr-bench/session-drills.json`, cada frase dos tomas:
  buena y mala): mide sobrecorrección y **falsas alarmas sobre el fonema
  objetivo** en tomas buenas, que es lo que decide si es usable.

  **Memoria (pensando en la Fase 3):** Parakeet 0.6B int8 ocupa ~660 MB en
  disco y ~1 GB cargado; Qwen3 8B Q4 ~5 GB; el teléfono tiene 12 GB. No
  pueden convivir cargados. Regla: el juez se carga al entrar a un drill de su
  sonido y se suelta al salir de la pantalla de pronunciación; el modelo de IA
  se suelta al salir de la conversación. Nunca dos pesados a la vez. Muestra chica (th: 4 casos de 2
  grabaciones; -ed: 3 de una). **Regla de decisión** para no volver a
  discutirlo: con el corpus de ~35 casos, si el segundo modelo atrapa ≥4 de 5
  errores plantados de su sonido y no baja el promedio de las buenas de ese
  sonido más de 5 puntos, se construye para ese sonido. Costo estimado:
  +660 MB de APK por Parakeet (+375 por Whisper small; el peso no importa,
  Fero lo dijo; alternativa: ponerlos al lado de la app como el modelo de IA),
  ~0,5 s más en esos drills, ~150 líneas (etiqueta `sound` en `Drill`, segundo
  reconocedor perezoso en `Listener`, combinación "más duro" en
  `scorePronunciation`, y la pantalla muestra lo que oyó cada uno).

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
