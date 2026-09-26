// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.withClip
import it.marcelpetrick.fork.R
import it.marcelpetrick.fork.detection.ElbowState
import it.marcelpetrick.fork.detection.Joint
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

    /** Visual warning mode; changes fade in (400 ms) and out (600 ms), never pop or flash. */
    var warning = VisualMode.OFF
        set(value) {
            if (value == field) return
            if (value == VisualMode.OFF) fadingOut = field
            field = value
            changedAt = clock()
            postInvalidateOnAnimation()
        }
    private var fadingOut = VisualMode.OFF
    private var changedAt = 0L

    /** The reminder card: which seat (colour and number) and side, or null. */
    var reminder: Pair<Int, Boolean>? = null

    /** When set, a short "Thank you" card after a correction is shown. */
    var thanks = false

    /** Paused: the preview is dimmed so it is obvious nothing is being watched. */
    var dimmed = false
    var onTap: ((Point) -> Unit)? = null
    var onRejectedTap: (() -> Unit)? = null

    /** Called while an existing corner (by index) is dragged to a new image point. */
    var onDrag: ((Int, Point) -> Unit)? = null
    private var dragging: Int? = null

    /** A still of the camera preview for the loupe, taken once per press (null: overlay only). */
    var snapshot: (() -> Bitmap?)? = null
    private var still: Bitmap? = null

    /** Where the finger rests while marking corners, in view pixels; drives the loupe. */
    internal var pressAt: FloatArray? = null
        private set

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
                pressAt = floatArrayOf(event.x, event.y)
                still = snapshot?.invoke()
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

            MotionEvent.ACTION_MOVE -> {
                pressAt = floatArrayOf(event.x, event.y)
                dragging?.let { index -> image(event)?.takeIf { it.inImage() }?.let { onDrag?.invoke(index, it) } }
                invalidate()
            }

            MotionEvent.ACTION_CANCEL -> {
                endPress()
            }

            MotionEvent.ACTION_UP -> {
                endPress()
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

    private fun endPress() {
        pressAt = null
        still = null
        invalidate()
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
        if (synthetic) canvas.drawColor(Palette.stageBackground)
        mapping?.let { canvas.drawPath(shadeMargins(it), fill.apply { color = Color.argb(150, 0, 0, 0) }) }
        table?.let { polygon(canvas, it.points, Color.argb(60, 255, 214, 102), Color.rgb(255, 214, 102)) }
        drawSeats(canvas)
        drawTaps(canvas)
        if (skeleton) drawSkeletons(canvas)
        if (dimmed) canvas.drawColor(Color.argb(140, 0, 0, 0))
        drawLoupe(canvas)
        drawWarning(canvas)
        drawCard(canvas)
    }

    private fun drawSeats(canvas: Canvas) {
        seats.forEachIndexed { index, seat ->
            polygon(canvas, seat.points, Color.argb(30, 120, 200, 255), Color.rgb(120, 200, 255))
            label(canvas, context.getString(R.string.seat_empty, index + 1).substringBefore(" ·"), seat.center(), Color.rgb(120, 200, 255))
        }
    }

    private fun drawTaps(canvas: Canvas) {
        if (taps.isEmpty()) return
        polygon(canvas, taps, Color.TRANSPARENT, Color.WHITE, closed = false)
        taps.forEachIndexed { index, tap ->
            val (x, y) = view(tap)
            canvas.drawCircle(x, y, context.dp(14).toFloat(), fill.apply { color = Palette.green })
            text.color = Color.WHITE
            canvas.drawText((index + 1).toString(), x - context.dp(5), y + context.dp(6), text)
        }
    }

    /** Untracked people as plain bones; tracked seats with state-coloured elbows and a label. */
    private fun drawSkeletons(canvas: Canvas) {
        val tracked = results.mapNotNull { it.pose }.toSet()
        poses.filter { it !in tracked }.forEach { bones(canvas, it) }
        for (seat in results) {
            val pose = seat.pose ?: continue
            bones(canvas, pose)
            for ((index, arm) in listOf(Joint.LEFT_ELBOW to seat.left, Joint.RIGHT_ELBOW to seat.right)) {
                val elbow = pose.landmarks.getOrNull(index)?.point ?: continue
                val (x, y) = view(elbow)
                canvas.drawCircle(x, y, context.dp(12).toFloat(), fill.apply { color = Palette.of(arm.state) })
            }
            pose.center()?.let {
                label(canvas, context.getString(R.string.seat_empty, seat.seat).substringBefore(" ·"), it, Color.WHITE)
            }
        }
    }

    /**
     * While a finger marks or drags a corner it hides the very spot being placed: a circle
     * above the finger shows that spot magnified, with the overlay and a crosshair.
     */
    private fun drawLoupe(canvas: Canvas) {
        val (x, y) = pressAt ?: return
        if (onTap == null) return
        val radius = context.dp(LOUPE_DP).toFloat()
        val cx = x.coerceIn(radius, maxOf(radius, width - radius))
        val cy = if (y - radius * 2.2f > radius) y - radius * 2.2f else y + radius * 2.2f
        val clip = Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) }
        canvas.withClip(clip) {
            drawColor(Palette.stageBackground)
            // Magnify around the finger: the loupe centre shows the point under the finger.
            translate(cx, cy)
            scale(LOUPE_ZOOM, LOUPE_ZOOM)
            translate(-x, -y)
            still?.let { drawBitmap(it, null, RectF(0f, 0f, this@StageView.width.toFloat(), this@StageView.height.toFloat()), null) }
            table?.let { polygon(canvas, it.points, Color.TRANSPARENT, Color.rgb(255, 214, 102)) }
            if (taps.isNotEmpty()) polygon(canvas, taps, Color.TRANSPARENT, Color.WHITE, closed = false)
        }
        stroke.color = Color.WHITE
        stroke.strokeWidth = context.dp(2).toFloat()
        val arm = radius / 3
        canvas.drawLine(cx - arm, cy, cx + arm, cy, stroke)
        canvas.drawLine(cx, cy - arm, cx, cy + arm, stroke)
        stroke.strokeWidth = context.dp(3).toFloat()
        canvas.drawCircle(cx, cy, radius, stroke)
    }

    private fun drawWarning(canvas: Canvas) {
        val elapsed = clock() - changedAt
        val mode = if (warning == VisualMode.OFF) fadingOut else warning
        val fade =
            if (warning == VisualMode.OFF) {
                1f - (elapsed / FADE_OUT_MS.toFloat()).coerceIn(0f, 1f)
            } else {
                (elapsed / FADE_IN_MS.toFloat()).coerceIn(0f, 1f)
            }
        if (mode == VisualMode.OFF || fade <= 0f) return
        val red = Palette.red

        fun tint(alpha: Float) = Color.argb((255 * alpha * fade).toInt(), Color.red(red), Color.green(red), Color.blue(red))
        when (mode) {
            VisualMode.FULL -> {
                canvas.drawColor(tint(0.43f))
            }

            VisualMode.ICON -> {
                val radius = context.dp(40).toFloat()
                canvas.drawCircle(width / 2f, radius * 1.5f, radius, fill.apply { color = tint(1f) })
                text.color = Color.WHITE
                text.textSize = radius * 1.4f
                canvas.drawText("!", width / 2f - radius * 0.2f, radius * 2f, text)
                text.textSize = context.dp(16).toFloat()
            }

            else -> {
                // Slow pulse: opacity 0.6↔1.0 over 2.4 s (0.42 Hz), far below WCAG's flash limit.
                val alpha = if (mode == VisualMode.PULSE) (0.8 + 0.2 * sin(2 * PI * (clock() % PULSE_MS) / PULSE_MS)).toFloat() else 1f
                stroke.color = tint(alpha)
                stroke.strokeWidth = context.dp(12).toFloat() * 2
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), stroke)
            }
        }
        if (mode == VisualMode.PULSE || fade < 1f) postInvalidateOnAnimation()
    }

    /** Centre card: the kind reminder naming the seat by colour, or a short thank-you. */
    private fun drawCard(canvas: Canvas) {
        val (seat, left) = reminder ?: (if (thanks) 0 to true else return)
        val title = context.getString(if (thanks && reminder == null) R.string.thank_you else R.string.warning_text)
        val detail =
            if (reminder == null) {
                ""
            } else {
                context.getString(
                    R.string.reminder_detail,
                    context.getString(SEAT_NAMES.getOrElse(seat - 1) { R.string.seat_colour_1 }),
                    context.getString(if (left) R.string.left else R.string.right),
                )
            }
        val pad = context.dp(16).toFloat()
        text.textSize = context.dp(20).toFloat()
        val lineWidth = maxOf(text.measureText(title), text.measureText(detail)) + pad * 3 + context.dp(16)
        val cardWidth = minOf(width - pad * 2, lineWidth)
        val cardHeight = context.dp(if (detail.isEmpty()) 56 else 88).toFloat()
        val left0 = (width - cardWidth) / 2
        val top = height - cardHeight - context.dp(28)
        val card = RectF(left0, top, left0 + cardWidth, top + cardHeight)
        fill.color = Palette.softOf(if (reminder == null) ElbowState.CLEAR else ElbowState.VIOLATION)
        canvas.drawRoundRect(card, pad, pad, fill)
        if (reminder !=
            null
        ) {
            canvas.drawCircle(
                card.left + pad + context.dp(8),
                card.top + pad + context.dp(10),
                context.dp(8).toFloat(),
                fill.apply {
                    color =
                        Palette.seat(seat)
                },
            )
        }
        text.color = Palette.ink
        canvas.drawText(title, card.left + pad * 2 + context.dp(16), card.top + pad + context.dp(18), text)
        if (detail.isNotEmpty()) {
            text.textSize = context.dp(16).toFloat()
            text.color = Palette.muted
            canvas.drawText(detail, card.left + pad * 2 + context.dp(16), card.top + pad + context.dp(50), text)
        }
        text.textSize = context.dp(16).toFloat()
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
        val visible = BONES.mapNotNull { (a, b) -> pose.joint(a)?.let { from -> pose.joint(b)?.let { to -> from to to } } }
        for ((from, to) in visible) {
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
        const val LOUPE_DP = 56
        const val LOUPE_ZOOM = 2.5f
        const val FADE_IN_MS = 400L
        const val FADE_OUT_MS = 600L
        const val PULSE_MS = 2400.0
        val SEAT_NAMES = listOf(R.string.seat_colour_1, R.string.seat_colour_2, R.string.seat_colour_3, R.string.seat_colour_4)
        val BONES =
            with(Joint) {
                listOf(
                    LEFT_SHOULDER to RIGHT_SHOULDER,
                    LEFT_SHOULDER to LEFT_ELBOW,
                    LEFT_ELBOW to LEFT_WRIST,
                    RIGHT_SHOULDER to RIGHT_ELBOW,
                    RIGHT_ELBOW to RIGHT_WRIST,
                    LEFT_SHOULDER to LEFT_HIP,
                    RIGHT_SHOULDER to RIGHT_HIP,
                    LEFT_HIP to RIGHT_HIP,
                )
            }
    }
}
