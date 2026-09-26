// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork.ui

import android.content.Context
import org.json.JSONObject

/** One bundled third-party component, as listed in the release SBOM (`scripts/sbom.py notices`). */
data class ThirdPartyComponent(
    val group: String,
    val name: String,
    val version: String,
    val license: String,
    val url: String,
    /** Asset path of the copyright notice a BSD/MIT licence asks to reproduce, if any. */
    val notice: String?,
)

/** Reads `assets/third_party.json`; the pipeline checks it against the SBOM of every build. */
fun thirdParty(context: Context): List<ThirdPartyComponent> {
    val json =
        JSONObject(
            context.assets
                .open(THIRD_PARTY)
                .bufferedReader()
                .use { it.readText() },
        )
    val items = json.getJSONArray("components")
    return (0 until items.length()).map { index ->
        val item = items.getJSONObject(index)
        ThirdPartyComponent(
            item.getString("group"),
            item.getString("name"),
            item.getString("version"),
            item.getString("license"),
            item.getString("url"),
            item.optString("notice").ifEmpty { null },
        )
    }
}

/** Full licence texts shipped as assets, keyed by SPDX identifier (the GPL first). */
val LICENSE_TEXTS = listOf("GPL-3.0-or-later", "Apache-2.0", "BSD-3-Clause", "MIT")

fun licenseAsset(id: String) = "$id.txt"

fun Context.asset(path: String): String = assets.open(path).bufferedReader().use { it.readText() }

private const val THIRD_PARTY = "third_party.json"
