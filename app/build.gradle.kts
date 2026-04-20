plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.qrgenie.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.qrgenie.app"
        minSdk = 26
        targetSdk = 36
        // NOTE: Play Console rejected upload because versionCode 5 was already used.
        // Bump versionCode when publishing new bundles and update versionName accordingly.
        versionCode = 6 // bumped for Play release
        versionName = "1.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Only attach a signing config if a keystore actually exists (either via
            // KEYSTORE_FILE env var or the default keystore.jks in project root).
            // This lets us build an unsigned bundle locally when the keystore is not
            // present (useful for diagnostics and CI-free checks). The signing config
            // itself is created/configured later in this file when possible.
            val envKs = System.getenv("KEYSTORE_FILE")
            val ksExists = (envKs != null && file(envKs).exists()) || rootProject.file("keystore.jks").exists()
            if (ksExists) {
                signingConfig = signingConfigs.findByName("release")
            }
            // Populate signing properties from environment variables if present (used by CI)
            // These will be read by the signing config created above.
            // Note: CI will write the keystore file and set KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD env vars.
            // We configure the signingConfig below in the 'signingConfigs' block.
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Signing config: if environment variables are set (CI), use them; otherwise the build will continue unsigned.
    // Avoid creating the 'release' SigningConfig twice (some environments/plugins may pre-create it).
    val ksPath = System.getenv("KEYSTORE_FILE") ?: rootProject.file("keystore.jks").path
    val ksPassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
    val ksAlias = System.getenv("KEY_ALIAS") ?: ""
    val ksKeyPassword = System.getenv("KEY_PASSWORD") ?: ""

    val existingReleaseConfig = signingConfigs.findByName("release")
    if (existingReleaseConfig == null) {
        signingConfigs.create("release") {
            storeFile = file(ksPath)
            storePassword = ksPassword
            keyAlias = ksAlias
            keyPassword = ksKeyPassword
        }
    } else {
        // Configure existing release signing config with environment-provided values (safe no-op if empty)
        existingReleaseConfig.storeFile = file(ksPath)
        existingReleaseConfig.storePassword = ksPassword
        existingReleaseConfig.keyAlias = ksAlias
        existingReleaseConfig.keyPassword = ksKeyPassword
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.6.0"
    }
}

dependencies {

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // QR generation
    implementation("com.google.zxing:core:3.5.4")

// QR scanning (use Play Services ML Kit artifact which is available in cache)
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")

// CameraX
    implementation("androidx.camera:camera-core:1.3.0")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")

    // Kotlin coroutines for .await()
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Provide Guava (ListenableFuture) which some ML Kit / Play services native
    // artifacts may reference. Use Android variant to reduce size impact.
    implementation("com.google.guava:guava:31.1-android")

        // Add this line
        implementation("androidx.compose.material:material-icons-extended:1.7.0")

    // Auto Update
        implementation("com.google.android.play:app-update:2.1.0")
        implementation("com.google.android.play:app-update-ktx:2.1.0")

    // Firebase Cloud Messaging (optional - requires google-services.json and Firebase setup)
    implementation("com.google.firebase:firebase-messaging:23.2.0")
    }





