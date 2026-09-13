# Hablo — reglas del proyecto

App Android para aprender inglés que funciona **sin internet** (única
excepción, opcional y apagada por defecto: la conversación con Gemini), hecha
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
- **No trabajar a ciegas.** Regla de Fero (2026-09-12), textual: *"de aquí en
  adelante no vamos a trabajar a ciegas, consultas información en la web, lees
  cosas similares y las aplicamos con tus correcciones, tenemos que agilizar
  esto"*. En la práctica: antes de elegir un modelo, un umbral o una forma de
  corregir, buscar si alguien ya lo midió, y citar la cifra en el commit.
  Media hora de lectura ahorra una tarde de banco de pruebas. El ejemplo que
  originó la regla: el detector de fonemas marcaba 66 de 88 palabras bien
  dichas y se iba a diagnosticar acento; un paper (abajo, "GOP") mostró que
  es la línea base conocida de GOP sin umbral (precisión 0,165).
- Compila con `gradlew` e instala en su **Samsung Galaxy S25 Ultra** por USB
  (`adb`). El emulador tiene el disco lleno y no sirve para el micrófono.

---

## Reglas duras — no romper

1. **Internet solo para la conversación, y solo si Fero la enciende.** Hasta
   la 0.8 el manifiesto no declaraba `INTERNET` a propósito. El 2026-09-12
   Fero decidió probar Gemini para la conversación ("probemos con gemini")
   porque el 8B local corregía errores del dictado como si fueran del alumno;
   el 2026-09-13, tras ver la calidad, **pagó Claude API** y ese es ahora el
   motor por defecto (`ClaudeLlm.kt`), con Gemini de respaldo gratuito y Qwen
   de respaldo sin internet. La clave está en
   `C:\Users\ferna\Hablo-modelos\claude.key` y en
   `files/modelos/claude.key` del teléfono.
   Lo que sigue siendo regla: (a) el permiso lo usa **únicamente**
   `CloudLlm.kt`; nada más en la app abre una conexión, ni para descargar
   modelos ni para telemetría; (b) sale **solo texto** (prompt del escenario +
   la charla ya transcrita); **nunca audio**; dictado y voces siguen en el
   teléfono; (c) interruptor en Ajustes **apagado por defecto** y muerto sin
   clave; (d) la clave vive en `files/modelos/gemini.key` en el teléfono y en
   `C:\Users\ferna\Hablo-modelos\gemini.key` en el PC: **jamás en el código,
   en el repo, en un commit ni en un log**. Lecciones, pronunciación y
   progreso no tocan la red.
2. **No actualizar el Android Gradle Plugin.** Android Studio va a ofrecer el
   "AGP Upgrade Assistant" cada vez. Decir que no. Las versiones están fijadas
   porque se sabe que funcionan juntas:
   - Gradle 8.11.1 · AGP 8.7.3 · Kotlin 2.0.21 · Compose BOM 2024.10.01 ·
     **sherpa-onnx 1.13.4 + onnxruntime-android 1.27.0, y van juntos**: los
     símbolos de `libonnxruntime.so` llevan etiqueta de versión
     (`VERS_1.27.0`) y Android no deja que una JNI use otra versión. sherpa
     1.13.4 está compilado contra ORT 1.27.0, que existe en Maven; 1.13.5–1.13.8
     usan 1.27.1/1.28.2, que no. Para subir sherpa-onnx hay que mirar
     `build-android-arm64-v8a.sh` de esa versión y que esa ORT esté en Maven;
     si no, las voces o el modelo de fonemas mueren con "cannot locate symbol
     OrtGetApiBase". `fetchSherpaAar` le quita al AAR su copia del `.so`;
     borrar el AAR viejo de `app/libs/` al cambiar de versión.
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
| Voz → texto | **Moonshine base v2** (`moonshine-base-en-quantized-2026-02-27`, 2 archivos `.ort`, 140 MB) vía sherpa-onnx **1.13.4** | Elegido el 2026-09-12 con `tools/asr-bench` sobre 26 grabaciones reales de Fero. Tiny devolvía texto vacío en 4 de 26 con audio bueno y confundía palabras fáciles ("seatsbooks", "Can't you"); base no, y mantiene la honestidad (delata "chip", "espeak", "wok-ed"). Whisper base.en quedó descartado: **corrige** "espeak Espanish" a "speak Spanish" al 100 %. Parakeet 110m es el más honesto pero castiga también lo bien dicho. También se probaron los grandes (Fero dijo que el peso no importa): **Whisper small.en** (74 % promedio, el mejor adivinando) y **Parakeet 0.6B v2** (70 %) corrigen los dos "espeak Espanish" → "speak Spanish" al 100 %; Parakeet 0.6B es el único que delata "tink/tird", pero pierde "six books" dos veces. Se quedó Moonshine base: el único que delata "espeak" (el error insignia). **Sobrecorrección medida** (errores plantados a propósito que el modelo "arregla" y puntúa BIEN, `tools/asr-bench/planted.txt`, 11 casos): Moonshine base **5/11 = 45 %** (think ×2, third, asked, spanish) · Whisper base/small 4/11 · Parakeet 0.6B 3/11 · Parakeet 110m 2/11. Con 11 casos esas cifras no se distinguen estadísticamente; lo que sí se ve es que cada modelo es ciego a sonidos distintos: Moonshine al `th` (3 de 4), Parakeet y Whisper a la `e` delante de `s` (espeak/Espanish, 2 de 2). Antes de reconocer, `Audio.kt` (`AudioPrep`) quita el DC, recorta el silencio, normaliza el pico y rechaza audio mudo/corto/ruidoso con "No te entendí, repite" en vez de dar 0 %. **Regla que no se negocia:** el reconocedor nunca ve la frase esperada (nada de hotwords ni sesgos); el objetivo solo se usa para puntuar después. |
| Pronunciación por fonema (GOP) | **wav2vec2-large-xlsr-53-l2-arctic-phoneme** int8 (317 MB) vía **onnxruntime-android 1.27.0**; `Gop.kt` + `PhonemeScorer.kt`; fonemas esperados de **CMUdict** (`assets/gop/cmudict.dict`); umbrales en `assets/gop/thresholds.json` | Es lo que hacen las apps comerciales (Azure, Speechace, ELSA): se conoce la frase, se alinean sus fonemas y se puntúa la confianza de cada uno; el reconocedor de palabras se queda solo para "¿se entendió?". Entrenado con habla no nativa anotada tal como se pronunció (incluye hispanohablantes). En el S25: carga 0,6 s, evalúa en 200–300 ms. Se carga al entrar a la pantalla y se suelta al salir. **Solo sonidos con umbral calibrado muestran veredicto**: `sh` y `h` en rojo/amarillo (precisión del rojo ≥ 66 %); `th`, `ed`, `es` **solo en amarillo** (`calibrate.py --yellow-only`: precisión entre 33 y 66 %, el rojo nunca dispara); `v`, `final` y `rl` sin veredicto (por debajo del 33 %) y la pantalla lo dice. Para mover un sonido de categoría: más corpus etiquetado → `phoneme_eval.py` → `calibrate.py --sounds sh h --yellow-only th ed es` → `thresholds.json`. **Etiqueta del alumno:** después de cada intento la app pregunta "¿Te sonó igual?" (Igual / Distinto / No sé) y lo anexa como `etiqueta:` al `.txt` de la grabación (`Listener.labelLastRecording`); solo se usa al recalibrar, nunca en tiempo real. Vale sobre todo para lo audible (h caída, sílaba de más, e+s); para ship/sheep y v/b el oído del alumno es el problema. El modelo lo exporta `phoneme_export.py`; `fetchGop` lo copia a assets (si falta, la app compila sin él y lo dice). |
| IA por internet (Fase 3, la que se usa) | **Claude API** (`ClaudeLlm.kt`, Messages API por REST con streaming SSE, `HttpURLConnection` + `org.json`, sin SDK) | Elegido y pagado por Fero el 2026-09-13. Selector en Ajustes: `claude-sonnet-5` (por defecto) y `claude-haiku-4-5`. Sin SDK de Java a propósito: no tocar las versiones fijadas (regla dura 2) ni sumar MB a un APK que ya lleva tres motores nativos. `thinking: {"type":"disabled"}` en Sonnet (en una charla corta pesa más el segundo de espera); el prompt de sistema va como bloque con `cache_control` (todavía no muerde: hace falta pasar de 1.024 tokens en Sonnet y 4.096 en Haiku). **Medido el 2026-09-13 con las frases reales del dictado de Fero:** Sonnet 5 acierta 5/5 (no corrige lo mal oído, sí la gramática), primera palabra en 0,8-1,0 s, $0,0023/turno ≈ **$2,06 al mes** a 30 turnos diarios; Haiku 4.5 igual de acertado, primera palabra 0,7-1,6 s, $0,0008/turno ≈ **$0,75 al mes**. Precios oficiales verificados: Haiku 4.5 $1/$5 por millón, Sonnet 5 $2/$10, Opus 5 $5/$25. Anthropic no entrena con lo enviado por API. |
| Voz en español | **Piper `es_MX-claude-high-int8`** (17 MB, voz "es" en `assets/tts/`) | Fero: "el español es importante, en ocasiones hay cosas que no entiendo". `Speaker` carga un SEGUNDO modelo a la vez (`piperEs`) en vez de intercambiarlo: en una charla se alterna inglés y español frase a frase y recargar costaría casi un segundo por cambio. `speakSpanishQueued()` lee la línea `CORRECCIÓN:` detrás de la respuesta en inglés. Se le ofrecieron mexicana, española, argentina y Kokoro "Dora"; eligió la mexicana. |
| IA por internet (respaldo gratis) | **Gemini API** (`CloudLlm.kt`, REST `streamGenerateContent?alt=sse` con `HttpURLConnection` + `org.json`, sin SDK) | Elegido por Fero el 2026-09-12 tras rechazar la calidad del 8B local. Escalera de modelos por cuota (ver "Estado y plan"). Solo texto; el interruptor de Ajustes lo enciende; la clave va por USB. Regla dura 1. |
| IA en el teléfono (Fase 3) | **llama.cpp 0.4.0 compilado dentro de la app** (`app/src/main/cpp/`: `CMakeLists.txt` + `llm_jni.cpp`, puente JNI propio con solo la API C; `Llm.kt`) + **Qwen3 8B Q4_K_M** (`Qwen3-8B-Q4_K_M.gguf`, 5,03 GB) | Qwen es Apache 2.0 sin letra chica. Se descartó Gemma 2/3 porque sus "Gemma Terms of Use" permiten a Google cambiar las condiciones después. El código de llama.cpp lo baja `fetchLlamaCpp` (fijado a una versión) y lo compila el NDK 27.2 con CMake 3.22.1, que AGP descarga por versión (no es el asistente). Solo arm64 (`abiFilters`): el APK bajó ~90 MB al soltar x86/armv7. El modelo vive en `Android/data/com.ferolabs.hablo/files/modelos/` (sin permisos; se llena con `adb push`; **desinstalar la app lo borra**). **Medido en el S25 (2026-09-12): carga en 9 s, prompt a 27 t/s, respuesta a 11,0 t/s** con 6 hilos, contexto 2048, mmap. Se queda el 8B. Laboratorio en Ajustes: cargar, probar (la respuesta la lee la profesora con Piper), liberar. Sampler: min_p 0.05, top_p 0.9, temp 0.6; Qwen3 con `/no_think` en el system prompt. |
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
- **Heredocs de Bash comen las barras invertidas** (`\\n` llega como `\n`,
  `\d` se rompe): los scripts Python de edición se escriben con la
  herramienta Write al scratchpad y se ejecutan desde ahí. Un heredoc solo
  sirve si el script no tiene ni una barra invertida.
- `Llm.stop()` llamaba a `nativeStop()` sin comprobar `loaded`: sin el
  modelo cargado la librería nativa no existe y es `UnsatisfiedLinkError`
  (pasaba al salir de una conversación por internet). Cualquier `external
  fun` de `Llm` va detrás de `if (loaded)`.

---

## Estado y plan

**Versión actual: 0.9** (conversación por internet; el motor por defecto pasó a
Claude API el 2026-09-13, ver "Sesión del 2026-09-13" más abajo).
**0.8** funcionando en el teléfono de Fero (2026-09-12):
evaluación por fonema (GOP) — `sh` y `h` en rojo/amarillo, `th`/`ed`/`es`
solo amarillo — mapa personal de sonidos, etiqueta del alumno tras cada
intento, y 40 drills (5 por sonido, `drills.json`). Fero, al oírse: "detecté
que hablo mal y por qué entiende lo que entiende; no hay desfase entre lo que
pronuncio y lo que entiende".

**Decisión de Fero (2026-09-12, misma noche, dos pasos).** Primero: "la
conversación sigue sin internet; si no me gusta, pagamos". Se hizo lo mejor
posible en local (fluidez, auto-corte, prompt caritativo) y al probarlo dijo
"no me gusta": en el log, Qwen 8B corregía en inglés lo que el dictado había
oído mal ("As model please", "Mail and sewer", "What nine is it") como si
fueran errores del alumno. Segundo: **"probemos con gemini"** → 0.9 trae la
conversación por internet con Gemini (`CloudLlm.kt`), interruptor en
Ajustes apagado por defecto, solo texto, Qwen queda de respaldo (si Gemini
no responde a mitad de charla se sigue en el teléfono sin perder el hilo).
Contexto verificado antes: las apps pagas (ELSA, Speak, Praktika) son fluidas
porque corren en servidores con modelos grandes; ni la suscripción de
claude.ai ni Google AI Plus incluyen API; Claude API ≈ $0,70 (Haiku 4.5) a
$3–4 (Opus 5) al mes para 20 turnos diarios y no entrena con lo enviado;
Gemini API tiene nivel gratuito sin tarjeta pero usa los datos para mejorar
productos (el pago no).

**Gemini, medido el 2026-09-12 (PC, `tools/asr-bench/gemini_bench.py`, con
las frases reales del dictado de Fero):** `gemini-3.8-flash` sin razonar
(`thinkingBudget: 0`) acertó 5/5 — no corrigió 'As model please', 'thoughts
with Buddha' ni 'What nine is it', y sí corrigió 'I have 25 years' y 'My
sister is doctor, she work' con la línea `CORRECCIÓN:` en español — en
1,0–2,5 s por turno; `gemini-3.5-flash-lite` 6/7 (una corrección falsa) en
0,6–1,1 s; `gemma-4-31b-it` piensa en voz alta 17–30 s por turno
(descartado); `gemini-2.5-flash` ya no existe para cuentas nuevas (404).
**Cuotas del nivel gratuito** (leídas en aistudio.google.com/rate-limit con
la cuenta de Fero): cada Flash 3.x (3.8, 3.7, 3.6, 3.5, 3) tiene **5
peticiones/minuto y 20 al día**, cada uno con su propia cuota; Flash-Lite
(3.5 y 3.1) 15/minuto y 500/día; Gemma 4 30/minuto y 14.400/día. Por eso
`CloudLlm.LADDER` es una **escalera**: 3.8 → 3.7 → 3.6 → 3.5 → 3 →
3.5-lite; ante 429 (cuota), 404 o 503 (saturado) se sube un peldaño en la
misma charla (≈100 turnos/día con los buenos y 500 más con Lite). Si un día
se pasa al nivel pago: desaparece el tope diario, Flash cuesta centavos al
mes y Google deja de usar el texto para entrenar. Errores que sí se ven:
un 400/401 (clave mala) se muestra tal cual; un 503 en todos los peldaños
también. `Probar la conexión` en Ajustes lista los modelos que acepta la
clave y avisa si falta alguno de la escalera.

**Fase 3, estado (2026-09-12): la conversación ya existe** (`ScreenConversation.kt`,
`assets/content/scenarios.json` con 5 escenarios A1, validados en
`checkContent`). Ciclo: la profesora abre hablando (Piper) → el alumno habla
(**Parakeet 0.6B** transcribe: al conversar se quiere el que mejor adivina,
no el honesto; en las 35 tomas buenas de la sesión sacó 20 perfectas contra
15 de Moonshine; vive al lado de la app en `files/modelos/parakeet/`, se
carga al entrar y se suelta al salir; si falta, Moonshine) o escribe → Qwen
responde en su papel y corrige en español en una línea `CORRECCIÓN:` (se
extrae esté donde esté; el resto se lee en voz alta, **frase por frase
mientras se genera** con `Speaker.speakQueued`) → Piper. La grabación se
**corta sola** al callarse (1,1 s de silencio tras haber hablado; piso de
ruido medido en los primeros 0,4 s; `AUTO_STOP_*` en `Listener`).
Memoria: al entrar se suelta el modelo de fonemas y se carga Qwen; Moonshine,
Parakeet y Piper conviven con él (~7 GB en total).

**Cómo se mantiene fluido (medido):** el prompt de sistema (~400 tokens con
la apertura) se procesa mientras la profesora saluda; después cada turno son
10–20 tokens nuevos (0,4–0,5 s) porque la memoria del modelo (KV cache) es
la verdad y `Llm.kt` solo le da el texto que la plantilla agrega
(`formattedSoFar` + `nativeFeed`); la respuesta sale a 10–11 t/s. **Trampas
ya pisadas:** (1) sin penalización de repetición el modelo copiaba su frase
anterior ocho turnos seguidos → `llama_sampler_init_penalties(…, 128, 1.18)`;
(2) respuestas vacías (1–2 tokens) que, al entrar al historial, enseñaban al
modelo a callarse → "vacía" = sin letras ni números, hasta 2 reintentos con
otra semilla y luego una frase de recuperación que también entra a la
memoria; (3) la corrección iba primero y el separador dejaba la parte hablada
vacía → se extrae la línea `CORRECCIÓN:` esté donde esté; (4) `<think>` en
Qwen3 NO es token de control: sale como texto y se limpia con `stripThinking`;
(5) el historial debe guardar la respuesta EXACTA generada (sin recortar):
si no, el prefijo no coincide y se reprocesa todo. Modo sin razonamiento
(`/no_think`): razonar costaría 5–15 s por turno.

Lo que sigue: probar la fluidez con auto-corte; sumar escenarios hacia ~50;
opcional una voz Piper en español (`es_MX`) para leer la corrección. Investigado
antes de construir: el
camino es el ejemplo oficial `examples/llama.android` de llama.cpp (módulo
nativo compilado desde fuente con CMake + NDK dentro de la app, puente JNI
propio; nada de AARs de terceros). Velocidad esperable en el 8 Elite:
~10–13 tokens/s para 8B por CPU (Qualcomm reporta 12,9 t/s con su runtime
para un 8B w4a16); hay backend OpenCL para Adreno 830 para después. Memoria:
Qwen3 8B Q4_K_M ≈ 4,9 GB + contexto ≈ 5,3 GB: cabe en 12 GB solo con
Moonshine y el modelo de fonemas descargados en esa pantalla. Plan B si va
lento: Qwen3 4B (~2,5 GB). Primer paso: NDK + CMake por línea de comandos,
puente mínimo (cargar, generar con streaming) y una pantalla de prueba que
mida tokens/s reales en el teléfono antes de escribir escenarios.

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

### Sesión del 2026-09-13: lo que dijeron los datos de uso

**Cómo usa la app (medido, no supuesto).** Sesión de 83 minutos (00:24-01:47):
138 grabaciones, 96 de ejercicios y 42 de conversación, repitiendo **solo 15
frases distintas** ("I'm twenty-five years old" 16 veces). 7 de las 8 lecciones
hechas (66-100 puntos), 440 XP. Mapa de sonidos: `sh` 33 intentos con 24
"dudoso", `th` 10 con 9. Diagnóstico: **se quedó sin contenido** (40 drills, 8
lecciones) y el amarillo perdió valor de tanto sonar. Sus palabras: "las otras
secciones son repetitivas y fáciles... falta una parte de explicar".

**Regla nueva: una banda de color solo se muestra si su precisión medida llega
a 0,50** (`PhonemeScorer.MIN_PRECISION`). Con `thresholds.json` actual eso
apaga el amarillo de `sh` (0,33), `h` (0,33) y `th` (0,38) —dos de cada tres
avisos eran falsa alarma— y deja el rojo de `sh` (0,86) y `h` (0,83) y el
amarillo de `-ed` (1,00) y `es` (0,57). `th` se queda sin veredicto y la
pantalla lo dice. No contradice el criterio de Fero ("prefiero que castigue de
más"): el rojo, que es el que castiga, sigue con recall 0,86-1,00. Corrige el
error de leer el amarillo de `-ed` como si estuviera entre 33 % y 66 %.

**El prompt de corrección va aparte y con ejemplo, no como una regla más.**
Fero preguntó por qué Haiku no corregía hablando si en el chat normal sí. No
era el modelo: con la regla enterrada en la lista, **Haiku 4.5 decía la frase
correcta en voz alta 0 de 3 veces; con el protocolo de tres partes y dos
ejemplos, 3 de 3** (Sonnet 5: 3/3 con las dos versiones; ninguno inventó
correcciones en los 3 turnos mal oídos). Medido con
`tools/asr-bench/claude_bench.py` y el A/B del scratchpad. Si se toca
`buildSystemPrompt`, no volver a enterrar ese protocolo.

**El primer mensaje de `messages` SÍ puede ser `assistant`** en la API de
Anthropic (verificado dos veces el 2026-09-13, HTTP 200): la apertura de la
profesora puede ir en el historial. No "arreglar" lo que no está roto.

**El tamaño real del contenido (cifras citadas, no estimadas a ojo).** A1→B2:
**4.666 cabeceras de vocabulario** (Capel 2010, *English Profile Journal* 1(1);
son cabeceras, no sentidos: el trabajo real es 2-3 veces mayor) y **550 horas
guiadas** (LanguageCert 95+95+180+180; Cambridge da 500-600 acumuladas, coincide).
A 20 minutos diarios son 4,5 años; a 45, dos —y son horas con profesor, o sea
un piso optimista—. Lecciones: ~205 **estimación propia**, no cifra publicada.
Hoy hay 8: el 4 %. Orden de gramática: no inventarlo, está tomado de sílabos
reales (Cambridge Empower para A1-A2, English File Intermediate para B1; el de
Empower B1 es distinto y no sirve de segunda fuente).

**Actividades que se van a construir porque están medidas** (verificadas una a
una; las que no aguantaron la verificación se descartaron):
- **Pares mínimos con las cuatro voces** (pantalla "Oído"): percepción g = 0,92
  intra-sujeto y g = 0,98 en la medida diferida (Uchihara, Karas & Thomson 2025,
  *SSLA* 47(3), 79 estudios); identificar la palabra (g = 0,95) funciona mucho
  mejor que "¿son iguales?" (g = 0,57); la variabilidad de hablantes es un
  moderador, y la app ya tiene cuatro voces. Transferencia al habla d = 0,54
  (Sakai & Moorman 2018) **pero** la correlación oído→boca no fue significativa
  (r = 0,31): hay que decirlo en pantalla y cerrar cada bloque hablando. Cubre
  justo lo que GOP no puede juzgar (ship/sheep, v/b, rl, consonante final).
- **Recuperación CON retroalimentación**: g = 0,73 con corrección contra 0,39
  sin ella (Rowland 2014, *Psychological Bulletin* 140, 159 comparaciones). La
  corrección no es adorno: es la mitad del efecto.
- **Instrucción explícita de gramática**: d = 0,96 (Norris & Ortega 2000). La
  sección de teoría en español no es un capricho, es de lo mejor medido.
- **Práctica espaciada** ≈ el doble que amontonada en entrenamiento de sonidos.
- Ojo con dos cifras que circulan y son falsas: "10-20 palabras nuevas por
  sesión" no tiene respaldo (lo que está medido es que el número de encuentros
  predice el aprendizaje, r = 0,34, Uchihara, Webb & Yanagisawa 2019), y la
  dirección de traducción que más rinde en principiantes es **inglés→español**,
  no al revés (Terai, Yamashita & Pasich 2021, *SSLA* 43(5)).

**Plan acordado (etapas, en orden de valor por trabajo).** 1) *Que deje de ser
previsible* — hecho el 13-09. 2) *La profesora que se acuerda*: contextos
separados con hilo propio, "Hablar de todo", ficha de memoria en
`files/memoria/perfil.json` (datos del alumno, errores con contador, resumen
por escenario; topes duros de 12 datos / 15 errores / 60 palabras / 400 tokens;
los errores se registran gratis desde la línea `CORRECCIÓN:` y el resumen con
UNA llamada a Haiku al cerrar la charla, ~$0,50/mes; si el JSON no parsea se
conserva la ficha vieja). 3) *Teoría*: empezar con 6 fichas, no 20, y medir
cuáles abre. 4) *Repaso de hoy* (Leitner 1/3/7/16/35 + dificultad graduada del
mismo ítem: elegir → armar → escribir → oír y escribir → decir). 5) *Oído*.
6) *Más tipos de ejercicio* (+ ids por ejercicio y `answer` como texto; hacerlo
con 55 ejercicios cuesta medio día, con 200 lecciones es una migración: va
antes que el mazo). 7) *Contenido A1→B2*. En paralelo, trabajo de PC: recalibrar
umbrales **a nivel de frase** con el corpus nuevo y afinar las puertas de audio.

**Deudas conocidas que van a morder si se ignoran:** no hay un solo test (todo
lo frágil es Kotlin puro: parseo de contenido, Leitner, truncado de la ficha,
`splitReply`; un source set `testImplementation` no toca AGP); `Course.load` corre
en el hilo principal antes de pintar; `Speaker` mantiene **una sola voz inglesa
cargada** (`ensureVoiceLoaded` libera y recarga), así que "una profesora al azar
por ítem" en la pantalla de Oído serían 12 recargas por bloque: hay que
pre-sintetizar o rotar por bloque.

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

  **VEREDICTO de Fero (2026-09-12): gana GOP con un solo modelo. El jurado
  por sonido y Parakeet 110m quedan descartados; no hay que medirlos más.**
  Razones: misma detección (9 de 11) con un modelo en vez de dos; memoria
  (Moonshine 140 MB + GOP 317 MB ≈ 460 MB, contra ≥ 740 MB del jurado, y en
  la Fase 3 van 5 GB de Qwen encima); sin tabla de ruteo sonido→modelo que
  se desincronice con 160 lecciones. Solo se vuelve al jurado si GOP no pasa
  la barra del 66 % (abajo).

  **Dos cifras publicadas que gobiernan el diseño:**
  - Interspeech 2025, *Enhancing GOP in CTC-Based Mispronunciation Detection
    with Phonological Knowledge* (arXiv 2506.02080): la línea base de GOP con
    alineación forzada sobre MPC da **recall 0,929 y precisión 0,165**: el
    83,5 % de lo que marca como mal dicho estaba bien ("overclassification of
    correct pronunciations as mispronunciations"). El 66/88 medido aquí es
    ese comportamiento, no el acento de Fero. Un `argmax` + alineación no es
    GOP: GOP es una **puntuación de confianza contra un umbral**, y el umbral
    se elige "selecting the GOP percentile that maximized MCC".
  - Silpachai et al. 2024, *Language Learning & Technology*, "Corrective
    feedback accuracy and pronunciation improvement: feedback that is 'good
    enough'": con corrección exacta al **66 %** los alumnos mejoraron igual que
    con 100 %; al 33 % mejoraron significativamente menos. **Barra dura: de
    cada tres correcciones mostradas, al menos dos verdaderas. Por debajo del
    33 % es peor que no corregir.** La salida cruda del detector (~14 %) está
    por debajo de eso: no se muestra nunca en crudo.

  **Plan de trabajo GOP, en orden:** (1) puntuación por fonema = número, no
  binario: log-posterior del fonema esperado en sus tramas alineadas por
  forzado CTC, menos el máximo sobre fonemas (documentar la variante);
  (2) umbral = percentil de GOP que maximiza MCC sobre el corpus, reportando
  percentil, MCC y matriz de confusión; (3) el veredicto se limita al sonido
  del ejercicio (etiqueta `sound`): en un drill de `th` solo θ/ð pueden
  marcarse, lo demás se calcula y no se muestra; (4) reportar la precisión de
  lo que se MOSTRARÍA: "de N correcciones mostradas, M eran errores reales →
  X % (barra ≥ 66 %)"; (5) calibrar con una partición y verificar con otra
  para no sobreajustar el umbral a 26 grabaciones. Partición: calibración =
  corpus del 12-09 (26) + frases 1–3 de cada sonido de la sesión; verificación
  = frases 4–5 de cada sonido (14 frases × 2 tomas, 14 errores plantados que
  el umbral nunca vio).

  **Cómo se le muestra a Fero (decidido y construido en 0.8):** la tarjeta
  "Sonido: …" con cada palabra del sonido ✓/~/✗ y una frase de resumen es lo
  primero; "Se entendió: X %" (palabras) debajo; la transcripción cruda solo
  bajo "Ver lo que oyó el dictado →". Arriba, "Hoy: <sonido> falló N de M".
  Dos umbrales (`calibrate.py`): rojo = MCC máximo; amarillo = el más
  permisivo con precisión ≥ 33 %. Castigar de más, pero en amarillo. Mapa
  personal en el inicio ("Tu mapa de sonidos", `Store.soundStats`). La vocal
  antes de "speak" sigue como **hipótesis** hasta calibrar `es`.

  Medición previa (2026-09-12) que motivó todo esto: puntuar FONEMAS (GOP) en
  vez de palabras. Idea de Fero: la sobrecorrección existe porque el reconocedor
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
  (Nota: ese 66 de 88 era el binario del argmax, no GOP; ver el veredicto
  arriba.) Expected phones: CMUdict (`pip install cmudict`) + tabla
  ARPAbet→IPA en `phoneme_eval.py`; para la app se precalcularían al
  construir y se guardarían en el JSON. Para correrlo en el teléfono hace falta la API Java
  de ONNX Runtime (`com.microsoft.onnxruntime:onnxruntime-android:1.28.0`,
  45 MB, dependencia normal de Gradle; el AAR de sherpa-onnx trae el motor
  1.28.2 sin API Java; misma versión de API, se comparte el `.so` con
  `pickFirst`). Diseño: porcentaje por palabras (Moonshine) para "¿se entendió?" +
  veredicto GOP solo sobre el sonido del drill. La sesión de 35 frases
  (`tools/asr-bench/session-drills.json`, dos tomas por frase) calibra y
  verifica el umbral.

  **Resultados de la sesión de 35 frases (2026-09-12, 70 tomas + repeticiones;
  etiquetas con `session_labels.py`; partición: calibración = corpus 12-09 +
  frases 1-3, verificación = frases 4-5):**
  - **Palabras (Moonshine base):** de 35 tomas buenas, solo **15** salieron
    con todas las palabras bien (43 %); de 35 errores plantados, **14** los
    "arregló" (40 %: 4 de 5 h caídas, 3 de 5 e+s, tank→thank, Tursday→
    Thursday, bideo→video…). Es el "siento que no me entiende" de Fero,
    medido: falla en las dos direcciones. No se muestra más "Entendí: …"
    como veredicto de pronunciación.
  - **GOP (L2-ARCTIC, umbral por MCC), precisión de lo que se mostraría,
    por sonido.** "Estricta" = toda marca en toma buena es falsa alarma;
    "ajustada" = la marca cuenta como real si ≥ 2 de 3 reconocedores de
    palabras independientes (Moonshine, Parakeet 0.6B, Whisper small)
    tampoco entendieron esa palabra (`label_check.py`; proxy de error real,
    porque las tomas "buenas" de un alumno traen errores de verdad y sin
    fonetista no hay verdad-terreno mejor):

    | sonido | puntaje | mostradas | estricta | ajustada | recall |
    |---|---|---|---|---|---|
    | sh | INS | 7 | 86 % | 100 % | 86 % |
    | h | AF | 6 | 83 % | 83 % | 100 % |
    | ed | AF | 21 | 38 % | 95 % | 100 % |
    | th | AF | 24 | 38 % | 62 % | 100 % |
    | final | AF | 19 | 26 % | 58 % | 100 % |
    | es | INS | 7 | 57 % | 57 % | 57 % |
    | v | AF | 13 | 23 % | 46 % | 60 % |

    Global (todas las sustituciones juntas, GOP-AF): calibración 28 %,
    verificación 44 % con recall 80 %. GOP-FA (la línea base del paper): 24 %.
    Conclusión: **sh y h pasan la barra del 66 % ya; ed pasa si se aceptan las
    etiquetas ajustadas; th, final, v y es quedan entre el 33 % y el 66 %**:
    ni "peor que nada" ni "suficiente". Ahí va la banda amarilla: rojo solo
    con el umbral de alta precisión (percentil que da ≥ 66 % estricta en
    calibración), amarillo con el umbral MCC. Los sonidos que ni así llegan
    se muestran solo en amarillo hasta tener mejor verdad-terreno.
  - Hipótesis de la vocal antes de s: en 3 de 11 tomas buenas el INS supera
    el umbral (3,5 / 4,6 / 5,5). Sigue viva; los reconocedores de palabras no
    sirven de proxy aquí porque son ciegos a ese error.
  - Cómo lo hacen las apps comerciales (Azure Pronunciation Assessment,
    Speechace, ELSA): **evaluación "scripted"**: conocen la frase, alinean a
    la fuerza sus fonemas con el audio y puntúan la confianza de cada uno
    (GOP); agregan a palabra y frase; **nunca muestran "lo que oyeron" como
    texto libre**. Usar la frase esperada para ALINEAR fonemas no es sesgar el
    reconocedor: el puntaje puede ser bajo. La regla "el reconocedor de
    palabras nunca ve la frase esperada" sigue igual para Moonshine.

  **Fonemas cubiertos:** el modelo reconoce los 39 fonemas del inglés y se
  calculan todos en cada frase, pero solo se muestra el sonido del drill. Sin
  categoría todavía: las **vocales** (ɪ/i de ship/sheep — la desviación más
  frecuente de Fero en el corpus —, æ, ʌ, ə), la -s final, w/j. La vocal ɪ/i
  es el siguiente sonido a calibrar (pregunta de Fero, 2026-09-12).

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
  cerrados, ciclo completo de voz. El avatar ya está: Fero generó los cuatro
  retratos (originales en `avatares/`, 2 por profesora: boca cerrada y
  abierta, mismo encuadre); recortados a 768² en
  `res/drawable-nodpi/avatar_<id>[_habla].webp`; `TeacherAvatar` muestra el
  retrato **quieto** y, mientras `speaker.busy`, un halo del color de la
  profesora que late (dos ondas). **El truco de dos cuadros (abrir/cerrar la
  boca) se probó y Fero lo rechazó: "se ve muy falso"; no volver a intentar
  bocas animadas con fotos.** Los cuadros `*_habla` quedan sin usar. Ajustes
  tiene "Mostrar la cara de la profesora" (`Store.showFaces`): apagado, un
  círculo con la inicial y el mismo halo. Se ve en el inicio, al elegir
  profesora, en Ajustes y en el resultado de la lección. Avatares hablantes
  neuronales (LivePortrait, SadTalker) no corren en tiempo real en el
  teléfono; descartados para lo que genera la IA.
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
