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
        // Incremented for next Play release
        versionCode = 7 // bumped for Play release
        versionName = "1.0.5"

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
    // Ensure we can add a small native shim and exclude offending vendor .so files
    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
        }
    }

    // Exclude known mis-aligned vendor native libraries so we can ship them from assets
    // and provide a shim in lib/ that loads them at runtime. Use jniLibs.excludes (non-deprecated API).
    // Use glob patterns that are compatible with the Android Gradle Plugin incremental packager.
    packaging {
        jniLibs {
            // Use recursive globbing to match any ABI folder (e.g. arm64-v8a, armeabi-v7a)
            excludes += listOf(
                "**/libimage_processing_util_jni.so",
                "**/libbarhopper_v3.so"
            )
        }
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

    // QR scanning (use Play Services ML Kit). Also try direct ML Kit artifact versions to test native libs.
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    // Try direct ML Kit versions (fallback) to see if different ML Kit releases include fixed native libs
    // Removed direct ML Kit artifact because the specific version wasn't available from configured repositories
    // Use Play Services ML Kit artifact above (play-services-mlkit-barcode-scanning:18.3.1) which is published.

// CameraX (test additional versions to find a build with 16KB-aligned native libs)
    // Use CameraX 1.6.0 which is available in the configured repositories and matches bundled test artifacts
    implementation("androidx.camera:camera-core:1.6.0")
    implementation("androidx.camera:camera-camera2:1.6.0")
    implementation("androidx.camera:camera-lifecycle:1.6.0")
    implementation("androidx.camera:camera-view:1.6.0")

    // Kotlin coroutines for .await()
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Provide Guava (ListenableFuture) which some ML Kit / Play services native
    // artifacts may reference. Use Android variant to reduce size impact.
    implementation("com.google.guava:guava:31.1-android")

        // Add this line
        implementation("androidx.compose.material:material-icons-extended:1.7.0")

    // (history storage will use file-based storage; keep build without kapt)

    // Auto Update
        implementation("com.google.android.play:app-update:2.1.0")
        implementation("com.google.android.play:app-update-ktx:2.1.0")

    // Firebase Cloud Messaging (optional - requires google-services.json and Firebase setup)
    implementation("com.google.firebase:firebase-messaging:23.2.0")
    }

// Prepare native assets (copy offending .so from Gradle cache into app assets) before build
tasks.register("prepareNativeAssets") {
    doLast {
        try {
            exec {
                // Run the helper script in tools/ to locate the vendor .so files in the Gradle cache
                // and copy them into app/src/main/assets/native/<abi>/
                commandLine("python", "${project.rootDir.path.replace('\\','/')}/tools/move_native_to_assets.py", project.projectDir.path)
            }
        } catch (e: Exception) {
            logger.warn("prepareNativeAssets failed: ${'$'}e")
        }
    }
}

// Ensure native assets are prepared before packaging
tasks.named("preBuild").configure {
    dependsOn("prepareNativeAssets")
}





