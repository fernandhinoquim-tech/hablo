# Hablo — reglas del proyecto

App Android para aprender inglés. Lecciones, pronunciación y progreso van
**sin internet**; la única excepción es la conversación, que sale como texto a
Claude (de pago, el motor por defecto desde el 2026-09-13) o a Gemini si se
elige en Ajustes, y sin clave se queda en el teléfono con Qwen. Hecha
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
   Lo que sigue siendo regla: (a) el permiso lo usan **únicamente**
   `ClaudeLlm.kt` y `CloudLlm.kt`; nada más en la app abre una conexión, ni
   para descargar modelos ni para telemetría; (b) sale **solo texto** (prompt del escenario +
   la charla ya transcrita); **nunca audio**; dictado y voces siguen en el
   teléfono; (c) se elige en Ajustes quién responde
   ("Tu profesora de conversación": Claude, Gemini o la IA del teléfono), y sin
   clave esa opción está muerta; (d) las claves viven en `files/modelos/*.key`
   en el teléfono y en `C:\Users\ferna\Hablo-modelos\*.key` en el PC, con
   `*.key` en `.gitignore`: **jamás en el código, en el repo, en un commit ni
   en un log**. Lecciones, pronunciación y
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
| IA por internet (respaldo gratis) | **Gemini API** (`CloudLlm.kt`, REST `streamGenerateContent?alt=sse` con `HttpURLConnection` + `org.json`, sin SDK) | Elegido por Fero el 2026-09-12 tras rechazar la calidad del 8B local. Escalera de modelos por cuota (ver "Estado y plan"). Solo texto; el interruptor de Ajustes lo enciende; la clave va por USB. Regla dura 1. |
| IA en el teléfono (Fase 3) | **llama.cpp 0.4.0 compilado dentro de la app** (`app/src/main/cpp/`: `CMakeLists.txt` + `llm_jni.cpp`, puente JNI propio con solo la API C; `Llm.kt`) + **Qwen3 8B Q4_K_M** (`Qwen3-8B-Q4_K_M.gguf`, 5,03 GB) | Qwen es Apache 2.0 sin letra chica. Se descartó Gemma 2/3 porque sus "Gemma Terms of Use" permiten a Google cambiar las condiciones después. El código de llama.cpp lo baja `fetchLlamaCpp` (fijado a una versión) y lo compila el NDK 27.2 con CMake 3.22.1, que AGP descarga por versión (no es el asistente). Solo arm64 (`abiFilters`): el APK bajó ~90 MB al soltar x86/armv7. El modelo vive en `Android/data/com.ferolabs.hablo/files/modelos/` (sin permisos; se llena con `adb push`; **desinstalar la app lo borra**). **Medido en el S25 (2026-09-12): carga en 9 s, prompt a 27 t/s, respuesta a 11,0 t/s** con 6 hilos, contexto 2048, mmap. Se queda el 8B. Laboratorio en Ajustes: cargar, probar (la respuesta la lee la profesora con Piper), liberar. Sampler: min_p 0.05, top_p 0.9, temp 0.6; Qwen3 con `/no_think` en el system prompt. |
| Interfaz | Kotlin + Jetpack Compose | Menos capas intermedias con tres motores nativos encima. |
| Progreso | SharedPreferences | Suficiente; nada sale del teléfono. |

Las voces y el motor sherpa-onnx **se descargan solos al compilar** — hay tareas
Gradle (`fetchSherpaAar`, `fetchVoices`, `fetchAsr`) que los bajan la primera
vez y los meten en `assets/`. No están en el repositorio.

El modelo de 8B (~5 GB) **no cabe dentro de un APK**: tiene que vivir como
archivo aparte en el teléfono, copiado una vez por USB.

**Esquema del contenido (v2, 2026-09-14; nueve tipos desde la tarde).** `curriculum.json`: `levels` →
`units` (`id`, `emoji`, `title`, `subtitle`) → `lessons` (`id`, `title`,
**`theory`**, `exercises`) → ejercicios. Reglas que hacen caer la compilación
(`checkContent`) y la carga (`Content.kt`, con tests en `ContentTest.kt`):
- **`id` en cada ejercicio, único en todo el curso**, formato `<lección>e<n>`
  (`a1u4l2e5`). Es la clave del mazo de repaso, del cuaderno y del informe.
- **`theory` obligatoria en cada lección**: `{"title", "body", "trap"}`, los
  tres no vacíos. `body` admite `**negrita**`; `trap` es el sello de la casa
  (el error concreto de quien piensa en español y por qué).
- `listen`: `audio`, `options` (≥ 2, sin repetidas), **`answer` = el TEXTO de
  la opción correcta** (nunca un índice) y `audio == answer`.
- `translate`: `es`, `options` (≥ 2, sin repetidas), `answer` = texto de la
  opción correcta; `accept` opcional (desde el 16-09).
- `build`: `es`, `answer` (la frase), `extra` (señuelos: sin repetidos y sin
  palabras que ya estén en `answer`); `accept` opcional (desde el 16-09): los
  otros órdenes correctos con las mismas fichas (a2u2l4e6, a2u4l3e7, a2u5l1e6)
  y las formas que valen cuando el mazo lo convierte en "escribir".
- `type`: `audio`, `meaning` (no vacío); `accept` opcional (desde el 16-09).
- **`accept` vale en los cinco tipos con respuesta en inglés** (write, cloze,
  translate, build, type), con la misma regla (`checkProduced`: sin vacíos,
  sin repetir la respuesta ni entre sí, con contracciones y números
  igualados). Hace falta porque el mazo, Aguanta y "adivina antes de ver"
  convierten translate y build en "escribir" y sin alternativas marcaban mal
  "I'm 25", "I'm home" o "That's it" (auditoría de Cowork del 16-09,
  `contenido-nuevo/auditoria-a1-a2/`, sección 5). `Repaso.ejercicioDe`,
  `adivinanzas` y los "corrige tu propio error" pasan el `accept` de todos.
  La `'s` tras sustantivo, "o'clock" y los años NO se normalizan a propósito:
  van en `accept`.
- `speak`: `text`, `sound` (regla dura 5); toda palabra de `text` tiene que
  estar en `cmudict.dict`.
- **Los cuatro tipos de producción (2026-09-14, propuesta de Cowork en
  `contenido-nuevo/integrado/…/tipos-de-ejercicio.md`, con su evidencia):**
  - `write`: `es`, `answer`, `accept` (alternativas válidas, sin repetir la
    respuesta ni entre sí — con contracciones expandidas: "I'm" = "I am").
    Ve el español y escribe el inglés. d = 1,38 sobre elegir entre opciones
    en pruebas de producción (KATE Journal 30).
  - `cloze`: `text` con exactamente un hueco `___`, `answer`, `es`, `accept`.
  - `shadow`: `text` (en cmudict). La profesora lo dice y el alumno lo repite
    enseguida. Se mide que salgan las palabras (≥ 60 %) y se MUESTRA "tú X s ·
    ella Y s" como dato; **nunca se puntúa por fonema** (la revisión de
    shadowing 2025 lo da inconcluso para sonidos sueltos).
  - `minimalPair`: `options` (palabras sueltas, en cmudict, ≥ 2, sin
    repetir), `answer` entre ellas, `sentence` opcional (debe contener la
    palabra). Suena la palabra y se IDENTIFICA cuál fue (g = 0,95), nunca
    "¿son iguales?" (g = 0,57). La voz es la de la profesora de la lección
    (rotar por bloque, no por ítem: `Speaker` tiene una sola voz cargada).
  - La corrección de `write`/`cloze` dice QUÉ falló (`Correccion.kt`:
    "Te faltó la terminación «-s»: es «works», no «work»", "Te faltó la
    palabra «a»", "Sobra la palabra «am»", orden cambiado), comparando con la
    respuesta o alternativa más parecida. g = 0,73 con corrección contra 0,39
    sin ella (Rowland 2014). Tests en `NuevosTiposTest.kt`.
- `tip` es opcional en todos (32 de los 55 originales no lo traen; el
  contenido nuevo lo trae siempre).
El orden de claves es `id, type, audio, es, text, sound, options, answer,
accept, extra, meaning, sentence, tip`, un ejercicio por línea: lo escribe
`tools/content/migrar_a1.py` (idempotente; también convierte `answer` de
índice a texto e inserta fichas). **Bandeja de entrada:** lo que Claude Cowork
entrega va a `contenido-nuevo/`; se valida con `tools/content/validar.py
<curriculum.json>`, se integra con el script, y el lote integrado se mueve a
`contenido-nuevo/integrado/<fecha>/` para que no queden dos copias vivas.
Para AÑADIR ejercicios a lecciones que ya existen: `tools/content/anexar.py
<adiciones.json>` con `{id de lección: [ejercicios]}`; numera los ids a
continuación y no toca nada de lo que había. El validador vigente es
`tools/content/validar2.py` (el de Cowork, con los nueve tipos y las
contracciones). **A1 quedó en 300 ejercicios el 2026-09-14** (161 de
producción, 53 %; antes 62, 30 %).

**Parche de la auditoría A1-A2 (Cowork, 2026-09-16; aplicado el mismo día):**
`contenido-nuevo/integrado/2026-09-17-parche-auditoria/` (276 ids, 381
cambios: 37 fichas de teoría, ≈150 `accept`, opciones que tenían dos
respuestas buenas, 42 ejercicios reemplazados con el mismo id). Se aplica con
su `parchar2.py` (como `parchar.py`, pero una clave puede ser id de lección y
traer `theory` entera; escribe `curriculum.json.parchado.json` al lado).
Consecuencias en código: (1) el mazo guarda copias de `en`/`es`, así que al
arrancar `Mazo.refrescar` las vuelve a tomar del curso por id (conserva caja
y escalón; retira el ítem si el ejercicio ya no trae par o su frase nueva ya
está con otro id) — en el teléfono de Fero refrescó 10 ítems; (2) "Corrige tu
propio error" salta los fallos cuya `correcta` ya no es la del ejercicio
(`Repaso.propiosErrores`, `coincideCorrecta`); (3) cuatro fichas traen
tablas Markdown (`| a | b |`) y `FichaScreen` las pinta como tabla
(`tablaMarkdown` + `TablaFicha`). Oído con las 4 voces de Piper
(`scratchpad/oir_frases.py`, Moonshine + Parakeet + modelo de fonemas): los
seis audios nuevos se entienden con las cuatro voces (Grace dice "food" que
suena a "fruit" y "home" que suena a "hand": es la voz menos fiable, ya
sabido); a1u9l2e3 "I'll help you" / "I help you" se distinguen en las cuatro
(el /l/ sale en 3 de 4 según el modelo de fonemas); **a2u8l1e2 "1920": Piper
NO lo lee "nineteen twenty"**: Sophie y Mia dicen "nineteen hundred twenty" y
a Emma los dos reconocedores le oyen "1902" (**arreglado el 2026-09-24**
con el parche de Cowork `contenido-nuevo/integrado/2026-09-24-parche-1920/`:
"nineteen twenty" en letras en audio/answer/options, y el tip enseña cómo se
dicen los años; **regla: los años en ejercicios de audio van en letras**); a2u6l3e9 "live" salió
/laɪv/ en 3 de 4 voces → como autorizó Cowork, el texto pasó a "Most of my
friends are from here."; a2u2l3e8 "I have just gotten home." con `h` se oye
bien (Grace no). Los cuatro pares mínimos "cuya frase delata la respuesta"
(a1u6l3e12, a1u9l2e12, a2u2l4e10, a2u5l2e10) son los de `play: sentence`: la
frase NO se muestra antes de responder pero SÍ se oye (es lo que la
portadora rompía, ver abajo); se dejan como están salvo que Cowork prefiera
frases donde las dos palabras encajen.

**Pares mínimos y Piper (medido el 2026-09-14, `tools/content/oir_pares.py`):**
con una palabra suelta de medio segundo Piper es inestable (Emma dijo "bed"
como un balbuceo de un segundo) y el fonema distintivo del par aparece en 63
de 96 casos (voz × palabra, juzgado por el modelo de fonemas); dentro de la
portadora "The word is ___." aparece en 78 de 96. Por eso `minimalPair` suena
siempre así, y el script mide igual. Dos hallazgos para el contenido:
**"live" es heterónimo** y Piper lo lee /laɪv/ (el par live/leave no sirve
con estas voces), y `three`, `think`, `match` y `want/won't` salen con su
fonema en solo 2 de 4 voces: mejor otros pares o aceptar que dependen de la
profesora. `can/can't` sí conserva la /t/ (3–4 de 4).
**Pares que la portadora rompe (medido el 2026-09-15, 4 voces × 3 corridas):**
en "The word is X." Piper no saca el fonema de `three` (7/12), `think`
(2/12), `won't` (3/12), `since` (3/12) ni `van` (5/12), y tampoco el de casi
ningún candidato de reemplazo con θ, v, ɪ u oʊ (thin 1/12, very 3/12, sit
0/12): cambiar la palabra no arregla nada. Dentro de su `sentence` sí sale:
three 10/12, think 10/12, since 11/12, van 9/12, won't 6/12 (Grace nunca da
oʊ para este modelo). Por eso `minimalPair` admite `"play": "sentence"` y esos
cinco ejercicios lo llevan: suena la frase, no la portadora. Es el oído del
modelo de fonemas, no el de una persona, y con Grace (vocales) es el menos
fiable. Sonidos que la portadora sí conserva 12/12: ship/sheep, chair/share;
can't 10/12.
**A2 entró el 2026-09-14 (noche): 10 unidades, 29 lecciones, 265 ejercicios
(producción 65 %)** con `tools/content/anexar_nivel.py`. **A1 ampliado el
2026-09-16 (Cowork, `contenido-nuevo/integrado/2026-09-17-a1-nuevo/`): 6
unidades nuevas INTERCALADAS donde les toca según Empower (a1u10 tras a1u1,
a1u11 tras a1u3, a1u12 tras a1u4, a1u13 tras a1u5, a1u14 tras a1u7, a1u15 tras
a1u8; 16 lecciones, 211 ejercicios) con su `anexar_unidades.py`, más 57
ejercicios añadidos a 16 lecciones viejas (`anexar.py`) y 6 fichas ampliadas
(`parchar2.py`). **A2 ampliado el mismo día
(`contenido-nuevo/integrado/2026-09-17-a2-nuevo/`, mismo método: a2u11 tras
a2u2, a2u12 tras a2u3, a2u13 tras a2u6, a2u14 tras a2u7, a2u15 tras a2u8,
a2u16 tras a2u10; 16 lecciones, 195 ejercicios, más 71 añadidos a 22
lecciones y 7 fichas ampliadas; a2u16l2e8 trae 540 `accept` legítimos —
although/even though/though × lugar × final × sujeto × orden— y `acepta` los
recorre sin problema).** Total: **2 niveles, 31 unidades, 88 lecciones,
1.099 ejercicios** (A1: 15 unidades, 43 lecciones, 568; A2: 16 unidades, 45
lecciones, 531). Comprobado con el progreso real de Fero (11 lecciones
aprobadas, a1u1-a1u4): con la regla nueva quedan abiertas a1u1-a1u5 y las
tres nuevas intercaladas, y ninguna unidad tocada tiene candado; con la
vieja, todo desde a1u2 quedaba cerrado.** Consecuencias en
código: (1) **la cadena de desbloqueo** (`desbloqueadas` en `ScreenHome.kt`,
con test): una unidad está abierta si es la primera, si alguna lección suya
tiene puntaje, si la anterior está aprobada, o si la anterior está abierta y
la de antes de esa aprobada; en la práctica quedan DOS unidades abiertas por
delante y así la nueva intercalada y la vieja que sigue se hacen en paralelo
(con la regla vieja, "todas las anteriores aprobadas", lo que Fero ya tenía
desde a1u2 quedaba con candado). (2) **Fechas**: en `Correccion.numeros` un
número justo después de un mes se queda en cifras ("May 3" ≠ "May three": es
el error que enseña a1u12l2; Cowork vuelve a poner esos `accept`); igual en
`checkContent` y en `validar2.py`, cuyo `suelta()` ahora replica de verdad
`sueltaEstricta` (guion = espacio, tildes fuera, números en letras: antes daba
un falso positivo en a1u10l2e6 con "310-555-2468" / "3105552468"). (3) Oído
con Piper (`scratchpad/oir_a1nuevo.py`): a1u10l1e8 E/I salen /i/ y /aɪ/ en las
cuatro voces; a1u13l1e9 white/wide se distinguen con Emma, Mia y Grace y **no
con Sophie** (su "wide" sale con /t/); a1u15l1e5 was/were se distinguen en las
cuatro. La pantalla de inicio agrupa por nivel (un
encabezado con barra "x de y lecciones" por nivel) y la cadena de desbloqueo
sigue de A1 a A2. Pares mínimos de A2 medidos: `ban`/`walk`/`work`/`sense`
bien; `since` (ɪ) sale 1 de 4 y `van` (v) 2 de 4 — marginales.
**Juego "Parejas"** (`Parejas.kt`): a mitad de cada lección, sus propias
frases español↔inglés barajadas para unir contra el reloj; no cuenta para la
nota (es reconocimiento); solo si la lección da ≥ 3 parejas. Pedido de Fero:
"agrega algún juego en las lecciones para que no sea repetitivo".
`drills.json`: lista de `drills` con `text`, `sound`, `focus`, `tip`.
`bench.py` lee el sonido de cada frase de esos mismos JSON.

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
- **Con unidades intercaladas, "todas las anteriores aprobadas" cierra lo ya
  hecho.** La cadena de desbloqueo es `desbloqueadas()` (dos por delante);
  si se cambia, correr `DesbloqueoTest`.
- **`adb shell input tap` sin argumentos revienta** (pasa cuando `find` no
  encontró el texto por un acento: los scripts PowerShell con acentos
  necesitan BOM UTF-8, y un comando en línea no lo tiene). Revisar que el
  nodo exista antes de tocar; y **antes de cualquier toque, mirar que no haya
  una llamada en curso** (el 16-09 apareció una a mitad de prueba: no se tocó
  nada, pero pudo haber pasado).
- El `when` sobre `Exercise` tiene **nueve** subclases (listen, translate,
  build, type, speak, write, cloze, shadow, minimalPair). Fácil olvidar una;
  el compilador avisa porque la clase es `sealed`.
- **Heredocs de Bash comen las barras invertidas** (`\\n` llega como `\n`,
  `\d` se rompe): los scripts Python de edición se escriben con la
  herramienta Write al scratchpad y se ejecutan desde ahí. Un heredoc solo
  sirve si el script no tiene ni una barra invertida.
- `Llm.stop()` llamaba a `nativeStop()` sin comprobar `loaded`: sin el
  modelo cargado la librería nativa no existe y es `UnsatisfiedLinkError`
  (pasaba al salir de una conversación por internet). Cualquier `external
  fun` de `Llm` va detrás de `if (loaded)`.
- **"Spanish" DUDOSO con dictado perfecto NO es un bug (verificado
  2026-09-15).** En "He speaks Spanish every day." el amarillo de `es` saltó
  3 de 3 veces con Moonshine al 100 %. Se corrió el mismo modelo de fonemas
  int8 en el PC sobre esas tomas: la decodificación muestra una **ɛ entre las
  dos s** ("hispiks**ɛ**spænɪʃ") en las tres, y no en las dos tomas de la
  misma frase donde el veredicto fue BIEN; quitar la s de "speaks" de la
  secuencia esperada EMPEORA el puntaje. Es el error insignia ("espanish"),
  que Moonshine y Parakeet son ciegos a oír; ese mismo día Fero escribió "he
  speaks espanish" en el dictado. **Nunca subir ni aflojar el umbral de `es`
  por "falsa alarma": es el único detector de ese error.** Lo que sí se puede
  mejorar es el texto de la tarjeta amarilla.
- **Moonshine devuelve '' con audio de más de ~12 s** (medido 2026-09-16: 17 s
  de retell → '' en 150 ms, con voz 5-11 s y SNR 14-27 dB). `Listener`
  reconoce por ventanas de 12 s (`VENTANA_S`) y junta los trozos; una frase
  de lección cabe en un trozo y no cambia nada.
- **PowerShell `>` escribe UTF-16.** Al respaldar un archivo del teléfono con
  `adb exec-out cat … > copia.json` y volverlo a subir, la app recibe UTF-16
  y el JSON no parsea (pasó el 17-09 con `aptis.json`; se restauró
  convirtiendo a UTF-8). Respaldar con `[IO.File]::WriteAllText(..., UTF8
  sin BOM)` o `adb pull` del propio archivo, nunca con `>`.
- **live/leave (a1u4l1e12) NO está roto en la app.** El par solo reproduce
  la respuesta ("The word is leave."), que Piper dice bien con las 4 voces;
  "live" (heterónimo, Piper lo lee /laɪv/) nunca suena. El fallo de Fero
  ("live" tocado cuando sonó "leave") fue error de oído real. No "arreglar"
  el par por eso. Los pares que sí muerden son aquellos cuya RESPUESTA sale
  sin su fonema (three/think/won't/since/van, ver "Pares mínimos y Piper").

---

## Inventario de lo pedido (de `contenido-nuevo/plan-por-etapas.md`, 2026-09-15)

Esta tabla vive aquí y no en un chat: cada etapa terminada tacha su línea con
fecha y commit. Es lo que evita que "la charla libre" vuelva a olvidarse (ya
pasó una vez, acordada el 13-09).

**Ya está:** cuatro profesoras con voz · lecciones y pronunciación sin
internet · reconocimiento honesto · GOP calibrado · conversación con IA en 5
escenarios · corrección en español · informe descargable · cuaderno de errores
· racha y puntos · A1 y A2 (56 lecciones, 565 ejercicios) · tests · Parejas.

| # | Pedido | Estado real (15-09) |
|---|---|---|
| 1 | Marca mal respuestas que están bien | **bug abierto**, diagnóstico hecho (scratchpad `eval/EVALUACION-HABLO-2026-09-15.md`) |
| 2 | Leyendas viejas y textos sin actualizar | en diagnóstico |
| 3 | Charla libre sin escenario ni nivel, **con memoria** | **hecha el 16-09** ("Hablar de todo", `Memoria.kt`, `files/memoria/perfil.json`); falta que Fero la use |
| 4 | Escenarios divididos **por nivel**, sin memoria | **hecho el 16-09**: 5 A1 + 6 A2 + 6 B1, agrupados por nivel; cada uno arranca limpio |
| 5 | Actividades más diversas | **etapa 3 hecha el 16-09**: adivina antes de ver, repaso, corrige tu propio error, contrarreloj, Aguanta |
| 6 | Retos con presión | **hecho el 16-09**: Aguanta (tres errores) y contrarreloj (solo ahí hay reloj) |
| 7 | Vocabulario con cronómetro | **hecho el 16-09**: contrarreloj con reloj grande, "lo fallado vuelve", marcas por banco; el banco de Cowork (`vocabulario.json`, 41 bancos) entró el mismo día (714ef91) |
| 8 | Historias cortas con preguntas | **hecho el 16-09**: 12 historias de Cowork (6 A2, 6 B1) con retell obligatorio; ver "Etapa 4". **17-09: 24 historias en 4 tandas** (A1 y segunda A2 de Cowork; las tandas van por nivel) |
| 9 | Crucigramas | **hecho el 17-09**: 103 rejillas de Cowork (34 A1, 69 A2), `Crucigrama.kt` + `ScreenCrucigrama.kt`; ver "Oído, dictado y crucigramas" |
| 10 | Repaso espaciado (mazo) | **hecho el 16-09**: `Mazo.kt`, Leitner 1/3/7/16/35 + escalera elegir→armar→escribir→oír y escribir→decir |
| 11 | Pantalla de Oído (pares mínimos) | **hecho el 17-09**: 16 bloques de Cowork (`oido.json`), `ScreenOido.kt`; y el **dictado de números** (`dictado.json`, 80 ítems, `ScreenDictado.kt`) con comparación estricta; ver "Oído, dictado y crucigramas" |
| 12 | Contenido B1 y B2 | **B1 hecho el 17-09** (Cowork: 15 unidades, 45 lecciones, 540 ejercicios, 70 % producción; ver "Nivel B1"); B2 no existe |
| 13 | Escritura con motor de reglas (Fase 4) | no existe |
| 14 | Respaldo del progreso y modo oscuro (Fase 6) | **respaldo: código hecho el 24-09** (ver "Respaldo del progreso"); falta probarlo en el teléfono. Modo oscuro no existe |
| 15 | Recalibrar audio al llegar a ~100 grabaciones | pendiente con condición |

**Etapas, una a la vez (no se abre la siguiente hasta que la anterior esté en
el teléfono y Fero la haya usado; si aparece un bug, se vuelve a la 1):**
0) Diagnóstico — hecho el 15-09. 1) Que no enseñe nada incorrecto (bugs del
diagnóstico + leyendas viejas) — **código hecho el 15-09 (0.9.1, commits
ffd49bf…7d0c47c: cloze, números, accept, puntaje de hablar, cuaderno e
informe, pares con frase, textos, prompt); termina cuando Fero la use una
semana sin que lo corrija mal.** 2) La profesora que se acuerda —
**código hecho el 16-09**: "Hablar de todo" (`Scenario.LIBRE`, prompt
`buildFreePrompt` con la ficha de `Memoria`: 12 datos / 15 errores / 60
palabras / 400 tokens; los errores se anotan gratis desde `CORRECCIÓN:` en
cada turno; al cerrar, `cerrarCharlaLibre` hace UNA llamada a Haiku
(`ClaudeLlm.resumir`, ~530 tokens de entrada, ~$0,001) que devuelve
`{"datos","resumen"}` y `Memoria.aplicarRespuesta` conserva la ficha vieja si
no parsea; probado en el PC 3/3 parsean, y Haiku usa la ficha sin recitarla
—"your sister the doctor"—). Los escenarios van agrupados por nivel en la
lista y arrancan limpios (12 nuevos de Cowork, `anexar_escenarios.py`). El
prompt del escenario y el de la charla libre COMPARTEN `reglasComunes()` y
`protocoloCorreccion()`: no duplicar. Ajustes muestra lo que recuerda y "Que
lo olvide todo"; "Borrar todo mi progreso" también la borra. Termina cuando
Fero pueda hablar de lo que quiera y al día siguiente ella se acuerde.
3) Retos y repaso — **código hecho y probado en el teléfono el 16-09** (0.9.3;
ver "Etapa 3" abajo: adivina, repaso, corrige tu propio error, contrarreloj y
Aguanta vistos en pantalla; el mazo arrancó con 59 frases para el 17-09);
termina cuando Fero pueda abrir la app sin lección pendiente y tener veinte
minutos de práctica distinta. El `vocabulario.json` de Cowork entró el 16-09
(714ef91: 41 bancos, 368 parejas). 4) Historias — **código hecho el 16-09** (ver "Etapa 4" abajo).
5) **Modo Aptis** (abajo) — **cambio de diseño el 16-09 (Fero): no es un
diagnóstico, es una PISTA DE PREPARACIÓN de A1 a B2 por destreza que evalúa
mientras entrena** (0.9.7; ver "Etapa 5" abajo). El diagnóstico que se
construyó por la mañana quedó como el simulacro completo, que se abre cuando
las cinco pistas alcanzan B1. Faltan los bancos de Reading, Listening, Writing
y Speaking (Cowork, en ese orden de rentabilidad: Writing → Reading →
Listening → Speaking).
6) B1 y B2 (Cowork), respaldo del progreso, modo oscuro, recalibración del
audio.

**Etapa 3 (2026-09-16), cómo está construida.** Todo corre en la misma
pantalla de lección con un `ModoLeccion` (LECCION / REPASO / AGUANTA) y una
`Lesson` sintética que arma `Repaso.kt`:
- *Adivina antes de ver*: al abrir una lección por primera vez, hasta 3 frases
  (`Repaso.adivinanzas`) para intentarlas SIN haberlas visto; "Ver cómo se
  dice"; no puntúa, no va al cuaderno, se puede saltar. Intentar y errar con
  la respuesta después deja mejor recuerdo que leerla directa (Kornell, Hays &
  Bjork 2009, *JEP:LMC* 35; Richland, Kornell & Kao 2009, *JEP:Applied* 15).
- *Mazo* (`Mazo.kt`, `filesDir/mazo.json`): al aprobar una lección entran
  sus frases con inglés y español (translate, write, build, cloze, type);
  Leitner 1/3/7/16/35 por caja; escalera 0..4 (elegir → armar → escribir →
  oír y escribir → decir) que sube al acertar y baja al fallar; aprendido =
  escalón 4 y caja 4. Sesión de hasta 20 ítems; solo el primer intento de la
  sesión mueve el mazo. La primera vez, si el mazo está vacío, entran las
  lecciones ya aprobadas (para mañana). "Decir" va con `Sound.GENERAL`: sin
  GOP y **sin reloj** (quitar presión al hablar solo cambia los "eeeh" por
  silencios).
- *Corrige tu propio error* (`Exercise.FixIt`): fallos del cuaderno de tipo
  escribir/completar/dictado, de días anteriores, con el ejercicio original
  (acepta alternativas; en cloze, el hueco solo o la frase). Hasta 3 por
  sesión, al final del repaso; se retira al corregirlo bien dos veces
  (`Mazo.registrarCorreccion`). Solo errores reales: los falsos se borraron.
- *Contrarreloj* (`ScreenContrarreloj.kt`): rondas de 6 parejas; una pareja
  con algún fallo vuelve a la cola hasta salir limpia; marca = segundos por
  pareja por banco (`Mazo.registrarMarca`, últimas 30), nunca reprueba. Bancos:
  "Frases de tus lecciones" (de las aprobadas) + `assets/content/vocabulario.json`
  (opcional; formato abajo).
- *Aguanta*: todo lo visto mezclado (ejercicios de lecciones aprobadas +
  ítems del mazo con escalón > 0, hasta 60), se corta al tercer error; sin
  puntos: "Llegaste a N · tu marca es M" (`Mazo.registrarAguanta`).

**Revisión adversarial del 2026-09-16 (workflow de 4 lentes + verificadores)
y lo que cambió (0.9.4):** (1) `Correccion.suelta` iguala **'s = is y 'd =
would solo tras sujeto** (pronombre, wh-, that/there/here; nunca tras un
nombre): el mazo pedía "What's your name?" en "escribir" y marcaba mal "What
is your name?" (24 frases del curso). El diagnóstico y el chequeo de
duplicados usan `sueltaEstricta` (sin esa igualación). Trade-off asumido:
"he is got" pasaría por "he's got". (2) Sin tildes en `normalizeAnswer`
("Bogota" = "Bogotá"), también en el `normaliza` de checkContent. (3) El mazo
no duplica frases que solo cambian en puntuación, y dos ítems con el MISMO
español (a1u3l1e4 "I work at a bank" / a1u6l2e1 "I work in a bank.") se
aceptan mutuamente y nunca son señuelo uno del otro; "armar" no usa como
señuelo la contracción/forma larga de la propia frase y se evalúa con
`Correccion.acepta`. (4) `TypeWhatYouHear` lleva `accept` (el mazo lo llena).
(5) Contrarreloj: cada ronda con `key(vuelta)` (dos rondas con las mismas
parejas nacían "terminadas" y sin botón); rondas de < 4 parejas no fijan
marca. (6) "Aprendido" exige haberlo DICHO (acierto en el último escalón).
(7) Un error del cuaderno se retira por ejercicio, no por día. (8) Memoria:
contador de generación (un resumen que llega después de "olvidar todo" no
revive la ficha); al salir a mitad de respuesta no se lee ni se anota lo
truncado; el resumen por Haiku solo si el motor elegido es Claude (con Gemini
o la IA del teléfono solo se guardan los errores, y la cabecera lo dice).
(9) Hueco: vale también "works in a hospital" / "She works". Refutados (no
tocar): las explicaciones en español en la charla libre no son "errores"
falsos; ver `subagents/workflows/wf_bd4683cb-21f`.

**Etapa 4 (2026-09-16): historias cortas.** `assets/content/historias.json`
(`tandas` → `historias`; formato en `contenido-nuevo/integrado/2026-09-16-historias/COMO-INTEGRAR-HISTORIAS.md`;
se anexan con `tools/content/anexar_historias.py`; `checkContent` valida
tandas de 3-12, 6-10 frases, 3 preguntas de 3 opciones, retell con ≥ 2 pistas
y glosario ≥ 2 en cmudict). `ScreenHistoria.kt`: la profesora LEE la historia
(frase por frase, Piper) con el texto y el glosario en pantalla → 3 preguntas
→ **retell hablado obligatorio** (no hay botón para saltarlo: sin él es
comprensión lectora y se pierde la mitad del efecto; intervención narrativa
oral d = 1,36, tandas de 3-12 sesiones d = 2,53), por partes: una grabación
de hasta 20 s por pista (`Listener.startRecording(maxSeconds = 20)`) → se
puntúa como shadow (cobertura de palabras de contenido de la historia +
segundos hablados; nunca por fonema, nunca reprueba) → el glosario entra al
mazo (`Mazo.alimentarPares`, ids `historia:<id>|<en>`; una palabra suelta
salta el escalón "armar"). Progreso en `Store.historiaHecha`. **Regla de
Cowork (hallazgo al escribirlas): el curso va en inglés AMERICANO** (store,
movie, neighbor, canceled); la excepción es el Modo Aptis, que es del British
Council y enseña la diferencia como contenido (stopover, no layover).
**La pantalla de lección se partió por tipo de ejercicio** (`ejercicios/Elegir.kt`,
`Escribir.kt`, `Armar.kt`, `Hablar.kt`; refactor puro, sin MVVM: ScreenLesson
pasó de 1.229 a 838 líneas y solo conserva el estado, la cola y los modos).

**Formato de `vocabulario.json` (lo escribe Cowork, se anexa con
`tools/content/anexar_vocabulario.py`):** `{"bancos": [{"id": "casa-a1",
"title": "🏠 La casa · A1", "level": "A1", "pares": [{"en": "kitchen", "es":
"cocina"}, …]}]}`; mínimo 3 parejas por banco, sin inglés ni español
repetido dentro del banco; `checkContent` lo valida si el archivo existe.
**Entró el 2026-09-16: 41 bancos (tema × nivel, 14 A1 / 14 A2 / 13 B1), 368
parejas; y esa misma noche el vocabulario ampliado de Cowork
(`contenido-nuevo/integrado/2026-09-17-vocab-ampliado/`: 43 bancos y 971
parejas nuevas más 13 parejas viejas corregidas con su `parchar_vocab.py`
—británico → americano, España → Colombia—; el parche va ANTES de
`anexar_vocabulario.py` o chocan parejas repetidas). Total: **84 bancos (26
A1 / 45 A2 / 13 B1), 1.339 parejas**, sin inglés ni español repetido en todo
el archivo; lecciones y bancos juntos rondan las 1.480 palabras distintas
(meta English Profile A2: ~1.526). La lista del contrarreloj va agrupada por
nivel, con cuántos bancos de cada nivel ya tienen marca y ⏱ en los que la
tienen.** Regla de Cowork: dentro de un banco no hay dos ingleses casi
sinónimos ni dos españoles equivalentes, así que nunca hay dos respuestas
válidas a la vez; **una ronda nunca mezcla bancos** (al mezclar, la garantía
desaparece). Los bancos van de 3 a 25 parejas: los grandes se parten en
rondas de hasta 8 del mismo banco (un subconjunto conserva la garantía).

**Nivel B1 (2026-09-17, Cowork, `contenido-nuevo/integrado/2026-09-17-b1-nivel/`):
15 unidades, 45 lecciones, 540 ejercicios (12 por lección, 70 % cloze/write;
Core Inventory B1 + English File Intermediate), con `anexar_nivel.py`.
Total: **3 niveles, 46 unidades, 133 lecciones, 1.639 ejercicios.** Con el
vocabulario B1 (`…/2026-09-17-vocab-b1/`: 40 bancos de 25 parejas,
`anexar_vocabulario.py`) son **124 bancos, 2.339 parejas** (≈ 2.400 palabras
distintas A1-B1; English Profile da ~2.950 para B1: 81 %), y 63 crucigramas
B1 (`tools/content/anexar_crucigramas.py`): **166 crucigramas**. Cambios de
código que pidió Cowork: (1) **`'d` = would O had** (`Correccion.variantes`):
en B1 "I'd" es "I had" en el tercer condicional y el past perfect; se prueban
las dos lecturas POR OCURRENCIA ("If I'd known, I'd have left" = had +
would) y `acepta` da por buena la respuesta si algún par de variantes
coincide; "'d have" solo es would (así "I had have left" no cuela); solo tras
sujeto. **Y `'s` = is O has** (18-09, revisión de Gemini): con el present
perfect y "have got" de B1, "She has got a sister" se comparaba contra "she
is got" y fallaba; ahora `'s` bifurca igual que `'d` (is/has por
ocurrencia), y delante de been/got/gotten solo es has (así "she is got" no
cuela). `igualaContracciones` (habla) cierra cualquiera de las dos lecturas a
la contracción del modelo. Lo que NO se hizo de esa revisión: aceptar
"dont"/"cant"/"ive" sin apóstrofo (en los 41 fallos del cuaderno de Fero no
hay ni uno así, y la corrección ya dice "Casi: es «don't», no «dont»"), y
cambiar contenido de B1 por gusto (por ejemplo "absolutely starving" es
inglés normal); sí entraron 5 `accept` "…sorry for the wait" en b1u14l2e7. (2) `CONTRACCIONES` suma must've /
should've / would've / could've / might've / hadn't / hasn't / haven't (también
en `checkContent` y `validar2.py`); eso dejó 214 `accept` de B1 como
duplicados exactos y `tools/content/dedupe_accept.py --aplicar` los quitó
(conserva el primero de cada forma; es idempotente). (3) **La primera unidad
de cada nivel está siempre abierta** (`desbloqueadas(units, score,
primeras)`; test en `DesbloqueoTest`): Fero puede empezar B1 sin terminar A2
porque el Modo Aptis pide B1; dentro del nivel la cadena sigue igual. (4) Las
listas del contrarreloj (124 bancos) y de crucigramas (166) se pliegan por
nivel: abiertos los niveles con marca o algo empezado, si no A1.

**Oído, dictado de números y crucigramas (2026-09-17; datos de Cowork en
`contenido-nuevo/integrado/2026-09-17-oido-dictado-drills/` y
`…/2026-09-17-crucigramas/`).** Tres pantallas nuevas en el inicio, todas sin
reloj y sin red:
- **Oído** (`ScreenOido.kt`, `assets/content/oido.json`: 16 bloques, 6-10
  pares cada uno, `sound` obligatorio como en los drills). Identificar CUÁL
  palabra sonó (g = 0,95), nunca "¿son iguales?" (0,57). **Una voz por
  bloque**, rotando entre las cuatro profesoras (`Store.siguienteVozOido`;
  `Speaker` tiene una sola voz cargada). Cada par suena dentro de su frase
  NEUTRA con `___` (el sentido no delata la respuesta); al fallar suena la
  otra palabra en la misma frase para comparar. El bloque **cierra hablando**
  la frase `hablar` con el jurado de su `sound` (oír bien y decir bien van
  sueltos, r = 0,31, y la pantalla lo dice); el modelo de fonemas se carga en
  esa fase y se suelta al salir. Resultado por bloque en prefs
  (`oido_<id>_bien/total`), ✓ con ≥ 80 %. Los fallos van al cuaderno (tipo
  "oído").
  **Medición antes de publicar** (`tools/content/oir_oido.py`, pedida por
  Cowork): dos jueces por (voz, palabra), porque ninguno es una persona: el
  modelo de fonemas (tramo alineado contra la frase esperada, más cerca de la
  palabra dicha que de su pareja) y Parakeet 0.6B (escribe la palabra). Una
  palabra pasa si la reconoce cualquiera; un par pasa con ≥ 3 de 4 voces.
  Solo con el modelo de fonemas se descartaba la mitad de los 147 pares
  (78/147), casi todo por su propio oído: oye la d final como t (ride→ɹaɪt 4
  de 4), b/v como ð, y las vocales de Grace mal; con los dos jueces quedan
  los descartes de verdad (ver el informe a Cowork en el commit).
- **Dictado de números** (`ScreenDictado.kt`, `dictado.json`: 80 ítems, 7
  tipos, modos `escribir`/`elegir`; el `audio` va en palabras). Tarea literal
  de Aptis Listening parte 1: hasta DOS escuchas, después el texto del audio
  y el `tip`. **Comparación estricta** `Correccion.dictado` /
  `aceptaDictado`: `suelta` quitaba `.`, `:`, `$`, `@` y daba por buenas
  «$650» por «$6.50», «14:50» por «1450», «wademail.com» por «wade@mail.com»
  (hallazgo de Cowork). La estricta conserva los signos DENTRO de una ficha,
  quita solo los de los bordes y el `$` inicial, números 0-100 en letras, el
  número tras un mes en cifras y el cero inicial de la hora («07:30» =
  «7:30»). Tests en `DictadoTest.kt`. Los fallos van al cuaderno con tipo
  "números" (NO "dictado": ese entra a "corrige tu propio error" con la regla
  suelta y daría por corregido lo que no lo está). Rondas de 10, primero lo
  que nunca salió bien (`Store.dictadoHecho`). **Deletreos con Grace:** sus
  letras sueltas no se entienden con ninguna forma de escribirlas (medido con
  Moonshine y Parakeet: «K, Y, L, E» → "case of white's L. E."); Sophie las
  dice limpias 8 de 8. Si la profesora es Grace, los deletreos los lee Sophie
  y la pantalla lo dice. La «A,» no se lee como artículo en ninguna voz.
- **Crucigramas** (`Crucigrama.kt` modelo + `filesDir/crucigramas.json`;
  `ScreenCrucigrama.kt`; `crucigramas.json`: 103 rejillas ≤ 10 × 10, 5-8
  palabras, cada una de UN banco de `vocabulario.json`; `checkContent` y
  `parseCrucigramas` replican `validar_cruci.py`: choques, corridas
  fantasma, numeración clásica, conectividad, pista = `es` del banco; **si
  se cambia un banco hay que regenerar con `generar.py` de Cowork**). Pista
  en español, palabra en inglés. Tocar una casilla selecciona su palabra;
  tocar el cursor en un cruce cambia de dirección. Palabra completa y bien →
  verde, Piper la dice y se pasa a la siguiente; completa y mal → marca
  suave. Ayudas «Una letra» y «Oírla» (la palabra deja de contar como "sin
  ayuda" y al terminar entra al mazo como `cruci:<banco>|<en>`). Se guarda a
  cada letra. La rejilla pinta cada casilla con `CeldaCruci` (parámetros
  primitivos + `key`): al teclear solo se recomponen las casillas que
  cambian, no las 100. **Teclado propio de 26 letras + ⌫, no el del sistema** (Cowork
  pedía el del sistema; el propio no tiene autocorrección ni pierde el foco y
  se pudo probar sin el teléfono a mano; se cambia si Fero lo prefiere).
- **Gramática y «¿Por qué?»** (auditoría del 16-09, "falta 1 y 2"; hecho el
  17-09). `ScreenGramatica.kt`: tarjeta «📖 Gramática» en el inicio con las
  88 fichas por nivel y unidad (✓ en las aprobadas) y, arriba, las fichas de
  REFERENCIA de `assets/content/referencia.json` (opcional; las escribe
  Cowork: `{"fichas": [{"id", "title", "body", "trap"}]}`, planas, mismas
  reglas que `theory`; `Course.referencia` las carga como lecciones sin
  ejercicios para reusar `FichaScreen`). Al fallar un ejercicio, la caja de
  corrección trae «📖 ¿Por qué? → <título de la ficha>» que abre la ficha de
  la lección de origen (`Course.lessonOfExercise`: saca `a1u4l2` de
  `a1u4l2e5` aunque venga envuelto, `fix|a1u4l2e5`; en el repaso y en
  Aguanta también; nada para historias ni crucigramas). Corrección explicada
  g = 0,73 contra 0,39 sin ella (Rowland 2014).
- **Drills**: 30 de Cowork (70 en total). Los de `general` no tienen
  veredicto por fonema y nunca acumulan datos, así que en el sorteo de
  `ScreenPronunciation` llevan peso fijo 1,0 (con el 1,5 de "sin probar"
  taparían a los sonidos que sí se miden).

**Etapa 5 (2026-09-16): el Modo Aptis es una PISTA DE PREPARACIÓN, no un
test.** Pedido de Fero, tarde del 16-09, después de que el diagnóstico de 30
tareas ya estaba hecho y probado: *un test de 30 tareas da una foto con margen
de error; una pista graduada mide con cada tarea, y subir rápido de nivel ES
el diagnóstico* (si ya es B1 leyendo, barre el A2 en una sesión y la pista lo
promueve; no hay que preguntárselo). Código: `Aptis.kt` (modelo, bancos,
promoción, lo guardado, el juez) y `ScreenAptis.kt` (tablero, pista,
simulacro). Tarjeta "🎯 Modo Aptis · Pista de preparación" en el inicio,
SECCIÓN APARTE del curso. Cómo está construido:
- **Contenido** (`assets/content/`, todo validado en `checkContent` y en
  `parseBancoAptis`, con ids únicos entre TODOS los archivos):
  `aptis-pistas.json` (reglas: `promocion` "N de M" y `ronda` por pista;
  obligatorio), `aptis-core-gramatica.json` y `aptis-core-vocabulario.json`
  (los 120 ítems de Cowork tal cual: gramática con `text`/`point`/`why`;
  vocabulario con `sub` synonym/definition/collocation + `prompt`, o usage +
  `text`; los 2 de C1 entran como B2), `aptis-diagnostico.json` (el
  simulacro; sus 30 tareas se reciclan como primeras tareas de sus pistas,
  salvo las que repiten un ítem del Core con el mismo enunciado) y los bancos
  de las pistas `aptis-<pista>.json`: **Writing ya entró el 16-09**
  (`pista-writing.json` de Cowork, 16 tareas A1-B2 en las 4 partes del
  examen: una palabra a cinco mensajes · 20-30 palabras · tres respuestas en
  un grupo · dos correos informal + formal, con `mensajes` y `promptEn/Es`;
  archivado en `contenido-nuevo/integrado/2026-09-16-aptis-writing/`);
  faltan Reading, Listening y Speaking. Formato de esos bancos: plano
  (`{"tareas": [...]}`, como las secciones del simulacro) o por partes
  (`{"partes": [{"aptis": "Parte 1 · una palabra", "tareas": [...]}]}`, el de
  Cowork; `validar_pista.py` es su validador); `why` opcional se muestra al
  corregir. `tools/content/integrar_diagnostico.py` valida el simulacro; los
  bancos del Core se copiaron tal cual. **Reading, Listening y Speaking
  entraron el 17-09** (Cowork, `contenido-nuevo/integrado/2026-09-17-aptis-pistas/`:
  42 + 42 + 28 tareas A1-B2, por partes del examen; con las del simulacro,
  46/48/31): las cuatro pistas arrancan en A1. Speaking partes 2 y 3 traen
  `foto` (descripción en inglés, que el juez recibe como "LA FOTO") y, cuando
  Fero genere las 20 fotos con Gemini (`FOTOS-PARA-GENERAR.md`), `"imagenes":
  ["images/speaking/s-009.webp"]` (dos en la parte 3): `FotosSpeaking` las
  pinta desde assets y `checkContent` exige que existan, .webp/.jpg y ≤ 400
  KB. Reading parte 3 (opiniones) no tiene tipo en la app; Cowork escribe el
  banco si se construye. `checkContent` avisa (no falla) si un nivel tiene
  menos tareas que las que pide la promoción (Writing A1 trae 2).
- **Cinco pistas** (`PistaAptis`: Core, Reading, Listening, Writing,
  Speaking). Cada una tiene DOS niveles a propósito (`Aptis`): el que se
  ENTRENA (`nivel`, arranca en el nivel más bajo con tareas: A2 hoy, A1
  cuando Cowork mande A1) y el ALCANZADO (`alcanzado`, null hasta la primera
  promoción). El tablero y el piso miran el alcanzado: Writing hoy solo trae
  B1 y B2, así que entrena B1 desde el arranque, pero entrenar B1 no es haber
  llegado a B1.
- **Promoción** = `Condicion.cumple` sobre las ÚLTIMAS N tareas del nivel que
  se entrena (N = "de" de la condición; "4 de 5" en Core/Reading/Listening,
  "2 de 3" en Writing/Speaking): ese nivel queda alcanzado y la pista pasa al
  siguiente con tareas. En Writing y Speaking una tarea cuenta como acierto si
  el nivel que estimó Claude ≥ el nivel de la tarea. **Nunca baja**: si las
  últimas N van flojas (menos de la mitad bien, `Aptis.flojo`), la ronda
  siguiente mezcla la mitad de tareas del nivel anterior (`Aptis.ronda`).
  Las rondas sirven primero lo nunca visto y luego lo más viejo.
- **La ronda** (`PistaScreen`): Core con 30 s por ítem como en el examen;
  después de CADA tarea, corrección con la respuesta y el `why` (g = 0,73 con
  explicación contra 0,39 sin ella); en Listening se muestra lo que decía el
  audio y se puede reoír; en Writing/Speaking, el juicio de la IA con su cita
  y "cuenta como B1 / no llega a B1". Si la IA no responde (sin clave, sin
  red), la tarea se reintenta o se salta sin contar.
- **El tablero** (`AptisScreen`): arriba en grande **TU PISO** (la más floja
  de las CUATRO; el Core no entra: es el desempate, y se dice), una barra
  por destreza (A1→B2 llena hasta el alcanzado, el nivel que se entrena
  marcado, B1 con la raya del examen), "últimas N: x bien · hacen falta M",
  y el simulacro **bloqueado hasta que las cinco hayan alcanzado B1**.
- **El simulacro** (`SimulacroScreen` + `AptisParteScreen`): el diagnóstico
  de la mañana, de una sentada, con el reloj (36 min, 30 tareas), sin
  corrección hasta el final; estima por parte con `EstimacionAptis` (Core
  con la tabla `estimacion.core` del JSON en proporciones; Reading/Listening
  exigen todos los ítems del nivel; IA = el nivel alcanzado en dos tareas) y
  muestra el piso y una tarjeta por destreza. Sus resultados se guardan
  aparte de las pistas y se invalidan si cambia el contenido
  (`ResultadoSeccion.vigente`).
- **Lo guardado**: `filesDir/aptis.json` versión 2 (`pistas` con nivel,
  alcanzado e historial de hasta 600 intentos, y `simulacro`); un archivo de
  la versión 1 (el diagnóstico de la mañana) se ignora y se empieza de cero.
- Lo que no se negocia se conserva: `AVISO_APTIS` en el tablero, en las
  pistas y partes que estima la IA y en el resultado del simulacro; la IA
  cita una frase del alumno como prueba (`JuezAptis.citaAparece`, reclamo una
  vez, descarte si repite; Sonnet 8/8 en el PC el 16-09); las muletillas que
  Parakeet inventa al final del silencio se cortan antes de juzgar
  (`sinMuletillas`).
- Trampas que siguen valiendo: Parakeet inventa "Mm-hmm."/"Okay." en las
  ventanas en silencio y repite una palabra en el corte de ventana ("in the
  country. Country."); en Speaking la duración cuenta (la rúbrica lo
  pregunta): 12 s en una tarea de 90 s sale por debajo de A2, y es estricto a
  propósito; los segundos de voz quedan por debajo de lo hablado si el audio
  es flojo, por eso al juez se le dan también los segundos totales.
- Probado en el S25 el 16-09 (`scratchpad/eval/etapa5h.ps1` y `etapa5i.ps1`,
  capturas en `caps5/8x-9x`): tablero vacío ("sin empezar"); ronda de Core de
  10 con corrección y `why` tras cada ítem y la promoción en el quinto
  ("alcanzas A2 y pasas a B1"); Reading (2 de A2, sin nivel aún); Writing en
  la pista juzgado por Claude ("✓ Cuenta como B1", cita textual); Speaking
  con la voz del PC ("✓ Cuenta como A2"); el archivo guarda nivel y alcanzado
  por pista; "Empezar el Modo Aptis de cero" con confirmación. El teléfono
  quedó limpio. Con los bancos de hoy Reading, Listening y Speaking tienen 1-2
  tareas por nivel: la promoción (4 de 5 / 2 de 3) exige repetirlas; se
  destraba cuando lleguen los bancos de Cowork. Con el banco de Writing
  puesto, la pista de Writing arranca en A1 (parte 1: cinco mensajes con una
  palabra), probado en el teléfono: "✓ Cuenta como A1" con la cita. También
  probado sembrando `aptis.json` (`etapa5k.ps1`): con 5 fallos en B1 el Core
  avisa "van flojas: mezcla A2" y la ronda alterna B1/A2/B1/A2; con las cinco
  en B1 el simulacro se abre, encadena Core → "Siguiente parte: Reading" y,
  al salir y volver, retoma en la parte pendiente.

**Respaldo del progreso (2026-09-24, `Respaldo.kt`, tarjeta en Ajustes).**
Un respaldo es UN JSON (`RespaldoDatos`: `formato`, `version`, `app`,
`fecha`, `prefs` con tipo por clave, `archivos` con el texto de
`progreso.json`, `mazo.json`, `aptis.json`, `crucigramas.json` y
`memoria/perfil.json`; `null` = no existía y al recuperar se borra; una clave
ausente no se toca). **Nunca entran grabaciones, modelos ni claves.** Va a
**Descargas › Hablo** por MediaStore, que Android NO borra al desinstalar
(documentación oficial "Access media files from shared storage"). Tres
momentos: al salir de la app (`MainActivity.onStop`, como mucho cada 10 min,
un archivo por día `hablo-auto-<día>.json`, quedan 7); antes de "Borrar todo"
y antes de recuperar (`hablo-antes-de-…`, quedan 5; si no se puede guardar,
no se borra ni se recupera nada); y a mano con "Guardar una copia y enviarla"
(menú de compartir: Drive, WhatsApp). Recuperar muestra "lo de ahora" contra
"lo del respaldo" (lecciones aprobadas, puntos, frases del mazo), escribe y
llama a `Activity.recreate()` (el mazo, el cuaderno y lo demás tienen copias
en memoria). **Trampa:** tras reinstalar, Android deja de considerar esas
copias "de la app" y la lista sale vacía; hay que abrirlas con el selector
de archivos del sistema ("Buscar el archivo en el teléfono…", que arranca en
Download/Hablo). Tests en `RespaldoTest.kt`. Aparte, y desde antes: el
manifiesto deja que el respaldo de Google del teléfono copie las
preferencias (`data_extraction_rules.xml`), lo hace el sistema, no la app.

**Modo Aptis (decisión de Fero, 2026-09-15).** Fero va a presentar **Aptis
ESOL General** (sin fecha aún, sin saber qué nivel le exigen). **El curso por
niveles se queda como está; Aptis va como SECCIÓN APARTE en la etapa 5**, no
se mezcla con las 56 lecciones. Datos del examen: 18 tipos de tarea en 5
componentes (Core 25 min: 25 gramática + 25 vocabulario en sinónimos,
definiciones, uso y combinaciones de palabras; Reading 35 min; Listening 40
min; Writing 50 min incl. dos correos informal/formal de 120-150 palabras;
Speaking 12 min: preguntas personales, describir una foto, comparar dos,
tema abstracto). El Core es el desempate entre niveles: lo más rentable.
**Aptis no puntúa fonemas**: GOP sirve para hablar mejor, no para la nota.
**Aptis exige B1 o superior EN TODAS las habilidades: es un piso, no un
promedio** (dato de Cowork, 2026-09-16). Por eso la meta del Modo Aptis es
**subir la habilidad más débil**, no la media: el diagnóstico corto tiene que
decir cuál es la más floja y ahí va el trabajo.
Tres actividades suben de prioridad por ser tareas literales del examen:
ordenar frases (Reading 2), dictado de números y horas (Listening 1) y
combinaciones de palabras (Core). Lo primero del modo es un **diagnóstico
corto** (una tarea de cada componente, cronometrada) para saber en qué nivel
anda por destreza. Hace falta lo que hoy no hay: imágenes para Speaking 2-3,
corrección de writing/speaking con Claude API contra rúbrica MCER (**en
pantalla: es una estimación, no la nota real**) y bancos grandes de ítems
(los escribe Cowork). Tarea de Fero: averiguar qué nivel le piden.

---

## Estado y plan

**Versión actual: 0.9.9** (2026-09-17/18: el nivel B1 con su vocabulario y
crucigramas; ver "Nivel B1"). **0.9.8** (2026-09-17: Oído, dictado de
números, crucigramas, 24 historias, 70 drills, las pistas de Aptis
Reading/Listening/Speaking, la sección Gramática y el botón «¿Por qué?»; ver
"Oído, dictado de números y crucigramas").
**0.9.7** (2026-09-16: el Modo Aptis como pista de
preparación por destreza, con el diagnóstico de la mañana convertido en el
simulacro; las etapas 2, 3 y 4 ya estaban en el teléfono desde la 0.9.5; ver
"Inventario de lo pedido"). **0.9.1** (2026-09-15: la etapa 1 del diagnóstico). **0.9** (conversación por internet; el motor por defecto pasó a
Claude API el 2026-09-13, ver "Sesión del 2026-09-13" más abajo). **A1 completo
el 2026-09-14: 27 lecciones, 207 ejercicios, 27 fichas de teoría** (19
lecciones nuevas escritas por Claude Cowork siguiendo un sílabo A1 real, más
las 8 fichas de las lecciones viejas), en el esquema v2 (abajo).
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
**Y el ejemplo no puede ser solo el de la edad (2026-09-15):** con "I have 30
years" como único ejemplo, Haiku corrigió "I am forty" (correcto) por "la
edad siempre va con years old" y hasta inventó "I have forty years" donde
Fero dijo "I am forty years old". A/B con 7 turnos (4 correctos, 3 con
error) × 3 corridas: Haiku pasó de **10/21 a 19/21** decisiones correctas y
Sonnet de 16/21 a 21/21 al (a) listar lo que NO se corrige ("I am forty",
"I'm 40", formas largas, variantes, "nunca reescribir una frase correcta
para hacerla más corta o natural"), (b) cambiar el ejemplo con error a
"is doctor / she work" y (c) añadir un ejemplo sin error que se parece a uno
("I am forty and I do not like coffee"). Con la regla de no reescribir,
Haiku 8/8 en los correctos y 8/9 en los errores reales.

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

**Plan acordado (etapas, en orden de valor por trabajo).** 0) *Esquema de
contenido v2* (ids por ejercicio, `answer` como texto, `theory` obligatoria,
`checkContent` estricto y los primeros tests) — **hecho el 14-09**, antes que
todo lo demás porque el mazo, el cuaderno y el informe dependen del id, y
migrar 55 ejercicios cuesta medio día pero migrar 1.400 sería una obra.
1) *Que deje de ser previsible* — hecho el 13-09. 2) *La profesora que se
acuerda*: "Hablar de todo" con hilo propio y ficha de memoria en
`files/memoria/perfil.json` (datos del alumno, errores con contador, resumen;
topes duros de 12 datos / 15 errores / 60 palabras / 400 tokens; los errores
se registran gratis desde la línea `CORRECCIÓN:` y el resumen con UNA llamada
a Haiku al cerrar la charla, ~$0,50/mes; si el JSON no parsea se conserva la
ficha vieja). Los escenarios de práctica NO llevan memoria (decisión de Fero).
3) *Teoría en pantalla*: las fichas ya existen en el JSON (27); falta la
sección "Gramática" y el botón "¿Por qué?" al fallar. 4) *Repaso de hoy*
(Leitner 1/3/7/16/35 + dificultad graduada del mismo ítem: elegir → armar →
escribir → oír y escribir → decir). 5) *Oído*. 6) *Más tipos de ejercicio*.
7) *Contenido A2→B2* (A1 ya está completo: 27 lecciones). En paralelo, trabajo
de PC: recalibrar umbrales **a nivel de frase** con el corpus nuevo y afinar
las puertas de audio.

**Deudas conocidas que van a morder si se ignoran:** los tests cubren solo el
parser del contenido (`app/src/test/.../ContentTest.kt`, 13 casos, `./gradlew
test`); Leitner, el truncado de la ficha y `splitReply` siguen sin test cuando
se escriban; `Course.load` corre
en el hilo principal antes de pintar; `Speaker` mantiene **una sola voz inglesa
cargada** (`ensureVoiceLoaded` libera y recarga), así que "una profesora al azar
por ítem" en la pantalla de Oído serían 12 recargas por bloque: hay que
pre-sintetizar o rotar por bloque.

**Decisiones de Fero sobre voz e informe (2026-09-13, tarde).** (a) Las
correcciones **solo en texto**: probó la voz española leyendo la línea
`CORRECCIÓN:` y la rechazó ("no me gusta el cambio de voz"); la voz `es_MX` se
quitó del APK, de `voicePackages` y de `Speaker`. Si algún día se retoma, el
problema de fondo sigue: cada voz Piper es de un idioma, y la voz inglesa de la
profesora leyendo español es ininteligible, así que cualquier explicación
hablada en español obliga a cambiar de voz. (b) Pronunciación y los `speak` de
las lecciones se quedan **siempre en el teléfono** (sin internet, sin coste);
la conversación puede usar cualquier motor. (c) **Informe descargable**
(`Progreso.kt`): idea suya para subirlo a su proyecto de Claude y practicar
allí. Cuaderno en `filesDir/progreso.json` (intentos con veredicto por fonema,
ejercicios fallados con lo que puso, correcciones de la profesora, y un diario
por día de ejercicios/frases habladas/turnos/lecciones), y un Markdown en
español que se arma **entero en el teléfono**: sin coste, sin red, y solo sale
cuando él toca "Descargar mi informe" en Ajustes. Lleva un bloque final de
instrucciones para su tutor de IA. (d) La memoria entre sesiones va **solo en
la charla libre**, no en los escenarios de práctica, y el resumen lo hará una
sola llamada a Haiku al cerrar la charla.

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
