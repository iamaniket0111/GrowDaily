package com.anitech.growdaily.database.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.data_class.AiChatMessage
import com.anitech.growdaily.data_class.ChatSender
import com.anitech.growdaily.data_class.SuggestedTask
import com.anitech.growdaily.data_class.updateFromEntity
import com.anitech.growdaily.database.repository.AiChatRepository
import com.anitech.growdaily.database.repository.AppRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AiChatViewModel(
    private val aiChatRepository: AiChatRepository,
    private val appRepository: AppRepository
) : ViewModel() {

    private val _messages = MutableLiveData<List<AiChatMessage>>(emptyList())
    val messages: LiveData<List<AiChatMessage>> get() = _messages

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _apiKey = MutableLiveData<String>("")
    val apiKey: LiveData<String> get() = _apiKey

    private var cooldownUntilMillis: Long = 0L

    init {
        val welcomeMessage = AiChatMessage(
            sender = ChatSender.AI,
            text = "Hi there! I'm your GrowDaily AI Assistant. 🌿\n\nI can help you build positive habits, break down big goals into daily tasks, or suggest personalized routines. How can I support your daily growth today?"
        )
        _messages.value = listOf(welcomeMessage)
    }

    fun setApiKey(key: String) {
        _apiKey.value = key
    }

    fun sendMessage(userPrompt: String, userApiKey: String? = null) {
        val trimmedPrompt = userPrompt.trim()
        if (trimmedPrompt.isBlank()) return

        // 0. Check client-side rate limit cooldown
        if (System.currentTimeMillis() < cooldownUntilMillis) {
            val remainingSec = kotlin.math.ceil((cooldownUntilMillis - System.currentTimeMillis()) / 1000.0).toInt()
            val cooldownText = "Rate limit reached. Please wait ${if (remainingSec <= 1) "1 second" else "$remainingSec seconds"} before trying again. ⏳"
            val currentList = _messages.value.orEmpty().toMutableList()
            currentList.add(AiChatMessage(sender = ChatSender.USER, text = trimmedPrompt))
            currentList.add(AiChatMessage(sender = ChatSender.AI, text = cooldownText, isError = true))
            _messages.value = currentList
            return
        }

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
            // Build context summary with current time, date, task stats, and custom lists
            val nowFormatted = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' hh:mm a", Locale.getDefault()).format(Date())
            val tasks = appRepository.getAllTasksFlow().firstOrNull().orEmpty()
            val existingLists = appRepository.getAllListsSync()
            val listSummary = if (existingLists.isNotEmpty()) {
                "Available Custom Lists: " + existingLists.joinToString(", ") { "${it.listTitle} (ID: ${it.id})" }
            } else {
                "Available Custom Lists: None"
            }
            val contextSummary = """
                Current Device Time & Date: $nowFormatted
                Total Active Tasks Scheduled: ${tasks.size}
                $listSummary
            """.trimIndent()

            val effectiveKey = userApiKey ?: _apiKey.value
            val previousHistory = _messages.value.orEmpty()
            val (aiResponseText, suggestedTasks, suggestedLists) = aiChatRepository.generateResponse(
                userPrompt = trimmedPrompt,
                userContextSummary = contextSummary,
                customApiKey = effectiveKey,
                chatHistory = previousHistory
            )

            // Parse if a rate limit cooldown was returned to enforce client-side guard
            val retryMatch = Regex("""Please wait ([\d]+) second""", RegexOption.IGNORE_CASE).find(aiResponseText)
            if (retryMatch != null) {
                val sec = retryMatch.groupValues[1].toLongOrNull() ?: 60L
                cooldownUntilMillis = System.currentTimeMillis() + (sec + 2) * 1000L
            }

            // 3. Replace loading message with real response
            val updatedList = _messages.value.orEmpty().toMutableList()
            updatedList.removeLastOrNull() // remove loading placeholder

            val isError = aiResponseText.contains("Rate limit") || aiResponseText.contains("Invalid API Key") || aiResponseText.contains("Unable to connect")
            
            val adjustedLists = suggestedLists?.map { suggestedList ->
                val matchingCount = suggestedTasks?.count { task ->
                    task.listName.equals(suggestedList.listTitle, ignoreCase = true) ||
                    task.createNewList.equals(suggestedList.listTitle, ignoreCase = true)
                } ?: 0
                val finalCount = if (matchingCount > 0) matchingCount else suggestedList.taskCount
                suggestedList.copy(taskCount = finalCount)
            }

            val aiMsg = AiChatMessage(
                sender = ChatSender.AI,
                text = aiResponseText,
                suggestedTasks = suggestedTasks,
                suggestedLists = adjustedLists,
                isError = isError
            )
            updatedList.add(aiMsg)

            _messages.value = updatedList
            _isLoading.value = false
        }
    }

    private suspend fun linkTaskToTargetList(task: SuggestedTask, taskId: String, allowCreateNewList: Boolean = true) {
        val existingLists = appRepository.getAllListsSync()

        val targetListId = when {
            !task.targetListId.isNullOrBlank() -> task.targetListId
            !task.listName.isNullOrBlank() -> {
                existingLists.firstOrNull { it.listTitle.equals(task.listName, ignoreCase = true) }?.id
            }
            allowCreateNewList && !task.createNewList.isNullOrBlank() -> {
                val matchingList = existingLists.firstOrNull { it.listTitle.equals(task.createNewList, ignoreCase = true) }
                if (matchingList != null) {
                    matchingList.id
                } else {
                    val newId = java.util.UUID.randomUUID().toString()
                    val newOrder = existingLists.size + 1
                    val newList = com.anitech.growdaily.data_class.ListEntity(
                        id = newId,
                        listTitle = task.createNewList.trim(),
                        sortOrder = newOrder
                    )
                    appRepository.insertList(newList)
                    newId
                }
            }
            else -> null
        }

        if (!targetListId.isNullOrBlank()) {
            appRepository.addTaskToList(targetListId, taskId)
        }
    }

    fun addSuggestedTask(
        messageId: String,
        taskIndex: Int,
        createList: Boolean = true,
        selectedDate: String = CommonMethods.getTodayDate()
    ) {
        viewModelScope.launch {
            val currentList = _messages.value.orEmpty().toMutableList()
            val msgIndex = currentList.indexOfFirst { it.id == messageId }
            if (msgIndex == -1) return@launch

            val targetMsg = currentList[msgIndex]
            val tasks = targetMsg.suggestedTasks?.toMutableList() ?: return@launch
            if (taskIndex !in tasks.indices) return@launch

            val targetTask = tasks[taskIndex]
            if (targetTask.isAdded) return@launch

            // Insert into Room DB with next manual order
            val nextOrder = (appRepository.getMaxManualOrder() ?: 0) + 1
            val taskEntity = targetTask.toTaskEntity(selectedDate, manualOrder = nextOrder)
            appRepository.insertTask(taskEntity)
            linkTaskToTargetList(targetTask, taskEntity.id, allowCreateNewList = createList)

            // Mark as added in UI
            tasks[taskIndex] = targetTask.copy(isAdded = true)
            currentList[msgIndex] = targetMsg.copy(suggestedTasks = tasks)

            _messages.value = currentList
        }
    }

    fun addAllSuggestedTasks(messageId: String, selectedDate: String = CommonMethods.getTodayDate()) {
        viewModelScope.launch {
            val currentList = _messages.value.orEmpty().toMutableList()
            val msgIndex = currentList.indexOfFirst { it.id == messageId }
            if (msgIndex == -1) return@launch

            val targetMsg = currentList[msgIndex]
            val tasks = targetMsg.suggestedTasks?.toMutableList() ?: return@launch

            var currentOrder = (appRepository.getMaxManualOrder() ?: 0)
            var addedCount = 0

            for (i in tasks.indices) {
                val task = tasks[i]
                if (!task.isAdded) {
                    currentOrder++
                    val taskEntity = task.toTaskEntity(selectedDate, manualOrder = currentOrder)
                    appRepository.insertTask(taskEntity)
                    linkTaskToTargetList(task, taskEntity.id)
                    tasks[i] = task.copy(isAdded = true)
                    addedCount++
                }
            }

            if (addedCount > 0) {
                currentList[msgIndex] = targetMsg.copy(suggestedTasks = tasks)
                _messages.value = currentList
            }
        }
    }

    fun removeSuggestedTask(messageId: String, taskIndex: Int) {
        val currentList = _messages.value.orEmpty().toMutableList()
        val msgIndex = currentList.indexOfFirst { it.id == messageId }
        if (msgIndex == -1) return

        val targetMsg = currentList[msgIndex]
        val tasks = targetMsg.suggestedTasks?.toMutableList() ?: return
        if (taskIndex !in tasks.indices) return

        tasks.removeAt(taskIndex)
        currentList[msgIndex] = targetMsg.copy(suggestedTasks = if (tasks.isEmpty()) null else tasks)
        _messages.value = currentList
    }

    fun addSuggestedList(messageId: String, listIndex: Int) {
        viewModelScope.launch {
            val currentList = _messages.value.orEmpty().toMutableList()
            val msgIndex = currentList.indexOfFirst { it.id == messageId }
            if (msgIndex == -1) return@launch

            val targetMsg = currentList[msgIndex]
            val lists = targetMsg.suggestedLists?.toMutableList() ?: return@launch
            if (listIndex !in lists.indices) return@launch

            val targetSuggestedList = lists[listIndex]
            if (targetSuggestedList.isCreated) return@launch

            val existingLists = appRepository.getAllListsSync()
            val matchingList = existingLists.firstOrNull { it.listTitle.equals(targetSuggestedList.listTitle, ignoreCase = true) }

            val listId = if (matchingList == null) {
                val newId = java.util.UUID.randomUUID().toString()
                val newOrder = existingLists.size + 1
                val newList = com.anitech.growdaily.data_class.ListEntity(
                    id = newId,
                    listTitle = targetSuggestedList.listTitle.trim(),
                    sortOrder = newOrder
                )
                appRepository.insertList(newList)
                newId
            } else {
                matchingList.id
            }

            // Link draft selected task IDs if user selected tasks in AddListFragment!
            val draftTaskIds = targetSuggestedList.selectedTaskIds
            if (!draftTaskIds.isNullOrEmpty()) {
                for (taskId in draftTaskIds) {
                    appRepository.addTaskToList(listId, taskId)
                }
            }

            // Also insert and link any suggested tasks associated with this list!
            val tasks = targetMsg.suggestedTasks?.toMutableList()
            if (!tasks.isNullOrEmpty()) {
                var currentOrder = (appRepository.getMaxManualOrder() ?: 0)
                for (i in tasks.indices) {
                    val task = tasks[i]
                    val matchesList = task.listName.equals(targetSuggestedList.listTitle, ignoreCase = true) ||
                            task.createNewList.equals(targetSuggestedList.listTitle, ignoreCase = true)
                    if (matchesList && !task.isAdded) {
                        currentOrder++
                        val taskEntity = task.toTaskEntity(CommonMethods.getTodayDate(), manualOrder = currentOrder)
                        appRepository.insertTask(taskEntity)
                        appRepository.addTaskToList(listId, taskEntity.id)
                        tasks[i] = task.copy(isAdded = true)
                    }
                }
            }

            lists[listIndex] = targetSuggestedList.copy(isCreated = true)
            currentList[msgIndex] = targetMsg.copy(suggestedLists = lists, suggestedTasks = tasks)
            _messages.value = currentList
        }
    }

    fun updateSuggestedListDraft(
        messageId: String,
        listIndex: Int,
        newTitle: String,
        selectedTaskIds: List<String>
    ) {
        val currentList = _messages.value.orEmpty().toMutableList()
        val msgIndex = currentList.indexOfFirst { it.id == messageId }
        if (msgIndex == -1) return

        val targetMsg = currentList[msgIndex]
        val lists = targetMsg.suggestedLists?.toMutableList() ?: return
        if (listIndex !in lists.indices) return

        val oldList = lists[listIndex]
        lists[listIndex] = oldList.copy(
            listTitle = newTitle,
            taskCount = selectedTaskIds.size,
            selectedTaskIds = selectedTaskIds
        )

        currentList[msgIndex] = targetMsg.copy(suggestedLists = lists)
        _messages.value = currentList
    }

    fun updateSuggestedTaskDraft(
        messageId: String,
        taskIndex: Int,
        updatedTask: com.anitech.growdaily.data_class.TaskEntity
    ) {
        val currentList = _messages.value.orEmpty().toMutableList()
        val msgIndex = currentList.indexOfFirst { it.id == messageId }
        if (msgIndex == -1) return

        val targetMsg = currentList[msgIndex]
        val tasks = targetMsg.suggestedTasks?.toMutableList() ?: return
        if (taskIndex !in tasks.indices) return

        val oldTask = tasks[taskIndex]
        tasks[taskIndex] = oldTask.updateFromEntity(updatedTask)

        currentList[msgIndex] = targetMsg.copy(suggestedTasks = tasks)
        _messages.value = currentList
    }

    fun getSuggestedList(messageId: String, listIndex: Int): com.anitech.growdaily.data_class.SuggestedList? {
        val msg = _messages.value?.firstOrNull { it.id == messageId }
        val lists = msg?.suggestedLists
        return if (lists != null && listIndex in lists.indices) lists[listIndex] else null
    }

    fun updateSuggestedListName(messageId: String, listIndex: Int, newListName: String) {
        val currentList = _messages.value.orEmpty().toMutableList()
        val msgIndex = currentList.indexOfFirst { it.id == messageId }
        if (msgIndex == -1) return

        val targetMsg = currentList[msgIndex]
        val lists = targetMsg.suggestedLists?.toMutableList() ?: return
        if (listIndex !in lists.indices) return

        lists[listIndex] = lists[listIndex].copy(listTitle = newListName)
        currentList[msgIndex] = targetMsg.copy(suggestedLists = lists)
        _messages.value = currentList
    }

    fun removeSuggestedList(messageId: String, listIndex: Int) {
        val currentList = _messages.value.orEmpty().toMutableList()
        val msgIndex = currentList.indexOfFirst { it.id == messageId }
        if (msgIndex == -1) return

        val targetMsg = currentList[msgIndex]
        val lists = targetMsg.suggestedLists?.toMutableList() ?: return
        if (listIndex !in lists.indices) return

        lists.removeAt(listIndex)
        currentList[msgIndex] = targetMsg.copy(suggestedLists = if (lists.isEmpty()) null else lists)
        _messages.value = currentList
    }

    fun refreshSuggestedListsState() {
        viewModelScope.launch {
            val currentMessages = _messages.value.orEmpty().toMutableList()
            if (currentMessages.isEmpty()) return@launch

            val existingLists = appRepository.getAllListsSync()
            var changed = false

            for (msgIdx in currentMessages.indices) {
                val msg = currentMessages[msgIdx]
                val suggestedLists = msg.suggestedLists?.toMutableList() ?: continue

                for (listIdx in suggestedLists.indices) {
                    val sList = suggestedLists[listIdx]
                    val matchingList = existingLists.firstOrNull {
                        it.listTitle.equals(sList.listTitle, ignoreCase = true)
                    }

                    if (matchingList != null) {
                        val taskIds = appRepository.getTaskIdsForList(matchingList.id)
                        val realTaskCount = taskIds.size
                        if (!sList.isCreated || sList.taskCount != realTaskCount) {
                            suggestedLists[listIdx] = sList.copy(
                                isCreated = true,
                                taskCount = realTaskCount
                            )
                            changed = true
                        }
                    }
                }

                if (changed) {
                    currentMessages[msgIdx] = msg.copy(suggestedLists = suggestedLists)
                }
            }

            if (changed) {
                _messages.value = currentMessages
            }
        }
    }

    fun getOrCreateListEntityForNavigation(listTitle: String, onReady: (com.anitech.growdaily.data_class.ListEntity) -> Unit) {
        viewModelScope.launch {
            val existingLists = appRepository.getAllListsSync()
            val existingList = existingLists.firstOrNull { it.listTitle.equals(listTitle, ignoreCase = true) }
            val targetEntity = existingList ?: com.anitech.growdaily.data_class.ListEntity(
                id = java.util.UUID.randomUUID().toString(),
                listTitle = listTitle.trim(),
                sortOrder = (existingLists.maxOfOrNull { it.sortOrder } ?: -1) + 1
            )
            onReady(targetEntity)
        }
    }
}
