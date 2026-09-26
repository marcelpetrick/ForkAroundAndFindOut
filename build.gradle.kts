// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.9" apply false
    id("com.diffplug.spotless") version "8.10.3"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("org.cyclonedx.bom") version "3.4.1"
}
// One identity for every module, so the SBOM names this project's components correctly.
allprojects {
    group = "it.marcelpetrick.fork"
    version = rootProject.file("VERSION").readText().trim()
}
// Static analysis for all Kotlin sources (production and tests), run once at the root so it
// works the same for the JVM modules and the Android app. Findings fail the build.
detekt {
    buildUponDefaultConfig = true
    config.setFrom("detekt.yml")
    source.setFrom(fileTree(".") { include("*/src/**/*.kt") })
    parallel = true
}
spotless {
    kotlin {
        target("*/src/**/*.kt")
        ktlint("1.8.0")
    }
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts")
        ktlint("1.8.0")
    }
}
