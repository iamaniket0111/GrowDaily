package com.anitech.scoremyday.data_class

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "conditions")
data class ConditionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val conditionTitle: String,
    val altConditionTitle: String? = null,
    var sortOrder: Int
) : Parcelable
