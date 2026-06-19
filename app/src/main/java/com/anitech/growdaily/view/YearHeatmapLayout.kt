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
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class YearHeatmapLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class MonthLabel(
        val month: Int,
        val text: String,
        val centerX: Float
    )

    private val rows = 7
    private val boxSize = dp(9f)
    private val boxGap = dp(1f)
    private val monthGap = dp(6f)
    private val cornerRadius = dp(2f)
    private val labelTopGap = dp(6f)
    private val labelHeight = dp(14f)
    private val cellStep get() = boxSize + boxGap

    private var targetYear: Int = LocalDate.now().year
    private val cells = mutableListOf<LocalDate?>()
    private val cellColors = mutableMapOf<Int, Int>()
    private val unavailableCellIndexes = mutableSetOf<Int>()
    private val columnX = mutableListOf<Float>()
    private val columnMonth = mutableListOf<Int>()
    private val monthLabels = mutableListOf<MonthLabel>()

    private val emptyBaseColor = ContextCompat.getColor(context, R.color.task_card_stroke)
    private val dashColor = ContextCompat.getColor(context, R.color.task_text_secondary)

    private var measuredW = 0
    private var measuredH = 0

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = dashColor
        textAlign = Paint.Align.CENTER
        textSize = sp(6f)
        isFakeBoldText = true
    }
    private val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.task_text_secondary)
        textAlign = Paint.Align.CENTER
        textSize = sp(11f)
    }
    private val rect = RectF()

    init {
        setYear(LocalDate.now().year)
    }

    fun setYear(year: Int) {
        if (targetYear == year && cells.isNotEmpty()) return
        targetYear = year
        rebuildCells()
    }

    private fun rebuildCells() {
        cells.clear()
        columnX.clear()
        columnMonth.clear()
        monthLabels.clear()

        for (month in 1..12) {
            val monthStart = LocalDate.of(targetYear, month, 1)
            val monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth())
            val weekStart = monthStart.minusDays((monthStart.dayOfWeek.value - 1).toLong())
            val weekEnd = monthEnd.plusDays((7 - monthEnd.dayOfWeek.value).toLong())

            var cursor = weekStart
            while (!cursor.isAfter(weekEnd)) {
                val visible = !cursor.isBefore(monthStart) && !cursor.isAfter(monthEnd)
                cells.add(if (visible) cursor else null)
                cursor = cursor.plusDays(1)
            }
        }

        recomputeGeometry()
        requestLayout()
        invalidate()
    }

    private fun recomputeGeometry() {
        columnX.clear()
        columnMonth.clear()
        monthLabels.clear()

        if (cells.isEmpty()) {
            measuredW = 0
            measuredH = 0
            return
        }

        val totalColumns = cells.size / rows
        var x = 0f

        for (column in 0 until totalColumns) {
            val start = column * rows
            val end = start + rows
            val visibleMonth = cells.subList(start, end)
                .firstNotNullOfOrNull { it?.monthValue }
                ?: continue

            columnX.add(x)
            columnMonth.add(visibleMonth)

            x += boxSize
            if (column < totalColumns - 1) {
                val nextMonth = cells.subList(end, end + rows)
                    .firstNotNullOfOrNull { it?.monthValue }
                x += if (nextMonth != null && nextMonth != visibleMonth) {
                    boxGap + monthGap
                } else {
                    boxGap
                }
            }
        }

        for (month in 1..12) {
            val firstColumn = columnMonth.indexOfFirst { it == month }
            val lastColumn = columnMonth.indexOfLast { it == month }
            if (firstColumn == -1 || lastColumn == -1) continue

            val centerX = (columnX[firstColumn] + columnX[lastColumn] + boxSize) / 2f
            val text = LocalDate.of(targetYear, month, 1)
                .month
                .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                .take(3)
                .uppercase()

            monthLabels.add(MonthLabel(month = month, text = text, centerX = centerX))
        }

        measuredW = if (columnX.isEmpty()) 0 else (columnX.last() + boxSize).toInt()
        measuredH = (rows * cellStep + labelTopGap + labelHeight).toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(measuredW, measuredH)
    }

    fun bindHeatmap(
        taskAddedDate: LocalDate,
        progressByDate: Map<LocalDate, Int>,
        unavailableDates: Set<LocalDate>,
        activeColor: Int
    ) {
        val emptyColor = ColorUtils.blendARGB(emptyBaseColor, activeColor, 0.12f)
        val futureColor = emptyBaseColor
        val beforeStartColor = emptyBaseColor
        val today = LocalDate.now()

        cellColors.clear()
        unavailableCellIndexes.clear()

        for (i in cells.indices) {
            val date = cells[i] ?: continue
            cellColors[i] = when {
                date.isAfter(today) -> futureColor
                date.isBefore(taskAddedDate) -> beforeStartColor
                unavailableDates.contains(date) -> {
                    unavailableCellIndexes.add(i)
                    beforeStartColor
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cells.isEmpty() || columnX.isEmpty()) return

        var currentColumn = 0
        var dayInWeek = 0

        for (i in cells.indices) {
            val date = cells[i]
            if (date != null) {
                val x = columnX[currentColumn]
                val y = dayInWeek * cellStep
                cellPaint.color = cellColors[i] ?: emptyBaseColor
                rect.set(x, y, x + boxSize, y + boxSize)
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cellPaint)

                if (unavailableCellIndexes.contains(i)) {
                    val dashX = x + boxSize / 2f
                    val dashBaseline = y + boxSize - ((boxSize - dashPaint.textSize) / 2f)
                    canvas.drawText("-", dashX, dashBaseline, dashPaint)
                }
            }

            dayInWeek++
            if (dayInWeek == rows) {
                dayInWeek = 0
                currentColumn++
            }
        }

        val textBaseline =
            rows * cellStep + labelTopGap + labelHeight - ((labelHeight - monthPaint.textSize) / 2f)

        monthLabels.forEach { label ->
            canvas.drawText(label.text, label.centerX, textBaseline, monthPaint)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
