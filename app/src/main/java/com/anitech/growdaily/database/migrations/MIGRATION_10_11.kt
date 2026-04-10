package com.anitech.growdaily.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE `daily_tasks`
            ADD COLUMN `showUntilCompleted` INTEGER NOT NULL DEFAULT 0
            """.trimIndent()
        )
    }
}
