package com.anitech.growdaily

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class SquareBorderProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ---------- Config ----------
    private val strokeWidth = 6f
    private val cornerRadius = 25f

    private var progressPercent = 0f
    private var progressColor: Int = Color.parseColor("#3B82F6")

    // ---------- Paints ----------
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = lightenColor(progressColor)
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@SquareBorderProgressView.strokeWidth
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#DDDDDD")
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@SquareBorderProgressView.strokeWidth
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = progressColor
    }

    // ---------- Path helpers ----------
    private val borderPath = Path()
    private val progressPath = Path()
    private val pathMeasure = PathMeasure()

    // ---------- Public API ----------
    fun setProgress(progress: Int) {
        progressPercent = progress.coerceIn(0, 100).toFloat()
        invalidate()
    }

    fun setProgressColor(color: Int) {
        progressColor = color
        progressPaint.color = color
        fillPaint.color = lightenColor(color)
        invalidate()
    }

    // ---------- Draw ----------
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawCenteredSquarePie(canvas)
        drawBorderBackground(canvas)
        drawBorderProgress(canvas)
    }

    // --------------------------------------------------
    // INNER FILL
    // --------------------------------------------------
    private fun drawCenteredSquarePie(canvas: Canvas) {

        if (progressPercent <= 0f) return

        val size = min(width, height).toFloat()

        val left = (width - size) / 2f
        val top = (height - size) / 2f
        val right = left + size
        val bottom = top + size

        val rect = RectF(left, top, right, bottom)

        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
    }

    // --------------------------------------------------
    // BACKGROUND BORDER
    // --------------------------------------------------
    private fun drawBorderBackground(canvas: Canvas) {

        val halfStroke = strokeWidth / 2f

        val rect = RectF(
            halfStroke,
            halfStroke,
            width - halfStroke,
            height - halfStroke
        )

        borderPath.reset()
        borderPath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)

        canvas.drawPath(borderPath, bgPaint)
    }

    // --------------------------------------------------
    // PROGRESS BORDER
    // --------------------------------------------------
    private fun drawBorderProgress(canvas: Canvas) {

        if (progressPercent <= 0f) return

        val halfStroke = strokeWidth / 2f

        val rect = RectF(
            halfStroke,
            halfStroke,
            width - halfStroke,
            height - halfStroke
        )

        borderPath.reset()
        borderPath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)

        pathMeasure.setPath(borderPath, true)

        val length = pathMeasure.length
        val progressLength = length * (progressPercent / 100f)

        progressPath.reset()
        pathMeasure.getSegment(0f, progressLength, progressPath, true)

        canvas.drawPath(progressPath, progressPaint)
    }

    // --------------------------------------------------
    // UTIL
    // --------------------------------------------------
    private fun lightenColor(color: Int, factor: Float = 0.8f): Int {

        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)

        return Color.rgb(
            (r + (255 - r) * factor).toInt(),
            (g + (255 - g) * factor).toInt(),
            (b + (255 - b) * factor).toInt()
        )
    }
}