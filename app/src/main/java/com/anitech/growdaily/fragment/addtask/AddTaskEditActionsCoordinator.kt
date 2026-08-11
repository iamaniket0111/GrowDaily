package com.anitech.growdaily.fragment.addtask

import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.dialog.DeleteTaskDialog
import com.anitech.growdaily.dialog.PauseOptionsDialog
import com.anitech.growdaily.dialog.TaskActionDialog
import com.anitech.growdaily.enum_class.TaskInactiveReason
import com.anitech.growdaily.enum_class.TaskType
import java.time.LocalDate

internal class AddTaskEditActionsCoordinator(
    private val host: AddTaskSectionHost,
) {

    fun bind() {
        host.binding.buttonSave.setOnClickListener { onSaveClicked() }
        bindDeletePauseActions()
    }

    var onSaveClicked: () -> Unit = {}

    fun updateDeletePauseUi() {
        val task = host.editingTask
        val layout = host.binding.deletePauseLayout
        layout.deletePauseContainer.isVisible = task != null
        if (task == null) return

        val isDailyTask = task.taskType == TaskType.DAILY
        val isPaused = isPausedDailyTask(task)
        val isEnded = isEndedDailyTask(task)

        layout.pauseRow.isVisible = isDailyTask
        layout.deletePauseSpacer.isVisible = isDailyTask

        val deleteParams = layout.deleteRow.layoutParams as LinearLayout.LayoutParams
        val pauseParams = layout.pauseRow.layoutParams as LinearLayout.LayoutParams
        deleteParams.weight = if (isDailyTask) 1f else 2f
        pauseParams.weight = 1f
        layout.deleteRow.layoutParams = deleteParams
        layout.pauseRow.layoutParams = pauseParams

        layout.tvPauseAction.text = when {
            isPaused -> host.getHostString(R.string.resume_action)
            isEnded -> host.getHostString(R.string.restart_from_today_action)
            else -> host.getHostString(R.string.pause_action)
        }
        layout.ivPause.setImageResource(
            if (isPaused || isEnded) android.R.drawable.ic_media_play else R.drawable.ic_pause
        )
        layout.ivPause.contentDescription = layout.tvPauseAction.text
    }

    private fun bindDeletePauseActions() {
        host.binding.deletePauseLayout.deleteRow.setOnClickListener {
            val task = host.editingTask ?: return@setOnClickListener
            DeleteTaskDialog(
                host.hostContext(),
                task,
                onDeleteConfirmed = { taskToDelete ->
                    com.anitech.growdaily.reminder.ReminderScheduler.cancelTaskReminder(host.hostContext(), taskToDelete.id)
                    host.viewModel.deleteTask(taskToDelete) { success ->
                        if (!host.isHostViewSafe()) return@deleteTask
                        if (success) {
                            host.onCloseScreen()
                        } else {
                            host.showHostSnackbar(host.getHostString(R.string.task_delete_failed_toast))
                        }
                    }
                }
            ).show()
        }

        host.binding.deletePauseLayout.pauseRow.setOnClickListener {
            val task = host.editingTask ?: return@setOnClickListener
            if (task.taskType != TaskType.DAILY) return@setOnClickListener

            when {
                isPausedDailyTask(task) -> showResumeDialog(task)
                isEndedDailyTask(task) -> showRestartDialog(task)
                else -> showPauseOptionsDialog(task)
            }
        }
    }

    private fun showResumeDialog(task: TaskEntity) {
        TaskActionDialog(
            context = host.hostContext(),
            title = host.getHostString(R.string.resume_daily_task_title),
            message = host.getHostString(R.string.resume_daily_task_message),
            primaryLabel = host.getHostString(R.string.resume_action),
            iconRes = android.R.drawable.ic_media_play,
            accentColor = host.accentColor,
            iconBubbleColor = host.hostAccentBubbleColor(),
            onPrimaryAction = {
                host.viewModel.resumeDailyTask(task) { success ->
                    if (!host.isHostViewSafe()) return@resumeDailyTask
                    if (success) {
                        host.onCloseScreen()
                    } else {
                        host.showHostSnackbar(host.getHostString(R.string.task_resume_failed_toast))
                    }
                }
            }
        ).show()
    }

    private fun showRestartDialog(task: TaskEntity) {
        TaskActionDialog(
            context = host.hostContext(),
            title = host.getHostString(R.string.restart_daily_task_title),
            message = host.getHostString(R.string.restart_daily_task_message),
            primaryLabel = host.getHostString(R.string.restart_action_label),
            iconRes = android.R.drawable.ic_media_play,
            accentColor = host.accentColor,
            iconBubbleColor = host.hostAccentBubbleColor(),
            onPrimaryAction = {
                host.viewModel.resumeDailyTask(task) { success ->
                    if (!host.isHostViewSafe()) return@resumeDailyTask
                    if (success) {
                        host.onCloseScreen()
                    } else {
                        host.showHostSnackbar(host.getHostString(R.string.task_restart_failed_toast))
                    }
                }
            }
        ).show()
    }

    private fun showPauseOptionsDialog(task: TaskEntity) {
        val today = LocalDate.parse(CommonMethods.getTodayDate())
        PauseOptionsDialog(
            context = host.hostContext(),
            title = host.getHostString(R.string.pause_daily_task_title),
            message = host.getHostString(R.string.pause_daily_task_message),
            iconRes = R.drawable.ic_pause,
            accentColor = host.accentColor,
            iconBubbleColor = host.hostAccentBubbleColor(),
            onPauseFromTomorrow = {
                pauseTask(
                    task.copy(
                        taskRemovedDate = today.toString(),
                        inactiveReason = TaskInactiveReason.PAUSED
                    )
                )
            },
            onPauseFromToday = {
                pauseTask(
                    task.copy(
                        taskRemovedDate = today.minusDays(1).toString(),
                        inactiveReason = TaskInactiveReason.PAUSED
                    )
                )
            }
        ).show()
    }

    private fun pauseTask(updated: TaskEntity) {
        host.viewModel.updateTask(updated) { success ->
            if (!host.isHostViewSafe()) return@updateTask
            if (success) {
                host.onCloseScreen()
            } else {
                host.showHostSnackbar(host.getHostString(R.string.task_pause_failed_toast))
            }
        }
    }

    private fun isPausedDailyTask(task: TaskEntity): Boolean {
        if (task.taskType != TaskType.DAILY) return false
        val removedDate = task.taskRemovedDate ?: return false
        return removedDate <= CommonMethods.getTodayDate() && task.inactiveReason == TaskInactiveReason.PAUSED
    }

    private fun isEndedDailyTask(task: TaskEntity): Boolean {
        if (task.taskType != TaskType.DAILY) return false
        val removedDate = task.taskRemovedDate ?: return false
        return removedDate <= CommonMethods.getTodayDate() && task.inactiveReason == TaskInactiveReason.ENDED
    }
}
