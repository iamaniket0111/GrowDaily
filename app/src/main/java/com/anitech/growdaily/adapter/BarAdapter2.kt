package com.anitech.growdaily.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.view.BarView
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.enum_class.PeriodType
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class BarAdapter2(
    private val task: TaskEntity?
) : RecyclerView.Adapter<BarAdapter2.BarViewHolder>() {

    private var periodType: PeriodType = PeriodType.WEEK
    private var anchorDate: LocalDate = LocalDate.now()
    private var barColor: Int = android.graphics.Color.parseColor("#708CFF")

    private var barDates: List<LocalDate> = emptyList()
    private var barScores: List<Float> = emptyList()

    inner class BarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val barView: BarView = view.findViewById(R.id.barView)
        val textDate: TextView = view.findViewById(R.id.textDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.rv_bar_small, parent, false)
        return BarViewHolder(view)
    }

    override fun getItemCount(): Int = barDates.size

    override fun onBindViewHolder(holder: BarViewHolder, position: Int) {

        val date = barDates[position]
        val score = barScores.getOrNull(position) ?: 0f

        holder.barView.setUseProgressPalette(false)
        holder.barView.setUseSolidBarColor(true)
        holder.barView.setBarColor(barColor)
        holder.barView.setScore(score)

        holder.textDate.text = when (periodType) {
            PeriodType.WEEK ->
                date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

            PeriodType.MONTH ->
                date.dayOfMonth.toString()

            PeriodType.YEAR ->
                date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
    }

    // ------------------------------------------------
    // Public API (used by Fragment)
    // ------------------------------------------------

    fun setPeriod(type: PeriodType) {
        periodType = type
        notifyDataSetChanged()
    }

    fun setAnchorDate(date: LocalDate) {
        anchorDate = date
        // anchor sirf title ke liye hai, data VM se aata hai
    }

    fun setBarColor(color: Int) {
        barColor = color
        notifyDataSetChanged()
    }

    fun submitData(
        dates: List<LocalDate>,
        scores: List<Float>
    ) {
        barDates = dates
        barScores = scores
        notifyDataSetChanged()
    }
}

