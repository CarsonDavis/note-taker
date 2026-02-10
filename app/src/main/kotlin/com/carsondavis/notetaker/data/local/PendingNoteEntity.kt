package com.carsondavis.notetaker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_notes")
data class PendingNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val filename: String,
    val createdAt: Long,
    val status: String = "pending" // pending, uploading, failed
)
