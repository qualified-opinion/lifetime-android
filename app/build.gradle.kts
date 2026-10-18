plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.lifetime"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.lifetime"
        minSdk = 26
        targetSdk = 35
        // The release workflow sets these; local and debug builds keep the defaults.
        versionCode = System.getenv("LIFETIME_VERSION_CODE")?.toInt() ?: 1
        versionName = System.getenv("LIFETIME_VERSION_NAME") ?: "0.1.0"
    }

    // Release signing comes only from the environment (GitHub secrets), never from the repo.
    // Without it, assembleRelease still builds an unsigned APK.
    val releaseKeystore = System.getenv("LIFETIME_KEYSTORE_PATH")
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("LIFETIME_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LIFETIME_KEY_ALIAS")
                keyPassword = System.getenv("LIFETIME_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            // Signed releases ship without R8 for now. CI builds a minified release with
            // -Plifetime.minify=true so shrinking problems show up before it is switched on.
            isMinifyEnabled = providers.gradleProperty("lifetime.minify").orNull == "true"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    lint {
        // CI runs lintDebug: errors fail the build, warnings are listed in the text report.
        abortOnError = true
        textReport = true
    }
    testOptions {
        // Lets JVM tests touch android.* stubs (e.g. Log) without crashing.
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    implementation(libs.androidx.exifinterface)

    // App lock: one prompt for fingerprint/face or the phone's PIN on Android 8 to 15.
    // BiometricPrompt needs a FragmentActivity; pin a current fragment over biometric's old one.
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)

    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    testImplementation(libs.junit)
    // Exercise actual Compose gestures and document/EXIF reads in JVM CI without an emulator.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
