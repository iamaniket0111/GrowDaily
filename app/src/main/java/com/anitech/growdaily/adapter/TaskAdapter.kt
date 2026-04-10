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
            setTaskNote(task, item.pendingFromText)
            setTaskType(task)
            handleScheduledTime(task)

            // Accessibility
            root.contentDescription = "Task: ${task.title}, Type: ${task.taskType.name}"
            val color = TaskColor.valueOf(task.colorCode).toColorInt(root.context)

            val isPastLike = item.isListFiltered || item.dateMode == DateMode.PAST
            val isToday = !item.isListFiltered && item.dateMode == DateMode.TODAY
            val showTime = isToday && task.isScheduled

            doneView.visibility = if (isPastLike || isToday) View.VISIBLE else View.GONE
            shContainer.visibility = if (isToday) View.VISIBLE else View.GONE
            timeTxt.visibility = if (showTime) View.VISIBLE else View.GONE

            val isCompleted = item.isCompleted
            updateCompletionState(task, item.completionPercent, isCompleted)

            updateColors(task, isActive, color, item.timeState, isCompleted)
            setupClickListeners(item, task, currentDate)
        }

        private fun setTaskNote(task: TaskEntity, pendingFromText: String?) = with(binding) {
            if (!task.note.isNullOrEmpty()) {
                taskNote.text = task.note
                taskNote.visibility = View.VISIBLE
            } else {
                taskNote.visibility = View.GONE
            }
            if (!pendingFromText.isNullOrBlank()) {
                taskPendingText.text = pendingFromText
                taskPendingText.visibility = View.VISIBLE
            } else {
                taskPendingText.visibility = View.GONE
            }
        }

        private fun setTaskType(task: TaskEntity) = with(binding) {
            taskType.text = binding.root.context.getString(task.taskType.labelRes)
        }

        private fun setTaskData(item: TaskUiItem) = with(binding) {
            val task = item.task
            taskTitle.text = task.title
            imageProfile.setImageResource(TaskIcon.fromName(task.iconResId).resId)
            taskWeight.text =
                root.context.getString(R.string.task_weight_prefix, item.trackingSettings.weightValue)

            taskWeight.visibility = if (task.taskType == TaskType.UNTIL_COMPLETE) {
                View.GONE
            } else {
                View.VISIBLE
            }

            // Show streak only for DAILY tasks
            if (task.taskType == TaskType.DAILY ) {
                streakContainer.visibility = View.VISIBLE
                taskStreak.text = "${item.currentStreak}"
            } else {
                streakContainer.visibility = View.GONE
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
            doneView.max = 100
            doneView.progress = completionPercent.coerceIn(0, 100)
            done.setImageResource(if (isCompleted) R.drawable.ic_check else 0)
            done.alpha = if (isCompleted) 1f else 0f
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
                taskTitle.setTextColor(white)
                taskNote.setTextColor(white)
                taskPendingText.setTextColor(white)
                taskType.setTextColor(white)
                taskWeight.setTextColor(white)
                taskStreak.setTextColor(white)

                // ICON
                imageProfile.setSolidBackgroundColorCompat(white)
                imageProfile.setColorFilter(color)
                flag.setColorFilter(white)
                fire.setColorFilter(white)


                // DONE
                styleDoneProgress(
                    progressView = doneView,
                    indicatorColor = white,
                    trackColor = adjustAlpha(white, 0.28f)
                )
                done.setSolidBackgroundColorCompat(if (isCompleted) color else white)
                done.imageTintList = getCachedColorStateList(white)

                // BACKGROUND
                body.backgroundTintList = getCachedColorStateList(color)
                weightContainer.backgroundTintList = getCachedColorStateList(color)
                streakContainer.backgroundTintList = getCachedColorStateList(color)



            } else {

                // TEXT COLORS
                taskTitle.setTextColor(primaryText)
                taskNote.setTextColor(secondaryText)
                taskPendingText.setTextColor(color)
                taskType.setTextColor(color)
                taskWeight.setTextColor(secondaryText)
                taskStreak.setTextColor(secondaryText)


                // ICON
                imageProfile.setSolidBackgroundColorCompat(color)
                imageProfile.setColorFilter(white)
                flag.setColorFilter(iconTint)
                fire.setColorFilter(iconTint)

                // DONE
                styleDoneProgress(
                    progressView = doneView,
                    indicatorColor = color,
                    trackColor = adjustAlpha(color, 0.18f)
                )
                done.setSolidBackgroundColorCompat(if (isCompleted) color else cardSurface)
                done.imageTintList = getCachedColorStateList(white)

                // BACKGROUND
                body.backgroundTintList = getCachedColorStateList(cardSurface)
                weightContainer.backgroundTintList = getCachedColorStateList(mutedSurface)
                streakContainer.backgroundTintList = getCachedColorStateList(mutedSurface)
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

        private fun adjustAlpha(color: Int, factor: Float): Int {
            val alpha = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
            return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        }

        private fun setupClickListeners(
            item: TaskUiItem,
            task: TaskEntity,
            currentDate: String
        ) = with(binding) {
            root.setOnClickListener {
                listener.moveToEditListener(task)
            }

            root.setOnLongClickListener {
                Toast.makeText(root.context, "Not implemented", Toast.LENGTH_SHORT).show()
                true
            }

            doneContainer.setOnClickListener {
                listener.onTaskCompleteClick(task.id, item.completionDate)
            }

            doneContainer.setOnLongClickListener {
                listener.onTaskCompleteLongClick(task.id, item.completionDate)
                true
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
        return oldItem.task.id == newItem.task.id
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
                oldItem.pendingFromText == newItem.pendingFromText

    }
}
