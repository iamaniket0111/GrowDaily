package com.anitech.growdaily.data_class

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
@Parcelize
data class DayLogEntity(
    val date: String,    // Format: YYYY-MM-DD (e.g., 2025-08-09)
    val title: String?,
    val content: String?,
    val emoji: String ="😐",
    val doneCount:Int,
    val pendingCount:Int,
    val dayScore:String,
    val diaryId:String?
): Parcelable