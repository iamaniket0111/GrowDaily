package com.anitech.growdaily

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * HeatmapLayout — fully Canvas-based.
 *
 * Zero child Views are inflated. Every cell is drawn directly onto the Canvas,
 * which means no layout inflation, no measure/layout passes for hundreds of
 * children, and no main-thread jank when binding.
 *
 * Public API is identical to the old version:
 *   setDateRange(start, end)   — optional, defaults to 1 year back → today
 *   bindHeatmap(taskAddedDate, completedDates, activeColor)
 */
class HeatmapLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── sizing ───────────────────────────────────────────────────────────────
    private val boxSize = dp(9f)
    private val boxGap  = dp(1f)
    private val monthGap = dp(6f)
    private val cornerRadius = dp(2f)

    private val cellStep get() = boxSize + boxGap   // distance from cell origin to next
    private val rows = 7


    /*
    *     private val rows = 7
    private val boxGap = dp(1)
    private val monthGap = dp(6)*/

    // ── state ────────────────────────────────────────────────────────────────
    private var startDate: LocalDate = LocalDate.now().minusYears(1)
    private var endDate: LocalDate   = LocalDate.now()

    /** Flat list of (date | null) mirroring week-major order, null = padding cell */
    private val cells = mutableListOf<LocalDate?>()

    // colours per cell index — set by bindHeatmap()
    private val cellColors = mutableMapOf<Int, Int>()

    private var activeColor  = Color.parseColor("#4CAF50")
    private var lightColor   = lightenColor(activeColor)
    private val grayColor    = Color.parseColor("#E0E0E0")

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect  = RectF()

    // ── computed layout ──────────────────────────────────────────────────────
    /** x-offset at the start of each column index (week column) */
    private val columnX = mutableListOf<Float>()
    private var measuredW = 0
    private var measuredH = 0

    init {
        setDateRange(LocalDate.now().minusYears(1), LocalDate.now())
    }

    // ── public API ───────────────────────────────────────────────────────────

    fun setDateRange(start: LocalDate, end: LocalDate) {
        startDate = start
        endDate   = end
        rebuildCells()
    }

    fun bindHeatmap(
        taskAddedDate: LocalDate,
        completedDates: Set<LocalDate>,
        activeColor: Int
    ) {
        this.activeColor = activeColor
        this.lightColor  = lightenColor(activeColor)

        cellColors.clear()
        for (i in cells.indices) {
            val date = cells[i] ?: continue
            cellColors[i] = when {
                date.isBefore(taskAddedDate)    -> grayColor
                completedDates.contains(date)   -> activeColor
                else                            -> lightColor
            }
        }
        invalidate()   // single draw call — no layout needed
    }

    // ── internals ────────────────────────────────────────────────────────────

    private fun rebuildCells() {
        cells.clear()
        columnX.clear()

        var monthCursor = startDate.withDayOfMonth(1)
        while (!monthCursor.isAfter(endDate)) {
            val monthStart  = monthCursor
            val monthEnd    = monthCursor.withDayOfMonth(monthCursor.lengthOfMonth())
            val effectiveStart = maxOf(monthStart, startDate)
            val effectiveEnd   = minOf(monthEnd,   endDate)

            val weekStart = monthStart.with(DayOfWeek.MONDAY)
            val weekEnd   = monthEnd.with(DayOfWeek.SUNDAY)

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

    /** Pre-compute the x position of every column so onDraw is pure arithmetic. */
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

                // month-gap: if the next visible cell is in a new month, add gap
                if (i < lastVisible) {
                    val curr = cells[i]
                    val next = cells.subList(i + 1, minOf(i + 8, cells.size))
                        .firstOrNull { it != null }
                    if (curr != null && next != null && curr.monthValue != next.monthValue) {
                        x += cellStep + monthGap
                    } else {
                        x += cellStep
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

        var colIdx    = 0
        var dayInWeek = 0

        for (i in 0..lastVisible) {
            val date = cells[i]

            if (date != null) {
                val x = columnX[colIdx]
                val y = dayInWeek * cellStep

                paint.color = cellColors[i] ?: lightColor
                rect.set(x, y, x + boxSize, y + boxSize)
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
            }

            dayInWeek++
            if (dayInWeek == rows) {
                dayInWeek = 0
                colIdx++
            }
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density

    private fun lightenColor(color: Int, factor: Float = 0.6f): Int {
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