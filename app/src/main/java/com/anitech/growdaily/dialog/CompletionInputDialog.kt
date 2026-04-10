package com.anitech.growdaily.dialog

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.anitech.growdaily.R
import com.anitech.growdaily.setSolidBackgroundColorCompat
import com.anitech.growdaily.data_class.TaskCompletionEntity
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.database.util.EffectiveTrackingSettings
import com.anitech.growdaily.enum_class.CompletionAction
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TrackingType
import com.anitech.growdaily.view.CircularSeekBarView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONArray
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class CompletionInputDialog(
    private val task: TaskEntity,
    private val date: String,
    private val currentCompletion: TaskCompletionEntity?,
    private val trackingSettingsOverride: EffectiveTrackingSettings? = null,
    private val checklistItemsOverride: String? = null,
    private val onAction: (CompletionAction) -> Unit
) : DialogFragment() {

    private var workingChecklistJson: String = ""
    private val badgeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_completion_input, null)

        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        // Set fixed width to prevent "grabbing" and ensure consistent look across devices
        val width = (resources.displayMetrics.widthPixels * 0.88).toInt().coerceAtMost(1000)
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        applyDialogAccent(view, resolveTaskColor())

        when (task.trackingType) {
            TrackingType.COUNT -> setupCount(view)
            TrackingType.TIMER -> setupTimer(view)
            TrackingType.CHECKLIST -> setupChecklist(view)
            TrackingType.BINARY -> dismiss()
        }

        view.findViewById<View>(R.id.btnClose).setOnClickListener { dismiss() }
        return dialog
    }

    private fun setupCount(view: View) {
        val section = view.findViewById<View>(R.id.timerCountLayout)
        val tvTitle = view.findViewById<TextView>(R.id.tvDialogTitle)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvDialogSubtitle)
        val tvValue = view.findViewById<TextView>(R.id.tvCurrentValue)
        val tvDateBadge = view.findViewById<TextView>(R.id.tvDateBadge)
        val circularSeek = view.findViewById<CircularSeekBarView>(R.id.circularSeekBar)
        val tvHint = view.findViewById<TextView>(R.id.tvStepHint)
        val btnDown = view.findViewById<Button>(R.id.btnStepDown)
        val btnUp = view.findViewById<Button>(R.id.btnStepUp)

        section.visibility = View.VISIBLE

        val target = (trackingSettingsOverride?.dailyTargetCount ?: task.dailyTargetCount).coerceAtLeast(1)
        var current = (currentCompletion?.count ?: 0).coerceIn(0, target)
        val ringColor = resolveTaskColor()

        tvTitle.text = task.title
        tvSubtitle.text = "Count progress"
        tvHint.text = "Drag the ring or use the controls"
        btnDown.text = "-1"
        btnUp.text = "+1"
        tvDateBadge.text = resolveDateBadgeText()

        circularSeek.max = target
        circularSeek.setProgressColor(ringColor)
        circularSeek.setTrackColor(withAlpha(ringColor, 0.18f))
        circularSeek.progress = current

        fun refresh() {
            tvValue.text = "$current / $target"
            tvValue.setTextColor(
                if (current >= target) ringColor
                else ContextCompat.getColor(requireContext(), R.color.completion_dialog_value_pending)
            )
        }

        refresh()

        circularSeek.setOnCircularChangeListener(object : CircularSeekBarView.OnCircularChangeListener {
            override fun onProgressChanged(view: CircularSeekBarView, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val delta = progress - current
                    current = progress
                    onAction(CompletionAction.CountDelta(delta))
                    refresh()
                }
            }
            override fun onStartTrackingTouch(view: CircularSeekBarView) {}
            override fun onStopTrackingTouch(view: CircularSeekBarView) {}
        })

        btnDown.setOnClickListener {
            if (current > 0) {
                current--
                circularSeek.progress = current
                onAction(CompletionAction.CountDelta(-1))
                refresh()
            }
        }

        btnUp.setOnClickListener {
            if (current < target) {
                current++
                circularSeek.progress = current
                onAction(CompletionAction.CountDelta(1))
                refresh()
            }
        }
    }

    private fun setupTimer(view: View) {
        val section = view.findViewById<View>(R.id.timerCountLayout)
        val tvTitle = view.findViewById<TextView>(R.id.tvDialogTitle)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvDialogSubtitle)
        val tvValue = view.findViewById<TextView>(R.id.tvCurrentValue)
        val tvDateBadge = view.findViewById<TextView>(R.id.tvDateBadge)
        val circularSeek = view.findViewById<CircularSeekBarView>(R.id.circularSeekBar)
        val tvHint = view.findViewById<TextView>(R.id.tvStepHint)
        val btnDown = view.findViewById<Button>(R.id.btnStepDown)
        val btnUp = view.findViewById<Button>(R.id.btnStepUp)

        section.visibility = View.VISIBLE

        val targetMin = ((trackingSettingsOverride?.targetDurationSeconds ?: task.targetDurationSeconds)
            .coerceAtLeast(60L) / 60L).toInt()
        var currentSec = (currentCompletion?.durationSeconds ?: 0L).coerceAtLeast(0L)
        val ringColor = resolveTaskColor()

        tvTitle.text = task.title
        tvSubtitle.text = "Time progress"
        tvHint.text = "Drag the ring or adjust by 1 minute"
        btnDown.text = "-1 min"
        btnUp.text = "+1 min"
        tvDateBadge.text = resolveDateBadgeText()

        circularSeek.max = targetMin.coerceAtLeast(1)
        circularSeek.setProgressColor(ringColor)
        circularSeek.setTrackColor(withAlpha(ringColor, 0.18f))
        circularSeek.progress = (currentSec / 60L).toInt().coerceIn(0, circularSeek.max)

        fun refresh() {
            val totalMin = currentSec / 60L
            val remSec = currentSec % 60L
            tvValue.text = String.format("%d:%02d / %d:00", totalMin, remSec, targetMin)
            tvValue.setTextColor(
                if (totalMin >= targetMin.toLong()) ringColor
                else ContextCompat.getColor(requireContext(), R.color.completion_dialog_value_pending)
            )
        }

        refresh()

        circularSeek.setOnCircularChangeListener(object : CircularSeekBarView.OnCircularChangeListener {
            override fun onProgressChanged(view: CircularSeekBarView, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val newSec = progress * 60L
                    val delta = newSec - currentSec
                    currentSec = newSec
                    onAction(CompletionAction.TimerAdd(delta))
                    refresh()
                }
            }
            override fun onStartTrackingTouch(view: CircularSeekBarView) {}
            override fun onStopTrackingTouch(view: CircularSeekBarView) {}
        })

        btnDown.setOnClickListener {
            if (currentSec >= 60L) {
                currentSec -= 60L
                circularSeek.progress = (currentSec / 60L).toInt()
                onAction(CompletionAction.TimerAdd(-60L))
                refresh()
            }
        }

        btnUp.setOnClickListener {
            val maxAllowed = circularSeek.max * 60L
            if (currentSec < maxAllowed) {
                currentSec += 60L
                circularSeek.progress = (currentSec / 60L).toInt()
                onAction(CompletionAction.TimerAdd(60L))
                refresh()
            }
        }
    }

    private fun setupChecklist(view: View) {
        val sectionHeader = view.findViewById<View>(R.id.checklistHeaderSection)
        val container = view.findViewById<LinearLayout>(R.id.checklistContainer)
        val tvTitle = view.findViewById<TextView>(R.id.tvDialogTitle)
        val tvDoneCount = view.findViewById<TextView>(R.id.tvChecklistDoneCount)
        val progressBar = view.findViewById<LinearProgressIndicator>(R.id.checklistProgress)
        val taskColor = resolveTaskColor()

        sectionHeader.visibility = View.VISIBLE
        tvTitle.text = task.title
        tvDoneCount.visibility = View.VISIBLE
        progressBar.max = 100
        progressBar.setIndicatorColor(taskColor)
        progressBar.trackColor = withAlpha(taskColor, 0.14f)

        val labels: List<String> = parseLabels(
            checklistItemsOverride ?: trackingSettingsOverride?.checklistItemsJson ?: task.checklistItems
        )
        workingChecklistJson = sanitizeChecklistJson(
            raw = currentCompletion?.checklistJson,
            labels = labels
        )

        fun rebuildRows() {
            container.removeAllViews()
            val array = JSONArray(workingChecklistJson)
            var doneCount = 0

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val label = item.optString("label", "")
                val done = item.optBoolean("done", false)
                if (done) doneCount++

                val row = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_checklist_check_row, container, false)

                val cb = row.findViewById<CheckBox>(R.id.cbItem)
                val tvLabel = row.findViewById<TextView>(R.id.tvItemLabel)

                cb.setOnCheckedChangeListener(null)
                cb.isChecked = done
                tvLabel.text = label
                cb.buttonTintList = android.content.res.ColorStateList.valueOf(taskColor)
                cb.isClickable = false
                
                // Slightly reduced alpha for done items background
                row.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    if (done) withAlpha(taskColor, 0.08f)
                    else ContextCompat.getColor(requireContext(), R.color.completion_dialog_row_surface)
                )
                
                tvLabel.paintFlags = if (done) {
                    tvLabel.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    tvLabel.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }
                tvLabel.setTextColor(
                    if (done) withAlpha(taskColor, 0.85f)
                    else ContextCompat.getColor(requireContext(), R.color.dialog_text_primary)
                )

                val updateItemState: (Boolean) -> Unit = { isChecked ->
                    val updatedArray = JSONArray(workingChecklistJson)
                    updatedArray.getJSONObject(i).put("done", isChecked)
                    workingChecklistJson = updatedArray.toString()
                    onAction(CompletionAction.ChecklistUpdate(workingChecklistJson))
                    rebuildRows()
                }

                row.setOnClickListener {
                    updateItemState(!done)
                }

                container.addView(row)
            }

            val percent = if (array.length() > 0) (doneCount * 100) / array.length() else 0
            tvDoneCount.text = "$doneCount / ${array.length()} done"
            progressBar.setProgress(percent, true)
        }

        rebuildRows()
    }

    private fun applyDialogAccent(view: View, taskColor: Int) {
        view.findViewById<View>(R.id.viewAccent)?.setSolidBackgroundColorCompat(taskColor)
        view.findViewById<Button>(R.id.btnClose)?.backgroundTintList = ColorStateList.valueOf(taskColor)
        view.findViewById<Button>(R.id.btnStepUp)?.backgroundTintList = ColorStateList.valueOf(taskColor)
        view.findViewById<TextView>(R.id.tvChecklistDoneCount)?.setTextColor(taskColor)
        view.findViewById<TextView>(R.id.tvDateBadge)?.setTextColor(taskColor)
        tintShapeStrokeAndText(view.findViewById(R.id.tvDateBadge), taskColor)
        tintShapeStrokeAndText(view.findViewById(R.id.tvStepHint), taskColor)
    }

    private fun tintShapeStrokeAndText(view: View?, taskColor: Int) {
        val background = view?.background?.mutate()
        if (background is GradientDrawable) {
            background.setStroke(
                (resources.displayMetrics.density * 1).toInt().coerceAtLeast(1),
                withAlpha(taskColor, 0.24f)
            )
        }
    }

    private fun resolveTaskColor(): Int {
        return runCatching {
            TaskColor.valueOf(task.colorCode).toColorInt(requireContext())
        }.getOrElse {
            ContextCompat.getColor(requireContext(), R.color.brand_blue)
        }
    }

    private fun resolveDateBadgeText(): String {
        val targetDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return ""
        val today = LocalDate.now()
        return when (targetDate) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> targetDate.format(badgeFormatter)
        }
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    private fun parseLabels(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val listType = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(json, listType)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun sanitizeChecklistJson(raw: String?, labels: List<String>): String {
        val currentArray = try { JSONArray(raw ?: "[]") } catch (e: Exception) { JSONArray() }
        val resultMap = mutableMapOf<String, Boolean>()
        for (i in 0 until currentArray.length()) {
            val obj = currentArray.getJSONObject(i)
            resultMap[obj.optString("label")] = obj.optBoolean("done")
        }

        val finalArray = JSONArray()
        labels.forEach { label ->
            val newObj = org.json.JSONObject()
            newObj.put("label", label)
            newObj.put("done", resultMap[label] ?: false)
            finalArray.put(newObj)
        }
        return finalArray.toString()
    }
}
