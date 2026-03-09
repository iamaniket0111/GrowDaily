package com.anitech.growdaily.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
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

class TaskAdapter(
    private val listener: OnItemClickListener
) : ListAdapter<TaskUiItem, TaskAdapter.ViewHolder>(TaskDiffCallback()) {
    private var currentDate: String = getTodayDate()
    private var dateMode: DateMode = DateMode.TODAY
    private val colorStateListCache = mutableMapOf<Int, ColorStateList>()

    interface OnItemClickListener {
        fun moveToEditListener(task: TaskEntity)
        fun onTaskCompleteClick(taskId: String, date: String)           // increment
        fun onTaskCompleteLongClick(taskId: String, date: String)       // decrement
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
            setTaskData(task, item.currentStreak)
            setTaskNote(task)
            setTaskType(task)
            handleScheduledTime(task)

            // Accessibility
            root.contentDescription = "Task: ${task.title}, Type: ${task.taskType.name}"
            val color = TaskColor.valueOf(task.colorCode).toColorInt(root.context)

            val isPastLike = item.isListFiltered || item.dateMode == DateMode.PAST
            val isToday = !item.isListFiltered && item.dateMode == DateMode.TODAY
            val showTime = isToday && task.isScheduled

            doneContainer.visibility = if (isPastLike || isToday) View.VISIBLE else View.GONE
            shContainer.visibility = if (isToday) View.VISIBLE else View.GONE
            timeTxt.visibility = if (showTime) View.VISIBLE else View.GONE


            val target = task.dailyTargetCount.coerceAtLeast(1)
            val isCompleted = item.completionCount >= target
            updateCompletionState(task, item.completionCount, isCompleted)

            updateColors(task, isActive, color, item.timeState, isCompleted)
            setupClickListeners(task, currentDate)
        }

        private fun setTaskNote(task: TaskEntity) = with(binding) {
            if (!task.note.isNullOrEmpty()) {
                taskNote.text = task.note
                taskNote.visibility = View.VISIBLE
            } else {
                taskNote.visibility = View.GONE
            }
        }

        private fun setTaskType(task: TaskEntity) = with(binding) {
            when (task.taskType) {
                TaskType.DAY -> taskType.text = "Today"
                TaskType.DAILY -> taskType.text = "Daily"
                TaskType.UNTIL_COMPLETE -> taskType.text = "Until Done"
            }
        }

        private fun setTaskData(task: TaskEntity,currentStreak:Int) = with(binding) {
            taskTitle.text = task.title
            val icon = runCatching { TaskIcon.valueOf(task.iconResId) }
                .getOrDefault(TaskIcon.entries.first())
            imageProfile.setImageResource(icon.resId)
            taskWeight.text =
                root.context.getString(R.string.task_weight_prefix, task.weight.weight)

            taskWeight.visibility = if (task.taskType == TaskType.UNTIL_COMPLETE) {
                View.GONE
            } else {
                View.VISIBLE
            }

            // Show streak only for DAILY tasks
            if (task.taskType == TaskType.DAILY ) {
                streakContainer.visibility = View.VISIBLE
                taskStreak.text = "${currentStreak}"
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
            completionCount: Int,
            isCompleted: Boolean
        ) = with(binding) {
            val target = task.dailyTargetCount.coerceAtLeast(1)
            // icon
            done.setImageResource(if (isCompleted) R.drawable.ic_check else 0)

            // doneCount text
            if (task.dailyTargetCount > 1 && completionCount < target) {
                doneCount.visibility = View.VISIBLE
                doneCount.text = "$completionCount/${task.dailyTargetCount}"
            } else {
                doneCount.visibility = View.GONE
            }
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
            val defaultTextColor = ContextCompat.getColor(context, R.color.default_text_color)
            val black = ContextCompat.getColor(context, R.color.black)
            val lightBg = ContextCompat.getColor(context, R.color.lightBackground)
            val iconTint = ContextCompat.getColor(context, R.color.iconTint)


            if (isActive) {

                // TEXT COLORS
                taskTitle.setTextColor(white)
                taskNote.setTextColor(white)
                taskType.setTextColor(white)
                taskWeight.setTextColor(white)
                taskStreak.setTextColor(white)

                // ICON
                imageProfile.backgroundTintList = getCachedColorStateList(white)
                imageProfile.setColorFilter(color)
                flag.setColorFilter(white)
                fire.setColorFilter(white)


                // DONE
                doneContainer.backgroundTintList = getCachedColorStateList(white)
                done.backgroundTintList = getCachedColorStateList(color)

                // BACKGROUND
                body.backgroundTintList = getCachedColorStateList(color)
                weightContainer.backgroundTintList = getCachedColorStateList(color)
                streakContainer.backgroundTintList = getCachedColorStateList(color)



            } else {

                // TEXT COLORS
                taskTitle.setTextColor(black)
                taskNote.setTextColor(defaultTextColor)
                taskType.setTextColor(color)
                taskWeight.setTextColor(defaultTextColor)
                taskStreak.setTextColor(defaultTextColor)


                // ICON
                imageProfile.backgroundTintList = getCachedColorStateList(color)
                imageProfile.setColorFilter(white)
                flag.setColorFilter(iconTint)
                fire.setColorFilter(iconTint)

                // DONE
                doneContainer.backgroundTintList = getCachedColorStateList(color)
                done.backgroundTintList =
                    getCachedColorStateList(if (isCompleted) color else white)

                // BACKGROUND
                body.backgroundTintList = getCachedColorStateList(white)
                weightContainer.backgroundTintList = getCachedColorStateList(lightBg)
                streakContainer.backgroundTintList = getCachedColorStateList(lightBg)
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

        private fun setupClickListeners(
            task: TaskEntity, currentDate: String
        ) = with(binding) {
            root.setOnClickListener {
                listener.moveToEditListener(task)
            }

            root.setOnLongClickListener {
                Toast.makeText(root.context, "Not implemented", Toast.LENGTH_SHORT).show()
                true
            }

            doneContainer.setOnClickListener {
                listener.onTaskCompleteClick(task.id, currentDate)
            }

            doneContainer.setOnLongClickListener {
                listener.onTaskCompleteLongClick(task.id, currentDate)
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
                oldItem.completionCount == newItem.completionCount&&
                oldItem.isListFiltered == newItem.isListFiltered

    }
}