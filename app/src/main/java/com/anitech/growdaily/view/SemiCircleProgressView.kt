package com.anitech.growdaily.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.anitech.growdaily.R

class SemiCircleProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // -----------------------
    // Config
    // -----------------------

    private var progress = 0f
    private var animatedProgress = 0f

    private var progressColor: Int =
        ContextCompat.getColor(context, R.color.brand_blue)

    private var trackColor: Int = ContextCompat.getColor(context, R.color.task_done_track)

    private val strokeWidth = 26f

    // -----------------------
    // Paints
    // -----------------------

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = this@SemiCircleProgressView.strokeWidth
        color = trackColor
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = this@SemiCircleProgressView.strokeWidth
        color = progressColor
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = strokeWidth + 8
        alpha = 40
    }

    private val arcRect = RectF()

    // -----------------------
    // Draw
    // -----------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val padding = strokeWidth / 2 + 6

        arcRect.set(
            padding,
            padding,
            w - padding,
            (w - padding * 2) + padding
        )

        // Track
        canvas.drawArc(
            arcRect,
            180f,
            180f,
            false,
            trackPaint
        )

        val sweep = (animatedProgress / 100f) * 180f

        // Glow
        glowPaint.color = progressColor
        canvas.drawArc(
            arcRect,
            180f,
            sweep,
            false,
            glowPaint
        )

        // Progress
        progressPaint.color = progressColor
        canvas.drawArc(
            arcRect,
            180f,
            sweep,
            false,
            progressPaint
        )
    }

    // -----------------------
    // Size
    // -----------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {

        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width / 2f + strokeWidth).toInt()

        setMeasuredDimension(width, height)
    }

    // -----------------------
    // Progress
    // -----------------------

    fun setProgress(value: Int) {

        val newProgress = value.coerceIn(0, 100).toFloat()

        val animator = ValueAnimator.ofFloat(animatedProgress, newProgress)

        animator.duration = 700

        animator.addUpdateListener {
            animatedProgress = it.animatedValue as Float
            invalidate()
        }

        animator.start()

        progress = newProgress
    }

    // -----------------------
    // Color
    // -----------------------

    fun setProgressColor(color: Int) {
        progressColor = color
        invalidate()
    }

    fun setTrackColor(color: Int) {
        trackColor = color
        trackPaint.color = color
        invalidate()
    }
}
