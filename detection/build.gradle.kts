// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
// Pure Kotlin/JVM detection: geometry, features, rules, seat tracking, temporal filter.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("java-test-fixtures")
    id("org.jetbrains.kotlinx.kover")
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        allWarningsAsErrors = true
    }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
}
kover {
    // Merged into the app's custom `all` variant (`:app:koverXmlReportAll`).
    currentProject {
        createVariant("all") { add("jvm") }
    }
}
