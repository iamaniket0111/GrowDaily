package com.anitech.growdaily

import android.app.Application
import com.anitech.growdaily.database.AppDatabase
import com.anitech.growdaily.database.AppRepository

class MyApp : Application() {
    lateinit var repository: AppRepository

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getDatabase(this)
        repository = AppRepository(
            database.dailyTaskDao(),
            database.diaryEntryDao(),
            database.moodDao(),
            database.conditionDao(),
            database.dateItemDao(),
            database.orderLogDao()
        )
    }
}
