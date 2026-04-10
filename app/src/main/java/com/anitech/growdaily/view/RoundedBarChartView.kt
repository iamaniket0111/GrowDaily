package com.anitech.growdaily.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.anitech.growdaily.data_class.DailyScore
import com.anitech.growdaily.R
import java.text.SimpleDateFormat
import java.util.*

class RoundedBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var barData: List<DailyScore> = emptyList()
    private val barRects = mutableListOf<RectF>()
    private var selectedIndex = 0
    private val defaultBarColor = ContextCompat.getColor(context, R.color.brand_blue)
    private val completeBarColor = ContextCompat.getColor(context, R.color.category_green)
    private val selectedBarSurface = ContextCompat.getColor(context, R.color.task_card_surface)
    private val selectedBarShadow = ContextCompat.getColor(context, R.color.task_card_stroke)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.task_text_primary)
        textSize = 28f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.task_text_secondary)
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }

    private val dotLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.task_bar_grid_line)
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val avgLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.task_bar_average_line)
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val cornerRadius = 20f
    private val barWidthPx = 10f.dpToPx()
    private val barSpacingPx = 40f.dpToPx()
    private val maxScore = 10

    fun setData(data: List<DailyScore>) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val previousSelectedDate = barData.getOrNull(selectedIndex)?.dayText
        barData = data

        selectedIndex = when {
            // agar naya data me bhi pehle wala selected date mil gaya
            previousSelectedDate != null && barData.any { it.dayText == previousSelectedDate } ->
                barData.indexOfFirst { it.dayText == previousSelectedDate }

            // agar aaj ki date data me available hai to usko select karo
            barData.any { it.date == today } ->
                barData.indexOfFirst { it.date == today }

            // fallback: last item select karo
            else -> data.lastIndex
        }

        requestLayout()
        invalidate()
    }


    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val totalWidth = (barData.size * (barWidthPx + barSpacingPx)) + barSpacingPx + 60f.dpToPx()
        val width = resolveSize(totalWidth.toInt(), widthMeasureSpec)
        val height = resolveSize(150.dpToPx().toInt(), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        barRects.clear()

        val chartTopPadding = 24f.dpToPx()
        val chartBottomPadding = 36f.dpToPx()
        val usableHeight = height - chartTopPadding - chartBottomPadding
        val bottomLineY = height - chartBottomPadding
        val topLineY = chartTopPadding

        // Draw dotted lines (top, bottom, average)
        canvas.drawLine(0f, topLineY, width.toFloat(), topLineY, dotLinePaint)
        canvas.drawLine(0f, bottomLineY, width.toFloat(), bottomLineY, dotLinePaint)

        val nonZeroData = barData.filter { it.score > 0 }
        val avgScore = if (nonZeroData.isNotEmpty()) {
            (nonZeroData.sumOf { it.score.toDouble() } / nonZeroData.size).toFloat()
        } else {
            0f
        }

        val avgLineY = bottomLineY - (avgScore / maxScore) * usableHeight
        canvas.drawLine(0f, avgLineY, width.toFloat(), avgLineY, avgLinePaint)

        for ((index, item) in barData.withIndex()) {
            val left = index * (barWidthPx + barSpacingPx) + barSpacingPx + 30f.dpToPx()
            val barHeight = (item.score / maxScore.toFloat()) * usableHeight
            val top = bottomLineY - barHeight
            val right = left + barWidthPx
            val bottom = bottomLineY

            val rect = RectF(left, top, right, bottom)
            barRects.add(rect)

            // Draw selection background
            if (index == selectedIndex) {
                val bgRect = RectF(
                    left - 20f.dpToPx(),
                    chartTopPadding / 2,
                    right + 20f.dpToPx(),
                    height.toFloat()
                )
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = selectedBarSurface
                    setShadowLayer(8f, 0f, 0f, selectedBarShadow)
                }
                setLayerType(LAYER_TYPE_SOFTWARE, null)
                canvas.drawRoundRect(bgRect, 40f.dpToPx(), 40f.dpToPx(), bgPaint)
            }

            // Draw bar
            barPaint.color =
                if (item.score.toInt() == 10) completeBarColor else defaultBarColor
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, barPaint)

            // Format and draw date

            canvas.drawText(
                item.dayText,
                rect.centerX(),
                bottom + 20f.dpToPx(),
                if (index == selectedIndex) textPaint else datePaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            for ((index, rect) in barRects.withIndex()) {
                if (event.x in rect.left..rect.right) {
                    selectedIndex = index
                    invalidate()
                    Toast.makeText(
                        context,
                        "Selected: ${barData[index].date}, Score: ${barData[index].score}",
                        Toast.LENGTH_SHORT
                    ).show()
                    barClickListener?.onBarClick(barData[index])
                    // Optionally trigger external callback / scroll to view
                    break
                }
            }
        }
        return true
    }

    interface OnBarClickListener {
        fun onBarClick(score: DailyScore)
    }

    private var barClickListener: OnBarClickListener? = null

    fun setOnBarClickListener(listener: OnBarClickListener) {
        barClickListener = listener
    }

    // Extension Functions
    fun Float.dpToPx(): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this,
            Resources.getSystem().displayMetrics
        )

    fun Int.dpToPx(): Float = this.toFloat().dpToPx()

    fun getSelectedBarCenterX(): Float? {
        return barRects.getOrNull(selectedIndex)?.centerX()
    }

}
