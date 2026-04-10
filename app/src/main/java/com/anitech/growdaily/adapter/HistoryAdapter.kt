package com.anitech.growdaily.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.WeekHabit
import com.anitech.growdaily.view.SquareBorderProgressView
import java.time.LocalDate

class HistoryAdapter(
    private var taskAddedDate: LocalDate,
    private var progressByDate: Map<LocalDate, Int>,
    private var taskColor: Int,
    private var weekList: List<WeekHabit>,
    private val listener: OnItemClickListener? = null
) : RecyclerView.Adapter<HistoryAdapter.WeekViewHolder>() {

    interface OnItemClickListener {
        fun onTaskCompleteClick(date: String)
    }

    class WeekViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val borderProgress: SquareBorderProgressView? =
            itemView.findViewById(R.id.borderProgress)
         val tvDate: TextView? = itemView.findViewById(R.id.tvDate)
        val tvDay: TextView? = itemView.findViewById(R.id.tvDay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeekViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.rv_history_item, parent, false)
        return WeekViewHolder(view)
    }

    override fun onBindViewHolder(holder: WeekViewHolder, position: Int) {
        val item = weekList[position]
        val date = item.date ?: return

        if (date.isBefore(taskAddedDate)) {
            holder.itemView.visibility = View.INVISIBLE
            return
        } else {
            holder.itemView.visibility = View.VISIBLE
        }

        val progress = progressByDate[date] ?: 0
        val isCompleted = progress >= 100
        val hasProgress = progress > 0

        holder.tvDay?.text = item.dayLetter
        holder.tvDate?.text = "${date.dayOfMonth}/${date.monthValue}"

        val context = holder.itemView.context
        val primaryText = ContextCompat.getColor(context, R.color.task_text_primary)
        val secondaryText = ContextCompat.getColor(context, R.color.task_text_secondary)
        val dateTextColor = if (hasProgress) taskColor else adjustAlpha(secondaryText, 0.9f)
        val dayTextColor = if (hasProgress) adjustAlpha(primaryText, 0.92f) else adjustAlpha(secondaryText, 0.72f)

        holder.tvDate?.setTextColor(dateTextColor)
        holder.tvDay?.setTextColor(dayTextColor)

        holder.borderProgress?.visibility = View.VISIBLE

        holder.borderProgress?.apply {
            setProgress(progress)
            setProgressColor(taskColor)
            setTrackColor(adjustAlpha(taskColor, 0.16f))
            alpha = if (isCompleted) 1f else 0.94f
        }

        holder.itemView.setOnClickListener(
            if (listener != null) {
                View.OnClickListener { listener.onTaskCompleteClick(date.toString()) }
            } else {
                null
            }
        )
    }

    override fun getItemCount(): Int = weekList.size

    fun updateData(
        progressByDate: Map<LocalDate, Int>,
        taskColor: Int
    ) {
        val oldProgressByDate = this.progressByDate
        val oldColor = this.taskColor

        this.progressByDate = progressByDate
        this.taskColor = taskColor

        if (oldColor != taskColor) {
            notifyItemRangeChanged(0, itemCount)
            return
        }

        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = weekList.size
            override fun getNewListSize() = weekList.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) = oldPos == newPos

            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                val date = weekList[oldPos].date ?: return true
                val oldProgress = oldProgressByDate[date] ?: 0
                val newProgress = this@HistoryAdapter.progressByDate[date] ?: 0
                return oldProgress == newProgress
            }
        })
        diff.dispatchUpdatesTo(this)
    }

    fun replaceData(
        taskAddedDate: LocalDate,
        progressByDate: Map<LocalDate, Int>,
        taskColor: Int,
        weekList: List<WeekHabit>
    ) {
        val structureChanged =
            this.taskAddedDate != taskAddedDate || this.weekList != weekList

        this.taskAddedDate = taskAddedDate
        this.weekList = weekList

        if (structureChanged) {
            this.progressByDate = progressByDate
            this.taskColor = taskColor
            notifyDataSetChanged()
        } else {
            updateData(progressByDate, taskColor)
        }
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
