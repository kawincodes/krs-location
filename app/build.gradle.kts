import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing: values come from keystore.properties (local) or environment variables (CI).
// When neither is present the release build falls back to the debug key so it stays installable.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(name: String): String? = keystoreProps.getProperty(name) ?: System.getenv(name)
val releaseKeystore = signingValue("KEYSTORE_FILE")?.let { rootProject.file(it) }?.takeIf { it.exists() }

// Google Maps SDK key: local.properties (MAPS_API_KEY=...) or the MAPS_API_KEY environment
// variable in CI. Without it the app still works with OpenStreetMap and hides Google Maps.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val mapsApiKey: String = (localProps.getProperty("MAPS_API_KEY") ?: System.getenv("MAPS_API_KEY") ?: "").trim()

android {
    namespace = "com.krstechsolutions.krslocation"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.krstechsolutions.krslocation"
        minSdk = 26
        targetSdk = 35
        // CI passes -PversionName / -PversionCode from the git tag and run number.
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 4
        versionName = (project.findProperty("versionName") as String?) ?: "1.2.0"

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = signingValue("KEYSTORE_PASSWORD")
                keyAlias = signingValue("KEY_ALIAS")
                keyPassword = signingValue("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (releaseKeystore != null) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("No release keystore configured; signing release with the debug key.")
                signingConfigs.getByName("debug")
            }
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
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    // Jetpack Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Lucide icons as Compose ImageVectors.
    implementation("com.composables:icons-lucide:1.1.0")

    // OpenStreetMap based map view. No API key required.
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // Google Maps (optional at runtime; needs MAPS_API_KEY).
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.maps.android:maps-compose:6.4.1")

    testImplementation("junit:junit:4.13.2")
}
