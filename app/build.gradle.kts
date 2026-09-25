// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlinx.kover")
}
android {
    namespace = "it.marcelpetrick.fork"
    compileSdk = 37
    defaultConfig {
        applicationId = "it.marcelpetrick.fork"
        minSdk = 34
        targetSdk = 37
        versionCode =
            rootProject
                .file("BUILD_NUMBER")
                .readText()
                .trim()
                .toInt()
        versionName = rootProject.file("VERSION").readText().trim()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // Release signing: a properties file (storeFile, storePassword, keyAlias, keyPassword)
    // from $FORK_SIGNING or the default local path. Without it the release stays unsigned.
    val signing =
        Properties().apply {
            val path =
                System.getenv("FORK_SIGNING") ?: "${System.getProperty("user.home")}/.android/fork-around-and-find-out-release.properties"
            file(path).takeIf { it.isFile }?.inputStream()?.use(::load)
        }
    signingConfigs {
        if (signing.containsKey("storeFile")) {
            create("release") {
                storeFile = file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        // Every Android 14 phone is arm64; the emulator needs x86_64 for debug/e2e builds.
        release {
            ndk { abiFilters += "arm64-v8a" }
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    lint {
        // Dependency freshness is reviewed deliberately; pinned builds must not expire.
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency")
        // The release is arm64-only by design (phones on a table); ChromeOS is not a target.
        disable += "ChromeOsAbiSupport"
        warningsAsErrors = true
        abortOnError = true
    }
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        allWarningsAsErrors = true
    }
}
dependencies {
    implementation(project(":core"))
    kover(project(":core"))
    kover(project(":detection"))
    testImplementation(testFixtures(project(":detection")))
    androidTestImplementation(testFixtures(project(":detection")))
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("com.google.mediapipe:tasks-vision:1.0.0")
    testImplementation("org.mockito:mockito-core:5.24.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}
kover {
    // One report over app (debug) + :core + :detection; the 95 % gate applies to all of it.
    currentProject {
        createVariant("all") { add("debug") }
    }
    reports {
        verify {
            rule { minBound(95) }
        }
    }
}
