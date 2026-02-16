package com.anitech.growdaily.fragment

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.database.AppViewModel
import com.anitech.growdaily.databinding.FragmentAddTaskBinding
import com.anitech.growdaily.dialog.ImagePickerDialog
import com.anitech.growdaily.dialog.TaskListBottomSheet
import com.anitech.growdaily.dialog.TaskPriorityBottomSheet
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon
import com.anitech.growdaily.enum_class.TaskType
import com.anitech.growdaily.enum_class.TaskWeight
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
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
    private var selectedTaskWeight: TaskWeight = TaskWeight.VERY_LOW

    private val TAG = "AddTaskFragment"

    private val selectedListIds = mutableListOf<String>()
    private var listsLoadedForEdit = false

    private lateinit var originalStartDate: String
    private var selectedStartDate: String = ""
    private var startDateChangeConfirmed = false
    private var ignoreScheduleToggle = false
    private var scheduleTime: String? = null
    private var reminderTime: String? = null

    private var selectedType: TaskType = TaskType.DAILY

    private var selectedRepeatType: String = "EVERY_DAY"


    // FIXME: Task delete hone par list se bhi remove karna hai
    //fixme: reminder schedule kaa thik se karna pdega

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
        val title = if (argTask != null) {
            "Edit Task"
        } else {
            "Add Task"
        }

        (requireActivity() as AppCompatActivity)
            .supportActionBar
            ?.title = title
        selectedType = when {
            argTask != null -> argTask.taskType

            !args.taskType.isNullOrBlank() ->
                TaskType.valueOf(args.taskType!!)

            else -> TaskType.DAILY
        }
        // FIXME: work on setupUiByTaskType()  method is remain
        setupUiByTaskType(selectedType)
        if (argTask != null) {
            // Edit mode setup
            setTaskData(argTask)

            updatePriorityText()

            setupObservers(true, argTask)

        } else {
            val today = CommonMethods.getTodayDate()
            originalStartDate = today
            selectedStartDate = today
            setupObservers(false, null)
        }
        setClickListeners()
        binding.startDateLayout.txtStartDate.text = selectedStartDate

        fragmentResultListener()

    }


    private fun updatePriorityText() {
        binding.taskWeightPriorityLayout.txtPriority.text =
            "${selectedTaskWeight.weight}/4"
    }

    private fun showStartDateWarningDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Change start date?")
            .setMessage(
                "Changing the start date can remove previous completion records."
            )
            .setNegativeButton("No", null)
            .setPositiveButton("Yes") { _, _ ->
                startDateChangeConfirmed = true
                openStartDatePicker()
            }
            .show()
    }

    private fun openStartDatePicker() {
        val cal = Calendar.getInstance()

        val parts = selectedStartDate.split("-")
        if (parts.size == 3) {
            cal.set(
                parts[0].toInt(),
                parts[1].toInt() - 1,
                parts[2].toInt()
            )
        }

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                selectedStartDate =
                    String.format("%04d-%02d-%02d", y, m + 1, d)
                binding.startDateLayout.txtStartDate.text = selectedStartDate
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )

        datePickerDialog.setOnShowListener {
            val color = ContextCompat.getColor(
                requireContext(),
                R.color.brand_blue
            )

            datePickerDialog.getButton(DatePickerDialog.BUTTON_POSITIVE)
                ?.setTextColor(color)

            datePickerDialog.getButton(DatePickerDialog.BUTTON_NEGATIVE)
                ?.setTextColor(color)
        }

        datePickerDialog.setOnCancelListener {
            startDateChangeConfirmed = false
        }

        datePickerDialog.show()
    }

    private fun handleSaveTask() {
        val title = binding.titleNoteLayout.editTextTitle.text.toString().trim()
        val note = binding.titleNoteLayout.editTextNote.text.toString().trim()
        //  var scheduledTime: String? = binding.txtTime.text.toString().trim()
//        val reminderOn = binding.switchReminder.isChecked
        val todayDate = CommonMethods.Companion.getTodayDate()
        if (binding.scheduleReminderLayout.switchSchedule.isChecked && scheduleTime == null) {
            Toast.makeText(requireContext(), "Select schedule time", Toast.LENGTH_SHORT).show()
            return
        }

        if (binding.scheduleReminderLayout.switchReminder.isChecked && reminderTime == null) {
            Toast.makeText(requireContext(), "Select reminder time", Toast.LENGTH_SHORT).show()
            return
        }


        val (isValid, errorMsg) = validateTask(title)
        if (!isValid) {
            Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show()
            return
        }

        val task = TaskEntity(
            id = args.task?.id ?: UUID.randomUUID().toString(),
            title = title,
            note = note.ifEmpty { null },
            weight = selectedTaskWeight,
            scheduledTime = scheduleTime,
            reminderTime = reminderTime,
            reminderEnabled = binding.scheduleReminderLayout.switchReminder.isChecked,
            isScheduled = binding.scheduleReminderLayout.switchSchedule.isChecked,
            taskAddedDate = selectedStartDate,
            taskRemovedDate = args.task?.taskRemovedDate,
            iconResId = selectedDrawableResId,
            colorCode = selectedBackgroundColor,
            taskType = selectedType,
            repeatType = null,
            repeatDays = null,
            dailyTargetCount = 2
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

        if (args.task != null) {
            // EDIT MODE
            viewModel.allLists.value?.forEach { list ->
                viewModel.saveTasksForList(
                    listId = list.id,
                    taskIds = if (selectedListIds.contains(list.id))
                        listOf(task.id)
                    else
                        emptyList()
                )
            }
        } else {
            // NEW TASK
            selectedListIds.forEach { listId ->
                viewModel.saveTasksForList(
                    listId = listId,
                    taskIds = listOf(task.id)
                )
            }
        }

        if (args.task != null &&
            startDateChangeConfirmed &&
            selectedStartDate != originalStartDate
        ) {
            viewModel.deleteCompletionsBefore(
                taskId = args.task!!.id,
                newStartDate = selectedStartDate
            )
        }
    }

    private fun updateListText() {
        if (selectedListIds.isEmpty()) {
            binding.addToListLayout.txtListSummary.text = "None"
            return
        }

        val allLists = viewModel.allLists.value
        // If lists not loaded yet, show a temporary hint so user knows it's loading.
        if (allLists.isNullOrEmpty()) {
            binding.addToListLayout.txtListSummary.text = "Loading..."
            return
        }

        val firstList = allLists.firstOrNull { it.id == selectedListIds.first() }
        val extraCount = selectedListIds.size - 1

        binding.addToListLayout.txtListSummary.text =
            if (extraCount > 0)
                "${firstList?.listTitle ?: "List"} +$extraCount"
            else
                firstList?.listTitle ?: "None"
    }

    private fun validateTask(title: String): Pair<Boolean, String?> {
        return when {
            title.isEmpty() -> false to "Please enter a task title"
            else -> true to null
        }
    }


    private fun setupObservers(isEditMode: Boolean, argTask: TaskEntity?) {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saveResult.observe(viewLifecycleOwner) { result ->
                    when (result) {
                        is AppViewModel.SaveResult.Success -> {
                            binding.buttonSave.isEnabled = true
                            binding.progressBarSave.visibility = View.GONE
                            val message =
                                if (result.isNewTask) "Task saved & auto-ordered!" else "Task Updated"
                            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

                            findNavController().popBackStack()
                        }

                        is AppViewModel.SaveResult.Error -> {
                            binding.buttonSave.isEnabled = true
                            binding.progressBarSave.visibility = View.GONE
                            Toast.makeText(
                                requireContext(),
                                "Save failed: ${result.message}",
                                Toast.LENGTH_SHORT
                            ).show()
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

        viewModel.allLists.observe(viewLifecycleOwner) {
            updateListText()
        }

        if (isEditMode) {
            if (argTask != null) {
                viewModel.getListIdsForTask(argTask.id) { ids ->
                    selectedListIds.clear()
                    selectedListIds.addAll(ids)
                    listsLoadedForEdit = true
                    updateListText()
                }
            }

        }

    }

    private fun setupUiByTaskType(type: TaskType) {
        when (type) {
            TaskType.DAILY -> {
                // future:
                // daily specific rules
            }

            TaskType.DAY -> {
                // future:
                // only start date relevant
            }

            TaskType.UNTIL_COMPLETE -> {
                // future:
                // weight / priority conditional
            }
        }
    }


    private fun setTaskData(argTask: TaskEntity) {
        binding.titleNoteLayout.editTextTitle.setText(argTask.title)
        if (argTask.note != null) {
            binding.titleNoteLayout.editTextNote.setText(argTask.note)
        }

        scheduleTime = argTask.scheduledTime
        reminderTime = argTask.reminderTime

        binding.scheduleReminderLayout.switchSchedule.isChecked =
            argTask.isScheduled && scheduleTime != null

        binding.scheduleReminderLayout.txtScheduleTime.text =
            scheduleTime ?: "--"

        binding.scheduleReminderLayout.switchReminder.isChecked =
            argTask.reminderEnabled && reminderTime != null

        binding.scheduleReminderLayout.txtReminderTime.text =
            reminderTime ?: "--"
        binding.scheduleReminderLayout.checkSameAsSchedule.isChecked =
            scheduleTime != null && scheduleTime == reminderTime


        selectedTaskWeight = argTask.weight

        originalStartDate = argTask.taskAddedDate
        selectedStartDate = originalStartDate

        val icon = TaskIcon.valueOf(argTask.iconResId)
        val color = TaskColor.valueOf(argTask.colorCode)

        binding.imageProfile.setImageResource(icon.resId)
        binding.imageProfile.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), color.resId))

        selectedDrawableResId = argTask.iconResId
        selectedBackgroundColor = argTask.colorCode

    }

    private fun setClickListeners() {
        binding.scheduleReminderLayout.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            binding.scheduleReminderLayout.layoutReminder.visibility =
                if (isChecked) View.VISIBLE else View.GONE

            if (!isChecked) {
                reminderTime = null
                binding.scheduleReminderLayout.txtReminderTime.text = "--"
                binding.scheduleReminderLayout.checkSameAsSchedule.isChecked = false
                return@setOnCheckedChangeListener
            }

            if (binding.scheduleReminderLayout.switchSchedule.isChecked && scheduleTime != null) {
                binding.scheduleReminderLayout.checkSameAsSchedule.visibility = View.VISIBLE
                binding.scheduleReminderLayout.checkSameAsSchedule.isChecked = true

                reminderTime = scheduleTime
                binding.scheduleReminderLayout.txtReminderTime.text = scheduleTime
            } else {
                binding.scheduleReminderLayout.checkSameAsSchedule.visibility = View.GONE
                openTimePicker {
                    reminderTime = it
                    binding.scheduleReminderLayout.txtReminderTime.text = it
                }
            }
        }

        if (!binding.scheduleReminderLayout.switchSchedule.isChecked) {
            binding.scheduleReminderLayout.checkSameAsSchedule.visibility = View.GONE
        }//dont know why it is here


        binding.scheduleReminderLayout.switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreScheduleToggle) return@setOnCheckedChangeListener

            binding.scheduleReminderLayout.layoutScheduleTime.visibility =
                if (isChecked) View.VISIBLE else View.GONE

            if (!isChecked) {
                // turned off -> clear schedule
                scheduleTime = null
                binding.scheduleReminderLayout.txtScheduleTime.text = "--"
                binding.scheduleReminderLayout.checkSameAsSchedule.isChecked = false
                return@setOnCheckedChangeListener
            }

            // isChecked == true -> show choice dialog first
            val hasReminder =
                reminderTime != null && binding.scheduleReminderLayout.switchReminder.isChecked

            val options = mutableListOf<String>()
            options.add("Set time now")
            if (hasReminder) options.add("Use reminder time (${reminderTime})")
            options.add("Cancel")

            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Enable Schedule")
                .setItems(options.toTypedArray()) { dialog, which ->
                    val choice = options[which]
                    when (choice) {
                        "Set time now" -> {
                            openTimePicker { time ->
                                scheduleTime = time
                                binding.scheduleReminderLayout.txtScheduleTime.text = time
                                // if same-as-schedule checked, copy to reminder
                                if (binding.scheduleReminderLayout.checkSameAsSchedule.isChecked) {
                                    reminderTime = time
                                    binding.scheduleReminderLayout.txtReminderTime.text = time
                                }
                            }
                        }

                        else -> {
                            if (choice.startsWith("Use reminder") && hasReminder) {
                                scheduleTime = reminderTime
                                binding.scheduleReminderLayout.txtScheduleTime.text = scheduleTime
                            } else {
                                // Cancel chosen: revert switch back OFF safely
                                ignoreScheduleToggle = true
                                binding.scheduleReminderLayout.switchSchedule.isChecked = false
                                binding.scheduleReminderLayout.layoutScheduleTime.visibility =
                                    View.GONE
                                ignoreScheduleToggle = false
                            }
                        }
                    }
                }
                .setCancelable(true)
                .show()
        }

        binding.scheduleReminderLayout.checkSameAsSchedule.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                reminderTime = scheduleTime
                binding.scheduleReminderLayout.txtReminderTime.text = scheduleTime
                binding.scheduleReminderLayout.txtReminderTime.isEnabled = false
            } else {
                binding.scheduleReminderLayout.txtReminderTime.isEnabled = true
                openTimePicker {
                    reminderTime = it
                    binding.scheduleReminderLayout.txtReminderTime.text = it
                }
            }
        }

        binding.addToListLayout.addToListRow.setOnClickListener {
            if (args.task != null && !listsLoadedForEdit) {
                Toast.makeText(
                    requireContext(),
                    "Loading lists, please wait…",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            TaskListBottomSheet(
                preselectedIds = selectedListIds.toList() // IMPORTANT: copy
            ) { ids ->
                selectedListIds.clear()
                selectedListIds.addAll(ids)
                updateListText()
            }.show(parentFragmentManager, "TaskListBottomSheet")
        }

        binding.startDateLayout.startDateRow.setOnClickListener {
            if (args.task != null) {
                // EDIT MODE → show warning FIRST
                showStartDateWarningDialog()
            } else {
                // NEW TASK → direct picker
                openStartDatePicker()
            }
        }

        binding.taskWeightPriorityLayout.priorityContainer.setOnClickListener {

            TaskPriorityBottomSheet(
                selectedWeight = selectedTaskWeight
            ) { weight ->
                selectedTaskWeight = weight
                updatePriorityText()
            }.show(parentFragmentManager, "TaskPriorityBottomSheet")

        }

        binding.imageProfile.setOnClickListener {
            val dialog = ImagePickerDialog.newInstance(
                selectedIcon = selectedDrawableResId,
                selectedColor = selectedBackgroundColor
            )
            dialog.setOnImageSelectedListener { iconName, colorName ->
                selectedDrawableResId = iconName
                selectedBackgroundColor = colorName

                val selectedIcon = TaskIcon.valueOf(iconName)
                val selectedColor = TaskColor.valueOf(colorName)

                binding.imageProfile.setImageResource(selectedIcon.resId)
                binding.imageProfile.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), selectedColor.resId)
                )
            }
            dialog.show(parentFragmentManager, "ImagePickerDialog")
        }

        binding.buttonSave.setOnClickListener {
            handleSaveTask()
        }

        binding.repeatLayout.repeatRow.setOnClickListener {
            findNavController().navigate(R.id.repeatConfigFragment)
        }


    }


    private fun fragmentResultListener() {

        // ICON PICKER RESULT
        parentFragmentManager.setFragmentResultListener(
            "iconPickerResult",
            viewLifecycleOwner
        ) { _, bundle ->

            val iconName = bundle.getString("drawableResId")
            val colorName = bundle.getString("backgroundColor")

            val selectedIcon = iconName?.let {
                TaskIcon.entries.find { enum -> enum.name == it }
            } ?: TaskIcon.TROPHY

            val selectedColor = colorName?.let {
                TaskColor.entries.find { enum -> enum.name == it }
            } ?: TaskColor.DARK_BLUE

            binding.imageProfile.setImageResource(selectedIcon.resId)
            binding.imageProfile.backgroundTintList =
                ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), selectedColor.resId)
                )

            selectedDrawableResId = selectedIcon.name
            selectedBackgroundColor = selectedColor.name
        }

        // REPEAT RESULT
        parentFragmentManager.setFragmentResultListener(
            "repeatResult",
            viewLifecycleOwner
        ) { _, bundle ->

            val repeatType = bundle.getString("repeatType") ?: "EVERY_DAY"

            // abhi sirf UI update
            binding.repeatLayout.txtRepeatSummary.text = when (repeatType) {
                "EVERY_DAY" -> "Every day"
                "WEEKLY" -> "Weekly"
                "MONTHLY" -> "Monthly"
                else -> "Custom"
            }

            // future ke liye store kar lo
            selectedRepeatType = repeatType
        }
    }


    private fun openTimePicker(onSelected: (String) -> Unit) {
        val cal = Calendar.getInstance()

        val dialog = TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                val c = Calendar.getInstance()
                c.set(Calendar.HOUR_OF_DAY, hour)
                c.set(Calendar.MINUTE, minute)

                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                onSelected(sdf.format(c.time))
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            false
        )

        dialog.setOnCancelListener {
            // user ne time pick kiye bina cancel kiya
            if (scheduleTime == null &&
                binding.scheduleReminderLayout.switchSchedule.isChecked
            ) {
                ignoreScheduleToggle = true
                binding.scheduleReminderLayout.switchSchedule.isChecked = false
                binding.scheduleReminderLayout.layoutScheduleTime.visibility = View.GONE
                ignoreScheduleToggle = false
            }
        }
        dialog.setOnShowListener {
            val positive = dialog.getButton(TimePickerDialog.BUTTON_POSITIVE)
            val negative = dialog.getButton(TimePickerDialog.BUTTON_NEGATIVE)
            val color = ContextCompat.getColor(requireContext(), R.color.brand_blue)
            positive?.setTextColor(color)
            negative?.setTextColor(color)
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}