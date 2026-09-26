// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun Context.column(padding: Int = 20): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
    }

fun Context.label(
    text: CharSequence,
    size: Float = 17f,
    bold: Boolean = false,
    color: Int = Palette.ink,
): TextView =
    TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(6), 0, dp(6))
    }

fun Context.title(text: CharSequence): TextView = label(text, 26f, true, Palette.green)

fun Context.action(
    text: CharSequence,
    primary: Boolean = false,
    onClick: () -> Unit,
): Button =
    Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setTextColor(if (primary) Palette.onGreen else Palette.green)
        background =
            GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(if (primary) Palette.green else Palette.card)
                setStroke(dp(2), Palette.green)
            }
        minHeight = dp(56)
        setOnClickListener { onClick() }
        layoutParams =
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
    }

fun Context.card(vararg children: View): LinearLayout =
    column(16).apply {
        background =
            GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Palette.card)
                setStroke(dp(1), Palette.line)
            }
        children.forEach(::addView)
        layoutParams =
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            }
    }

fun Context.row(vararg children: View): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        children.forEach { child ->
            addView(child, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
        }
    }

/** A rounded state chip: soft background and coloured words, never colour alone. */
fun Context.chip(
    text: CharSequence,
    state: ElbowState,
): TextView =
    label(text, 15f, bold = true, color = Palette.of(state)).apply {
        setPadding(dp(12), dp(6), dp(12), dp(6))
        background =
            GradientDrawable().apply {
                cornerRadius = dp(999).toFloat()
                setColor(Palette.softOf(state))
            }
    }

/** Sets text only when it changes: per-tick refreshes must not relayout or spam accessibility. */
fun TextView.update(value: CharSequence) {
    if (text.toString() != value.toString()) text = value
}
