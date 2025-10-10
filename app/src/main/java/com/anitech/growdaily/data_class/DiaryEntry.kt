package com.anitech.growdaily.data_class

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "diary_entries")
data class DiaryEntry(
    @PrimaryKey val id: String,
    val date: String,    // Format: YYYY-MM-DD (e.g., 2025-08-09)
    val title: String,
    val content: String?,
): Parcelable