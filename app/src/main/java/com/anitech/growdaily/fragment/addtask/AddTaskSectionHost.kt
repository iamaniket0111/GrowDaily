package com.anitech.growdaily.fragment.addtask

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import com.anitech.growdaily.MainActivity
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.database.viewmodel.AddTaskViewModel
import com.anitech.growdaily.databinding.FragmentAddTaskBinding
import com.anitech.growdaily.enum_class.TaskType

/**
 * Shared dependencies for Add Task form section coordinators.
 */
internal interface AddTaskSectionHost {
    val binding: FragmentAddTaskBinding
    val viewModel: AddTaskViewModel
    val taskType: TaskType
    val editingTask: TaskEntity?
    var accentColor: Int
    var originalStartDate: String
    fun hostMainActivity(): MainActivity?
    fun hostContext(): Context
    fun hostResources(): Resources
    fun hostLifecycleOwner(): LifecycleOwner
    fun hostParentFragmentManager(): FragmentManager
    fun getHostString(@StringRes resId: Int): String
    fun getHostString(@StringRes resId: Int, vararg formatArgs: Any): String
    fun isHostViewSafe(): Boolean
    fun showHostSnackbar(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null)
    fun showHostToast(message: String)
    fun hostAccentBubbleColor(): Int
    fun hostNavigate(actionId: Int, bundle: android.os.Bundle? = null)
    fun hostPopBackStack()
    fun hostDpToPx(value: Int): Int
    fun onCloseScreen()
    fun dismissAddTaskTimePicker()
}
