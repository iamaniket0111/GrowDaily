package com.anitech.growdaily

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.anitech.growdaily.database.AppDatabase
import com.anitech.growdaily.database.repository.AppRepository
import com.anitech.growdaily.reminder.ReminderScheduler
import com.anitech.growdaily.settings.ThemePreference
import com.anitech.growdaily.settings.ThemePreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class MyApp : Application() {
    companion object {
        private const val THEME_LOAD_TIMEOUT_MS = 500L
    }

    lateinit var repository: AppRepository
    lateinit var themePreferencesManager: ThemePreferencesManager
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("sqlcipher")
        themePreferencesManager = ThemePreferencesManager(this)

        runBlocking {
            val preference = runCatching {
                withTimeoutOrNull(THEME_LOAD_TIMEOUT_MS) {
                    themePreferencesManager.themePreferenceFlow.first()
                } ?: ThemePreference.SYSTEM
            }.getOrDefault(ThemePreference.SYSTEM)

            AppCompatDelegate.setDefaultNightMode(
                themePreferencesManager.mapToNightMode(preference)
            )
        }

        val database = AppDatabase.getDatabase(this)
        repository = AppRepository(
            database.dailyTaskDao(),
            database.listDao(),
            database.taskCompletionDao(),
            database.checklistProgressDao(),
            database.taskTrackingVersionDao(),
            database.taskDaySnapshotDao(),
            database.taskExtraDateDao(),
            database.untilCompleteChildDao()
        )
        ReminderScheduler.createNotificationChannel(this)
        
        appScope.launch {
            repository.startTaskDaySnapshotSync(this)
            repository.getAllTasksFlow().collectLatest { tasks ->
                ReminderScheduler.syncAll(this@MyApp, tasks)
            }
        }
    }
}
