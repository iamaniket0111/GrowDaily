package com.anitech.growdaily.adapter

import android.content.res.ColorStateList
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.databinding.RvTaskItemBinding
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon
import java.time.LocalTime
import java.time.format.DateTimeParseException
import java.time.format.DateTimeFormatter
import java.util.Collections

class TaskReorderAdapter(
    private var taskList: MutableList<TaskEntity>,
    private val dragStartListener: (RecyclerView.ViewHolder) -> Unit,
    private val reorderCompleteListener: OnReorderCompleteListener? = null
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

        fun bind(task: TaskEntity) {

            binding.taskTitle.text = task.title
            binding.taskNote.visibility = View.GONE

            val icon = TaskIcon.valueOf(task.iconResId)
            val color = TaskColor.valueOf(task.colorCode)

            binding.imageProfile.setImageResource(icon.resId)
            binding.imageProfile.backgroundTintList =
                ColorStateList.valueOf(
                    ContextCompat.getColor(binding.root.context, color.resId)
                )

            binding.taskWeight.text =
                binding.root.context.getString(
                    R.string.task_weight_prefix,
                    task.weight.weight
                )

            // 🔥 IMPORTANT CHANGE
            if (task.isScheduled) {
                binding.doneContainer.visibility = View.GONE
            } else {
                binding.doneContainer.visibility = View.VISIBLE
                binding.done.setImageResource(R.drawable.ic_drag_handle)
                binding.done.imageTintList =
                    ColorStateList.valueOf(
                        ContextCompat.getColor(binding.root.context, color.resId)
                    )

                binding.doneContainer.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        dragStartListener(this)
                    }
                    false
                }
            }

            if (task.scheduledTime != null) {
                binding.timeTxt.text = task.scheduledTime
                binding.timeTxt.visibility = View.VISIBLE
            } else {
                binding.timeTxt.visibility = View.GONE
            }

            binding.shContainer.visibility = View.GONE
        }
    }

    fun moveItem(from: Int, to: Int) {
        Collections.swap(taskList, from, to)
        notifyItemMoved(from, to)
    }

    fun notifyReorderFinished() {
        val orderedIds = taskList.map { it.id }
        reorderCompleteListener?.onReorderComplete(orderedIds)
    }

    fun updateList(newTasks: List<TaskEntity>) {
        taskList.clear()
        taskList.addAll(newTasks)
        notifyDataSetChanged()
    }
}

