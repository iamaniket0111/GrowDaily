package com.anitech.growdaily.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
) : ViewGroup(context, attrs) {

    private val rows = 7
    private val boxGap = dp(1)
    private val monthGap = dp(6)

    private var targetYear: Int = LocalDate.now().year
    private val cells = mutableListOf<LocalDate?>()
    private val monthViews = mutableListOf<TextView>()

    // Track month positions: month -> (startCellIndex, endCellIndex)
    private val monthRanges = mutableMapOf<Int, Pair<Int, Int>>()

    private var boxWidth = 0
    private var boxHeight = 0

    init {
        setYear(LocalDate.now().year)
    }

    fun setYear(year: Int) {
        targetYear = year
        val startDate = LocalDate.of(year, 1, 1)
        val endDate = LocalDate.of(year, 12, 31)

        cells.clear()
        monthViews.clear()
        monthRanges.clear()
        removeAllViews()

        // Create calendar cells first
        var monthCursor = startDate
        var cellIndex = 0
        var currentMonth = -1
        var monthStartIndex = -1

        while (!monthCursor.isAfter(endDate)) {
            val monthStart = monthCursor.withDayOfMonth(1)
            val monthEnd = monthCursor.withDayOfMonth(monthCursor.lengthOfMonth())

            // Get the Monday of the week containing month start
            val weekStart = monthStart.minusDays((monthStart.dayOfWeek.value - 1).toLong())
            // Get the Sunday of the week containing month end
            val weekEnd = monthEnd.plusDays((7 - monthEnd.dayOfWeek.value).toLong())

            // Track month start
            if (monthCursor.monthValue != currentMonth) {
                if (currentMonth != -1) {
                    monthRanges[currentMonth] = Pair(monthStartIndex, cellIndex - 1)
                }
                currentMonth = monthCursor.monthValue
                monthStartIndex = cellIndex
            }

            var cursor = weekStart
            while (!cursor.isAfter(weekEnd)) {
                val visible = !cursor.isBefore(monthStart) && !cursor.isAfter(monthEnd)
                cells.add(if (visible) cursor else null)

                addView(
                    LayoutInflater.from(context)
                        .inflate(R.layout.item_box, this, false).apply {
                            visibility = if (visible) View.VISIBLE else View.INVISIBLE
                        }
                )

                cursor = cursor.plusDays(1)
                cellIndex++
            }

            monthCursor = monthCursor.plusMonths(1)
        }

        // Close last month
        if (currentMonth != -1) {
            monthRanges[currentMonth] = Pair(monthStartIndex, cellIndex - 1)
        }

        // Create month labels AFTER boxes
        for (month in 1..12) {
            val monthView = LayoutInflater.from(context)
                .inflate(R.layout.text_heapmap_layout, this, false) as TextView
            monthView.text = LocalDate.of(year, month, 1)
                .month
                .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                .take(3)
                .uppercase()
            monthViews.add(monthView)
            addView(monthView)
        }

        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (childCount == 0 || cells.isEmpty()) {
            setMeasuredDimension(0, 0)
            return
        }

        // Measure a sample box (first child is a box)
        val boxView = getChildAt(0)
        measureChild(boxView, widthMeasureSpec, heightMeasureSpec)

        boxWidth = boxView.measuredWidth + boxGap
        boxHeight = boxView.measuredHeight + boxGap

        // Calculate total width
        val totalWeeks = cells.size / rows
        val monthGapsTotal = 11 * monthGap // 11 gaps between 12 months
        val totalWidth = totalWeeks * boxWidth + monthGapsTotal

        // Height: boxes only (labels will be positioned at bottom)
        val totalHeight = rows * boxHeight + dp(20) // Add space for labels

        setMeasuredDimension(totalWidth, totalHeight)

        // Measure all children
        for (i in 0 until childCount) {
            measureChild(getChildAt(i), widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (childCount == 0 || cells.isEmpty()) return

        val totalCells = cells.size
        val boxesCount = totalCells
        val monthLabelStartIndex = boxesCount // Month labels start after boxes

        // Layout boxes first
        var xOffset = 0
        var currentMonth = 0
        var monthStartX = 0

        for (i in 0 until boxesCount) {
            if (i >= childCount) break

            val week = i / rows
            val dayInWeek = i % rows

            // Check if month changed
            val date = cells[i]
            date?.let {
                if (it.monthValue != currentMonth) {
                    if (currentMonth != 0) {
                        // Add gap between months
                        xOffset += monthGap
                    }
                    currentMonth = it.monthValue
                    monthStartX = week * boxWidth + xOffset
                }
            }

            val left = week * boxWidth + xOffset
            val top = dayInWeek * boxHeight

            val boxView = getChildAt(i)
            if (cells[i] != null && boxView.visibility == View.VISIBLE) {
                boxView.layout(
                    left,
                    top,
                    left + boxWidth - boxGap,
                    top + boxHeight - boxGap
                )
            }
        }

        // Now layout month labels at exact bottom center of each month
        for (month in 1..12) {
            val monthRange = monthRanges[month] ?: continue
            val monthView = monthViews[month - 1]
            val monthViewIndex = monthLabelStartIndex + month - 1

            if (monthViewIndex >= childCount) continue

            // Find the visual X range for this month
            val (startCellIndex, endCellIndex) = monthRange

            // Find first visible cell in month to get left position
            var firstVisibleWeek = -1
            var lastVisibleWeek = -1
            for (i in startCellIndex..endCellIndex) {
                if (cells[i] != null) {
                    val week = i / rows
                    if (firstVisibleWeek == -1) firstVisibleWeek = week
                    lastVisibleWeek = week
                }
            }

            if (firstVisibleWeek == -1 || lastVisibleWeek == -1) continue

            // Calculate month's visual boundaries
            var monthLeftX = 0
            var monthRightX = 0
            var foundLeft = false

            for (i in 0 until boxesCount) {
                val week = i / rows
                val dayInWeek = i % rows

                if (week == firstVisibleWeek && !foundLeft) {
                    // This is where we need to recalculate X for this week
                    // Count month gaps before this month
                    val monthOfCell = cells[i]?.monthValue ?: continue
                    val gapsBefore = countMonthGapsBefore(monthOfCell)
                    monthLeftX = week * boxWidth + gapsBefore * monthGap
                    foundLeft = true
                }

                if (week == lastVisibleWeek) {
                    val monthOfCell = cells[i]?.monthValue ?: continue
                    val gapsBefore = countMonthGapsBefore(monthOfCell)
                    monthRightX = (week + 1) * boxWidth + gapsBefore * monthGap
                }
            }

            // Calculate month width
            val monthWidth = monthRightX - monthLeftX

            // Center the label horizontally
            val labelLeft = monthLeftX + (monthWidth - monthView.measuredWidth) / 2

            // Position at bottom (just below the last row of boxes)
            val labelTop = rows * boxHeight

            monthView.layout(
                labelLeft,
                labelTop,
                labelLeft + monthView.measuredWidth,
                labelTop + monthView.measuredHeight
            )
            monthView.visibility = View.VISIBLE
        }
    }

    private fun countMonthGapsBefore(targetMonth: Int): Int {
        var gaps = 0
        var currentMonth = 0

        for (i in cells.indices) {
            val date = cells[i] ?: continue
            if (date.monthValue != currentMonth) {
                if (currentMonth != 0 && date.monthValue <= targetMonth) {
                    gaps++
                }
                currentMonth = date.monthValue
                if (currentMonth == targetMonth) break
            }
        }

        return gaps
    }

    fun bindHeatmap(
        taskAddedDate: LocalDate,
        progressByDate: Map<LocalDate, Int>,
        unavailableDates: Set<LocalDate>,
        activeColor: Int
    ) {
        val emptyColor = ColorUtils.blendARGB(
            ContextCompat.getColor(context, R.color.task_card_stroke),
            activeColor,
            0.12f
        )
        val futureColor = ContextCompat.getColor(context, R.color.task_card_stroke)
        val beforeStartColor = ContextCompat.getColor(context, R.color.task_card_stroke)
        val dashColor = ContextCompat.getColor(context, R.color.task_text_secondary)

        val today = LocalDate.now()

        for (i in cells.indices) {

            val date = cells[i] ?: continue
            val box = getChildAt(i) as? TextView ?: continue
            box.text = ""

            when {
                date.isAfter(today) -> {
                    box.setBackgroundColor(futureColor)
                }

                date.isBefore(taskAddedDate) -> {
                    box.setBackgroundColor(beforeStartColor)
                }

                unavailableDates.contains(date) -> {
                    box.setBackgroundColor(emptyColor)
                    box.text = "-"
                    box.setTextColor(dashColor)
                }

                else -> {
                    box.setBackgroundColor(
                        resolveHeatmapProgressColor(
                            activeColor = activeColor,
                            progressPercent = progressByDate[date] ?: 0,
                            emptyColor = emptyColor
                        )
                    )
                }
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
