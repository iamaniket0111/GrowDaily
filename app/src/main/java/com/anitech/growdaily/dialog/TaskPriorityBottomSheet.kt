package com.anitech.growdaily.dialog

import android.os.Bundle
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.core.graphics.ColorUtils
import androidx.core.widget.TextViewCompat
import com.anitech.growdaily.R
import com.anitech.growdaily.enum_class.TaskWeight
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class TaskPriorityBottomSheet(
    private val selectedWeight: TaskWeight,
    private val accentColor: Int,
    private val onPrioritySelected: (TaskWeight) -> Unit
) : BottomSheetDialogFragment() {


    override fun getTheme(): Int {
        return R.style.TaskBottomSheetDialogTheme
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(
            R.layout.bottom_sheet_task_priority,
            container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val radioGroup = view.findViewById<RadioGroup>(R.id.radioGroupPriority)
        val radioButtons = listOf(
            view.findViewById<RadioButton>(R.id.radioVeryLow),
            view.findViewById<RadioButton>(R.id.radioLow),
            view.findViewById<RadioButton>(R.id.radioHigh),
            view.findViewById<RadioButton>(R.id.radioVeryHigh)
        )

        // Preselect
        when (selectedWeight) {
            TaskWeight.VERY_LOW -> radioGroup.check(R.id.radioVeryLow)
            TaskWeight.LOW -> radioGroup.check(R.id.radioLow)
            TaskWeight.HIGH -> radioGroup.check(R.id.radioHigh)
            TaskWeight.VERY_HIGH -> radioGroup.check(R.id.radioVeryHigh)
        }
        updateSelectionStyles(radioButtons, radioGroup.checkedRadioButtonId)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            updateSelectionStyles(radioButtons, checkedId)
            val weight = when (checkedId) {
                R.id.radioVeryLow -> TaskWeight.VERY_LOW
                R.id.radioLow -> TaskWeight.LOW
                R.id.radioHigh -> TaskWeight.HIGH
                R.id.radioVeryHigh -> TaskWeight.VERY_HIGH
                else -> TaskWeight.VERY_LOW
            }

            onPrioritySelected(weight)
            dismiss()
        }
    }

    private fun updateSelectionStyles(radioButtons: List<RadioButton>, checkedId: Int) {
        radioButtons.forEach { button ->
            val isChecked = button.id == checkedId
            button.backgroundTintList = ColorStateList.valueOf(
                if (isChecked) ColorUtils.setAlphaComponent(accentColor, 28)
                else requireContext().getColor(R.color.task_card_surface)
            )
            button.setTextColor(
                if (isChecked) accentColor
                else requireContext().getColor(R.color.task_text_primary)
            )
            TextViewCompat.setCompoundDrawableTintList(
                button,
                ColorStateList.valueOf(
                    if (isChecked) accentColor
                    else requireContext().getColor(R.color.task_text_secondary)
                )
            )
        }
    }

}
