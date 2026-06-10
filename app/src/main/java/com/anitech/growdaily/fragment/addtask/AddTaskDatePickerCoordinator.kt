package com.anitech.growdaily.fragment.addtask

import androidx.core.view.isVisible
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.AddTaskUiState
import com.anitech.growdaily.dialog.TaskActionDialog
import com.anitech.growdaily.enum_class.TaskType
import com.google.android.material.datepicker.CalendarConstraints
import androidx.fragment.app.DialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

internal class AddTaskDatePickerCoordinator(
    private val host: AddTaskSectionHost,
    private val accentCoordinator: AddTaskAccentCoordinator,
    private val onSyncTimeChoice: (tag: String?, newTime: String) -> Unit,
) {

    companion object {
        const val TIME_PICKER_TAG = "TIME_PICKER"
    }

    fun bindDateActions() {
        val binding = host.binding
        binding.startDateLayout.startDateRow.setOnClickListener { openStartDatePicker() }
        binding.endDateLayout.endDateRow.setOnClickListener {
            if (host.taskType == TaskType.DAILY) openEndDatePicker()
        }
        binding.endDateLayout.txtClearEndDate.setOnClickListener {
            host.viewModel.updateEndDate(null)
        }
        binding.untilCompleteLayout.untilCompleteRow.setOnClickListener {
            binding.untilCompleteLayout.switchUntilComplete.toggle()
        }
        binding.untilCompleteLayout.switchUntilComplete.setOnCheckedChangeListener { _, isChecked ->
            host.viewModel.updateShowUntilCompleted(isChecked)
        }
    }

    fun renderDateFields(state: AddTaskUiState) {
        val binding = host.binding
        binding.startDateLayout.txtStartDate.text = state.startDate
        binding.endDateLayout.endDateRow.isVisible = host.taskType == TaskType.DAILY
        binding.endDateLayout.txtEndDate.text =
            state.endDate ?: host.getHostString(R.string.no_end_date)
        binding.endDateLayout.txtEndDate.setTextColor(
            if (state.endDate.isNullOrBlank()) {
                androidx.core.content.ContextCompat.getColor(
                    host.hostContext(),
                    R.color.add_form_text_secondary
                )
            } else {
                host.accentColor
            }
        )
        binding.endDateLayout.txtClearEndDate.isVisible =
            host.taskType == TaskType.DAILY && !state.endDate.isNullOrBlank()
    }

    /**
     * @param onCancel Invoked only when the user taps Cancel — not when the dialog is
     * dismissed because another screen opened or the host view was destroyed.
     */
    fun openTimePicker(
        tag: String? = null,
        onCancel: (() -> Unit)? = null,
        onSelected: (String) -> Unit
    ) {
        dismissTimePickerIfShowing()
        val wasSynced = areScheduleAndReminderSynced()
        val cal = Calendar.getInstance()
        val timePicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(cal.get(Calendar.HOUR_OF_DAY))
            .setMinute(cal.get(Calendar.MINUTE))
            .setTitleText(host.getHostString(R.string.select_time))
            .setPositiveButtonText(host.getHostString(R.string.picker_set_time))
            .setNegativeButtonText(host.getHostString(R.string.cancel_button))
            .setTheme(accentCoordinator.resolveTimePickerThemeRes())
            .build()

        var timePicked = false
        timePicker.addOnPositiveButtonClickListener {
            timePicked = true
            val newTime = formatPickedTime(cal, timePicker.hour, timePicker.minute)
            onSelected(newTime)
            if (wasSynced) onSyncTimeChoice(tag, newTime)
        }

        if (onCancel != null) {
            timePicker.addOnNegativeButtonClickListener {
                if (!timePicked) onCancel()
            }
        }

        timePicker.show(host.hostParentFragmentManager(), TIME_PICKER_TAG)
    }

    fun dismissTimePickerIfShowing() {
        val picker = host.hostParentFragmentManager()
            .findFragmentByTag(TIME_PICKER_TAG) as? DialogFragment
        picker?.dismissAllowingStateLoss()
    }

    private fun openStartDatePicker() {
        val currentDate = host.viewModel.uiState.value.startDate
        val cal = calendarFromDateString(currentDate)
        val datePicker = buildDatePicker(
            title = host.getHostString(R.string.select_start_date),
            selectionMillis = cal.timeInMillis,
            constraints = CalendarConstraints.Builder()
                .setOpenAt(cal.timeInMillis)
                .build()
        )

        datePicker.addOnPositiveButtonClickListener { selection ->
            handleStartDateSelection(formatPickerDate(selection))
        }
        datePicker.show(host.hostParentFragmentManager(), "DATE_PICKER")
    }

    private fun openEndDatePicker() {
        val state = host.viewModel.uiState.value
        val startDate = state.startDate
        val currentEndDate = state.endDate
        val currentDate = when {
            currentEndDate.isNullOrBlank() -> startDate
            currentEndDate < startDate -> startDate
            else -> currentEndDate
        }
        val cal = calendarFromDateString(currentDate)

        val startMillis = runCatching {
            val start = LocalDate.parse(startDate, CommonMethods.sdf)
            start.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()

        val constraintsBuilder = CalendarConstraints.Builder().setOpenAt(cal.timeInMillis)
        if (startMillis != null) {
            constraintsBuilder.setStart(startMillis)
            constraintsBuilder.setValidator(
                com.google.android.material.datepicker.DateValidatorPointForward.from(startMillis)
            )
        }

        val datePicker = buildDatePicker(
            title = host.getHostString(R.string.select_end_date),
            selectionMillis = cal.timeInMillis,
            constraints = constraintsBuilder.build()
        )

        datePicker.addOnPositiveButtonClickListener { selection ->
            host.viewModel.updateEndDate(formatPickerDate(selection))
        }
        datePicker.show(host.hostParentFragmentManager(), "END_DATE_PICKER")
    }

    private fun handleStartDateSelection(selectedDate: String) {
        if (host.editingTask == null) {
            host.viewModel.updateStartDate(selectedDate)
            return
        }
        if (host.taskType != TaskType.DAILY) {
            host.viewModel.updateStartDate(selectedDate)
            return
        }

        val originalDate = runCatching { LocalDate.parse(host.originalStartDate) }.getOrNull()
        val newDate = runCatching { LocalDate.parse(selectedDate) }.getOrNull()

        if (originalDate != null && newDate != null && newDate.isAfter(originalDate)) {
            showStartDateSelectionConfirmation(selectedDate, originalDate, newDate)
        } else {
            host.viewModel.updateStartDate(selectedDate)
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
            context = host.hostContext(),
            title = host.getHostString(R.string.use_new_start_date_title),
            message = host.getHostString(
                R.string.use_new_start_date_message,
                originalDisplayDate,
                newDisplayDate
            ),
            primaryLabel = host.getHostString(R.string.keep_new_date_button, newDisplayDate),
            secondaryLabel = host.getHostString(R.string.keep_original_date_button, originalDisplayDate),
            iconRes = R.drawable.ic_warning,
            accentColor = host.accentColor,
            iconBubbleColor = host.hostAccentBubbleColor(),
            onPrimaryAction = { host.viewModel.updateStartDate(selectedDate) },
            onSecondaryAction = { host.viewModel.updateStartDate(host.originalStartDate) }
        ).show()
    }

    private fun buildDatePicker(
        title: String,
        selectionMillis: Long,
        constraints: CalendarConstraints
    ): MaterialDatePicker<Long> {
        return MaterialDatePicker.Builder.datePicker()
            .setTitleText(title)
            .setSelection(selectionMillis)
            .setCalendarConstraints(constraints)
            .setPositiveButtonText(host.getHostString(R.string.picker_set_date))
            .setNegativeButtonText(host.getHostString(R.string.cancel_button))
            .setTheme(accentCoordinator.resolveDatePickerThemeRes())
            .build()
    }

    private fun calendarFromDateString(date: String): Calendar {
        val cal = Calendar.getInstance()
        runCatching {
            val parts = date.split("-")
            if (parts.size == 3) {
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
        }
        return cal
    }

    private fun formatPickerDate(selectionMillis: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = selectionMillis
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun formatPickedTime(calendar: Calendar, hour: Int, minute: Int): String {
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(calendar.time)
    }

    private fun areScheduleAndReminderSynced(): Boolean {
        val currentState = host.viewModel.uiState.value
        val scheduleTime = currentState.scheduleTime
        val reminderTime = currentState.reminderTime
        return scheduleTime != null && scheduleTime == reminderTime
    }
}
