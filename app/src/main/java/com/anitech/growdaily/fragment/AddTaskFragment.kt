package com.anitech.growdaily.fragment

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.MainActivity
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
import com.anitech.growdaily.enum_class.TaskInactiveReason
import com.anitech.growdaily.enum_class.TaskIcon
import com.anitech.growdaily.enum_class.RepeatType
import com.anitech.growdaily.enum_class.TaskType
import com.anitech.growdaily.enum_class.TaskWeight
import com.anitech.growdaily.enum_class.TrackingType
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
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

    private var accentColor: Int = Color.BLUE

    private var selectedType: TaskType = TaskType.DAILY
    private var originalStartDate: String = ""
    private var hasUserSelectedTaskAppearance: Boolean = false
    private var initialStateSnapshot: AddTaskUiState? = null
    private var initialSelectedListIds: List<String>? = null

    private var ignoreScheduleToggle = false
    private var ignoreReminderToggle = false
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                ensureReminderPermissionThenEnable()
            } else {
                ignoreReminderToggle = true
                binding.reminderLayoutMain.switchReminder.isChecked = false
                ignoreReminderToggle = false
                viewModel.updateReminder(null, false)
                
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.notification_permission_needed),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    showPermissionDeniedFeedback()
                }
            }
        }

    private fun showPermissionDeniedFeedback() {
        TaskActionDialog(
            context = requireContext(),
            title = getString(R.string.notification_permission_needed),
            message = getString(R.string.discard_changes_message_edit), // Reusing a message for now or add new one
            primaryLabel = getString(R.string.exact_alarm_permission_button),
            secondaryLabel = getString(R.string.cancel_button),
            iconRes = R.drawable.ic_warning,
            accentColor = accentColor,
            iconBubbleColor = accentBubbleColor(),
            onPrimaryAction = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            }
        ).show()
    }

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
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Try to capture initial accent color immediately
        (requireActivity() as? MainActivity)?.accentColor?.value?.let {
            accentColor = it
        }

        setupActionBar()
        setupTaskType()
        observeAccentColor()
        setupObservers()
        setupClickListeners()
        setupTextListeners()
        setupTrackingTypeListeners()
        setupRepeatConfigResult()
        loadTaskDataIfEditing()
        setupDiscardHandling()
        setupMenu()

        updateDeletePauseUi()
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: android.view.Menu, menuInflater: android.view.MenuInflater) {
                // No specific menu items for this fragment, but adding provider 
                // ensures we don't leak global menus.
                menu.clear() 
            }

            override fun onMenuItemSelected(menuItem: android.view.MenuItem): Boolean {
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun observeAccentColor() {
        (requireActivity() as? MainActivity)?.accentColor?.observe(viewLifecycleOwner) { color ->
            accentColor = color
            updateAccentColorUi(color)
        }
    }

    private fun updateAccentColorUi(color: Int) {
        maybeApplyAccentAsDefaultTaskColor(color)
        binding.buttonSave.backgroundTintList = ColorStateList.valueOf(color)
        binding.buttonSave.setTextColor(onAccentTextColor(color))
        binding.progressBarSave.indeterminateTintList = ColorStateList.valueOf(color)
        binding.taskTrackingType.btnAddChecklistItem.backgroundTintList = ColorStateList.valueOf(color)
        binding.taskWeightPriorityLayout.txtPriority.setTextColor(color)
        binding.startDateLayout.txtStartDate.setTextColor(color)
        binding.repeatLayout.txtRepeatSummary.setTextColor(color)
        
        // Warning Layout Styling
        binding.warningLayout.ivWarningIcon.imageTintList = ColorStateList.valueOf(color)
        binding.warningLayout.ivWarningIcon.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 36))
        binding.warningLayout.tvWarningTitle.setTextColor(color)
        
        // Subtle background tint for the warning card in light mode
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (!isDarkMode) {
            binding.warningLayout.root.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 10))
            binding.warningLayout.root.backgroundTintMode = PorterDuff.Mode.SRC_OVER
        } else {
            binding.warningLayout.root.backgroundTintList = null
        }

        applyAccentToEditTexts(color)
        applyAccentToSwitches(color)
        binding.addToListLayout.txtListSummary.setTextColor(
            if ((viewModel.selectedListIds.value ?: emptyList()).isEmpty()) {
                ContextCompat.getColor(requireContext(), R.color.add_form_text_secondary)
            } else {
                color
            }
        )
        binding.endDateLayout.txtEndDate.setTextColor(
            if ((viewModel.uiState.value?.endDate).isNullOrBlank()) {
                ContextCompat.getColor(requireContext(), R.color.add_form_text_secondary)
            } else {
                color
            }
        )
        // Refresh tracking type highlights with the new accent color
        viewModel.uiState.value?.trackingType?.let { highlightSelectedType(it) }
    }

    private fun applyAccentToEditTexts(color: Int) {
        val highlightColor = ColorUtils.setAlphaComponent(color, 48)
        listOf(
            binding.titleNoteLayout.editTextTitle,
            binding.titleNoteLayout.editTextNote,
            binding.taskTrackingType.etChecklistItem
        ).forEach { editText ->
            editText.textCursorDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(color)
                setSize(dpToPx(2), 1)
            }
            editText.highlightColor = highlightColor
        }
    }

    private fun applyAccentToSwitches(color: Int) {
        val thumbTint = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                color,
                ContextCompat.getColor(requireContext(), R.color.white)
            )
        )
        val trackTint = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                ColorUtils.setAlphaComponent(color, 110),
                ContextCompat.getColor(requireContext(), R.color.task_done_track)
            )
        )

        tintSwitch(binding.scheduleLayout.switchSchedule, thumbTint, trackTint)
        tintSwitch(binding.reminderLayoutMain.switchReminder, thumbTint, trackTint)
        tintSwitch(binding.untilCompleteLayout.switchUntilComplete, thumbTint, trackTint)
    }

    private fun tintSwitch(switch: SwitchCompat, thumbTint: ColorStateList, trackTint: ColorStateList) {
        switch.thumbTintList = thumbTint
        switch.trackTintList = trackTint
    }

    private fun tintSwitch(switch: Switch, thumbTint: ColorStateList, trackTint: ColorStateList) {
        switch.thumbTintList = thumbTint
        switch.trackTintList = trackTint
    }

    private fun setupActionBar() {
        val title = if (args.task != null) getString(R.string.edit_task_title) else getString(R.string.add_task_title)
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
        binding.warningLayout.root.visibility =
            if (args.task != null) View.VISIBLE else View.GONE

        if (binding.titleNoteLayout.editTextTitle.text.toString() != state.title)
            binding.titleNoteLayout.editTextTitle.setText(state.title)
        if (binding.titleNoteLayout.editTextNote.text.toString() != state.note)
            binding.titleNoteLayout.editTextNote.setText(state.note)

        binding.startDateLayout.txtStartDate.text = state.startDate
        binding.endDateLayout.endDateRow.visibility =
            if (selectedType == TaskType.DAILY) View.VISIBLE else View.GONE
        binding.endDateLayout.txtEndDate.text = state.endDate ?: getString(R.string.no_end_date)
        binding.endDateLayout.txtEndDate.setTextColor(
            if (state.endDate.isNullOrBlank()) {
                ContextCompat.getColor(requireContext(), R.color.add_form_text_secondary)
            } else {
                accentColor
            }
        )
        binding.endDateLayout.txtClearEndDate.visibility =
            if (selectedType == TaskType.DAILY && !state.endDate.isNullOrBlank()) View.VISIBLE else View.GONE

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

        binding.taskWeightPriorityLayout.txtPriority.text = getString(R.string.task_weight_prefix, state.weight.weight)
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
                "${binding.repeatLayout.txtRepeatSummary.text}${getString(R.string.gap_days_suffix)}"
        }

        updateIconAndColor(state.icon, state.color)

        binding.taskTrackingType.root.visibility =
            if (args.task != null && state.trackingType == TrackingType.BINARY) View.GONE else View.VISIBLE
        binding.taskTrackingType.typeSelectorContainer.visibility =
            if (args.task != null) View.GONE else View.VISIBLE

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
                if (args.task != null) getString(R.string.task_updated_toast) else getString(R.string.task_saved_toast),
                Toast.LENGTH_SHORT
            ).show()
            findNavController().popBackStack()
            viewModel.resetSaveState()
        }

        captureInitialSnapshotIfNeeded(state)
    }

    // ── Tracking type helpers ─────────────────────────────────────────────────

    private fun setupTrackingTypeListeners() {
        btnBinary.setOnClickListener    { viewModel.updateTrackingType(TrackingType.BINARY) }
        btnCount.setOnClickListener     { viewModel.updateTrackingType(TrackingType.COUNT) }
        btnTimer.setOnClickListener     { viewModel.updateTrackingType(TrackingType.TIMER) }
        btnChecklist.setOnClickListener { viewModel.updateTrackingType(TrackingType.CHECKLIST) }

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
        val activeColor   = accentColor
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.add_form_muted_surface)
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
        activeBtn.setTextColor(onAccentTextColor(activeColor))
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
            if (isPaused) getString(R.string.resume_action) else getString(R.string.pause_action)
        binding.deletePauseLayout.ivPause.setImageResource(
            if (isPaused) android.R.drawable.ic_media_play else R.drawable.ic_pause
        )
        binding.deletePauseLayout.ivPause.contentDescription =
            if (isPaused) getString(R.string.resume_action) else getString(R.string.pause_action)
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
        binding.endDateLayout.endDateRow.setOnClickListener {
            if (selectedType == TaskType.DAILY) {
                openEndDatePicker()
            }
        }
        binding.endDateLayout.txtClearEndDate.setOnClickListener {
            viewModel.updateEndDate(null)
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
            if (isChecked) ensureReminderPermissionThenEnable() else viewModel.updateReminder(null, false)
        }

        binding.scheduleLayout.scheduleRow.setOnClickListener {
            if (binding.scheduleLayout.switchSchedule.isChecked)
                openTimePicker(tag = "schedule") { time -> viewModel.updateSchedule(time, true) }
        }

        binding.reminderLayoutMain.reminderBody.setOnClickListener {
            if (binding.reminderLayoutMain.switchReminder.isChecked)
                openTimePicker(tag = "reminder") { time -> viewModel.updateReminder(time, true) }
        }

        binding.reminderLayoutMain.ivBatteryWarning.setOnClickListener {
            TaskActionDialog(
                context = requireContext(),
                title = getString(R.string.battery_optimization_title),
                message = getString(R.string.battery_optimization_message),
                primaryLabel = getString(R.string.exact_alarm_permission_button),
                secondaryLabel = getString(R.string.cancel_button),
                iconRes = R.drawable.ic_warning,
                accentColor = accentColor,
                iconBubbleColor = accentBubbleColor(),
                onPrimaryAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        startActivity(intent)
                    }
                }
            ).show()
        }

        binding.addToListLayout.addToListRow.setOnClickListener {
            val currentIds = viewModel.selectedListIds.value ?: emptyList()
            TaskListBottomSheet(
                allListsLiveData = viewModel.allLists,
                preselectedIds = currentIds,
                accentColor = accentColor,
                onInsertList = { list -> viewModel.insertList(list) }
            ) { ids -> viewModel.updateSelectedLists(ids) }
                .show(parentFragmentManager, "TaskListBottomSheet")
        }

        binding.taskWeightPriorityLayout.priorityContainer.setOnClickListener {
            val currentWeight = viewModel.uiState.value?.weight ?: TaskWeight.VERY_LOW
            TaskPriorityBottomSheet(
                selectedWeight = currentWeight,
                accentColor = accentColor
            ) { weight ->
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
                hasUserSelectedTaskAppearance = true
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
                    title = getString(R.string.resume_daily_task_title),
                    message = getString(R.string.resume_daily_task_message),
                    primaryLabel = getString(R.string.resume_action),
                    iconRes = android.R.drawable.ic_media_play,
                    accentColor = accentColor,
                    iconBubbleColor = accentBubbleColor(),
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
                .setTitle(getString(R.string.schedule_time_dialog_title))
                .setMessage(getString(R.string.use_same_time_reminder_message, reminderTime))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.use_same_time_button, reminderTime)) { _, _ ->
                    viewModel.updateSchedule(reminderTime, true)
                }
                .setNegativeButton(getString(R.string.pick_different_time_button)) { _, _ ->
                    openTimePickerOrRevertSchedule()
                }
                .show()
            val primaryColor = accentColor
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
                .setTitle(getString(R.string.reminder_time_dialog_title))
                .setMessage(getString(R.string.use_same_time_schedule_message, scheduleTime))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.use_same_time_button, scheduleTime)) { _, _ ->
                    viewModel.updateReminder(scheduleTime, true)
                }
                .setNegativeButton(getString(R.string.pick_different_time_button)) { _, _ ->
                    openTimePickerOrRevertReminder()
                }
                .show()
            val primaryColor = accentColor
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(primaryColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(primaryColor)
        } else {
            openTimePickerOrRevertReminder()
        }
    }

    private fun ensureReminderPermissionThenEnable() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        // We check for exact alarm permission but don't block enabling if missing.
        // Instead, we'll show the warning icon which is already visible in the UI.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                // Optionally show the permission dialog once, but don't return.
                // showExactAlarmPermissionDialog()
            }
        }

        handleReminderEnabled()
    }

    private fun showExactAlarmPermissionDialog() {
        TaskActionDialog(
            context = requireContext(),
            title = getString(R.string.exact_alarm_permission_title),
            message = getString(R.string.exact_alarm_permission_message),
            primaryLabel = getString(R.string.exact_alarm_permission_button),
            secondaryLabel = getString(R.string.cancel_button),
            iconRes = R.drawable.ic_warning,
            accentColor = accentColor,
            iconBubbleColor = accentBubbleColor(),
            onPrimaryAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.fromParts("package", requireContext().packageName, null)
                    }
                    startActivity(intent)
                }
            },
            onSecondaryAction = {
                ignoreReminderToggle = true
                binding.reminderLayoutMain.switchReminder.isChecked = false
                ignoreReminderToggle = false
                viewModel.updateReminder(null, false)
            }
        ).show()
    }

    private fun openTimePickerOrRevertSchedule() {
        var timePicked = false
        openTimePicker(
            tag = "schedule",
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
            tag = "reminder",
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
        val currentDate = viewModel.uiState.value?.startDate ?: CommonMethods.getTodayDate()
        val cal = Calendar.getInstance()
        try {
            val parts = currentDate.split("-")
            if (parts.size == 3) {
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
        } catch (e: Exception) { /* use today */ }

        val constraints = CalendarConstraints.Builder()
            .setOpenAt(cal.timeInMillis)
            .build()

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.select_start_date))
            .setSelection(cal.timeInMillis)
            .setCalendarConstraints(constraints)
            .setPositiveButtonText(getString(R.string.picker_set_date))
            .setNegativeButtonText(getString(R.string.cancel_button))
            .setTheme(resolveDatePickerThemeRes())
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = selection
            val selectedDate = String.format(Locale.US, "%04d-%02d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            handleStartDateSelection(selectedDate)
        }
        datePicker.show(parentFragmentManager, "DATE_PICKER")
    }

    private fun openEndDatePicker() {
        val startDate = viewModel.uiState.value?.startDate ?: CommonMethods.getTodayDate()
        val currentEndDate = viewModel.uiState.value?.endDate
        val currentDate = when {
            currentEndDate.isNullOrBlank() -> startDate
            currentEndDate < startDate -> startDate
            else -> currentEndDate
        }
        val cal = Calendar.getInstance()
        try {
            val parts = currentDate.split("-")
            if (parts.size == 3) {
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
        } catch (_: Exception) { /* use current calendar */ }

        val startMillis = runCatching {
            val start = LocalDate.parse(
                viewModel.uiState.value?.startDate ?: CommonMethods.getTodayDate(),
                CommonMethods.sdf
            )
            start.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()

        val constraintsBuilder = CalendarConstraints.Builder()
            .setOpenAt(cal.timeInMillis)
        if (startMillis != null) {
            constraintsBuilder.setStart(startMillis)
            constraintsBuilder.setValidator(com.google.android.material.datepicker.DateValidatorPointForward.from(startMillis))
        }

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.select_end_date))
            .setSelection(cal.timeInMillis)
            .setCalendarConstraints(constraintsBuilder.build())
            .setPositiveButtonText(getString(R.string.picker_set_date))
            .setNegativeButtonText(getString(R.string.cancel_button))
            .setTheme(resolveDatePickerThemeRes())
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = selection
            val selectedDate = String.format(
                Locale.US,
                "%04d-%02d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            viewModel.updateEndDate(selectedDate)
        }
        datePicker.show(parentFragmentManager, "END_DATE_PICKER")
    }

    private fun openTimePicker(
        tag: String? = null,
        onDismiss: (() -> Unit)? = null,
        onSelected: (String) -> Unit
    ) {
        val currentState = viewModel.uiState.value
        val oldScheduleTime = currentState?.scheduleTime
        val oldReminderTime = currentState?.reminderTime
        val wasSynced = oldScheduleTime != null && oldScheduleTime == oldReminderTime

        val cal = Calendar.getInstance()

        val timePicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(cal.get(Calendar.HOUR_OF_DAY))
            .setMinute(cal.get(Calendar.MINUTE))
            .setTitleText(getString(R.string.select_time))
            .setPositiveButtonText(getString(R.string.picker_set_time))
            .setNegativeButtonText(getString(R.string.cancel_button))
            .setTheme(resolveTimePickerThemeRes())
            .build()

        var timePicked = false
        timePicker.addOnPositiveButtonClickListener {
            timePicked = true
            cal.set(Calendar.HOUR_OF_DAY, timePicker.hour)
            cal.set(Calendar.MINUTE, timePicker.minute)
            val newTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
            onSelected(newTime)

            // Sync logic
            if (wasSynced) {
                // If they were synced, and we are updating one, ask to update both
                if (tag == "schedule") {
                    // Changing schedule, ask for reminder
                    showSyncReminderTimeDialog(newTime)
                } else if (tag == "reminder") {
                    // Changing reminder, ask for schedule
                    showSyncScheduleTimeDialog(newTime)
                }
            }
        }

        if (onDismiss != null) {
            timePicker.addOnDismissListener {
                if (!timePicked) onDismiss()
            }
        }

        timePicker.show(parentFragmentManager, "TIME_PICKER")
    }

    private fun showSyncReminderTimeDialog(newTime: String) {
        TaskActionDialog(
            context = requireContext(),
            title = getString(R.string.reminder_time_dialog_title),
            message = getString(R.string.sync_reminder_time_message, newTime),
            primaryLabel = getString(R.string.update_both_button),
            secondaryLabel = getString(R.string.update_only_this_button),
            iconRes = R.drawable.ic_notification,
            accentColor = accentColor,
            iconBubbleColor = accentBubbleColor(),
            onPrimaryAction = {
                viewModel.updateReminder(newTime, true)
            }
        ).show()
    }

    private fun showSyncScheduleTimeDialog(newTime: String) {
        TaskActionDialog(
            context = requireContext(),
            title = getString(R.string.schedule_time_dialog_title),
            message = getString(R.string.sync_schedule_time_message, newTime),
            primaryLabel = getString(R.string.update_both_button),
            secondaryLabel = getString(R.string.update_only_this_button),
            iconRes = R.drawable.ic_notification, // Using notification icon for sync
            accentColor = accentColor,
            iconBubbleColor = accentBubbleColor(),
            onPrimaryAction = {
                viewModel.updateSchedule(newTime, true)
            }
        ).show()
    }

    @StyleRes
    private fun resolveDatePickerThemeRes(): Int {
        val context = requireContext()
        return when (accentColor) {
            ContextCompat.getColor(context, R.color.category_red) -> R.style.Theme_GrowDaily_MaterialDatePicker_Red
            ContextCompat.getColor(context, R.color.category_orange) -> R.style.Theme_GrowDaily_MaterialDatePicker_Orange
            ContextCompat.getColor(context, R.color.category_yellow) -> R.style.Theme_GrowDaily_MaterialDatePicker_Yellow
            ContextCompat.getColor(context, R.color.category_green) -> R.style.Theme_GrowDaily_MaterialDatePicker_Green
            ContextCompat.getColor(context, R.color.category_teal) -> R.style.Theme_GrowDaily_MaterialDatePicker_Teal
            ContextCompat.getColor(context, R.color.category_blue) -> R.style.Theme_GrowDaily_MaterialDatePicker_Blue
            ContextCompat.getColor(context, R.color.category_purple) -> R.style.Theme_GrowDaily_MaterialDatePicker_Purple
            else -> R.style.Theme_GrowDaily_MaterialDatePicker_DarkBlue
        }
    }

    @StyleRes
    private fun resolveTimePickerThemeRes(): Int {
        val context = requireContext()
        return when (accentColor) {
            ContextCompat.getColor(context, R.color.category_red) -> R.style.Theme_GrowDaily_MaterialTimePicker_Red
            ContextCompat.getColor(context, R.color.category_orange) -> R.style.Theme_GrowDaily_MaterialTimePicker_Orange
            ContextCompat.getColor(context, R.color.category_yellow) -> R.style.Theme_GrowDaily_MaterialTimePicker_Yellow
            ContextCompat.getColor(context, R.color.category_green) -> R.style.Theme_GrowDaily_MaterialTimePicker_Green
            ContextCompat.getColor(context, R.color.category_teal) -> R.style.Theme_GrowDaily_MaterialTimePicker_Teal
            ContextCompat.getColor(context, R.color.category_blue) -> R.style.Theme_GrowDaily_MaterialTimePicker_Blue
            ContextCompat.getColor(context, R.color.category_purple) -> R.style.Theme_GrowDaily_MaterialTimePicker_Purple
            else -> R.style.Theme_GrowDaily_MaterialTimePicker_DarkBlue
        }
    }

    private fun handleStartDateSelection(selectedDate: String) {
        if (args.task == null) {
            viewModel.updateStartDate(selectedDate)
            return
        }

        if (selectedType != TaskType.DAILY) {
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
            title = getString(R.string.use_new_start_date_title),
            message = getString(R.string.use_new_start_date_message, originalDisplayDate, newDisplayDate),
            primaryLabel = getString(R.string.keep_new_date_button, newDisplayDate),
            secondaryLabel = getString(R.string.keep_original_date_button, originalDisplayDate),
            iconRes = R.drawable.ic_warning,
            accentColor = accentColor,
            iconBubbleColor = accentBubbleColor(),
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
            title = getString(R.string.pause_daily_task_title),
            message = getString(R.string.pause_daily_task_message),
            iconRes = R.drawable.ic_pause,
            accentColor = accentColor,
            iconBubbleColor = accentBubbleColor(),
            onPauseFromTomorrow = {
                viewModel.updateTask(
                    task.copy(
                        taskRemovedDate = today.toString(),
                        inactiveReason = TaskInactiveReason.PAUSED
                    )
                )
                findNavController().popBackStack()
            },
            onPauseFromToday = {
                viewModel.updateTask(
                    task.copy(
                        taskRemovedDate = today.minusDays(1).toString(),
                        inactiveReason = TaskInactiveReason.PAUSED
                    )
                )
                findNavController().popBackStack()
            }
        ).show()
    }

    // ── List summary ──────────────────────────────────────────────────────────

    private fun updateListSummary() {
        val selectedIds = viewModel.selectedListIds.value ?: emptyList()
        val allLists    = viewModel.allLists.value ?: emptyList()
        if (selectedIds.isEmpty()) {
            binding.addToListLayout.txtListSummary.text = getString(R.string.list_summary_none)
            binding.addToListLayout.txtListSummary.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.add_form_text_secondary)
            )
            return
        }
        val firstList  = allLists.firstOrNull { it.id == selectedIds.first() }
        val extraCount = selectedIds.size - 1
        binding.addToListLayout.txtListSummary.text = if (extraCount > 0)
            getString(R.string.list_summary_multiple, firstList?.listTitle ?: getString(R.string.list_placeholder), extraCount)
        else
            firstList?.listTitle ?: getString(R.string.list_placeholder)
        binding.addToListLayout.txtListSummary.setTextColor(accentColor)
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private fun saveTask() {
        val state = viewModel.uiState.value ?: return

        if (state.title.isBlank()) {
            Toast.makeText(requireContext(), getString(R.string.error_enter_task_title), Toast.LENGTH_SHORT).show()
            return
        }
        if (state.isScheduled && state.scheduleTime == null) {
            Toast.makeText(requireContext(), getString(R.string.error_select_schedule_time), Toast.LENGTH_SHORT).show()
            return
        }
        if (state.isReminderEnabled && state.reminderTime == null) {
            Toast.makeText(requireContext(), getString(R.string.error_select_reminder_time), Toast.LENGTH_SHORT).show()
            return
        }

        performSave()
    }

    private fun attemptClose() {
        if (!hasUnsavedChanges()) {
            findNavController().popBackStack()
            return
        }

        TaskActionDialog(
            context = requireContext(),
            title = getString(R.string.discard_changes_title),
            message = if (args.task != null) {
                getString(R.string.discard_changes_message_edit)
            } else {
                getString(R.string.discard_changes_message_add)
            },
            primaryLabel = getString(R.string.discard_button),
            secondaryLabel = getString(R.string.keep_editing_button),
            iconRes = R.drawable.ic_warning,
            accentColor = accentColor,
            iconBubbleColor = accentBubbleColor(),
            onPrimaryAction = {
                findNavController().popBackStack()
            }
        ).show()
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

    private fun accentBubbleColor(): Int {
        return ColorUtils.setAlphaComponent(accentColor, (255 * 0.20f).toInt())
    }

    private fun onAccentTextColor(color: Int): Int {
        return ContextCompat.getColor(requireContext(), R.color.white)
    }

    private fun dpToPx(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
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

    private fun performSave() {
        viewModel.saveTask(
            isEdit = args.task != null,
            existingId = args.task?.id,
            taskType = selectedType,
            originalTask = args.task
        ) { success, error ->
            if (!success) {
                Toast.makeText(requireContext(), error ?: getString(R.string.save_failed_toast), Toast.LENGTH_SHORT).show()
                return@saveTask
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun maybeApplyAccentAsDefaultTaskColor(color: Int) {
        if (args.task != null || hasUserSelectedTaskAppearance) return

        val matchedTaskColor = TaskColor.entries.firstOrNull {
            ContextCompat.getColor(requireContext(), it.resId) == color
        } ?: return

        val currentState = viewModel.uiState.value ?: return
        if (currentState.color == matchedTaskColor.name) return

        viewModel.updateIconAndColor(
            icon = currentState.icon,
            color = matchedTaskColor.name
        )
    }
}
