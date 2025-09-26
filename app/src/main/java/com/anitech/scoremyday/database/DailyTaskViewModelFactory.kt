package com.anitech.scoremyday

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.anitech.scoremyday.database.AppRepository
import com.anitech.scoremyday.database.AppViewModel

class DailyTaskViewModelFactory(private val repository: AppRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
