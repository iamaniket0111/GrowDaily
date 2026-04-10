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
import androidx.core.content.ContextCompat
import com.anitech.growdaily.setSolidBackgroundColorCompat
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon

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

        private val body: View = itemView.findViewById(R.id.body)
        private val imageProfile: ImageView = itemView.findViewById(R.id.imageProfile)
        private val taskType: TextView = itemView.findViewById(R.id.taskType)
        private val taskTitle: TextView = itemView.findViewById(R.id.taskTitle)
        private val doneContainer: FrameLayout = itemView.findViewById(R.id.doneContainer)
        private val doneImg: ImageView = itemView.findViewById(R.id.done)

        fun bind(task: TaskEntity) {
            val color = runCatching { TaskColor.valueOf(task.colorCode) }
                .getOrDefault(TaskColor.BLUE)
                .toColorInt(itemView.context)
            val white = Color.WHITE

            val icon = runCatching { TaskIcon.valueOf(task.iconResId) }
                .getOrDefault(TaskIcon.entries.first())
            imageProfile.setImageResource(icon.resId)
            imageProfile.setSolidBackgroundColorCompat(color)
            imageProfile.setColorFilter(white)

            taskType.text = itemView.context.getString(task.taskType.labelRes)
            taskType.setTextColor(color)
            taskTitle.text = task.title

            applySelectionState(task.id, color, white)

            val toggleSelection = {
                if (selectedTaskIds.contains(task.id)) {
                    selectedTaskIds.remove(task.id)
                    listener.onTaskUnSelected(task.id)
                } else {
                    selectedTaskIds.add(task.id)
                    listener.onTaskSelected(task.id)
                }
                applySelectionState(task.id, color, white)
            }

            body.setOnClickListener { toggleSelection() }
            doneContainer.setOnClickListener { toggleSelection() }
        }

        private fun applySelectionState(taskId: String, color: Int, white: Int) {
            val isSelected = selectedTaskIds.contains(taskId)
            val cardSurface = ContextCompat.getColor(itemView.context, R.color.task_card_surface)
            val mutedSurface = ContextCompat.getColor(itemView.context, R.color.task_done_track)
            doneContainer.backgroundTintList = ColorStateList.valueOf(if (isSelected) color else mutedSurface)

            if (isSelected) {
                doneImg.visibility = View.VISIBLE
                doneImg.setImageResource(R.drawable.ic_check)
                doneImg.setSolidBackgroundColorCompat(color)
                doneImg.setColorFilter(white)
                body.alpha = 1f
            } else {
                doneImg.visibility = View.VISIBLE
                doneImg.setImageResource(0)
                doneImg.setSolidBackgroundColorCompat(cardSurface)
                doneImg.clearColorFilter()
                body.alpha = 0.96f
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
