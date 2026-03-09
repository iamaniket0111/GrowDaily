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
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.MyApp
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.AddTaskUiState
import com.anitech.growdaily.database.AddTaskViewModel
import com.anitech.growdaily.database.AddTaskViewModelFactory
import com.anitech.growdaily.databinding.FragmentAddTaskBinding
import com.anitech.growdaily.dialog.DeleteTaskDialog
import com.anitech.growdaily.dialog.ImagePickerDialog
import com.anitech.growdaily.dialog.TaskListBottomSheet
import com.anitech.growdaily.dialog.TaskPriorityBottomSheet
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon
import com.anitech.growdaily.enum_class.TaskType
import com.anitech.growdaily.enum_class.TaskWeight
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddTaskFragment : Fragment() {
    private var _binding: FragmentAddTaskBinding? = null
    private val binding get() = _binding!!

    private val args: AddTaskFragmentArgs by navArgs()

    private val viewModel: AddTaskViewModel by viewModels {
        AddTaskViewModelFactory(
            (requireActivity().application as MyApp).repository
        )
    }

    private var selectedType: TaskType = TaskType.DAILY
    private var selectedRepeatType: String = "EVERY_DAY"
    private var originalStartDate: String = ""
    private var startDateChangeConfirmed = false
    private var ignoreScheduleToggle = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupActionBar()
        setupTaskType()
        setupObservers()
        setupClickListeners()
        setupTextListeners()
        loadTaskDataIfEditing()

        binding.deletePauseLayout.deletePauseContainer.visibility =
            if (args.task != null) View.VISIBLE else View.GONE

    }

    private fun setupActionBar() {
        val title = if (args.task != null) "Edit Task" else "Add Task"
        (requireActivity() as AppCompatActivity).supportActionBar?.title = title
    }

    private fun setupTaskType() {
        selectedType = when {
            args.task != null -> args.task!!.taskType
            !args.taskType.isNullOrBlank() -> TaskType.valueOf(args.taskType!!)
            else -> TaskType.DAILY
        }
        // Setup UI based on task type if needed
    }

    private fun setupObservers() {
        // Observe UI state
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            updateUIFromState(state)
        }

        // Observe lists for updating list summary
        viewModel.allLists.observe(viewLifecycleOwner) {
            updateListSummary()
        }

        // Observe selected list IDs
        viewModel.selectedListIds.observe(viewLifecycleOwner) {
            updateListSummary()
        }
    }

    private fun updateUIFromState(state: AddTaskUiState) {
        // Update text fields only if they're different to avoid infinite loops
        if (binding.titleNoteLayout.editTextTitle.text.toString() != state.title) {
            binding.titleNoteLayout.editTextTitle.setText(state.title)
        }
        if (binding.titleNoteLayout.editTextNote.text.toString() != state.note) {
            binding.titleNoteLayout.editTextNote.setText(state.note)
        }

        binding.startDateLayout.txtStartDate.text = state.startDate
        binding.scheduleReminderLayout.switchSchedule.isChecked = state.isScheduled
        binding.scheduleReminderLayout.switchReminder.isChecked = state.isReminderEnabled
        binding.scheduleReminderLayout.txtScheduleTime.text = state.scheduleTime ?: "--"
        binding.scheduleReminderLayout.txtReminderTime.text = state.reminderTime ?: "--"

        // Update visibility
        binding.scheduleReminderLayout.layoutScheduleTime.visibility =
            if (state.isScheduled) View.VISIBLE else View.GONE
        binding.scheduleReminderLayout.layoutReminder.visibility =
            if (state.isReminderEnabled) View.VISIBLE else View.GONE

        // Update priority text
        binding.taskWeightPriorityLayout.txtPriority.text = "${state.weight.weight}/4"

        // Update icon and color
        updateIconAndColor(state.icon, state.color)

        // Show/hide loading
        binding.progressBarSave.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        binding.buttonSave.isEnabled = !state.isLoading

        // Show error if any
        state.errorMessage?.let {
            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }

        // Handle save completion
        if (state.isSaved) {
            Toast.makeText(
                requireContext(),
                if (args.task != null) "Task updated" else "Task saved",
                Toast.LENGTH_SHORT
            ).show()
            findNavController().popBackStack()
            viewModel.resetSaveState()
        }
    }

    private fun updateIconAndColor(iconName: String, colorName: String) {
        try {
            val icon = TaskIcon.valueOf(iconName)
            val color = TaskColor.valueOf(colorName)

            binding.imageProfile.setImageResource(icon.resId)
            binding.imageProfile.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), color.resId)
            )
        } catch (e: Exception) {
            // Use defaults if there's an error
        }
    }

    private fun loadTaskDataIfEditing() {
        args.task?.let { task ->
            viewModel.loadTaskForEdit(task)
            viewModel.loadTaskListIds(task.id) {
                updateListSummary()
            }
            originalStartDate = task.taskAddedDate
        }
    }

    private fun setupTextListeners() {
        binding.titleNoteLayout.editTextTitle.doAfterTextChanged {
            viewModel.updateTitle(it.toString())
        }

        binding.titleNoteLayout.editTextNote.doAfterTextChanged {
            viewModel.updateNote(it.toString())
        }
    }

    private fun setupClickListeners() {
        binding.startDateLayout.startDateRow.setOnClickListener {
            if (args.task != null) {
                showStartDateWarningDialog()
            } else {
                openStartDatePicker()
            }
        }

        binding.scheduleReminderLayout.switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreScheduleToggle) return@setOnCheckedChangeListener

            if (isChecked) {
                showScheduleTimeOptions()
            } else {
                viewModel.updateSchedule(null, false)
            }
        }

        binding.scheduleReminderLayout.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showReminderTimeOptions()
            } else {
                viewModel.updateReminder(null, false)
            }
        }

        binding.scheduleReminderLayout.checkSameAsSchedule.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && viewModel.uiState.value?.scheduleTime != null) {
                val scheduleTime = viewModel.uiState.value?.scheduleTime
                viewModel.updateReminder(scheduleTime, true)
                binding.scheduleReminderLayout.txtReminderTime.isEnabled = false
            } else {
                binding.scheduleReminderLayout.txtReminderTime.isEnabled = true
                openTimePicker { time ->
                    viewModel.updateReminder(time, true)
                }
            }
        }

        binding.scheduleReminderLayout.scheduleRow.setOnClickListener {
            if (binding.scheduleReminderLayout.switchSchedule.isChecked) {
                openTimePicker { time ->
                    viewModel.updateSchedule(time, true)
                    if (binding.scheduleReminderLayout.checkSameAsSchedule.isChecked) {
                        viewModel.updateReminder(time, true)
                    }
                }
            }
        }

        binding.scheduleReminderLayout.reminderRow.setOnClickListener {
            if (binding.scheduleReminderLayout.switchReminder.isChecked &&
                !binding.scheduleReminderLayout.checkSameAsSchedule.isChecked
            ) {
                openTimePicker { time ->
                    viewModel.updateReminder(time, true)
                }
            }
        }

        binding.addToListLayout.addToListRow.setOnClickListener {
            val currentIds = viewModel.selectedListIds.value ?: emptyList()
            TaskListBottomSheet(
                preselectedIds = currentIds
            ) { ids ->
                viewModel.updateSelectedLists(ids)
            }.show(parentFragmentManager, "TaskListBottomSheet")
        }

        binding.taskWeightPriorityLayout.priorityContainer.setOnClickListener {
            val currentWeight = viewModel.uiState.value?.weight ?: TaskWeight.VERY_LOW
            TaskPriorityBottomSheet(
                selectedWeight = currentWeight
            ) { weight ->
                viewModel.updateWeight(weight)
            }.show(parentFragmentManager, "TaskPriorityBottomSheet")
        }

        binding.imageProfile.setOnClickListener {
            val currentState = viewModel.uiState.value
            val dialog = ImagePickerDialog.newInstance(
                selectedIcon = currentState?.icon ?: "TROPHY",
                selectedColor = currentState?.color ?: "DARK_BLUE"
            )
            dialog.setOnImageSelectedListener { iconName, colorName ->
                viewModel.updateIconAndColor(iconName, colorName)
            }
            dialog.show(parentFragmentManager, "ImagePickerDialog")
        }

        binding.deletePauseLayout.deleteRow.setOnClickListener {

            val task = args.task ?: return@setOnClickListener
            val currentDate = CommonMethods.getTodayDate()

            DeleteTaskDialog(
                requireContext(),
                task,
                currentDate,

                onDeleteDailyCompletely = {
                    viewModel.deleteTask(it)
                    findNavController().popBackStack()
                },

                onUpdateDailyDate = {
                    viewModel.updateTask(it)
                    findNavController().popBackStack()
                },

                onDeleteNonDaily = {
                    viewModel.deleteTask(it)
                    findNavController().popBackStack()
                }

            ).show()
        }

        binding.buttonSave.setOnClickListener {
            saveTask()
        }

        binding.repeatLayout.repeatRow.setOnClickListener {
            findNavController().navigate(R.id.repeatConfigFragment)
        }
    }

    private fun showScheduleTimeOptions() {
        val options = mutableListOf("Set time now")
        val currentState = viewModel.uiState.value

        if (currentState?.isReminderEnabled == true && currentState.reminderTime != null) {
            options.add("Use reminder time (${currentState.reminderTime})")
        }
        options.add("Cancel")

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Enable Schedule")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Set time now" -> {
                        openTimePicker { time ->
                            viewModel.updateSchedule(time, true)
                            if (binding.scheduleReminderLayout.checkSameAsSchedule.isChecked) {
                                viewModel.updateReminder(time, true)
                            }
                        }
                    }

                    else -> {
                        if (options[which].startsWith("Use reminder") &&
                            currentState?.reminderTime != null
                        ) {
                            viewModel.updateSchedule(currentState.reminderTime, true)
                            if (binding.scheduleReminderLayout.checkSameAsSchedule.isChecked) {
                                viewModel.updateReminder(currentState.reminderTime, true)
                            }
                        } else {
                            // Cancel - turn switch off
                            ignoreScheduleToggle = true
                            binding.scheduleReminderLayout.switchSchedule.isChecked = false
                            ignoreScheduleToggle = false
                            viewModel.updateSchedule(null, false)
                        }
                    }
                }
            }
            .show()
    }

    private fun showReminderTimeOptions() {
        val currentState = viewModel.uiState.value

        if (currentState?.isScheduled == true && currentState.scheduleTime != null) {
            binding.scheduleReminderLayout.checkSameAsSchedule.visibility = View.VISIBLE
            binding.scheduleReminderLayout.checkSameAsSchedule.isChecked = true
            viewModel.updateReminder(currentState.scheduleTime, true)
        } else {
            binding.scheduleReminderLayout.checkSameAsSchedule.visibility = View.GONE
            openTimePicker { time ->
                viewModel.updateReminder(time, true)
            }
        }
    }

    private fun showStartDateWarningDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Change start date?")
            .setMessage("Changing the start date may remove previous completion records.")
            .setNegativeButton("No", null)
            .setPositiveButton("Yes") { _, _ ->
                startDateChangeConfirmed = true
                openStartDatePicker()
            }
            .show()
    }

    private fun openStartDatePicker() {
        val cal = Calendar.getInstance()
        val currentDate = viewModel.uiState.value?.startDate ?: CommonMethods.getTodayDate()

        try {
            val parts = currentDate.split("-")
            if (parts.size == 3) {
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
        } catch (e: Exception) {
            // Use current date if parsing fails
        }

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                val selectedDate = String.format("%04d-%02d-%02d", y, m + 1, d)
                viewModel.updateStartDate(selectedDate)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )

        datePickerDialog.setOnShowListener {
            val color = ContextCompat.getColor(requireContext(), R.color.brand_blue)
            datePickerDialog.getButton(DatePickerDialog.BUTTON_POSITIVE)?.setTextColor(color)
            datePickerDialog.getButton(DatePickerDialog.BUTTON_NEGATIVE)?.setTextColor(color)
        }

        datePickerDialog.show()
    }

    private fun openTimePicker(onSelected: (String) -> Unit) {
        val cal = Calendar.getInstance()

        val dialog = TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                onSelected(sdf.format(cal.time))
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            false
        )

        dialog.setOnShowListener {
            val color = ContextCompat.getColor(requireContext(), R.color.brand_blue)
            dialog.getButton(TimePickerDialog.BUTTON_POSITIVE)?.setTextColor(color)
            dialog.getButton(TimePickerDialog.BUTTON_NEGATIVE)?.setTextColor(color)
        }

        dialog.show()
    }

    private fun updateListSummary() {
        val selectedIds = viewModel.selectedListIds.value ?: emptyList()
        val allLists = viewModel.allLists.value ?: emptyList()

        if (selectedIds.isEmpty()) {
            binding.addToListLayout.txtListSummary.text = "None"
            return
        }

        val firstList = allLists.firstOrNull { it.id == selectedIds.first() }
        val extraCount = selectedIds.size - 1

        binding.addToListLayout.txtListSummary.text = if (extraCount > 0) {
            "${firstList?.listTitle ?: "List"} +$extraCount"
        } else {
            firstList?.listTitle ?: "List"
        }
    }

    private fun saveTask() {
        val state = viewModel.uiState.value ?: return

        // Validate
        if (state.title.isBlank()) {
            Toast.makeText(requireContext(), "Please enter a task title", Toast.LENGTH_SHORT).show()
            return
        }

        if (state.isScheduled && state.scheduleTime == null) {
            Toast.makeText(requireContext(), "Please select schedule time", Toast.LENGTH_SHORT)
                .show()
            return
        }

        if (state.isReminderEnabled && state.reminderTime == null) {
            Toast.makeText(requireContext(), "Please select reminder time", Toast.LENGTH_SHORT)
                .show()
            return
        }

        // Save task
        viewModel.saveTask(
            isEdit = args.task != null,
            existingId = args.task?.id,
            taskType = selectedType
        ) { success, error ->
            if (!success) {
                Toast.makeText(requireContext(), error ?: "Save failed", Toast.LENGTH_SHORT).show()
            }
        }

        // Handle completion deletion if start date changed
        if (args.task != null && startDateChangeConfirmed &&
            state.startDate != originalStartDate
        ) {
            viewModel.deleteCompletionsBefore(args.task!!.id, state.startDate)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}