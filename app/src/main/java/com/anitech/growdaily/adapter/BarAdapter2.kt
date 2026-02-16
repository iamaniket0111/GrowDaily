package com.anitech.growdaily.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.BarView
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.DailyScore
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.TaskCompletionEntity
import com.anitech.growdaily.enum_class.PeriodType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

class BarAdapter2(
    private val task: TaskEntity
) : RecyclerView.Adapter<BarAdapter2.BarViewHolder>() {

    private var periodType: PeriodType = PeriodType.WEEK
    private var anchorDate: LocalDate = LocalDate.now()

    private var selectedPosition: Int = RecyclerView.NO_POSITION

    private var completionDateSet: Set<String> = emptySet()

    private val taskStart = LocalDate.parse(task.taskAddedDate)
    private val taskEnd = task.taskRemovedDate?.let { LocalDate.parse(it) }



    inner class BarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val barView: BarView = view.findViewById(R.id.barView)
        val textDate: TextView = view.findViewById(R.id.textDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.rv_bar_small, parent, false)
        return BarViewHolder(view)
    }

    override fun getItemCount(): Int {
        return when (periodType) {
            PeriodType.WEEK -> 7
            PeriodType.MONTH -> YearMonth.from(anchorDate).lengthOfMonth()
            PeriodType.YEAR -> 12
        }
    }

    override fun onBindViewHolder(holder: BarViewHolder, position: Int) {
        val adapterPos = holder.bindingAdapterPosition
        val scoreData = when (periodType) {
            PeriodType.WEEK -> getWeekScore(adapterPos)
            PeriodType.MONTH -> getMonthDayScore(adapterPos)
            PeriodType.YEAR -> getYearMonthScore(adapterPos)
        }

        holder.barView.setScore(scoreData.score)

        holder.textDate.text = scoreData.dayText


    }

    // --------------------------
    // WEEK MODE
    // --------------------------

    private fun getWeekScore(position: Int): DailyScore {
        val startOfWeek = anchorDate.with(DayOfWeek.MONDAY)
        val currentDate = startOfWeek.plusDays(position.toLong())

        val baseScore = buildDailyScore(currentDate)

        val dayName = currentDate.dayOfWeek
            .getDisplayName(TextStyle.SHORT, Locale.getDefault())

        return baseScore.copy(
            dayText = dayName,                // 👈 Mon, Tue
            monthDayText = "${dayName} ${currentDate.dayOfMonth}"
        )
    }


    // --------------------------
    // MONTH MODE (day by day)
    // --------------------------

    private fun getMonthDayScore(position: Int): DailyScore {
        val firstDay = anchorDate.withDayOfMonth(1)
        val currentDate = firstDay.plusDays(position.toLong())

        return buildDailyScore(currentDate)
    }

    // --------------------------
    // YEAR MODE (month aggregation)
    // --------------------------

    private fun getYearMonthScore(position: Int): DailyScore {

        val year = anchorDate.year
        val month = position + 1
        val yearMonth = YearMonth.of(year, month)

        val daysInMonth = yearMonth.lengthOfMonth()

        var completedDays = 0
        var activeDays = 0


        for (day in 1..daysInMonth) {
            val date = yearMonth.atDay(day)

            if (date.isBefore(taskStart)) continue
            if (taskEnd != null && date.isAfter(taskEnd)) continue

            activeDays++

            val dateString = date.toString()
            if (completionDateSet.contains(dateString)) {
                completedDays++
            }

        }

        val percent = if (activeDays > 0) {
            (completedDays.toFloat() / activeDays) * 10f
        } else 0f

        return DailyScore(
            date = yearMonth.toString(),
            dayText = yearMonth.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            monthDayText = "${yearMonth.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} $year",
            score = percent,
            taskCount = completedDays
        )
    }

    // --------------------------
    // Shared daily logic
    // --------------------------

    private fun buildDailyScore(date: LocalDate): DailyScore {


        val active = !date.isBefore(taskStart) &&
                (taskEnd == null || !date.isAfter(taskEnd))

        val dateString = date.toString()

        val completed = completionDateSet.contains(dateString)


        val score = if (active && completed) 10f else 0f

        return DailyScore(
            date = dateString,
            dayText = date.dayOfMonth.toString(),
            monthDayText = "${date.monthValue}/${date.dayOfMonth}",
            score = score,
            taskCount = if (completed) 1 else 0
        )
    }

    // --------------------------
    // Public setters
    // --------------------------

    fun setPeriod(type: PeriodType) {
        periodType = type
        selectedPosition = RecyclerView.NO_POSITION
        notifyDataSetChanged()
    }

    fun setAnchorDate(date: LocalDate) {
        anchorDate = date
        notifyDataSetChanged()
    }

    fun setCompletions(list: List<TaskCompletionEntity>) {
        completionDateSet = list.map { it.date }.toSet()
        notifyDataSetChanged()
    }

}
