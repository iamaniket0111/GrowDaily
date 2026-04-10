package com.anitech.growdaily.data_class

import androidx.room.Entity

@Entity(
    tableName = "task_completions",
    primaryKeys = ["taskId", "date"]
)
data class TaskCompletionEntity(
    val taskId: String,
    val date: String,

    // ── COUNT / BINARY ────────────────────────────────────────────────────────
    /** BINARY: 1 = done, 0 = not done.
     *  COUNT : number of times completed (up to dailyTargetCount). */
    val count: Int = 0,

    // ── TIMER ─────────────────────────────────────────────────────────────────
    /** Accumulated seconds tracked on this date. 0 when not a TIMER task. */
    val durationSeconds: Long = 0L,

    // ── CHECKLIST ─────────────────────────────────────────────────────────────
    /**
     * JSON array that mirrors the fixed labels from TaskEntity.checklistItems,
     * but carries the per-day checked state.
     * e.g. [{"label":"Warm up","done":true},{"label":"Main set","done":false}]
     * Null when not a CHECKLIST task.
     */
    val checklistJson: String? = null
)