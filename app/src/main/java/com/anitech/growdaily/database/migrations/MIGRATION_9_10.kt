package com.anitech.growdaily.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `task_day_snapshots` (
                `taskId` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `completionCount` INTEGER NOT NULL,
                `progressPercent` INTEGER NOT NULL,
                `isCompleted` INTEGER NOT NULL,
                PRIMARY KEY(`taskId`, `date`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_task_day_snapshots_date`
            ON `task_day_snapshots` (`date`)
            """.trimIndent()
        )
    }
}
