package com.anitech.scoremyday.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.anitech.scoremyday.BarView
import com.anitech.scoremyday.CommonMethods.Companion.filterTasks
import com.anitech.scoremyday.R
import com.anitech.scoremyday.data_class.DailyScore
import com.anitech.scoremyday.data_class.DailyTask
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

class BarAdapter(
    private var tasks: List<DailyTask>,
    private val listener: (DailyScore) -> Unit
) : RecyclerView.Adapter<BarAdapter.BarViewHolder>() {
    private var selectedPosition: Int = RecyclerView.NO_POSITION
    private val today: LocalDate = LocalDate.now()
    private val startDate: LocalDate = today.minusDays(500)
    private val endDate: LocalDate = today.plusDays(500)
    private val totalDays: Int = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1

    inner class BarViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val barView: BarView = view.findViewById(R.id.barView)
        val textDate: TextView = view.findViewById(R.id.textDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.rv_score_bar, parent, false)
        return BarViewHolder(view)
    }

    override fun onBindViewHolder(holder: BarViewHolder, position: Int) {
        val currentDate = startDate.plusDays(position.toLong())
        val dateString = currentDate.toString()

        val dailyScore = calculateScoreForDate(dateString, currentDate)

        // Set score
        holder.barView.setScore(dailyScore.score)

        // Update date text
        holder.textDate.text = if (position == selectedPosition) dailyScore.monthDayText else dailyScore.dayText

        // Highlight today
        val colorRes = if (currentDate == today) R.color.category_dark_blue else R.color.black
        holder.textDate.setTextColor(ContextCompat.getColor(holder.view.context, colorRes))

        // Highlight selection
        if (position == selectedPosition) {
            holder.view.setBackgroundResource(R.drawable.circular_corners_stroke)
            holder.textDate.setTypeface(null, Typeface.BOLD)
        } else {
            holder.view.setBackgroundResource(0)
            holder.textDate.setTypeface(null, Typeface.NORMAL)
        }

        // Handle click
        holder.view.setOnClickListener {
            val previousPos = selectedPosition
            selectedPosition = holder.adapterPosition
            if (previousPos != RecyclerView.NO_POSITION) notifyItemChanged(previousPos)
            notifyItemChanged(selectedPosition)
            listener(dailyScore)
        }
    }

    override fun getItemCount(): Int = totalDays

    private fun calculateScoreForDate(dateString: String, currentDate: LocalDate): DailyScore {
        val tasksForDate = filterTasks(tasks, dateString)

        val (totalWeight, completedWeight) = tasksForDate.fold(0f to 0f) { acc, task ->
            val weight = task.weight.weight.toFloat() // ensure it's Float
            val total = acc.first + weight
            val completed = acc.second + if (task.completedDates.contains(dateString)) weight else 0f
            total to completed
        }


        val score = if (totalWeight > 0f) ((completedWeight / totalWeight) * 10f * 10).roundToInt() / 10f else 0f

        return DailyScore(
            date = dateString,
            dayText = currentDate.dayOfMonth.toString(),
            monthDayText = "${currentDate.monthValue}/${currentDate.dayOfMonth}",
            score = score,
            taskCount = tasksForDate.size
        )
    }

    fun updateData(newTasks: List<DailyTask>) {
        tasks = newTasks
        notifyDataSetChanged()

        // Default selection = today
        if (selectedPosition == RecyclerView.NO_POSITION) {
            selectedPosition = ChronoUnit.DAYS.between(startDate, today).toInt().coerceIn(0, totalDays - 1)
        }
    }
}
