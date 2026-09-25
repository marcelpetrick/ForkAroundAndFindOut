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
    reports {
        verify {
            rule { minBound(95) }
        }
    }
}
