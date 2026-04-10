package com.anitech.growdaily.database.util

import com.anitech.growdaily.data_class.TaskCompletionEntity
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.TaskTrackingVersionEntity
import com.anitech.growdaily.enum_class.TrackingType
import org.json.JSONArray

// ── Pure completion derivation ────────────────────────────────────────────────

data class EffectiveTrackingSettings(
    val weightValue: Int,
    val dailyTargetCount: Int,
    val targetDurationSeconds: Long,
    val checklistItemsJson: String?
)

fun resolveTrackingSettings(
    task: TaskEntity,
    date: String,
    versions: List<TaskTrackingVersionEntity>
): EffectiveTrackingSettings {
    val matching = versions
        .filter { it.effectiveFromDate <= date }
        .maxByOrNull { it.effectiveFromDate }

    return if (matching != null) {
        EffectiveTrackingSettings(
            weightValue = matching.weightValue,
            dailyTargetCount = matching.dailyTargetCount,
            targetDurationSeconds = matching.targetDurationSeconds,
            checklistItemsJson = matching.checklistItemsJson
        )
    } else {
        EffectiveTrackingSettings(
            weightValue = task.weight.weight,
            dailyTargetCount = task.dailyTargetCount,
            targetDurationSeconds = task.targetDurationSeconds,
            checklistItemsJson = task.checklistItems
        )
    }
}

/**
 * Returns true when [completion] satisfies the completion threshold defined
 * by [task]'s trackingType. Works for all 4 types.
 *
 * Rules:
 *  BINARY    — count >= 1
 *  COUNT     — count >= dailyTargetCount
 *  TIMER     — durationSeconds >= targetDurationSeconds  (and target > 0)
 *  CHECKLIST — all items in checklistJson have "done": true
 *
 * A null [completion] always returns false.
 */
fun isCompletedDerived(task: TaskEntity, completion: TaskCompletionEntity?): Boolean {
    return isCompletedDerived(
        task = task,
        completion = completion,
        settings = EffectiveTrackingSettings(
            weightValue = task.weight.weight,
            dailyTargetCount = task.dailyTargetCount,
            targetDurationSeconds = task.targetDurationSeconds,
            checklistItemsJson = task.checklistItems
        )
    )
}

fun isCompletedDerived(
    task: TaskEntity,
    completion: TaskCompletionEntity?,
    settings: EffectiveTrackingSettings
): Boolean {
    if (completion == null) return false

    return when (task.trackingType) {
        TrackingType.BINARY ->
            completion.count >= 1

        TrackingType.COUNT ->
            completion.count >= settings.dailyTargetCount.coerceAtLeast(1)

        TrackingType.TIMER ->
            settings.targetDurationSeconds > 0 &&
                    completion.durationSeconds >= settings.targetDurationSeconds

        TrackingType.CHECKLIST ->
            checklistAllDone(completion.checklistJson)
    }
}

fun completionPercent(task: TaskEntity, completion: TaskCompletionEntity?): Int {
    return completionPercent(
        task = task,
        completion = completion,
        settings = EffectiveTrackingSettings(
            weightValue = task.weight.weight,
            dailyTargetCount = task.dailyTargetCount,
            targetDurationSeconds = task.targetDurationSeconds,
            checklistItemsJson = task.checklistItems
        )
    )
}

fun completionPercent(
    task: TaskEntity,
    completion: TaskCompletionEntity?,
    settings: EffectiveTrackingSettings
): Int {
    if (completion == null) return 0

    val raw = when (task.trackingType) {
        TrackingType.BINARY -> if (completion.count >= 1) 100f else 0f

        TrackingType.COUNT -> {
            val target = settings.dailyTargetCount.coerceAtLeast(1)
            (completion.count.toFloat() / target.toFloat()) * 100f
        }

        TrackingType.TIMER -> {
            val target = settings.targetDurationSeconds.coerceAtLeast(60L)
            (completion.durationSeconds.toFloat() / target.toFloat()) * 100f
        }

        TrackingType.CHECKLIST -> checklistCompletionPercent(checklistJson = completion.checklistJson)
    }

    return raw.toInt().coerceIn(0, 100)
}

/**
 * Returns true only when [checklistJson] is non-null, non-empty, and every
 * item object has "done": true.
 */
fun checklistAllDone(checklistJson: String?): Boolean {
    if (checklistJson.isNullOrBlank()) return false
    return try {
        val array = JSONArray(checklistJson)
        if (array.length() == 0) return false
        (0 until array.length()).all { i ->
            array.getJSONObject(i).optBoolean("done", false)
        }
    } catch (e: Exception) {
        false
    }
}

fun checklistCompletionPercent(checklistJson: String?): Float {
    if (checklistJson.isNullOrBlank()) return 0f
    return try {
        val array = JSONArray(checklistJson)
        if (array.length() == 0) return 0f
        val doneCount = (0 until array.length()).count { i ->
            array.getJSONObject(i).optBoolean("done", false)
        }
        (doneCount.toFloat() / array.length().toFloat()) * 100f
    } catch (e: Exception) {
        0f
    }
}

/**
 * Builds the initial checklistJson string from a task's fixed label list.
 * All items start as not done.
 *
 * Input  : ["Warm up", "Main set", "Cool down"]
 * Output : [{"label":"Warm up","done":false},{"label":"Main set","done":false},...]
 */
fun buildInitialChecklistJson(labels: List<String>): String {
    val array = JSONArray()
    labels.forEach { label ->
        array.put(
            org.json.JSONObject().apply {
                put("label", label)
                put("done", false)
            }
        )
    }
    return array.toString()
}

/**
 * Toggles a single checklist item at [index] in [checklistJson] and returns
 * the updated JSON string.
 */
fun toggleChecklistItem(checklistJson: String, index: Int): String {
    val array = JSONArray(checklistJson)
    val item = array.getJSONObject(index)
    item.put("done", !item.optBoolean("done", false))
    array.put(index, item)
    return array.toString()
}

// ── AppRepository new methods (added via extension to keep the original file minimal) ──

/**
 * Adds [seconds] to the timer duration for [taskId] on [date].
 * Creates the row if it doesn't exist yet.
 */
