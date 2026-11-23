import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firma della release (vedi docs/release.md). Nessun segreto è nel repository: i valori
// arrivano dalle variabili d'ambiente HISTORIA_* (CI) oppure dal file locale
// android/keystore.properties, ignorato da git (modello: keystore.properties.example).
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun releaseSigningValue(environmentName: String, propertyName: String): String? {
    return providers.environmentVariable(environmentName).orNull?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }
}

val releaseStoreFile = releaseSigningValue("HISTORIA_KEYSTORE_FILE", "storeFile")?.let { rootProject.file(it) }
val releaseStorePassword = releaseSigningValue("HISTORIA_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = releaseSigningValue("HISTORIA_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = releaseSigningValue("HISTORIA_KEY_PASSWORD", "keyPassword")
val hasReleaseSigning = releaseStoreFile != null && releaseStorePassword != null &&
    releaseKeyAlias != null && releaseKeyPassword != null

if (providers.gradleProperty("historia.requireReleaseSigning").orNull == "true") {
    if (!hasReleaseSigning) {
        throw GradleException(
            "Firma release non configurata: imposta HISTORIA_KEYSTORE_FILE, HISTORIA_KEYSTORE_PASSWORD, " +
                "HISTORIA_KEY_ALIAS e HISTORIA_KEY_PASSWORD oppure android/keystore.properties."
        )
    }
    if (releaseStoreFile?.isFile != true) {
        throw GradleException("Keystore di firma non trovato: ${releaseStoreFile?.path}")
    }
}

android {
    namespace = "it.historia.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.historia.go"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // Senza credenziali la build produce app-release-unsigned.apk (non installabile).
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Robolectric: SharedPreferences, filesDir e asset reali nei test JVM.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("com.github.mik3y:usb-serial-for-android:3.10.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}
