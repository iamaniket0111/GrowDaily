package com.anitech.growdaily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.anitech.growdaily.database.AppRepository
import com.anitech.growdaily.database.AppViewModel

class DailyTaskViewModelFactory(private val repository: AppRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
