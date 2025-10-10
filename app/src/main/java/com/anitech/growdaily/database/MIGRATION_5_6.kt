package com.anitech.growdaily.database


import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1️⃣ Create table if not exists
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS ConditionEntity (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                conditionTitle TEXT NOT NULL,
                altConditionTitle TEXT,
                sortOrder INTEGER NOT NULL
            )
        """.trimIndent())

        // 2️⃣ Insert predefined conditions
        val predefinedConditions = listOf(
            "Work Day",
            "Holiday / Outing",
            "Health Issue",
            "Unexpected Situation",
            "Travel / Commute",
            "Family Time",
            "Social Event",
            "Laziness / Procrastination",
            "No Resources / Tools Available",
            "Other Priorities"
        )

        predefinedConditions.forEachIndexed { index, title ->
            db.execSQL(
                "INSERT INTO ConditionEntity (conditionTitle, altConditionTitle, sortOrder) VALUES (?, ?, ?)",
                arrayOf(title, null, index)
            )
        }
    }
}


