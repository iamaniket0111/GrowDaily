package com.anitech.growdaily.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `checklist_versions` (
                `taskId` TEXT NOT NULL,
                `effectiveFromDate` TEXT NOT NULL,
                `checklistItemsJson` TEXT NOT NULL,
                PRIMARY KEY(`taskId`, `effectiveFromDate`)
            )
            """.trimIndent()
        )
    }
}
