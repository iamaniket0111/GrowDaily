package com.anitech.growdaily.data_class

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "list_table")
data class ListEntity(
    @PrimaryKey val id: String, // UUID
    val listTitle: String,
    val sortOrder: Int
) : Parcelable

