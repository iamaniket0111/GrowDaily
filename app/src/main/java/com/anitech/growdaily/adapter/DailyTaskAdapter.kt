package com.anitech.growdaily.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.RepeatTaskUi
import com.anitech.growdaily.data_class.WeekHabit
import com.anitech.growdaily.databinding.RvRepeatTaskItemBinding
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskType
import java.time.LocalDate

class DailyTaskAdapter(
    private var taskList: List<RepeatTaskUi>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<DailyTaskAdapter.TaskViewHolder>() {

    private lateinit var historyAdapter: HistoryAdapter

    interface OnItemClickListener {
        fun moveToEditListener(task: TaskEntity)
        fun onTaskCompleteClick(taskId: String, date: String)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = RvRepeatTaskItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(taskList[position])
    }

    override fun getItemCount(): Int = taskList.size

    fun updateList(newList: List<RepeatTaskUi>) {
        taskList = newList
        notifyDataSetChanged()
    }

    inner class TaskViewHolder(
        private val binding: RvRepeatTaskItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RepeatTaskUi) = with(binding) {

            val task = item.task

            // ---------- BASIC DATA ----------
            taskTitle.text = task.title

            taskType.text = when (task.taskType) {
                TaskType.DAY -> "Today"
                TaskType.DAILY -> "Daily"
                TaskType.UNTIL_COMPLETE -> "Until Done"
            }

            taskWeight.text = root.context.getString(
                R.string.task_weight_prefix,
                task.weight.weight
            )

            taskWeight.visibility = if (task.taskType == TaskType.UNTIL_COMPLETE)
                android.view.View.GONE else android.view.View.VISIBLE

            // ---------- COLOR SETUP ----------
            val colorInt = TaskColor.fromName(task.colorCode)
                ?.toColorInt(root.context)
                ?: ContextCompat.getColor(root.context, R.color.brand_blue)

            imageProfile.backgroundTintList = ColorStateList.valueOf(colorInt)
            taskType.setTextColor(colorInt)
            body.backgroundTintList = ColorStateList.valueOf(Color.WHITE)

            // ---------- STATS ----------
            // Streak
            txtStreakCount.text = "${item.currentStreak}"

            // Score out of 10 e.g. "7.3/10"
            val scoreFormatted = String.format("%.1f", item.completionOutOf10)
            txtScoreOutOf10.text = "$scoreFormatted"
            txtScoreOutOf10.setTextColor(colorInt)

            // Progress bar (0–100)
            val progressPercent = ((item.completionOutOf10 / 10f) * 100).toInt()
            progressBar.setIndicatorColor(colorInt)
            progressBar.progress = progressPercent

            // ---------- HEATMAP ----------
            val addedDate = LocalDate.parse(task.taskAddedDate)

            Log.e("completedDatesSize", "adapter size:" + item.completedDates.size.toString())

            heatmapScroll.post {
                heatmapScroll.scrollTo(
                    heatmapScroll.getChildAt(0).measuredWidth,
                    0
                )
            }

            heatmapView.bindHeatmap(
                taskAddedDate = addedDate,
                completedDates = item.completedDates,
                activeColor = colorInt
            )

            // ---------- WEEK RECYCLER ----------
            val today = LocalDate.now()
            val weekList = mutableListOf<WeekHabit>()
            var cursor = addedDate
            while (!cursor.isAfter(today)) {
                weekList.add(
                    WeekHabit(
                        date = cursor,
                        dayLetter = cursor.dayOfWeek.name.first().toString()
                    )
                )
                cursor = cursor.plusDays(1)
            }

            historyAdapter = HistoryAdapter(
                taskId = task.id,
                taskAddedDate = addedDate,
                completedDates = item.completedDates,
                taskColor = colorInt,
                weekList = weekList,
                listener = object : HistoryAdapter.OnItemClickListener {
                    override fun onTaskCompleteClick(taskId: String, date: String) {
                        listener.onTaskCompleteClick(taskId, date)
                    }
                }
            )

            weekRecycler.apply {
                layoutManager = LinearLayoutManager(
                    context,
                    LinearLayoutManager.HORIZONTAL,
                    false
                )
                adapter = historyAdapter
                scrollToPosition(historyAdapter.itemCount - 1)
            }

            // ---------- CLICK ----------
            root.setOnClickListener {
                listener.moveToEditListener(task)
            }
        }
    }
}