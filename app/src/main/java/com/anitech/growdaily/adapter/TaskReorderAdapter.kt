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
import com.anitech.growdaily.data_class.DailyTask
import com.anitech.growdaily.databinding.RvDailyTaskItemBinding
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon
import java.time.LocalTime
import java.time.format.DateTimeParseException
import java.time.format.DateTimeFormatter
import java.util.Collections

class TaskReorderAdapter(
    private var taskList: MutableList<DailyTask>,
    private val dragStartListener: (RecyclerView.ViewHolder) -> Unit,
    private val reorderCompleteListener: OnReorderCompleteListener? = null
) : RecyclerView.Adapter<TaskReorderAdapter.ViewHolder>() {

    // 👇 Removed unused OnItemClickListener

    private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

    interface OnReorderCompleteListener {
        fun onReorderComplete(orderedTaskIds: List<String>)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvDailyTaskItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val task = taskList[position]
        holder.bind(task)
    }

    override fun getItemCount(): Int = taskList.size

    inner class ViewHolder(
        private val binding: RvDailyTaskItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(task: DailyTask) {
            binding.taskTitle.text = task.title
            binding.taskNote.visibility = View.GONE

            val icon = TaskIcon.valueOf(task.iconResId)
            val color = TaskColor.valueOf(task.colorCode)

            binding.doneContainer.background = null
            binding.doneContainer.backgroundTintList = null
            binding.done.setImageResource(R.drawable.ic_drag_handle)
            binding.done.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(binding.root.context, color.resId))

            binding.imageProfile.setImageResource(icon.resId)
            binding.imageProfile.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(binding.root.context, color.resId))

            binding.taskWeight.text =
                binding.root.context.getString(R.string.task_weight_prefix, task.weight.weight)

            // 👇 Time visibility same, good
            if (task.scheduledTime != null) {
                binding.timeTxt.text = task.scheduledTime
                binding.timeTxt.visibility = View.VISIBLE
            } else {
                binding.timeTxt.visibility = View.GONE
            }

            binding.shContainer.visibility = View.GONE
            binding.doneContainer.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) dragStartListener(this)
                false
            }
        }
    }

    fun moveItem(from: Int, to: Int) {
        Collections.swap(taskList, from, to)
        notifyItemMoved(from, to)
    }

    /** 👇 Updated: Keep nulls at exact positions, fill gaps with sorted timed items */
    fun autoReorderByTime() {
        val nullPositions = mutableMapOf<Int, DailyTask>()
        val timed = mutableListOf<DailyTask>()

        // Collect current null positions and timed items
        taskList.forEachIndexed { index, item ->
            if (item.scheduledTime == null) {
                nullPositions[index] = item
            } else {
                timed.add(item)
            }
        }

        // Sort timed by ascending time – with error handling
        try {
            timed.sortBy { LocalTime.parse(it.scheduledTime!!, timeFormatter) }
        } catch (e: DateTimeParseException) {
            Log.e("AdapterDebug", "Time parse error in sort: ${e.message}", e)
            // Fallback: Sort by alphabetical time string if parse fails
            timed.sortBy { it.scheduledTime }
        }

        // Rebuild list: nulls stay fixed, timed fill the gaps in order
        val newItems = mutableListOf<DailyTask>()
        var timedIndex = 0
        for (i in 0 until taskList.size) {
            if (nullPositions.containsKey(i)) {
                newItems.add(nullPositions[i]!!)
            } else if (timedIndex < timed.size) {
                newItems.add(timed[timedIndex++])
            }
        }

        taskList.clear()
        taskList.addAll(newItems)
        notifyDataSetChanged()

        val orderedIds = taskList.map { it.id }
        Log.d("AdapterDebug", "Auto-reorder complete, ordered IDs: $orderedIds")
        reorderCompleteListener?.onReorderComplete(orderedIds)
    }

    fun updateList(newTaskData: List<DailyTask>) {
        Log.d("AdapterDebug", "updateList called with: ${newTaskData.size} tasks")
        taskList.clear()
        taskList.addAll(newTaskData)
        notifyDataSetChanged()
        Log.d("AdapterDebug", "Adapter updated, itemCount: ${taskList.size}")
    }
}