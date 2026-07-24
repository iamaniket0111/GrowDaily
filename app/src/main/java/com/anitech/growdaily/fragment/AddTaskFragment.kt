package com.anitech.growdaily.fragment

import android.Manifest
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.anitech.growdaily.MainActivity
import com.anitech.growdaily.MyApp
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.AddTaskUiState
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.database.viewmodel.AddTaskViewModel
import com.anitech.growdaily.database.viewmodel.AddTaskViewModelFactory
import com.anitech.growdaily.databinding.FragmentAddTaskBinding
import com.anitech.growdaily.dialog.TaskActionDialog
import com.anitech.growdaily.enum_class.AddTaskUiEvent
import com.anitech.growdaily.enum_class.AddTaskValidationError
import com.anitech.growdaily.enum_class.TaskType
import com.anitech.growdaily.fragment.addtask.AddTaskAccentCoordinator
import com.anitech.growdaily.fragment.addtask.AddTaskDatePickerCoordinator
import com.anitech.growdaily.fragment.addtask.AddTaskEditActionsCoordinator
import com.anitech.growdaily.fragment.addtask.AddTaskMetadataCoordinator
import com.anitech.growdaily.fragment.addtask.AddTaskScheduleReminderCoordinator
import com.anitech.growdaily.fragment.addtask.AddTaskSectionHost
import com.anitech.growdaily.fragment.addtask.AddTaskTrackingSectionController
import com.anitech.growdaily.util.AddTaskSaveValidator
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class AddTaskFragment : Fragment(), AddTaskSectionHost {

    companion object {
        private const val SCROLL_STATE_KEY = "form_scroll_y"
    }

    private var _binding: FragmentAddTaskBinding? = null
    override val binding get() = _binding!!

    private val args: AddTaskFragmentArgs by navArgs()

    override val viewModel: AddTaskViewModel by viewModels {
        AddTaskViewModelFactory((requireActivity().application as MyApp).repository)
    }

    override var accentColor: Int = Color.BLUE
    override var taskType: TaskType = TaskType.DAILY
    override var originalStartDate: String = ""
    override val editingTask: TaskEntity? get() = args.task

    private lateinit var accentCoordinator: AddTaskAccentCoordinator
    private lateinit var datePickerCoordinator: AddTaskDatePickerCoordinator
    private lateinit var scheduleCoordinator: AddTaskScheduleReminderCoordinator
    private lateinit var trackingController: AddTaskTrackingSectionController
    private lateinit var metadataCoordinator: AddTaskMetadataCoordinator
    private lateinit var editActionsCoordinator: AddTaskEditActionsCoordinator

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            scheduleCoordinator.onNotificationPermissionResult(granted)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hostMainActivity()?.accentColor?.value?.let { accentColor = it }

        initCoordinators()
        setupTaskType()
        setupActionBar()
        setupHeaderListeners()
        setupObservers()
        setupDiscardHandling()
        setupMenu()

        accentCoordinator.observeAccentColor()
        trackingController.bind()
        trackingController.setupAccessibility()
        datePickerCoordinator.bindDateActions()
        scheduleCoordinator.bindListeners()
        metadataCoordinator.bind()
        metadataCoordinator.bindRepeatConfigResult()
        editActionsCoordinator.bind()

        loadTaskDataIfEditing()
        editActionsCoordinator.updateDeletePauseUi()
        scheduleCoordinator.updateWarningVisibility()
        scheduleDirtyBaselineCapture()

        savedInstanceState?.getInt(SCROLL_STATE_KEY)?.let { scrollY ->
            binding.formScrollView.post { binding.formScrollView.scrollTo(0, scrollY) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _binding?.formScrollView?.let { outState.putInt(SCROLL_STATE_KEY, it.scrollY) }
    }

    override fun onResume() {
        super.onResume()
        scheduleCoordinator.updateWarningVisibility()
    }

    override fun onDestroyView() {
        dismissAddTaskTimePicker()
        super.onDestroyView()
        _binding = null
    }

    override fun dismissAddTaskTimePicker() {
        if (::scheduleCoordinator.isInitialized) {
            scheduleCoordinator.dismissActiveDialogs()
        } else if (::datePickerCoordinator.isInitialized) {
            datePickerCoordinator.dismissTimePickerIfShowing()
        }
    }

    // ── AddTaskSectionHost ────────────────────────────────────────────────────

    override fun hostMainActivity(): MainActivity? = requireActivity() as? MainActivity
    override fun hostContext(): Context = requireContext()
    override fun hostResources(): Resources = resources
    override fun hostLifecycleOwner() = viewLifecycleOwner
    override fun hostParentFragmentManager(): FragmentManager = parentFragmentManager
    override fun getHostString(resId: Int): String = getString(resId)
    override fun getHostString(resId: Int, vararg formatArgs: Any): String = getString(resId, *formatArgs)
    override fun isHostViewSafe(): Boolean =
        _binding != null && viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    override fun hostAccentBubbleColor(): Int =
        ColorUtils.setAlphaComponent(accentColor, (255 * 0.20f).toInt())
    override fun hostNavigate(actionId: Int, bundle: Bundle?) = findNavController().navigate(actionId, bundle)
    override fun hostPopBackStack() {
        findNavController().popBackStack()
    }
    override fun hostDpToPx(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    override fun onCloseScreen() {
        hostPopBackStack()
    }

    override fun showHostSnackbar(message: String, actionLabel: String?, onAction: (() -> Unit)?) {
        if (!isHostViewSafe()) return
        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
        if (actionLabel != null && onAction != null) {
            snackbar.setAction(actionLabel) { onAction() }
        }
        snackbar.setActionTextColor(accentColor)
        snackbar.show()
    }

    override fun showHostToast(message: String) {
        if (!isHostViewSafe()) return
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun initCoordinators() {
        lateinit var accent: AddTaskAccentCoordinator

        accent = AddTaskAccentCoordinator(
            host = this,
            lifecycleOwner = viewLifecycleOwner,
            onAccentColorChanged = { },
            onTrackingTypeRefresh = { type -> trackingController.refreshHighlight(type) },
            onMaybeApplyDefaultTaskColor = { color -> accent.maybeApplyAccentAsDefaultTaskColor(color) }
        )
        accentCoordinator = accent
        trackingController = AddTaskTrackingSectionController(this, accentCoordinator)

        lateinit var schedule: AddTaskScheduleReminderCoordinator
        datePickerCoordinator = AddTaskDatePickerCoordinator(
            host = this,
            accentCoordinator = accentCoordinator,
            onSyncTimeChoice = { tag, newTime -> schedule.handleSyncedTimeSelection(tag, newTime) }
        )
        scheduleCoordinator = AddTaskScheduleReminderCoordinator(
            host = this,
            datePickerCoordinator = datePickerCoordinator,
            requestNotificationPermission = {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onNotificationPermissionDenied = { showNotificationPermissionDeniedFeedback() }
        ).also { schedule = it }

        metadataCoordinator = AddTaskMetadataCoordinator(this)
        editActionsCoordinator = AddTaskEditActionsCoordinator(this).apply {
            onSaveClicked = ::saveTask
        }
    }

    private fun setupActionBar() {
        if (parentFragment is TaskDetailFragment) return
        val title = if (editingTask != null) {
            getString(R.string.edit_task_title)
        } else {
            getString(R.string.add_task_title)
        }
        (requireActivity() as AppCompatActivity).supportActionBar?.title = title
    }

    private fun setupTaskType() {
        taskType = when {
            editingTask != null -> editingTask!!.taskType
            !args.taskType.isNullOrBlank() -> {
                runCatching { TaskType.valueOf(args.taskType!!) }.getOrDefault(TaskType.DAILY)
            }
            else -> TaskType.DAILY
        }
    }

    private fun setupHeaderListeners() {
        binding.titleNoteLayout.editTextTitle.doAfterTextChanged {
            viewModel.updateTitle(it.toString())
        }
        binding.titleNoteLayout.editTextNote.doAfterTextChanged {
            viewModel.updateNote(it.toString())
        }
    }

    private fun setupObservers() {
        viewModel.allLists.observe(viewLifecycleOwner) { metadataCoordinator.updateListSummary() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderFromState(state)
                        captureDirtyBaselineIfNeeded(state)
                    }
                }
                launch {
                    viewModel.selectedListIds.collect { metadataCoordinator.updateListSummary() }
                }
                launch {
                    viewModel.events.collect { handleUiEvent(it) }
                }
            }
        }
    }

    private fun renderFromState(state: AddTaskUiState) {
        binding.warningLayout.root.isVisible = editingTask != null && taskType != TaskType.DAY

        if (binding.titleNoteLayout.editTextTitle.text.toString() != state.title) {
            binding.titleNoteLayout.editTextTitle.setText(state.title)
        }
        if (binding.titleNoteLayout.editTextNote.text.toString() != state.note) {
            binding.titleNoteLayout.editTextNote.setText(state.note)
        }

        datePickerCoordinator.renderDateFields(state)
        scheduleCoordinator.render(state)
        metadataCoordinator.render(state)
        trackingController.render(state)

        binding.progressBarSave.isVisible = state.isLoading
        binding.loadingBlocker.isVisible = state.isLoading
        binding.buttonSave.isEnabled = !state.isLoading
        binding.formScrollView.isEnabled = !state.isLoading
        binding.buttonSave.alpha = if (state.isLoading) 0.6f else 1f
    }

    private fun setupMenu() {
        if (parentFragment is TaskDetailFragment) return
        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menu.clear()
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        android.R.id.home -> {
                            attemptClose()
                            true
                        }
                        else -> false
                    }
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )
    }

    private fun setupDiscardHandling() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val parent = parentFragment as? TaskDetailFragment
                    if (parent != null) {
                        if (hasUnsavedChanges()) {
                            attemptClose()
                        } else {
                            isEnabled = false
                            requireActivity().onBackPressedDispatcher.onBackPressed()
                            isEnabled = true
                        }
                    } else {
                        attemptClose()
                    }
                }
            }
        )
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private fun loadTaskDataIfEditing() {
        val task = editingTask
        if (task == null) {
            viewModel.ensureAddFormInitialized()
            if (taskType == TaskType.DAY) {
                viewModel.updateShowUntilCompleted(true)
            }
            return
        }
        originalStartDate = task.taskAddedDate
        viewModel.ensureEditTaskLoaded(task)
        viewModel.ensureListIdsLoaded(task.id) {
            if (!isHostViewSafe()) return@ensureListIdsLoaded
            metadataCoordinator.updateListSummary()
        }
    }

    private fun captureDirtyBaselineIfNeeded(state: AddTaskUiState) {
        val task = editingTask ?: return
        viewModel.dirtyStateTracker.tryCaptureEditState(state, task)
    }

    /**
     * Capture discard-changes baselines after accent color and other entry-time UI
     * updates have been applied (they run synchronously before this post).
     */
    private fun scheduleDirtyBaselineCapture() {
        binding.root.post {
            if (!isHostViewSafe()) return@post
            if (editingTask == null) {
                viewModel.captureAddModeDirtyBaseline()
            } else {
                editingTask?.let { task ->
                    viewModel.dirtyStateTracker.tryCaptureEditState(viewModel.uiState.value, task)
                }
            }
        }
    }

    // ── Events & save ─────────────────────────────────────────────────────────

    private fun handleUiEvent(event: AddTaskUiEvent) {
        if (!isHostViewSafe()) return
        when (event) {
            is AddTaskUiEvent.ShowValidationError -> {
                showHostSnackbar(messageForValidationError(event.error))
            }
            is AddTaskUiEvent.ShowMessage -> {
                val message = if (event.retrySave && event.message.isBlank()) {
                    getString(R.string.save_failed_toast)
                } else {
                    event.message
                }
                showHostSnackbar(
                    message = message,
                    actionLabel = event.actionLabel ?: if (event.retrySave) {
                        getString(R.string.retry_button)
                    } else {
                        null
                    },
                    onAction = if (event.retrySave) {
                        { saveTask() }
                    } else {
                        null
                    }
                )
            }
            is AddTaskUiEvent.Saved -> {
                showHostToast(
                    if (event.isEdit) getString(R.string.task_updated_toast)
                    else getString(R.string.task_saved_toast)
                )
            }
            AddTaskUiEvent.NavigateBack -> onCloseScreen()
        }
    }

    private fun saveTask() {
        val state = viewModel.uiState.value
        AddTaskSaveValidator.validate(state, taskType)?.let { error ->
            showHostSnackbar(messageForValidationError(error))
            return
        }
        viewModel.saveTask(
            isEdit = editingTask != null,
            existingId = editingTask?.id,
            taskType = taskType,
            originalTask = editingTask
        )
    }

    internal fun attemptClose() {
        if (!hasUnsavedChanges()) {
            onCloseScreen()
            return
        }
        TaskActionDialog(
            context = requireContext(),
            title = getString(R.string.discard_changes_title),
            message = if (editingTask != null) {
                getString(R.string.discard_changes_message_edit)
            } else {
                getString(R.string.discard_changes_message_add)
            },
            primaryLabel = getString(R.string.discard_button),
            secondaryLabel = getString(R.string.keep_editing_button),
            iconRes = R.drawable.ic_warning,
            accentColor = accentColor,
            iconBubbleColor = hostAccentBubbleColor(),
            onPrimaryAction = { onCloseScreen() }
        ).show()
    }

    private fun hasUnsavedChanges(): Boolean {
        return viewModel.dirtyStateTracker.hasUnsavedChanges(
            currentState = viewModel.uiState.value,
            currentListIds = viewModel.selectedListIds.value
        )
    }

    private fun showNotificationPermissionDeniedFeedback() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            showHostToast(getString(R.string.notification_permission_needed))
            return
        }
        TaskActionDialog(
            context = requireContext(),
            title = getString(R.string.notification_permission_needed),
            message = getString(R.string.notification_permission_denied_message),
            primaryLabel = getString(R.string.open_notification_settings_button),
            secondaryLabel = getString(R.string.cancel_button),
            iconRes = R.drawable.ic_warning,
            accentColor = accentColor,
            iconBubbleColor = hostAccentBubbleColor(),
            onPrimaryAction = {
                if (!com.anitech.growdaily.reminder.ReminderPermissionHelper.openNotificationSettings(requireContext())) {
                    showHostToast(getString(R.string.settings_notifications_open_failed))
                }
            }
        ).show()
    }

    private fun messageForValidationError(error: AddTaskValidationError): String {
        return when (error) {
            AddTaskValidationError.TITLE_REQUIRED -> getString(R.string.error_enter_task_title)
            AddTaskValidationError.SCHEDULE_TIME_REQUIRED -> getString(R.string.error_select_schedule_time)
            AddTaskValidationError.REMINDER_TIME_REQUIRED -> getString(R.string.error_select_reminder_time)
            AddTaskValidationError.CHECKLIST_EMPTY -> getString(R.string.error_add_checklist_item)
            AddTaskValidationError.CHECKLIST_DUPLICATE -> getString(R.string.error_checklist_item_duplicate)
            AddTaskValidationError.END_DATE_BEFORE_START -> getString(R.string.error_end_date_before_start)
            AddTaskValidationError.REPEAT_WEEKDAYS_REQUIRED -> getString(R.string.error_select_weekday)
            AddTaskValidationError.REPEAT_MONTH_DAYS_REQUIRED -> getString(R.string.error_select_month_day)
        }
    }
}
