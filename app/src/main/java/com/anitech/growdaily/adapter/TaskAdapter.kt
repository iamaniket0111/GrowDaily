package com.anitech.growdaily.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.CommonMethods.Companion.getTodayDate
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.DailyTask
import com.anitech.growdaily.databinding.RvDailyTaskItemBinding
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon
import com.anitech.growdaily.enum_class.TaskType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TaskAdapter(
    private var numbersList: List<DailyTask>,
    private val listener: OnItemClickListener,
) : RecyclerView.Adapter<TaskAdapter.ViewHolder>() {

    private var currentDate: String = getTodayDate()
    private val selectedItems = mutableSetOf<DailyTask>()
    var isFutureDate = false
    private var selectionMode = false

    private val tag: String = "TaskAdapter"
    private var activeScheduledTime: String? = null  // Latest past/current scheduled time


    interface OnItemClickListener {
        fun moveToEditListener(task: DailyTask)
        fun onItemSelectedCountChanged(count: Int)
        fun onTaskCompleteClick(task: DailyTask)
    }

    fun selectionCount(): Int = selectedItems.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            RvDailyTaskItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val task = numbersList[position]
        val isSelected = selectedItems.contains(task)
        holder.bind(task, isSelected, position, currentDate, isFutureDate)
    }

    override fun getItemCount(): Int = numbersList.size


    inner class ViewHolder(private val binding: RvDailyTaskItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            task: DailyTask,
            isSelected: Boolean,
            position: Int,
            currentDate: String,
            isFutureDate: Boolean
        ) = with(binding) {

            setTaskData(task)
            // Note visibility
            setTaskNote(task)
            // Task type text (color later in final block)
            setTaskType(task)
            // Scheduled time handling
            handleScheduledTime(task)

            // Disable interaction for future dates
            if (isFutureDate) {
                futureTaskWork()
                return
            }
            root.isEnabled = true
            doneContainer.isEnabled = true


            updateCompletionState(task, currentDate)

            // Divider
            if (position == numbersList.size - 1) {
                shDivider.visibility = View.GONE
            } else {
                shDivider.visibility = View.VISIBLE
            }


            val isActive = task.isScheduled && task.scheduledTime != null &&
                    task.scheduledTime.trim()
                        .equals(activeScheduledTime?.trim(), ignoreCase = true)

            updateColors(task, isActive, isSelected)
            // Click listeners
            setupClickListeners(task, position, currentDate)

        }

        private fun setTaskNote(task: DailyTask) = with(binding) {
            if (!task.note.isNullOrEmpty()) {
                taskNote.text = task.note
                taskNote.visibility = View.VISIBLE
            } else {
                taskNote.visibility = View.GONE
            }
        }

        private fun setTaskType(task: DailyTask) = with(binding) {
            when (task.taskType) {
                TaskType.DAY -> taskType.text = "Today"
                TaskType.DAILY -> taskType.text = "Daily"
                TaskType.UNTIL_COMPLETE -> taskType.text = "Until Done"
            }
        }

        private fun toggleSelection(task: DailyTask, position: Int) {
            if (selectedItems.contains(task)) {
                selectedItems.remove(task)
            } else {
                selectedItems.add(task)
            }
            notifyItemChanged(position)
            listener.onItemSelectedCountChanged(selectedItems.size)

            if (selectedItems.isEmpty()) {
                selectionMode = false
                notifyDataSetChanged()
            }
        }










        private fun getShViewDrawableRes(timeComparison: String): Int {
            return when (timeComparison) {
                "future" -> R.drawable.sh_view2
                "past" -> R.drawable.sh_view1
                else -> 0
            }
        }

        private fun setTaskData(task: DailyTask) = with(binding) {
            taskTitle.text = task.title
            // Set icon and weight (tint later)
            imageProfile.setImageResource(TaskIcon.valueOf(task.iconResId).resId)
            taskWeight.text =
                root.context.getString(R.string.task_weight_prefix, task.weight.weight)
        }

        private fun handleScheduledTime(task: DailyTask) = with(binding) {
            if (task.scheduledTime != null) {
                timeTxt.text = task.scheduledTime
                timeTxt.visibility = View.VISIBLE

                if (task.isScheduled) {
                    shView.visibility = View.VISIBLE
                } else {
                    shView.visibility = View.GONE
                }
            } else {
                timeTxt.text = ""
                timeTxt.visibility = View.GONE
                shView.visibility = View.GONE
            }
        }

        private fun futureTaskWork() = with(binding) {
            root.isEnabled = false
            doneContainer.isEnabled = false

            taskTitle.setTextColor(
                ContextCompat.getColor(
                    root.context,
                    R.color.default_text_color
                )
            )
            taskNote.setTextColor(
                ContextCompat.getColor(
                    root.context,
                    android.R.color.darker_gray
                )
            )

            body.backgroundTintList =
                ContextCompat.getColorStateList(root.context, R.color.light_gray_25)
            done.setImageResource(0)
        }

        private fun updateCompletionState(task: DailyTask, currentDate: String) = with(binding) {
            // Completed or not (icon only here, colors later)
            if (task.completedDates.contains(currentDate)) {
                done.setImageResource(R.drawable.ic_check)
            } else {
                done.setImageResource(0)
            }
        }


        private fun updateColors(task: DailyTask, isActive: Boolean, isSelected: Boolean) =
            with(binding) {
                // Background logic with priorities
                val color = TaskColor.valueOf(task.colorCode).toColorInt(root.context)
                val white = Color.WHITE


                when {
                    isSelected -> body.backgroundTintList =
                        ContextCompat.getColorStateList(root.context, android.R.color.darker_gray)

                    task.completedDates.contains(currentDate) -> body.backgroundTintList =
                        ColorStateList.valueOf(
                            ContextCompat.getColor(root.context, R.color.category_dark_blue_10)
                        )

                    isActive -> {
                        // Highlight body: brand blue
                        body.backgroundTintList = ColorStateList.valueOf(color)
                    }

                    else -> body.backgroundTintList = ColorStateList.valueOf(white)
                }
// FINAL COLOR SETTING BLOCK - Highest priority for text/tints
                if (isActive) {
                    // HIGHLIGHT THEME
                    taskType.setTextColor(white)
                    taskTitle.setTextColor(white)
                    taskNote.setTextColor(white)
                    taskWeight.setTextColor(white)
                    imageProfile.backgroundTintList = ColorStateList.valueOf(white)
                    imageProfile.setColorFilter(color)
                    doneContainer.backgroundTintList = ColorStateList.valueOf(white)
                    shView.setImageResource(R.drawable.sh_view3)
                    Log.d(tag, "Applied HIGHLIGHT colors to ${task.title} (white/blue theme)")
                } else {
                    // ELSE THEME
                    taskType.setTextColor(color)
                    taskTitle.setTextColor(Color.BLACK)
                    taskNote.setTextColor(
                        ContextCompat.getColor(
                            root.context,
                            R.color.default_text_color
                        )
                    )
                    taskWeight.setTextColor(
                        ContextCompat.getColor(
                            root.context,
                            R.color.default_text_color
                        )
                    )
                    imageProfile.backgroundTintList = ColorStateList.valueOf(color)
                    imageProfile.setColorFilter(white)
                    doneContainer.backgroundTintList = ColorStateList.valueOf(color)
                    if (task.isScheduled && task.scheduledTime != null) {
                        val timeComp = getTimeComparison(task.scheduledTime)
                        shView.setImageResource(getShViewDrawableRes(timeComp))
                    }

                    // Title/Note based on completed
                    if (task.completedDates.contains(currentDate)) {
                        taskTitle.setTextColor(
                            ContextCompat.getColor(
                                root.context,
                                R.color.default_text_color
                            )
                        )
                        taskNote.setTextColor(
                            ContextCompat.getColor(
                                root.context,
                                android.R.color.darker_gray
                            )
                        )
                    } else {
                        taskTitle.setTextColor(ContextCompat.getColor(root.context, R.color.black))
                        taskNote.setTextColor(
                            ContextCompat.getColor(
                                root.context,
                                R.color.default_text_color
                            )
                        )
                    }

                    Log.d(tag, "Applied ELSE colors to ${task.title} (brand blue/white theme)")
                }
                //default colors
                shView.setColorFilter(color)
                shDivider.backgroundTintList = ColorStateList.valueOf(color)
            }

        private fun setupClickListeners(
            task: DailyTask,
            position: Int,
            currentDate: String
        ) = with(binding) {
            root.setOnClickListener {
                if (selectionMode) {
                    toggleSelection(task, position)
                } else {
                    listener.moveToEditListener(task)
                }
            }

            root.setOnLongClickListener {
                if (!selectionMode) selectionMode = true
                toggleSelection(task, position)
                true
            }

            doneContainer.setOnClickListener {
                Log.e(tag, "bind: $currentDate")
                if (task.completedDates.contains(currentDate)) {
                    task.completedDates = task.completedDates.toMutableList().apply {
                        remove(currentDate)
                    }
                } else {
                    task.completedDates = task.completedDates.toMutableList().apply {
                        add(currentDate)
                    }
                }
                listener.onTaskCompleteClick(task)
            }
        }
    }

    fun updateList(newContacts: List<DailyTask>, currentDatee: String) {
        currentDate = currentDatee
        numbersList = newContacts
        selectedItems.clear()
        selectionMode = false
        updateActiveScheduledTime()  // NEW: Compute active time
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedItems.clear()
        notifyDataSetChanged()
        listener.onItemSelectedCountChanged(0)
        selectionMode = false
    }

    fun selectAll() {
        selectedItems.clear()
        selectedItems.addAll(numbersList)
        notifyDataSetChanged()
        listener.onItemSelectedCountChanged(selectedItems.size)
        selectionMode = true
    }

    fun getSelectedItems(): MutableSet<DailyTask> = selectedItems
    private fun updateActiveScheduledTime() {
        val currentMinutes = getCurrentMinutes()
        Log.d(tag, "Current minutes: $currentMinutes")
        var maxPastMinutes = -1
        var maxTimeStr: String? = null

        numbersList.forEach { task ->
            if (task.isScheduled && task.scheduledTime != null) {
                val taskMinutes = timeStringToMinutes(task.scheduledTime)
                Log.d(tag, "Task ${task.title}: ${task.scheduledTime} -> $taskMinutes mins")
                if (taskMinutes <= currentMinutes && taskMinutes > maxPastMinutes) {
                    maxPastMinutes = taskMinutes
                    maxTimeStr = task.scheduledTime
                }
            }
        }

        activeScheduledTime = maxTimeStr
        Log.d(tag, "Active time set to: $activeScheduledTime")
    }

    private fun timeStringToMinutes(timeStr: String): Int {
        return try {
            val sdf = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
            val date = sdf.parse(timeStr) ?: return 0
            val calendar = Calendar.getInstance().apply { time = date }
            calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        } catch (e: Exception) {
            Log.e(tag, "Time parse error: $timeStr", e)
            0
        }
    }

    private fun getTimeComparison(scheduledTime: String): String {
        val currentMinutes = getCurrentMinutes()
        val scheduledMinutes = timeStringToMinutes(scheduledTime)
        return when {
            currentMinutes > scheduledMinutes -> "past"
            currentMinutes == scheduledMinutes -> "current"
            else -> "future"
        }
    }

    private fun getCurrentMinutes(): Int {
        val currentTimeStr = getCurrentTime()
        return timeStringToMinutes(currentTimeStr)
    }

    private fun getCurrentTime(): String {
        val sdf =
            SimpleDateFormat("hh:mm a", Locale.ENGLISH)  // 12hr with AM/PM, jaise "01:00 PM"
        return sdf.format(Date())
    }
}
