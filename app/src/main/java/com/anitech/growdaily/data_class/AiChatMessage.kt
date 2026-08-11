package com.anitech.growdaily.data_class

import java.util.UUID

enum class ChatSender {
    USER,
    AI,
    SYSTEM
}

data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: ChatSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val suggestedTasks: List<SuggestedTask>? = null,
    val isLoading: Boolean = false,
    val isError: Boolean = false
)
