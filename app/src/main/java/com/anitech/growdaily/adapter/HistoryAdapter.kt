package com.anitech.growdaily.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.SquareBorderProgressView
import com.anitech.growdaily.data_class.WeekHabit
import java.time.LocalDate

class HistoryAdapter(
    private val taskId: String,
    private val taskAddedDate: LocalDate,
    private val completedDates: Set<LocalDate>,
    private val taskColor: Int,
    private val weekList: List<WeekHabit>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<HistoryAdapter.WeekViewHolder>()  {
    private var completedTaskIds: Set<String> = emptySet()
    private lateinit var dailyTaskId:String

    interface OnItemClickListener {
        fun onTaskCompleteClick(taskId: String, date: String)
    }
     class WeekViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val borderProgress =  itemView.findViewById<SquareBorderProgressView>(R.id.borderProgress)
        val tvDate = itemView.findViewById<TextView>(R.id.tvDate)
        val tvDay = itemView.findViewById<TextView>(R.id.tvDay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeekViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.rv_week_habit_item, parent, false)
        return WeekViewHolder(view)
    }

    override fun onBindViewHolder(holder: WeekViewHolder, position: Int) {
        val item = weekList[position]
        val date = item.date

        // Hide dates before taskAddedDate
        if (date.isBefore(taskAddedDate)) {
            holder.itemView.visibility = View.INVISIBLE
            return
        } else {
            holder.itemView.visibility = View.VISIBLE
        }

        // tvDay
        holder.tvDay.text = item.dayLetter

        // tvDate -> d/M
        holder.tvDate.text = "${date.dayOfMonth}/${date.monthValue}"

        // tvDate color = task color
        holder.tvDate.setTextColor(taskColor)

        // progress logic
        val isCompleted = completedDates.contains(date)
        holder.borderProgress.apply {
           // setColor(taskColor)
            setProgress(if (isCompleted) 100 else 0)
        }
        holder.borderProgress.setProgressColor(taskColor)
//        var progress =0
//        holder.itemView.setOnClickListener {
//            if (progress < 100) {
//                progress += 10
//                holder.borderProgress.setProgress(progress)
//            }
//        }

        holder.itemView.setOnClickListener {
            listener.onTaskCompleteClick(
                taskId,
                date.toString() // yyyy-MM-dd
            )
        }
    }

    override fun getItemCount(): Int = weekList.size
}
/*what to do
        * we have to show data from task added date
        * set color of task to tvDate text
        * if completed date containes date then set progress to 100 else 0
        * text of tvDate set in the formate 2/3 2 is day and 3 is month
        * text of tvDay is the first letter of week day in capital ex. "M" for monday
        * i will handle click listner logic in fragment only provide task id and date in yyyy-MM-dd formate*/
