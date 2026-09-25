// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
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
 * Transparent layer over the camera preview. Every model coordinate (landmarks, table,
 * seats, taps) is normalized to the upright camera image; [mapping] converts it to view
 * pixels. Without a mapping (synthetic demo) the image fills this view.
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

    /** Normalized image → view pixels; taps outside the image (letterbox margins) are rejected. */
    var mapping: Matrix? = null
    var warning = VisualMode.OFF
    var onTap: ((Point) -> Unit)? = null
    var onRejectedTap: (() -> Unit)? = null

    /** Called while an existing corner (by index) is dragged to a new image point. */
    var onDrag: ((Int, Point) -> Unit)? = null
    private var dragging: Int? = null

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
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Pressing on an existing corner starts adjusting it instead of adding one.
                val grab = context.dp(GRAB_DP).toFloat()
                dragging =
                    taps.indices
                        .map { it to view(taps[it]) }
                        .filter { (_, xy) -> kotlin.math.hypot(xy[0] - event.x, xy[1] - event.y) <= grab }
                        .minByOrNull { (_, xy) -> kotlin.math.hypot(xy[0] - event.x, xy[1] - event.y) }
                        ?.first
                        ?.takeIf { onDrag != null }
            }
            MotionEvent.ACTION_MOVE -> dragging?.let { index -> image(event)?.takeIf { it.inImage() }?.let { onDrag?.invoke(index, it) } }
            MotionEvent.ACTION_UP -> {
                if (dragging == null) {
                    val point = image(event)
                    if (point == null || !point.inImage()) onRejectedTap?.invoke() else onTap?.invoke(point)
                }
                dragging = null
                performClick()
            }
        }
        return true
    }

    /** Normalized image coordinates of a touch, or null if the mapping cannot be inverted. */
    private fun image(event: MotionEvent): Point? {
        val inverse = Matrix()
        if (!matrix().invert(inverse)) return null
        val xy = floatArrayOf(event.x, event.y).also { inverse.mapPoints(it) }
        return Point(xy[0].toDouble(), xy[1].toDouble())
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (synthetic) canvas.drawColor(Palette.STAGE)
        mapping?.let { canvas.drawPath(shadeMargins(it), fill.apply { color = Color.argb(150, 0, 0, 0) }) }
        table?.let { polygon(canvas, it.points, Color.argb(60, 255, 214, 102), Color.rgb(255, 214, 102)) }
        seats.forEachIndexed { index, seat ->
            polygon(canvas, seat.points, Color.argb(30, 120, 200, 255), Color.rgb(120, 200, 255))
            label(canvas, context.getString(R.string.seat_empty, index + 1).substringBefore(" ·"), seat.center(), Color.rgb(120, 200, 255))
        }
        if (taps.isNotEmpty()) {
            polygon(canvas, taps, Color.TRANSPARENT, Color.WHITE, closed = false)
            taps.forEachIndexed { index, tap ->
                val (x, y) = view(tap)
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
                    val (x, y) = view(elbow)
                    canvas.drawCircle(
                        x,
                        y,
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

    private fun matrix(): Matrix = mapping ?: Matrix().apply { setScale(width.toFloat(), height.toFloat()) }

    /** View pixel position of a normalized image point. */
    private fun view(point: Point): FloatArray = floatArrayOf(point.x.toFloat(), point.y.toFloat()).also { matrix().mapPoints(it) }

    private fun shadeMargins(mapping: Matrix): Path {
        val image = RectF(0f, 0f, 1f, 1f).also { mapping.mapRect(it) }
        return Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            addRect(image, Path.Direction.CW)
        }
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
            val (x, y) = view(point)
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
            val (x1, y1) = view(from.point)
            val (x2, y2) = view(to.point)
            canvas.drawLine(x1, y1, x2, y2, stroke)
        }
    }

    private fun label(
        canvas: Canvas,
        value: String,
        at: Point,
        color: Int,
    ) {
        text.color = color
        val (x, y) = view(at)
        canvas.drawText(value, x - text.measureText(value) / 2, y - context.dp(18), text)
    }

    private companion object {
        const val GRAB_DP = 28
        val BONES = listOf(11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16, 11 to 23, 12 to 24, 23 to 24)
    }
}
