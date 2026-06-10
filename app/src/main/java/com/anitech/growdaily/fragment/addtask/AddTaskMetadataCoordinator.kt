package com.anitech.growdaily.fragment.addtask

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.clearFragmentResult
import androidx.core.view.isVisible
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.AddTaskUiState
import com.anitech.growdaily.dialog.IconAndColorDialog
import com.anitech.growdaily.dialog.TaskListBottomSheet
import com.anitech.growdaily.dialog.TaskPriorityBottomSheet
import com.anitech.growdaily.enum_class.RepeatType
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon
import com.anitech.growdaily.enum_class.TaskType
import com.anitech.growdaily.enum_class.TaskWeight
import com.anitech.growdaily.setSolidBackgroundColorCompat

internal class AddTaskMetadataCoordinator(
    private val host: AddTaskSectionHost,
) {

    fun bind() {
        bindMetadataActions()
        bindRepeatNavigation()
    }

    fun bindRepeatConfigResult() {
        val fragmentManager = host.hostParentFragmentManager()
        fragmentManager.setFragmentResultListener(
            "repeatResult",
            host.hostLifecycleOwner()
        ) { _, bundle ->
            val type = bundle.getString("repeatType")
                ?.let { runCatching { RepeatType.valueOf(it) }.getOrNull() }
                ?: RepeatType.DAILY
            val days = bundle.getIntegerArrayList("repeatDays")?.toList().orEmpty()
            host.viewModel.updateRepeatConfig(type, days)
            
            // Consume and clear the result so it is not processed again
            fragmentManager.clearFragmentResult("repeatResult")
        }
    }

    fun render(state: AddTaskUiState) {
        val binding = host.binding
        binding.taskWeightPriorityLayout.txtPriority.text =
            host.getHostString(R.string.task_weight_prefix, state.weight.weight)
        binding.repeatLayout.repeatRow.isVisible = host.taskType == TaskType.DAILY
        binding.untilCompleteLayout.untilCompleteRow.isVisible = host.taskType == TaskType.DAY
        binding.untilCompleteLayout.switchUntilComplete.isChecked = state.showUntilCompleted

        val today = CommonMethods.getTodayDate()
        val showRestart = host.editingTask != null &&
                host.taskType == TaskType.DAILY &&
                host.originalStartDate < today
        binding.restartProgressLayout.root.isVisible = showRestart

        val repeatSummary = when (state.repeatType) {
            RepeatType.DAYS_OF_WEEK -> {
                CommonMethods.formatRepeatSummary(
                    state.repeatType,
                    CommonMethods.serializeRepeatDays(state.repeatDays)
                )
            }
            RepeatType.DAYS_OF_MONTH -> {
                "Days of month"
            }
            else -> {
                "Every day"
            }
        }
        binding.repeatLayout.txtRepeatSummary.text = repeatSummary

        updateIconAndColor(state.icon, state.color)
    }

    fun updateListSummary() {
        val selectedIds = host.viewModel.selectedListIds.value
        val allLists = host.viewModel.allLists.value ?: emptyList()
        val listLayout = host.binding.addToListLayout
        val summaryView = listLayout.txtListSummary
        val extraView = listLayout.txtListSummaryExtra
        val secondaryColor = ContextCompat.getColor(host.hostContext(), R.color.add_form_text_secondary)

        if (selectedIds.isEmpty()) {
            summaryView.text = host.getHostString(R.string.list_summary_none)
            summaryView.setTextColor(secondaryColor)
            extraView.visibility = View.GONE
            return
        }

        val firstList = allLists.firstOrNull { it.id == selectedIds.first() }
        val extraCount = selectedIds.size - 1
        summaryView.text = firstList?.listTitle ?: host.getHostString(R.string.list_placeholder)
        summaryView.setTextColor(host.accentColor)
        if (extraCount > 0) {
            extraView.text = host.getHostString(R.string.list_summary_extra_count, extraCount)
            extraView.setTextColor(host.accentColor)
            extraView.visibility = View.VISIBLE
        } else {
            extraView.visibility = View.GONE
        }
    }

    private fun bindMetadataActions() {
        val binding = host.binding
        binding.addToListLayout.addToListRow.setOnClickListener {
            TaskListBottomSheet(
                allListsLiveData = host.viewModel.allLists,
                preselectedIds = host.viewModel.selectedListIds.value,
                accentColor = host.accentColor,
                onInsertList = { list -> host.viewModel.insertList(list) }
            ) { ids -> host.viewModel.updateSelectedLists(ids) }
                .show(host.hostParentFragmentManager(), "TaskListBottomSheet")
        }

        binding.taskWeightPriorityLayout.priorityContainer.setOnClickListener {
            val currentWeight = host.viewModel.uiState.value.weight
            TaskPriorityBottomSheet(
                selectedWeight = currentWeight,
                accentColor = host.accentColor
            ) { weight ->
                host.viewModel.updateWeight(weight)
            }.show(host.hostParentFragmentManager(), "TaskPriorityBottomSheet")
        }

        binding.imageProfile.setOnClickListener {
            val currentState = host.viewModel.uiState.value
            val dialog = IconAndColorDialog.newInstance(
                selectedIcon = currentState.icon,
                selectedColor = currentState.color
            )
            dialog.setOnImageSelectedListener { iconName, colorName ->
                host.viewModel.hasUserSelectedTaskAppearance = true
                host.viewModel.updateIconAndColor(iconName, colorName)
            }
            dialog.show(host.hostParentFragmentManager(), "IconAndColorDialog")
        }

        binding.restartProgressLayout.root.setOnClickListener {
            showRestartProgressWarningDialog()
        }
    }

    private fun bindRepeatNavigation() {
        host.binding.repeatLayout.repeatRow.setOnClickListener {
            host.dismissAddTaskTimePicker()
            val state = host.viewModel.uiState.value
            val bundle = Bundle().apply {
                putBoolean("isEditing", host.editingTask != null)
                putString("repeatType", state.repeatType.name)
                putIntegerArrayList("repeatDays", ArrayList(state.repeatDays))
            }
            host.hostNavigate(R.id.repeatConfigFragment, bundle)
        }
    }

    private fun updateIconAndColor(iconName: String, colorName: String) {
        runCatching {
            val icon = TaskIcon.valueOf(iconName)
            val color = TaskColor.valueOf(colorName)
            host.binding.imageProfile.setImageResource(icon.resId)
            host.binding.imageProfile.setSolidBackgroundColorCompat(
                ContextCompat.getColor(host.hostContext(), color.resId)
            )
        }
    }

    private fun showRestartProgressWarningDialog() {
        val originalTask = host.editingTask ?: return
        val context = host.hostContext()
        val bubbleColor = androidx.core.graphics.ColorUtils.setAlphaComponent(host.accentColor, 36)

        com.anitech.growdaily.dialog.TaskActionDialog(
            context = context,
            title = "Restart Task Progress?",
            message = "This will permanently delete all past completions and snapshots for this task, and reset its start date to today. This action cannot be undone.",
            primaryLabel = "Restart",
            secondaryLabel = "Cancel",
            iconRes = R.drawable.ic_warning,
            accentColor = host.accentColor,
            iconBubbleColor = bubbleColor,
            onPrimaryAction = {
                val today = CommonMethods.getTodayDate()
                host.originalStartDate = today
                host.viewModel.restartProgressDirectly(
                    existingId = originalTask.id,
                    taskType = host.taskType,
                    originalTask = originalTask
                )
            }
        ).show()
    }
}
