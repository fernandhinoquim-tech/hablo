import java.text.Normalizer
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
val assetsDir = File(projectDir, "src/main/assets")
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
                            "they've" to "they have", "let's" to "let us",
                            // B1 (17-09): modales del pasado y negativos de have, como en Correccion.kt
                            "must've" to "must have", "should've" to "should have", "would've" to "would have",
                            "could've" to "could have", "might've" to "might have",
                            "hadn't" to "had not", "hasn't" to "has not", "haven't" to "have not")
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
                        val meses = setOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")
                        fun numeros(tokens: List<String>): List<String> {
                            val out = mutableListOf<String>()
                            var i = 0
                            while (i < tokens.size) {
                                val t = tokens[i]
                                // un numero pegado a un mes es una fecha y se queda en cifras (Correccion.MESES)
                                if (t.all { it.isDigit() } && (i == 0 || tokens[i - 1] !in meses)) {
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
                            // Sin tildes, como normalizeAnswer en Content.kt ("Bogota" = "Bogota con tilde").
                            val sinTildes = Normalizer.normalize(t.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
                            var x = " " + sinTildes.replace('-', ' ').replace('\u2013', ' ').replace("\u2019", "'")
                                .filter { it.isLetterOrDigit() || it == ' ' || it == '\'' }.trim().replace(Regex("\\s+"), " ") + " "
                            for ((corta, larga) in contracciones) x = x.replace(" $corta ", " $larga ")
                            return numeros(x.trim().split(" ").filter { it.isNotEmpty() }).joinToString(" ")
                        }
                        // "accept" vale en los seis tipos con respuesta en ingles (write, cloze, y desde el 16-09
                        // translate, build y type: el mazo los convierte en "escribir"); misma regla que checkProduced.
                        if (type in setOf("write", "cloze", "translate", "build", "type")) {
                            val answer = textOf(if (type == "type") e["audio"] else e["answer"])
                            val accept = (e["accept"] as? List<*>)?.map { textOf(it) } ?: emptyList()
                            val vistas = hashSetOf(normaliza(answer))
                            for (a in accept) {
                                if (a.isBlank()) problems.add("$where: \"accept\" tiene una entrada vacia")
                                else if (!vistas.add(normaliza(a))) problems.add("$where: \"accept\" repite la respuesta o se repite: \"$a\"")
                            }
                        }
                        if (type == "write" || type == "cloze") {
                            val answer = textOf(e["answer"])
                            if (answer.isBlank()) problems.add("$where: falta \"answer\"")
                            if (textOf(e["es"]).isBlank()) problems.add("$where: falta \"es\"")
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

        // Bancos de vocabulario del contrarreloj (etapa 3): opcional; si esta, se revisa.
        val vocabFile = File(contentDir, "vocabulario.json")
        val bancoEs = HashMap<String, Map<String, String>>()   // id de banco -> (en -> es), para los crucigramas
        if (vocabFile.exists()) {
            val vocab = slurper.parse(vocabFile) as Map<*, *>
            val bancoIds = HashSet<String>()
            ((vocab["bancos"] as? List<*>) ?: emptyList<Any>()).forEachIndexed { i, b ->
                val o = b as Map<*, *>
                val where = "vocabulario.json, banco ${i + 1}"
                o["id"]?.let { id ->
                    bancoEs[id.toString()] = ((o["pares"] as? List<*>) ?: emptyList<Any>()).mapNotNull { p ->
                        (p as? Map<*, *>)?.let { pm -> pm["en"]?.toString()?.trim().orEmpty() to pm["es"]?.toString()?.trim().orEmpty() }
                    }.toMap()
                }
                for (key in listOf("id", "title", "level")) {
                    if (o[key] == null || o[key].toString().isBlank()) problems.add("$where: falta \"$key\"")
                }
                if (o["id"] != null && !bancoIds.add(o["id"].toString())) problems.add("$where: id repetido")
                val pares = (o["pares"] as? List<*>) ?: emptyList<Any>()
                if (pares.size < 3) problems.add("$where: un banco necesita al menos 3 parejas")
                val ens = HashSet<String>(); val ess = HashSet<String>()
                pares.forEachIndexed { j, p ->
                    val pm = p as? Map<*, *>
                    val en = pm?.get("en")?.toString()?.trim().orEmpty()
                    val es = pm?.get("es")?.toString()?.trim().orEmpty()
                    if (en.isBlank() || es.isBlank()) problems.add("$where: pareja ${j + 1} sin \"en\" o sin \"es\"")
                    if (!ens.add(en.lowercase()) || !ess.add(es.lowercase())) problems.add("$where: pareja repetida: \"$en\" / \"$es\"")
                }
            }
        }

        // Historias (etapa 4): opcional; si esta, se revisa como validar_historias.py de Cowork.
        val histFile = File(contentDir, "historias.json")
        val glosarioPalabras = ArrayList<Pair<String, String>>()   // (donde, palabra) para el chequeo contra cmudict
        if (histFile.exists()) {
            val hist = slurper.parse(histFile) as Map<*, *>
            val hIds = HashSet<String>()
            ((hist["tandas"] as? List<*>) ?: emptyList<Any>()).forEachIndexed { ti, t ->
                val tm = t as Map<*, *>
                val whereT = "historias.json, tanda ${ti + 1}"
                for (key in listOf("id", "level", "title")) {
                    if (tm[key] == null || tm[key].toString().isBlank()) problems.add("$whereT: falta \"$key\"")
                }
                val hs = (tm["historias"] as? List<*>) ?: emptyList<Any>()
                if (hs.size < 3 || hs.size > 12) problems.add("$whereT: ${hs.size} historias (el efecto maximo esta entre 3 y 12)")
                hs.forEachIndexed { hi, h ->
                    val o = h as Map<*, *>
                    val where = "$whereT, historia ${hi + 1}"
                    for (key in listOf("id", "level", "title", "titleEs", "trap")) {
                        if (o[key] == null || o[key].toString().isBlank()) problems.add("$where: falta \"$key\"")
                    }
                    if (o["id"] != null && !hIds.add(o["id"].toString())) problems.add("$where: id repetido")
                    val text = (o["text"] as? List<*>) ?: emptyList<Any>()
                    if (text.size < 6 || text.size > 10) problems.add("$where: ${text.size} frases (se pidio 6-10)")
                    val preguntas = (o["preguntas"] as? List<*>) ?: emptyList<Any>()
                    if (preguntas.size != 3) problems.add("$where: hacen falta 3 preguntas")
                    preguntas.forEachIndexed { qi, q ->
                        val qm = q as Map<*, *>
                        val opts = (qm["options"] as? List<*>)?.map { it.toString() } ?: emptyList()
                        if (opts.size != 3) problems.add("$where, pregunta ${qi + 1}: 3 opciones")
                        if (opts.toSet().size != opts.size) problems.add("$where, pregunta ${qi + 1}: opciones repetidas")
                        if (qm["answer"].toString() !in opts) problems.add("$where, pregunta ${qi + 1}: answer fuera de options")
                    }
                    val retell = o["retell"] as? Map<*, *>
                    if (retell == null || retell["prompt_es"].toString().isBlank()) problems.add("$where: falta el retell (es la mitad del efecto)")
                    if (((retell?.get("pistas") as? List<*>)?.size ?: 0) < 2) problems.add("$where: el retell necesita al menos 2 pistas")
                    val glos = (o["glosario"] as? List<*>) ?: emptyList<Any>()
                    if (glos.size < 2) problems.add("$where: al menos 2 palabras de glosario")
                    glos.forEach { g ->
                        val gm = g as Map<*, *>
                        val en = gm["en"]?.toString().orEmpty(); val es = gm["es"]?.toString().orEmpty()
                        if (en.isBlank() || es.isBlank()) problems.add("$where: glosario sin \"en\" o sin \"es\"")
                        glosarioPalabras.add(where to en)
                    }
                }
            }
        }

        // Oido (pares minimos) y dictado de numeros (Cowork, 17-09): opcionales; si estan, como validar_extra.py.
        val oidoFile = File(contentDir, "oido.json")
        if (oidoFile.exists()) {
            val oido = slurper.parse(oidoFile) as Map<*, *>
            val bIds = HashSet<String>()
            ((oido["bloques"] as? List<*>) ?: emptyList<Any>()).forEachIndexed { i, b ->
                val o = b as Map<*, *>
                val where = "oido.json, bloque ${i + 1}"
                for (key in listOf("id", "title", "level", "contraste", "explicacion", "hablar")) {
                    if (o[key] == null || o[key].toString().isBlank()) problems.add("$where: falta \"$key\"")
                }
                if (o["id"] != null && !bIds.add(o["id"].toString())) problems.add("$where: id repetido")
                checkSound(o, where)
                glosarioPalabras.add(where to o["hablar"].toString())
                val pares = (o["pares"] as? List<*>) ?: emptyList<Any>()
                if (pares.size < 4 || pares.size > 10) problems.add("$where: ${pares.size} pares (se pidio 6-10; minimo 4 mientras Cowork repone los descartados por oir_oido.py)")
                val vistos = HashSet<String>()
                pares.forEachIndexed { j, p ->
                    val pm = p as? Map<*, *>
                    val a = pm?.get("a")?.toString()?.trim().orEmpty(); val bb = pm?.get("b")?.toString()?.trim().orEmpty()
                    val frase = pm?.get("frase")?.toString().orEmpty()
                    if (a.isBlank() || bb.isBlank() || a.equals(bb, true) || a.contains(' ') || bb.contains(' ')) problems.add("$where, par ${j + 1}: mal formado (\"$a\" / \"$bb\")")
                    if (!vistos.add(a.lowercase() + "/" + bb.lowercase())) problems.add("$where: par repetido $a/$bb")
                    if (frase.split("___").size != 2) problems.add("$where, par $a/$bb: la frase necesita exactamente un ___")
                    // Las dos palabras y la frase las DICE la profesora: en cmudict, o la voz las inventa.
                    glosarioPalabras.add("$where, par $a/$bb" to "$a $bb " + frase.replace("___", " "))
                }
            }
        }
        val dictadoFile = File(contentDir, "dictado.json")
        if (dictadoFile.exists()) {
            val dictado = slurper.parse(dictadoFile) as Map<*, *>
            val dIds = HashSet<String>()
            ((dictado["items"] as? List<*>) ?: emptyList<Any>()).forEachIndexed { i, it ->
                val o = it as Map<*, *>
                val where = "dictado.json, item ${i + 1}"
                for (key in listOf("id", "level", "tipo", "modo", "audio", "tip", "answer", "pregunta_es")) {
                    if (o[key] == null || o[key].toString().isBlank()) problems.add("$where: falta \"$key\"")
                }
                if (o["id"] != null && !dIds.add(o["id"].toString())) problems.add("$where: id repetido")
                val audio = o["audio"]?.toString().orEmpty()
                if (audio.any { c -> c.isDigit() }) problems.add("$where: el audio lleva cifras (escribirlo en palabras para que Piper lo lea como se dice)")
                // El audio se dice en voz alta: sus palabras (sin las letras sueltas del deletreo) en cmudict.
                glosarioPalabras.add(where to audio.split(Regex("[\\s,.!?;:]+")).filter { w -> w.length > 1 }.joinToString(" "))
                when (o["modo"]) {
                    // (que ningun accept repita la respuesta con la regla estricta lo revisa
                    // Content.parseDictado al cargar, y DictadoTest sobre el archivo del repo)
                    "escribir" -> ((o["accept"] as? List<*>) ?: emptyList<Any>()).forEach { a ->
                        if (a.toString().isBlank()) problems.add("$where: accept vacio")
                    }
                    "elegir" -> {
                        val opts = (o["options"] as? List<*>)?.map { x -> x.toString() } ?: emptyList()
                        if (opts.size != 3 || opts.toSet().size != 3) problems.add("$where: hacen falta 3 opciones distintas")
                        if (o["answer"].toString() !in opts) problems.add("$where: answer fuera de options")
                    }
                    else -> problems.add("$where: modo \"${o["modo"]}\" (escribir o elegir)")
                }
            }
        }

        // Fichas de referencia de la seccion Gramatica (opcional; las escribe Cowork): title, body y trap no vacios, ids unicos.
        val refFile = File(contentDir, "referencia.json")
        if (refFile.exists()) {
            val ref = slurper.parse(refFile) as Map<*, *>
            val rIds = HashSet<String>()
            ((ref["fichas"] as? List<*>) ?: run { problems.add("referencia.json: falta \"fichas\""); emptyList<Any>() }).forEachIndexed { i, f ->
                val o = f as Map<*, *>
                val where = "referencia.json, ficha ${i + 1}"
                for (key in listOf("id", "title", "body", "trap")) {
                    if (o[key] == null || o[key].toString().isBlank()) problems.add("$where: falta \"$key\"")
                }
                if (o["id"] != null && !rIds.add(o["id"].toString())) problems.add("$where: id repetido")
            }
        }

        // Crucigramas (Cowork, 17-09): como validar_cruci.py. Cada uno sale de UN banco y la pista es el "es" del banco.
        val cruciFile = File(contentDir, "crucigramas.json")
        if (cruciFile.exists()) {
            val cruci = slurper.parse(cruciFile) as Map<*, *>
            val cIds = HashSet<String>()
            ((cruci["crucigramas"] as? List<*>) ?: emptyList<Any>()).forEachIndexed { i, c ->
                val o = c as Map<*, *>
                val where = "crucigramas.json, ${o["id"] ?: "crucigrama ${i + 1}"}"
                for (key in listOf("id", "title", "level", "banco")) {
                    if (o[key] == null || o[key].toString().isBlank()) problems.add("$where: falta \"$key\"")
                }
                if (o["id"] != null && !cIds.add(o["id"].toString())) problems.add("$where: id repetido")
                val filas = (o["filas"] as? Number)?.toInt() ?: 0
                val columnas = (o["columnas"] as? Number)?.toInt() ?: 0
                if (filas !in 2..10 || columnas !in 2..10) problems.add("$where: rejilla de $filas x $columnas (como mucho 10 x 10)")
                val es = bancoEs[o["banco"]?.toString()]
                if (vocabFile.exists() && es == null) problems.add("$where: banco \"${o["banco"]}\" inexistente en vocabulario.json")
                val palabras = (o["palabras"] as? List<*>) ?: emptyList<Any>()
                if (palabras.size < 5 || palabras.size > 8) problems.add("$where: ${palabras.size} palabras (se pidio 5-8)")
                val rejilla = HashMap<Pair<Int, Int>, Char>()
                val inicios = HashMap<Pair<Int, Int>, Int>()
                val ens = HashSet<String>()
                val esperadas = HashSet<String>()
                for (p in palabras) {
                    val pm = p as Map<*, *>
                    val en = pm["en"]?.toString()?.trim().orEmpty()
                    val dir = pm["dir"]?.toString()?.trim()?.uppercase().orEmpty()
                    val fila = (pm["fila"] as? Number)?.toInt() ?: -1
                    val col = (pm["col"] as? Number)?.toInt() ?: -1
                    val numero = (pm["numero"] as? Number)?.toInt() ?: 0
                    if (en.isBlank() || !en.all { ch -> ch.isLetter() } || dir !in listOf("H", "V")) { problems.add("$where: palabra mal formada (\"$en\", dir \"$dir\")"); continue }
                    if (!ens.add(en.lowercase())) problems.add("$where: palabra repetida \"$en\"")
                    if (es != null && es[en] != pm["pista"]?.toString()?.trim()) problems.add("$where: la pista de \"$en\" no coincide con el banco (\"${pm["pista"]}\" frente a \"${es[en]}\")")
                    for (k in en.indices) {
                        val cell = if (dir == "H") fila to col + k else fila + k to col
                        if (cell.first !in 0 until filas || cell.second !in 0 until columnas) { problems.add("$where: \"$en\" se sale de la rejilla"); break }
                        val ch = en[k].lowercaseChar()
                        val previa = rejilla[cell]
                        if (previa != null && previa != ch) problems.add("$where: choque de letras en $cell (\"$en\")")
                        rejilla[cell] = ch
                    }
                    val ini = fila to col
                    if (inicios[ini]?.let { n -> n != numero } == true) problems.add("$where: numeracion inconsistente en $ini")
                    inicios[ini] = numero
                    esperadas.add("${en.lowercase()}@${fila},${col}$dir")
                }
                // numeracion clasica
                inicios.keys.sortedWith(compareBy({ it.first }, { it.second })).forEachIndexed { k, cell ->
                    if (inicios[cell] != k + 1) problems.add("$where: numeracion no clasica ($cell debia ser ${k + 1})")
                }
                // corridas de >= 2 letras == palabras (sin fantasmas)
                val corridas = HashSet<String>()
                for ((cell, _) in rejilla) {
                    for (h in listOf(true, false)) {
                        val antes = if (h) cell.first to cell.second - 1 else cell.first - 1 to cell.second
                        if (antes in rejilla) continue
                        val sb = StringBuilder(); var cur = cell
                        while (cur in rejilla) { sb.append(rejilla[cur]); cur = if (h) cur.first to cur.second + 1 else cur.first + 1 to cur.second }
                        if (sb.length >= 2) corridas.add("$sb@${cell.first},${cell.second}${if (h) "H" else "V"}")
                    }
                }
                if (corridas != esperadas) problems.add("$where: corridas que no son palabras (fantasmas) o palabras sin corrida: ${(corridas - esperadas) + (esperadas - corridas)}")
                // conectado
                if (rejilla.isNotEmpty()) {
                    val vistos = HashSet<Pair<Int, Int>>()
                    val pila = ArrayDeque<Pair<Int, Int>>(); pila.add(rejilla.keys.first())
                    while (pila.isNotEmpty()) {
                        val z = pila.removeLast()
                        if (!vistos.add(z)) continue
                        for (n in listOf(z.first + 1 to z.second, z.first - 1 to z.second, z.first to z.second + 1, z.first to z.second - 1)) if (n in rejilla) pila.add(n)
                    }
                    if (vistos.size != rejilla.size) problems.add("$where: rejilla no conectada")
                }
            }
        }

        // Modo Aptis (etapa 5): los ids de tarea son unicos entre TODOS los archivos aptis-*.json (Lector en Aptis.kt).
        val tareaIds = HashSet<String>()
        val nivelesItem = setOf("A1", "A2", "B1", "B2", "C1")

        // Las reglas de las pistas: promocion "N de M" y tamano de ronda por pista.
        val pistasFile = File(contentDir, "aptis-pistas.json")
        if (pistasFile.exists()) {
            val cfg = slurper.parse(pistasFile) as Map<*, *>
            val promo = cfg["promocion"] as? Map<*, *>
            val ronda = cfg["ronda"] as? Map<*, *>
            if (promo == null) problems.add("aptis-pistas.json: falta \"promocion\"")
            if (ronda == null) problems.add("aptis-pistas.json: falta \"ronda\"")
            for (id in listOf("core", "reading", "listening", "writing", "speaking")) {
                val texto = promo?.get(id)?.toString().orEmpty()
                if (!Regex("^\\s*\\d+ de \\d+\\s*$").matches(texto)) problems.add("aptis-pistas.json: promocion.$id tiene que ser \"N de M\" (dice \"$texto\")")
                if (((ronda?.get(id) as? Number)?.toInt() ?: 0) <= 0) problems.add("aptis-pistas.json: falta ronda.$id")
            }
        }

        // El banco del Core de Cowork (dos archivos), como validar_core.py.
        for (nombre in listOf("aptis-core-gramatica.json", "aptis-core-vocabulario.json")) {
            val f = File(contentDir, nombre)
            if (!f.exists()) continue
            val banco = slurper.parse(f) as Map<*, *>
            val kind = banco["kind"]?.toString()
            ((banco["items"] as? List<*>) ?: emptyList<Any>()).forEachIndexed { i, it ->
                val im = it as Map<*, *>
                val where = "$nombre, item ${i + 1}"
                val id = im["id"]?.toString().orEmpty()
                if (id.isBlank()) problems.add("$where: falta \"id\"") else if (!tareaIds.add(id)) problems.add("$where: id repetido \"$id\"")
                if (im["level"]?.toString() !in nivelesItem) problems.add("$where: level \"${im["level"]}\" no es A1, A2, B1, B2 ni C1")
                val opts = (im["options"] as? List<*>)?.map { it.toString() } ?: emptyList()
                if (opts.size != 3) problems.add("$where: Aptis usa 3 opciones, tiene ${opts.size}")
                if (opts.map { it.trim().lowercase() }.toSet().size != opts.size) problems.add("$where: opciones repetidas")
                if (im["answer"]?.toString() !in opts) problems.add("$where: answer fuera de options")
                if (im["why"]?.toString().isNullOrBlank()) problems.add("$where: falta \"why\" (la explicacion en espanol)")
                if (kind == "grammar") {
                    if (im["text"].toString().split("___").size != 2) problems.add("$where: exactamente un hueco ___")
                    if (im["point"]?.toString().isNullOrBlank()) problems.add("$where: falta \"point\"")
                } else {
                    val sub = im["sub"]?.toString()
                    if (sub !in setOf("synonym", "definition", "usage", "collocation")) problems.add("$where: sub invalido \"$sub\"")
                    if (sub == "usage" && im["text"].toString().split("___").size != 2) problems.add("$where: usage necesita un hueco ___")
                    if (sub != "usage" && im["prompt"]?.toString().isNullOrBlank()) problems.add("$where: falta \"prompt\"")
                }
            }
        }

        fun strs(x: Any?): List<String> = (x as? List<*>)?.map { it.toString() } ?: emptyList()
        fun idNuevo(where: String, id: Any?) {
            val s = id?.toString().orEmpty()
            if (s.isBlank()) problems.add("$where: falta \"id\"") else if (!tareaIds.add(s)) problems.add("$where: id repetido \"$s\"")
        }
        fun nivel(where: String, o: Map<*, *>) {
            if (o["level"]?.toString() !in nivelesItem) problems.add("$where: level \"${o["level"]}\" no es A1, A2, B1 ni B2")
        }
        fun opciones(where: String, o: Map<*, *>): List<String> {
            val opts = strs(o["options"])
            if (opts.size < 2) problems.add("$where: hacen falta al menos 2 opciones")
            if (opts.toSet().size != opts.size) problems.add("$where: opciones repetidas")
            if (o["answer"]?.toString() !in opts) problems.add("$where: answer fuera de options")
            return opts
        }
        fun hueco(where: String, text: Any?) {
            if (text.toString().split("___").size != 2) problems.add("$where: el texto necesita exactamente un hueco ___")
        }
        // Una tarea de pista o de simulacro, segun la destreza (como Lector en Aptis.kt).
        fun tareaDe(pista: String, where: String, tm: Map<*, *>) {
            idNuevo(where, tm["id"]); nivel(where, tm)
            when (pista) {
                "core" -> { hueco(where, tm["text"]); opciones(where, tm) }
                "reading" -> when (tm["tipo"]?.toString()) {
                    "completar" -> { hueco(where, tm["text"]); opciones(where, tm) }
                    "ordenar" -> {
                        if (tm["primera"]?.toString().isNullOrBlank()) problems.add("$where: falta \"primera\"")
                        val des = strs(tm["desordenadas"]); val orden = strs(tm["orden"])
                        if (des.size < 2) problems.add("$where: hacen falta al menos 2 frases desordenadas")
                        if (des.toSet().size != des.size) problems.add("$where: frases repetidas")
                        if (orden.sorted() != des.sorted()) problems.add("$where: \"orden\" no es una permutacion de \"desordenadas\"")
                    }
                    "titulos" -> {
                        val parrafos = strs(tm["parrafos"]); val titulos = strs(tm["titulos"]); val answer = strs(tm["answer"])
                        if (parrafos.size < 2) problems.add("$where: hacen falta al menos 2 parrafos")
                        if (titulos.size <= parrafos.size) problems.add("$where: tiene que sobrar al menos un titulo")
                        if (titulos.toSet().size != titulos.size) problems.add("$where: titulos repetidos")
                        if (answer.size != parrafos.size) problems.add("$where: \"answer\" necesita un titulo por parrafo")
                        if (answer.toSet().size != answer.size || answer.any { it !in titulos }) problems.add("$where: \"answer\" con titulos repetidos o fuera de \"titulos\"")
                    }
                    else -> problems.add("$where: tipo desconocido \"${tm["tipo"]}\"")
                }
                "listening" -> {
                    opciones(where, tm)
                    for (key in listOf("audio", "pregunta")) if (tm[key]?.toString().isNullOrBlank()) problems.add("$where: falta \"$key\"")
                    val audio = tm["audio"]?.toString().orEmpty()
                    // Piper lee las cifras a su manera ("1920" no es "nineteen twenty"): el audio va en palabras.
                    if (audio.any { c -> c.isDigit() }) problems.add("$where: el audio lleva cifras (escribirlo en palabras)")
                    if (tm["tipo"] == "quien" && !(audio.contains("MAN:") && audio.contains("WOMAN:"))) problems.add("$where: una tarea \"quien\" necesita MAN: y WOMAN: en el audio")
                }
                "writing", "speaking" -> {
                    for (idioma in listOf("en", "es")) {
                        val camel = "prompt" + idioma.replaceFirstChar { it.uppercase() }
                        if (tm["prompt_$idioma"]?.toString().isNullOrBlank() && tm[camel]?.toString().isNullOrBlank()) problems.add("$where: falta \"prompt_$idioma\"")
                    }
                    if (strs(tm["rubrica"]).isEmpty()) problems.add("$where: falta la rubrica")
                    val seg = if (pista == "writing") tm["segundos"] else (tm["hablar_seg"] ?: tm["hablarSeg"])
                    if (((seg as? Number)?.toInt() ?: 0) <= 0) problems.add("$where: falta " + (if (pista == "writing") "\"segundos\"" else "\"hablar_seg\""))
                    // Las fotos de Speaking (partes 2 y 3), cuando esten: en assets, .webp/.jpg, <= 400 KB; la parte 3 lleva dos.
                    if (pista == "speaking") {
                        val imagenes = strs(tm["imagenes"])
                        for (img in imagenes) {
                            val f = File(assetsDir, img)
                            if (!Regex("^images/[a-z0-9_/-]+\\.(webp|jpg)$").matches(img)) problems.add("$where: imagen \"$img\" (ruta dentro de assets, .webp o .jpg)")
                            else if (!f.exists()) problems.add("$where: falta la imagen assets/$img")
                            else if (f.length() > 400 * 1024) problems.add("$where: la imagen $img pesa ${f.length() / 1024} KB (tope 400)")
                        }
                        val parte3 = where.contains("parte 3")
                        if (imagenes.isNotEmpty() && parte3 && imagenes.size != 2) problems.add("$where: la parte 3 (comparar) lleva dos imagenes")
                        if (imagenes.isNotEmpty() && !parte3 && imagenes.size != 1) problems.add("$where: una sola imagen")
                    }
                }
            }
        }

        // Los bancos de las pistas de Cowork: aptis-<pista>.json, planos ("tareas") o por "partes" del examen.
        // La promocion "4 de 5" (reading, listening) necesita al menos 5 tareas por nivel; "2 de 3" (writing, speaking), 3.
        for (pista in listOf("reading", "listening", "writing", "speaking")) {
            val f = File(contentDir, "aptis-$pista.json")
            if (!f.exists()) continue
            val banco = slurper.parse(f) as Map<*, *>
            val partes = banco["partes"] as? List<*>
            val porNivel = HashMap<String, Int>()
            fun cuenta(t: Map<*, *>) { t["level"]?.toString()?.let { porNivel[it] = (porNivel[it] ?: 0) + 1 } }
            if (partes != null) partes.forEachIndexed { pi, parte ->
                val pm = parte as Map<*, *>
                val whereP = "aptis-$pista.json, parte ${pi + 1}"
                if (pm["aptis"]?.toString().isNullOrBlank() && pm["title"]?.toString().isNullOrBlank()) problems.add("$whereP: falta \"aptis\" o \"title\"")
                ((pm["tareas"] as? List<*>) ?: emptyList<Any>()).forEachIndexed { i, t -> tareaDe(pista, "$whereP, tarea ${i + 1}", t as Map<*, *>); cuenta(t as Map<*, *>) }
            } else {
                val tareas = (banco["tareas"] as? List<*>) ?: (banco["items"] as? List<*>) ?: emptyList<Any>()
                if (tareas.isEmpty()) problems.add("aptis-$pista.json: sin \"tareas\"")
                tareas.forEachIndexed { i, t -> tareaDe(pista, "aptis-$pista.json, tarea ${i + 1}", t as Map<*, *>); cuenta(t as Map<*, *>) }
            }
            // Aviso, no error: con menos tareas la ronda las repite y la promocion tarda mas (Writing A1 trae 2).
            val minimo = if (pista in listOf("reading", "listening")) 5 else 3
            for ((nivel, n) in porNivel.toSortedMap()) if (n < minimo) logger.warn("  aviso: aptis-$pista.json: $nivel tiene $n tareas y la promocion pide $minimo; la ronda las repetira")
        }

        // Diagnostico = simulacro del Modo Aptis: opcional; si esta, se revisa como parseDiagnostico (Aptis.kt).
        val diagFile = File(contentDir, "aptis-diagnostico.json")
        if (diagFile.exists()) {
            val diag = slurper.parse(diagFile) as Map<*, *>
            val secIds = HashSet<String>()
            // Los umbrales del Core viven en el JSON ("4 de 5 en A2 y 4 de 5 en B1"): tienen que poder leerse.
            val estimacionCore = ((diag["estimacion"] as? Map<*, *>)?.get("core") as? Map<*, *>)
            if (estimacionCore == null) problems.add("aptis-diagnostico.json: falta \"estimacion.core\"")
            else for (nivel in listOf("A2", "B1", "B2")) {
                val texto = estimacionCore[nivel]?.toString().orEmpty()
                if (!Regex("\\d+ de \\d+ en (A2|B1|B2)").containsMatchIn(texto)) problems.add("aptis-diagnostico.json: estimacion.core.$nivel tiene que decir \"N de M en <nivel>\" (dice \"$texto\")")
            }
            val secciones = (diag["secciones"] as? List<*>) ?: run { problems.add("aptis-diagnostico.json: falta \"secciones\""); emptyList<Any>() }
            secciones.forEachIndexed { si, sec ->
                val o = sec as Map<*, *>
                val whereS = "aptis-diagnostico.json, seccion ${si + 1}"
                val id = o["id"]?.toString().orEmpty()
                if (id.isBlank() || !secIds.add(id)) problems.add("$whereS: id vacio o repetido")
                for (key in listOf("skill", "title")) if (o[key]?.toString().isNullOrBlank()) problems.add("$whereS: falta \"$key\"")
                if (((o["minutos"] as? Number)?.toInt() ?: 0) <= 0) problems.add("$whereS: \"minutos\" tiene que ser mayor que 0")
                when (id) {
                    "core" -> {
                        if (((o["segundos_por_item"] as? Number)?.toInt() ?: 0) <= 0) problems.add("$whereS: falta \"segundos_por_item\"")
                        val items = (o["items"] as? List<*>) ?: emptyList<Any>()
                        val niveles = HashSet<String>()
                        items.forEachIndexed { i, it ->
                            val im = it as Map<*, *>
                            val where = "$whereS, item ${i + 1}"
                            idNuevo(where, im["id"]); nivel(where, im); hueco(where, im["text"]); opciones(where, im)
                            niveles.add(im["level"].toString())
                        }
                        for (l in listOf("A2", "B1", "B2")) if (l !in niveles) problems.add("$whereS: no hay items de $l (la estimacion los necesita)")
                    }
                    "reading" -> {
                        val tareas = (o["tareas"] as? List<*>) ?: emptyList<Any>()
                        if (tareas.isEmpty()) problems.add("$whereS: sin tareas")
                        tareas.forEachIndexed { i, t ->
                            val tm = t as Map<*, *>
                            val where = "$whereS, tarea ${i + 1}"
                            idNuevo(where, tm["id"]); nivel(where, tm)
                            when (tm["tipo"]?.toString()) {
                                "completar" -> { hueco(where, tm["text"]); opciones(where, tm) }
                                "ordenar" -> {
                                    if (tm["primera"]?.toString().isNullOrBlank()) problems.add("$where: falta \"primera\"")
                                    val des = strs(tm["desordenadas"]); val orden = strs(tm["orden"])
                                    if (des.size < 2) problems.add("$where: hacen falta al menos 2 frases desordenadas")
                                    if (des.toSet().size != des.size) problems.add("$where: frases repetidas")
                                    if (orden.sorted() != des.sorted()) problems.add("$where: \"orden\" no es una permutacion de \"desordenadas\"")
                                }
                                "titulos" -> {
                                    val parrafos = strs(tm["parrafos"]); val titulos = strs(tm["titulos"]); val answer = strs(tm["answer"])
                                    if (parrafos.size < 2) problems.add("$where: hacen falta al menos 2 parrafos")
                                    if (titulos.size <= parrafos.size) problems.add("$where: tiene que sobrar al menos un titulo")
                                    if (titulos.toSet().size != titulos.size) problems.add("$where: titulos repetidos")
                                    if (answer.size != parrafos.size) problems.add("$where: \"answer\" necesita un titulo por parrafo")
                                    if (answer.toSet().size != answer.size || answer.any { it !in titulos }) problems.add("$where: \"answer\" con titulos repetidos o fuera de \"titulos\"")
                                }
                                else -> problems.add("$where: tipo desconocido \"${tm["tipo"]}\"")
                            }
                        }
                    }
                    "listening" -> {
                        val tareas = (o["tareas"] as? List<*>) ?: emptyList<Any>()
                        if (tareas.isEmpty()) problems.add("$whereS: sin tareas")
                        tareas.forEachIndexed { i, t ->
                            val tm = t as Map<*, *>
                            val where = "$whereS, tarea ${i + 1}"
                            idNuevo(where, tm["id"]); nivel(where, tm); opciones(where, tm)
                            for (key in listOf("audio", "pregunta")) if (tm[key]?.toString().isNullOrBlank()) problems.add("$where: falta \"$key\"")
                        }
                    }
                    "writing", "speaking" -> {
                        val tareas = (o["tareas"] as? List<*>) ?: emptyList<Any>()
                        if (tareas.size < 2) problems.add("$whereS: la estimacion por IA necesita al menos 2 tareas")
                        tareas.forEachIndexed { i, t ->
                            val tm = t as Map<*, *>
                            val where = "$whereS, tarea ${i + 1}"
                            idNuevo(where, tm["id"]); nivel(where, tm)
                            for (key in listOf("prompt_en", "prompt_es")) if (tm[key]?.toString().isNullOrBlank()) problems.add("$where: falta \"$key\"")
                            if (strs(tm["rubrica"]).isEmpty()) problems.add("$where: falta la rubrica")
                            val seg = if (id == "writing") "segundos" else "hablar_seg"
                            if (((tm[seg] as? Number)?.toInt() ?: 0) <= 0) problems.add("$where: falta \"$seg\"")
                        }
                    }
                    else -> problems.add("$whereS: seccion desconocida \"$id\"")
                }
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
            // El glosario de las historias va al mazo y se dice en voz alta: tiene que estar en cmudict.
            texts.addAll(glosarioPalabras)
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
        versionCode = 18
        versionName = "0.9.9"

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
