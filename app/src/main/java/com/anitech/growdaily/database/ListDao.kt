package com.anitech.growdaily.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.data_class.ListTaskCrossRef
import com.anitech.growdaily.data_class.ListWithTasks
import kotlinx.coroutines.flow.Flow

@Dao
interface ListDao {

    @Insert
    suspend fun insertList(list: ListEntity)

    @Query("SELECT * FROM list_table ORDER BY sortOrder ASC")
    fun getAllLists(): LiveData<List<ListEntity>>

    @Query("SELECT taskId FROM list_task_cross_ref WHERE listId = :listId")
    fun getTaskIdsForListFlow(listId: String): Flow<List<String>>

    @Update
    suspend fun updateList(list: ListEntity)

    @Insert
    suspend fun insertListTask(ref: ListTaskCrossRef)

    @Delete
    suspend fun deleteListTask(ref: ListTaskCrossRef)

    @Transaction
    @Query("SELECT * FROM list_table WHERE id = :listId")
    fun getListWithTasks(listId: String): LiveData<ListWithTasks>

    @Query("SELECT taskId FROM list_task_cross_ref WHERE listId = :listId")
    suspend fun getTaskIdsForList(listId: String): List<String>

    @Query("SELECT listId FROM list_task_cross_ref WHERE taskId = :taskId")
    suspend fun getListIdsForTask(taskId: String): List<String>

    @Update
    suspend fun updateLists(lists: List<ListEntity>)


    @Delete
    suspend fun deleteList(list: ListEntity)

    @Query("DELETE FROM list_task_cross_ref WHERE listId = :listId")
    suspend fun deleteAllTaskRefsForList(listId: String)

    @Query("DELETE FROM list_task_cross_ref WHERE taskId = :taskId")
    suspend fun removeTaskFromAllLists(taskId: String)

}