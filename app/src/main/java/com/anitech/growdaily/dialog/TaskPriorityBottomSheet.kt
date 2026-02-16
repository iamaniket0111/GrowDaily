package com.anitech.growdaily.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import com.anitech.growdaily.R
import com.anitech.growdaily.enum_class.TaskWeight
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class TaskPriorityBottomSheet(
    private val selectedWeight: TaskWeight,
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

        // Preselect
        when (selectedWeight) {
            TaskWeight.VERY_LOW -> radioGroup.check(R.id.radioVeryLow)
            TaskWeight.LOW -> radioGroup.check(R.id.radioLow)
            TaskWeight.HIGH -> radioGroup.check(R.id.radioHigh)
            TaskWeight.VERY_HIGH -> radioGroup.check(R.id.radioVeryHigh)
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
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



}
