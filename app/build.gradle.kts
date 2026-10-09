import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// google-services.json is developer-supplied (README step 4) and gitignored.
// The build still compiles without it; the app then shows a "Firebase not configured" screen.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    // Crash reports go to Firebase Crashlytics (needs google-services.json too).
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
}

// Secrets: local.properties first, then environment variables. Never hardcode keys.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(name: String): String =
    (localProps.getProperty(name) ?: System.getenv(name) ?: "").trim()

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.greenlight.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.greenlight.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1 // replaced per build below (buildMinute)
        versionName = "1.5.2"

        buildConfigField("String", "TMDB_API_KEY", "\"${secret("TMDB_API_KEY")}\"")
        buildConfigField("String", "PLACES_API_KEY", "\"${secret("PLACES_API_KEY")}\"")
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("Boolean", "DEV_TOOLS", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("Boolean", "DEV_TOOLS", "false")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)

    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.play.services.location)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

/**
 * versionCode = minutes since 2020-01-01, so every build is numbered higher than the last.
 * Admin → App → "Block versions older than this one" relies on that. A ValueSource is used so
 * it's recomputed on every build even with the configuration cache on.
 */
abstract class BuildMinute : ValueSource<Int, ValueSourceParameters.None> {
    override fun obtain(): Int = ((System.currentTimeMillis() - 1_577_836_800_000L) / 60_000).toInt()
}
val buildMinute = providers.of(BuildMinute::class) {}
androidComponents {
    onVariants { variant -> variant.outputs.forEach { it.versionCode.set(buildMinute) } }
}
