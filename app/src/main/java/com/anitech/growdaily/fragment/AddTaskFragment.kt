package com.anitech.growdaily.fragment

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.MyApp
import com.anitech.growdaily.R
import com.anitech.growdaily.setSolidBackgroundColorCompat
import com.anitech.growdaily.data_class.AddTaskUiState
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.database.viewmodel.AddTaskViewModel
import com.anitech.growdaily.database.viewmodel.AddTaskViewModelFactory
import com.anitech.growdaily.databinding.FragmentAddTaskBinding
import com.anitech.growdaily.dialog.DeleteTaskDialog
import com.anitech.growdaily.dialog.IconAndColorDialog
import com.anitech.growdaily.dialog.PauseOptionsDialog
import com.anitech.growdaily.dialog.TaskActionDialog
import com.anitech.growdaily.dialog.TaskListBottomSheet
import com.anitech.growdaily.dialog.TaskPriorityBottomSheet
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon
import com.anitech.growdaily.enum_class.RepeatType
import com.anitech.growdaily.enum_class.TaskType
import com.anitech.growdaily.enum_class.TaskWeight
import com.anitech.growdaily.enum_class.TrackingType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    private var originalStartDate: String = ""
    private var initialStateSnapshot: AddTaskUiState? = null
    private var initialSelectedListIds: List<String>? = null

    private var ignoreScheduleToggle = false
    private var ignoreReminderToggle = false

    // ── Tracking type views (resolved lazily after view is created) ───────────
    private val btnBinary   get() = binding.taskTrackingType.binaryType
    private val btnCount    get() = binding.taskTrackingType.countType
    private val btnTimer    get() = binding.taskTrackingType.timerType
    private val btnChecklist get() = binding.taskTrackingType.checklistType

    private val countExtra    get() = binding.taskTrackingType.countExtraContainer
    private val timerExtra    get() = binding.taskTrackingType.timerExtraContainer
    private val checklistExtra get() = binding.taskTrackingType.checklistExtraContainer

    private val btnCountMinus  get() = binding.taskTrackingType.btnCountMinus
    private val btnCountPlus   get() = binding.taskTrackingType.btnCountPlus
    private val txtCountValue  get() = binding.taskTrackingType.txtCountValue

    private val btnMinutesMinus get() = binding.taskTrackingType.btnMinutesMinus
    private val btnMinutesPlus  get() = binding.taskTrackingType.btnMinutesPlus
    private val txtMinutesValue get() = binding.taskTrackingType.txtMinutesValue

    private val checklistItemsContainer get() = binding.taskTrackingType.checklistItemsContainer
    private val etChecklistItem         get() = binding.taskTrackingType.etChecklistItem
    private val btnAddChecklistItem     get() = binding.taskTrackingType.btnAddChecklistItem

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupActionBar()
        setupTaskType()
        setupObservers()
        setupClickListeners()
        setupTextListeners()
        setupTrackingTypeListeners()
        setupRepeatConfigResult()
        loadTaskDataIfEditing()
        setupDiscardHandling()

        updateDeletePauseUi()
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

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
        viewModel.allLists.observe(viewLifecycleOwner) { updateListSummary() }
        viewModel.selectedListIds.observe(viewLifecycleOwner) { updateListSummary() }
    }

    // ── Observer-driven UI update ─────────────────────────────────────────────

    private fun updateUIFromState(state: AddTaskUiState) {
        binding.tvVersioningHint.visibility =
            if (args.task != null) View.VISIBLE else View.GONE

        if (binding.titleNoteLayout.editTextTitle.text.toString() != state.title)
            binding.titleNoteLayout.editTextTitle.setText(state.title)
        if (binding.titleNoteLayout.editTextNote.text.toString() != state.note)
            binding.titleNoteLayout.editTextNote.setText(state.note)

        binding.startDateLayout.txtStartDate.text = state.startDate

        if (binding.scheduleLayout.switchSchedule.isChecked != state.isScheduled) {
            ignoreScheduleToggle = true
            binding.scheduleLayout.switchSchedule.isChecked = state.isScheduled
            ignoreScheduleToggle = false
        }
        if (binding.reminderLayoutMain.switchReminder.isChecked != state.isReminderEnabled) {
            ignoreReminderToggle = true
            binding.reminderLayoutMain.switchReminder.isChecked = state.isReminderEnabled
            ignoreReminderToggle = false
        }

        binding.scheduleLayout.txtScheduleTime.text = state.scheduleTime ?: "--"
        binding.reminderLayoutMain.txtReminderTime.text = state.reminderTime ?: "--"
        binding.scheduleLayout.layoutScheduleTime.visibility =
            if (state.isScheduled) View.VISIBLE else View.GONE
        binding.reminderLayoutMain.layoutReminder.visibility =
            if (state.isReminderEnabled) View.VISIBLE else View.GONE

        binding.taskWeightPriorityLayout.txtPriority.text = "${state.weight.weight}/4"
        binding.repeatLayout.repeatRow.visibility =
            if (selectedType == TaskType.DAILY) View.VISIBLE else View.GONE
        binding.untilCompleteLayout.untilCompleteRow.visibility =
            if (selectedType == TaskType.DAY) View.VISIBLE else View.GONE
        binding.untilCompleteLayout.switchUntilComplete.isChecked = state.showUntilCompleted
        binding.repeatLayout.txtRepeatSummary.text =
            CommonMethods.formatRepeatSummary(
                state.repeatType,
                CommonMethods.serializeRepeatDays(state.repeatDays)
            )
        if (state.repeatType != RepeatType.DAILY && state.showMissedOnGapDays) {
            binding.repeatLayout.txtRepeatSummary.text =
                "${binding.repeatLayout.txtRepeatSummary.text} · gap days"
        }

        updateIconAndColor(state.icon, state.color)

        // ── Tracking type UI ─────────────────────────────────────────────────
        highlightSelectedType(state.trackingType)
        showExtraFieldsFor(state.trackingType)
        txtCountValue.text = state.dailyTargetCount.toString()
        txtMinutesValue.text = (state.targetDurationSeconds / 60).toString()
        rebuildChecklistItemViews(state.checklistItems)

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
            navigateBackWithLoading("Returning...")
            viewModel.resetSaveState()
        }

        captureInitialSnapshotIfNeeded(state)
    }

    // ── Tracking type helpers ─────────────────────────────────────────────────

    private fun setupTrackingTypeListeners() {
        if (args.task == null) {
            btnBinary.setOnClickListener    { viewModel.updateTrackingType(TrackingType.BINARY) }
            btnCount.setOnClickListener     { viewModel.updateTrackingType(TrackingType.COUNT) }
            btnTimer.setOnClickListener     { viewModel.updateTrackingType(TrackingType.TIMER) }
            btnChecklist.setOnClickListener { viewModel.updateTrackingType(TrackingType.CHECKLIST) }
        } else {
            listOf(btnBinary, btnCount, btnTimer, btnChecklist).forEach { button ->
                button.isEnabled = false
                button.alpha = 0.65f
            }
        }

        // COUNT stepper
        btnCountMinus.setOnClickListener {
            val current = viewModel.uiState.value?.dailyTargetCount ?: 1
            viewModel.updateDailyTargetCount(current - 1)
        }
        btnCountPlus.setOnClickListener {
            val current = viewModel.uiState.value?.dailyTargetCount ?: 1
            viewModel.updateDailyTargetCount(current + 1)
        }

        // TIMER stepper (steps of 5 minutes)
        btnMinutesMinus.setOnClickListener {
            val currentSec = viewModel.uiState.value?.targetDurationSeconds ?: 600L
            viewModel.updateTargetDurationSeconds(currentSec - 300L)   // -5 min
        }
        btnMinutesPlus.setOnClickListener {
            val currentSec = viewModel.uiState.value?.targetDurationSeconds ?: 600L
            viewModel.updateTargetDurationSeconds(currentSec + 300L)   // +5 min
        }

        // CHECKLIST add item
        btnAddChecklistItem.setOnClickListener {
            val text = etChecklistItem.text.toString()
            if (text.isNotBlank()) {
                viewModel.addChecklistItem(text)
                etChecklistItem.setText("")
            }
        }

        // Also add on keyboard "Done"
        etChecklistItem.setOnEditorActionListener { _, _, _ ->
            val text = etChecklistItem.text.toString()
            if (text.isNotBlank()) {
                viewModel.addChecklistItem(text)
                etChecklistItem.setText("")
            }
            true
        }
    }

    /** Highlights the active type button, resets the others. */
    private fun highlightSelectedType(type: TrackingType) {
        val activeColor   = ContextCompat.getColor(requireContext(), R.color.brand_blue)
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.add_form_muted_surface)
        val whiteColor    = ContextCompat.getColor(requireContext(), R.color.white)
        val blackColor    = ContextCompat.getColor(requireContext(), R.color.add_form_text_secondary)

        listOf(btnBinary, btnCount, btnTimer, btnChecklist).forEach { btn ->
            btn.backgroundTintList = ColorStateList.valueOf(inactiveColor)
            btn.setTextColor(blackColor)
        }

        val activeBtn = when (type) {
            TrackingType.BINARY    -> btnBinary
            TrackingType.COUNT     -> btnCount
            TrackingType.TIMER     -> btnTimer
            TrackingType.CHECKLIST -> btnChecklist
        }
        activeBtn.backgroundTintList = ColorStateList.valueOf(activeColor)
        activeBtn.setTextColor(whiteColor)
    }

    /** Shows only the extra field container relevant to [type]. */
    private fun showExtraFieldsFor(type: TrackingType) {
        countExtra.visibility     = if (type == TrackingType.COUNT)     View.VISIBLE else View.GONE
        timerExtra.visibility     = if (type == TrackingType.TIMER)     View.VISIBLE else View.GONE
        checklistExtra.visibility = if (type == TrackingType.CHECKLIST) View.VISIBLE else View.GONE
    }

    /**
     * Rebuilds the checklist item rows inside [checklistItemsContainer].
     * Each row shows the label and a delete button.
     * Called every time the checklist list changes in the state.
     */
    private fun rebuildChecklistItemViews(items: List<String>) {
        checklistItemsContainer.removeAllViews()
        items.forEachIndexed { index, label ->
            val row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_checklist_label_row, checklistItemsContainer, false)

            row.findViewById<TextView>(R.id.tvChecklistLabel).text = label
            row.findViewById<ImageButton>(R.id.btnRemoveChecklistItem).setOnClickListener {
                viewModel.removeChecklistItem(index)
            }

            checklistItemsContainer.addView(row)
        }
    }

    // ── Icon / color ──────────────────────────────────────────────────────────

    private fun updateIconAndColor(iconName: String, colorName: String) {
        try {
            val icon  = TaskIcon.valueOf(iconName)
            val color = TaskColor.valueOf(colorName)
            binding.imageProfile.setImageResource(icon.resId)
            binding.imageProfile.setSolidBackgroundColorCompat(
                ContextCompat.getColor(requireContext(), color.resId)
            )
        } catch (e: Exception) { /* use defaults */ }
    }

    // ── Load for edit ─────────────────────────────────────────────────────────

    private fun loadTaskDataIfEditing() {
        args.task?.let { task ->
            originalStartDate = task.taskAddedDate
            viewModel.loadTaskForEdit(task)
            viewModel.loadTaskListIds(task.id) { ids ->
                initialSelectedListIds = ids.sorted()
                updateListSummary()
            }
        } ?: run {
            initialSelectedListIds = emptyList()
        }
    }

    private fun setupDiscardHandling() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    attemptClose()
                }
            }
        )
    }

    private fun setupRepeatConfigResult() {
        parentFragmentManager.setFragmentResultListener(
            "repeatResult",
            viewLifecycleOwner
        ) { _, bundle ->
            val type = bundle.getString("repeatType")
                ?.let { runCatching { RepeatType.valueOf(it) }.getOrNull() }
                ?: RepeatType.DAILY
            val days = bundle.getIntegerArrayList("repeatDays")?.toList().orEmpty()
            val showMissedOnGapDays = bundle.getBoolean("showMissedOnGapDays", false)
            viewModel.updateRepeatConfig(type, days)
            viewModel.updateShowMissedOnGapDays(showMissedOnGapDays)
        }
    }

    private fun updateDeletePauseUi() {
        val task = args.task
        binding.deletePauseLayout.deletePauseContainer.visibility =
            if (task != null) View.VISIBLE else View.GONE

        if (task == null) return

        val isDailyTask = task.taskType == TaskType.DAILY
        val isPaused = isPausedDailyTask(task)
        val deleteParams =
            binding.deletePauseLayout.deleteRow.layoutParams as LinearLayout.LayoutParams
        val pauseParams =
            binding.deletePauseLayout.pauseRow.layoutParams as LinearLayout.LayoutParams

        binding.deletePauseLayout.pauseRow.visibility =
            if (isDailyTask) View.VISIBLE else View.GONE
        binding.deletePauseLayout.deletePauseSpacer.visibility =
            if (isDailyTask) View.VISIBLE else View.GONE

        deleteParams.weight = if (isDailyTask) 1f else 2f
        pauseParams.weight = 1f
        binding.deletePauseLayout.deleteRow.layoutParams = deleteParams
        binding.deletePauseLayout.pauseRow.layoutParams = pauseParams

        binding.deletePauseLayout.tvPauseAction.text =
            if (isPaused) "Resume" else "Pause"
        binding.deletePauseLayout.ivPause.setImageResource(
            if (isPaused) android.R.drawable.ic_media_play else R.drawable.ic_pause
        )
        binding.deletePauseLayout.ivPause.contentDescription =
            if (isPaused) "Resume" else "Pause"
    }

    private fun isPausedDailyTask(task: com.anitech.growdaily.data_class.TaskEntity): Boolean {
        if (task.taskType != TaskType.DAILY) return false
        val removedDate = task.taskRemovedDate ?: return false
        return removedDate <= CommonMethods.getTodayDate()
    }

    // ── Text listeners ────────────────────────────────────────────────────────

    private fun setupTextListeners() {
        binding.titleNoteLayout.editTextTitle.doAfterTextChanged {
            viewModel.updateTitle(it.toString())
        }
        binding.titleNoteLayout.editTextNote.doAfterTextChanged {
            viewModel.updateNote(it.toString())
        }
    }

    // ── Click listeners ───────────────────────────────────────────────────────

    private fun setupClickListeners() {

        binding.startDateLayout.startDateRow.setOnClickListener {
            openStartDatePicker()
        }

        binding.untilCompleteLayout.untilCompleteRow.setOnClickListener {
            binding.untilCompleteLayout.switchUntilComplete.toggle()
        }
        binding.untilCompleteLayout.switchUntilComplete.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateShowUntilCompleted(isChecked)
        }

        binding.scheduleLayout.switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreScheduleToggle) return@setOnCheckedChangeListener
            if (isChecked) handleScheduleEnabled() else viewModel.updateSchedule(null, false)
        }

        binding.reminderLayoutMain.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreReminderToggle) return@setOnCheckedChangeListener
            if (isChecked) handleReminderEnabled() else viewModel.updateReminder(null, false)
        }

        binding.scheduleLayout.scheduleRow.setOnClickListener {
            if (binding.scheduleLayout.switchSchedule.isChecked)
                openTimePicker { time -> viewModel.updateSchedule(time, true) }
        }

        binding.reminderLayoutMain.reminderBody.setOnClickListener {
            if (binding.reminderLayoutMain.switchReminder.isChecked)
                openTimePicker { time -> viewModel.updateReminder(time, true) }
        }

        binding.addToListLayout.addToListRow.setOnClickListener {
            val currentIds = viewModel.selectedListIds.value ?: emptyList()
            TaskListBottomSheet(
                allListsLiveData = viewModel.allLists,
                preselectedIds = currentIds,
                onInsertList = { list -> viewModel.insertList(list) }
            ) { ids -> viewModel.updateSelectedLists(ids) }
                .show(parentFragmentManager, "TaskListBottomSheet")
        }

        binding.taskWeightPriorityLayout.priorityContainer.setOnClickListener {
            val currentWeight = viewModel.uiState.value?.weight ?: TaskWeight.VERY_LOW
            TaskPriorityBottomSheet(selectedWeight = currentWeight) { weight ->
                viewModel.updateWeight(weight)
            }.show(parentFragmentManager, "TaskPriorityBottomSheet")
        }

        binding.imageProfile.setOnClickListener {
            val currentState = viewModel.uiState.value
            val dialog = IconAndColorDialog.newInstance(
                selectedIcon  = currentState?.icon  ?: "TROPHY",
                selectedColor = currentState?.color ?: "DARK_BLUE"
            )
            dialog.setOnImageSelectedListener { iconName, colorName ->
                viewModel.updateIconAndColor(iconName, colorName)
            }
            dialog.show(parentFragmentManager, "IconAndColorDialog")
        }

        binding.deletePauseLayout.deleteRow.setOnClickListener {
            val task = args.task ?: return@setOnClickListener
            DeleteTaskDialog(
                requireContext(),
                task,
                onDeleteConfirmed = {
                    viewModel.deleteTask(it)
                    findNavController().popBackStack()
                }
            ).show()
        }

        binding.deletePauseLayout.pauseRow.setOnClickListener {
            val task = args.task ?: return@setOnClickListener
            if (task.taskType != TaskType.DAILY) return@setOnClickListener

            val isPaused = isPausedDailyTask(task)
            if (isPaused) {
                TaskActionDialog(
                    context = requireContext(),
                    title = "Resume daily task?",
                    message = "This will make the task active again starting today.",
                    primaryLabel = "Resume",
                    iconRes = android.R.drawable.ic_media_play,
                    accentColor = ContextCompat.getColor(requireContext(), R.color.brand_blue),
                    iconBubbleColor = 0x332196F3,
                    onPrimaryAction = {
                        viewModel.resumeDailyTask(task)
                        findNavController().popBackStack()
                    }
                ).show()
            } else {
                showPauseOptionsDialog(task)
            }
        }

        binding.buttonSave.setOnClickListener { saveTask() }

        binding.repeatLayout.repeatRow.setOnClickListener {
            val state = viewModel.uiState.value
            findNavController().navigate(
                R.id.repeatConfigFragment,
                bundleOf(
                    "isEditing" to (args.task != null),
                    "repeatType" to (state?.repeatType?.name ?: RepeatType.DAILY.name),
                    "repeatDays" to ArrayList(state?.repeatDays ?: emptyList<Int>()),
                    "showMissedOnGapDays" to (state?.showMissedOnGapDays ?: false)
                )
            )
        }
    }

    // ── Schedule / Reminder enable helpers ───────────────────────────────────

    private fun handleScheduleEnabled() {
        val currentState = viewModel.uiState.value
        val reminderTime = currentState?.reminderTime
        if (currentState?.isReminderEnabled == true && reminderTime != null) {
            val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Schedule time")
                .setMessage("Use the same time as your reminder ($reminderTime)?")
                .setCancelable(false)
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
            openTimePickerOrRevertSchedule()
        }
    }

    private fun handleReminderEnabled() {
        val currentState = viewModel.uiState.value
        val scheduleTime = currentState?.scheduleTime
        if (currentState?.isScheduled == true && scheduleTime != null) {
            val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
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

    private fun openTimePickerOrRevertSchedule() {
        var timePicked = false
        openTimePicker(
            onDismiss = {
                if (!timePicked) {
                    ignoreScheduleToggle = true
                    binding.scheduleLayout.switchSchedule.isChecked = false
                    ignoreScheduleToggle = false
                    viewModel.updateSchedule(null, false)
                }
            }
        ) { time -> timePicked = true; viewModel.updateSchedule(time, true) }
    }

    private fun openTimePickerOrRevertReminder() {
        var timePicked = false
        openTimePicker(
            onDismiss = {
                if (!timePicked) {
                    ignoreReminderToggle = true
                    binding.reminderLayoutMain.switchReminder.isChecked = false
                    ignoreReminderToggle = false
                    viewModel.updateReminder(null, false)
                }
            }
        ) { time -> timePicked = true; viewModel.updateReminder(time, true) }
    }

    // ── Date / Time pickers ───────────────────────────────────────────────────

    private fun openStartDatePicker() {
        val cal = Calendar.getInstance()
        val currentDate = viewModel.uiState.value?.startDate ?: CommonMethods.getTodayDate()
        try {
            val parts = currentDate.split("-")
            if (parts.size == 3) cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        } catch (e: Exception) { /* use today */ }

        val picker = DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                val selectedDate = String.format("%04d-%02d-%02d", y, m + 1, d)
                handleStartDateSelection(selectedDate)
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        )
        picker.setOnShowListener {
            val color = ContextCompat.getColor(requireContext(), R.color.brand_blue)
            picker.getButton(DatePickerDialog.BUTTON_POSITIVE)?.setTextColor(color)
            picker.getButton(DatePickerDialog.BUTTON_NEGATIVE)?.setTextColor(color)
        }
        picker.show()
    }

    private fun openTimePicker(onDismiss: (() -> Unit)? = null, onSelected: (String) -> Unit) {
        val cal = Calendar.getInstance()
        val dialog = TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                onSelected(SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time))
            },
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false
        )
        dialog.setOnShowListener {
            val color = ContextCompat.getColor(requireContext(), R.color.brand_blue)
            dialog.getButton(TimePickerDialog.BUTTON_POSITIVE)?.setTextColor(color)
            dialog.getButton(TimePickerDialog.BUTTON_NEGATIVE)?.setTextColor(color)
        }
        if (onDismiss != null) dialog.setOnDismissListener { onDismiss() }
        dialog.show()
    }

    private fun handleStartDateSelection(selectedDate: String) {
        if (args.task == null) {
            viewModel.updateStartDate(selectedDate)
            return
        }

        val originalDate = runCatching { LocalDate.parse(originalStartDate) }.getOrNull()
        val newDate = runCatching { LocalDate.parse(selectedDate) }.getOrNull()

        if (originalDate != null && newDate != null && newDate.isAfter(originalDate)) {
            showStartDateSelectionConfirmation(selectedDate, originalDate, newDate)
        } else {
            viewModel.updateStartDate(selectedDate)
        }
    }

    private fun showStartDateSelectionConfirmation(
        selectedDate: String,
        originalDate: LocalDate,
        newDate: LocalDate
    ) {
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
        val originalDisplayDate = originalDate.format(formatter)
        val newDisplayDate = newDate.format(formatter)
        TaskActionDialog(
            context = requireContext(),
            title = "Keep new start date?",
            message = "Changing the start date from $originalDisplayDate to $newDisplayDate will delete completion history before $newDisplayDate when you save.",
            primaryLabel = "Keep $newDisplayDate",
            secondaryLabel = "Keep $originalDisplayDate",
            iconRes = R.drawable.ic_warning,
            accentColor = ContextCompat.getColor(requireContext(), R.color.brand_blue),
            iconBubbleColor = 0x332196F3,
            onPrimaryAction = {
                viewModel.updateStartDate(selectedDate)
            },
            onSecondaryAction = {
                viewModel.updateStartDate(originalStartDate)
            }
        ).show()
    }

    private fun showPauseOptionsDialog(task: TaskEntity) {
        val today = LocalDate.parse(CommonMethods.getTodayDate())
        PauseOptionsDialog(
            context = requireContext(),
            title = "Pause daily task?",
            message = "Choose when this task should stop showing up. Past progress will stay intact.",
            iconRes = R.drawable.ic_pause,
            accentColor = ContextCompat.getColor(requireContext(), R.color.brand_blue),
            iconBubbleColor = 0x332196F3,
            onPauseFromTomorrow = {
                viewModel.updateTask(task.copy(taskRemovedDate = today.toString()))
                findNavController().popBackStack()
            },
            onPauseFromToday = {
                viewModel.updateTask(task.copy(taskRemovedDate = today.minusDays(1).toString()))
                findNavController().popBackStack()
            }
        ).show()
    }

    // ── List summary ──────────────────────────────────────────────────────────

    private fun updateListSummary() {
        val selectedIds = viewModel.selectedListIds.value ?: emptyList()
        val allLists    = viewModel.allLists.value ?: emptyList()
        if (selectedIds.isEmpty()) {
            binding.addToListLayout.txtListSummary.text = "None"
            binding.addToListLayout.txtListSummary.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.add_form_text_secondary)
            )
            return
        }
        val firstList  = allLists.firstOrNull { it.id == selectedIds.first() }
        val extraCount = selectedIds.size - 1
        binding.addToListLayout.txtListSummary.text = if (extraCount > 0)
            "${firstList?.listTitle ?: "List"} +$extraCount"
        else
            firstList?.listTitle ?: "List"
        binding.addToListLayout.txtListSummary.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.brand_blue)
        )
    }

    // ── Save ──────────────────────────────────────────────────────────────────

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

        val originalDate = originalStartDate.takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
        val newDate = runCatching { LocalDate.parse(state.startDate) }.getOrNull()

        val shouldDeleteBeforeNewStart =
            args.task != null &&
                originalDate != null &&
                newDate != null &&
                newDate.isAfter(originalDate)

        performSave(deleteCompletionsBeforeNewStart = shouldDeleteBeforeNewStart)
    }

    private fun attemptClose() {
        if (!hasUnsavedChanges()) {
            navigateBackWithLoading()
            return
        }

        TaskActionDialog(
            context = requireContext(),
            title = "Discard changes?",
            message = if (args.task != null) {
                "You have unsaved edits to this task. If you leave now, those changes will be lost."
            } else {
                "You have started creating a task. If you leave now, those changes will be lost."
            },
            primaryLabel = "Discard",
            secondaryLabel = "Keep editing",
            iconRes = R.drawable.ic_warning,
            accentColor = ContextCompat.getColor(requireContext(), R.color.brand_blue),
            iconBubbleColor = 0x332196F3,
            onPrimaryAction = {
                navigateBackWithLoading()
            }
        ).show()
    }

    private fun navigateBackWithLoading(message: String = "Going back...") {
        binding.tvNavigationLoading.text = message
        binding.navigationLoadingOverlay.visibility = View.VISIBLE
        binding.buttonSave.isEnabled = false
        binding.root.post {
            if (_binding == null || !isAdded) return@post
            findNavController().popBackStack()
        }
    }

    private fun hasUnsavedChanges(): Boolean {
        val baselineState = initialStateSnapshot ?: return false
        val baselineListIds = initialSelectedListIds ?: return false
        val currentState = viewModel.uiState.value ?: return false
        val currentListIds = (viewModel.selectedListIds.value ?: emptyList()).sorted()

        return normalizeStateForDirtyCheck(currentState) != baselineState ||
            currentListIds != baselineListIds
    }

    private fun captureInitialSnapshotIfNeeded(state: AddTaskUiState) {
        if (initialStateSnapshot != null) return

        val task = args.task
        if (task == null) {
            initialStateSnapshot = normalizeStateForDirtyCheck(state)
            return
        }

        val looksLikeLoadedEditState =
            state.title == task.title &&
                state.startDate == task.taskAddedDate &&
                state.icon == task.iconResId &&
                state.color == task.colorCode

        if (looksLikeLoadedEditState) {
            initialStateSnapshot = normalizeStateForDirtyCheck(state)
        }
    }

    private fun normalizeStateForDirtyCheck(state: AddTaskUiState): AddTaskUiState {
        return state.copy(
            title = state.title.trim(),
            note = state.note.trim(),
            checklistItems = state.checklistItems.map { it.trim() },
            repeatDays = state.repeatDays.distinct().sorted(),
            isLoading = false,
            errorMessage = null,
            isSaved = false,
            manualOrder = 0
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                attemptClose()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun performSave(deleteCompletionsBeforeNewStart: Boolean) {
        val state = viewModel.uiState.value ?: return
        viewModel.saveTask(
            isEdit = args.task != null,
            existingId = args.task?.id,
            taskType = selectedType,
            originalTask = args.task
        ) { success, error ->
            if (!success) {
                Toast.makeText(requireContext(), error ?: "Save failed", Toast.LENGTH_SHORT).show()
                return@saveTask
            }
            if (deleteCompletionsBeforeNewStart && args.task != null) {
                viewModel.deleteCompletionsBefore(args.task!!.id, state.startDate)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
