package com.anitech.growdaily.fragment.addtask

import android.app.Dialog
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
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
        val binding = host.binding
        binding.scheduleLayout.switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreScheduleToggle) return@setOnCheckedChangeListener
            if (isChecked) handleScheduleEnabled() else host.viewModel.updateSchedule(null, false)
        }

        binding.reminderLayoutMain.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreReminderToggle) return@setOnCheckedChangeListener
            if (isChecked) ensureReminderPermissionThenEnable() else host.viewModel.updateReminder(null, false)
        }

        binding.scheduleLayout.scheduleRow.setOnClickListener {
            if (binding.scheduleLayout.switchSchedule.isChecked) {
                datePickerCoordinator.openTimePicker(tag = "schedule") { time ->
                    host.viewModel.updateSchedule(time, true)
                }
            }
        }

        binding.reminderLayoutMain.reminderBody.setOnClickListener {
            if (binding.reminderLayoutMain.switchReminder.isChecked) {
                datePickerCoordinator.openTimePicker(tag = "reminder") { time ->
                    host.viewModel.updateReminder(time, true)
                }
            }
        }

        binding.reminderLayoutMain.ivBatteryWarning.setOnClickListener {
            when (ReminderPermissionHelper.primaryReliabilityIssue(host.hostContext())) {
                ReminderPermissionHelper.ReliabilityIssue.NOTIFICATIONS_DISABLED ->
                    showNotificationsDisabledDialog()
                ReminderPermissionHelper.ReliabilityIssue.NONE ->
                    showBatteryOptimizationDialog()
            }
        }
    }

    fun render(state: AddTaskUiState) {
        val binding = host.binding
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

        val placeholder = host.getHostString(R.string.time_placeholder)
        val placeholderColor = ContextCompat.getColor(host.hostContext(), R.color.add_form_text_secondary)
        binding.scheduleLayout.txtScheduleTime.bindAddTaskTimeValue(
            time = state.scheduleTime,
            placeholder = placeholder,
            accentColor = host.accentColor,
            placeholderColor = placeholderColor,
        )
        binding.reminderLayoutMain.txtReminderTime.bindAddTaskTimeValue(
            time = state.reminderTime,
            placeholder = placeholder,
            accentColor = host.accentColor,
            placeholderColor = placeholderColor,
        )
        binding.scheduleLayout.layoutScheduleTime.isVisible = state.isScheduled
        binding.reminderLayoutMain.layoutReminder.isVisible = state.isReminderEnabled
    }

    fun updateWarningVisibility() {
        if (!host.isHostViewSafe()) return
        host.binding.reminderLayoutMain.ivBatteryWarning.isVisible =
            ReminderPermissionHelper.primaryReliabilityIssue(host.hostContext()) !=
                ReminderPermissionHelper.ReliabilityIssue.NONE
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        if (granted) {
            proceedAfterNotificationAccessGranted()
        } else {
            disableReminderToggle()
            onNotificationPermissionDenied()
        }
    }

    fun showSyncReminderTimeDialog(newTime: String) {
        showOverlayDialog(
            TaskActionDialog(
                context = host.hostContext(),
                title = host.getHostString(R.string.reminder_time_dialog_title),
                message = host.getHostString(R.string.sync_reminder_time_message, newTime),
                primaryLabel = host.getHostString(R.string.update_both_button),
                secondaryLabel = host.getHostString(R.string.update_only_this_button),
                iconRes = R.drawable.ic_notification,
                accentColor = host.accentColor,
                iconBubbleColor = host.hostAccentBubbleColor(),
                onPrimaryAction = { host.viewModel.updateReminder(newTime, true) }
            )
        )
    }

    fun showSyncScheduleTimeDialog(newTime: String) {
        showOverlayDialog(
            TaskActionDialog(
                context = host.hostContext(),
                title = host.getHostString(R.string.schedule_time_dialog_title),
                message = host.getHostString(R.string.sync_schedule_time_message, newTime),
                primaryLabel = host.getHostString(R.string.update_both_button),
                secondaryLabel = host.getHostString(R.string.update_only_this_button),
                iconRes = R.drawable.ic_notification,
                accentColor = host.accentColor,
                iconBubbleColor = host.hostAccentBubbleColor(),
                onPrimaryAction = { host.viewModel.updateSchedule(newTime, true) }
            )
        )
    }

    fun handleSyncedTimeSelection(tag: String?, newTime: String) {
        when (tag) {
            "schedule" -> showSyncReminderTimeDialog(newTime)
            "reminder" -> showSyncScheduleTimeDialog(newTime)
        }
    }

    private fun handleScheduleEnabled() {
        val currentState = host.viewModel.uiState.value
        val reminderTime = currentState.reminderTime
        if (currentState.isReminderEnabled && reminderTime != null) {
            showLinkedTimeChoiceDialog(
                title = host.getHostString(R.string.schedule_time_dialog_title),
                message = host.getHostString(R.string.use_same_time_reminder_message, reminderTime),
                positiveLabel = host.getHostString(R.string.use_same_time_button, reminderTime),
                onUseLinkedTime = { host.viewModel.updateSchedule(reminderTime, true) },
                onPickDifferentTime = ::openTimePickerOrRevertSchedule
            )
        } else {
            openTimePickerOrRevertSchedule()
        }
    }

    private fun handleReminderEnabled() {
        val currentState = host.viewModel.uiState.value
        val scheduleTime = currentState.scheduleTime
        if (currentState.isScheduled && scheduleTime != null) {
            showLinkedTimeChoiceDialog(
                title = host.getHostString(R.string.reminder_time_dialog_title),
                message = host.getHostString(R.string.use_same_time_schedule_message, scheduleTime),
                positiveLabel = host.getHostString(R.string.use_same_time_button, scheduleTime),
                onUseLinkedTime = { host.viewModel.updateReminder(scheduleTime, true) },
                onPickDifferentTime = ::openTimePickerOrRevertReminder
            )
        } else {
            openTimePickerOrRevertReminder()
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
        datePickerCoordinator.openTimePicker(
            tag = "schedule",
            onCancel = { disableScheduleToggle() }
        ) { time ->
            host.viewModel.updateSchedule(time, true)
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
        host.binding.scheduleLayout.switchSchedule.isChecked = false
        ignoreScheduleToggle = false
        host.viewModel.updateSchedule(null, false)
    }

    private fun disableReminderToggle() {
        ignoreReminderToggle = true
        host.binding.reminderLayoutMain.switchReminder.isChecked = false
        ignoreReminderToggle = false
        host.viewModel.updateReminder(null, false)
    }
}
