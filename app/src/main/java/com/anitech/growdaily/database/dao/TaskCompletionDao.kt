package com.anitech.growdaily.database.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.anitech.growdaily.data_class.TaskCompletionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskCompletionDao {

    // ── Core upsert ───────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletion(entity: TaskCompletionEntity)

    // ── Single row fetch ──────────────────────────────────────────────────────

    @Query(
        """
        SELECT * FROM task_completions 
        WHERE taskId = :taskId AND date = :date
        LIMIT 1
        """
    )
    suspend fun isTaskCompletedOnDate(
        taskId: String,
        date: String
    ): TaskCompletionEntity?

    // ── TIMER: accumulate duration ────────────────────────────────────────────

    /**
     * Adds [addSeconds] to the existing durationSeconds for this (taskId, date) row.
     * If no row exists yet, creates one with durationSeconds = [addSeconds].
     * count is preserved / defaulted to 0 so existing logic is unaffected.
     */
    @Transaction
    suspend fun addDuration(taskId: String, date: String, addSeconds: Long) {
        val existing = isTaskCompletedOnDate(taskId, date)
        if (existing == null) {
            insertCompletion(
                TaskCompletionEntity(
                    taskId = taskId,
                    date = date,
                    durationSeconds = addSeconds
                )
            )
        } else {
            insertCompletion(
                existing.copy(durationSeconds = existing.durationSeconds + addSeconds)
            )
        }
    }

    // ── CHECKLIST: update per-day checked state ───────────────────────────────

    /**
     * Upserts the checklistJson for this (taskId, date) row.
     * If no row exists yet, creates one with count = 0 and durationSeconds = 0.
     */
    @Transaction
    suspend fun updateChecklist(taskId: String, date: String, checklistJson: String) {
        val existing = isTaskCompletedOnDate(taskId, date)
        if (existing == null) {
            insertCompletion(
                TaskCompletionEntity(
                    taskId = taskId,
                    date = date,
                    checklistJson = checklistJson
                )
            )
        } else {
            insertCompletion(
                existing.copy(checklistJson = checklistJson)
            )
        }
    }

    // ── Bulk / utility queries (unchanged) ───────────────────────────────────

    @Query(
        """
        SELECT MIN(date) FROM task_completions
        WHERE taskId = :taskId
        """
    )
    suspend fun getFirstCompletionDate(taskId: String): String?

    @Query(
        """
        DELETE FROM task_completions 
        WHERE taskId = :taskId
        """
    )
    suspend fun deleteAllForTask(taskId: String)

    @Query(
        """
        DELETE FROM task_completions 
        WHERE taskId = :taskId AND date = :date
        """
    )
    suspend fun delete(taskId: String, date: String)

    @Query("SELECT * FROM task_completions")
    fun getAllCompletions(): LiveData<List<TaskCompletionEntity>>

    @Query(
        """
        SELECT taskId FROM task_completions
        WHERE date = :date
        """
    )
    suspend fun getCompletedTaskIdsForDate(date: String): List<String>

    @Query("SELECT * FROM task_completions")
    fun getAllCompletionsTaskData(): LiveData<List<TaskCompletionEntity>>

    @Query(
        """
        DELETE FROM task_completions
        WHERE taskId = :taskId AND date < :newStartDate
        """
    )
    suspend fun deleteCompletionsBefore(taskId: String, newStartDate: String)

    @Query(
        """
        SELECT * FROM task_completions
        WHERE taskId = :taskId
        ORDER BY date ASC
        """
    )
    fun getCompletionsForTask(taskId: String): LiveData<List<TaskCompletionEntity>>

    @Query("SELECT * FROM task_completions WHERE date = :date")
    suspend fun getCompletionsForDateSuspend(date: String): List<TaskCompletionEntity>

    @Query("SELECT * FROM task_completions")
    fun getAllCompletionsFlow(): Flow<List<TaskCompletionEntity>>
}
