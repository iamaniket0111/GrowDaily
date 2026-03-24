package com.anitech.growdaily.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.RepeatTaskUi
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.WeekHabit
import com.anitech.growdaily.databinding.RvRepeatTaskItemBinding
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon
import com.anitech.growdaily.enum_class.TaskType
import java.time.LocalDate

class RepeatTaskAdapter(
    private val listener: OnItemClickListener
) : ListAdapter<RepeatTaskUi, RepeatTaskAdapter.TaskViewHolder>(RepeatTaskDiffCallback()) {

    interface OnItemClickListener {
        fun moveToEditListener(task: TaskEntity)
        fun onTaskCompleteClick(taskId: String, date: String)
    }

    // ---- DiffUtil ----
    class RepeatTaskDiffCallback : DiffUtil.ItemCallback<RepeatTaskUi>() {

        override fun areItemsTheSame(oldItem: RepeatTaskUi, newItem: RepeatTaskUi): Boolean {
            // Same task ID = same "row"
            return oldItem.task.id == newItem.task.id
        }

        override fun areContentsTheSame(oldItem: RepeatTaskUi, newItem: RepeatTaskUi): Boolean {
            // Only redraw if something the UI actually shows has changed.
            // Comparing the full data class (which includes completedDates Set)
            // is correct here since RepeatTaskUi is a data class.
            return oldItem == newItem
        }

        // Optional: return a payload so onBindViewHolder can do a partial update
        // instead of a full rebind. Add later if needed for extra smoothness.
    }

    // ---- ViewHolder ----
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = RvRepeatTaskItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TaskViewHolder(
        private val binding: RvRepeatTaskItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        // Cache the HistoryAdapter so we never re-create it on re-bind;
        // we just call updateData() instead.
        private var historyAdapter: HistoryAdapter? = null

        // Track the last taskId bound so we know when the row is being
        // recycled for a completely different task (requires fresh adapter state).
        private var lastBoundTaskId: String? = null

        fun bind(item: RepeatTaskUi) = with(binding) {

            val task = item.task

            // ---------- BASIC DATA ----------
            taskTitle.text = task.title

            taskType.text = binding.root.context.getString(task.taskType.labelRes)

            imageProfile.setImageResource(TaskIcon.fromName(task.iconResId).resId)

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
            txtStreakCount.text = "${item.currentStreak}"

            val scoreFormatted = String.format("%.1f", item.completionOutOf10)
            txtScoreOutOf10.text = scoreFormatted
            txtScoreOutOf10.setTextColor(colorInt)

            val progressPercent = ((item.completionOutOf10 / 10f) * 100).toInt()
            progressBar.setIndicatorColor(colorInt)
            progressBar.progress = progressPercent

            // ---------- HEATMAP ----------
            val addedDate = LocalDate.parse(task.taskAddedDate)

            Log.e("completedDatesSize", "adapter size:" + item.completedDates.size.toString())

            heatmapView.bindHeatmap(
                taskAddedDate = addedDate,
                completedDates = item.completedDates.keys,
                activeColor = colorInt
            )

            // Scroll heatmap to end only once per task (not every re-bind)
            if (lastBoundTaskId != task.id) {
                heatmapScroll.post {
                    heatmapScroll.getChildAt(0)?.let { child ->
                        heatmapScroll.scrollTo(child.measuredWidth, 0)
                    }
                }
            }

            // ---------- WEEK LIST (built once per task, reused on re-bind) ----------
            val weekList: List<WeekHabit>
            val today = LocalDate.now()

            if (lastBoundTaskId != task.id) {
                // Row is being used for a new/different task — rebuild the week list.
                val list = mutableListOf<WeekHabit>()
                var cursor = addedDate
                while (!cursor.isAfter(today)) {
                    list.add(
                        WeekHabit(
                            date = cursor,
                            dayLetter = cursor.dayOfWeek.name.first().toString()
                        )
                    )
                    cursor = cursor.plusDays(1)
                }
                weekList = list
            } else {
                // Same task re-bound (e.g. completion toggled) — no need to rebuild dates.
                weekList = emptyList() // historyAdapter.updateData() handles it below
            }

            // ---------- WEEK RECYCLER ----------
            if (historyAdapter == null || lastBoundTaskId != task.id) {
                // First bind or recycled to a different task — create fresh adapter.
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
                        context, LinearLayoutManager.HORIZONTAL, false
                    )
                    adapter = historyAdapter
                }
            } else {
                // Same task, data changed (e.g. a date was toggled) — update in place.
                historyAdapter?.updateData(
                    completedDates = item.completedDates,
                    taskColor = colorInt
                )
            }

            // Always scroll to the last item so today is visible.
            historyAdapter?.let { ha ->
                if (ha.itemCount > 0) weekRecycler.scrollToPosition(ha.itemCount - 1)
            }

            lastBoundTaskId = task.id

            // ---------- CLICK ----------
            root.setOnClickListener { listener.moveToEditListener(task) }
        }
    }
}