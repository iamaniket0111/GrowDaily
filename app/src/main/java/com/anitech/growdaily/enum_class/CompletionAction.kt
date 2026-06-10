package com.anitech.growdaily.enum_class

import android.os.Bundle
import androidx.core.os.bundleOf

/** Typed result emitted by the completion input dialog back to the caller. */
sealed class CompletionAction {
    /** COUNT: increment or decrement by [delta] (positive = add, negative = subtract). */
    data class CountDelta(val delta: Int) : CompletionAction()
    /** TIMER: add [seconds] to today's accumulated duration. */
    data class TimerAdd(val seconds: Long) : CompletionAction()
    /** CHECKLIST: full updated JSON string after a toggle. */
    data class ChecklistUpdate(val json: String) : CompletionAction()

    companion object {
        private const val ARG_TASK_ID = "taskId"
        private const val ARG_DATE = "date"
        private const val ARG_ACTION_KIND = "actionKind"
        private const val KIND_COUNT = "count"
        private const val KIND_TIMER = "timer"
        private const val KIND_CHECKLIST = "checklist"
        private const val ARG_COUNT_DELTA = "countDelta"
        private const val ARG_TIMER_SECONDS = "timerSeconds"
        private const val ARG_CHECKLIST_JSON = "checklistJson"

        fun toResultBundle(taskId: String, date: String, action: CompletionAction): Bundle {
            val extras = when (action) {
                is CountDelta -> bundleOf(
                    ARG_ACTION_KIND to KIND_COUNT,
                    ARG_COUNT_DELTA to action.delta
                )
                is TimerAdd -> bundleOf(
                    ARG_ACTION_KIND to KIND_TIMER,
                    ARG_TIMER_SECONDS to action.seconds
                )
                is ChecklistUpdate -> bundleOf(
                    ARG_ACTION_KIND to KIND_CHECKLIST,
                    ARG_CHECKLIST_JSON to action.json
                )
            }
            extras.putString(ARG_TASK_ID, taskId)
            extras.putString(ARG_DATE, date)
            return extras
        }

        fun fromResultBundle(bundle: Bundle): Triple<String, String, CompletionAction>? {
            val taskId = bundle.getString(ARG_TASK_ID) ?: return null
            val date = bundle.getString(ARG_DATE) ?: return null
            val action = when (bundle.getString(ARG_ACTION_KIND)) {
                KIND_COUNT -> {
                    if (!bundle.containsKey(ARG_COUNT_DELTA)) return null
                    CountDelta(bundle.getInt(ARG_COUNT_DELTA))
                }
                KIND_TIMER -> TimerAdd(bundle.getLong(ARG_TIMER_SECONDS))
                KIND_CHECKLIST -> {
                    val json = bundle.getString(ARG_CHECKLIST_JSON) ?: return null
                    ChecklistUpdate(json)
                }
                else -> return null
            }
            return Triple(taskId, date, action)
        }
    }
}