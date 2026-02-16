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
import com.anitech.growdaily.data_class.TaskHeatmapUi
import com.anitech.growdaily.data_class.WeekHabit
import com.anitech.growdaily.databinding.RvAnalysisTaskItemBinding
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskType
import java.time.LocalDate

class DailyTaskAdapter(
    private var taskList: List<TaskHeatmapUi>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<DailyTaskAdapter.TaskViewHolder>() {

    private lateinit var weekHabitAdapter: WeekHabitAdapter

    interface OnItemClickListener {
        fun moveToEditListener(task: TaskEntity)
        fun onTaskCompleteClick(taskId: String, date: String)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = RvAnalysisTaskItemBinding.inflate(
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

    fun updateList(newList: List<TaskHeatmapUi>) {
        taskList = newList
        notifyDataSetChanged()
    }

    inner class TaskViewHolder(
        private val binding: RvAnalysisTaskItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(task: TaskHeatmapUi) = with(binding) {

            // ---------- BASIC DATA ----------
            taskTitle.text = task.task.title

            taskType.text = when (task.task.taskType) {
                TaskType.DAY -> "Today"
                TaskType.DAILY -> "Daily"
                TaskType.UNTIL_COMPLETE -> "Until Done"
            }

            taskWeight.text = root.context.getString(
                R.string.task_weight_prefix,
                task.task.weight.weight
            )

            if (task.task.taskType == TaskType.UNTIL_COMPLETE) {
                taskWeight.visibility = android.view.View.GONE
            } else {
                taskWeight.visibility = android.view.View.VISIBLE
            }

            // ---------- COLOR SETUP ----------
            val colorInt = TaskColor.fromName(task.task.colorCode)
                ?.toColorInt(root.context)
                ?: ContextCompat.getColor(root.context, R.color.brand_blue)

            imageProfile.backgroundTintList = ColorStateList.valueOf(colorInt)
            taskType.setTextColor(colorInt)
            body.backgroundTintList =
                ColorStateList.valueOf(Color.WHITE)

            // ---------- HEATMAP ----------
            val addedDate = LocalDate.parse(task.task.taskAddedDate)

            Log.e("completedDatesSize", "adapter size:" + task.completedDates.size.toString())
            heatmapScroll.post {
                heatmapScroll.scrollTo(
                    heatmapScroll.getChildAt(0).measuredWidth,
                    0
                )
            }



            heatmapView.bindHeatmap(
                taskAddedDate = addedDate,
                completedDates = task.completedDates,
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

            weekHabitAdapter = WeekHabitAdapter(
                taskId = task.task.id,
                taskAddedDate = addedDate,
                completedDates = task.completedDates,
                taskColor = colorInt,
                weekList = weekList,
                listener = object : WeekHabitAdapter.OnItemClickListener {
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
                adapter = weekHabitAdapter
                scrollToPosition(weekHabitAdapter.itemCount - 1)
            }

            // ---------- CLICK ----------
            root.setOnClickListener {
                listener.moveToEditListener(task.task)
            }
        }
    }
}
