package com.anitech.growdaily.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.anitech.growdaily.setSolidBackgroundColorCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.CommonMethods.Companion.getTodayDate
import com.anitech.growdaily.R
import com.anitech.growdaily.adjustAlpha
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.TaskUiItem
import com.anitech.growdaily.databinding.RvTaskItemBinding
import com.anitech.growdaily.enum_class.DateMode
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon
import com.anitech.growdaily.enum_class.TaskType
import com.anitech.growdaily.enum_class.TimeState
import com.google.android.material.progressindicator.CircularProgressIndicator

class TaskAdapter(
    private val listener: OnItemClickListener
) : ListAdapter<TaskUiItem, TaskAdapter.ViewHolder>(TaskDiffCallback()) {
    private var currentDate: String = getTodayDate()
    private var dateMode: DateMode = DateMode.TODAY

    private val colorStateListCache = mutableMapOf<Int, ColorStateList>()

    init {
        setHasStableIds(true)
    }

    interface OnItemClickListener {
        fun moveToEditListener(task: TaskEntity)
        fun onTaskCompleteClick(taskId: String, date: String)
        fun onTaskCompleteLongClick(taskId: String, date: String)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskAdapter.ViewHolder {
        val binding = RvTaskItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)

    }

    override fun onBindViewHolder(holder: TaskAdapter.ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, currentDate)
    }

    override fun getItemId(position: Int): Long = getItem(position).listItemKey.hashCode().toLong()

    inner class ViewHolder(
        private val binding: RvTaskItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: TaskUiItem, currentDate: String
        ) = with(binding) {
            val task = item.task
            val isActive = item.isActive

            // Basic data setup
            setTaskData(item)
            setTaskNote(task, item.pendingFromDate)
            setTaskType(task)
            handleScheduledTime(task)

            // Accessibility
            root.contentDescription = root.context.getString(R.string.task_content_description, task.title, task.taskType.name)
            val color = TaskColor.valueOf(task.colorCode).toColorInt(root.context)

            val isPastLike = item.isListFiltered || item.dateMode == DateMode.PAST
            val isToday = !item.isListFiltered && item.dateMode == DateMode.TODAY
            val isFuture = item.dateMode == DateMode.FUTURE
            val showTime = isToday && task.isScheduled

            shContainer.visibility = if (isToday) View.VISIBLE else View.GONE
            timeTxt.visibility = if (showTime) View.VISIBLE else View.GONE

            if (isFuture) {
                body.doneView.visibility = View.VISIBLE
                body.notAllowedImg.visibility = View.VISIBLE
                body.done.visibility = View.GONE
                body.doneContainer.alpha = 0.38f
            } else {
                body.doneView.visibility = if (isPastLike || isToday) View.VISIBLE else View.GONE
                body.notAllowedImg.visibility = View.GONE
                body.done.visibility = View.VISIBLE
                body.doneContainer.alpha = 1f
            }

            val isCompleted = item.isCompleted
            updateCompletionState(task, item.completionPercent, isCompleted)

            updateColors(task, isActive, color, item.timeState, isCompleted)
            setupClickListeners(item, task, currentDate)
        }

        private fun setTaskNote(task: TaskEntity, pendingFromDate: String?) = with(binding) {
            if (!task.note.isNullOrEmpty()) {
                body.taskNote.text = task.note
                body.taskNote.visibility = View.VISIBLE
            } else {
                body.taskNote.visibility = View.GONE
            }
            if (!pendingFromDate.isNullOrBlank()) {
                body.taskPendingText.text = root.context.getString(R.string.pending_from_format, com.anitech.growdaily.CommonMethods.formatDate(pendingFromDate))
                body.taskPendingText.visibility = View.VISIBLE
            } else {
                body.taskPendingText.visibility = View.GONE
            }
        }

        private fun setTaskType(task: TaskEntity) = with(binding) {
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
            body.taskType.text = label
        }

        private fun setTaskData(item: TaskUiItem) = with(binding) {
            val task = item.task
            body.taskTitle.text = task.title
            body.imageProfile.setImageResource(TaskIcon.fromName(task.iconResId).resId)
            body.taskWeight.text =
                root.context.getString(R.string.task_weight_prefix, item.trackingSettings.weightValue)

            body.taskWeight.visibility = if (task.taskType == TaskType.UNTIL_COMPLETE) {
                View.GONE
            } else {
                View.VISIBLE
            }

            // Show streak only for DAILY tasks
            if (task.taskType == TaskType.DAILY ) {
                body.streakContainer.visibility = View.VISIBLE
                body.taskStreak.text = "${item.currentStreak}"
            } else {
                body.streakContainer.visibility = View.GONE
            }

            // Show reminder time if reminder is enabled
            if (task.reminderEnabled && !task.reminderTime.isNullOrBlank()) {
                body.reminderContainer.visibility = View.VISIBLE
                body.remTime.text = task.reminderTime
            } else {
                body.reminderContainer.visibility = View.GONE
            }
        }

        private fun handleScheduledTime(task: TaskEntity) = with(binding) {
            if (task.scheduledTime != null) {
                timeTxt.text = task.scheduledTime
                timeTxt.visibility = View.VISIBLE
                shView.visibility = if (task.isScheduled) View.VISIBLE else View.GONE
            } else {
                timeTxt.text = ""
                timeTxt.visibility = View.GONE
                shView.visibility = View.GONE
            }
        }

        private fun updateCompletionState(
            task: TaskEntity,
            completionPercent: Int,
            isCompleted: Boolean
        ) = with(binding) {
            body.doneView.max = 100
            body.doneView.progress = completionPercent.coerceIn(0, 100)
            body.done.setImageResource(if (isCompleted) R.drawable.ic_check else 0)
            body.done.alpha = if (isCompleted) 1f else 0f
        }


        private fun getCachedColorStateList(color: Int): ColorStateList {
            return colorStateListCache.getOrPut(color) { ColorStateList.valueOf(color) }
        }

        private fun applyTheme(
            color: Int,
            white: Int,
            task: TaskEntity,
            isActive: Boolean,
            timeState: TimeState,
            isCompleted: Boolean
        ) = with(binding) {
            val context = root.context
            val primaryText = ContextCompat.getColor(context, R.color.task_text_primary)
            val secondaryText = ContextCompat.getColor(context, R.color.task_text_secondary)
            val cardSurface = ContextCompat.getColor(context, R.color.task_card_surface)
            val mutedSurface = ContextCompat.getColor(context, R.color.task_done_track)
            val iconTint = ContextCompat.getColor(context, R.color.iconTint)

            if (isActive) {
                // TEXT COLORS
                body.taskTitle.setTextColor(white)
                body.taskNote.setTextColor(white)
                body.taskPendingText.setTextColor(white)
                body.taskType.setTextColor(white)
                body.taskWeight.setTextColor(white)
                body.taskStreak.setTextColor(white)
                body.remTime.setTextColor(white)

                // ICON
                body.imageProfile.setSolidBackgroundColorCompat(white)
                body.imageProfile.setColorFilter(color)
                body.flag.setColorFilter(white)
                body.fire.setColorFilter(white)
                body.bell.setColorFilter(white)
                body.notAllowedImg.setColorFilter(white)

                // DONE
                styleDoneProgress(
                    progressView = body.doneView,
                    indicatorColor = white,
                    trackColor = white.adjustAlpha(0.28f)
                )
                body.done.setSolidBackgroundColorCompat(if (isCompleted) color else white)
                body.done.imageTintList = getCachedColorStateList(white)

                // BACKGROUND
                body.root.backgroundTintList = getCachedColorStateList(color)
                body.weightContainer.backgroundTintList = getCachedColorStateList(color)
                body.streakContainer.backgroundTintList = getCachedColorStateList(color)
                body.reminderContainer.backgroundTintList = getCachedColorStateList(color)

            } else {

                // TEXT COLORS
                body.taskTitle.setTextColor(primaryText)
                body.taskNote.setTextColor(secondaryText)
                body.taskPendingText.setTextColor(color)
                body.taskType.setTextColor(color)
                body.taskWeight.setTextColor(secondaryText)
                body.taskStreak.setTextColor(secondaryText)
                body.remTime.setTextColor(secondaryText)

                // ICON
                body.imageProfile.setSolidBackgroundColorCompat(color)
                body.imageProfile.setColorFilter(white)
                body.flag.setColorFilter(iconTint)
                body.fire.setColorFilter(iconTint)
                body.bell.setColorFilter(iconTint)
                body.notAllowedImg.setColorFilter(color)

                // DONE
                styleDoneProgress(
                    progressView = body.doneView,
                    indicatorColor = color,
                    trackColor = color.adjustAlpha(0.18f)
                )
                body.done.setSolidBackgroundColorCompat(if (isCompleted) color else cardSurface)
                body.done.imageTintList = getCachedColorStateList(white)

                // BACKGROUND
                val isNight = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                val alphaFactor = if (isNight) 0.12f else 0.07f
                
                body.root.backgroundTintList = getCachedColorStateList(cardSurface)
                body.weightContainer.backgroundTintList = getCachedColorStateList(color.adjustAlpha(alphaFactor))
                body.streakContainer.backgroundTintList = getCachedColorStateList(color.adjustAlpha(alphaFactor))
                body.reminderContainer.backgroundTintList = getCachedColorStateList(color.adjustAlpha(alphaFactor))
            }

            // shView update
            if (dateMode == DateMode.TODAY && task.isScheduled && timeState != TimeState.NONE) {
                shView.setImageResource(
                    when {
                        isActive -> R.drawable.sh_view3
                        timeState == TimeState.PAST -> R.drawable.sh_view1
                        timeState == TimeState.CURRENT -> R.drawable.sh_view1
                        timeState == TimeState.FUTURE -> R.drawable.sh_view2
                        else -> 0
                    }
                )
            }
        }

        private fun updateColors(
            task: TaskEntity,
            isActive: Boolean,
            color: Int,
            timeState: TimeState,
            isCompleted: Boolean
        ) = with(binding) {
            val white = Color.WHITE
            applyTheme(color, white, task, isActive, timeState, isCompleted)

            // Common tints
            shView.setColorFilter(color)
            shDivider.backgroundTintList = getCachedColorStateList(color)
        }

        private fun styleDoneProgress(
            progressView: CircularProgressIndicator,
            indicatorColor: Int,
            trackColor: Int
        ) {
            progressView.setIndicatorColor(indicatorColor)
            progressView.trackColor = trackColor
        }

        private fun setupClickListeners(
            item: TaskUiItem,
            task: TaskEntity,
            currentDate: String
        ) = with(binding) {
            root.setOnClickListener {
                listener.moveToEditListener(item.sourceTask ?: task)
            }

            root.setOnLongClickListener {
                Toast.makeText(root.context, R.string.not_implemented, Toast.LENGTH_SHORT).show()
                true
            }

            if (item.dateMode == DateMode.FUTURE) {
                body.doneContainer.setOnClickListener(null)
                body.doneContainer.setOnLongClickListener(null)
                body.doneContainer.isClickable = false
                body.doneContainer.isFocusable = false
            } else {
                body.doneContainer.isClickable = true
                body.doneContainer.isFocusable = true
                body.doneContainer.setOnClickListener {
                    listener.onTaskCompleteClick(task.id, item.completionDate)
                }

                body.doneContainer.setOnLongClickListener {
                    listener.onTaskCompleteLongClick(task.id, item.completionDate)
                    true
                }
            }
        }
    }


    fun updateList(newList: List<TaskUiItem>, currentDatee: String, mode: DateMode) {
        currentDate = currentDatee
        dateMode = mode
        submitList(newList)
    }


}

class TaskDiffCallback : DiffUtil.ItemCallback<TaskUiItem>() {

    override fun areItemsTheSame(oldItem: TaskUiItem, newItem: TaskUiItem): Boolean {
        return oldItem.listItemKey == newItem.listItemKey
    }

    override fun areContentsTheSame(oldItem: TaskUiItem, newItem: TaskUiItem): Boolean {
        return oldItem.task == newItem.task &&
                oldItem.isActive == newItem.isActive &&
                oldItem.timeState == newItem.timeState &&
                oldItem.dateMode == newItem.dateMode &&
                oldItem.completionPercent == newItem.completionPercent &&
                oldItem.trackingSettings == newItem.trackingSettings &&
                oldItem.isCompleted == newItem.isCompleted &&
                oldItem.isListFiltered == newItem.isListFiltered &&
                oldItem.completionDate == newItem.completionDate &&
                oldItem.pendingFromDate == newItem.pendingFromDate

    }
}
