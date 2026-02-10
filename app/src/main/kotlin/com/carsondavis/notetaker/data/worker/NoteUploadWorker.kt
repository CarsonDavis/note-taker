package com.carsondavis.notetaker.data.worker

import android.content.Context
import android.util.Base64
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.carsondavis.notetaker.data.api.CreateFileRequest
import com.carsondavis.notetaker.data.api.GitHubApi
import com.carsondavis.notetaker.data.auth.AuthManager
import com.carsondavis.notetaker.data.local.PendingNoteDao
import com.carsondavis.notetaker.data.local.SubmissionDao
import com.carsondavis.notetaker.data.local.SubmissionEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

@HiltWorker
class NoteUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val api: GitHubApi,
    private val authManager: AuthManager,
    private val pendingNoteDao: PendingNoteDao,
    private val submissionDao: SubmissionDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val token = authManager.accessToken.first() ?: return Result.retry()
        val owner = authManager.repoOwner.first() ?: return Result.retry()
        val repo = authManager.repoName.first() ?: return Result.retry()

        val pending = pendingNoteDao.getAllPending()
        if (pending.isEmpty()) return Result.success()

        for (note in pending) {
            pendingNoteDao.updateStatus(note.id, "uploading")
            try {
                val path = "inbox/${note.filename}.md"
                val content = Base64.encodeToString(note.text.toByteArray(), Base64.NO_WRAP)

                try {
                    api.createFile(
                        auth = "Bearer $token",
                        owner = owner,
                        repo = repo,
                        path = path,
                        request = CreateFileRequest(
                            message = "Add note ${note.filename}",
                            content = content
                        )
                    )
                } catch (e: HttpException) {
                    if (e.code() == 422) {
                        // Conflict — try with suffix
                        val altPath = "inbox/${note.filename}-1.md"
                        api.createFile(
                            auth = "Bearer $token",
                            owner = owner,
                            repo = repo,
                            path = altPath,
                            request = CreateFileRequest(
                                message = "Add note ${note.filename}-1",
                                content = content
                            )
                        )
                    } else {
                        throw e
                    }
                }

                submissionDao.insert(
                    SubmissionEntity(
                        timestamp = System.currentTimeMillis(),
                        preview = note.text.take(50),
                        success = true
                    )
                )
                pendingNoteDao.delete(note.id)
            } catch (_: Exception) {
                pendingNoteDao.updateStatus(note.id, "failed")
                return Result.retry()
            }
        }

        return Result.success()
    }
}
