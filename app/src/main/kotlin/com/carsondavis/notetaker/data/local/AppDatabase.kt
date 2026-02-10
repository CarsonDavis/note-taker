package com.carsondavis.notetaker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SubmissionEntity::class, PendingNoteEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun submissionDao(): SubmissionDao
    abstract fun pendingNoteDao(): PendingNoteDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `pending_notes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `text` TEXT NOT NULL,
                        `filename` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `status` TEXT NOT NULL DEFAULT 'pending'
                    )"""
                )
            }
        }
    }
}
