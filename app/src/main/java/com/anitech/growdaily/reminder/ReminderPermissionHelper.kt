package com.anitech.growdaily.reminder

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Version-aware checks for reminder delivery:
 *
 * - **API 33+ (Android 13+)**: [android.Manifest.permission.POST_NOTIFICATIONS] runtime permission.
 * - **API 24–32**: No runtime permission; user can still disable app notifications in system settings.
 * - **API 31+ (Android 12+)**: Exact alarms may require [AlarmManager.canScheduleExactAlarms].
 * - **API ≤30**: Exact alarms allowed without special permission (still subject to Doze).
 */
object ReminderPermissionHelper {

    enum class ReliabilityIssue {
        NONE,
        NOTIFICATIONS_DISABLED
    }

    /** True when the app must show the Android 13+ notification permission dialog. */
    fun needsPostNotificationRuntimeRequest(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    /**
     * Whether the app is allowed to post notifications on this device.
     * Use this before [NotificationManagerCompat.notify] and when enabling reminders in UI.
     */
    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        return true
    }

    fun primaryReliabilityIssue(context: Context): ReliabilityIssue {
        if (!canPostNotifications(context)) return ReliabilityIssue.NOTIFICATIONS_DISABLED
        return ReliabilityIssue.NONE
    }

    fun openNotificationSettings(context: Context): Boolean {
        val packageName = context.packageName
        val packageManager = context.packageManager

        val notificationIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            putExtra("app_package", packageName)
            putExtra("app_uid", context.applicationInfo.uid)
        }
        val fallbackIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )

        val target = when {
            notificationIntent.resolveActivity(packageManager) != null -> notificationIntent
            fallbackIntent.resolveActivity(packageManager) != null -> fallbackIntent
            else -> null
        } ?: return false

        return runCatching {
            context.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }
}
