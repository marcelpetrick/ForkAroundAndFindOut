// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
// Desktop tools over recorded session logs: replay/evaluation and synthetic log generation.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("application")
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
application {
    mainClass = "it.marcelpetrick.fork.tools.ToolsKt"
}
dependencies {
    implementation(project(":core"))
    testImplementation("junit:junit:4.13.2")
}
kover {
    currentProject {
        createVariant("all") { add("jvm") }
    }
}
