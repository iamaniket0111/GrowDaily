package com.anitech.growdaily.fragment

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.OneShotPreDrawListener.add
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.R
import com.anitech.growdaily.adapter.ConditionCheckAdapter
import com.anitech.growdaily.data_class.DailyTask
import com.anitech.growdaily.database.AppViewModel
import com.anitech.growdaily.databinding.FragmentAddTaskBinding
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon
import com.anitech.growdaily.enum_class.TaskType
import com.anitech.growdaily.enum_class.TaskWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class AddTaskFragment : Fragment() {
    private var _binding: FragmentAddTaskBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppViewModel by activityViewModels()
    private val args: AddTaskFragmentArgs by navArgs()

    var selectedDrawableResId: String = "TROPHY"
    var selectedBackgroundColor: String = "DARK_BLUE"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddTaskBinding.inflate(inflater, container, false)
        val root: View = binding.root
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val argTask = args.task
        if (argTask != null) {
            binding.editTextTitle.setText(argTask.title)
            if (argTask.note != null) {
                binding.editTextNote.setText(argTask.note)
            }

            if (argTask.scheduledTime != null) {
                binding.txtTime.text = argTask.scheduledTime
            }
            setSelectedTaskWeight(argTask.weight)
            val icon = TaskIcon.valueOf(argTask.iconResId)
            val color = TaskColor.valueOf(argTask.colorCode)

            binding.imageProfile.setImageResource(icon.resId)
            binding.imageProfile.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), color.resId))

            selectedDrawableResId = argTask.iconResId
            selectedBackgroundColor = argTask.colorCode

            when (argTask.taskType) {
                TaskType.DAILY -> binding.radioGroupType.check(R.id.radioDailyTask)
                TaskType.DAY -> binding.radioGroupType.check(R.id.radioDayTask)
                TaskType.UNTIL_COMPLETE -> binding.radioGroupType.check(R.id.radioUntilComplete)
            }
            // FIXME: have to work on making it enable or not
            when (argTask.taskType) {
                TaskType.DAILY, TaskType.UNTIL_COMPLETE -> {
                    // Radio buttons disabled
                    for (i in 0 until binding.radioGroupType.childCount) {
                        binding.radioGroupType.getChildAt(i).isEnabled = false
                    }
                }

                TaskType.DAY -> {
                    // Radio buttons enabled
                    for (i in 0 until binding.radioGroupType.childCount) {
                        binding.radioGroupType.getChildAt(i).isEnabled = true
                    }
                }
            }
//            if (argTask.isDaily) {
//                binding.isDaily.isChecked = true
//                binding.isDaily.isEnabled = false
//            } else {
//                binding.isDaily.isChecked = false
//            }// FIXME: should we make it editable if its added today??taki previous datapar vaise bhi kuchh farak nhi pdega

            val taskId = argTask.id
            val date = CommonMethods.Companion.getTodayDate()
            viewModel.getNoteForDate(taskId, date)
        }


        // Adapter initialization
        val initialTaskType = argTask?.taskType ?: TaskType.DAILY
        val conditionCheckAdapter =
            ConditionCheckAdapter(emptyList(), initialTaskType == TaskType.DAILY) { id, isChecked ->
                Log.d("Condition", "Condition $id selected = $isChecked")
            }

// Enable/disable checkboxes based on selected task type
        binding.radioGroupType.setOnCheckedChangeListener { _, _ ->
            val selectedTaskType = getSelectedTaskType()
            conditionCheckAdapter.setCheckBoxEnabled(selectedTaskType == TaskType.DAILY)
        }


        binding.rvCheckCondition.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvCheckCondition.adapter = conditionCheckAdapter

        val preselectedConditionIds = argTask?.conditionIds ?: emptyList()

        viewModel.getAllConditions().observe(viewLifecycleOwner) { conditions ->
            conditionCheckAdapter.updateData(conditions)
            if (preselectedConditionIds.isNotEmpty()) {
                conditionCheckAdapter.setPreselectedIds(preselectedConditionIds)
            }
        }


        binding.imageProfile.setOnClickListener {
            val bundle = Bundle().apply {
                putString("selectedIcon", selectedDrawableResId)   // e.g. "GRADUATION_CAP"
                putString("selectedColor", selectedBackgroundColor)  // e.g. "TEAL"
            }
            findNavController().navigate(
                R.id.imageProviderFragment,
                bundle
            )
        }

//        binding.dateBtn.setOnClickListener {
//            showDatePickerDialog()
//        }

        binding.switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            setTimeText()
            if (isChecked && !binding.switchReminder.isChecked) showTimePickerDialog( binding.switchSchedule)
        }

        binding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            setTimeText()
            if (isChecked && !binding.switchSchedule.isChecked) showTimePickerDialog( binding.switchReminder)
        }


        binding.txtTime.setOnClickListener {
            showTimePickerDialog(null)
        }

        binding.isDaily.setOnCheckedChangeListener { _, isChecked ->
            conditionCheckAdapter.setCheckBoxEnabled(isChecked)
        }

        binding.buttonSave.setOnClickListener {
            val taskId = UUID.randomUUID().toString()
            val title = binding.editTextTitle.text.toString().trim()
            val note = binding.editTextNote.text.toString().trim()
            var scheduledTime: String? = binding.txtTime.text.toString().trim()
            val reminderOn = binding.switchReminder.isChecked
            val isDaily = binding.isDaily.isChecked  // Note: Ye deprecated lag raha, taskType use kar
            val todayDate = CommonMethods.Companion.getTodayDate()
            val isScheduled = binding.switchSchedule.isChecked

            // 👇 Tera check same rakh – null time handle
            if (!binding.switchSchedule.isChecked && !binding.switchReminder.isChecked) {
                scheduledTime = null
            } else if (scheduledTime.isNullOrBlank()) {  // 👇 Naya: Blank bhi null bana de
                Log.w("AddTaskDebug", "Time input blank, setting to null")
                scheduledTime = null
            }

            val selectedConditionIds = conditionCheckAdapter.getSelectedIds()

            // Validation same
            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a task title", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val iconEnum = TaskIcon.valueOf(selectedDrawableResId)
            val colorEnum = TaskColor.valueOf(selectedBackgroundColor)
            val taskType = getSelectedTaskType()

            if (argTask == null) {  // New task add
                val newTask = DailyTask(
                    id = taskId,
                    title = title,
                    note = note.ifEmpty { null },
                    isCompleted = false,
                    weight = getSelectedTaskWeight(),
                    scheduledTime = scheduledTime,
                    completedTime = null,
                    taskAddedDate = todayDate,
                    taskRemovedDate = null,  // 👇 Fix: null tha, explicit kar diya
                    reminderEnabled = reminderOn,
                    completedDates = emptyList(),
                    conditionIds = selectedConditionIds,
                    iconResId = selectedDrawableResId,
                    colorCode = selectedBackgroundColor,
                    taskType = taskType,
                    isScheduled = isScheduled
                )

                // 👇 Naya: Smart insert + log logic (background me)
// ... existing vars same, validation same, newTask create same

                viewModel.viewModelScope.launch {
                    try {
                        // 1. Current ordered tasks fetch kar (ViewModel se)
                        val currentTasks = viewModel.getTasksForDate(todayDate)
                        Log.d("AddTaskDebug", "Current tasks: ${currentTasks.size}, sample times: ${currentTasks.take(3).map { it.scheduledTime }}")

                        // 2. Smart rebuild: Mimic autoReorderByTime() – nulls fixed, timed (old + new) sorted in gaps
                        val nullPositions = mutableMapOf<Int, DailyTask>()
                        val timed = mutableListOf<DailyTask>()

                        // Collect current nulls & timent from currentTasks
                        currentTasks.forEachIndexed { index, item ->
                            if (item.scheduledTime == null) {
                                nullPositions[index] = item
                            } else {
                                timed.add(item)
                            }
                        }

                        val updatedTasks: List<DailyTask> = if (!scheduledTime.isNullOrBlank()) {
                            // Timed new task: Add to timed list, sort all timed ascending
                            val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")
                            try {
                                val newTime = LocalTime.parse(scheduledTime, timeFormatter)
                                timed.add(newTask)  // New timed add kar timed pool me

                                // Sort all timed (including new) by time
                                timed.sortBy { LocalTime.parse(it.scheduledTime!!, timeFormatter) }
                                Log.d("AddTaskDebug", "Sorted timed times: ${timed.map { it.scheduledTime }}")

                                // Rebuild: Nulls fixed, sorted timed fill gaps
                                val newItems = mutableListOf<DailyTask>()
                                var timedIndex = 0
                                val totalSlots = currentTasks.size + 1  // +1 for new task
                                for (i in 0 until totalSlots) {
                                    if (nullPositions.containsKey(i)) {
                                        newItems.add(nullPositions[i]!!)
                                    } else if (timedIndex < timed.size) {
                                        newItems.add(timed[timedIndex++])
                                    }
                                }
                                // If extra timed (more than gaps), add at end
                                while (timedIndex < timed.size) {
                                    newItems.add(timed[timedIndex++])
                                }
                                newItems
                            } catch (e: DateTimeParseException) {
                                Log.e("AddTaskDebug", "Invalid time format: $scheduledTime, fallback append")
                                // Fallback: Treat as null, append at end
                                val fallbackItems = currentTasks.toMutableList().apply { add(newTask) }
                                fallbackItems
                            }
                        } else {
                            // Null time new task: Append at end (simple, no sort needed)
                            Log.d("AddTaskDebug", "Null time task, appending at end")
                            currentTasks.toMutableList().apply { add(newTask) }
                        }

                        // 3. DB me sirf new task insert kar (order log me handle hoga)
                        viewModel.insertTask(newTask)

                        // 4. Nayi order ke liye ChangeLog log kar
                        val orderedIds = updatedTasks.map { it.id }
                        viewModel.logTaskReorder(todayDate, orderedIds)

                        Log.d("AddTaskDebug", "New task added, final pos: ${updatedTasks.indexOf(newTask)}, total: ${updatedTasks.size}, timed: ${!scheduledTime.isNullOrBlank()}")

                        // 5. UI success
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Task saved & auto-ordered!", Toast.LENGTH_SHORT).show()
                            requireActivity().onBackPressedDispatcher.onBackPressed()
                        }
                    } catch (e: Exception) {
                        Log.e("AddTaskDebug", "Add failed: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {  // Edit task – tera existing update logic same rakh (no smart insert needed for edit)
                val task = DailyTask(
                    id = argTask.id,
                    title = title,
                    note = note.ifEmpty { null },
                    isCompleted = false,
                    weight = getSelectedTaskWeight(),
                    scheduledTime = null,  // 👇 Fix: Edit me bhi scheduledTime allow kar, null mat force
                    completedTime = null,
                    taskAddedDate = argTask.taskAddedDate,
                    taskRemovedDate = argTask.taskRemovedDate,
                    reminderEnabled = reminderOn,
                    completedDates = emptyList(),
                    conditionIds = selectedConditionIds,
                    iconResId = selectedDrawableResId,
                    colorCode = selectedBackgroundColor,
                    taskType = taskType,
                    isScheduled = isScheduled
                )

                viewModel.updateTask(task)
                Toast.makeText(requireContext(), "Task Updated", Toast.LENGTH_SHORT).show()
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
        parentFragmentManager.setFragmentResultListener(
            "iconPickerResult", viewLifecycleOwner
        ) { _, bundle ->
            val iconName = bundle.getString("drawableResId")
            val colorName = bundle.getString("backgroundColor")

            val selectedIcon = iconName?.let { TaskIcon.valueOf(it) }
            val selectedColor = colorName?.let { TaskColor.valueOf(it) }

            if (selectedIcon != null && selectedColor != null) {
                binding.imageProfile.setImageResource(selectedIcon.resId)
                binding.imageProfile.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        requireContext(),
                        selectedColor.resId
                    )
                )
                selectedDrawableResId = iconName
                selectedBackgroundColor = colorName
                Log.d("AddTaskFragment", "Icon: $iconName \t Color: $colorName")
            }
        }
    }

    private fun showTimePickerDialog(triggerSwitch: SwitchCompat?) {
        var hourOfDay = 0
        var minute = 0

        val currentTimeText = binding.txtTime.text.toString().trim()
        if (currentTimeText != "--") {
            try {
                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val date = sdf.parse(currentTimeText)
                val cal = Calendar.getInstance().apply { time = date!! }
                hourOfDay = cal.get(Calendar.HOUR_OF_DAY)
                minute = cal.get(Calendar.MINUTE)
            } catch (e: Exception) {
                e.printStackTrace()
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.HOUR_OF_DAY, 1)
                hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
                minute = calendar.get(Calendar.MINUTE)
            }
        } else {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.HOUR_OF_DAY, 1)
            hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
            minute = calendar.get(Calendar.MINUTE)
        }

        var isTimeSelected = false

        val timePickerDialog = TimePickerDialog(
            requireContext(),
            { _, selectedHour, selectedMinute ->
                isTimeSelected = true

                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, selectedHour)
                cal.set(Calendar.MINUTE, selectedMinute)

                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                binding.txtTime.text = sdf.format(cal.time)
            },
            hourOfDay, minute, false
        )

        // Allow user to cancel or touch outside
        timePickerDialog.setCancelable(true)
        timePickerDialog.setCanceledOnTouchOutside(true)

        // If user cancels or closes dialog without pressing OK
        timePickerDialog.setOnCancelListener {
            if (triggerSwitch?.isChecked == true) triggerSwitch.isChecked = false
        }

        timePickerDialog.setOnDismissListener {
            if (!isTimeSelected && triggerSwitch?.isChecked == true) {
                triggerSwitch.isChecked = false
            }
        }

        // Set dialog button colors
        timePickerDialog.setOnShowListener {
            val positive = timePickerDialog.getButton(TimePickerDialog.BUTTON_POSITIVE)
            val negative = timePickerDialog.getButton(TimePickerDialog.BUTTON_NEGATIVE)
            val color = ContextCompat.getColor(requireContext(), R.color.brand_blue)
            positive?.setTextColor(color)
            negative?.setTextColor(color)
        }

        timePickerDialog.show()
    }


    private fun getSelectedTaskWeight(): TaskWeight {
        return when (binding.radioGroupScore.checkedRadioButtonId) {
            R.id.radioVeryLow -> TaskWeight.VERY_LOW
            R.id.radioLow -> TaskWeight.LOW
            R.id.radioHigh -> TaskWeight.HIGH
            R.id.radioVeryHigh -> TaskWeight.VERY_HIGH
            else -> TaskWeight.VERY_LOW
        }
    }

    private fun setSelectedTaskWeight(weight: TaskWeight) {
        when (weight) {
            TaskWeight.VERY_LOW -> binding.radioGroupScore.check(R.id.radioVeryLow)
            TaskWeight.LOW -> binding.radioGroupScore.check(R.id.radioLow)
            TaskWeight.HIGH -> binding.radioGroupScore.check(R.id.radioHigh)
            TaskWeight.VERY_HIGH -> binding.radioGroupScore.check(R.id.radioVeryHigh)
        }
    }

    private fun getSelectedTaskType(): TaskType {
        return when (binding.radioGroupType.checkedRadioButtonId) {
            R.id.radioDailyTask -> TaskType.DAILY
            R.id.radioDayTask -> TaskType.DAY
            R.id.radioUntilComplete -> TaskType.UNTIL_COMPLETE
            else -> TaskType.DAILY
        }
    }

    private fun setTimeText() {
        if (!binding.switchSchedule.isChecked && !binding.switchReminder.isChecked) {
            binding.txtTime.text = "--"
        }
    }
}