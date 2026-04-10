package com.anitech.growdaily.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `task_tracking_versions` ADD COLUMN `weightValue` INTEGER NOT NULL DEFAULT 1"
        )
        db.execSQL(
            """
            UPDATE `task_tracking_versions`
            SET `weightValue` = COALESCE(
                (SELECT `weight`
                 FROM `daily_tasks`
                 WHERE `daily_tasks`.`id` = `task_tracking_versions`.`taskId`),
                1
            )
            """.trimIndent()
        )
    }
}
