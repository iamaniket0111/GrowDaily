package com.anitech.growdaily.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `checklist_progress_items` (
                `taskId` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `label` TEXT NOT NULL,
                `isDone` INTEGER NOT NULL,
                PRIMARY KEY(`taskId`, `date`, `label`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_checklist_progress_items_task_date`
            ON `checklist_progress_items` (`taskId`, `date`)
            """.trimIndent()
        )
    }
}
