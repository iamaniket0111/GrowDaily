package com.anitech.growdaily.fragment

import android.app.AlertDialog
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

    // Guards to prevent observer-driven UI updates from re-firing toggle listeners
    private var ignoreScheduleToggle = false
    private var ignoreReminderToggle = false

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
    }

    private fun setupObservers() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            updateUIFromState(state)
        }

        viewModel.allLists.observe(viewLifecycleOwner) {
            updateListSummary()
        }

        viewModel.selectedListIds.observe(viewLifecycleOwner) {
            updateListSummary()
        }
    }

    private fun updateUIFromState(state: AddTaskUiState) {
        // Update text fields only if different to avoid infinite loops
        if (binding.titleNoteLayout.editTextTitle.text.toString() != state.title) {
            binding.titleNoteLayout.editTextTitle.setText(state.title)
        }
        if (binding.titleNoteLayout.editTextNote.text.toString() != state.note) {
            binding.titleNoteLayout.editTextNote.setText(state.note)
        }

        binding.startDateLayout.txtStartDate.text = state.startDate

        // ── Schedule switch ──────────────────────────────────────────────────
        // Use ignore flags so that programmatic isChecked changes don't fire
        // the checked-change listeners (which would show dialogs unexpectedly).
        if (binding.scheduleReminderLayout.switchSchedule.isChecked != state.isScheduled) {
            ignoreScheduleToggle = true
            binding.scheduleReminderLayout.switchSchedule.isChecked = state.isScheduled
            ignoreScheduleToggle = false
        }

        // ── Reminder switch ──────────────────────────────────────────────────
        if (binding.scheduleReminderLayout.switchReminder.isChecked != state.isReminderEnabled) {
            ignoreReminderToggle = true
            binding.scheduleReminderLayout.switchReminder.isChecked = state.isReminderEnabled
            ignoreReminderToggle = false
        }

        binding.scheduleReminderLayout.txtScheduleTime.text = state.scheduleTime ?: "--"
        binding.scheduleReminderLayout.txtReminderTime.text = state.reminderTime ?: "--"

        binding.scheduleReminderLayout.layoutScheduleTime.visibility =
            if (state.isScheduled) View.VISIBLE else View.GONE
        binding.scheduleReminderLayout.layoutReminder.visibility =
            if (state.isReminderEnabled) View.VISIBLE else View.GONE

        binding.taskWeightPriorityLayout.txtPriority.text = "${state.weight.weight}/4"

        updateIconAndColor(state.icon, state.color)

        binding.progressBarSave.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        binding.buttonSave.isEnabled = !state.isLoading

        state.errorMessage?.let {
            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }

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

        // ── Start date ───────────────────────────────────────────────────────
        binding.startDateLayout.startDateRow.setOnClickListener {
            if (args.task != null) {
                showStartDateWarningDialog()
            } else {
                openStartDatePicker()
            }
        }

        // ── Schedule toggle ──────────────────────────────────────────────────
        binding.scheduleReminderLayout.switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreScheduleToggle) return@setOnCheckedChangeListener

            if (isChecked) {
                handleScheduleEnabled()
            } else {
                viewModel.updateSchedule(null, false)
            }
        }

        // ── Reminder toggle ──────────────────────────────────────────────────
        binding.scheduleReminderLayout.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreReminderToggle) return@setOnCheckedChangeListener

            if (isChecked) {
                handleReminderEnabled()
            } else {
                viewModel.updateReminder(null, false)
            }
        }


        // ── Tap on the schedule time label to change time ────────────────────
        binding.scheduleReminderLayout.scheduleRow.setOnClickListener {
            if (binding.scheduleReminderLayout.switchSchedule.isChecked) {
                openTimePicker { time ->
                    viewModel.updateSchedule(time, true)
                }
            }
        }

        // ── Tap on the reminder time label to change time ────────────────────
        binding.scheduleReminderLayout.reminderRow.setOnClickListener {
            if (binding.scheduleReminderLayout.switchReminder.isChecked) {
                openTimePicker { time ->
                    viewModel.updateReminder(time, true)
                }
            }
        }

        // ── Other rows ───────────────────────────────────────────────────────
        binding.addToListLayout.addToListRow.setOnClickListener {
            val currentIds = viewModel.selectedListIds.value ?: emptyList()
            TaskListBottomSheet(
                allListsLiveData = viewModel.allLists,
                preselectedIds = currentIds,
                onInsertList = { list -> viewModel.insertList(list) }
            ) { ids ->
                viewModel.updateSelectedLists(ids)
            }.show(parentFragmentManager, "TaskListBottomSheet")
        }

        binding.taskWeightPriorityLayout.priorityContainer.setOnClickListener {
            val currentWeight = viewModel.uiState.value?.weight ?: TaskWeight.VERY_LOW
            TaskPriorityBottomSheet(selectedWeight = currentWeight) { weight ->
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

    // ── Schedule/Reminder enable helpers ────────────────────────────────────

    /**
     * Called when the user manually turns ON the Schedule switch.
     *
     * • If reminder is already set  → ask "use same time?" (non-cancellable dialog).
     * • Otherwise                   → open TimePicker directly.
     *
     * If the user dismisses without choosing (back press is the only escape since the
     * dialog is not cancellable), the switch is turned back off cleanly.
     */
    private fun handleScheduleEnabled() {
        val currentState = viewModel.uiState.value
        val reminderTime = currentState?.reminderTime

        if (currentState?.isReminderEnabled == true && reminderTime != null) {
            // Offer "use reminder time" but force a decision — not cancellable by touch-outside
            val dialog =   androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Schedule time")
                .setMessage("Use the same time as your reminder ($reminderTime)?")
                .setCancelable(false)                       // must tap a button
                .setPositiveButton("Use same ($reminderTime)") { _, _ ->
                    viewModel.updateSchedule(reminderTime, true)
                }
                .setNegativeButton("Pick different time") { _, _ ->
                    openTimePickerOrRevertSchedule()
                }
                .show()

            val primaryColor = ContextCompat.getColor(requireContext(), R.color.brand_blue)

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(primaryColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(primaryColor)
        } else {
            // No reminder set — go straight to TimePicker
            openTimePickerOrRevertSchedule()
        }
    }

    /**
     * Called when the user manually turns ON the Reminder switch.
     *
     * • If schedule is already set  → ask "use same time?" (non-cancellable dialog).
     * • Otherwise                   → open TimePicker directly.
     */
    private fun handleReminderEnabled() {
        val currentState = viewModel.uiState.value
        val scheduleTime = currentState?.scheduleTime

        if (currentState?.isScheduled == true && scheduleTime != null) {
            val dialog =  androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Reminder time")
                .setMessage("Use the same time as your schedule ($scheduleTime)?")
                .setCancelable(false)
                .setPositiveButton("Use same ($scheduleTime)") { _, _ ->
                    viewModel.updateReminder(scheduleTime, true)
                }
                .setNegativeButton("Pick different time") { _, _ ->
                    openTimePickerOrRevertReminder()
                }
                .show()

            val primaryColor = ContextCompat.getColor(requireContext(), R.color.brand_blue)

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(primaryColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(primaryColor)
        } else {
            openTimePickerOrRevertReminder()
        }
    }

    /**
     * Opens the TimePicker for schedule time.
     * If the user cancels the picker (taps Cancel), the switch is reverted to OFF
     * so we never end up with isScheduled=true but no time chosen.
     */
    private fun openTimePickerOrRevertSchedule() {
        var timePicked = false
        openTimePicker(
            onDismiss = {
                if (!timePicked) {
                    ignoreScheduleToggle = true
                    binding.scheduleReminderLayout.switchSchedule.isChecked = false
                    ignoreScheduleToggle = false
                    viewModel.updateSchedule(null, false)
                }
            }
        ) { time ->
            timePicked = true
            viewModel.updateSchedule(time, true)
        }
    }

    /**
     * Opens the TimePicker for reminder time.
     * If the user cancels the picker, the switch is reverted to OFF.
     */
    private fun openTimePickerOrRevertReminder() {
        var timePicked = false
        openTimePicker(
            onDismiss = {
                if (!timePicked) {
                    ignoreReminderToggle = true
                    binding.scheduleReminderLayout.switchReminder.isChecked = false
                    ignoreReminderToggle = false
                    viewModel.updateReminder(null, false)
                }
            }
        ) { time ->
            timePicked = true
            viewModel.updateReminder(time, true)
        }
    }

    // ── Date / Time pickers ──────────────────────────────────────────────────

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

    /**
     * Opens a TimePickerDialog and calls back on selection or dismissal.
     *
     * @param onDismiss  called when the dialog is dismissed for any reason (including
     *                   after [onSelected] fires — guard with a flag if needed).
     * @param onSelected called with the formatted time string when the user confirms.
     *                   Placed last so it can be used as a trailing lambda at call sites.
     */
    private fun openTimePicker(
        onDismiss: (() -> Unit)? = null,
        onSelected: (String) -> Unit
    ) {
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

        if (onDismiss != null) {
            dialog.setOnDismissListener { onDismiss() }
        }

        dialog.show()
    }

    // ── List summary ─────────────────────────────────────────────────────────

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

    // ── Save ─────────────────────────────────────────────────────────────────

    private fun saveTask() {
        val state = viewModel.uiState.value ?: return

        if (state.title.isBlank()) {
            Toast.makeText(requireContext(), "Please enter a task title", Toast.LENGTH_SHORT).show()
            return
        }

        if (state.isScheduled && state.scheduleTime == null) {
            Toast.makeText(requireContext(), "Please select schedule time", Toast.LENGTH_SHORT).show()
            return
        }

        if (state.isReminderEnabled && state.reminderTime == null) {
            Toast.makeText(requireContext(), "Please select reminder time", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.saveTask(
            isEdit = args.task != null,
            existingId = args.task?.id,
            taskType = selectedType
        ) { success, error ->
            if (!success) {
                Toast.makeText(requireContext(), error ?: "Save failed", Toast.LENGTH_SHORT).show()
            }
        }

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