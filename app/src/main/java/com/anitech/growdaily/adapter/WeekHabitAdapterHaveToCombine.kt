package com.anitech.growdaily.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.SquareBorderProgressView
import com.anitech.growdaily.data_class.TaskEntity
import java.time.LocalDate

class WeekHabitAdapterHaveToCombine(
    private val task: TaskEntity,
    private var completedDates: Set<LocalDate>,
    private val taskColor: Int
) : RecyclerView.Adapter<WeekHabitAdapterHaveToCombine.WeekViewHolder>() {

    private val startDate = LocalDate.parse(task.taskAddedDate)
    private val today = LocalDate.now()

    private val totalDays =
        java.time.temporal.ChronoUnit.DAYS.between(startDate, today).toInt() + 1

    class WeekViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val borderProgress =
            itemView.findViewById<SquareBorderProgressView>(R.id.borderProgress)
        val tvDate = itemView.findViewById<TextView>(R.id.tvDate)
        val tvDay = itemView.findViewById<TextView>(R.id.tvDay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeekViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.rv_week_analysis_expanded, parent, false)
        return WeekViewHolder(view)
    }

    override fun getItemCount(): Int = totalDays

    override fun onBindViewHolder(holder: WeekViewHolder, position: Int) {

        val date = startDate.plusDays(position.toLong())

        // Day letter (Mon, Tue, etc.)
        holder.tvDay.text =
            date.dayOfWeek.name.take(3)

        // Date format: d/M
        holder.tvDate.text = "${date.dayOfMonth}/${date.monthValue}"

        holder.tvDate.setTextColor(taskColor)

        val isCompleted = completedDates.contains(date)

        holder.borderProgress.apply {
            setProgressColor(taskColor)
            setProgress(if (isCompleted) 100 else 0)
        }
    }

    fun updateCompletedDates(newDates: Set<LocalDate>) {
        completedDates = newDates
        notifyDataSetChanged()
    }
}

/*what to do
        * we have to show data from task added date
        * set color of task to tvDate text
        * if completed date containes date then set progress to 100 else 0
        * text of tvDate set in the formate 2/3 2 is day and 3 is month
        * text of tvDay is the first letter of week day in capital ex. "M" for monday
        * i will handle click listner logic in fragment only provide task id and date in yyyy-MM-dd formate*/
