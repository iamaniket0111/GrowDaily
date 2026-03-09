package com.anitech.growdaily.database

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class RepeatTaskViewModelFactory(
    private val repository: AppRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RepeatTaskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RepeatTaskViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}