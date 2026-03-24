package com.anitech.growdaily.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon
import com.anitech.growdaily.enum_class.TaskType

class TaskForListAdapter(
    allTasks: List<TaskEntity>,
    private val selectedTaskIds: MutableSet<String>,
    private val listener: OnItemClickListener
) : ListAdapter<TaskEntity, TaskForListAdapter.ViewHolder>(TaskForListDiffCallback()) {

    interface OnItemClickListener {
        fun onTaskSelected(taskId: String)
        fun onTaskUnSelected(taskId: String)
    }

    init {
        // seed initial list passed from the fragment
        submitList(allTasks.toList())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.rv_simple_task_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

         private val imageProfile: ImageView = itemView.findViewById(R.id.imageProfile)
        private val taskType: TextView = itemView.findViewById(R.id.taskType)
        private val taskTitle: TextView = itemView.findViewById(R.id.taskTitle)
        private val doneContainer: FrameLayout = itemView.findViewById(R.id.doneContainer)
        private val doneImg: ImageView = itemView.findViewById(R.id.done)

        fun bind(task: TaskEntity) {
            val color = TaskColor.valueOf(task.colorCode).toColorInt(itemView.context)
            val white = Color.WHITE

            // Icon
            val icon = runCatching { TaskIcon.valueOf(task.iconResId) }
                .getOrDefault(TaskIcon.entries.first())
            imageProfile.setImageResource(icon.resId)
            imageProfile.backgroundTintList = ColorStateList.valueOf(color)
            imageProfile.setColorFilter(white)

            // Type label
            taskType.text = itemView.context.getString(task.taskType.labelRes)
            taskType.setTextColor(color)

            // Title
            taskTitle.text = task.title

            // Selection state
            applySelectionState(task.id, color, white)

            // Click — toggle selection
            doneContainer.setOnClickListener {
                if (selectedTaskIds.contains(task.id)) {
                    selectedTaskIds.remove(task.id)
                    listener.onTaskUnSelected(task.id)
                } else {
                    selectedTaskIds.add(task.id)
                    listener.onTaskSelected(task.id)
                }
                applySelectionState(task.id, color, white)
            }
        }

        private fun applySelectionState(taskId: String, color: Int, white: Int) {
            val isSelected = selectedTaskIds.contains(taskId)

            // doneContainer background uses task color always
            doneContainer.backgroundTintList = ColorStateList.valueOf(color)

            if (isSelected) {
                // Show check; inner circle uses task color so check is white-on-color
                doneImg.visibility = View.VISIBLE
                doneImg.setImageResource(R.drawable.ic_check)
                doneImg.backgroundTintList = ColorStateList.valueOf(color)
                doneImg.setColorFilter(white)
              } else {
                // Hide check; inner circle is white
                doneImg.visibility = View.VISIBLE
                doneImg.setImageResource(0)          // clear icon
                doneImg.backgroundTintList = ColorStateList.valueOf(white)
                doneImg.clearColorFilter()
              }
        }

    }
}

class TaskForListDiffCallback : DiffUtil.ItemCallback<TaskEntity>() {
    override fun areItemsTheSame(oldItem: TaskEntity, newItem: TaskEntity) =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: TaskEntity, newItem: TaskEntity) =
        oldItem == newItem
}