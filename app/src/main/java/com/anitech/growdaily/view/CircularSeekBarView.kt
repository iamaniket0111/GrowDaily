package com.anitech.growdaily.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.anitech.growdaily.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class CircularSeekBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface OnCircularChangeListener {
        fun onProgressChanged(view: CircularSeekBarView, progress: Int, fromUser: Boolean)
        fun onStartTrackingTouch(view: CircularSeekBarView)
        fun onStopTrackingTouch(view: CircularSeekBarView)
    }

    private companion object {
        const val START_ANGLE = 135f
        const val SWEEP_ANGLE = 270f
        const val STROKE_DP = 18f
        const val TOUCH_EXTRA_DP = 28f
        const val DESIRED_SIZE_DP = 240f
        const val KNOB_RADIUS_RATIO = 0.72f
    }

    private val dp = resources.displayMetrics.density
    private val strokeWidthPx = STROKE_DP * dp
    private val touchRadiusExtraPx = TOUCH_EXTRA_DP * dp

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = strokeWidthPx
        color = ContextCompat.getColor(context, R.color.task_done_track)
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = strokeWidthPx
        color = ContextCompat.getColor(context, R.color.brand_blue)
    }

    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = progressPaint.color
    }

    private val arcBounds = RectF()

    // Cached to avoid allocations in onDraw
    private var progressSweep = 0f
    private var knobCx = 0f
    private var knobCy = 0f
    private val knobRadius = strokeWidthPx * KNOB_RADIUS_RATIO

    private var listener: OnCircularChangeListener? = null
    private var isDragging = false

    var max: Int = 100
        set(value) {
            field = value.coerceAtLeast(1)
            progress = progress.coerceIn(0, field)
            recalcKnob()
            invalidate()
        }

    var progress: Int = 0
        set(value) {
            val clamped = value.coerceIn(0, max)
            if (field == clamped) return
            field = clamped
            recalcKnob()
            listener?.onProgressChanged(this, field, false)
            invalidate()
        }

    fun setProgressColor(color: Int) {
        progressPaint.color = color
        knobPaint.color = color
        invalidate()
    }

    fun setTrackColor(color: Int) {
        trackPaint.color = color
        invalidate()
    }

    fun setOnCircularChangeListener(l: OnCircularChangeListener?) {
        listener = l
    }


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = strokeWidthPx / 2f
        arcBounds.set(
            paddingLeft + inset,
            paddingTop + inset,
            w - paddingRight - inset,
            h - paddingBottom - inset
        )
        recalcKnob()
    }

    private fun recalcKnob() {
        progressSweep = SWEEP_ANGLE * (progress.toFloat() / max.toFloat())
        val angleRad = Math.toRadians((START_ANGLE + progressSweep).toDouble())
        // Use the shorter of width/height in case padding makes bounds non-square
        val radius = minOf(arcBounds.width(), arcBounds.height()) / 2f
        knobCx = arcBounds.centerX() + radius * cos(angleRad).toFloat()
        knobCy = arcBounds.centerY() + radius * sin(angleRad).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        // Use a square sub-rect centered in arcBounds to keep arc circular
        val size = minOf(arcBounds.width(), arcBounds.height())
        val cx = arcBounds.centerX()
        val cy = arcBounds.centerY()
        val half = size / 2f
        val squareBounds = RectF(cx - half, cy - half, cx + half, cy + half)

        canvas.drawArc(squareBounds, START_ANGLE, SWEEP_ANGLE, false, trackPaint)
        if (progressSweep > 0f) {
            canvas.drawArc(squareBounds, START_ANGLE, progressSweep, false, progressPaint)
        }
        canvas.drawCircle(knobCx, knobCy, knobRadius, knobPaint)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isHitOnArc(event.x, event.y)) return false
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                updateProgressFromTouch(event.x, event.y, fromUser = true)
                listener?.onStartTrackingTouch(this)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false
                updateProgressFromTouch(event.x, event.y, fromUser = true)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) return false
                isDragging = false
                updateProgressFromTouch(event.x, event.y, fromUser = true)
                listener?.onStopTrackingTouch(this)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                listener?.onStopTrackingTouch(this)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** Only start a drag if the finger is near the arc ring. */
    private fun isHitOnArc(x: Float, y: Float): Boolean {
        val dx = x - arcBounds.centerX()
        val dy = y - arcBounds.centerY()
        val distance = sqrt(dx * dx + dy * dy)
        val radius = arcBounds.width() / 2f
        return distance in (radius - touchRadiusExtraPx)..(radius + touchRadiusExtraPx)
    }

    private fun updateProgressFromTouch(x: Float, y: Float, fromUser: Boolean) {
        if (!isHitOnArc(x, y)) return

        val dx = x - arcBounds.centerX()
        val dy = y - arcBounds.centerY()

        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angle < 0f) angle += 360f

        val arcRelative = ((angle - START_ANGLE) + 360f) % 360f
        val clamped = arcRelative.coerceIn(0f, SWEEP_ANGLE)
        val newProgress = ((clamped / SWEEP_ANGLE) * max).roundToInt().coerceIn(0, max)

        if (progress != newProgress) {
            val old = progress
            progress = newProgress
            // progress setter fires listener with fromUser=false; re-fire with correct flag
            if (old != progress) {
                listener?.onProgressChanged(this, newProgress, fromUser)
            }
        }
    }
}