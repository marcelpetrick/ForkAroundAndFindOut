// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import it.marcelpetrick.fork.R
import it.marcelpetrick.fork.detection.Point
import it.marcelpetrick.fork.detection.Polygon
import it.marcelpetrick.fork.detection.Pose
import it.marcelpetrick.fork.detection.SeatResult
import it.marcelpetrick.fork.monitoring.VisualMode
import kotlin.math.PI
import kotlin.math.sin

/**
 * Transparent layer over the camera preview. Coordinates are normalized to this view,
 * which has exactly the preview's size, so taps and landmarks share one space.
 */
class StageView(
    context: Context,
) : View(context) {
    var clock: () -> Long = SystemClock::uptimeMillis
    var table: Polygon? = null
    var seats: List<Polygon> = emptyList()
    var taps: List<Point> = emptyList()
    var poses: List<Pose> = emptyList()
    var results: List<SeatResult> = emptyList()
    var skeleton = true
    var synthetic = false

    /** Visible image within this view; taps outside it (letterbox margins) are rejected. */
    var bounds: RectF? = null
    var warning = VisualMode.OFF
    var onTap: ((Point) -> Unit)? = null
    var onRejectedTap: (() -> Unit)? = null

    private val stroke =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = context.dp(16).toFloat()
            isFakeBoldText = true
        }

    fun refresh() {
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (onTap == null || width == 0 || height == 0) return false
        if (event.action == MotionEvent.ACTION_UP) {
            val point = Point(event.x.toDouble() / width, event.y.toDouble() / height)
            val visible = bounds
            if (visible != null && !visible.contains(point.x.toFloat(), point.y.toFloat())) {
                onRejectedTap?.invoke()
            } else {
                onTap?.invoke(point)
            }
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (synthetic) canvas.drawColor(Palette.STAGE)
        bounds?.let(::shadeMargins)?.let { canvas.drawPath(it, fill.apply { color = Color.argb(150, 0, 0, 0) }) }
        table?.let { polygon(canvas, it.points, Color.argb(60, 255, 214, 102), Color.rgb(255, 214, 102)) }
        seats.forEachIndexed { index, seat ->
            polygon(canvas, seat.points, Color.argb(30, 120, 200, 255), Color.rgb(120, 200, 255))
            label(canvas, context.getString(R.string.seat_empty, index + 1).substringBefore(" ·"), seat.center(), Color.rgb(120, 200, 255))
        }
        if (taps.isNotEmpty()) {
            polygon(canvas, taps, Color.TRANSPARENT, Color.WHITE, closed = false)
            taps.forEachIndexed { index, tap ->
                val x = tap.x.toFloat() * width
                val y = tap.y.toFloat() * height
                canvas.drawCircle(x, y, context.dp(14).toFloat(), fill.apply { color = Palette.GREEN })
                text.color = Color.WHITE
                canvas.drawText((index + 1).toString(), x - context.dp(5), y + context.dp(6), text)
            }
        }
        if (skeleton) {
            val tracked = results.mapNotNull { it.pose }.toSet()
            poses.filter { it !in tracked }.forEach { bones(canvas, it) }
            for (seat in results) {
                val pose = seat.pose ?: continue
                bones(canvas, pose)
                for ((index, arm) in listOf(13 to seat.left, 14 to seat.right)) {
                    val elbow = pose.landmarks.getOrNull(index)?.point ?: continue
                    canvas.drawCircle(
                        elbow.x.toFloat() * width,
                        elbow.y.toFloat() * height,
                        context.dp(12).toFloat(),
                        fill.apply { color = Palette.of(arm.state) },
                    )
                }
                pose.center()?.let {
                    label(canvas, context.getString(R.string.seat_empty, seat.seat).substringBefore(" ·"), it, Color.WHITE)
                }
            }
        }
        drawWarning(canvas)
    }

    private fun drawWarning(canvas: Canvas) {
        if (warning == VisualMode.OFF) return
        val red = Palette.RED
        val edge = context.dp(14).toFloat()
        when (warning) {
            VisualMode.FULL -> canvas.drawColor(Color.argb(110, Color.red(red), Color.green(red), Color.blue(red)))
            VisualMode.ICON -> {
                val radius = context.dp(40).toFloat()
                canvas.drawCircle(width / 2f, radius * 1.5f, radius, fill.apply { color = red })
                text.color = Color.WHITE
                text.textSize = radius * 1.4f
                canvas.drawText("!", width / 2f - radius * 0.2f, radius * 2f, text)
                text.textSize = context.dp(16).toFloat()
            }
            else -> {
                // Slow pulse is a gentle 0.5 Hz fade, far below WCAG's three-flashes limit.
                val alpha =
                    if (warning == VisualMode.PULSE) {
                        (140 + 115 * sin(2 * PI * (clock() % 2000) / 2000.0)).toInt()
                    } else {
                        255
                    }
                stroke.color = Color.argb(alpha, Color.red(red), Color.green(red), Color.blue(red))
                stroke.strokeWidth = edge * 2
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), stroke)
                if (warning == VisualMode.PULSE) postInvalidateOnAnimation()
            }
        }
    }

    private fun shadeMargins(visible: RectF): Path =
        Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            addRect(visible.left * width, visible.top * height, visible.right * width, visible.bottom * height, Path.Direction.CW)
        }

    private fun polygon(
        canvas: Canvas,
        points: List<Point>,
        fillColor: Int,
        lineColor: Int,
        closed: Boolean = true,
    ) {
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = point.x.toFloat() * width
            val y = point.y.toFloat() * height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (closed) {
            path.close()
            canvas.drawPath(path, fill.apply { color = fillColor })
        }
        stroke.color = lineColor
        stroke.strokeWidth = context.dp(3).toFloat()
        canvas.drawPath(path, stroke)
    }

    private fun bones(
        canvas: Canvas,
        pose: Pose,
    ) {
        stroke.strokeWidth = context.dp(4).toFloat()
        for ((a, b) in BONES) {
            val from = pose.joint(a) ?: continue
            val to = pose.joint(b) ?: continue
            stroke.color = Color.argb((255 * minOf(from.confidence, to.confidence)).toInt(), 255, 255, 255)
            canvas.drawLine(
                from.point.x.toFloat() * width,
                from.point.y.toFloat() * height,
                to.point.x.toFloat() * width,
                to.point.y.toFloat() * height,
                stroke,
            )
        }
    }

    private fun label(
        canvas: Canvas,
        value: String,
        at: Point,
        color: Int,
    ) {
        text.color = color
        canvas.drawText(value, at.x.toFloat() * width - text.measureText(value) / 2, at.y.toFloat() * height - context.dp(18), text)
    }

    private companion object {
        val BONES = listOf(11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16, 11 to 23, 12 to 24, 23 to 24)
    }
}
