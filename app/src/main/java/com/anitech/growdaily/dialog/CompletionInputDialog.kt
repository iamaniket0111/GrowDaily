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
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.anitech.growdaily.R
import com.anitech.growdaily.setSolidBackgroundColorCompat
import com.anitech.growdaily.data_class.TaskCompletionEntity
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.database.util.EffectiveTrackingSettings
import com.anitech.growdaily.enum_class.CompletionAction
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon
import com.anitech.growdaily.enum_class.TrackingType
import com.anitech.growdaily.view.CircularSeekBarView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONArray
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.content.DialogInterface
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CompletionInputDialog : DialogFragment() {

    private lateinit var task: TaskEntity
    private lateinit var date: String
    private var currentCompletion: TaskCompletionEntity? = null
    private var trackingSettingsOverride: EffectiveTrackingSettings? = null
    private var checklistItemsOverride: String? = null

    private var workingChecklistJson: String = ""
    private val badgeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    private val gson = Gson()

    private var timerJob: Job? = null
    private var isTimerRunning = false
    private var currentSec = 0L
    private var lastEmittedSec = 0L


// Timer accuracy & haptics support
private var startTimeMillis = 0L
private var baseSeconds = 0L
private var hasCelebrated = false

companion object {
const val REQUEST_KEY = "completionInputResult"

private const val ARG_TASK = "task"
private const val ARG_DATE = "date"
private const val ARG_COMPLETION_JSON = "completion_json"
private const val ARG_HAS_TRACKING_OVERRIDE = "has_tracking_override"
private const val ARG_DAILY_TARGET_COUNT = "daily_target_count"
private const val ARG_TARGET_DURATION_SECONDS = "target_duration_seconds"
private const val ARG_CHECKLIST_ITEMS_JSON = "checklist_items_json"
private const val ARG_CHECKLIST_OVERRIDE = "checklist_override"

fun newInstance(
task: TaskEntity,
date: String,
currentCompletion: TaskCompletionEntity?,
trackingSettingsOverride: EffectiveTrackingSettings? = null,
checklistItemsOverride: String? = null
): CompletionInputDialog {
val args = bundleOf(
ARG_TASK to task,
ARG_DATE to date,
ARG_HAS_TRACKING_OVERRIDE to (trackingSettingsOverride != null),
ARG_CHECKLIST_OVERRIDE to checklistItemsOverride
)
currentCompletion?.let {
args.putString(ARG_COMPLETION_JSON, Gson().toJson(it))
}
trackingSettingsOverride?.let {
args.putInt(ARG_DAILY_TARGET_COUNT, it.dailyTargetCount)
args.putLong(ARG_TARGET_DURATION_SECONDS, it.targetDurationSeconds)
args.putString(ARG_CHECKLIST_ITEMS_JSON, it.checklistItemsJson)
}
return CompletionInputDialog().apply { arguments = args }
}
}

override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)
val args = requireArguments()
task = BundleCompat.getParcelable(args, ARG_TASK, TaskEntity::class.java)
?: throw IllegalStateException("Task argument is required")
date = args.getString(ARG_DATE)
?: throw IllegalStateException("Date argument is required")
args.getString(ARG_COMPLETION_JSON)?.let { json ->
currentCompletion = gson.fromJson(json, TaskCompletionEntity::class.java)
}
if (args.getBoolean(ARG_HAS_TRACKING_OVERRIDE, false)) {
trackingSettingsOverride = EffectiveTrackingSettings(
weightValue = 0,
dailyTargetCount = args.getInt(ARG_DAILY_TARGET_COUNT),
targetDurationSeconds = args.getLong(ARG_TARGET_DURATION_SECONDS),
checklistItemsJson = args.getString(ARG_CHECKLIST_ITEMS_JSON)
)
}
checklistItemsOverride = args.getString(ARG_CHECKLIST_OVERRIDE)

if (savedInstanceState != null) {
currentSec = savedInstanceState.getLong("currentSec", currentSec)
lastEmittedSec = savedInstanceState.getLong("lastEmittedSec", lastEmittedSec)
isTimerRunning = savedInstanceState.getBoolean("isTimerRunning", false)
if (isTimerRunning) {
startTimeMillis = savedInstanceState.getLong("startTimeMillis", android.os.SystemClock.elapsedRealtime())
baseSeconds = savedInstanceState.getLong("baseSeconds", currentSec)
}
hasCelebrated = savedInstanceState.getBoolean("hasCelebrated", false)
}
}

override fun onSaveInstanceState(outState: Bundle) {
super.onSaveInstanceState(outState)
outState.putLong("currentSec", currentSec)
outState.putLong("lastEmittedSec", lastEmittedSec)
outState.putBoolean("isTimerRunning", isTimerRunning)
if (isTimerRunning) {
outState.putLong("startTimeMillis", startTimeMillis)
outState.putLong("baseSeconds", baseSeconds)
}
outState.putBoolean("hasCelebrated", hasCelebrated)
}

override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
val view = LayoutInflater.from(requireContext())
.inflate(R.layout.dialog_completion_input, null)

val dialog = Dialog(requireContext())
dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
dialog.setContentView(view)
dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

val width = (resources.displayMetrics.widthPixels * 0.88).toInt().coerceAtMost(1000)
dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

dialog.setCancelable(true)
dialog.setCanceledOnTouchOutside(true)

val taskColor = resolveTaskColor()
applyDialogAccent(view, taskColor)
bindHeader(view, taskColor)

when (task.trackingType) {
TrackingType.COUNT -> setupCount(view)
TrackingType.TIMER -> setupTimer(view, savedInstanceState)
TrackingType.CHECKLIST -> setupChecklist(view)
TrackingType.BINARY -> dismiss()
}

view.findViewById<View>(R.id.buttonSave).setOnClickListener { dismiss() }
return dialog
}

private fun emitAction(action: CompletionAction) {
setFragmentResult(
REQUEST_KEY,
CompletionAction.toResultBundle(task.id, date, action)
)
}

private fun bindHeader(view: View, color: Int) {
val header = view.findViewById<View>(R.id.header)
val txtTitle = header.findViewById<TextView>(R.id.txtTaskTitle)
val txtNote = header.findViewById<TextView>(R.id.txtTaskNote)
val iconLayout = view.findViewById<View>(R.id.iconLayout)
val imgIcon = iconLayout.findViewById<android.widget.ImageView>(R.id.imgTaskIcon)
val viewIconBg = iconLayout.findViewById<View>(R.id.viewIconBg)

txtTitle.text = task.title
if (task.note.isNullOrBlank()) {
txtNote.visibility = View.GONE
} else {
txtNote.visibility = View.VISIBLE
txtNote.text = task.note
}

val iconRes = TaskIcon.fromName(task.iconResId).resId
imgIcon.setImageResource(iconRes)
viewIconBg.setSolidBackgroundColorCompat(color)
}

private fun setupCount(view: View) {
val section = view.findViewById<View>(R.id.timerCountLayout)
val countContainer = view.findViewById<View>(R.id.countContainer)
countContainer.visibility = View.VISIBLE
val tvValue = view.findViewById<TextView>(R.id.tvCountValue)
val tvTarget = view.findViewById<TextView>(R.id.tvCountTarget)
val tvDateBadge = view.findViewById<TextView>(R.id.tvDateBadge)
val circularSeek = view.findViewById<CircularSeekBarView>(R.id.circularSeekBar)
val tvHint = view.findViewById<TextView>(R.id.tvStepHint)
val btnDown = view.findViewById<Button>(R.id.btnStepDown)
val btnUp = view.findViewById<Button>(R.id.btnStepUp)

section.visibility = View.VISIBLE

val target = (trackingSettingsOverride?.dailyTargetCount ?: task.dailyTargetCount).coerceAtLeast(1)
var current = (currentCompletion?.count ?: 0).coerceIn(0, target)
var lastEmittedCount = current
val ringColor = resolveTaskColor()

tvHint.setText(R.string.count_hint)
btnDown.text = "-1"
btnUp.text = "+1"
tvDateBadge.text = resolveDateBadgeText()

circularSeek.max = target
circularSeek.setProgressColor(ringColor)
circularSeek.setTrackColor(withAlpha(ringColor, 0.18f))
circularSeek.progress = current

fun refresh() {
tvValue.text = current.toString()
tvTarget.text = String.format(Locale.getDefault(), "of %d", target)
tvValue.setTextColor(
if (current >= target) ringColor
else ContextCompat.getColor(requireContext(), R.color.completion_dialog_value_pending)
)
}

refresh()

circularSeek.setOnCircularChangeListener(object : CircularSeekBarView.OnCircularChangeListener {
override fun onProgressChanged(view: CircularSeekBarView, progress: Int, fromUser: Boolean) {
if (fromUser) {
current = progress
refresh()
}
}
override fun onStartTrackingTouch(view: CircularSeekBarView) {}
override fun onStopTrackingTouch(view: CircularSeekBarView) {
val delta = current - lastEmittedCount
if (delta != 0) {
emitAction(CompletionAction.CountDelta(delta))
lastEmittedCount = current
}
}
})

btnDown.setOnClickListener {
if (current > 0) {
current--
circularSeek.progress = current
val delta = current - lastEmittedCount
if (delta != 0) {
emitAction(CompletionAction.CountDelta(delta))
lastEmittedCount = current
}
refresh()
}
}

btnUp.setOnClickListener {
if (current < target) {
current++
circularSeek.progress = current
val delta = current - lastEmittedCount
if (delta != 0) {
emitAction(CompletionAction.CountDelta(delta))
lastEmittedCount = current
}
refresh()
}
}
}

private fun setupTimer(view: View, savedInstanceState: Bundle?) {
val section = view.findViewById<View>(R.id.timerCountLayout)
val timerContainer = view.findViewById<View>(R.id.timerContainer)
timerContainer.visibility = View.VISIBLE
val tvValue = view.findViewById<TextView>(R.id.tvTimerValue)
val tvTarget = view.findViewById<TextView>(R.id.tvTimerTarget)
val tvDateBadge = view.findViewById<TextView>(R.id.tvDateBadge)
val circularSeek = view.findViewById<CircularSeekBarView>(R.id.circularSeekBar)
val tvHint = view.findViewById<TextView>(R.id.tvStepHint)
val btnDown = view.findViewById<Button>(R.id.btnStepDown)
val btnUp = view.findViewById<Button>(R.id.btnStepUp)
val btnPlayPause = view.findViewById<android.widget.ImageButton>(R.id.btnPlayPause)

section.visibility = View.VISIBLE
btnPlayPause.visibility = View.VISIBLE

val targetMin = ((trackingSettingsOverride?.targetDurationSeconds ?: task.targetDurationSeconds)
.coerceAtLeast(60L) / 60L).toInt()

if (savedInstanceState == null) {
currentSec = (currentCompletion?.durationSeconds ?: 0L).coerceAtLeast(0L)
lastEmittedSec = currentSec
}

val ringColor = resolveTaskColor()

tvHint.setText(R.string.timer_hint)
btnDown.text = "-1 min"
btnUp.text = "+1 min"
tvDateBadge.text = resolveDateBadgeText()

val initialMax = maxOf(targetMin.coerceAtLeast(1), (currentSec / 60L).toInt())
circularSeek.max = initialMax
circularSeek.setProgressColor(ringColor)
circularSeek.setTrackColor(withAlpha(ringColor, 0.18f))
circularSeek.progress = (currentSec / 60L).toInt().coerceIn(0, circularSeek.max)

fun refresh() {
val elapsedHr = currentSec / 3600L
val elapsedMin = (currentSec % 3600L) / 60L
val elapsedSec = currentSec % 60L

val elapsedStr = if (elapsedHr > 0L) {
String.format(Locale.getDefault(), "%dh %02dm %02ds", elapsedHr, elapsedMin, elapsedSec)
} else {
String.format(Locale.getDefault(), "%dm %02ds", elapsedMin, elapsedSec)
}

val targetHr = targetMin / 60
val targetRemMin = targetMin % 60
val targetStr = if (targetHr > 0) {
if (targetRemMin == 0) {
String.format(Locale.getDefault(), "%dh", targetHr)
} else {
String.format(Locale.getDefault(), "%dh %dm", targetHr, targetRemMin)
}
} else {
String.format(Locale.getDefault(), "%dm", targetMin)
}

tvValue.text = elapsedStr
tvTarget.text = String.format(Locale.getDefault(), "of %s", targetStr)

val totalMin = currentSec / 60L
tvValue.setTextColor(
if (totalMin >= targetMin.toLong()) ringColor
else ContextCompat.getColor(requireContext(), R.color.completion_dialog_value_pending)
)

if (isTimerRunning) {
btnPlayPause.setImageResource(R.drawable.ic_pause)
} else {
btnPlayPause.setImageResource(R.drawable.ic_play)
}

// Haptic & Visual completion feedback celebration (runs only once per dialog session)
if (totalMin >= targetMin.toLong() && !hasCelebrated) {
hasCelebrated = true
view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
// Small pop animation on value text
tvValue.animate()
.scaleX(1.15f)
.scaleY(1.15f)
.setDuration(180)
.withEndAction {
tvValue.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
}
.start()
}
if (totalMin < targetMin.toLong()) {
hasCelebrated = false
}
}

refresh()

// Restore active stopwatch if it was running before rotation
if (isTimerRunning) {
startStopwatch(btnPlayPause, circularSeek, isRestoring = true) { refresh() }
}

btnPlayPause.setOnClickListener {
if (isTimerRunning) {
stopStopwatch(btnPlayPause)
} else {
startStopwatch(btnPlayPause, circularSeek) { refresh() }
}
}

// Tap text view to open direct numeric keypad input
tvValue.setOnClickListener {
if (isTimerRunning) {
stopStopwatch(btnPlayPause)
}
showDurationInputDialog(targetMin, circularSeek, ::refresh)
}

circularSeek.setOnCircularChangeListener(object : CircularSeekBarView.OnCircularChangeListener {
override fun onProgressChanged(view: CircularSeekBarView, progress: Int, fromUser: Boolean) {
if (fromUser) {
currentSec = progress * 60L
refresh()
}
}
override fun onStartTrackingTouch(view: CircularSeekBarView) {
if (isTimerRunning) {
stopStopwatch(btnPlayPause)
}
}
override fun onStopTrackingTouch(view: CircularSeekBarView) {
val delta = currentSec - lastEmittedSec
if (delta != 0L) {
emitAction(CompletionAction.TimerAdd(delta))
lastEmittedSec = currentSec
}
}
})

btnDown.setOnClickListener {
if (isTimerRunning) {
stopStopwatch(btnPlayPause)
}
if (currentSec >= 60L) {
currentSec -= 60L
val currentMin = (currentSec / 60L).toInt()
circularSeek.progress = currentMin

val delta = currentSec - lastEmittedSec
if (delta != 0L) {
emitAction(CompletionAction.TimerAdd(delta))
lastEmittedSec = currentSec
}
refresh()
}
}

btnUp.setOnClickListener {
if (isTimerRunning) {
stopStopwatch(btnPlayPause)
}
currentSec += 60L
val currentMin = (currentSec / 60L).toInt()

if (currentMin > circularSeek.max) {
circularSeek.max = currentMin + 5
}
circularSeek.progress = currentMin

val delta = currentSec - lastEmittedSec
if (delta != 0L) {
emitAction(CompletionAction.TimerAdd(delta))
lastEmittedSec = currentSec
}
refresh()
}
}

private fun showDurationInputDialog(targetMin: Int, circularSeek: CircularSeekBarView, refresh: () -> Unit) {
val context = requireContext()
val input = android.widget.EditText(context).apply {
inputType = android.text.InputType.TYPE_CLASS_NUMBER
val currentMin = (currentSec / 60L).toInt()
setText(currentMin.toString())
setSelection(text.length)
}

val container = android.widget.FrameLayout(context).apply {
val padding = (resources.displayMetrics.density * 20).toInt()
setPadding(padding, padding / 2, padding, padding / 2)
addView(input)
}

com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
.setTitle(R.string.enter_duration_title)
.setMessage(R.string.enter_duration_message)
.setView(container)
.setPositiveButton(android.R.string.ok) { _, _ ->
val typedMin = input.text.toString().toIntOrNull() ?: 0
currentSec = typedMin * 60L
val currentMin = (currentSec / 60L).toInt()
if (currentMin > circularSeek.max) {
circularSeek.max = currentMin + 5
}
circularSeek.progress = currentMin

val delta = currentSec - lastEmittedSec
if (delta != 0L) {
emitAction(CompletionAction.TimerAdd(delta))
lastEmittedSec = currentSec
}
refresh()
}
.setNegativeButton(android.R.string.cancel, null)
.show()
}

private fun startStopwatch(
btnPlayPause: android.widget.ImageButton,
circularSeek: CircularSeekBarView,
isRestoring: Boolean = false,
refresh: () -> Unit
) {
isTimerRunning = true
btnPlayPause.setImageResource(R.drawable.ic_pause)

if (!isRestoring) {
startTimeMillis = android.os.SystemClock.elapsedRealtime()
baseSeconds = currentSec
}

timerJob = lifecycleScope.launch {
while (isActive && isTimerRunning) {
delay(200)
val elapsedMillis = android.os.SystemClock.elapsedRealtime() - startTimeMillis
currentSec = baseSeconds + (elapsedMillis / 1000L)

val currentMin = (currentSec / 60L).toInt()
if (currentMin > circularSeek.max) {
circularSeek.max = currentMin + 5
}
circularSeek.progress = currentMin

refresh()

if (currentSec - lastEmittedSec >= 30L) {
val delta = currentSec - lastEmittedSec
if (delta > 0L) {
emitAction(CompletionAction.TimerAdd(delta))
lastEmittedSec = currentSec
}
}
}
}
}

private fun stopStopwatch(btnPlayPause: android.widget.ImageButton) {
isTimerRunning = false
btnPlayPause.setImageResource(R.drawable.ic_play)
timerJob?.cancel()
timerJob = null

val delta = currentSec - lastEmittedSec
if (delta != 0L) {
emitAction(CompletionAction.TimerAdd(delta))
lastEmittedSec = currentSec
}
}

override fun onDismiss(dialog: DialogInterface) {
isTimerRunning = false
timerJob?.cancel()
timerJob = null

if (!requireActivity().isChangingConfigurations &&
this::task.isInitialized &&
task.trackingType == TrackingType.TIMER
) {
val delta = currentSec - lastEmittedSec
if (delta != 0L) {
emitAction(CompletionAction.TimerAdd(delta))
lastEmittedSec = currentSec
}
}

super.onDismiss(dialog)
}

private fun setupChecklist(view: View) {
val sectionHeader = view.findViewById<View>(R.id.checklistHeaderSection)
val container = view.findViewById<LinearLayout>(R.id.checklistContainer)
val tvDoneCount = view.findViewById<TextView>(R.id.tvChecklistDoneCount)
val progressBar = view.findViewById<LinearProgressIndicator>(R.id.checklistProgress)
val taskColor = resolveTaskColor()

sectionHeader.visibility = View.VISIBLE
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

val bg = row.background?.mutate()
if (bg is GradientDrawable) {
val fillColor = if (done) withAlpha(taskColor, 0.08f)
else ContextCompat.getColor(requireContext(), R.color.completion_dialog_row_surface)
bg.setColor(fillColor)

val strokeColor = if (done) withAlpha(taskColor, 0.24f)
else ContextCompat.getColor(requireContext(), R.color.completion_dialog_stroke)
bg.setStroke(
(resources.displayMetrics.density * 1).toInt().coerceAtLeast(1),
strokeColor
)
}

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
emitAction(CompletionAction.ChecklistUpdate(workingChecklistJson))
rebuildRows()
}

row.setOnClickListener {
updateItemState(!done)
}

container.addView(row)
}

val percent = if (array.length() > 0) (doneCount * 100) / array.length() else 0
tvDoneCount.text = getString(R.string.checklist_status_format, doneCount, array.length())
progressBar.setProgress(percent, true)
}

rebuildRows()
}

private fun applyDialogAccent(view: View, taskColor: Int) {
view.findViewById<View>(R.id.viewAccent)?.setSolidBackgroundColorCompat(taskColor)
view.findViewById<Button>(R.id.buttonSave)?.backgroundTintList = ColorStateList.valueOf(taskColor)
view.findViewById<Button>(R.id.btnStepUp)?.backgroundTintList = ColorStateList.valueOf(taskColor)
view.findViewById<TextView>(R.id.tvChecklistDoneCount)?.setTextColor(taskColor)
view.findViewById<TextView>(R.id.tvDateBadge)?.setTextColor(taskColor)
view.findViewById<android.widget.ImageButton>(R.id.btnPlayPause)?.imageTintList = ColorStateList.valueOf(taskColor)
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
today -> getString(R.string.today)
today.minusDays(1) -> getString(R.string.yesterday)
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
gson.fromJson(json, listType)
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

