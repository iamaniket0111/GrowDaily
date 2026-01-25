package com.anitech.growdaily.fragment

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
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

class AddTaskFragment : Fragment() {
    private var _binding: FragmentAddTaskBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppViewModel by activityViewModels()
    private val args: AddTaskFragmentArgs by navArgs()

    var selectedDrawableResId: String = "TROPHY"
    var selectedBackgroundColor: String = "DARK_BLUE"
    private val TAG = "AddTaskFragment"

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
            // Edit mode setup
            binding.editTextTitle.setText(argTask.title)
            if (argTask.note != null) {
                binding.editTextNote.setText(argTask.note)
            }

            if (argTask.scheduledTime != null) {
                binding.txtTime.text = argTask.scheduledTime
                binding.switchSchedule.isChecked = true
                if (argTask.reminderEnabled) binding.switchReminder.isChecked = true
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

            // Proper radio enable/disable for edit mode
            when (argTask.taskType) {
                TaskType.DAILY, TaskType.UNTIL_COMPLETE -> {
                    binding.radioDailyTask.isEnabled = false
                    binding.radioDayTask.isEnabled = false
                    binding.radioUntilComplete.isEnabled = false
                }
                TaskType.DAY -> {
                    binding.radioDailyTask.isEnabled = true
                    binding.radioDayTask.isEnabled = true
                    binding.radioUntilComplete.isEnabled = true
                }
            }

            val taskId = argTask.id
            val date = CommonMethods.Companion.getTodayDate()
            viewModel.getNoteForDate(taskId, date)
        }

        // Adapter initialization
        val initialTaskType = argTask?.taskType ?: TaskType.DAILY
        val conditionCheckAdapter =
            ConditionCheckAdapter(emptyList(), initialTaskType == TaskType.DAILY) { id, isChecked ->
                Log.d(TAG, "Condition $id selected = $isChecked")
            }

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
                putString("selectedIcon", selectedDrawableResId)
                putString("selectedColor", selectedBackgroundColor)
            }
            findNavController().navigate(R.id.imageProviderFragment, bundle)
        }

        binding.switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            setTimeText()
            if (isChecked && !binding.switchReminder.isChecked) showTimePickerDialog(binding.switchSchedule)
        }

        binding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            setTimeText()
            if (isChecked && !binding.switchSchedule.isChecked) showTimePickerDialog(binding.switchReminder)
        }

        binding.txtTime.setOnClickListener {
            showTimePickerDialog(null)
        }

        binding.buttonSave.setOnClickListener {
            handleSaveTask()
        }

        parentFragmentManager.setFragmentResultListener(
            "iconPickerResult",
            viewLifecycleOwner
        ) { _, bundle ->
            val iconName = bundle.getString("drawableResId")
            val colorName = bundle.getString("backgroundColor")

            val selectedIcon = iconName?.let { TaskIcon.entries.find { enum -> enum.name == it } ?: TaskIcon.TROPHY }
            val selectedColor = colorName?.let { TaskColor.entries.find { enum -> enum.name == it } ?: TaskColor.DARK_BLUE }

            selectedIcon?.let {
                binding.imageProfile.setImageResource(it.resId)
                selectedDrawableResId = iconName
            }
            selectedColor?.let {
                binding.imageProfile.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), it.resId)
                )
                selectedBackgroundColor = colorName
            }
            Log.d(TAG, "Icon: $iconName \t Color: $colorName")
        }

        observeViewModel()
    }

    private fun handleSaveTask() {
        val title = binding.editTextTitle.text.toString().trim()
        val note = binding.editTextNote.text.toString().trim()
        var scheduledTime: String? = binding.txtTime.text.toString().trim()
        val reminderOn = binding.switchReminder.isChecked
        val todayDate = CommonMethods.Companion.getTodayDate()
        val isScheduled = binding.switchSchedule.isChecked

        if (!binding.switchSchedule.isChecked && !binding.switchReminder.isChecked) {
            scheduledTime = null
        } else if (scheduledTime.isNullOrBlank()) {
            Log.w(TAG, "Time input blank, setting to null")
            scheduledTime = null
        }

        val selectedConditionIds =
            (binding.rvCheckCondition.adapter as? ConditionCheckAdapter)?.getSelectedIds() ?: emptyList()

        val (isValid, errorMsg) = validateTask(title, scheduledTime)
        if (!isValid) {
            Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show()
            return
        }
        val taskType = getSelectedTaskType()

        val task = DailyTask(
            id = args.task?.id ?: UUID.randomUUID().toString(),
            title = title,
            note = note.ifEmpty { null },
            isCompleted = args.task?.isCompleted ?: false,
            weight = getSelectedTaskWeight(),
            scheduledTime = scheduledTime,
            completedTime = args.task?.completedTime,
            taskAddedDate = args.task?.taskAddedDate ?: todayDate,
            taskRemovedDate = args.task?.taskRemovedDate,
            reminderEnabled = reminderOn,
            completedDates = args.task?.completedDates ?: emptyList(),
            conditionIds = selectedConditionIds,
            iconResId = selectedDrawableResId,
            colorCode = selectedBackgroundColor,
            taskType = taskType,
            isScheduled = isScheduled
        )

        binding.buttonSave.isEnabled = false
        binding.progressBarSave.visibility = View.VISIBLE

        // 👈 Safe reset call (public fun in ViewModel)
        viewModel.resetSaveResult()

        viewModel.saveOrUpdateTask(
            task = task,
            date = todayDate,
            isEdit = args.task != null,
            originalScheduledTime = args.task?.scheduledTime
        )
    }

    private fun validateTask(title: String, scheduledTime: String?): Pair<Boolean, String?> {
        return when {
            title.isEmpty() -> false to "Please enter a task title"
            scheduledTime.isNullOrBlank() && (binding.switchSchedule.isChecked || binding.switchReminder.isChecked) -> false to "Please select a time"
            else -> true to null
        }
    }

    private fun showTimePickerDialog(triggerSwitch: SwitchCompat?) {
        var hourOfDay: Int
        var minute: Int

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

        timePickerDialog.setCancelable(true)
        timePickerDialog.setCanceledOnTouchOutside(true)

        timePickerDialog.setOnCancelListener {
            if (triggerSwitch?.isChecked == true) triggerSwitch.isChecked = false
        }

        timePickerDialog.setOnDismissListener {
            if (!isTimeSelected && triggerSwitch?.isChecked == true) {
                triggerSwitch.isChecked = false
            }
        }

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

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saveResult.observe(viewLifecycleOwner) { result ->
                    when (result) {
                        is AppViewModel.SaveResult.Success -> {
                            binding.buttonSave.isEnabled = true
                            binding.progressBarSave.visibility = View.GONE
                            val message = if (result.isNewTask) "Task saved & auto-ordered!" else "Task Updated"
                            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

                            findNavController().popBackStack()
                        }
                        is AppViewModel.SaveResult.Error -> {
                            binding.buttonSave.isEnabled = true
                            binding.progressBarSave.visibility = View.GONE
                            Toast.makeText(requireContext(), "Save failed: ${result.message}", Toast.LENGTH_SHORT).show()
                        }
                        is AppViewModel.SaveResult.Loading -> {
                            // Handled in handleSaveTask
                        }
                        AppViewModel.SaveResult.Idle -> {
                            // Ignore
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}