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
 * Receives system events that invalidate scheduled alarms (boot, time change, etc.)
 * and rebuilds all reminders from the database.
 *
 * Uses [goAsync] to keep the BroadcastReceiver alive during the DB read, and a
 * [SupervisorJob] so a coroutine failure doesn't silently kill the scope.
 */
class ReminderRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive action=${intent.action}")

        val pendingResult = goAsync()

        // SupervisorJob: if the coroutine throws, the exception is logged
        // but pendingResult.finish() is still called in `finally`.
        CoroutineScope(SupervisorJob()).launch(Dispatchers.IO) {
            try {
                ReminderScheduler.syncFromDatabase(context.applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync reminders on ${intent.action}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ReminderRescheduleReceiver"
    }
}
