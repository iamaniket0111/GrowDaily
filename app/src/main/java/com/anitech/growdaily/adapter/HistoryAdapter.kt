package com.anitech.growdaily.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.SquareBorderProgressView
import com.anitech.growdaily.data_class.WeekHabit
import java.time.LocalDate

class HistoryAdapter(
    private val taskId: String,
    private val taskAddedDate: LocalDate,
    private var completedDates: Map<LocalDate, Int>,
    private var taskColor: Int,
    private val weekList: List<WeekHabit>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<HistoryAdapter.WeekViewHolder>() {

    interface OnItemClickListener {
        fun onTaskCompleteClick(taskId: String, date: String)
    }

    class WeekViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val borderProgress: SquareBorderProgressView =
            itemView.findViewById(R.id.borderProgress)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvDay: TextView = itemView.findViewById(R.id.tvDay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeekViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.rv_week_habit_item, parent, false)
        return WeekViewHolder(view)
    }

    override fun onBindViewHolder(holder: WeekViewHolder, position: Int) {
        val item = weekList[position]
        val date = item.date

        if (date.isBefore(taskAddedDate)) {
            holder.itemView.visibility = View.INVISIBLE
            return
        } else {
            holder.itemView.visibility = View.VISIBLE
        }

        holder.tvDay.text = item.dayLetter
        holder.tvDate.text = "${date.dayOfMonth}/${date.monthValue}"
        holder.tvDate.setTextColor(taskColor)

        val isCompleted = completedDates.containsKey(date)
        holder.borderProgress.apply {
            setProgress(if (isCompleted) 100 else 0)
            setProgressColor(taskColor)
        }

        holder.itemView.setOnClickListener {
            listener.onTaskCompleteClick(taskId, date.toString())
        }
    }

    override fun getItemCount(): Int = weekList.size

    /**
     * Called when only completedDates or color changed for the SAME task.
     * Uses DiffUtil so only the cells that flipped are redrawn — no full
     * notifyDataSetChanged() needed.
     */
    fun updateData(completedDates: Map<LocalDate, Int>, taskColor: Int) {
        val oldCompleted = this.completedDates
        val oldColor = this.taskColor

        this.completedDates = completedDates
        this.taskColor = taskColor

        // If color changed every visible cell needs a redraw.
        if (oldColor != taskColor) {
            notifyItemRangeChanged(0, itemCount)
            return
        }

        // Color didn't change — only redraw cells whose completion state flipped.
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = weekList.size
            override fun getNewListSize() = weekList.size

            override fun areItemsTheSame(oldPos: Int, newPos: Int) = oldPos == newPos

            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                val date = weekList[oldPos].date
                return oldCompleted.contains(date) == completedDates.containsKey(date)
            }
        })
        diff.dispatchUpdatesTo(this)
    }
}