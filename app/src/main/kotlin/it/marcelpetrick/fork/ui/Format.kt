// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork.ui

import java.time.LocalDate

/** Minutes and seconds, e.g. 12:03, for session lines and the meal summary. */
fun clockText(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

/** Suggested file name for a records export; the date keeps a second export from replacing the first. */
fun exportName(date: LocalDate): String = "fork-around-samples-$date.json"
