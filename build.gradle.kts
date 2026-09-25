// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
plugins {
    id("com.android.application") version "9.3.2" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.9" apply false
    id("com.diffplug.spotless") version "8.10.0"
}
spotless {
    kotlin {
        target("*/src/**/*.kt")
        ktlint("1.7.1")
    }
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts")
        ktlint("1.7.1")
    }
}
