package com.anitech.growdaily.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE `daily_tasks`
            ADD COLUMN `showMissedOnGapDays` INTEGER NOT NULL DEFAULT 0
            """.trimIndent()
        )
    }
}
