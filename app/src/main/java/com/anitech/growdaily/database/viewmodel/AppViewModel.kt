package com.anitech.growdaily.database.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.database.repository.AppRepository
import kotlinx.coroutines.launch


class AppViewModel(private val repository: AppRepository) : ViewModel() {

    val allTasks: LiveData<List<TaskEntity>> =
        repository
            .getAllTasksFlow()
            .asLiveData()


    //====have to remove above=========


    fun updateManualOrder(orderedIds: List<String>) {
        viewModelScope.launch {
            orderedIds.forEachIndexed { index, taskId ->
                repository.updateTaskOrder(taskId, index)
            }
        }
    }

    //list
    fun insertList(list: ListEntity) {
        viewModelScope.launch {
            repository.insertList(list)
        }
    }

    val allLists: LiveData<List<ListEntity>> =
        repository.getAllLists()

    fun updateList(list: ListEntity) {
        viewModelScope.launch {
            repository.updateList(list)
        }
    }

    fun updateListOrder(lists: List<ListEntity>) {
        viewModelScope.launch {
            repository.updateListOrder(lists)
        }
    }


    fun getTaskIdsForList(
        listId: String,
        onResult: (List<String>) -> Unit
    ) {
        viewModelScope.launch {
            val ids = repository.getTaskIdsForList(listId)
            onResult(ids)
        }
    }


    fun saveTasksForList(listId: String, taskIds: List<String>) {
        viewModelScope.launch {
            repository.syncTasksForList(listId, taskIds)
        }
    }

    fun deleteList(list: ListEntity) {
        viewModelScope.launch {
            repository.deleteList(list)
        }
    }

}

