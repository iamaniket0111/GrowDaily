package com.anitech.growdaily.database

import androidx.room.TypeConverter
import com.anitech.growdaily.data_class.DateDataEntity
import com.anitech.growdaily.enum_class.TaskType
import com.anitech.growdaily.enum_class.TaskWeight
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

    @TypeConverter
    fun fromDateDataList(list: List<DateDataEntity>): String {
        return gson.toJson(list)
    }

    @TypeConverter
    fun toDateDataList(json: String): List<DateDataEntity> {
        val type = object : TypeToken<List<DateDataEntity>>() {}.type
        return gson.fromJson(json, type)
    }

}
