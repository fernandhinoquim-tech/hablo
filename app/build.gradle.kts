import java.io.File
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---------------------------------------------------------------------------
// Voces Piper y motor sherpa-onnx.
// Gradle los descarga solo la primera vez que compilas y los mete dentro del
// APK. Despues de eso la app habla sin depender de nada de Android.
// ---------------------------------------------------------------------------

// sherpa-onnx y onnxruntime-android tienen que estar compilados contra la MISMA
// version de ONNX Runtime: los simbolos del motor llevan etiqueta de version
// (VERS_1.27.0) y Android no deja que una JNI use otra. sherpa-onnx 1.13.4 usa
// ORT 1.27.0, que existe en Maven; 1.13.5-1.13.8 usan 1.27.1/1.28.2, que no.
val sherpaVersion = "1.13.4"
val onnxRuntimeVersion = "1.27.0"

// Solo voces inglesas. Se probó una voz española para leer las correcciones y
// Fero la rechazó el 2026-09-13: "no me gusta el cambio de voz"; las
// correcciones se quedan en texto. No volver a empaquetarla sin pedírselo.
val voicePackages = mapOf(
    "emma"   to "vits-piper-en_US-kristin-medium-int8",
    "sophie" to "vits-piper-en_GB-jenny_dioco-medium-int8",
    "mia"    to "vits-piper-en_US-ljspeech-medium-int8",
    "grace"  to "vits-piper-en_GB-southern_english_female-medium-int8"
)

val libsDir = File(projectDir, "libs")
val ttsAssetsDir = File(projectDir, "src/main/assets/tts")
val piperCache = File(layout.buildDirectory.get().asFile, "piper-cache")

fun download(url: String, target: File) {
    target.parentFile.mkdirs()
    logger.lifecycle("  descargando ${target.name} ...")
    URI(url).toURL().openStream().use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    }
}

// El AAR de sherpa-onnx trae su propia copia de libonnxruntime.so. Se le quita
// al descargarlo para que quede una sola en el APK: la oficial de
// onnxruntime-android, de la misma version (ver sherpaVersion).
fun stripBundledOnnxRuntime(aar: File) {
    val tmp = File(aar.parentFile, aar.name + ".tmp")
    var removed = 0
    ZipFile(aar).use { zin ->
        ZipOutputStream(tmp.outputStream().buffered()).use { zout ->
            for (entry in zin.entries()) {
                if (entry.name.endsWith("/libonnxruntime.so")) { removed++; continue }
                zout.putNextEntry(ZipEntry(entry.name))
                if (!entry.isDirectory) zin.getInputStream(entry).use { it.copyTo(zout) }
                zout.closeEntry()
            }
        }
    }
    if (removed > 0) {
        aar.delete()
        tmp.renameTo(aar)
        logger.lifecycle("  sherpa-onnx: quitadas $removed copias de libonnxruntime.so (se usa la de onnxruntime-android)")
    } else {
        tmp.delete()
    }
}

val fetchSherpaAar = tasks.register("fetchSherpaAar") {
    val aar = File(libsDir, "sherpa-onnx-$sherpaVersion.aar")
    outputs.file(aar)
    doLast {
        if (!aar.exists() || aar.length() < 1_000_000) {
            download(
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar",
                aar
            )
        }
        stripBundledOnnxRuntime(aar)
    }
}

val fetchVoices = tasks.register("fetchVoices") {
    outputs.dir(ttsAssetsDir)
    doLast {
        piperCache.mkdirs()
        ttsAssetsDir.mkdirs()
        val espeakTarget = File(ttsAssetsDir, "espeak-ng-data")

        voicePackages.forEach { (voiceId, pkg) ->
            val voiceDir = File(ttsAssetsDir, voiceId)
            val modelOut = File(voiceDir, "model.onnx")
            val tokensOut = File(voiceDir, "tokens.txt")
            if (modelOut.exists() && tokensOut.exists() && espeakTarget.exists()) {
                logger.lifecycle("  voz '$voiceId' ya lista")
                return@forEach
            }

            val tarball = File(piperCache, "$pkg.tar.bz2")
            if (!tarball.exists() || tarball.length() < 100_000) {
                download(
                    "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$pkg.tar.bz2",
                    tarball
                )
            }

            val extracted = File(piperCache, pkg)
            if (!extracted.exists()) {
                copy {
                    from(tarTree(resources.bzip2(tarball)))
                    into(extracted)
                }
            }

            // Los nombres internos del paquete varian, asi que se buscan.
            val onnx = extracted.walkTopDown().firstOrNull { it.isFile && it.name.endsWith(".onnx") }
            val tokens = extracted.walkTopDown().firstOrNull { it.isFile && it.name == "tokens.txt" }
            val espeak = extracted.walkTopDown().firstOrNull { it.isDirectory && it.name == "espeak-ng-data" }

            if (onnx == null || tokens == null) {
                throw GradleException("El paquete $pkg no trae .onnx o tokens.txt")
            }

            voiceDir.mkdirs()
            onnx.copyTo(modelOut, overwrite = true)
            tokens.copyTo(tokensOut, overwrite = true)
            logger.lifecycle("  voz '$voiceId' lista (${modelOut.length() / 1024 / 1024} MB)")

            // espeak-ng-data es identico en todas las voces: se guarda una sola vez.
            if (!espeakTarget.exists() && espeak != null) {
                espeak.copyRecursively(espeakTarget, overwrite = true)
                logger.lifecycle("  espeak-ng-data instalado")
            }
        }
    }
}

// Moonshine base v2 (export "quantized-2026-02-27"): dos archivos .ort. Se
// eligió con el banco de pruebas tools/asr-bench sobre grabaciones reales:
// tiny devolvía texto vacío en 4 de 26; base no, y sigue siendo honesto.
val asrPackage = "sherpa-onnx-moonshine-base-en-quantized-2026-02-27"
val asrAssetsDir = File(projectDir, "src/main/assets/asr")

val fetchAsr = tasks.register("fetchAsr") {
    outputs.dir(asrAssetsDir)
    doLast {
        // El marcador dice qué paquete hay instalado: si cambia, se vuelve a bajar.
        val marker = File(asrAssetsDir, ".paquete")
        if (marker.exists() && marker.readText().trim() == asrPackage) {
            logger.lifecycle("  reconocimiento de voz ya listo ($asrPackage)")
            return@doLast
        }
        asrAssetsDir.deleteRecursively()
        piperCache.mkdirs()
        val tarball = File(piperCache, "$asrPackage.tar.bz2")
        if (!tarball.exists() || tarball.length() < 1_000_000) {
            download(
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$asrPackage.tar.bz2",
                tarball
            )
        }
        val extracted = File(piperCache, asrPackage)
        if (!extracted.exists()) {
            copy {
                from(tarTree(resources.bzip2(tarball)))
                into(extracted)
            }
        }
        asrAssetsDir.mkdirs()
        // Se copian solo los modelos y los tokens; los wav de prueba no hacen falta.
        extracted.walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".onnx") || it.name.endsWith(".ort") || it.name == "tokens.txt") }
            .forEach { f ->
                f.copyTo(File(asrAssetsDir, f.name), overwrite = true)
                logger.lifecycle("  asr: ${f.name} (${f.length() / 1024 / 1024} MB)")
            }
        marker.writeText(asrPackage)
    }
}

// ---------------------------------------------------------------------------
// Modelo de fonemas para GOP (evaluacion de pronunciacion por fonema).
// Lo exporta tools/asr-bench/phoneme_export.py (Hugging Face -> ONNX int8);
// aqui solo se copia a assets/gop/. Si no esta, la app compila igual y la
// evaluacion por fonema queda apagada (la pantalla lo dice).
// ---------------------------------------------------------------------------

val gopModelName = "wav2vec2-large-xlsr-53-l2-arctic-phoneme"
val gopSource = File(rootDir, "tools/asr-bench/models/phoneme/$gopModelName/model.int8.onnx")
val gopAssetsDir = File(projectDir, "src/main/assets/gop")

val fetchGop = tasks.register("fetchGop") {
    outputs.dir(gopAssetsDir)
    doLast {
        val target = File(gopAssetsDir, "model.int8.onnx")
        if (target.exists() && target.length() > 100_000_000) {
            logger.lifecycle("  modelo de fonemas ya listo (${target.length() / 1024 / 1024} MB)")
        } else if (gopSource.exists()) {
            gopSource.copyTo(target, overwrite = true)
            logger.lifecycle("  modelo de fonemas copiado (${target.length() / 1024 / 1024} MB)")
        } else {
            logger.warn("  AVISO: no hay modelo de fonemas en ${gopSource.path}; la app compila sin GOP. Correr tools/asr-bench/phoneme_export.py $gopModelName")
        }
    }
}

// ---------------------------------------------------------------------------
// Motor de IA (Fase 3): llama.cpp se compila desde el codigo fuente dentro de
// la app (cpp/CMakeLists.txt). Aqui se descarga ese codigo, fijado a una
// version, a cpp/llama.cpp/ (no va al repositorio: son ~150 MB).
// ---------------------------------------------------------------------------

val llamaCppVersion = "0.4.0"
val llamaCppDir = File(projectDir, "src/main/cpp/llama.cpp")

val fetchLlamaCpp = tasks.register("fetchLlamaCpp") {
    outputs.dir(llamaCppDir)
    doLast {
        val marker = File(llamaCppDir, ".version")
        if (marker.exists() && marker.readText().trim() == llamaCppVersion) {
            logger.lifecycle("  llama.cpp $llamaCppVersion ya listo")
            return@doLast
        }
        piperCache.mkdirs()
        val zip = File(piperCache, "llama.cpp-$llamaCppVersion.zip")
        if (!zip.exists() || zip.length() < 1_000_000) {
            download("https://github.com/ggml-org/llama.cpp/archive/refs/tags/v$llamaCppVersion.zip", zip)
        }
        llamaCppDir.deleteRecursively()
        val extracted = File(piperCache, "llama.cpp-src")
        extracted.deleteRecursively()
        copy {
            from(zipTree(zip))
            into(extracted)
        }
        // el zip trae una carpeta raiz llama.cpp-<version>/
        val root = extracted.listFiles()?.firstOrNull { it.isDirectory }
            ?: throw GradleException("El zip de llama.cpp no trae carpeta raiz")
        root.copyRecursively(llamaCppDir, overwrite = true)
        marker.writeText(llamaCppVersion)
        logger.lifecycle("  llama.cpp $llamaCppVersion listo en ${llamaCppDir.path}")
    }
}

// ---------------------------------------------------------------------------
// Revision del contenido antes de compilar. Un ejercicio de hablar sin su
// etiqueta "sound" haria que el jurado por sonido no dispare y nadie se
// enteraria; aqui la compilacion se cae y dice exactamente donde. La misma
// revision la hace la app al arrancar (Content.kt), por si el JSON se edita
// por fuera de Gradle.
// ---------------------------------------------------------------------------

val contentDir = File(projectDir, "src/main/assets/content")
val validSounds = listOf("sh", "th", "h", "v", "ed", "final", "es", "rl", "general")
val validTypes = setOf("listen", "translate", "build", "type", "speak", "write", "cloze", "shadow", "minimalPair")

val checkContent = tasks.register("checkContent") {
    inputs.dir(contentDir)
    doLast {
        val problems = ArrayList<String>()

        fun checkSound(o: Map<*, *>, where: String) {
            val sound = o["sound"]
            when {
                sound == null -> problems.add("$where: falta \"sound\". Valores: ${validSounds.joinToString(", ")}")
                sound !in validSounds -> problems.add("$where: \"sound\": \"$sound\" no existe. Valores: ${validSounds.joinToString(", ")}")
            }
        }

        val slurper = groovy.json.JsonSlurper()

        // Mismas reglas que Content.kt al cargar, pero aqui se cae la COMPILACION:
        // con ~1.400 ejercicios en la Fase 5 nadie los revisa a ojo. Un "answer"
        // fuera de las opciones ensenaria ingles incorrecto sin que nada avise.
        val curriculum = slurper.parse(File(contentDir, "curriculum.json")) as Map<*, *>
        val lessonIds = HashSet<String>()
        val exerciseIds = HashSet<String>()
        fun textOf(v: Any?): String = v?.toString().orEmpty().trim()
        for (level in curriculum["levels"] as List<*>) {
            for (unit in (level as Map<*, *>)["units"] as List<*>) {
                for (lesson in (unit as Map<*, *>)["lessons"] as List<*>) {
                    val l = lesson as Map<*, *>
                    val id = l["id"].toString()
                    if (!lessonIds.add(id)) problems.add("leccion $id: id repetido")

                    // La ficha de teoria es obligatoria y completa.
                    val theory = l["theory"] as? Map<*, *>
                    if (theory == null) {
                        problems.add("leccion $id: falta \"theory\" (title, body y trap)")
                    } else {
                        for (key in listOf("title", "body", "trap")) {
                            if (textOf(theory[key]).isBlank()) problems.add("leccion $id: \"theory\" sin \"$key\"")
                        }
                    }

                    (l["exercises"] as List<*>).forEachIndexed { i, ex ->
                        val e = ex as Map<*, *>
                        val where = "leccion $id, ejercicio ${i + 1}"
                        val type = e["type"]
                        if (type !in validTypes) problems.add("$where: tipo \"$type\" desconocido")

                        val exId = textOf(e["id"])
                        if (exId.isBlank()) problems.add("$where: falta \"id\" (formato ${id}e${i + 1})")
                        else if (!exerciseIds.add(exId)) problems.add("$where: id \"$exId\" repetido en el curso")

                        if (type == "listen" || type == "translate") {
                            val options = (e["options"] as? List<*>)?.map { textOf(it) } ?: emptyList()
                            val answer = textOf(e["answer"])
                            if (options.size < 2) problems.add("$where: \"options\" necesita al menos 2 opciones")
                            val repetidas = options.groupBy { it }.filter { it.value.size > 1 }.keys
                            if (repetidas.isNotEmpty()) problems.add("$where: opciones repetidas: ${repetidas.joinToString(" | ")}")
                            if (answer.isBlank()) problems.add("$where: falta \"answer\" (el texto de la opcion correcta)")
                            else if (answer !in options) problems.add("$where: \"answer\" no esta entre las opciones: \"$answer\"")
                            if (type == "listen" && textOf(e["audio"]) != answer) problems.add("$where: en listen, \"audio\" y \"answer\" deben ser iguales")
                        }
                        if (type == "type" && textOf(e["meaning"]).isBlank()) problems.add("$where: falta \"meaning\"")

                        // Los cuatro tipos de produccion (2026-09-14).
                        // Misma lista que Correccion.kt: "I'm fine" y "I am fine" son la misma respuesta.
                        val contracciones = listOf("i'm" to "i am", "you're" to "you are", "we're" to "we are", "they're" to "they are",
                            "isn't" to "is not", "aren't" to "are not", "wasn't" to "was not", "weren't" to "were not",
                            "don't" to "do not", "doesn't" to "does not", "didn't" to "did not", "can't" to "can not", "cannot" to "can not",
                            "couldn't" to "could not", "won't" to "will not", "wouldn't" to "would not", "shouldn't" to "should not",
                            "i'll" to "i will", "you'll" to "you will", "he'll" to "he will", "she'll" to "she will", "it'll" to "it will",
                            "we'll" to "we will", "they'll" to "they will", "i've" to "i have", "you've" to "you have", "we've" to "we have",
                            "they've" to "they have", "let's" to "let us")
                        // Numeros como Correccion.kt: "8" = "eight", "twenty five" = "twenty-five".
                        val unidades = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
                            "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
                        val decenas = mapOf(20 to "twenty", 30 to "thirty", 40 to "forty", 50 to "fifty", 60 to "sixty", 70 to "seventy", 80 to "eighty", 90 to "ninety")
                        fun enLetras(n: Int): String? = when {
                            n < 0 || n > 100 -> null
                            n < 20 -> unidades[n]
                            n == 100 -> "one hundred"
                            n % 10 == 0 -> decenas[n]
                            else -> decenas[n / 10 * 10] + "-" + unidades[n % 10]
                        }
                        fun numeros(tokens: List<String>): List<String> {
                            val out = mutableListOf<String>()
                            var i = 0
                            while (i < tokens.size) {
                                val t = tokens[i]
                                if (t.all { it.isDigit() }) {
                                    val letras = t.toIntOrNull()?.let { enLetras(it) }
                                    if (letras != null) { out += letras.split(" "); i++; continue }
                                }
                                if (decenas.containsValue(t) && i + 1 < tokens.size && unidades.indexOf(tokens[i + 1]) in 1..9) {
                                    out += t + "-" + tokens[i + 1]; i += 2; continue
                                }
                                if (t == "a" && i + 1 < tokens.size && tokens[i + 1] == "hundred") { out += "one"; i++; continue }
                                out += t; i++
                            }
                            return out
                        }
                        fun normaliza(t: String): String {
                            var x = " " + t.lowercase().replace('-', ' ').replace('\u2013', ' ').replace("\u2019", "'")
                                .filter { it.isLetterOrDigit() || it == ' ' || it == '\'' }.trim().replace(Regex("\\s+"), " ") + " "
                            for ((corta, larga) in contracciones) x = x.replace(" $corta ", " $larga ")
                            return numeros(x.trim().split(" ").filter { it.isNotEmpty() }).joinToString(" ")
                        }
                        if (type == "write" || type == "cloze") {
                            val answer = textOf(e["answer"])
                            if (answer.isBlank()) problems.add("$where: falta \"answer\"")
                            if (textOf(e["es"]).isBlank()) problems.add("$where: falta \"es\"")
                            val accept = (e["accept"] as? List<*>)?.map { textOf(it) } ?: emptyList()
                            val vistas = hashSetOf(normaliza(answer))
                            for (a in accept) {
                                if (a.isBlank()) problems.add("$where: \"accept\" tiene una entrada vacia")
                                else if (!vistas.add(normaliza(a))) problems.add("$where: \"accept\" repite la respuesta o se repite: \"$a\"")
                            }
                            if (type == "cloze") {
                                val text = textOf(e["text"])
                                val huecos = text.windowed(3).count { it == "___" }
                                if (huecos != 1) problems.add("$where: \"text\" tiene que tener exactamente un hueco ___ (tiene $huecos)")
                            }
                        }
                        if (type == "shadow" && textOf(e["text"]).isBlank()) problems.add("$where: falta \"text\"")
                        if (type == "minimalPair") {
                            val options = (e["options"] as? List<*>)?.map { textOf(it) } ?: emptyList()
                            val answer = textOf(e["answer"])
                            if (options.size < 2) problems.add("$where: \"options\" necesita al menos 2 palabras")
                            if (options.toSet().size != options.size) problems.add("$where: opciones repetidas")
                            if (answer.isBlank() || answer !in options) problems.add("$where: \"answer\" no esta entre las opciones: \"$answer\"")
                            options.filter { it.contains(' ') }.forEach { problems.add("$where: las opciones de un par minimo son palabras sueltas: \"$it\"") }
                            val sentence = textOf(e["sentence"])
                            if (sentence.isNotBlank() && !sentence.lowercase().contains(answer.lowercase())) problems.add("$where: \"sentence\" no contiene la palabra \"$answer\"")
                            val play = textOf(e["play"])
                            if (play.isNotBlank() && play != "sentence") problems.add("$where: \"play\" solo admite \"sentence\"")
                            if (play == "sentence" && sentence.isBlank()) problems.add("$where: \"play\": \"sentence\" sin \"sentence\"")
                        }
                        if (type == "build") {
                            val propias = textOf(e["answer"]).split(" ").map { it.lowercase() }.filter { it.isNotEmpty() }.toSet()
                            val extra = (e["extra"] as? List<*>)?.map { textOf(it) } ?: emptyList()
                            val choque = extra.filter { it.lowercase() in propias }
                            if (choque.isNotEmpty()) problems.add("$where: \"extra\" repite palabras de la respuesta: ${choque.joinToString(", ")}")
                            if (extra.map { it.lowercase() }.toSet().size != extra.size) problems.add("$where: \"extra\" tiene palabras repetidas")
                        }
                        if (type == "speak") checkSound(e, where)
                    }
                }
            }
        }

        val drills = slurper.parse(File(contentDir, "drills.json")) as Map<*, *>
        (drills["drills"] as List<*>).forEachIndexed { i, d ->
            checkSound(d as Map<*, *>, "drills.json, drill ${i + 1}")
        }

        // Escenarios de conversacion: todos los campos, sin excepcion.
        val scenarios = slurper.parse(File(contentDir, "scenarios.json")) as Map<*, *>
        val scenarioIds = HashSet<String>()
        (scenarios["scenarios"] as List<*>).forEachIndexed { i, sc ->
            val o = sc as Map<*, *>
            val where = "scenarios.json, escenario ${i + 1}"
            for (key in listOf("id", "title", "level", "goalEs", "role", "opening")) {
                if (o[key] == null || o[key].toString().isBlank()) problems.add("$where: falta \"$key\"")
            }
            for (key in listOf("targets", "watch")) {
                if ((o[key] as? List<*>).isNullOrEmpty()) problems.add("$where: \"$key\" vacio")
            }
            if (o["id"] != null && !scenarioIds.add(o["id"].toString())) problems.add("$where: id repetido")
            // Las ayudas de gramatica son opcionales, pero si estan tienen que
            // traer el patron en ingles y la explicacion en espanol.
            (o["help"] as? List<*>)?.forEachIndexed { j, h ->
                val a = h as? Map<*, *>
                val en = a?.get("en")?.toString().orEmpty()
                val es = a?.get("es")?.toString().orEmpty()
                if (en.isBlank() || es.isBlank()) problems.add("$where: ayuda ${j + 1} sin \"en\" o sin \"es\"")
            }
        }

        // Toda palabra que se pide decir en voz alta tiene que estar en el
        // diccionario de pronunciacion: sin fonemas esperados no hay GOP y el
        // sonido del ejercicio quedaria sin evaluar en silencio.
        val dictFile = File(gopAssetsDir, "cmudict.dict")
        if (dictFile.exists()) {
            val known = HashSet<String>()
            dictFile.forEachLine { line ->
                if (!line.startsWith(";;;")) {
                    val w = line.substringBefore(' ')
                    known.add(w.substringBefore('('))
                }
            }
            fun spokenWords(text: String): List<String> =
                text.lowercase().replace('\u2019', '\'').replace('-', ' ')
                    .filter { it.isLetterOrDigit() || it == ' ' || it == '\'' }
                    .split(' ').filter { it.isNotBlank() }
            val texts = ArrayList<Pair<String, String>>()
            (drills["drills"] as List<*>).forEachIndexed { i, d ->
                texts.add("drills.json, drill ${i + 1}" to ((d as Map<*, *>)["text"].toString()))
            }
            for (level in curriculum["levels"] as List<*>) {
                for (unit in (level as Map<*, *>)["units"] as List<*>) {
                    for (lesson in (unit as Map<*, *>)["lessons"] as List<*>) {
                        val l = lesson as Map<*, *>
                        (l["exercises"] as List<*>).forEachIndexed { i, ex ->
                            val e = ex as Map<*, *>
                            val where = "leccion ${l["id"]}, ejercicio ${i + 1}"
                            when (e["type"]) {
                                "speak", "shadow" -> texts.add(where to e["text"].toString())
                                // Las palabras del par las DICE la profesora: si no estan en el
                                // diccionario, la voz las inventa y el ejercicio miente.
                                "minimalPair" -> (e["options"] as? List<*>)?.forEach { texts.add(where to it.toString()) }
                            }
                        }
                    }
                }
            }
            for ((where, text) in texts) {
                val missing = spokenWords(text).filter { it !in known }
                if (missing.isNotEmpty()) problems.add("$where: palabras que no estan en cmudict.dict: ${missing.joinToString(", ")} (agregarlas a assets/gop/cmudict-extra.dict)")
            }
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                "Contenido invalido (${problems.size}):\n  " + problems.joinToString("\n  ")
            )
        }
        logger.lifecycle("  contenido revisado: ${lessonIds.size} lecciones, ${(drills["drills"] as List<*>).size} drills, ${scenarioIds.size} escenarios")
    }
}

android {
    namespace = "com.ferolabs.hablo"
    compileSdk = 35

    // NDK y CMake por version: AGP los descarga al SDK si faltan. No es el
    // asistente de AGP; AGP/Gradle/Kotlin siguen fijados (regla dura 2).
    // Para los tests de JVM: android.util.Log devuelve 0 en vez de reventar con "Stub!".
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.ferolabs.hablo"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "0.9.2"

        // Solo el procesador del S25 Ultra. De paso el APK deja de llevar las
        // copias de sherpa-onnx y ONNX Runtime para x86/armv7 (~100 MB menos).
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DCMAKE_BUILD_TYPE=Release"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // Los modelos no se comprimen: cargan mas rapido y pesan casi igual.
    androidResources {
        noCompress += listOf("onnx", "ort")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // Por si un AAR viejo de sherpa-onnx en libs/ todavia trae su copia:
            // que no rompa el empaquetado. La buena es la de onnxruntime-android
            // (ver stripBundledOnnxRuntime).
            pickFirsts += listOf(
                "lib/arm64-v8a/libonnxruntime.so",
                "lib/armeabi-v7a/libonnxruntime.so",
                "lib/x86/libonnxruntime.so",
                "lib/x86_64/libonnxruntime.so"
            )
        }
    }
}

tasks.named("preBuild") {
    dependsOn(checkContent, fetchSherpaAar, fetchVoices, fetchAsr, fetchGop, fetchLlamaCpp)
}

dependencies {
    // Solo el AAR de la version fijada: si en libs/ queda uno viejo, no se mezcla.
    implementation(files("libs/sherpa-onnx-$sherpaVersion.aar"))

    // API Java de ONNX Runtime para el modelo de fonemas. Trae el motor nativo
    // oficial, que usan tambien las voces y Moonshine (sherpa-onnx). Tiene que
    // ser la misma version con la que se compilo sherpa-onnx: ver arriba.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:$onnxRuntimeVersion")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Tests de JVM (./gradlew test): Kotlin puro, no tocan AGP ni las versiones
    // fijadas. org.json real porque el android.jar de los tests trae stubs.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
