package com.anitech.growdaily.database

import androidx.room.TypeConverter
import com.anitech.growdaily.enum_class.RepeatType
import com.anitech.growdaily.enum_class.TaskType
import com.anitech.growdaily.enum_class.TaskWeight
import com.anitech.growdaily.enum_class.TrackingType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromTaskWeight(weight: TaskWeight): Int = weight.weight

    @TypeConverter
    fun toTaskWeight(value: Int): TaskWeight {
        return TaskWeight.entries.firstOrNull { it.weight == value } ?: TaskWeight.LOW
    }

    @TypeConverter
    fun fromTaskType(value: TaskType): String = value.name

    @TypeConverter
    fun toTaskType(value: String): TaskType = TaskType.valueOf(value)

    @TypeConverter
    fun fromRepeatType(value: RepeatType?): String? = value?.name

    @TypeConverter
    fun toRepeatType(value: String?): RepeatType? {
        if (value.isNullOrBlank()) return null
        return RepeatType.entries.firstOrNull { it.name == value }
    }

    @TypeConverter
    fun fromTrackingType(value: TrackingType): String = value.name

    @TypeConverter
    fun toTrackingType(value: String): TrackingType =
        TrackingType.entries.firstOrNull { it.name == value } ?: TrackingType.BINARY

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromIntList(value: List<Int>): String {
        return value.joinToString(",")
    }

    @TypeConverter
    fun toIntList(value: String): List<Int> {
        return if (value.isEmpty()) emptyList() else value.split(",").map { it.toInt() }
    }
}
