// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork.ui

import android.content.Context
import android.graphics.Color
import it.marcelpetrick.fork.R
import it.marcelpetrick.fork.detection.ElbowState

/**
 * Design tokens (plan_v2/design/ui-ux.md), loaded from `colors.xml` so the light ("warm
 * ivory") and dark ("dim room") variants follow the system setting. Call [load] whenever
 * the configuration changes; views are rebuilt afterwards.
 */
object Palette {
    var surface = Color.rgb(247, 242, 232)
    var card = Color.rgb(255, 253, 249)
    var line = Color.rgb(228, 220, 203)
    var ink = Color.rgb(31, 42, 38)
    var muted = Color.rgb(107, 117, 112)
    var green = Color.rgb(40, 101, 82)
    var onGreen = Color.WHITE
    var amber = Color.rgb(138, 90, 29)
    var red = Color.rgb(179, 38, 30)
    var unknownColor = Color.rgb(95, 102, 98)
    private var soft = mapOf<ElbowState, Int>()
    private var seats = listOf<Int>()
    val stageBackground = Color.rgb(24, 32, 28)

    fun load(context: Context) {
        fun c(id: Int) = context.getColor(id)
        surface = c(R.color.bg)
        card = c(R.color.card)
        line = c(R.color.line)
        ink = c(R.color.ink)
        muted = c(R.color.muted)
        green = c(R.color.green)
        onGreen = c(R.color.on_green)
        amber = c(R.color.amber)
        red = c(R.color.red)
        unknownColor = c(R.color.grey)
        soft =
            mapOf(
                ElbowState.UNKNOWN to c(R.color.grey_soft),
                ElbowState.CLEAR to c(R.color.green_soft),
                ElbowState.SUSPECT to c(R.color.amber_soft),
                ElbowState.VIOLATION to c(R.color.red_soft),
            )
        seats = listOf(c(R.color.seat1), c(R.color.seat2), c(R.color.seat3), c(R.color.seat4))
    }

    /** Text/marker colour for a state; states always also carry words. */
    fun of(state: ElbowState): Int =
        when (state) {
            ElbowState.UNKNOWN -> unknownColor
            ElbowState.CLEAR -> green
            ElbowState.SUSPECT -> amber
            ElbowState.VIOLATION -> red
        }

    /** Chip background for a state. */
    fun softOf(state: ElbowState): Int = soft[state] ?: card

    /** Seat identity colour (1-based); seats are colours, never names. */
    fun seat(number: Int): Int = seats.getOrElse(number - 1) { green }
}
