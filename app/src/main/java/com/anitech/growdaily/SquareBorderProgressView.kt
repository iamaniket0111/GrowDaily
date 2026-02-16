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

    private val strokeWidth = 6f
    private val segmentCount = 10
    private val gapSizePx = context.resources.displayMetrics.density * 1f

    private var progressPercent = 0f
    private var progressColor: Int = Color.BLUE

    // -------- PAINTS --------
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = lightenColor(progressColor)
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#DDDDDD")
        strokeWidth = this@SquareBorderProgressView.strokeWidth
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@SquareBorderProgressView.strokeWidth
        strokeCap = Paint.Cap.BUTT
        color = progressColor
    }

    private val path = Path()
    private val pathMeasure = PathMeasure()

    // -------- PUBLIC API --------
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1️⃣ INNER PIE (BACKGROUND)
        drawCenteredSquarePie(canvas)

        // 2️⃣ BORDER BACKGROUND
        drawBorder(canvas)

        // 3️⃣ BORDER PROGRESS (TOP MOST)
        drawBorderProgress(canvas)
    }

    // --------------------------------------------------
    // INNER SQUARE PIE (NO PADDING, FULL WIDTH)
    // --------------------------------------------------
    private fun drawCenteredSquarePie(canvas: Canvas) {
        if (progressPercent <= 0f) return

         val size = min(width, height).toFloat()

        val left = (width - size) / 2f
        val top = (height - size) / 2f
        val right = left + size
        val bottom = top + size

        val cx = width / 2f
        val cy = height / 2f

        val halfTop = size / 2f
        val segLens = floatArrayOf(
            halfTop, size, size, size, halfTop
        )

        val totalPerimeter = segLens.sum()
        val fillLength = totalPerimeter * (progressPercent / 100f)

        val fillPath = Path()
        fillPath.moveTo(cx, cy)
        fillPath.lineTo(cx, top)

        var remaining = fillLength

        fun drawSeg(
            sx: Float, sy: Float,
            ex: Float, ey: Float,
            len: Float
        ): Boolean {
            if (remaining <= 0f) return false
            if (remaining >= len) {
                fillPath.lineTo(ex, ey)
                remaining -= len
                return true
            }
            val t = remaining / len
            fillPath.lineTo(
                sx + (ex - sx) * t,
                sy + (ey - sy) * t
            )
            remaining = 0f
            return false
        }

        // top-center → top-right
        if (!drawSeg(cx, top, right, top, segLens[0])) {
            fillPath.close(); canvas.drawPath(fillPath, fillPaint); return
        }

        // top-right → bottom-right
        if (!drawSeg(right, top, right, bottom, segLens[1])) {
            fillPath.close(); canvas.drawPath(fillPath, fillPaint); return
        }

        // bottom-right → bottom-left
        if (!drawSeg(right, bottom, left, bottom, segLens[2])) {
            fillPath.close(); canvas.drawPath(fillPath, fillPaint); return
        }

        // bottom-left → top-left
        if (!drawSeg(left, bottom, left, top, segLens[3])) {
            fillPath.close(); canvas.drawPath(fillPath, fillPaint); return
        }

        // top-left → top-center
        drawSeg(left, top, cx, top, segLens[4])

        fillPath.close()
        canvas.drawPath(fillPath, fillPaint)
    }

    // --------------------------------------------------
    // BORDER BACKGROUND
    // --------------------------------------------------
    private fun drawBorder(canvas: Canvas) {
        val halfStroke = strokeWidth / 2
        val rect = RectF(
            halfStroke,
            halfStroke,
            width - halfStroke,
            height - halfStroke
        )
        canvas.drawRect(rect, bgPaint)
    }

    // --------------------------------------------------
    // BORDER PROGRESS (SEGMENTED)
    // --------------------------------------------------
    private fun drawBorderProgress(canvas: Canvas) {
        val halfStroke = strokeWidth / 2
        val rect = RectF(
            halfStroke,
            halfStroke,
            width - halfStroke,
            height - halfStroke
        )

        path.reset()
        path.moveTo(rect.left, rect.top)
        path.lineTo(rect.right, rect.top)
        path.lineTo(rect.right, rect.bottom)
        path.lineTo(rect.left, rect.bottom)
        path.close()

        pathMeasure.setPath(path, false)

        val totalLength = pathMeasure.length
        val segmentLength = totalLength / segmentCount
        val completedSegments = (progressPercent / 10).toInt()

        var start = 0f
        repeat(completedSegments) {
            val end = start + segmentLength - gapSizePx
            if (end > start) {
                val segPath = Path()
                pathMeasure.getSegment(start, end, segPath, true)
                canvas.drawPath(segPath, progressPaint)
            }
            start += segmentLength
        }
    }

    // --------------------------------------------------
    // COLOR HELPER
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
