package com.anitech.growdaily.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.anitech.growdaily.R
import kotlin.math.min

class SquareBorderProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    // ---------- Config ----------
    private val strokeWidth = 6f
    private val cornerRadius = 25f
    private var progressPercent = 0f
    private var progressColor: Int = ContextCompat.getColor(context, R.color.brand_blue)
    private var trackColor: Int = ContextCompat.getColor(context, R.color.task_done_track)
    // ---------- Paints ----------
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorWithAlpha(progressColor, 0.22f)
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@SquareBorderProgressView.strokeWidth
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = trackColor
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
        fillPaint.color = colorWithAlpha(color, 0.22f)
        invalidate()
    }
    fun setTrackColor(color: Int) {
        trackColor = color
        bgPaint.color = color
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
        val outerRect = buildSquareRect()
        val inset = strokeWidth * 0.5f
        val rect = RectF(
            outerRect.left + inset,
            outerRect.top + inset,
            outerRect.right - inset,
            outerRect.bottom - inset
        )
        val progress = progressPercent / 100f
        // Perimeter split: 0.5 (top-center→right), 1.0 (right edge), 1.0 (bottom), 1.0 (left), 0.5 (top-left→center)
        // Total = 4.0 units, but start is at cx so first segment is half-width
        val totalPerimeter = 4f
        val current = progress * totalPerimeter // 0..4
        val cx = rect.centerX()
        val cy = rect.centerY()
        val halfW = rect.width() / 2f
        val path = Path()
        path.moveTo(cx, cy)
        path.lineTo(cx, rect.top) // anchor to top-center
        when {
            // Segment 1: top-center → top-right (0 → 0.5)
            current <= 0.5f -> {
                val x = cx + halfW * (current / 0.5f)
                path.lineTo(x, rect.top)
            }
            // Segment 2: top-right corner → bottom-right (0.5 → 1.5)
            current <= 1.5f -> {
                path.lineTo(rect.right, rect.top)
                val y = rect.top + rect.height() * ((current - 0.5f) / 1f)
                path.lineTo(rect.right, y)
            }
            // Segment 3: bottom-right → bottom-left (1.5 → 2.5)
            current <= 2.5f -> {
                path.lineTo(rect.right, rect.top)
                path.lineTo(rect.right, rect.bottom)
                val x = rect.right - rect.width() * ((current - 1.5f) / 1f)
                path.lineTo(x, rect.bottom)
            }
            // Segment 4: bottom-left → top-left (2.5 → 3.5)
            current <= 3.5f -> {
                path.lineTo(rect.right, rect.top)
                path.lineTo(rect.right, rect.bottom)
                path.lineTo(rect.left, rect.bottom)
                val y = rect.bottom - rect.height() * ((current - 2.5f) / 1f)
                path.lineTo(rect.left, y)
            }
            // Segment 5: top-left → top-center (3.5 → 4.0)
            else -> {
                path.lineTo(rect.right, rect.top)
                path.lineTo(rect.right, rect.bottom)
                path.lineTo(rect.left, rect.bottom)
                path.lineTo(rect.left, rect.top)
                val x = rect.left + halfW * ((current - 3.5f) / 0.5f)
                path.lineTo(x, rect.top)
            }
        }
        path.close()
        val clipPath = Path().apply {
            addRoundRect(
                rect,
                effectiveCornerRadius(rect),
                effectiveCornerRadius(rect),
                Path.Direction.CW
            )
        }
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawPath(path, fillPaint)
        canvas.restore()
    }
    // --------------------------------------------------
    // BACKGROUND BORDER
    // --------------------------------------------------
    private fun drawBorderBackground(canvas: Canvas) {
        borderPath.reset()
        buildProgressPath(borderPath, buildSquareRect())
        canvas.drawPath(borderPath, bgPaint)
    }
    // --------------------------------------------------
    // PROGRESS BORDER
    // --------------------------------------------------
    private fun drawBorderProgress(canvas: Canvas) {
        if (progressPercent <= 0f) return
        borderPath.reset()
        buildProgressPath(borderPath, buildSquareRect())
        pathMeasure.setPath(borderPath, true)
        val length = pathMeasure.length
        val progressLength = length * (progressPercent / 100f)
        progressPath.reset()
        pathMeasure.getSegment(0f, progressLength, progressPath, true)
        canvas.drawPath(progressPath, progressPaint)
    }
    private fun buildSquareRect(): RectF {
        val halfStroke = strokeWidth / 2f
        val size = min(width, height).toFloat() - strokeWidth
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        return RectF(left, top, left + size, top + size)
    }
    private fun effectiveCornerRadius(rect: RectF): Float {
        return min(cornerRadius, min(rect.width(), rect.height()) / 2f)
    }
    // Start the path from the top-center so progress visually begins there.
    private fun buildProgressPath(path: Path, rect: RectF) {
        val radius = effectiveCornerRadius(rect)
        val centerX = rect.centerX()
        path.moveTo(centerX, rect.top)
        path.lineTo(rect.right - radius, rect.top)
        path.arcTo(
            RectF(rect.right - 2 * radius, rect.top, rect.right, rect.top + 2 * radius),
            -90f,
            90f,
            false
        )
        path.lineTo(rect.right, rect.bottom - radius)
        path.arcTo(
            RectF(rect.right - 2 * radius, rect.bottom - 2 * radius, rect.right, rect.bottom),
            0f,
            90f,
            false
        )
        path.lineTo(rect.left + radius, rect.bottom)
        path.arcTo(
            RectF(rect.left, rect.bottom - 2 * radius, rect.left + 2 * radius, rect.bottom),
            90f,
            90f,
            false
        )
        path.lineTo(rect.left, rect.top + radius)
        path.arcTo(
            RectF(rect.left, rect.top, rect.left + 2 * radius, rect.top + 2 * radius),
            180f,
            90f,
            false
        )
        path.lineTo(centerX, rect.top)
    }
    // --------------------------------------------------
    // UTIL
    // --------------------------------------------------
    fun colorWithAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
