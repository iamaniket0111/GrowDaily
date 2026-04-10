package com.anitech.growdaily.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `task_tracking_versions` (
                `taskId` TEXT NOT NULL,
                `effectiveFromDate` TEXT NOT NULL,
                `dailyTargetCount` INTEGER NOT NULL,
                `targetDurationSeconds` INTEGER NOT NULL,
                `checklistItemsJson` TEXT,
                PRIMARY KEY(`taskId`, `effectiveFromDate`)
            )
            """.trimIndent()
        )
    }
}
