package com.anitech.growdaily.database.migrations

// In your database package, or within AppDatabase.kt

// In your database package, or within AppDatabase.kt
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create the 'mood_entries' table as expected by Room
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `mood_entries` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `date` TEXT NOT NULL,
                `emoji` TEXT NOT NULL
            )
        """.trimIndent())
        // If there are other columns in your MoodHistoryItem entity that were expected, add them here.
        // For example, if you had 'notes':
        // db.execSQL("ALTER TABLE `mood_entries` ADD COLUMN `notes` TEXT")
    }
}

