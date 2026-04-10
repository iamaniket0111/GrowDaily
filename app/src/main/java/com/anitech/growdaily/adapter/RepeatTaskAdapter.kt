package com.anitech.growdaily.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.setSolidBackgroundColorCompat
import com.anitech.growdaily.data_class.RepeatTaskUi
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.WeekHabit
import com.anitech.growdaily.databinding.RvRepeatTaskItemBinding
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon
import com.anitech.growdaily.enum_class.TaskType
import java.time.LocalDate
import java.time.format.DateTimeParseException

class RepeatTaskAdapter(
    private val listener: OnItemClickListener
) : ListAdapter<RepeatTaskUi, RepeatTaskAdapter.TaskViewHolder>(RepeatTaskDiffCallback()) {

    interface OnItemClickListener {
        fun moveToEditListener(task: TaskEntity)
        fun onTaskCompleteClick(taskId: String, date: String)
    }

    class RepeatTaskDiffCallback : DiffUtil.ItemCallback<RepeatTaskUi>() {
        override fun areItemsTheSame(oldItem: RepeatTaskUi, newItem: RepeatTaskUi): Boolean {
            return oldItem.task.seriesId.ifBlank { oldItem.task.id } ==
                newItem.task.seriesId.ifBlank { newItem.task.id }
        }

        override fun areContentsTheSame(oldItem: RepeatTaskUi, newItem: RepeatTaskUi): Boolean {
            return oldItem == newItem
        }
    }

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

        private var historyAdapter: HistoryAdapter? = null
        private var lastBoundSignature: String? = null

        fun bind(item: RepeatTaskUi) = with(binding) {
            val task = item.task
            val bindSignature = buildHistorySignature(item)

            taskTitle.text = task.title
            taskType.text = binding.root.context.getString(task.taskType.labelRes)
            imageProfile.setImageResource(TaskIcon.fromName(task.iconResId).resId)
            taskWeight.text = root.context.getString(
                R.string.task_weight_prefix,
                task.weight.weight
            )
            taskWeight.visibility = if (task.taskType == TaskType.UNTIL_COMPLETE) {
                android.view.View.GONE
            } else {
                android.view.View.VISIBLE
            }

            val colorInt = TaskColor.fromName(task.colorCode)
                ?.toColorInt(root.context)
                ?: ContextCompat.getColor(root.context, R.color.brand_blue)
            val cardSurface = ContextCompat.getColor(root.context, R.color.task_card_surface)

            imageProfile.setSolidBackgroundColorCompat(colorInt)
            taskType.setTextColor(colorInt)
            body.backgroundTintList = ColorStateList.valueOf(cardSurface)

            txtStreakCount.text = "${item.currentStreak}"
            txtScoreOutOf10.text = formatScoreOutOf10(item.completionOutOf10)
            txtScoreOutOf10.setTextColor(colorInt)

            val progressPercent = ((item.completionOutOf10 / 10f) * 100).toInt()
            progressBar.setIndicatorColor(colorInt)
            progressBar.trackColor = adjustAlpha(colorInt, 0.18f)
            progressBar.progress = progressPercent

            val today = LocalDate.now()
            heatmapView.bindHeatmap(
                taskAddedDate = item.seriesStartDate,
                progressByDate = item.progressByDate,
                unavailableDates = buildUnavailableDates(item, today),
                activeColor = colorInt
            )

            if (lastBoundSignature != bindSignature) {
                heatmapScroll.post {
                    heatmapScroll.getChildAt(0)?.let { child ->
                        heatmapScroll.scrollTo(child.measuredWidth, 0)
                    }
                }
            }

            val weekList = if (lastBoundSignature != bindSignature) {
                buildHistoryItems(item)
            } else {
                emptyList()
            }

            if (historyAdapter == null || lastBoundSignature != bindSignature) {
                historyAdapter = HistoryAdapter(
                    taskAddedDate = item.seriesStartDate,
                    progressByDate = item.progressByDate,
                    taskColor = colorInt,
                    weekList = weekList,
                    listener = object : HistoryAdapter.OnItemClickListener {
                        override fun onTaskCompleteClick(date: String) {
                            listener.onTaskCompleteClick(resolveTaskIdForDate(item, date), date)
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
                }
            } else {
                historyAdapter?.updateData(
                    progressByDate = item.progressByDate,
                    taskColor = colorInt
                )
            }

            historyAdapter?.let { adapter ->
                if (adapter.itemCount > 0) {
                    weekRecycler.scrollToPosition(adapter.itemCount - 1)
                }
            }

            lastBoundSignature = bindSignature
            root.setOnClickListener { listener.moveToEditListener(task) }
        }

        private fun buildHistorySignature(item: RepeatTaskUi): String {
            return listOf(
                item.task.seriesId.ifBlank { item.task.id },
                item.seriesStartDate.toString(),
                item.taskIdByDate.size.toString(),
                item.taskIdByDate.keys.minOrNull()?.toString().orEmpty(),
                item.taskIdByDate.keys.maxOrNull()?.toString().orEmpty()
            ).joinToString("|")
        }

        private fun buildUnavailableDates(
            item: RepeatTaskUi,
            endDate: LocalDate
        ): Set<LocalDate> {
            val unavailable = mutableSetOf<LocalDate>()
            val scheduledDates = item.taskIdByDate.keys
            var date = item.seriesStartDate
            while (!date.isAfter(endDate)) {
                if (!scheduledDates.contains(date)) {
                    unavailable.add(date)
                }
                date = date.plusDays(1)
            }
            return unavailable
        }

        private fun buildHistoryItems(item: RepeatTaskUi): List<WeekHabit> {
            return item.taskIdByDate.keys
                .sorted()
                .map { date ->
                    WeekHabit(
                        date = date,
                        dayLetter = date.dayOfWeek.name.first().toString()
                    )
                }
        }

        private fun formatScoreOutOf10(score: Float): String {
            return if (score % 1f == 0f) {
                score.toInt().toString()
            } else {
                String.format("%.1f", score)
            }
        }

        private fun resolveTaskIdForDate(item: RepeatTaskUi, date: String): String {
            return try {
                item.taskIdByDate[LocalDate.parse(date)] ?: item.task.id
            } catch (_: DateTimeParseException) {
                item.task.id
            }
        }

        private fun adjustAlpha(color: Int, factor: Float): Int {
            val alpha = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
            return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        }
    }
}
