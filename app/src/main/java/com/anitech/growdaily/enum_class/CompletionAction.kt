package com.anitech.growdaily.enum_class

/** Typed result emitted by [CompletionInputDialog] back to the caller. */
sealed class CompletionAction {
    /** COUNT: increment or decrement by [delta] (positive = add, negative = subtract). */
    data class CountDelta(val delta: Int) : CompletionAction()
    /** TIMER: add [seconds] to today's accumulated duration. */
    data class TimerAdd(val seconds: Long) : CompletionAction()
    /** CHECKLIST: full updated JSON string after a toggle. */
    data class ChecklistUpdate(val json: String) : CompletionAction()
}