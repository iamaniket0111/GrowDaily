package com.anitech.growdaily.dialog

import android.app.Dialog
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.NumberPicker
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.MainActivity
import com.anitech.growdaily.R

class TaskDurationDialog : DialogFragment() {

    companion object {
        private const val ARG_START_TIME = "arg_start_time"
        private const val ARG_EXISTING_END_TIME = "arg_existing_end_time"

        fun newInstance(startTime: String, existingEndTime: String? = null): TaskDurationDialog {
            return TaskDurationDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_START_TIME, startTime)
                    putString(ARG_EXISTING_END_TIME, existingEndTime)
                }
            }
        }
    }

    var onEndTimeSelected: ((String) -> Unit)? = null

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90f).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_task_duration, null)

        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90f).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val startTime = arguments?.getString(ARG_START_TIME) ?: ""
        val existingEndTime = arguments?.getString(ARG_EXISTING_END_TIME)
        val startMins = CommonMethods.timeToMinutes(startTime) ?: 0

        val txtStartTimePreview = view.findViewById<TextView>(R.id.txtStartTimePreview)
        val txtEndTimePreview = view.findViewById<TextView>(R.id.txtEndTimePreview)
        val pickerHours = view.findViewById<NumberPicker>(R.id.pickerHours)
        val pickerMinutes = view.findViewById<NumberPicker>(R.id.pickerMinutes)
        val btnSetDuration = view.findViewById<Button>(R.id.btnSetDuration)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)

        txtStartTimePreview?.text = startTime

        // Calculate initial duration
        var initDurationMins = 60
        if (!existingEndTime.isNullOrBlank()) {
            val endMins = CommonMethods.timeToMinutes(existingEndTime)
            if (endMins != null) {
                var diff = endMins - startMins
                if (diff < 0) diff += 1440
                if (diff > 0) initDurationMins = diff
            }
        }

        // Configure pickers
        pickerHours?.minValue = 0
        pickerHours?.maxValue = 23
        pickerMinutes?.minValue = 0
        pickerMinutes?.maxValue = 59
        pickerHours?.setFormatter { String.format("%02d", it) }
        pickerMinutes?.setFormatter { String.format("%02d", it) }

        pickerHours?.value = initDurationMins / 60
        pickerMinutes?.value = initDurationMins % 60

        val chip15m = view.findViewById<TextView>(R.id.chipDuration15m)
        val chip30m = view.findViewById<TextView>(R.id.chipDuration30m)
        val chip45m = view.findViewById<TextView>(R.id.chipDuration45m)
        val chip1h = view.findViewById<TextView>(R.id.chipDuration1h)
        val chip15h = view.findViewById<TextView>(R.id.chipDuration15h)
        val chip2h = view.findViewById<TextView>(R.id.chipDuration2h)
        val chip3h = view.findViewById<TextView>(R.id.chipDuration3h)

        val chips = listOf(chip15m, chip30m, chip45m, chip1h, chip15h, chip2h, chip3h)
        val accentColor = (requireActivity() as? MainActivity)?.accentColor?.value ?: Color.BLUE
        val surfaceColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.task_chip_surface)
        val textSecondaryColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.task_text_secondary)
        val whiteColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.white)

        fun updateChipHighlighting(totalMins: Int) {
            val selectedIndex = when (totalMins) {
                15 -> 0
                30 -> 1
                45 -> 2
                60 -> 3
                90 -> 4
                120 -> 5
                180 -> 6
                else -> -1
            }
            chips.forEachIndexed { idx, chip ->
                if (chip != null) {
                    val isSelected = (idx == selectedIndex)
                    chip.backgroundTintList = ColorStateList.valueOf(if (isSelected) accentColor else surfaceColor)
                    chip.setTextColor(if (isSelected) whiteColor else textSecondaryColor)
                }
            }
        }

        fun updateLivePreview() {
            val hrs = pickerHours?.value ?: 0
            val mins = pickerMinutes?.value ?: 0
            val totalMins = hrs * 60 + mins

            updateChipHighlighting(totalMins)

            if (totalMins == 0) {
                txtEndTimePreview?.text = "Select duration"
                txtEndTimePreview?.setTextColor(textSecondaryColor)
                return
            }

            val endMins = (startMins + totalMins) % 1440
            val endTimeStr = CommonMethods.minutesToTime(endMins)

            val durationLabel = when {
                hrs > 0 && mins > 0 -> "${hrs}h ${mins}m"
                hrs > 0 -> "${hrs}h"
                else -> "${mins}m"
            }

            val isNextDay = (startMins + totalMins) >= 1440
            txtEndTimePreview?.text = if (isNextDay) "$endTimeStr ($durationLabel +1d)" else "$endTimeStr ($durationLabel)"
            txtEndTimePreview?.setTextColor(accentColor)
        }

        updateLivePreview()

        pickerHours?.setOnValueChangedListener { _, _, _ -> updateLivePreview() }
        pickerMinutes?.setOnValueChangedListener { _, _, _ -> updateLivePreview() }

        fun setDurationValues(hrs: Int, mins: Int) {
            pickerHours?.value = hrs
            pickerMinutes?.value = mins
            updateLivePreview()
        }

        chip15m?.setOnClickListener { setDurationValues(0, 15) }
        chip30m?.setOnClickListener { setDurationValues(0, 30) }
        chip45m?.setOnClickListener { setDurationValues(0, 45) }
        chip1h?.setOnClickListener { setDurationValues(1, 0) }
        chip15h?.setOnClickListener { setDurationValues(1, 30) }
        chip2h?.setOnClickListener { setDurationValues(2, 0) }
        chip3h?.setOnClickListener { setDurationValues(3, 0) }

        btnSetDuration?.setOnClickListener {
            val hrs = pickerHours?.value ?: 0
            val mins = pickerMinutes?.value ?: 0
            val totalMins = hrs * 60 + mins
            if (totalMins <= 0) {
                android.widget.Toast.makeText(requireContext(), "Please choose a duration of at least 1 minute", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val endMins = (startMins + totalMins) % 1440
            val endTime = CommonMethods.minutesToTime(endMins)
            onEndTimeSelected?.invoke(endTime)
            dismiss()
        }

        btnCancel?.setOnClickListener {
            dismiss()
        }

        (requireActivity() as? MainActivity)?.accentColor?.value?.let { color ->
            applyAccentColor(view, color, pickerHours, pickerMinutes, btnSetDuration)
        }

        dialog.window?.setLayout(
            ((resources.displayMetrics.widthPixels) * 0.9f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        return dialog
    }

    private fun applyAccentColor(
        view: View,
        color: Int,
        pickerHours: NumberPicker?,
        pickerMinutes: NumberPicker?,
        btnSetDuration: Button?
    ) {
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (!isDarkMode) {
            view.findViewById<TextView>(R.id.txtTitle)?.setTextColor(color)
            view.findViewById<TextView>(R.id.txtStartTimePreview)?.setTextColor(color)
        }

        btnSetDuration?.backgroundTintList = ColorStateList.valueOf(color)

        listOf(pickerHours, pickerMinutes).forEach { picker ->
            if (picker != null) {
                runCatching {
                    val field = NumberPicker::class.java.getDeclaredField("mSelectionDivider")
                    field.isAccessible = true
                    field.set(picker, ColorDrawable(color))
                }
                picker.invalidate()
            }
        }
    }
}
