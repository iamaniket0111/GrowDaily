package com.anitech.growdaily.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.anitech.growdaily.DailyTaskDao
import com.anitech.growdaily.data_class.ConditionEntity
import com.anitech.growdaily.data_class.DailyTask
import com.anitech.growdaily.data_class.DateItemEntity
import com.anitech.growdaily.data_class.DayNoteEntity
import com.anitech.growdaily.data_class.DiaryEntry
import com.anitech.growdaily.data_class.MoodHistoryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        DailyTask::class,
        DiaryEntry::class,
        DayNoteEntity::class,
        MoodHistoryItem::class,
        ConditionEntity::class,
        DateItemEntity::class
    ],
    version = 6,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyTaskDao(): DailyTaskDao
    abstract fun diaryEntryDao(): DiaryEntryDao
    abstract fun moodDao(): MoodHistoryDao
    abstract fun conditionDao(): ConditionDao
    abstract fun dateItemDao(): DateItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // 👇 predefined list
        private val predefinedConditions = listOf(
            "Work Day",
            "Holiday / Outing",
            "Health Issue",
            "Unexpected Situation",
            "Travel / Commute",
            "Family Time",
            "Social Event",
            "Laziness / Procrastination",
            "No Resources / Tools Available",
            "Other Priorities"
        )

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "task_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_4_5, MIGRATION_5_6)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                INSTANCE?.conditionDao()?.insertAll(
                                    predefinedConditions.mapIndexed { index, title ->
                                        ConditionEntity(
                                            conditionTitle = title,
                                            altConditionTitle = null,
                                            sortOrder = index
                                        )
                                    }
                                )
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

    }
}
