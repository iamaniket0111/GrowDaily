package com.anitech.growdaily.reminder

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.MainActivity
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.database.AppDatabase
import com.anitech.growdaily.enum_class.TaskType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ReminderScheduler {

    private const val TAG = "ReminderScheduler"
    const val CHANNEL_ID = "task_reminders"
    private const val CHANNEL_NAME = "Task reminders"
    private const val CHANNEL_DESCRIPTION = "Reminders for scheduled tasks"
    const val ACTION_TASK_REMINDER = "com.anitech.growdaily.action.TASK_REMINDER"
    private const val EXTRA_TASK_ID = "extra_task_id"
    private const val EXTRA_TASK_TITLE = "extra_task_title"
    private const val EXTRA_TASK_NOTE = "extra_task_note"
    private const val EXTRA_OCCURRENCE_AT = "extra_occurrence_at"
    private const val MISSED_REMINDER_GRACE_MINUTES = 15L

    private const val RC_ALARM = 1001
    private const val RC_ACTIVITY = 1002
    private const val PREFS_REMINDER_STATE = "reminder_state"
    private const val KEY_LAST_DELIVERED_PREFIX = "last_delivered_"

    private val timeFormatter = java.time.format.DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("hh:mm a")
        .toFormatter(Locale.ENGLISH)

    /**
     * Mutex + backing set kept in sync under that mutex.
     * All reads/writes go through [syncMutex] so concurrent coroutine calls
     * on Dispatchers.IO cannot corrupt the set.
     */
    private val syncMutex = Mutex()
    private val trackedReminderTaskIds = mutableSetOf<String>()

    // -------------------------------------------------------------------------
    // Notification channel
    // -------------------------------------------------------------------------

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = CHANNEL_DESCRIPTION }
        )
    }

    // -------------------------------------------------------------------------
    // Public sync helpers
    // -------------------------------------------------------------------------

    suspend fun syncFromDatabase(context: Context) = withContext(Dispatchers.IO) {
        val tasks = AppDatabase.getDatabase(context).dailyTaskDao().getAllTasksNow()
        syncAll(context, tasks)
    }

    suspend fun syncAll(context: Context, tasks: List<TaskEntity>) = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val currentTaskIds = tasks.mapTo(mutableSetOf()) { it.id }

            // Cancel alarms for tasks that no longer exist
            val removedTaskIds = trackedReminderTaskIds - currentTaskIds
            removedTaskIds.forEach { cancelTaskReminder(context, it) }

            // Schedule or cancel per task
            tasks.forEach { task ->
                if (task.reminderEnabled && !task.reminderTime.isNullOrBlank()) {
                    scheduleTaskReminder(context, task)
                } else {
                    cancelTaskReminder(context, task.id)
                }
            }

            trackedReminderTaskIds.clear()
            trackedReminderTaskIds.addAll(currentTaskIds)
        }
    }

    suspend fun rescheduleTaskById(context: Context, taskId: String) = withContext(Dispatchers.IO) {
        val task = AppDatabase.getDatabase(context).dailyTaskDao().getTaskByIdNow(taskId)
        if (task == null || !task.reminderEnabled || task.reminderTime.isNullOrBlank()) {
            cancelTaskReminder(context, taskId)
        } else {
            scheduleTaskReminder(context, task)
        }
    }

    // -------------------------------------------------------------------------
    // Alarm scheduling / cancellation
    // -------------------------------------------------------------------------

    fun cancelTaskReminder(context: Context, taskId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(reminderPendingIntent(context, taskId, null, null, null))
        alarmManager.cancel(legacyReminderPendingIntent(context, taskId))
    }

    private fun scheduleTaskReminder(context: Context, task: TaskEntity) {
        val nextTrigger = computeNextTrigger(context, task) ?: run {
            cancelTaskReminder(context, task.id)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(legacyReminderPendingIntent(context, task.id))
        val pendingIntent = reminderPendingIntent(
            context = context,
            taskId = task.id,
            title = task.title,
            note = task.note,
            occurrenceAtMillis = nextTrigger.occurrenceAtMillis
        )

        scheduleInexact(alarmManager, nextTrigger.triggerAtMillis, pendingIntent)
        Log.d(TAG, "Alarm set for task=${task.id}")
    }

    private fun scheduleInexact(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------

    fun showNotification(context: Context, taskId: String, title: String, note: String?) {
        if (!ReminderPermissionHelper.canPostNotifications(context)) {
            Log.d(TAG, "Notifications disabled — not showing notification for taskId=$taskId")
            return
        }
        createNotificationChannel(context)

        val body = if (!note.isNullOrBlank()) note else "It's time for $title"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_trophy)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(activityPendingIntent(context, taskId))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_trophy)
                    .setContentTitle("Task reminder")
                    .setContentText("Open Grow Daily to view your reminder.")
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .build()
            )
            .build()

        NotificationManagerCompat.from(context).notify(taskId.hashCode(), notification)
    }

    // -------------------------------------------------------------------------
    // PendingIntent factories
    // Separate request-code namespaces prevent collisions between the two types.
    // -------------------------------------------------------------------------

    private fun reminderPendingIntent(
        context: Context,
        taskId: String,
        title: String?,
        note: String?,
        occurrenceAtMillis: Long?
    ): PendingIntent {
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION_TASK_REMINDER
            data = Uri.parse("growdaily://reminder/$taskId")
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, title)
            putExtra(EXTRA_TASK_NOTE, note)
            putExtra(EXTRA_OCCURRENCE_AT, occurrenceAtMillis)
        }
        return PendingIntent.getBroadcast(
            context,
            RC_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun activityPendingIntent(context: Context, taskId: String): PendingIntent {
        return PendingIntent.getActivity(
            context,
            // Same lower 16 bits, but in the activity namespace → no collision
            RC_ACTIVITY,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = Uri.parse("growdaily://task/$taskId")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun legacyReminderPendingIntent(context: Context, taskId: String): PendingIntent {
        val legacyIntent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION_TASK_REMINDER
            putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode() and 0xFFFF,
            legacyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // -------------------------------------------------------------------------
    // Next-trigger computation
    // -------------------------------------------------------------------------

    private fun computeNextTrigger(context: Context, task: TaskEntity): ReminderTrigger? {
        val reminderTime = task.reminderTime ?: return null
        val localTime = runCatching { LocalTime.parse(reminderTime, timeFormatter) }.getOrElse {
            Log.w(TAG, "Could not parse reminderTime='$reminderTime' for task=${task.id}")
            return null
        }

        val now = LocalDateTime.now()
        val zoneId = ZoneId.systemDefault()

        return when (task.taskType) {
            TaskType.DAY -> computeDayTaskTrigger(context, task, localTime, now, zoneId)
            TaskType.DAILY -> computeDailyTaskTrigger(context, task, localTime, now, zoneId)
            else -> null
        }
    }

    private fun computeDayTaskTrigger(
        context: Context,
        task: TaskEntity,
        localTime: LocalTime,
        now: LocalDateTime,
        zoneId: ZoneId
    ): ReminderTrigger? {
        val taskDate = runCatching { LocalDate.parse(task.taskAddedDate, CommonMethods.sdf) }
            .getOrElse { return null }
        val candidate = LocalDateTime.of(taskDate, localTime)
        val occurrenceAtMillis = candidate.atZone(zoneId).toInstant().toEpochMilli()
        return when {
            candidate.isAfter(now) -> ReminderTrigger(
                triggerAtMillis = occurrenceAtMillis,
                occurrenceAtMillis = occurrenceAtMillis
            )
            shouldCatchMissedReminder(candidate, now) &&
                !wasOccurrenceDelivered(context, task.id, occurrenceAtMillis) ->
                ReminderTrigger(
                    triggerAtMillis = now.plusSeconds(5).atZone(zoneId).toInstant().toEpochMilli(),
                    occurrenceAtMillis = occurrenceAtMillis
                )
            else -> null
        }
    }

    private fun computeDailyTaskTrigger(
        context: Context,
        task: TaskEntity,
        localTime: LocalTime,
        now: LocalDateTime,
        zoneId: ZoneId
    ): ReminderTrigger? {
        val startDate = runCatching { LocalDate.parse(task.taskAddedDate, CommonMethods.sdf) }
            .getOrElse { return null }

        val endDate = task.taskRemovedDate
            ?.let { runCatching { LocalDate.parse(it, CommonMethods.sdf) }.getOrNull() }
            ?: LocalDate.now().plusYears(1)

        var cursor = maxOf(LocalDate.now(), startDate)

        // Cap iterations defensively (max ~365 days)
        val iterationLimit = 400
        var iterations = 0

        while (!cursor.isAfter(endDate) && iterations < iterationLimit) {
            iterations++
            val candidateDate = cursor.format(CommonMethods.sdf)
            if (CommonMethods.isTaskActiveOnDate(task, candidateDate)) {
                val candidateDateTime = LocalDateTime.of(cursor, localTime)
                val occurrenceAtMillis = candidateDateTime.atZone(zoneId).toInstant().toEpochMilli()
                when {
                    candidateDateTime.isAfter(now) ->
                        return ReminderTrigger(
                            triggerAtMillis = occurrenceAtMillis,
                            occurrenceAtMillis = occurrenceAtMillis
                        )
                    shouldCatchMissedReminder(candidateDateTime, now) &&
                        !wasOccurrenceDelivered(context, task.id, occurrenceAtMillis) ->
                        return ReminderTrigger(
                            triggerAtMillis = now.plusSeconds(5).atZone(zoneId).toInstant().toEpochMilli(),
                            occurrenceAtMillis = occurrenceAtMillis
                        )
                }
            }
            cursor = cursor.plusDays(1)
        }

        if (iterations >= iterationLimit) {
            Log.w(TAG, "Hit iteration limit computing next trigger for DAILY task=${task.id}")
        }

        return null
    }

    private fun shouldCatchMissedReminder(candidate: LocalDateTime, now: LocalDateTime): Boolean {
        if (candidate.toLocalDate() != now.toLocalDate()) return false
        val delayMinutes = java.time.Duration.between(candidate, now).toMinutes()
        return delayMinutes in 0..MISSED_REMINDER_GRACE_MINUTES
    }

    // -------------------------------------------------------------------------
    // Intent extras extraction (called from receivers)
    // -------------------------------------------------------------------------

    fun extractTaskId(intent: Intent): String? = intent.getStringExtra(EXTRA_TASK_ID)
    fun extractTaskTitle(intent: Intent): String? = intent.getStringExtra(EXTRA_TASK_TITLE)
    fun extractTaskNote(intent: Intent): String? = intent.getStringExtra(EXTRA_TASK_NOTE)
    fun extractOccurrenceAt(intent: Intent): Long? {
        val occurrenceAt = intent.getLongExtra(EXTRA_OCCURRENCE_AT, Long.MIN_VALUE)
        return occurrenceAt.takeUnless { it == Long.MIN_VALUE }
    }

    fun markOccurrenceDelivered(context: Context, taskId: String, occurrenceAtMillis: Long?) {
        occurrenceAtMillis ?: return
        context.getSharedPreferences(PREFS_REMINDER_STATE, Context.MODE_PRIVATE)
            .edit()
            .putLong("$KEY_LAST_DELIVERED_PREFIX$taskId", occurrenceAtMillis)
            .apply()
    }

    private fun wasOccurrenceDelivered(context: Context, taskId: String, occurrenceAtMillis: Long): Boolean {
        val deliveredAt = context.getSharedPreferences(PREFS_REMINDER_STATE, Context.MODE_PRIVATE)
            .getLong("$KEY_LAST_DELIVERED_PREFIX$taskId", Long.MIN_VALUE)
        return deliveredAt == occurrenceAtMillis
    }

    private data class ReminderTrigger(
        val triggerAtMillis: Long,
        val occurrenceAtMillis: Long
    )
}
