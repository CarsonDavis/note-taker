package com.carsondavis.notetaker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SubmissionDao {
    @Insert
    suspend fun insert(submission: SubmissionEntity)

    @Query("SELECT * FROM submissions ORDER BY timestamp DESC LIMIT 10")
    fun getRecent(): Flow<List<SubmissionEntity>>

    @Query("DELETE FROM submissions")
    suspend fun deleteAll()
}
