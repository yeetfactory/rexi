import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Lets the add and edit forms keep what was typed when Android rebuilds the screen, such as when the phone turns.
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
}

/** Signing secrets live outside version control. Without them a release build is left unsigned. */
val keystore =
    Properties().apply {
        rootProject
            .file("keystore.properties")
            .takeIf { it.exists() }
            ?.inputStream()
            ?.use { load(it) }
    }

android {
    namespace = "top.yeetfactory.rexi"
    compileSdk = 37

    defaultConfig {
        applicationId = "top.yeetfactory.rexi"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1"
    }

    signingConfigs {
        if (keystore.isNotEmpty()) {
            create("release") {
                storeFile = file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        // Whether a newer library exists depends on the date, not on this code, so report it without failing.
        informational += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Lint analyses in the background while Gradle moves on to other tasks. In `check` that includes the release KSP
// task, which rewrites its generated files, and lint once failed reading one of them mid-rewrite. Waiting for every
// KSP task in the same build closes that window, at the cost of a few seconds of overlap.
val kspTasks = tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") }
tasks.matching { it.name.startsWith("lintAnalyze") || it.name.startsWith("lintVitalAnalyze") }.configureEach {
    mustRunAfter(kspTasks)
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.core.ktx)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.glance.appwidget)
    implementation(libs.biometric)
    implementation(libs.fragment)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.coroutines.test)
}
