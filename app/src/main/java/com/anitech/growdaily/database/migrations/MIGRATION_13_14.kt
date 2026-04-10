package com.anitech.growdaily.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE daily_tasks ADD COLUMN seriesId TEXT NOT NULL DEFAULT ''"
        )
        db.execSQL(
            "UPDATE daily_tasks SET seriesId = id WHERE seriesId = ''"
        )
    }
}
