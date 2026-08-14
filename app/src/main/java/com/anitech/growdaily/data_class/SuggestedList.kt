package com.anitech.growdaily.data_class

data class SuggestedList(
    val listTitle: String = "",
    val taskCount: Int = 0,
    val isCreated: Boolean = false,
    val selectedTaskIds: List<String>? = null
)
