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
import com.anitech.growdaily.adjustAlpha
import com.anitech.growdaily.setSolidBackgroundColorCompat
import com.anitech.growdaily.data_class.RepeatTaskUi
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.databinding.RvRepeatTaskItemBinding
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon
import com.anitech.growdaily.enum_class.TaskType
import java.time.LocalDate
import java.time.format.DateTimeParseException

class RepeatTaskAdapter(
    private val listener: OnItemClickListener
) : ListAdapter<RepeatTaskUi, RepeatTaskAdapter.TaskViewHolder>(RepeatTaskDiffCallback()) {

    private var accentColor: Int? = null

    companion object {
        private val sharedHistoryPool = RecyclerView.RecycledViewPool()
        private const val PAYLOAD_PROGRESS_ONLY = "progress_only"
    }

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

        override fun getChangePayload(oldItem: RepeatTaskUi, newItem: RepeatTaskUi): Any? {
            val staticSame =
                oldItem.task == newItem.task &&
                    oldItem.seriesStartDate == newItem.seriesStartDate &&
                    oldItem.taskIdByDate == newItem.taskIdByDate &&
                    oldItem.historyItems == newItem.historyItems &&
                    oldItem.unavailableDates == newItem.unavailableDates &&
                    oldItem.trackingVersions == newItem.trackingVersions

            val dynamicChanged =
                oldItem.progressByDate != newItem.progressByDate ||
                    oldItem.completionByDate != newItem.completionByDate ||
                    oldItem.currentStreak != newItem.currentStreak ||
                    oldItem.completionOutOf10 != newItem.completionOutOf10 ||
                    oldItem.completedCount != newItem.completedCount ||
                    oldItem.totalDays != newItem.totalDays

            return if (staticSame && dynamicChanged) PAYLOAD_PROGRESS_ONLY else null
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = RvRepeatTaskItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TaskViewHolder(binding)
    }

    override fun getItemId(position: Int): Long {
        val item = getItem(position)
        return item.task.seriesId.ifBlank { item.task.id }.hashCode().toLong()
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(
        holder: TaskViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_PROGRESS_ONLY)) {
            holder.bindProgressOnly(getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class TaskViewHolder(
        private val binding: RvRepeatTaskItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var historyAdapter: HistoryAdapter? = null
        private var lastBoundSignature: String? = null
        private var lastHeatmapSignature: String? = null
        private var hasAutoScrolledHeatmap = false
        private var hasAutoScrolledHistory = false

        init {
            binding.weekRecycler.apply {
                layoutManager = LinearLayoutManager(
                    context,
                    LinearLayoutManager.HORIZONTAL,
                    false
                )
                itemAnimator = null
                isNestedScrollingEnabled = false
                setHasFixedSize(true)
                setRecycledViewPool(sharedHistoryPool)
            }
        }

        fun bind(item: RepeatTaskUi) = with(binding) {
            val task = item.task
            val bindSignature = buildHistorySignature(item)
            val heatmapSignature = buildHeatmapSignature(item)

            taskTitle.text = task.title
            val label = when (task.taskType) {
                TaskType.DAILY -> {
                    when (task.repeatType) {
                        com.anitech.growdaily.enum_class.RepeatType.DAYS_OF_WEEK -> {
                            com.anitech.growdaily.CommonMethods.formatRepeatSummary(task.repeatType, task.repeatDays)
                        }
                        com.anitech.growdaily.enum_class.RepeatType.DAYS_OF_MONTH -> {
                            "Days of month"
                        }
                        else -> {
                            "Every day"
                        }
                    }
                }
                TaskType.DAY -> {
                    "Day task"
                }
                TaskType.UNTIL_COMPLETE -> {
                    root.context.getString(task.taskType.labelRes)
                }
            }
            taskType.text = label
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
            
            val isNight = (root.context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val alphaFactor = if (isNight) 0.12f else 0.07f
            val containerTint = ColorStateList.valueOf(colorInt.adjustAlpha(alphaFactor))

            weightContainer.backgroundTintList = containerTint
            streakContainer.backgroundTintList = containerTint

            txtStreakCount.text = "${item.currentStreak}"
            txtScoreOutOf10.text = formatScoreOutOf10(item.completionOutOf10)
            txtScoreOutOf10.setTextColor(colorInt)

            val progressPercent = ((item.completionOutOf10 / 10f) * 100).toInt()
            progressBar.setIndicatorColor(colorInt)
            progressBar.trackColor = colorInt.adjustAlpha(0.18f)
            progressBar.progress = progressPercent

            if (lastHeatmapSignature != heatmapSignature) {
                heatmapView.bindHeatmap(
                    taskAddedDate = item.seriesStartDate,
                    progressByDate = item.progressByDate,
                    unavailableDates = item.unavailableDates,
                    activeColor = colorInt
                )
                lastHeatmapSignature = heatmapSignature
            }

            if (lastBoundSignature != bindSignature && !hasAutoScrolledHeatmap) {
                heatmapScroll.post {
                    heatmapScroll.getChildAt(0)?.let { child ->
                        heatmapScroll.scrollTo(child.measuredWidth, 0)
                    }
                }
                hasAutoScrolledHeatmap = true
            }

            val effectiveItems = if (lastBoundSignature != bindSignature) {
                item.historyItems.ifEmpty {
                    // Future start date: show one placeholder cell
                    listOf(com.anitech.growdaily.data_class.WeekHabit(
                        date = item.seriesStartDate,
                        dayLetter = item.seriesStartDate.dayOfWeek.getDisplayName(
                            java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()
                        )
                    ))
                }
            } else emptyList()

            if (historyAdapter == null || lastBoundSignature != bindSignature) {
                historyAdapter = HistoryAdapter(
                    taskAddedDate = item.seriesStartDate,
                    progressByDate = item.progressByDate,
                    taskColor = colorInt,
                    weekList = effectiveItems,
                    listener = object : HistoryAdapter.OnItemClickListener {
                        override fun onTaskCompleteClick(date: String) {
                            listener.onTaskCompleteClick(resolveTaskIdForDate(item, date), date)
                        }
                    }
                )
                weekRecycler.adapter = historyAdapter
                hasAutoScrolledHistory = false
            } else {
                historyAdapter?.updateData(
                    progressByDate = item.progressByDate,
                    taskColor = colorInt
                )
            }

            historyAdapter?.let { adapter ->
                if (adapter.itemCount > 0 && !hasAutoScrolledHistory) {
                    weekRecycler.scrollToPosition(adapter.itemCount - 1)
                    hasAutoScrolledHistory = true
                }
            }

            lastBoundSignature = bindSignature
            root.setOnClickListener { listener.moveToEditListener(task) }
        }

        fun bindProgressOnly(item: RepeatTaskUi) = with(binding) {
            val colorInt = TaskColor.fromName(item.task.colorCode)
                ?.toColorInt(root.context)
                ?: ContextCompat.getColor(root.context, R.color.brand_blue)
            val heatmapSignature = buildHeatmapSignature(item)

            txtStreakCount.text = "${item.currentStreak}"
            txtScoreOutOf10.text = formatScoreOutOf10(item.completionOutOf10)
            txtScoreOutOf10.setTextColor(colorInt)

            val progressPercent = ((item.completionOutOf10 / 10f) * 100).toInt()
            progressBar.setIndicatorColor(colorInt)
            progressBar.trackColor = colorInt.adjustAlpha(0.18f)
            progressBar.progress = progressPercent

            if (lastHeatmapSignature != heatmapSignature) {
                heatmapView.bindHeatmap(
                    taskAddedDate = item.seriesStartDate,
                    progressByDate = item.progressByDate,
                    unavailableDates = item.unavailableDates,
                    activeColor = colorInt
                )
                lastHeatmapSignature = heatmapSignature
            }

            historyAdapter?.updateData(
                progressByDate = item.progressByDate,
                taskColor = colorInt
            )
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

        private fun buildHeatmapSignature(item: RepeatTaskUi): String {
            return listOf(
                item.seriesStartDate.toString(),
                item.progressByDate.hashCode().toString(),
                item.unavailableDates.hashCode().toString(),
                item.task.colorCode
            ).joinToString("|")
        }

        private fun formatScoreOutOf10(score: Float): String {
            return if (score % 1f == 0f) {
                score.toInt().toString()
            } else {
                val context = binding.root.context
                context.getString(R.string.score_format_decimal, score)
            }
        }

        private fun resolveTaskIdForDate(item: RepeatTaskUi, date: String): String {
            return try {
                item.taskIdByDate[LocalDate.parse(date)] ?: item.task.id
            } catch (_: DateTimeParseException) {
                item.task.id
            }
        }
    }

    fun setAccentColor(color: Int) {
        this.accentColor = color
        notifyDataSetChanged()
    }

    init {
        setHasStableIds(true)
    }
}
