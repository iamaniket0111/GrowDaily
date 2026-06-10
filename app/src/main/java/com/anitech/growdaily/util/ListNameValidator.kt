package com.anitech.growdaily.util

import com.anitech.growdaily.data_class.ListEntity

object ListNameValidator {

    const val MAX_LENGTH = 30

    enum class Error {
        BLANK,
        DUPLICATE,
    }

    fun validate(
        rawName: String,
        existingLists: List<ListEntity>,
        excludeListId: String? = null,
    ): Error? {
        val trimmed = rawName.trim()
        if (trimmed.isBlank()) return Error.BLANK
        val duplicate = existingLists.any { existing ->
            existing.id != excludeListId &&
                existing.listTitle.equals(trimmed, ignoreCase = true)
        }
        if (duplicate) return Error.DUPLICATE
        return null
    }
}
