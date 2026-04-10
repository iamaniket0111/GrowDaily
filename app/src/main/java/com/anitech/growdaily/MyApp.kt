package com.anitech.growdaily

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.anitech.growdaily.database.AppDatabase
import com.anitech.growdaily.database.repository.AppRepository
import com.anitech.growdaily.settings.ThemePreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MyApp : Application() {
    lateinit var repository: AppRepository
    lateinit var themePreferencesManager: ThemePreferencesManager
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        themePreferencesManager = ThemePreferencesManager(this)
        runBlocking {
            val preference = themePreferencesManager.themePreferenceFlow.first()
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
            database.taskDaySnapshotDao()
        )
        repository.startTaskDaySnapshotSync(appScope)
    }
}
