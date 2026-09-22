import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp) // genera el código de Room a partir de las anotaciones
}

// Room guarda aquí un JSON con el esquema de cada versión de la base de datos (va a Git):
// sirve para revisar cambios y escribir migraciones, como las de Supabase.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Las claves de Supabase viven en local.properties (no se sube a Git):
//   supabase.url=https://xxxx.supabase.co
//   supabase.key=sb_publishable_xxxx   <- clave pública/anon, NUNCA la service_role
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

// Datos de la clave de firma para la versión de publicación. Van en keystore.properties, que NO
// se sube a Git (ver .gitignore). Sin ese archivo, la app sigue compilando en modo depuración.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "com.example.exotube"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.exotube"
        minSdk = 26
        targetSdk = 37
        // versionCode es el número que compara Android para saber si una versión es más nueva:
        // sube de uno en uno y nunca se repite. versionName es solo lo que ve el usuario.
        versionCode = 4
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${localProperties.getProperty("supabase.url", "")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${localProperties.getProperty("supabase.key", "")}\"")
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // v2: la firma que comprueban los teléfonos actuales.
                // v3: además permite cambiar la clave en el futuro sin perder a los usuarios.
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // R8 desactivado: yt-dlp y Room usan reflexión, y habría que escribir reglas "keep"
            // para que no se borre código que sí se usa. Sin ofuscar, el APK es igual de funcional.
            optimization {
                enable = false
            }
            // Si no hay keystore.properties, el APK sale sin firmar (no se puede instalar).
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // youtubedl-android incluye Python + FFmpeg por arquitectura (~30 MB cada una).
    // Generamos un APK por ABI para no distribuir un APK gigante con todas.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        jniLibs {
            // yt-dlp se ejecuta como binario nativo: Android debe extraer las .so a disco
            // (equivale a android:extractNativeLibs="true" en el manifest).
            useLegacyPackaging = true
            // Python y FFmpeg vienen como .zip renombrados a .so: no se pueden "strippear".
            keepDebugSymbols += listOf("**/libpython.zip.so", "**/libffmpeg.zip.so")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // NewPipeExtractor usa partes de Java (java.nio.file) que Android solo trae desde la 13:
        // el "desugaring" las incluye en la app para los teléfonos más viejos.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // --- UI: Jetpack Compose + Material 3 ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)

    // --- Arquitectura: ViewModel + ciclo de vida ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // --- Asincronía ---
    implementation(libs.kotlinx.coroutines.android)

    // --- Descargas en segundo plano (Fase 4) ---
    implementation(libs.androidx.work.runtime.ktx)

    // --- Motor de extracción: yt-dlp + FFmpeg (unir video/audio, convertir a MP3) ---
    implementation(libs.youtubedl.android.library)
    implementation(libs.youtubedl.android.ffmpeg)
    // Pide los enlaces de los videos de YouTube desde dentro de la app, sin arrancar Python.
    implementation(libs.newpipe.extractor)
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    // --- Backend: Supabase (Postgrest + Auth anónima) sobre Ktor ---
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // --- Miniaturas (de internet y fotogramas de los videos descargados) ---
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.video)

    // --- Reproductor de música/video ---
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    // Descargar los videos en línea con OkHttp (que ya viaja en la app) para poder elegir por qué IP salen.
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui.compose)

    // --- Playlists (base de datos local) y navegación entre pantallas ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.navigation.compose)

    // --- Tests ---
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
