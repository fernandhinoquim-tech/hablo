import java.io.File
import java.net.URI

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

val sherpaVersion = "1.13.2"

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

val asrPackage = "sherpa-onnx-moonshine-tiny-en-int8"
val asrAssetsDir = File(projectDir, "src/main/assets/asr")

val fetchAsr = tasks.register("fetchAsr") {
    outputs.dir(asrAssetsDir)
    doLast {
        if (File(asrAssetsDir, "tokens.txt").exists()) {
            logger.lifecycle("  reconocimiento de voz ya listo")
            return@doLast
        }
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
            .filter { it.isFile && (it.name.endsWith(".onnx") || it.name == "tokens.txt") }
            .forEach { f ->
                f.copyTo(File(asrAssetsDir, f.name), overwrite = true)
                logger.lifecycle("  asr: ${f.name} (${f.length() / 1024 / 1024} MB)")
            }
    }
}

android {
    namespace = "com.ferolabs.hablo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ferolabs.hablo"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.6"
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
        noCompress += listOf("onnx")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.named("preBuild") {
    dependsOn(fetchSherpaAar, fetchVoices, fetchAsr)
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

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
}
