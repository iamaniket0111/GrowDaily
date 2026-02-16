package com.anitech.growdaily.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.BarView
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.DailyScore
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.enum_class.TaskType
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

class BarAdapter(
    private val listener: OnBarInteractionListener
) : RecyclerView.Adapter<BarAdapter.BarViewHolder>() {
    private var selectedPosition: Int = RecyclerView.NO_POSITION
    private val today: LocalDate = LocalDate.now()
    private val startDate: LocalDate = today.minusDays(500)
    private val endDate: LocalDate = today.plusDays(500)
    private val totalDays: Int = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
    var isSelectingMode = false
    private var completedTaskMap: Map<String, Map<String, Int>> = emptyMap()
    private var tasksByDate: Map<String, List<TaskEntity>> = emptyMap()




    interface OnBarInteractionListener {
        fun onBarSelected(dailyScore: DailyScore)
        fun onTodayBarOutOfView()
    }

    inner class BarViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val barView: BarView = view.findViewById(R.id.barView)
        val textDate: TextView = view.findViewById(R.id.textDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.rv_bar, parent, false)
        return BarViewHolder(view)
    }

    override fun onBindViewHolder(holder: BarViewHolder, position: Int) {
        val currentDate = startDate.plusDays(position.toLong())
        val dateString = currentDate.toString()

        val dailyScore = calculateScoreForDate(dateString, currentDate)

        // Set score
        holder.barView.setScore(dailyScore.score)

        // Update date text
        holder.textDate.text =
            if (position == selectedPosition) dailyScore.monthDayText else dailyScore.dayText

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
            if (!isSelectingMode) {
                val previousPos = selectedPosition
                selectedPosition = holder.adapterPosition
                if (previousPos != RecyclerView.NO_POSITION) notifyItemChanged(previousPos)
                notifyItemChanged(selectedPosition)
                listener.onBarSelected(dailyScore)
            }
        }
    }

    fun updateData(
        newTasksByDate: Map<String, List<TaskEntity>>,
        completionMap: Map<String, Map<String, Int>>
    )
    {
        tasksByDate = newTasksByDate
        completedTaskMap = completionMap
        notifyDataSetChanged()

        if (selectedPosition == RecyclerView.NO_POSITION) {
            selectedPosition =
                ChronoUnit.DAYS.between(startDate, today).toInt()
                    .coerceIn(0, totalDays - 1)
        }
    }

    override fun getItemCount(): Int = totalDays

    private fun calculateScoreForDate(
        dateString: String,
        currentDate: LocalDate
    ): DailyScore {

        val tasksForDate = tasksByDate[dateString]
            ?.filter { it.taskType != TaskType.UNTIL_COMPLETE }
            ?: emptyList()

        val completionForDate =
            completedTaskMap[dateString] ?: emptyMap()

        val (totalWeight, completedWeight) =
            tasksForDate.fold(0f to 0f) { acc, task ->

                val weight = task.weight.weight.toFloat()
                val total = acc.first + weight

                val count = completionForDate[task.id] ?: 0
                val target = task.dailyTargetCount.coerceAtLeast(1)

                val completed = acc.second +
                        if (count >= target) weight else 0f

                total to completed
            }

        val score = if (totalWeight > 0f) {
            ((completedWeight / totalWeight) * 10f * 10)
                .roundToInt() / 10f
        } else 0f

        return DailyScore(
            date = dateString,
            dayText = currentDate.dayOfMonth.toString(),
            monthDayText = "${currentDate.monthValue}/${currentDate.dayOfMonth}",
            score = score,
            taskCount = tasksForDate.size
        )
    }



    fun checkIfTodayVisible(layoutManager: RecyclerView.LayoutManager) {
        if (layoutManager is androidx.recyclerview.widget.LinearLayoutManager) {
            val firstVisible = layoutManager.findFirstVisibleItemPosition()
            val lastVisible = layoutManager.findLastVisibleItemPosition()

            val todayIndex = ChronoUnit.DAYS.between(startDate, today).toInt()
            if (todayIndex < firstVisible || todayIndex > lastVisible) {
                listener.onTodayBarOutOfView()
            }
        }
    }



}


