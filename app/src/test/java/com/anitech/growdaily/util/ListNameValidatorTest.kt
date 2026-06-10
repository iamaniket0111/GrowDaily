package com.anitech.growdaily.util

import com.anitech.growdaily.data_class.ListEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListNameValidatorTest {

    @Test
    fun blankName_returnsBlank() {
        assertEquals(
            ListNameValidator.Error.BLANK,
            ListNameValidator.validate("   ", emptyList())
        )
    }

    @Test
    fun duplicateName_returnsDuplicate() {
        val existing = listOf(listEntity("1", "Work"))
        assertEquals(
            ListNameValidator.Error.DUPLICATE,
            ListNameValidator.validate("work", existing)
        )
    }

    @Test
    fun sameListWhenEditing_isAllowed() {
        val existing = listOf(listEntity("1", "Work"))
        assertNull(
            ListNameValidator.validate("Work", existing, excludeListId = "1")
        )
    }

    @Test
    fun validUniqueName_returnsNull() {
        val existing = listOf(listEntity("1", "Work"))
        assertNull(ListNameValidator.validate("Personal", existing))
    }

    private fun listEntity(id: String, title: String): ListEntity {
        return ListEntity(id = id, listTitle = title, sortOrder = 0)
    }
}
