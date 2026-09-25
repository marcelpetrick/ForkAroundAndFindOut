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
import it.marcelpetrick.fork.detection.ElbowState

/** Warm ivory surfaces, deep green controls, restrained amber/red status accents. */
object Palette {
    val SURFACE = Color.rgb(255, 248, 236)
    val CARD = Color.WHITE
    val INK = Color.rgb(31, 42, 36)
    val MUTED = Color.rgb(92, 104, 97)
    val GREEN = Color.rgb(40, 101, 82)
    val AMBER = Color.rgb(183, 121, 31)
    val RED = Color.rgb(179, 38, 30)
    val UNKNOWN = Color.rgb(128, 128, 128)
    val STAGE = Color.rgb(24, 32, 28)

    fun of(state: ElbowState): Int =
        when (state) {
            ElbowState.UNKNOWN -> UNKNOWN
            ElbowState.CLEAR -> GREEN
            ElbowState.SUSPECT -> AMBER
            ElbowState.VIOLATION -> RED
        }
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
    color: Int = Palette.INK,
): TextView =
    TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(6), 0, dp(6))
    }

fun Context.title(text: CharSequence): TextView = label(text, 26f, true, Palette.GREEN)

fun Context.action(
    text: CharSequence,
    primary: Boolean = false,
    onClick: () -> Unit,
): Button =
    Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setTextColor(if (primary) Color.WHITE else Palette.GREEN)
        background =
            GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(if (primary) Palette.GREEN else Palette.CARD)
                setStroke(dp(2), Palette.GREEN)
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
                setColor(Palette.CARD)
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
