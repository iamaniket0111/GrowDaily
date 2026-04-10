package com.anitech.growdaily.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // ── daily_tasks ───────────────────────────────────────────────────────

        // trackingType: default 'BINARY' for all existing tasks
        db.execSQL(
            "ALTER TABLE `daily_tasks` ADD COLUMN `trackingType` TEXT NOT NULL DEFAULT 'BINARY'"
        )

        // checklistItems: nullable JSON string, null for all existing tasks
        db.execSQL(
            "ALTER TABLE `daily_tasks` ADD COLUMN `checklistItems` TEXT"
        )

        // targetDurationSeconds: default 0 for all existing tasks
        db.execSQL(
            "ALTER TABLE `daily_tasks` ADD COLUMN `targetDurationSeconds` INTEGER NOT NULL DEFAULT 0"
        )

        // ── task_completions ──────────────────────────────────────────────────

        // durationSeconds: default 0 for all existing rows
        db.execSQL(
            "ALTER TABLE `task_completions` ADD COLUMN `durationSeconds` INTEGER NOT NULL DEFAULT 0"
        )

        // checklistJson: nullable, null for all existing rows
        db.execSQL(
            "ALTER TABLE `task_completions` ADD COLUMN `checklistJson` TEXT"
        )
    }
}
