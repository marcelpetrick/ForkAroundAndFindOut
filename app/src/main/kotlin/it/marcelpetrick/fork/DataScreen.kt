// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork

import android.app.AlertDialog
import android.net.Uri
import android.view.View
import it.marcelpetrick.fork.MainActivity.Screen
import it.marcelpetrick.fork.monitoring.SessionFiles
import it.marcelpetrick.fork.ui.Palette
import it.marcelpetrick.fork.ui.action
import it.marcelpetrick.fork.ui.card
import it.marcelpetrick.fork.ui.column
import it.marcelpetrick.fork.ui.label
import it.marcelpetrick.fork.ui.row
import it.marcelpetrick.fork.ui.title
import java.io.OutputStream

/**
 * Local data: stored records and session logs, with export and delete.
 * Its functions extend [MainActivity], the single host that owns the lifecycle, the camera
 * and navigation; the screen owns only its own views and state.
 */
internal class DataScreen {
    fun MainActivity.dataPage(): View =
        column().apply {
            addView(title(getString(R.string.data_title)))
            addView(label(getString(R.string.data_help)))
            val count = store.records().length()
            store.notice?.let { addView(card(label(it, color = Palette.red))) }
            notice?.let { addView(label(it, bold = true)) }
            addView(label(getString(R.string.data_count, count), 19f, bold = true))
            addView(action(getString(R.string.export)) { exporter.launch("fork-around-samples.json") })
            val logs = SessionFiles.list(sessionsDir)
            addView(label(getString(R.string.session_logs, logs.size, SessionFiles.totalBytes(sessionsDir) / 1024), 19f, bold = true))
            for (log in logs) {
                addView(
                    card(
                        label(getString(R.string.session_entry, log.name, log.length() / 1024)),
                        row(
                            action(getString(R.string.export_log)) {
                                exportLog = log
                                logExporter.launch(log.name)
                            },
                            action(getString(R.string.delete_log)) {
                                confirm(R.string.delete_log_confirm) {
                                    log.delete()
                                    notice = getString(R.string.deleted_log)
                                }
                            },
                        ),
                    ),
                )
            }
            addView(
                action(getString(R.string.delete)) {
                    confirm(R.string.delete_confirm) {
                        store.delete()
                        SessionFiles.deleteAll(sessionsDir)
                        notice = getString(R.string.deleted)
                    }
                },
            )
            addView(action(getString(R.string.back), primary = true) { begin(Screen.WELCOME) })
        }

    private fun MainActivity.confirm(
        message: Int,
        action: () -> Unit,
    ) {
        AlertDialog
            .Builder(this)
            .setMessage(message)
            .setPositiveButton(R.string.delete) { _, _ ->
                action()
                show(Screen.DATA)
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    @Suppress("TooGenericExceptionCaught") // any provider or I/O failure becomes a message on the Data screen
    fun MainActivity.exportTo(
        uri: Uri,
        write: (OutputStream) -> Unit,
    ) {
        notice =
            try {
                contentResolver.openOutputStream(uri, "wt")!!.use(write)
                getString(R.string.exported)
            } catch (error: Exception) {
                getString(R.string.export_failed, error.message)
            }
        show(Screen.DATA)
    }
}
