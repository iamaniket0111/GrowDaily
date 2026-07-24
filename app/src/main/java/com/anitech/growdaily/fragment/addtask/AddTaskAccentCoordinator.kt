package com.anitech.growdaily.fragment.addtask

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import androidx.annotation.StyleRes
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.LifecycleOwner
import com.anitech.growdaily.MainActivity
import com.anitech.growdaily.R
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TrackingType

internal class AddTaskAccentCoordinator(
    private val host: AddTaskSectionHost,
    private val lifecycleOwner: LifecycleOwner,
    private val onAccentColorChanged: (Int) -> Unit,
    private val onTrackingTypeRefresh: (TrackingType) -> Unit,
    private val onMaybeApplyDefaultTaskColor: (Int) -> Unit,
) {
    private enum class AccentThemeKey {
        RED, ORANGE, YELLOW, GREEN, TEAL, BLUE, PURPLE, DARK_BLUE
    }

    fun observeAccentColor() {
        val mainActivity = host.hostMainActivity() ?: return
        mainActivity.accentColor.observe(lifecycleOwner) { color ->
            host.accentColor = color
            onAccentColorChanged(color)
            applyAccentColorUi(color)
        }
    }

    fun applyAccentColorUi(color: Int) {
        onMaybeApplyDefaultTaskColor(color)
        val binding = host.binding
        with(binding) {
            buttonSave.backgroundTintList = ColorStateList.valueOf(color)
            buttonSave.setTextColor(onAccentTextColor())
            progressBarSave.indeterminateTintList = ColorStateList.valueOf(color)
            taskWeightPriorityLayout.txtPriority.setTextColor(color)
            startDateLayout.txtStartDate.setTextColor(color)
            repeatLayout.txtRepeatSummary.setTextColor(color)
            applyScheduleReminderTimeColors(color)

            warningLayout.ivWarningIcon.imageTintList = ColorStateList.valueOf(color)
            warningLayout.ivWarningIcon.backgroundTintList =
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 36))
            warningLayout.tvInfoTitle.setTextColor(color)

            val isDarkMode =
                (host.hostResources().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            if (!isDarkMode) {
                warningLayout.root.backgroundTintList =
                    ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 10))
                warningLayout.root.backgroundTintMode = PorterDuff.Mode.SRC_OVER
            } else {
                warningLayout.root.backgroundTintList = null
            }

            applyAccentToEditTexts(color)
            applyAccentToSwitches(color)

            val ctx = host.hostContext()
            val listSummaryColor = if (host.viewModel.selectedListIds.value.isEmpty()) {
                ContextCompat.getColor(ctx, R.color.add_form_text_secondary)
            } else {
                color
            }
            addToListLayout.txtListSummary.setTextColor(listSummaryColor)
            addToListLayout.txtListSummaryExtra.setTextColor(listSummaryColor)
            endDateLayout.txtEndDate.setTextColor(
                if (host.viewModel.uiState.value.endDate.isNullOrBlank()) {
                    ContextCompat.getColor(ctx, R.color.add_form_text_secondary)
                } else {
                    color
                }
            )
        }
        onTrackingTypeRefresh(host.viewModel.uiState.value.trackingType)
    }

    private fun applyScheduleReminderTimeColors(accentColor: Int) {
        val state = host.viewModel.uiState.value
        val placeholder = host.getHostString(R.string.time_placeholder)
        val placeholderColor =
            ContextCompat.getColor(host.hostContext(), R.color.add_form_text_secondary)
        val binding = host.binding
        binding.scheduleLayout.txtScheduleTime.bindAddTaskTimeValue(
            time = state.scheduleTime,
            placeholder = placeholder,
            accentColor = accentColor,
            placeholderColor = placeholderColor,
        )
        binding.reminderLayoutMain.txtReminderTime.bindAddTaskTimeValue(
            time = state.reminderTime,
            placeholder = placeholder,
            accentColor = accentColor,
            placeholderColor = placeholderColor,
        )
    }

    fun applyAccentToEditTexts(color: Int) {
        val highlightColor = ColorUtils.setAlphaComponent(color, 48)
        val binding = host.binding
        listOf(
            binding.titleNoteLayout.editTextTitle,
            binding.titleNoteLayout.editTextNote
        ).forEach { editText ->
            editText.textCursorDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(color)
                setSize(host.hostDpToPx(2), 1)
            }
            editText.highlightColor = highlightColor
        }
    }

    fun applyAccentToSwitches(color: Int) {
        val ctx = host.hostContext()
        val thumbTint = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                color,
                ContextCompat.getColor(ctx, R.color.white)
            )
        )
        val trackTint = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                ColorUtils.setAlphaComponent(color, 110),
                ContextCompat.getColor(ctx, R.color.task_done_track)
            )
        )
        val binding = host.binding
        tintSwitch(binding.scheduleLayout.switchSchedule, thumbTint, trackTint)
        tintSwitch(binding.reminderLayoutMain.switchReminder, thumbTint, trackTint)
        tintSwitch(binding.untilCompleteLayout.switchUntilComplete, thumbTint, trackTint)
    }

    @StyleRes
    fun resolveDatePickerThemeRes(): Int {
        return when (resolveAccentThemeKey()) {
            AccentThemeKey.RED -> R.style.Theme_GrowDaily_MaterialDatePicker_Red
            AccentThemeKey.ORANGE -> R.style.Theme_GrowDaily_MaterialDatePicker_Orange
            AccentThemeKey.YELLOW -> R.style.Theme_GrowDaily_MaterialDatePicker_Yellow
            AccentThemeKey.GREEN -> R.style.Theme_GrowDaily_MaterialDatePicker_Green
            AccentThemeKey.TEAL -> R.style.Theme_GrowDaily_MaterialDatePicker_Teal
            AccentThemeKey.BLUE -> R.style.Theme_GrowDaily_MaterialDatePicker_Blue
            AccentThemeKey.PURPLE -> R.style.Theme_GrowDaily_MaterialDatePicker_Purple
            AccentThemeKey.DARK_BLUE -> R.style.Theme_GrowDaily_MaterialDatePicker_DarkBlue
        }
    }

    @StyleRes
    fun resolveTimePickerThemeRes(): Int {
        return when (resolveAccentThemeKey()) {
            AccentThemeKey.RED -> R.style.Theme_GrowDaily_MaterialTimePicker_Red
            AccentThemeKey.ORANGE -> R.style.Theme_GrowDaily_MaterialTimePicker_Orange
            AccentThemeKey.YELLOW -> R.style.Theme_GrowDaily_MaterialTimePicker_Yellow
            AccentThemeKey.GREEN -> R.style.Theme_GrowDaily_MaterialTimePicker_Green
            AccentThemeKey.TEAL -> R.style.Theme_GrowDaily_MaterialTimePicker_Teal
            AccentThemeKey.BLUE -> R.style.Theme_GrowDaily_MaterialTimePicker_Blue
            AccentThemeKey.PURPLE -> R.style.Theme_GrowDaily_MaterialTimePicker_Purple
            AccentThemeKey.DARK_BLUE -> R.style.Theme_GrowDaily_MaterialTimePicker_DarkBlue
        }
    }

    fun onAccentTextColor(): Int {
        return ContextCompat.getColor(host.hostContext(), R.color.white)
    }

    fun maybeApplyAccentAsDefaultTaskColor(color: Int) {
        if (host.editingTask != null || host.viewModel.hasUserSelectedTaskAppearance) return

        val matchedTaskColor = TaskColor.entries.firstOrNull {
            ContextCompat.getColor(host.hostContext(), it.resId) == color
        } ?: return

        val currentState = host.viewModel.uiState.value
        if (currentState.color == matchedTaskColor.name) return

        host.viewModel.updateIconAndColor(
            icon = currentState.icon,
            color = matchedTaskColor.name
        )
    }

    private fun tintSwitch(switch: SwitchCompat, thumbTint: ColorStateList, trackTint: ColorStateList) {
        switch.thumbTintList = thumbTint
        switch.trackTintList = trackTint
    }

    private fun resolveAccentThemeKey(): AccentThemeKey {
        val context = host.hostContext()
        return when (host.accentColor) {
            ContextCompat.getColor(context, R.color.category_red) -> AccentThemeKey.RED
            ContextCompat.getColor(context, R.color.category_orange) -> AccentThemeKey.ORANGE
            ContextCompat.getColor(context, R.color.category_yellow) -> AccentThemeKey.YELLOW
            ContextCompat.getColor(context, R.color.category_green) -> AccentThemeKey.GREEN
            ContextCompat.getColor(context, R.color.category_teal) -> AccentThemeKey.TEAL
            ContextCompat.getColor(context, R.color.category_blue) -> AccentThemeKey.BLUE
            ContextCompat.getColor(context, R.color.category_purple) -> AccentThemeKey.PURPLE
            else -> AccentThemeKey.DARK_BLUE
        }
    }
}
