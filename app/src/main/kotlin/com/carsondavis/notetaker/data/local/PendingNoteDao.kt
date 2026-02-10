package com.carsondavis.notetaker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingNoteDao {
    @Insert
    suspend fun insert(note: PendingNoteEntity): Long

    @Query("SELECT * FROM pending_notes WHERE status IN ('pending', 'failed') ORDER BY createdAt ASC")
    suspend fun getAllPending(): List<PendingNoteEntity>

    @Query("SELECT COUNT(*) FROM pending_notes")
    fun getPendingCount(): Flow<Int>

    @Query("UPDATE pending_notes SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("DELETE FROM pending_notes WHERE id = :id")
    suspend fun delete(id: Long)
}
