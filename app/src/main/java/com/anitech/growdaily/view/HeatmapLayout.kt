package com.anitech.growdaily.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.anitech.growdaily.R
import com.anitech.growdaily.resolveHeatmapProgressColor
import java.time.DayOfWeek
import java.time.LocalDate

class HeatmapLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val boxSize = dp(9f)
    private val boxGap = dp(1f)
    private val monthGap = dp(6f)
    private val cornerRadius = dp(2f)
    private val rows = 7
    private val cellStep get() = boxSize + boxGap

    private var startDate: LocalDate = LocalDate.now().minusYears(1)
    private var endDate: LocalDate = LocalDate.now()

    private val cells = mutableListOf<LocalDate?>()
    private val cellColors = mutableMapOf<Int, Int>()
    private val unavailableCellIndexes = mutableSetOf<Int>()

    private var activeColor = ContextCompat.getColor(context, R.color.brand_blue)
    private var emptyColor = ContextCompat.getColor(context, R.color.task_done_track)
    private val beforeStartColor = ContextCompat.getColor(context, R.color.task_card_stroke)
    private val dashColor = ContextCompat.getColor(context, R.color.task_text_secondary)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = dashColor
        textAlign = Paint.Align.CENTER
        textSize = dp(7f)
    }
    private val rect = RectF()

    private val columnX = mutableListOf<Float>()
    private var measuredW = 0
    private var measuredH = 0

    init {
        setDateRange(LocalDate.now().minusYears(1), LocalDate.now())
    }

    fun setDateRange(start: LocalDate, end: LocalDate) {
        startDate = start
        endDate = end
        rebuildCells()
    }

    fun bindHeatmap(
        taskAddedDate: LocalDate,
        progressByDate: Map<LocalDate, Int>,
        unavailableDates: Set<LocalDate>,
        activeColor: Int
    ) {
        this.activeColor = activeColor
        this.emptyColor = ColorUtils.blendARGB(
            ContextCompat.getColor(context, R.color.task_card_stroke),
            activeColor,
            0.12f
        )

        cellColors.clear()
        unavailableCellIndexes.clear()
        for (i in cells.indices) {
            val date = cells[i] ?: continue
            cellColors[i] = when {
                date.isBefore(taskAddedDate) -> beforeStartColor
                unavailableDates.contains(date) -> {
                    unavailableCellIndexes.add(i)
                    emptyColor
                }
                else -> resolveHeatmapProgressColor(
                    activeColor = activeColor,
                    progressPercent = progressByDate[date] ?: 0,
                    emptyColor = emptyColor
                )
            }
        }
        invalidate()
    }

    private fun rebuildCells() {
        cells.clear()
        columnX.clear()

        var monthCursor = startDate.withDayOfMonth(1)
        while (!monthCursor.isAfter(endDate)) {
            val monthStart = monthCursor
            val monthEnd = monthCursor.withDayOfMonth(monthCursor.lengthOfMonth())
            val effectiveStart = maxOf(monthStart, startDate)
            val effectiveEnd = minOf(monthEnd, endDate)

            val weekStart = monthStart.with(DayOfWeek.MONDAY)
            val weekEnd = monthEnd.with(DayOfWeek.SUNDAY)

            var cursor = weekStart
            while (!cursor.isAfter(weekEnd)) {
                val visible = !cursor.isBefore(effectiveStart) && !cursor.isAfter(effectiveEnd)
                cells.add(if (visible) cursor else null)
                cursor = cursor.plusDays(1)
            }
            monthCursor = monthCursor.plusMonths(1)
        }

        recomputeLayout()
        requestLayout()
        invalidate()
    }

    private fun recomputeLayout() {
        columnX.clear()
        if (cells.isEmpty()) return

        val lastVisible = cells.indexOfLast { it != null }
        if (lastVisible == -1) return

        var x = 0f
        var dayInWeek = 0

        for (i in 0..lastVisible) {
            if (dayInWeek == 0) {
                columnX.add(x)
            }
            dayInWeek++
            if (dayInWeek == rows) {
                dayInWeek = 0
                if (i < lastVisible) {
                    val curr = cells[i]
                    val next = cells.subList(i + 1, minOf(i + 8, cells.size)).firstOrNull { it != null }
                    x += if (curr != null && next != null && curr.monthValue != next.monthValue) {
                        cellStep + monthGap
                    } else {
                        cellStep
                    }
                }
            }
        }

        val totalCols = columnX.size
        measuredW = if (totalCols == 0) 0 else (columnX.last() + boxSize).toInt()
        measuredH = (rows * cellStep - boxGap).toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(measuredW, measuredH)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cells.isEmpty() || columnX.isEmpty()) return

        val lastVisible = cells.indexOfLast { it != null }
        if (lastVisible == -1) return

        var colIdx = 0
        var dayInWeek = 0

        for (i in 0..lastVisible) {
            val date = cells[i]
            if (date != null) {
                val x = columnX[colIdx]
                val y = dayInWeek * cellStep
                paint.color = cellColors[i] ?: emptyColor
                rect.set(x, y, x + boxSize, y + boxSize)
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
                if (unavailableCellIndexes.contains(i)) {
                    val dashX = x + (boxSize / 2f)
                    val dashY = y + boxSize - ((boxSize - dashPaint.textSize) / 2f)
                    canvas.drawText("-", dashX, dashY, dashPaint)
                }
            }

            dayInWeek++
            if (dayInWeek == rows) {
                dayInWeek = 0
                colIdx++
            }
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
