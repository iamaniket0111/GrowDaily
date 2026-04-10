package com.anitech.growdaily.view

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.anitech.growdaily.R
import com.anitech.growdaily.lightenHeatmapColor
import com.anitech.growdaily.resolveHeatmapProgressColor

class BarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var score: Float = 0f
    private val maxScore = 10
    private val defaultBarColor = ContextCompat.getColor(context, R.color.task_bar_fill)
    private val completeBarColor = ContextCompat.getColor(context, R.color.task_bar_fill_complete)
    private var barColor: Int = defaultBarColor
    private var emptyColor: Int = lightenHeatmapColor(barColor, 0.88f)
    private var useProgressPalette: Boolean = false
    private var useSolidBarColor: Boolean = false
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = barColor
    }
    private val cornerRadius = 20f.dpToPx()

    fun setScore(value: Float) {
        score = value
        invalidate()
    }

    fun setBarColor(color: Int) {
        barColor = color
        emptyColor = lightenHeatmapColor(color, 0.88f)
        invalidate()
    }

    fun setUseProgressPalette(enabled: Boolean) {
        useProgressPalette = enabled
        invalidate()
    }

    fun setUseSolidBarColor(enabled: Boolean) {
        useSolidBarColor = enabled
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val usableHeight = height.toFloat()
        val barHeight = (score / maxScore.toFloat()) * usableHeight
        val top = usableHeight - barHeight
        val rect = RectF(0f, top, width.toFloat(), usableHeight)

        barPaint.color = if (useSolidBarColor) {
            barColor
        } else if (useProgressPalette) {
            val progressPercent = ((score / maxScore.toFloat()) * 100).toInt()
            resolveHeatmapProgressColor(barColor, progressPercent, emptyColor)
        } else {
            if (score.toInt() == 10) completeBarColor else defaultBarColor
        }

        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, barPaint)
    }

    private fun Float.dpToPx(): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this,
            Resources.getSystem().displayMetrics
        )
}
