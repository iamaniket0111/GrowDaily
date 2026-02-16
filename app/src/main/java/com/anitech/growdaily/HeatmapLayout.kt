package com.anitech.growdaily

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import java.time.DayOfWeek
import java.time.LocalDate
 import kotlin.math.ceil

class HeatmapLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    private val rows = 7
    private val boxGap = dp(1)
    private val monthGap = dp(6)

    private var startDate: LocalDate = LocalDate.now().minusYears(1)
    private var endDate: LocalDate = LocalDate.now()

    private val cells = mutableListOf<LocalDate?>()

    init {
        setDateRange(endDate.minusYears(1), endDate)
    }

    // --------------------------------------------------
    // PUBLIC API
    // --------------------------------------------------
    fun setDateRange(start: LocalDate, end: LocalDate) {
        startDate = start
        endDate = end

        cells.clear()
        removeAllViews()

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

                val visible =
                    !cursor.isBefore(effectiveStart) &&
                            !cursor.isAfter(effectiveEnd)

                cells.add(if (visible) cursor else null)

                addView(
                    LayoutInflater.from(context)
                        .inflate(R.layout.item_box, this, false).apply {
                            visibility = if (visible) View.VISIBLE else View.INVISIBLE
                        }
                )

                cursor = cursor.plusDays(1)
            }

            monthCursor = monthCursor.plusMonths(1)
        }

        requestLayout()
        invalidate()
    }

    // --------------------------------------------------
    // MEASURE (FIXED 🔥)
    // --------------------------------------------------
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (childCount == 0) {
            setMeasuredDimension(0, 0)
            return
        }

        val sample = getChildAt(0)
        measureChild(sample, widthMeasureSpec, heightMeasureSpec)

        val boxWidth = sample.measuredWidth + boxGap
        val boxHeight = sample.measuredHeight + boxGap

        val lastVisibleIndex = cells.indexOfLast { it != null }
        if (lastVisibleIndex == -1) {
            setMeasuredDimension(0, 0)
            return
        }

        // Calculate actual weeks needed for visible cells
        val totalWeeks = ceil((lastVisibleIndex + 1) / 7.0).toInt()

        // Calculate month gaps between visible cells
        var monthGapTotal = 0
        for (i in 0 until lastVisibleIndex) {
            val curr = cells[i]
            val next = cells[i + 1]
            if (curr != null && next != null && curr.monthValue != next.monthValue) {
                monthGapTotal += monthGap
            }
        }

        val width = totalWeeks * boxWidth + monthGapTotal
        val height = rows * boxHeight

        setMeasuredDimension(width, height)

        // Measure all children
        for (i in 0 until childCount) {
            measureChild(getChildAt(i), widthMeasureSpec, heightMeasureSpec)
        }
    }
    // --------------------------------------------------
    // LAYOUT (trim trailing space)
    // --------------------------------------------------
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (childCount == 0) return

        val sample = getChildAt(0)
        val boxWidth = sample.measuredWidth + boxGap
        val boxHeight = sample.measuredHeight + boxGap

        val lastVisibleIndex = cells.indexOfLast { it != null }
        if (lastVisibleIndex == -1) return

        var xOffset = 0
        var weekIndex = 0
        var dayIndexInWeek = 0

        for (i in 0..lastVisibleIndex) {
            // Calculate position
            val left = weekIndex * boxWidth + xOffset
            val top = dayIndexInWeek * boxHeight

            // Layout only visible views
            val view = getChildAt(i)
            if (cells[i] != null) {
                view.layout(
                    left,
                    top,
                    left + view.measuredWidth,
                    top + view.measuredHeight
                )
            }

            // Update indices
            dayIndexInWeek++
            if (dayIndexInWeek == 7) {
                dayIndexInWeek = 0
                weekIndex++
            }

            // Add month gap if needed (but only if there are more visible cells)
            if (i < lastVisibleIndex) {
                val curr = cells[i]
                val next = cells[i + 1]
                if (curr != null && next != null && curr.monthValue != next.monthValue) {
                    xOffset += monthGap
                }
            }
        }

        // Hide all views after lastVisibleIndex
        for (i in lastVisibleIndex + 1 until childCount) {
            getChildAt(i).visibility = View.GONE
        }
    }
    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    fun bindHeatmap(
        taskAddedDate: LocalDate,
        completedDates: Set<LocalDate>,
        activeColor: Int
    ) {

        val lightTaskColor = lightenColor(activeColor)
        for (i in cells.indices) {

            val date = cells[i] ?: continue
            val box = getChildAt(i)

            when {
                date.isBefore(taskAddedDate) -> {
                    box.setBackgroundColor(Color.parseColor("#E0E0E0")) // light gray
                }

                completedDates.contains(date) -> {
                    box.setBackgroundColor(activeColor) // task color
                }

                else -> {
                    box.setBackgroundColor(lightTaskColor)  // light green
                }
            }
        }
    }

    private fun lightenColor(color: Int, factor: Float = 0.6f): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)

        val newR = (r + (255 - r) * factor).toInt()
        val newG = (g + (255 - g) * factor).toInt()
        val newB = (b + (255 - b) * factor).toInt()

        return Color.rgb(newR, newG, newB)
    }

}
