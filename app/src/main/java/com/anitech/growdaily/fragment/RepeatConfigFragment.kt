package com.anitech.growdaily.fragment

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.anitech.growdaily.R
import com.anitech.growdaily.databinding.FragmentRepeatConfigBinding
import com.anitech.growdaily.enum_class.RepeatType

class RepeatConfigFragment : Fragment() {

    private var _binding: FragmentRepeatConfigBinding? = null
    private val binding get() = _binding!!
    private val selectedMonthDays = linkedSetOf<Int>()

    private val weekChecks by lazy {
        listOf(
            1 to binding.checkMon,
            2 to binding.checkTue,
            3 to binding.checkWed,
            4 to binding.checkThu,
            5 to binding.checkFri,
            6 to binding.checkSat,
            7 to binding.checkSun
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRepeatConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMonthDayGrid()
        bindInitialState()

        binding.repeatTypeGroup.setOnCheckedChangeListener { _, _ ->
            updateVisibleSections()
        }

        binding.btnDone.setOnClickListener {
            val repeatType = selectedRepeatType()
            val repeatDays = when (repeatType) {
                RepeatType.DAILY -> emptyList()
                RepeatType.DAYS_OF_WEEK -> selectedWeekDays().also {
                    if (it.isEmpty()) {
                        Toast.makeText(requireContext(), "Select at least one weekday", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                }
                RepeatType.DAYS_OF_MONTH -> parseMonthDays().also {
                    if (it.isEmpty()) {
                        Toast.makeText(requireContext(), "Select at least one day of month", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                }
            }
            val showMissedOnGapDays = when (repeatType) {
                RepeatType.DAILY -> false
                RepeatType.DAYS_OF_WEEK -> binding.switchGapCarryWeekly.isChecked
                RepeatType.DAYS_OF_MONTH -> binding.switchGapCarryMonthly.isChecked
            }

            parentFragmentManager.setFragmentResult(
                "repeatResult",
                bundleOf(
                    "repeatType" to repeatType.name,
                    "repeatDays" to ArrayList(repeatDays),
                    "showMissedOnGapDays" to showMissedOnGapDays
                )
            )

            findNavController().popBackStack()
        }
    }

    private fun bindInitialState() {
        val isEditing = arguments?.getBoolean("isEditing") ?: false
        val repeatType = arguments?.getString("repeatType")
            ?.let { runCatching { RepeatType.valueOf(it) }.getOrNull() }
            ?: RepeatType.DAILY
        val repeatDays = arguments?.getIntegerArrayList("repeatDays")?.toList().orEmpty()
        val showMissedOnGapDays = arguments?.getBoolean("showMissedOnGapDays") ?: false

        binding.tvRepeatEditHint.visibility = if (isEditing) View.VISIBLE else View.GONE

        when (repeatType) {
            RepeatType.DAILY -> binding.radioEveryDay.isChecked = true
            RepeatType.DAYS_OF_WEEK -> binding.radioWeekly.isChecked = true
            RepeatType.DAYS_OF_MONTH -> binding.radioMonthly.isChecked = true
        }

        weekChecks.forEach { (value, checkbox) ->
            checkbox.isChecked = repeatDays.contains(value)
        }

        if (repeatType == RepeatType.DAYS_OF_MONTH) {
            selectedMonthDays.clear()
            selectedMonthDays.addAll(repeatDays.filter { it in 1..31 })
            refreshMonthDayGrid()
        }

        binding.switchGapCarryWeekly.isChecked =
            repeatType == RepeatType.DAYS_OF_WEEK && showMissedOnGapDays
        binding.switchGapCarryMonthly.isChecked =
            repeatType == RepeatType.DAYS_OF_MONTH && showMissedOnGapDays

        updateVisibleSections()
    }

    private fun selectedRepeatType(): RepeatType {
        return when (binding.repeatTypeGroup.checkedRadioButtonId) {
            binding.radioWeekly.id -> RepeatType.DAYS_OF_WEEK
            binding.radioMonthly.id -> RepeatType.DAYS_OF_MONTH
            else -> RepeatType.DAILY
        }
    }

    private fun updateVisibleSections() {
        val repeatType = selectedRepeatType()
        binding.weekDaysContainer.visibility =
            if (repeatType == RepeatType.DAYS_OF_WEEK) View.VISIBLE else View.GONE
        binding.monthDaysContainer.visibility =
            if (repeatType == RepeatType.DAYS_OF_MONTH) View.VISIBLE else View.GONE
        if (repeatType == RepeatType.DAILY) {
            binding.switchGapCarryWeekly.isChecked = false
            binding.switchGapCarryMonthly.isChecked = false
        }
    }

    private fun selectedWeekDays(): List<Int> {
        return weekChecks
            .filter { (_, checkbox) -> checkbox.isChecked }
            .map { it.first }
    }

    private fun parseMonthDays(): List<Int> {
        return selectedMonthDays.toList().sorted()
    }

    private fun setupMonthDayGrid() {
        val context = requireContext()
        binding.monthDaysGrid.removeAllViews()

        for (day in 1..31) {
            val dayView = TextView(context).apply {
                tag = day
                text = day.toString()
                gravity = android.view.Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTypeface(typeface, Typeface.BOLD)
                minHeight = dpToPx(40)
                minWidth = dpToPx(40)
                setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                background = ContextCompat.getDrawable(
                    context,
                    R.drawable.task_filter_chip
                )
                setOnClickListener {
                    if (selectedMonthDays.contains(day)) {
                        selectedMonthDays.remove(day)
                    } else {
                        selectedMonthDays.add(day)
                    }
                    refreshMonthDayGrid()
                }
            }

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            }
            binding.monthDaysGrid.addView(dayView, params)
        }
        refreshMonthDayGrid()
    }

    private fun refreshMonthDayGrid() {
        val context = requireContext()
        repeat(binding.monthDaysGrid.childCount) { index ->
            val dayView = binding.monthDaysGrid.getChildAt(index) as? TextView ?: return@repeat
            val day = dayView.tag as? Int ?: return@repeat
            val isSelected = selectedMonthDays.contains(day)

            dayView.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (isSelected) R.color.brand_blue else R.color.task_done_track
                )
            )
            dayView.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (isSelected) R.color.white else R.color.task_text_primary
                )
            )
        }

        val sortedDays = selectedMonthDays.toList().sorted()
        binding.txtMonthDaySummary.text = when {
            sortedDays.isEmpty() -> "No dates selected"
            sortedDays.size <= 6 -> sortedDays.joinToString(", ")
            else -> "${sortedDays.take(6).joinToString(", ")} +${sortedDays.size - 6}"
        }
    }

    private fun dpToPx(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
