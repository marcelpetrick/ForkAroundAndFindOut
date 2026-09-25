// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
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
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}
kover {
    reports {
        verify {
            rule { minBound(95) }
        }
    }
}
