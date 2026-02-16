package com.anitech.growdaily.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.BuildConfig
import com.anitech.growdaily.CommonMethods.Companion.getTodayDate
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.databinding.RvDailyTaskItemBinding
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon
import com.anitech.growdaily.enum_class.TaskType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TaskAdapter(
    private var taskList: List<TaskEntity>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var currentDate: String = getTodayDate()
    private val selectedItems = mutableSetOf<TaskEntity>()
    var isFutureDate = false
    private var selectionMode = false

    private val tag: String = "TaskAdapter"
    private var activeScheduledTime: String? = null  // Latest past/current scheduled time
    private val ITEM_VIEW = 1
    private val FOOTER_VIEW = 2
    val sdf = SimpleDateFormat("hh:mm a", Locale.ENGLISH)

    // Cache for ColorStateList to improve performance
    private val colorStateListCache = mutableMapOf<Int, ColorStateList>()
    private var completionMapForDate: Map<String, Int> = emptyMap()


    interface OnItemClickListener {
        fun moveToEditListener(task: TaskEntity)
        fun onItemSelectedCountChanged(count: Int)
        fun onTaskCompleteClick(taskId: String, date: String)           // increment
        fun onTaskCompleteLongClick(taskId: String, date: String)       // decrement

    }

    override fun getItemViewType(position: Int): Int {
        return if (taskList.isNotEmpty() && position == taskList.size) FOOTER_VIEW else ITEM_VIEW
    }

    fun selectionCount(): Int = selectedItems.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == FOOTER_VIEW) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.rv_daily_task_footer, parent, false)
            ExtraViewHolder(view)
        } else {
            val binding = RvDailyTaskItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            ViewHolder(binding, onToggleSelection)
        }
    }

    override fun onBindViewHolder(holder:  RecyclerView.ViewHolder, position: Int) {
        if (holder is ExtraViewHolder) {
            val context = holder.itemView.context
            val colorInt = taskList.lastOrNull()
                ?.let { task ->
                    TaskColor.fromName(task.colorCode)?.toColorInt(context)
                }
                ?: ContextCompat.getColor(context, R.color.brand_blue)

            holder.shView.imageTintList = ColorStateList.valueOf(colorInt)
        }

        else{
            val task = taskList[position]
            val isSelected = selectedItems.contains(task)
            (holder as ViewHolder).bind(task, isSelected, position, currentDate, isFutureDate)
        }
    }

    override fun getItemCount(): Int = if (taskList.isEmpty()) 0 else taskList.size + 1

    inner class ViewHolder(
        private val binding: RvDailyTaskItemBinding,
        private val onToggleSelection: (TaskEntity, Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            task: TaskEntity,
            isSelected: Boolean,
            position: Int,
            currentDate: String,
            isFutureDate: Boolean
        ) = with(binding) {
            // Basic data setup
            setTaskData(task)
            setTaskNote(task)
            setTaskType(task)
            handleScheduledTime(task)

            // Accessibility
            root.contentDescription = "Task: ${task.title}, Type: ${task.taskType.name}"
            val color = TaskColor.valueOf(task.colorCode).toColorInt(root.context)

            // Disable interaction for future dates
            if (isFutureDate) {
                futureTaskWork()
                return
            }
            root.isEnabled = true
            doneContainer.isEnabled = !selectionMode // FIXME: this is not working

            updateCompletionState(task, currentDate,color)

            val isActive = task.isScheduled && task.scheduledTime != null &&
                    task.scheduledTime.trim().equals(activeScheduledTime?.trim(), ignoreCase = true)

            updateColors(task, isActive, isSelected, currentDate,color)
            setupClickListeners(task, position, currentDate)
        }

        private fun setTaskNote(task: TaskEntity) = with(binding) {
            if (!task.note.isNullOrEmpty()) {
                taskNote.text = task.note
                taskNote.visibility = View.VISIBLE
            } else {
                taskNote.visibility = View.GONE
            }
        }

        private fun setTaskType(task: TaskEntity) = with(binding) {
            when (task.taskType) {
                TaskType.DAY -> taskType.text = "Today"
                TaskType.DAILY -> taskType.text = "Daily"
                TaskType.UNTIL_COMPLETE -> taskType.text = "Until Done"
            }
        }

        private fun getShViewDrawableRes(timeComparison: String): Int {
            return when (timeComparison) {
                "future" -> R.drawable.sh_view2
                "past", "current" -> R.drawable.sh_view1  // Treat current as past for drawable
                else -> 0
            }
        }

        private fun setTaskData(task: TaskEntity) = with(binding) {
            taskTitle.text = task.title
            val icon = runCatching { TaskIcon.valueOf(task.iconResId) }.getOrDefault(TaskIcon.entries.first())
            imageProfile.setImageResource(icon.resId)
            taskWeight.text = root.context.getString(R.string.task_weight_prefix, task.weight.weight)
            if (task.taskType == TaskType.UNTIL_COMPLETE){
                taskWeight.visibility = View.GONE
            }else{
                taskWeight.visibility = View.VISIBLE
            }
        }

        private fun handleScheduledTime(task: TaskEntity) = with(binding) {
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

            taskTitle.setTextColor(ContextCompat.getColor(root.context, R.color.default_text_color))
            taskNote.setTextColor(ContextCompat.getColor(root.context, android.R.color.darker_gray))

            body.backgroundTintList = ContextCompat.getColorStateList(root.context, R.color.light_gray_25)
            done.setImageResource(0)
        }

        private fun updateCompletionState(task: TaskEntity, currentDate: String, color: Int) = with(binding) {
            val count = completionMapForDate[task.id] ?: 0
            val target = task.dailyTargetCount.coerceAtLeast(1)
            val isCompleted = count >= target

            // icon
            if (isCompleted) done.setImageResource(R.drawable.ic_check) else done.setImageResource(0)

            // doneCount text
            if (task.dailyTargetCount > 1) {
                doneCount.visibility = View.VISIBLE
                doneCount.text = "$count/${task.dailyTargetCount}"
                if(count >= target){
                    doneCount.visibility = View.GONE
                }
            } else {
                doneCount.visibility = View.GONE
            }

        }


        // TaskState for cleaner state management
        private fun getTaskState(
            task: TaskEntity,
            isSelected: Boolean,
            isActive: Boolean,
            currentDate: String
        ): TaskState = when {
            isSelected -> TaskState.SELECTED
            isActive -> TaskState.ACTIVE
            else -> TaskState.NORMAL
        }

        private fun getCachedColorStateList(color: Int): ColorStateList {
            return colorStateListCache.getOrPut(color) { ColorStateList.valueOf(color) }
        }

        private fun applyTheme(
            state: TaskState,
            color: Int,
            white: Int,
            currentDate: String,
            task: TaskEntity
        ) = with(binding) {
            val context = root.context
            val defaultTextColor = ContextCompat.getColor(context, R.color.default_text_color)
            val darkerGray = ContextCompat.getColor(context, android.R.color.darker_gray)
            val completedBgColor = ContextCompat.getColor(context, R.color.category_dark_blue_10)
            val black = ContextCompat.getColor(context, R.color.black)
            val count = completionMapForDate[task.id] ?: 0
            val target = task.dailyTargetCount.coerceAtLeast(1)
            val isCompleted = count >= target

            // Color tuples: (title, note, type, weight, iconBg, iconFilter, doneBg)
            val colors = when (state) {
                TaskState.ACTIVE ->
                    listOf(white, white, white, white, white, color, white, color)

                TaskState.SELECTED ->
                    listOf(white, white, white, white, white, Color.DKGRAY, white, Color.DKGRAY)

                TaskState.NORMAL ->
                    listOf(
                        black,
                        defaultTextColor,
                        color,
                        defaultTextColor,
                        color,
                        white,
                        color,
                        if (isCompleted) color else white
                    )
            }

            taskTitle.setTextColor(colors[0])
            taskNote.setTextColor(colors[1])
            taskType.setTextColor(colors[2])
            taskWeight.setTextColor(colors[3])
            imageProfile.backgroundTintList = getCachedColorStateList(colors[4])
            imageProfile.setColorFilter(colors[5])
            doneContainer.backgroundTintList = getCachedColorStateList(colors[6])
            done.backgroundTintList = getCachedColorStateList(colors[7])

            // Background
            body.backgroundTintList = when (state) {
                TaskState.ACTIVE -> getCachedColorStateList(color)
                TaskState.SELECTED -> ContextCompat.getColorStateList(context, android.R.color.darker_gray)
                TaskState.NORMAL -> getCachedColorStateList(white)
            }//   TaskState.COMPLETED -> ColorStateList.valueOf(completedBgColor)


            // shView update
            if (task.isScheduled && task.scheduledTime != null) {
                val timeComp = getTimeComparison(task.scheduledTime)
                shView.setImageResource(when (state) {
                    TaskState.ACTIVE -> R.drawable.sh_view3
                    else -> getShViewDrawableRes(timeComp)
                })
            }

            if (BuildConfig.DEBUG) {
                Log.d(tag, "Applied ${state::class.simpleName} theme to ${task.title}")
            }
        }

        private fun updateColors(
            task: TaskEntity,
            isActive: Boolean,
            isSelected: Boolean,
            currentDate: String,
            color: Int,
        ) = with(binding) {

            val white = Color.WHITE
            val state = getTaskState(task, isSelected, isActive, currentDate)
            applyTheme(state, color, white, currentDate, task)

            // Common tints
            shView.setColorFilter(color)
            shDivider.backgroundTintList = getCachedColorStateList(color)
        }

        private fun setupClickListeners(
            task: TaskEntity,
            position: Int,
            currentDate: String
        ) = with(binding) {
            root.setOnClickListener {
                if (selectionMode) {
                    onToggleSelection(task, position)
                } else {
                    listener.moveToEditListener(task)
                }
            }

            root.setOnLongClickListener {
                if (!selectionMode) selectionMode = true
                onToggleSelection(task, position)
                true
            }

            doneContainer.setOnClickListener { listener.onTaskCompleteClick(task.id, currentDate) }
            doneContainer.setOnLongClickListener { listener.onTaskCompleteLongClick(task.id, currentDate); true }


        }

        // Time comparison functions (moved here if needed, but kept in adapter for shared use)
        private fun getTimeComparison(scheduledTime: String): String {
            val currentMinutes = getCurrentMinutes()
            val scheduledMinutes = timeStringToMinutes(scheduledTime)
            return when {
                currentMinutes > scheduledMinutes -> "past"
                currentMinutes == scheduledMinutes -> "current"
                else -> "future"
            }
        }

        private fun timeStringToMinutes(timeStr: String): Int {
            return try {
                 val date = sdf.parse(timeStr) ?: return 0
                val calendar = Calendar.getInstance().apply { time = date }
                calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(tag, "Time parse error: $timeStr", e)
                0
            }
        }

        private fun getCurrentMinutes(): Int {
            val currentTimeStr = getCurrentTime()
            return timeStringToMinutes(currentTimeStr)
        }

        private fun getCurrentTime(): String {

            return sdf.format(Date())
        }
    }

    class ExtraViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        val shView: ImageView = itemView.findViewById(R.id.shView)
    }

    fun updateList(newContacts: List<TaskEntity>, currentDatee: String) {
        currentDate = currentDatee
        taskList = newContacts
        selectedItems.clear()
        selectionMode = false
        //updateActiveScheduledTime()
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
        selectedItems.addAll(taskList)
        notifyDataSetChanged()
        listener.onItemSelectedCountChanged(selectedItems.size)
        selectionMode = true
    }

    fun getSelectedItems(): MutableSet<TaskEntity> = selectedItems

    private fun updateActiveScheduledTime() {
        val currentMinutes = getCurrentMinutes()
        if (BuildConfig.DEBUG) Log.d(tag, "Current minutes: $currentMinutes")

        var maxPastMinutes = -1
        var maxTimeStr: String? = null

        taskList.forEach { task ->
            if (task.isScheduled && task.scheduledTime != null ) {
                val taskMinutes = timeStringToMinutes(task.scheduledTime)
                if (BuildConfig.DEBUG) Log.d(tag, "Task ${task.title}: ${task.scheduledTime} -> $taskMinutes mins")

                // only consider past or equal times
                if (taskMinutes <= currentMinutes && taskMinutes > maxPastMinutes) {
                    maxPastMinutes = taskMinutes
                    maxTimeStr = task.scheduledTime
                }
            }
        }

        // ✅ Only update if a past task actually exists
        activeScheduledTime = if (maxPastMinutes != -1) maxTimeStr else null

        if (BuildConfig.DEBUG) {
            Log.d(tag, "Active time set to: $activeScheduledTime (maxPastMinutes=$maxPastMinutes)")
        }
    }


    // Shared time utils (moved from adapter to avoid duplication if needed)
    private fun timeStringToMinutes(timeStr: String): Int {
        return try {
             val date = sdf.parse(timeStr) ?: return 0
            val calendar = Calendar.getInstance().apply { time = date }
            calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(tag, "Time parse error: $timeStr", e)
            0
        }
    }

    private fun getCurrentMinutes(): Int {
        val currentTimeStr = getCurrentTime()
        return timeStringToMinutes(currentTimeStr)
    }

    private fun getCurrentTime(): String {
         return sdf.format(Date())
    }

    sealed class TaskState {
        object SELECTED : TaskState()
        object ACTIVE : TaskState()
        object NORMAL : TaskState()
    }

    // Lambda for toggle selection to avoid tight coupling
    private val onToggleSelection: (TaskEntity, Int) -> Unit = { task, position ->
        if (selectedItems.contains(task)) {
            selectedItems.remove(task)
        } else {
            selectedItems.add(task)
        }
        notifyItemChanged(position)
        listener.onItemSelectedCountChanged(selectedItems.size)

        if (selectedItems.isEmpty()) {
            selectionMode = false
            // Avoid full notifyDataSetChanged(); just refresh if needed
            // For now, it's fine as selections are per-item
        }
    }


    fun updateCompletionForDate(map: Map<String, Int>) {
        val oldMap = completionMapForDate
        completionMapForDate = map

        // Only update items whose completion actually changed
        taskList.forEachIndexed { index, task ->
            val old = oldMap[task.id] ?: 0
            val new = map[task.id] ?: 0
            if (old != new) {
                notifyItemChanged(index)
            }
        }
    }



}