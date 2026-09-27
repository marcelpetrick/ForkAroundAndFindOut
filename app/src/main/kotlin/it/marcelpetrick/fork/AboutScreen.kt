// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork

import android.text.util.Linkify
import android.view.View
import it.marcelpetrick.fork.MainActivity.Companion.SOURCE_URL
import it.marcelpetrick.fork.MainActivity.Companion.TEXT_PAGE_CHARS
import it.marcelpetrick.fork.MainActivity.Screen
import it.marcelpetrick.fork.ui.LICENSE_TEXTS
import it.marcelpetrick.fork.ui.Palette
import it.marcelpetrick.fork.ui.action
import it.marcelpetrick.fork.ui.asset
import it.marcelpetrick.fork.ui.card
import it.marcelpetrick.fork.ui.column
import it.marcelpetrick.fork.ui.label
import it.marcelpetrick.fork.ui.licenseAsset
import it.marcelpetrick.fork.ui.row
import it.marcelpetrick.fork.ui.thirdParty
import it.marcelpetrick.fork.ui.title

/**
 * Who made the app, the GPL notices, every bundled component, licence and notice texts.
 * Its functions extend [MainActivity], the single host that owns the lifecycle, the camera
 * and navigation; the screen owns only its own views and state.
 */
internal class AboutScreen {
    /**
     * Who made it, the GPL's "appropriate legal notices" (copyright, no warranty, how to read the
     * licence), the source of this exact version, and every bundled component with its licence.
     */
    fun MainActivity.aboutPage(): View =
        column().apply {
            addView(title(getString(R.string.about)))
            addView(label(getString(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), 19f, bold = true))
            addView(
                card(
                    label(getString(R.string.about_author), bold = true),
                    label(getString(R.string.about_copyright)),
                    label(getString(R.string.about_license), 15f),
                    action(getString(R.string.read_gpl)) { showText(getString(R.string.gpl_title), licenseAsset(LICENSE_TEXTS.first())) },
                ),
            )
            addView(
                card(
                    label(getString(R.string.about_source_version, "$SOURCE_URL/tree/v${BuildConfig.VERSION_NAME}"), 15f).apply {
                        autoLinkMask = Linkify.WEB_URLS
                    },
                    label(getString(R.string.welcome_privacy), 15f),
                ),
            )
            val components = thirdParty(this@aboutPage)
            addView(label(getString(R.string.third_party_title, components.size), 20f, bold = true, color = Palette.green))
            addView(label(getString(R.string.third_party_help), 15f, color = Palette.muted))
            for (component in components) addView(componentEntry(component))
            addView(label(getString(R.string.license_texts_title), 20f, bold = true, color = Palette.green))
            for (id in LICENSE_TEXTS.drop(1)) addView(action(getString(R.string.read_license, id)) { showText(id, licenseAsset(id)) })
            addView(label(getString(R.string.welcome_limits), color = Palette.muted))
            addView(action(getString(R.string.back), primary = true) { show(Screen.WELCOME) })
        }

    private fun MainActivity.componentEntry(component: it.marcelpetrick.fork.ui.ThirdPartyComponent): View =
        column(0).apply {
            addView(label(getString(R.string.third_party_entry, component.name, component.version, component.license), 15f, bold = true))
            addView(label("${component.group} · ${component.url}", 13f, color = Palette.muted).apply { autoLinkMask = Linkify.WEB_URLS })
            component.notice?.let { notice ->
                addView(action(getString(R.string.read_notice, component.name)) { showText(component.name, notice) })
            }
        }

    private var textTitle = ""

    private var textAsset = ""

    var textPage = 0

    /** Shows a licence or notice text from the assets; Back returns to About. */
    private fun MainActivity.showText(
        title: String,
        asset: String,
    ) {
        textTitle = title
        textAsset = asset
        textPage = 0
        show(Screen.TEXT)
    }

    /** Long notices (MediaPipe's lists 187 native libraries) are shown in pages, not at once. */
    fun MainActivity.textPage(): View =
        column().apply {
            val pages = asset(textAsset).chunked(TEXT_PAGE_CHARS)
            addView(title(textTitle))
            if (pages.size > 1) addView(label(getString(R.string.text_page, textPage + 1, pages.size), 15f, bold = true))
            addView(label(pages[textPage], 13f).apply { typeface = android.graphics.Typeface.MONOSPACE })
            if (pages.size > 1) {
                addView(
                    row(
                        action(getString(R.string.previous_page)) { turnPage(-1, pages.size) },
                        action(getString(R.string.next_page)) { turnPage(1, pages.size) },
                    ),
                )
            }
            addView(action(getString(R.string.back), primary = true) { show(Screen.ABOUT) })
        }

    private fun MainActivity.turnPage(
        delta: Int,
        pages: Int,
    ) {
        textPage = (textPage + delta).coerceIn(0, pages - 1)
        show(Screen.TEXT)
    }
}
