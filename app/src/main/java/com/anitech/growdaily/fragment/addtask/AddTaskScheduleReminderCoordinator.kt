package com.anitech.growdaily.fragment.addtask

import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.provider.Settings
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.AddTaskUiState
import com.anitech.growdaily.dialog.TaskActionDialog
import com.anitech.growdaily.reminder.ReminderPermissionHelper

internal class AddTaskScheduleReminderCoordinator(
    private val host: AddTaskSectionHost,
    private val datePickerCoordinator: AddTaskDatePickerCoordinator,
    private val requestNotificationPermission: () -> Unit,
    private val onNotificationPermissionDenied: () -> Unit,
) {
    private var ignoreScheduleToggle = false
    private var ignoreReminderToggle = false
    private var activeOverlayDialog: Dialog? = null

    fun dismissActiveDialogs() {
        activeOverlayDialog?.dismiss()
        activeOverlayDialog = null
        datePickerCoordinator.dismissTimePickerIfShowing()
    }

    fun bindListeners() {
        val binding = host.binding.combinedScheduleLayout
        binding.switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreScheduleToggle) return@setOnCheckedChangeListener
            val currentTime = host.viewModel.uiState.value.scheduleTime
            if (isChecked) handleScheduleEnabled() else host.viewModel.updateSchedule(currentTime, false)
        }

        binding.blockStartTime.setOnClickListener {
            val currentState = host.viewModel.uiState.value
            val oldStartTime = currentState.scheduleTime
            val oldReminderTime = currentState.reminderTime
            val oldEndTime = currentState.endTime

            datePickerCoordinator.openTimePicker(tag = "schedule", initialTime = oldStartTime) { newTime ->
                host.viewModel.updateSchedule(newTime, currentState.isScheduled)

                if (!oldStartTime.isNullOrBlank() && !oldEndTime.isNullOrBlank()) {
                    val oldStartMins = CommonMethods.timeToMinutes(oldStartTime)
                    val oldEndMins = CommonMethods.timeToMinutes(oldEndTime)
                    val newStartMins = CommonMethods.timeToMinutes(newTime)
                    if (oldStartMins != null && oldEndMins != null && newStartMins != null) {
                        var durationMins = oldEndMins - oldStartMins
                        if (durationMins < 0) durationMins += 1440
                        val newEndMins = (newStartMins + durationMins) % 1440
                        host.viewModel.updateEndTime(CommonMethods.minutesToTime(newEndMins))
                    }
                } else if (oldEndTime.isNullOrBlank()) {
                    val newStartMins = CommonMethods.timeToMinutes(newTime)
                    if (newStartMins != null) {
                        val defaultEndMins = (newStartMins + 15) % 1440
                        host.viewModel.updateEndTime(CommonMethods.minutesToTime(defaultEndMins))
                    }
                }

                if (currentState.isReminderEnabled && !oldReminderTime.isNullOrBlank()) {
                    val oldStartMins = CommonMethods.timeToMinutes(oldStartTime)
                    val oldRemMins = CommonMethods.timeToMinutes(oldReminderTime)
                    val newStartMins = CommonMethods.timeToMinutes(newTime)

                    if (newStartMins != null) {
                        if (oldStartMins != null && oldRemMins != null) {
                            val offsetMins = (oldStartMins - oldRemMins + 1440) % 1440
                            val newRemMins = (newStartMins - offsetMins + 1440) % 1440
                            host.viewModel.updateReminder(CommonMethods.minutesToTime(newRemMins), true)
                        } else {
                            host.viewModel.updateReminder(newTime, true)
                        }
                    }
                }
            }
        }

        binding.blockEndTime.setOnClickListener {
            val currentState = host.viewModel.uiState.value
            val startTime = currentState.scheduleTime

            if (startTime.isNullOrBlank()) {
                android.widget.Toast.makeText(host.hostContext(), "Please set a Start Time first", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val fragmentManager = (host.hostContext() as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager
            if (fragmentManager != null) {
                val dialog = com.anitech.growdaily.dialog.TaskDurationDialog.newInstance(startTime, currentState.endTime)
                dialog.onEndTimeSelected = { selectedEndTime ->
                    host.viewModel.updateEndTime(selectedEndTime)
                }
                dialog.show(fragmentManager, "TaskDurationDialog")
            }
        }

        binding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreReminderToggle) return@setOnCheckedChangeListener
            if (isChecked) {
                openReminderDialogOrEnable()
            } else {
                host.viewModel.updateReminder(null, false)
            }
        }

        binding.reminderRowCombined.setOnClickListener {
            if (binding.switchReminder.isChecked) {
                openReminderDialogOrEnable()
            } else {
                binding.switchReminder.isChecked = true
            }
        }

        binding.ivBatteryWarning.setOnClickListener {
            when (ReminderPermissionHelper.primaryReliabilityIssue(host.hostContext())) {
                ReminderPermissionHelper.ReliabilityIssue.NOTIFICATIONS_DISABLED ->
                    showNotificationsDisabledDialog()
                ReminderPermissionHelper.ReliabilityIssue.NONE ->
                    showBatteryOptimizationDialog()
            }
        }
    }

    private fun openReminderDialogOrEnable() {
        val currentState = host.viewModel.uiState.value
        val startTime = currentState.scheduleTime
        if (startTime.isNullOrBlank()) {
            android.widget.Toast.makeText(host.hostContext(), "Please set a Start Time first", android.widget.Toast.LENGTH_SHORT).show()
            disableReminderToggle()
            return
        }

        val fragmentManager = (host.hostContext() as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager
        if (fragmentManager != null) {
            val wasAlreadyEnabled = currentState.isReminderEnabled
            val dialog = com.anitech.growdaily.dialog.TaskReminderDialog.newInstance(
                startTime = startTime,
                existingReminderTime = currentState.reminderTime,
                isReminderEnabled = currentState.isReminderEnabled
            )
            dialog.onReminderSelected = { remTime, isEnabled ->
                if (isEnabled && remTime != null) {
                    host.viewModel.updateReminder(remTime, true)
                } else {
                    disableReminderToggle()
                }
            }
            dialog.onCancelled = {
                if (!wasAlreadyEnabled) {
                    disableReminderToggle()
                }
            }
            dialog.show(fragmentManager, "TaskReminderDialog")
        }
    }

    private fun calculateDurationText(startTime: String?, endTime: String?): String {
        if (startTime.isNullOrBlank() || endTime.isNullOrBlank()) return ""
        val startMins = CommonMethods.timeToMinutes(startTime) ?: return ""
        val endMins = CommonMethods.timeToMinutes(endTime) ?: return ""

        var diffMins = endMins - startMins
        val isNextDay = diffMins < 0
        if (isNextDay) {
            diffMins += 1440
        }
        if (diffMins == 0) return ""

        val hours = diffMins / 60
        val mins = diffMins % 60

        val durationStr = when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }

        return if (isNextDay) "$durationStr (+1d)" else durationStr
    }

    fun render(state: AddTaskUiState) {
        val binding = host.binding.combinedScheduleLayout
        if (binding.switchSchedule.isChecked != state.isScheduled) {
            ignoreScheduleToggle = true
            binding.switchSchedule.isChecked = state.isScheduled
            ignoreScheduleToggle = false
        }
        if (binding.switchReminder.isChecked != state.isReminderEnabled) {
            ignoreReminderToggle = true
            binding.switchReminder.isChecked = state.isReminderEnabled
            ignoreReminderToggle = false
        }

        val placeholder = host.getHostString(R.string.time_placeholder)
        val placeholderColor = ContextCompat.getColor(host.hostContext(), R.color.add_form_text_secondary)
        
        binding.txtStartTimeValue.bindAddTaskTimeValue(
            time = state.scheduleTime,
            placeholder = placeholder,
            accentColor = host.accentColor,
            placeholderColor = placeholderColor,
        )

        if (!state.endTime.isNullOrBlank()) {
            val durationText = calculateDurationText(state.scheduleTime, state.endTime)
            binding.txtEndTimeValue.text = if (durationText.isNotEmpty()) {
                "${state.endTime} ($durationText)"
            } else {
                state.endTime
            }
            binding.txtEndTimeValue.setTextColor(host.accentColor)
        } else {
            binding.txtEndTimeValue.text = placeholder
            binding.txtEndTimeValue.setTextColor(placeholderColor)
        }

        if (state.isReminderEnabled && !state.reminderTime.isNullOrBlank()) {
            binding.txtReminderValue.text = state.reminderTime
            binding.txtReminderValue.setTextColor(host.accentColor)
            binding.txtReminderValue.visibility = View.VISIBLE
        } else {
            binding.txtReminderValue.visibility = View.GONE
        }

        updateWarningVisibility()
    }



    fun updateWarningVisibility() {
        if (!host.isHostViewSafe()) return
        val hasIssue = ReminderPermissionHelper.primaryReliabilityIssue(host.hostContext()) !=
            ReminderPermissionHelper.ReliabilityIssue.NONE
        host.binding.combinedScheduleLayout.ivBatteryWarning.isVisible =
            hasIssue && host.viewModel.uiState.value.isReminderEnabled
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        if (granted) {
            proceedAfterNotificationAccessGranted()
        } else {
            disableReminderToggle()
            onNotificationPermissionDenied()
        }
    }

    private fun handleScheduleEnabled() {
        val currentState = host.viewModel.uiState.value
        if (currentState.scheduleTime.isNullOrBlank()) {
            openTimePickerOrRevertSchedule()
        } else {
            host.viewModel.updateSchedule(currentState.scheduleTime, true)
        }
    }

    private fun handleReminderEnabled() {
        val currentState = host.viewModel.uiState.value
        if (currentState.reminderTime.isNullOrBlank()) {
            if (!currentState.scheduleTime.isNullOrBlank()) {
                host.viewModel.updateReminder(currentState.scheduleTime, true)
            } else {
                openTimePickerOrRevertReminder()
            }
        } else {
            host.viewModel.updateReminder(currentState.reminderTime, true)
        }
    }

    private fun ensureReminderPermissionThenEnable() {
        when {
            ReminderPermissionHelper.needsPostNotificationRuntimeRequest() &&
                !ReminderPermissionHelper.canPostNotifications(host.hostContext()) -> {
                requestNotificationPermission()
            }
            !ReminderPermissionHelper.canPostNotifications(host.hostContext()) -> {
                disableReminderToggle()
                showNotificationsDisabledDialog()
            }
            else -> proceedAfterNotificationAccessGranted()
        }
    }

    /**
     * Notifications are allowed. Reminder can be enabled; user may still see a warning
     * if exact alarms are off (alarms fall back to inexact scheduling).
     */
    private fun proceedAfterNotificationAccessGranted() {
        updateWarningVisibility()
        handleReminderEnabled()
    }

    private fun showNotificationsDisabledDialog() {
        TaskActionDialog(
            context = host.hostContext(),
            title = host.getHostString(R.string.notification_permission_needed),
            message = host.getHostString(R.string.notification_permission_denied_message),
            primaryLabel = host.getHostString(R.string.open_notification_settings_button),
            secondaryLabel = host.getHostString(R.string.cancel_button),
            iconRes = R.drawable.ic_warning,
            accentColor = host.accentColor,
            iconBubbleColor = host.hostAccentBubbleColor(),
            onPrimaryAction = {
                if (!ReminderPermissionHelper.openNotificationSettings(host.hostContext())) {
                    host.showHostToast(host.getHostString(R.string.settings_notifications_open_failed))
                }
            }
        ).show()
    }


    private fun showBatteryOptimizationDialog() {
        TaskActionDialog(
            context = host.hostContext(),
            title = host.getHostString(R.string.battery_optimization_title),
            message = host.getHostString(R.string.battery_optimization_message),
            primaryLabel = host.getHostString(R.string.exact_alarm_permission_button),
            secondaryLabel = host.getHostString(R.string.cancel_button),
            iconRes = R.drawable.ic_warning,
            accentColor = host.accentColor,
            iconBubbleColor = host.hostAccentBubbleColor(),
            onPrimaryAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    host.hostContext().startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    )
                }
            }
        ).show()
    }

    private fun showLinkedTimeChoiceDialog(
        title: String,
        message: String,
        positiveLabel: String,
        onUseLinkedTime: () -> Unit,
        onPickDifferentTime: () -> Unit
    ) {
        showOverlayDialog(
            TaskActionDialog(
                context = host.hostContext(),
                title = title,
                message = message,
                primaryLabel = positiveLabel,
                secondaryLabel = host.getHostString(R.string.pick_different_time_button),
                iconRes = R.drawable.ic_notification,
                accentColor = host.accentColor,
                iconBubbleColor = host.hostAccentBubbleColor(),
                onPrimaryAction = onUseLinkedTime,
                onSecondaryAction = onPickDifferentTime,
            ),
            cancelable = false,
            canceledOnTouchOutside = false,
        )
    }

    /**
     * Tracks schedule/reminder overlays so they can be dismissed when navigating away
     * (e.g. Repeat settings) without firing "pick different time" or reopening on return.
     */
    private fun showOverlayDialog(
        dialogBuilder: TaskActionDialog,
        cancelable: Boolean = true,
        canceledOnTouchOutside: Boolean = cancelable,
    ) {
        dismissActiveDialogs()
        val dialog = dialogBuilder.show(
            cancelable = cancelable,
            canceledOnTouchOutside = canceledOnTouchOutside,
        )
        activeOverlayDialog = dialog
        dialog.setOnDismissListener {
            if (activeOverlayDialog === dialog) {
                activeOverlayDialog = null
            }
        }
    }

    private fun openTimePickerOrRevertSchedule() {
        val currentState = host.viewModel.uiState.value
        val oldEndTime = currentState.endTime
        datePickerCoordinator.openTimePicker(
            tag = "schedule",
            onCancel = { disableScheduleToggle() }
        ) { time ->
            host.viewModel.updateSchedule(time, true)
            if (oldEndTime.isNullOrBlank()) {
                val startMins = CommonMethods.timeToMinutes(time)
                if (startMins != null) {
                    val defaultEndMins = (startMins + 15) % 1440
                    host.viewModel.updateEndTime(CommonMethods.minutesToTime(defaultEndMins))
                }
            }
        }
    }

    private fun openTimePickerOrRevertReminder() {
        datePickerCoordinator.openTimePicker(
            tag = "reminder",
            onCancel = { disableReminderToggle() }
        ) { time ->
            host.viewModel.updateReminder(time, true)
        }
    }

    private fun disableScheduleToggle() {
        ignoreScheduleToggle = true
        host.binding.combinedScheduleLayout.switchSchedule.isChecked = false
        ignoreScheduleToggle = false
        val currentTime = host.viewModel.uiState.value.scheduleTime
        host.viewModel.updateSchedule(currentTime, false)
    }

    private fun disableReminderToggle() {
        ignoreReminderToggle = true
        host.binding.combinedScheduleLayout.switchReminder.isChecked = false
        ignoreReminderToggle = false
        host.viewModel.updateReminder(null, false)
    }
}
