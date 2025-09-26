package com.anitech.scoremyday

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.anitech.scoremyday.CommonMethods.Companion.getTodayDate
import com.anitech.scoremyday.data_class.DailyScore

class ScoreBarBackground @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dotLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val avgLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ADD8E6")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private var avgScore: Float = 0f
    private val maxScore = 10

    fun setAverageScore(score: Float) {
        avgScore = score
       // requestLayout()
        invalidate()
    }

    fun setData(data: List<DailyScore>) {
        val today = getTodayDate()
        val filteredData = data.filter { it.score > 0 || it.date == today }

        val avgScore = if (filteredData.isNotEmpty()) {
            (filteredData.sumOf { it.score.toDouble() } / filteredData.size).toFloat()
        } else {
            0f
        }

        setAverageScore(avgScore)
    }



    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val topLineY = 0f
        val bottomLineY = height.toFloat()

        // top line
        canvas.drawLine(0f, topLineY, width.toFloat(), topLineY, dotLinePaint)

        // bottom line
        canvas.drawLine(0f, bottomLineY, width.toFloat(), bottomLineY, dotLinePaint)

        // average line
        if (avgScore > 0f) {
            val usableHeight = height.toFloat()
            val avgLineY = bottomLineY - (avgScore / maxScore) * usableHeight
            canvas.drawLine(0f, avgLineY, width.toFloat(), avgLineY, avgLinePaint)
        }
    }
}

