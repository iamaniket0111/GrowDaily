package com.anitech.growdaily.database.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.anitech.growdaily.database.repository.AppRepository

class ManageRepeatTasksViewModelFactory(
    private val repository: AppRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ManageRepeatTasksViewModel::class.java)) {
            return ManageRepeatTasksViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
