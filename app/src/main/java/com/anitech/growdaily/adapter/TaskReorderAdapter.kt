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
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon
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

            val icon = TaskIcon.valueOf(task.iconResId)
            val color = TaskColor.valueOf(task.colorCode)
            val colorInt = ContextCompat.getColor(binding.root.context, color.resId)

            binding.body.imageProfile.setImageResource(icon.resId)
            binding.body.imageProfile.setSolidBackgroundColorCompat(colorInt)

            binding.body.weightContainer.visibility = View.GONE
            binding.body.streakContainer.visibility = View.GONE

            binding.body.taskType.setTextColor(colorInt)
            binding.body.taskType.setTextAppearance(R.style.ReorderItemType)
            binding.body.taskType.text = itemView.context.getString(task.taskType.labelRes)

            binding.body.done.background = null
            binding.body.doneView.visibility = View.GONE
            
            // Show drag handle for all tasks in the reorder screen
            binding.body.doneContainer.visibility = View.VISIBLE
            binding.body.done.setImageResource(R.drawable.ic_drag_handle)
            binding.body.done.imageTintList = ColorStateList.valueOf(colorInt)

            binding.body.doneContainer.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    dragStartListener(this)
                }
                false
            }

            if (task.scheduledTime != null) {
                binding.timeTxt.text = task.scheduledTime
                binding.timeTxt.visibility = View.VISIBLE
            } else {
                binding.timeTxt.visibility = View.GONE
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
