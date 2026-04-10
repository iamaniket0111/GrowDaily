package com.anitech.growdaily.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.anitech.growdaily.data_class.ChecklistVersionEntity
import com.anitech.growdaily.data_class.ChecklistProgressItemEntity
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.data_class.ListTaskCrossRef
import com.anitech.growdaily.data_class.TaskCompletionEntity
import com.anitech.growdaily.data_class.TaskDaySnapshotEntity
import com.anitech.growdaily.data_class.TaskOrderChangeLog
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.TaskTrackingVersionEntity
import com.anitech.growdaily.database.dao.ChecklistProgressDao
import com.anitech.growdaily.database.dao.ChecklistVersionDao
import com.anitech.growdaily.database.dao.ListDao
import com.anitech.growdaily.database.dao.OrderLogDao
import com.anitech.growdaily.database.dao.TaskCompletionDao
import com.anitech.growdaily.database.dao.TaskDao
import com.anitech.growdaily.database.dao.TaskDaySnapshotDao
import com.anitech.growdaily.database.dao.TaskTrackingVersionDao
import com.anitech.growdaily.database.migrations.MIGRATION_10_11
import com.anitech.growdaily.database.migrations.MIGRATION_11_12
import com.anitech.growdaily.database.migrations.MIGRATION_12_13
import com.anitech.growdaily.database.migrations.MIGRATION_13_14
import com.anitech.growdaily.database.migrations.MIGRATION_14_15
import com.anitech.growdaily.database.migrations.MIGRATION_15_16
import com.anitech.growdaily.database.migrations.MIGRATION_16_17
import com.anitech.growdaily.database.migrations.MIGRATION_1_2
import com.anitech.growdaily.database.migrations.MIGRATION_2_3
import com.anitech.growdaily.database.migrations.MIGRATION_4_5
import com.anitech.growdaily.database.migrations.MIGRATION_5_6
import com.anitech.growdaily.database.migrations.MIGRATION_6_7
import com.anitech.growdaily.database.migrations.MIGRATION_7_8
import com.anitech.growdaily.database.migrations.MIGRATION_8_9
import com.anitech.growdaily.database.migrations.MIGRATION_9_10

@Database(
    entities = [
        TaskEntity::class,
        ListEntity::class,
        TaskOrderChangeLog::class,
        ListTaskCrossRef::class,
        TaskCompletionEntity::class,
        ChecklistVersionEntity::class,
        ChecklistProgressItemEntity::class,
        TaskTrackingVersionEntity::class,
        TaskDaySnapshotEntity::class
    ],
    version = 17,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyTaskDao(): TaskDao
    abstract fun listDao(): ListDao
    abstract fun orderLogDao(): OrderLogDao
    abstract fun taskCompletionDao():TaskCompletionDao
    abstract fun checklistVersionDao(): ChecklistVersionDao
    abstract fun checklistProgressDao(): ChecklistProgressDao
    abstract fun taskTrackingVersionDao(): TaskTrackingVersionDao
    abstract fun taskDaySnapshotDao(): TaskDaySnapshotDao


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
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
                        MIGRATION_16_17
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }

    }
}
