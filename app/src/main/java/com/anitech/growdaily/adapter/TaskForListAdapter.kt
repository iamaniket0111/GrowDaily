package com.anitech.growdaily.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.TaskEntity

class TaskForListAdapter(
    private var allTasks: List<TaskEntity>,
    private val selectedTaskIds: MutableSet<String>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<TaskForListAdapter.ViewHolder>() {

    interface OnItemClickListener {
        fun onTaskSelected(taskId: String)
        fun onTaskUnSelected(taskId: String)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.rv_simple_task_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(allTasks[position])
    }

    override fun getItemCount(): Int = allTasks.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val doneContainer: FrameLayout = itemView.findViewById(R.id.doneContainer)
        private val doneImg: ImageView = itemView.findViewById(R.id.done)
        private val taskTitle: TextView = itemView.findViewById(R.id.taskTitle)

        fun bind(task: TaskEntity) {
            taskTitle.text = task.title

            updateCheck(task.id)

            doneContainer.setOnClickListener {
                if (selectedTaskIds.contains(task.id)) {
                    selectedTaskIds.remove(task.id)
                    listener.onTaskUnSelected(task.id)
                } else {
                    selectedTaskIds.add(task.id)
                    listener.onTaskSelected(task.id)
                }
                updateCheck(task.id)
            }
        }

        private fun updateCheck(taskId: String) {
            if (selectedTaskIds.contains(taskId)) {
                doneImg.setImageResource(R.drawable.ic_check)
            } else {
                doneImg.setImageResource(0)
            }
        }
    }

    fun submitList(list: List<TaskEntity>) {
        allTasks = list
        notifyDataSetChanged()
        Log.e("ListSize:",allTasks.size.toString())
    }
}