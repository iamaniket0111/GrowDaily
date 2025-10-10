package com.anitech.growdaily.data_class

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "mood_entries")
data class MoodHistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val emoji: String,
    val date: String  // Format: YYYY-MM-DD (e.g., 2025-08-09)
) : Parcelable

