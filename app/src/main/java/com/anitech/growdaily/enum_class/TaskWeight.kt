package com.anitech.growdaily.enum_class

enum class TaskWeight(val weight: Int) {
    VERY_LOW(1),
    LOW(2),
    HIGH(3),
    VERY_HIGH(4);

    companion object {

        fun fromWeight(value: Int): TaskWeight {
            return entries.find { it.weight == value } ?: VERY_LOW
        }

        fun displayText(value: Int): String {
            return "${value}/4"
        }
    }
}
