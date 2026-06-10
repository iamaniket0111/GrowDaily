package com.anitech.growdaily.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fired by [AlarmManager] when a task reminder is due.
 *
 * Responsibilities:
 *  1. Show the notification (if permission granted).
 *  2. Reschedule the next occurrence of this task's reminder.
 *
 * Uses [goAsync] + [SupervisorJob] so the reschedule DB call completes
 * reliably even if the process would otherwise be reaped after onReceive returns.
 */
class TaskReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = ReminderScheduler.extractTaskId(intent) ?: run {
            Log.w(TAG, "Received reminder intent with no task ID — ignoring")
            return
        }

        val title = ReminderScheduler.extractTaskTitle(intent)
            .orEmpty()
            .ifBlank { "Task reminder" }
        val note = ReminderScheduler.extractTaskNote(intent)
        val occurrenceAt = ReminderScheduler.extractOccurrenceAt(intent)

        Log.d(TAG, "Reminder fired for taskId=$taskId")

        // Show notification only if POST_NOTIFICATIONS permission is granted
        // (mandatory check on Android 13+; always true on older versions).
        ReminderScheduler.markOccurrenceDelivered(context.applicationContext, taskId, occurrenceAt)

        if (ReminderPermissionHelper.canPostNotifications(context)) {
            ReminderScheduler.showNotification(context.applicationContext, taskId, title, note)
        } else {
            Log.d(TAG, "Notifications disabled — skipping notification for taskId=$taskId")
        }

        // Reschedule asynchronously; always runs regardless of notification permission.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob()).launch(Dispatchers.IO) {
            try {
                ReminderScheduler.rescheduleTaskById(context.applicationContext, taskId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reschedule taskId=$taskId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "TaskReminderReceiver"
    }
}
