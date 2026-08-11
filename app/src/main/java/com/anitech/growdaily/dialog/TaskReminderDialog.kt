package com.anitech.growdaily.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.NumberPicker
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.MainActivity
import com.anitech.growdaily.R

class TaskReminderDialog : DialogFragment() {

    companion object {
        private const val ARG_START_TIME = "arg_start_time"
        private const val ARG_REMINDER_TIME = "arg_reminder_time"
        private const val ARG_IS_ENABLED = "arg_is_enabled"

        fun newInstance(
            startTime: String?,
            existingReminderTime: String?,
            isReminderEnabled: Boolean
        ): TaskReminderDialog {
            return TaskReminderDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_START_TIME, startTime)
                    putString(ARG_REMINDER_TIME, existingReminderTime)
                    putBoolean(ARG_IS_ENABLED, isReminderEnabled)
                }
            }
        }
    }

    var onReminderSelected: ((reminderTime: String?, isEnabled: Boolean) -> Unit)? = null
    var onCancelled: (() -> Unit)? = null

    private var selectedReminderTime: String? = null
    private var isReminderEnabled: Boolean = false
    private var isCustomTime: Boolean = false
    private var isProgrammaticWheelChange: Boolean = false
    private var isConfirmed: Boolean = false

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (!isConfirmed) {
            onCancelled?.invoke()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_task_reminder, null)

        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val startTime = arguments?.getString(ARG_START_TIME)
        selectedReminderTime = arguments?.getString(ARG_REMINDER_TIME)
        isReminderEnabled = arguments?.getBoolean(ARG_IS_ENABLED, false) ?: false

        // Default to Start Time if no reminder time is set
        if (selectedReminderTime.isNullOrBlank() && !startTime.isNullOrBlank()) {
            selectedReminderTime = startTime
            isReminderEnabled = true
        }

        val txtReminderPreview = view.findViewById<TextView>(R.id.txtReminderPreview)
        val txtStartTimeSubtext = view.findViewById<TextView>(R.id.txtStartTimeSubtext)
        val btnDone = view.findViewById<Button>(R.id.btnDone)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)

        val chipStartTime = view.findViewById<TextView>(R.id.chipReminderStartTime)
        val chip5m = view.findViewById<TextView>(R.id.chipReminder5m)
        val chip10m = view.findViewById<TextView>(R.id.chipReminder10m)
        val chip15m = view.findViewById<TextView>(R.id.chipReminder15m)
        val chip30m = view.findViewById<TextView>(R.id.chipReminder30m)
        val chip1h = view.findViewById<TextView>(R.id.chipReminder1h)
        val chipOff = view.findViewById<TextView>(R.id.chipReminderOff)

        val pickerHours = view.findViewById<NumberPicker>(R.id.pickerHours)
        val pickerMinutes = view.findViewById<NumberPicker>(R.id.pickerMinutes)
        val pickerAmPm = view.findViewById<NumberPicker>(R.id.pickerAmPm)

        pickerHours?.minValue = 1
        pickerHours?.maxValue = 12

        pickerMinutes?.minValue = 0
        pickerMinutes?.maxValue = 59
        pickerMinutes?.setFormatter { String.format("%02d", it) }

        pickerAmPm?.minValue = 0
        pickerAmPm?.maxValue = 1
        pickerAmPm?.displayedValues = arrayOf("AM", "PM")

        val presetChips = listOf(chipStartTime, chip5m, chip10m, chip15m, chip30m, chip1h, chipOff)

        val accentColor = (requireActivity() as? MainActivity)?.accentColor?.value ?: Color.parseColor("#3B82F6")
        val surfaceColor = ContextCompat.getColor(requireContext(), R.color.task_chip_surface)
        val textSecondaryColor = ContextCompat.getColor(requireContext(), R.color.task_text_secondary)
        val whiteColor = ContextCompat.getColor(requireContext(), R.color.white)

        btnDone?.backgroundTintList = ColorStateList.valueOf(accentColor)
        txtReminderPreview?.setTextColor(accentColor)

        if (!startTime.isNullOrBlank()) {
            txtStartTimeSubtext?.text = "Start Time: $startTime"
            txtStartTimeSubtext?.visibility = View.VISIBLE
        } else {
            txtStartTimeSubtext?.visibility = View.GONE
        }

        fun syncWheelsFromTime(timeStr: String?) {
            if (pickerHours == null || pickerMinutes == null || pickerAmPm == null) return
            val totalMins = CommonMethods.timeToMinutes(timeStr) ?: (CommonMethods.timeToMinutes(startTime) ?: 480)
            val hour24 = (totalMins / 60) % 24
            val minute = totalMins % 60
            val isPm = hour24 >= 12
            val hour12 = when (hour24 % 12) {
                0 -> 12
                else -> hour24 % 12
            }
            isProgrammaticWheelChange = true
            pickerHours.value = hour12
            pickerMinutes.value = minute
            pickerAmPm.value = if (isPm) 1 else 0
            isProgrammaticWheelChange = false
        }

        fun getTimeFromWheels(): String {
            val h12 = pickerHours?.value ?: 8
            val mins = pickerMinutes?.value ?: 0
            val isPm = (pickerAmPm?.value == 1)
            val h24 = if (isPm) {
                if (h12 == 12) 12 else h12 + 12
            } else {
                if (h12 == 12) 0 else h12
            }
            val totalMins = (h24 * 60 + mins) % 1440
            return CommonMethods.minutesToTime(totalMins)
        }

        fun updatePreviewAndHighlighting() {
            val startMins = CommonMethods.timeToMinutes(startTime)
            val remMins = CommonMethods.timeToMinutes(selectedReminderTime)

            var activePresetIdx = -1

            if (!isReminderEnabled || selectedReminderTime.isNullOrBlank()) {
                txtReminderPreview?.text = "Reminder Time: Off"
                txtReminderPreview?.setTextColor(textSecondaryColor)
                activePresetIdx = 6 // Off chip
            } else {
                txtReminderPreview?.text = "Reminder Time: $selectedReminderTime"
                txtReminderPreview?.setTextColor(accentColor)

                if (startMins != null && remMins != null) {
                    val diff = (startMins - remMins + 1440) % 1440
                    activePresetIdx = when (diff) {
                        0 -> 0
                        5 -> 1
                        10 -> 2
                        15 -> 3
                        30 -> 4
                        60 -> 5
                        else -> -1
                    }
                    if (activePresetIdx == -1) {
                        isCustomTime = true
                    }
                }
            }

            presetChips.forEachIndexed { index, chip ->
                val isSelected = (index == activePresetIdx)
                chip?.backgroundTintList = ColorStateList.valueOf(if (isSelected) accentColor else surfaceColor)
                chip?.setTextColor(if (isSelected) whiteColor else textSecondaryColor)
            }
        }

        val onWheelChangedListener = NumberPicker.OnValueChangeListener { _, _, _ ->
            if (isProgrammaticWheelChange) return@OnValueChangeListener
            selectedReminderTime = getTimeFromWheels()
            isReminderEnabled = true
            isCustomTime = true
            updatePreviewAndHighlighting()
        }

        pickerHours?.setOnValueChangedListener(onWheelChangedListener)
        pickerMinutes?.setOnValueChangedListener(onWheelChangedListener)
        pickerAmPm?.setOnValueChangedListener(onWheelChangedListener)

        fun setPreset(minsOffset: Int) {
            isCustomTime = false
            val startMins = CommonMethods.timeToMinutes(startTime) ?: 0
            val remMins = (startMins - minsOffset + 1440) % 1440
            selectedReminderTime = CommonMethods.minutesToTime(remMins)
            isReminderEnabled = true
            syncWheelsFromTime(selectedReminderTime)
            updatePreviewAndHighlighting()
        }

        chipStartTime?.setOnClickListener { setPreset(0) }
        chip5m?.setOnClickListener { setPreset(5) }
        chip10m?.setOnClickListener { setPreset(10) }
        chip15m?.setOnClickListener { setPreset(15) }
        chip30m?.setOnClickListener { setPreset(30) }
        chip1h?.setOnClickListener { setPreset(60) }

        chipOff?.setOnClickListener {
            isCustomTime = false
            selectedReminderTime = null
            isReminderEnabled = false
            updatePreviewAndHighlighting()
        }

        btnCancel?.setOnClickListener {
            isConfirmed = false
            dismiss()
        }

        btnDone?.setOnClickListener {
            isConfirmed = true
            onReminderSelected?.invoke(selectedReminderTime, isReminderEnabled)
            dismiss()
        }

        syncWheelsFromTime(selectedReminderTime)
        updatePreviewAndHighlighting()
        return dialog
    }
}
