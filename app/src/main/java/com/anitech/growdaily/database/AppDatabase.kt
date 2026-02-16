package com.anitech.growdaily.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.anitech.growdaily.TaskDao
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.DayNoteEntity
import com.anitech.growdaily.data_class.DiaryEntry
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.data_class.ListTaskCrossRef
import com.anitech.growdaily.data_class.MoodHistoryItem
import com.anitech.growdaily.data_class.TaskCompletionEntity
import com.anitech.growdaily.data_class.TaskOrderChangeLog

@Database(
    entities = [
        TaskEntity::class,
        DiaryEntry::class,
        DayNoteEntity::class,
        MoodHistoryItem::class,
        ListEntity::class,
        TaskOrderChangeLog::class,
        ListTaskCrossRef::class,
        TaskCompletionEntity::class
    ],
    version = 6,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyTaskDao(): TaskDao
    abstract fun diaryEntryDao(): DiaryEntryDao
    abstract fun moodDao(): MoodHistoryDao
    abstract fun listDao(): ListDao
    abstract fun orderLogDao(): OrderLogDao
    abstract fun taskCompletionDao():TaskCompletionDao


    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "task_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }

    }
}
