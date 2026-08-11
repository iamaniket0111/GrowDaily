package com.anitech.growdaily.database.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.data_class.AiChatMessage
import com.anitech.growdaily.data_class.ChatSender
import com.anitech.growdaily.data_class.SuggestedTask
import com.anitech.growdaily.database.repository.AiChatRepository
import com.anitech.growdaily.database.repository.AppRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class AiChatViewModel(
    private val appRepository: AppRepository,
    private val aiChatRepository: AiChatRepository = AiChatRepository()
) : ViewModel() {

    private val _messages = MutableLiveData<List<AiChatMessage>>(emptyList())
    val messages: LiveData<List<AiChatMessage>> get() = _messages

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _apiKey = MutableLiveData<String>("")
    val apiKey: LiveData<String> get() = _apiKey

    init {
        // Initial Welcome Message from GrowDaily AI
        val welcomeMessage = AiChatMessage(
            sender = ChatSender.AI,
            text = "Hi there! I'm your GrowDaily AI Assistant. 🌿\n\nI can help you build positive habits, break down big goals into daily tasks, or suggest personalized routines. How can I support your daily growth today?"
        )
        _messages.value = listOf(welcomeMessage)
    }

    fun setApiKey(key: String) {
        _apiKey.value = key
    }

    fun sendMessage(userText: String, userApiKey: String? = null) {
        val trimmedPrompt = userText.trim()
        if (trimmedPrompt.isBlank()) return

        val currentList = _messages.value.orEmpty().toMutableList()

        // 1. Add User Message
        val userMsg = AiChatMessage(sender = ChatSender.USER, text = trimmedPrompt)
        currentList.add(userMsg)

        // 2. Add Loading AI Message
        val loadingMsg = AiChatMessage(sender = ChatSender.AI, text = "", isLoading = true)
        currentList.add(loadingMsg)
        _messages.value = currentList
        _isLoading.value = true

        viewModelScope.launch {
            // Build context summary from current tasks in DB
            val tasks = appRepository.getAllTasksFlow().firstOrNull().orEmpty()
            val contextSummary = "User currently has ${tasks.size} active tasks scheduled in GrowDaily."

            val effectiveKey = userApiKey ?: _apiKey.value
            val (aiResponseText, suggestedTasks) = aiChatRepository.generateResponse(
                userPrompt = trimmedPrompt,
                userContextSummary = contextSummary,
                customApiKey = effectiveKey
            )

            // 3. Replace loading message with real response
            val updatedList = _messages.value.orEmpty().toMutableList()
            updatedList.removeLastOrNull() // remove loading placeholder

            val isError = aiResponseText.contains("Invalid API Key") || aiResponseText.contains("Unable to connect")
            val aiMsg = AiChatMessage(
                sender = ChatSender.AI,
                text = aiResponseText,
                suggestedTasks = suggestedTasks,
                isError = isError
            )
            updatedList.add(aiMsg)

            _messages.value = updatedList
            _isLoading.value = false
        }
    }

    fun addSuggestedTask(messageId: String, taskIndex: Int, selectedDate: String = CommonMethods.getTodayDate()) {
        viewModelScope.launch {
            val currentList = _messages.value.orEmpty().toMutableList()
            val msgIndex = currentList.indexOfFirst { it.id == messageId }
            if (msgIndex == -1) return@launch

            val targetMsg = currentList[msgIndex]
            val tasks = targetMsg.suggestedTasks?.toMutableList() ?: return@launch
            if (taskIndex !in tasks.indices) return@launch

            val targetTask = tasks[taskIndex]
            if (targetTask.isAdded) return@launch

            // Insert into Room DB
            val taskEntity = targetTask.toTaskEntity(selectedDate)
            appRepository.insertTask(taskEntity)

            // Mark as added in UI
            tasks[taskIndex] = targetTask.copy(isAdded = true)
            currentList[msgIndex] = targetMsg.copy(suggestedTasks = tasks)

            _messages.value = currentList
        }
    }
}
