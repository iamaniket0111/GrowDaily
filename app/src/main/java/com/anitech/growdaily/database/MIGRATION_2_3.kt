package com.anitech.growdaily.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `diary_entries` (
                `id` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `content` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}
