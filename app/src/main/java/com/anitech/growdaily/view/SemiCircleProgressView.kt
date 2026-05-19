package com.anitech.growdaily.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.anitech.growdaily.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class SemiCircleProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var progress = 0f
    private var animatedProgress = 0f

    private var progressColor = ContextCompat.getColor(context, R.color.brand_blue)
    private var trackColor = ContextCompat.getColor(context, R.color.task_done_track)

    private val strokeWidth = 24f
    private val startAngle = 145f
    private val totalSweep = 250f

    private val arcRect = RectF()

    // Track uses BUTT — we draw round caps ourselves as filled circles
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        strokeWidth = this@SemiCircleProgressView.strokeWidth
        color = trackColor
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = this@SemiCircleProgressView.strokeWidth
        color = progressColor
    }

    // Used to paint the round caps at track start and end
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = trackColor
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val padding = strokeWidth

        val size = min(w, h * 1.7f)
        val left = (w - size) / 2f + padding / 2f
        val top = padding / 2f

        arcRect.set(left, top, left + size - padding, top + size - padding)

        val progressSweep = (animatedProgress / 100f) * totalSweep

        // The arc is drawn on a potentially non-square rect, so we compute
        // the actual ellipse radii for correct cap placement
        val rx = arcRect.width() / 2f
        val ry = arcRect.height() / 2f
        val cx = arcRect.centerX()
        val cy = arcRect.centerY()
        val capRadius = strokeWidth / 2f

        // ---- Track ----
        canvas.drawArc(arcRect, startAngle, totalSweep, false, trackPaint)

        // Round cap at track START
        val startRad = Math.toRadians(startAngle.toDouble())
        canvas.drawCircle(
            cx + rx * cos(startRad).toFloat(),
            cy + ry * sin(startRad).toFloat(),
            capRadius, capPaint
        )

        // Round cap at track END
        val endRad = Math.toRadians((startAngle + totalSweep).toDouble())
        canvas.drawCircle(
            cx + rx * cos(endRad).toFloat(),
            cy + ry * sin(endRad).toFloat(),
            capRadius, capPaint
        )

        // ---- Progress ----
        if (progressSweep > 0f) {
            progressPaint.color = progressColor
            canvas.drawArc(arcRect, startAngle, progressSweep, false, progressPaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width * 0.68f).toInt()
        setMeasuredDimension(width, height)
    }

    fun setProgress(value: Int) {
        val newProgress = value.coerceIn(0, 100).toFloat()
        ValueAnimator.ofFloat(animatedProgress, newProgress).apply {
            duration = 850
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                animatedProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        progress = newProgress
    }

    fun setProgressColor(color: Int) {
        progressColor = color
        progressPaint.color = color
        invalidate()
    }

    fun setTrackColor(color: Int) {
        trackColor = color
        trackPaint.color = color
        capPaint.color = color
        invalidate()
    }
}