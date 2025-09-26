package com.anitech.scoremyday.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Log
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
import com.anitech.scoremyday.CommonMethods.Companion.getTodayDate
import com.anitech.scoremyday.CommonMethods.Companion.isFutureDate
import com.anitech.scoremyday.R
import com.anitech.scoremyday.data_class.DailyTask
import com.anitech.scoremyday.enum_class.TaskColor
import com.anitech.scoremyday.enum_class.TaskIcon

class TaskAdapter(
    private var numbersList: List<DailyTask>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<TaskAdapter.ViewHolder>() {
    private var currentDate: String = getTodayDate()
    private val selectedItems = mutableSetOf<DailyTask>()
    private var selectionMode = false

    val tag: String = "TaskAdapter"

    fun selectionCount(): Int {
        return selectedItems.size
    }

    interface OnItemClickListener {
        fun moveToEditListener(task: DailyTask)
        fun onItemSelectedCountChanged(count: Int)
        fun onTaskCompleteClick(task: DailyTask)
        fun onFutureDateChangeBg(isFuture: Boolean)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.rv_daily_task_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val task = numbersList[position]
        val isSelected = selectedItems.contains(task)
        holder.bind(task, isSelected, position, currentDate)
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


        fun bind(task: DailyTask, isSelected: Boolean, position: Int, currentDate: String) {
            taskTitle.text = task.title

            if (!task.note.isNullOrEmpty()) {
                taskNote.text = task.note
                taskNote.visibility = View.VISIBLE
            } else {
                taskNote.visibility = View.GONE
            }


            val icon = TaskIcon.valueOf(task.iconResId)
            val color = TaskColor.valueOf(task.colorCode)
            imageProfile.setImageResource(icon.resId)
            imageProfile.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(itemView.context, color.resId))

            taskWeight.text =
                itemView.context.getString(R.string.task_weight_prefix, task.weight.weight)

            if (isFutureDate(currentDate)&& selectionMode) {

                //itemView.isClickable = false
                itemView.isEnabled = false
                doneContainer.isEnabled = false

                listener.onFutureDateChangeBg(true)
                taskTitle.setTextColor(
                    ContextCompat.getColor(
                        itemView.context,
                        R.color.default_text_color
                    )
                )
                taskNote.setTextColor(
                    ContextCompat.getColor(
                        itemView.context,
                        android.R.color.darker_gray
                    )
                )

                body.backgroundTintList =
                    ContextCompat.getColorStateList(
                        itemView.context,
                        android.R.color.darker_gray
                    )

                doneImg.setImageResource(0)
                specificNoteBtn.visibility = View.GONE
            } else {
                itemView.isEnabled = true
                doneContainer.isEnabled = true
                listener.onFutureDateChangeBg(false)
                if (task.completedDates.contains(currentDate)) {
                    doneImg.setImageResource(R.drawable.ic_check)
                    specificNoteBtn.visibility = View.VISIBLE
                    taskTitle.setTextColor(
                        ContextCompat.getColor(
                            itemView.context,
                            R.color.default_text_color
                        )
                    )
                    taskNote.setTextColor(
                        ContextCompat.getColor(
                            itemView.context,
                            android.R.color.darker_gray
                        )
                    )


                } else {
                    doneImg.setImageResource(0)
                    specificNoteBtn.visibility = View.GONE
                    taskTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.black))
                    taskNote.setTextColor(
                        ContextCompat.getColor(
                            itemView.context,
                            R.color.default_text_color
                        )
                    )
                }

                if (isSelected) {
                    body.backgroundTintList =
                        ContextCompat.getColorStateList(
                            itemView.context,
                            android.R.color.darker_gray
                        )
                } else if (task.completedDates.contains(currentDate)) {
                    body.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(itemView.context, R.color.category_dark_blue_10)
                    )
                } else {
                    body.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                }

                itemView.setOnClickListener {
                    if (selectionMode) {
                        toggleSelection(task, position)
                    } else {
                        // Normally do nothing on click
                        listener.moveToEditListener(task)
                    }
                }

                specificNoteBtn.setOnClickListener {
                    listener.moveToEditListener(task)
                }

                itemView.setOnLongClickListener {
                    if (!selectionMode) {
                        selectionMode = true
                    }
                    toggleSelection(task, position)
                    true
                }

                doneContainer.setOnClickListener {
                    Log.e(tag, "bind: $currentDate")
                    if (task.completedDates.contains(currentDate)) {
                        // Remove the current date if already marked complete
                        task.completedDates = task.completedDates.toMutableList().apply {
                            remove(currentDate)
                        }
                    } else {
                        // Add the current date if not marked complete
                        task.completedDates = task.completedDates.toMutableList().apply {
                            add(currentDate)
                        }
                    }
                    // Notify listener
                    listener.onTaskCompleteClick(task)
                }
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

    //selection related
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

    fun getSelectedItems(): MutableSet<DailyTask> {
        return selectedItems
    }

}
