package com.anitech.scoremyday.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.anitech.scoremyday.R
import com.anitech.scoremyday.data_class.DailyTask
import com.anitech.scoremyday.enum_class.TaskColor
import com.anitech.scoremyday.enum_class.TaskIcon

class TaskForConditionAdapter(
    private var numbersList: List<DailyTask>,
    private val conditionId: Int,
    private val listener: OnItemClickListener

) : RecyclerView.Adapter<TaskForConditionAdapter.ViewHolder>() {
    var conditionTasks: List<DailyTask> = emptyList()

    interface OnItemClickListener {
        fun onTaskCompleteClick(task: DailyTask)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.rv_daily_task_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val task = numbersList[position]
        holder.bind(task, position, conditionId)
    }

    override fun getItemCount(): Int = numbersList.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val taskTitle: TextView = itemView.findViewById(R.id.taskTitle)
        private val taskNote: TextView = itemView.findViewById(R.id.taskNote)
        private val doneContainer: FrameLayout = itemView.findViewById(R.id.doneContainer)
        private val doneImg: ImageView = itemView.findViewById(R.id.done)
        private val imageProfile: ImageView = itemView.findViewById(R.id.imageProfile)
        private val taskWeight: TextView = itemView.findViewById(R.id.taskWeight)
        private val body: LinearLayout = itemView.findViewById(R.id.body)
        private val specificNoteBtn: ImageButton = itemView.findViewById(R.id.specificNoteBtn)


        fun bind(task: DailyTask, position: Int, conditionId: Int) {
            taskTitle.text = task.title
            taskNote.visibility = View.GONE
            specificNoteBtn.visibility = View.GONE

            val icon = TaskIcon.valueOf(task.iconResId)
            val color = TaskColor.valueOf(task.colorCode)

            imageProfile.setImageResource(icon.resId)
            imageProfile.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(itemView.context, color.resId))

            taskWeight.text =
                itemView.context.getString(R.string.task_weight_prefix, task.weight.weight)

            if (!conditionTasks.isEmpty() && conditionTasks.contains(task)) {
                doneImg.setImageResource(R.drawable.ic_check)
            } else {
                doneImg.setImageResource(0)
            }
            body.backgroundTintList =
                ColorStateList.valueOf(Color.WHITE)//don't think this one was required

            doneContainer.setOnClickListener {
                //have to work here
                if (task.conditionIds.contains(conditionId)) {
                    // Remove the current date if already marked complete
                    task.conditionIds = task.conditionIds.toMutableList().apply {
                        remove(conditionId)
                    }
                } else {
                    // Add the current date if not marked complete
                    task.conditionIds = task.conditionIds.toMutableList().apply {
                        add(conditionId)
                    }
                }
                // Notify listener
                listener.onTaskCompleteClick(task)//have to update task
            }
        }
    }

    fun updateList(newList: List<DailyTask>) {
        numbersList = newList
        //   notifyDataSetChanged()
    }

    fun updateConditionList(newConditionList: List<DailyTask>) {
        conditionTasks = newConditionList
        notifyDataSetChanged()
    }

}
