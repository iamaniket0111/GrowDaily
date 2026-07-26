package com.anitech.growdaily.adapter

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.setSolidBackgroundColorCompat
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.databinding.RvTaskItemBinding
import com.anitech.growdaily.adjustAlpha
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon
import com.anitech.growdaily.enum_class.TaskType
import java.util.Collections

class TaskReorderAdapter(
    private var taskList: MutableList<TaskEntity>,
    private val dragStartListener: (RecyclerView.ViewHolder) -> Unit,
    private val reorderCompleteListener: OnReorderCompleteListener? = null,
) : RecyclerView.Adapter<TaskReorderAdapter.ViewHolder>() {

    interface OnReorderCompleteListener {
        fun onReorderComplete(orderedTaskIds: List<String>)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvTaskItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = taskList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(taskList[position])
    }

    inner class ViewHolder(
        private val binding: RvTaskItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility")
        fun bind(task: TaskEntity) {

            binding.body.taskTitle.text = task.title
            binding.body.taskTitle.setTextAppearance(R.style.ReorderItemTitle)
            binding.body.taskNote.visibility = View.GONE
            binding.body.taskPendingText.visibility = View.GONE

            val icon = TaskIcon.fromName(task.iconResId)
            val colorInt = TaskColor.valueOf(task.colorCode).toColorInt(itemView.context)

            binding.body.imageProfile.setImageResource(icon.resId)
            binding.body.imageProfile.setSolidBackgroundColorCompat(colorInt)

            binding.body.weightContainer.visibility = View.GONE
            binding.body.streakContainer.visibility = View.GONE
            binding.body.reminderContainer.visibility = View.GONE

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
                TaskType.DAY -> "Day task"
                TaskType.UNTIL_COMPLETE -> itemView.context.getString(task.taskType.labelRes)
            }

            binding.body.taskType.setTextColor(colorInt)
            binding.body.taskType.text = label

            val context = itemView.context
            val secondaryText = ContextCompat.getColor(context, R.color.task_text_secondary)
            val iconTint = ContextCompat.getColor(context, R.color.iconTint)
            val isNight = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val alphaFactor = if (isNight) 0.12f else 0.07f
            val badgeBgTint = ColorStateList.valueOf(colorInt.adjustAlpha(alphaFactor))

            // Badge 1: Scheduled Time Badge
            if (task.isScheduled && !task.scheduledTime.isNullOrBlank()) {
                binding.body.scheduleTimeContainer.visibility = View.VISIBLE
                binding.body.scheduleTimeText.text = task.scheduledTime
                binding.body.scheduleTimeContainer.backgroundTintList = badgeBgTint
                binding.body.scheduleTimeText.setTextColor(secondaryText)
                binding.body.scheduleClockIcon.setColorFilter(iconTint)
            } else {
                binding.body.scheduleTimeContainer.visibility = View.GONE
            }

            // Badges 2 & 3: Status Badge (Paused or Stopped/Ended)
            when (task.inactiveReason) {
                com.anitech.growdaily.enum_class.TaskInactiveReason.PAUSED -> {
                    binding.body.statusContainer.visibility = View.VISIBLE
                    binding.body.statusText.text = itemView.context.getString(R.string.paused_repeat_tasks_tab)
                    binding.body.statusIcon.setImageResource(R.drawable.ic_pause)
                    binding.body.statusContainer.backgroundTintList = badgeBgTint
                    binding.body.statusText.setTextColor(secondaryText)
                    binding.body.statusIcon.setColorFilter(iconTint)
                }
                com.anitech.growdaily.enum_class.TaskInactiveReason.ENDED -> {
                    binding.body.statusContainer.visibility = View.VISIBLE
                    binding.body.statusText.text = itemView.context.getString(R.string.ended_repeat_tasks_tab)
                    binding.body.statusIcon.setImageResource(R.drawable.ic_close)
                    binding.body.statusContainer.backgroundTintList = badgeBgTint
                    binding.body.statusText.setTextColor(secondaryText)
                    binding.body.statusIcon.setColorFilter(iconTint)
                }
                else -> {
                    binding.body.statusContainer.visibility = View.GONE
                }
            }

            binding.body.done.background = null
            binding.body.doneView.visibility = View.GONE
            binding.body.notAllowedImg.visibility = View.GONE

            // Hide top time header & timeline container so all cards have 100% uniform height
            binding.timeTxt.visibility = View.GONE
            binding.shContainer.visibility = View.GONE

            // Show drag handle for all tasks in the reorder screen
            binding.body.doneContainer.visibility = View.VISIBLE
            binding.body.done.visibility = View.VISIBLE
            binding.body.done.setImageResource(R.drawable.ic_drag_handle)
            binding.body.done.imageTintList = ColorStateList.valueOf(colorInt)

            binding.body.doneContainer.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    dragStartListener(this)
                }
                false
            }

            binding.shContainer.visibility = View.GONE

            // Reset visual state
            binding.body.root.translationZ = 0f
            binding.body.root.alpha = 1.0f
        }

        fun onItemSelected() {
            binding.body.root.animate()
                .translationZ(12f)
                .alpha(0.85f)
                .setDuration(150)
                .start()
        }

        fun onItemClear() {
            binding.body.root.animate()
                .translationZ(0f)
                .alpha(1.0f)
                .setDuration(150)
                .start()
        }
    }

    fun moveItem(from: Int, to: Int) {
        if (from == to) return
        Collections.swap(taskList, from, to)
        notifyItemMoved(from, to)
    }

    fun notifyReorderFinished() {
        val orderedIds = taskList.map { it.id }
        reorderCompleteListener?.onReorderComplete(orderedIds)
    }

    fun updateList(newTasks: List<TaskEntity>) {
        val diffResult = DiffUtil.calculateDiff(TaskDiffCallback(taskList, newTasks))
        taskList.clear()
        taskList.addAll(newTasks)
        diffResult.dispatchUpdatesTo(this)
    }

    private class TaskDiffCallback(
        private val oldList: List<TaskEntity>,
        private val newList: List<TaskEntity>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean =
            oldList[oldPos].id == newList[newPos].id
        override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean =
            oldList[oldPos] == newList[newPos]
    }
}
