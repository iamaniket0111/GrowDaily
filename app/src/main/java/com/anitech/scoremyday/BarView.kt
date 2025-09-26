package com.anitech.scoremyday

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

class BarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var score: Float = 0f
    private val maxScore = 10
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#708CFF")
    }
    private val cornerRadius = 20f.dpToPx()

    fun setScore(value: Float) {
        score = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val usableHeight = height.toFloat()
        val barHeight = (score / maxScore.toFloat()) * usableHeight
        val top = usableHeight - barHeight
        val rect = RectF(0f, top, width.toFloat(), usableHeight)

        // Green if score == 10
        barPaint.color = if (score.toInt() == 10) Color.parseColor("#4CAF50") else Color.parseColor("#708CFF")

        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, barPaint)
    }

    private fun Float.dpToPx(): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this,
            Resources.getSystem().displayMetrics
        )
}
