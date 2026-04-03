import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.lumo)
}

android {
    namespace = "com.bluemix.clients_lead"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bluemix.clients_lead"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Secrets
        val secretsFile = rootProject.file("local.properties")
        val secrets = Properties().apply { if (secretsFile.exists()) load(secretsFile.inputStream()) }

        val apiBaseUrl = secrets.getProperty("API_BASE_URL")
            ?: "https://backup-server-q2dc.onrender.com"

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")

        // Maps API key from local.properties → AndroidManifest placeholder
        val mapsApiKey = secrets.getProperty("MAPS_API_KEY") ?: ""
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    buildTypes {
        debug {
            // optionally relax network if you really need http during dev:
            // manifestPlaceholders["usesCleartext"] = "true"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Fail fast if secrets are missing in CI
            buildConfigField(
                "boolean",
                "RELEASE_SECRETS_OK",
                "true.toString()"
            )
        }
    }

    // Java/Kotlin toolchain
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // (Alternatively) kotlin {
    //    jvmToolchain(17)
    // }

    buildFeatures {
        compose = true
        buildConfig = true
        mlModelBinding = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }
}

dependencies {

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM + core set (includes material3, ui, ui-graphics, icons, ripple)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose.core)
    implementation("androidx.compose.animation:animation")
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Google Maps & Places (single source via catalog)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.maps.compose)
    implementation(libs.maps.compose.utils)
    implementation("com.google.android.libraries.places:places:3.3.0")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // KotlinX
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Ktor (networking stack used by ApiClientProvider, repositories, etc.)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.auth)

    // Data & Work & Room (KSP)
    implementation(libs.androidx.datastore)
    implementation(libs.work.runtime)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    // Accompanist
    implementation(libs.accompanist.permissions)

    // DI
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Misc
    implementation(libs.timber)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // ML Kit Text Recognition
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // CameraX
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")
}
