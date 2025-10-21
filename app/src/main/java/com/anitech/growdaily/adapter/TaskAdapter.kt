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

class TaskAdapter(
    private var numbersList: List<DailyTask>,
    private val listener: OnItemClickListener,
) : RecyclerView.Adapter<TaskAdapter.ViewHolder>() {

    private var currentDate: String = getTodayDate()
    private val selectedItems = mutableSetOf<DailyTask>()
    var isFutureDate = false
    private var selectionMode = false

    private val tag: String = "TaskAdapter"

    interface OnItemClickListener {
        fun moveToEditListener(task: DailyTask)
        fun onItemSelectedCountChanged(count: Int)
        fun onTaskCompleteClick(task: DailyTask)
    }

    fun selectionCount(): Int = selectedItems.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvDailyTaskItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
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

            when (task.taskType) {
                TaskType.DAY -> taskType.text= "Today"
                TaskType.DAILY -> taskType.text= "Daily"
                TaskType.UNTIL_COMPLETE -> taskType.text= "Until Done"
            }

            taskTitle.text = task.title

            // Note visibility
            if (!task.note.isNullOrEmpty()) {
                taskNote.text = task.note
                taskNote.visibility = View.VISIBLE
            } else {
                taskNote.visibility = View.GONE
            }

            // Set icon and color
            val icon = TaskIcon.valueOf(task.iconResId)
            val color = TaskColor.valueOf(task.colorCode)
            imageProfile.setImageResource(icon.resId)
            imageProfile.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(root.context, color.resId))

            taskWeight.text =
                root.context.getString(R.string.task_weight_prefix, task.weight.weight)

            // Disable interaction for future dates
            if (isFutureDate) {
                root.isEnabled = false
                doneContainer.isEnabled = false

                taskTitle.setTextColor(
                    ContextCompat.getColor(root.context, R.color.default_text_color)
                )
                taskNote.setTextColor(
                    ContextCompat.getColor(root.context, android.R.color.darker_gray)
                )

                body.backgroundTintList = ContextCompat.getColorStateList(
                    root.context,
                    R.color.light_gray_25
                )
                done.setImageResource(0)
                return
            }

            root.isEnabled = true
            doneContainer.isEnabled = true

            // Completed or not
            if (task.completedDates.contains(currentDate)) {
                done.setImageResource(R.drawable.ic_check)
                taskTitle.setTextColor(
                    ContextCompat.getColor(root.context, R.color.default_text_color)
                )
                taskNote.setTextColor(
                    ContextCompat.getColor(root.context, android.R.color.darker_gray)
                )
            } else {
                done.setImageResource(0)
                taskTitle.setTextColor(ContextCompat.getColor(root.context, R.color.black))
                taskNote.setTextColor(
                    ContextCompat.getColor(root.context, R.color.default_text_color)
                )
            }

            // Background logic for selection/completion
            when {
                isSelected -> body.backgroundTintList =
                    ContextCompat.getColorStateList(root.context, android.R.color.darker_gray)

                task.completedDates.contains(currentDate) -> body.backgroundTintList =
                    ColorStateList.valueOf(
                        ContextCompat.getColor(
                            root.context,
                            R.color.category_dark_blue_10
                        )
                    )

                else -> body.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            }

            // Click listeners
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

    fun updateList(newContacts: List<DailyTask>, currentDatee: String) {
        currentDate = currentDatee
        numbersList = newContacts
        selectedItems.clear()
        selectionMode = false
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
}
