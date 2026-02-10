package com.carsondavis.notetaker.data.repository

import android.util.Base64
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.carsondavis.notetaker.data.api.CreateFileRequest
import com.carsondavis.notetaker.data.api.GitHubApi
import com.carsondavis.notetaker.data.api.GitHubDirectoryEntry
import com.carsondavis.notetaker.data.auth.AuthManager
import com.carsondavis.notetaker.data.local.PendingNoteDao
import com.carsondavis.notetaker.data.local.PendingNoteEntity
import com.carsondavis.notetaker.data.local.SubmissionDao
import com.carsondavis.notetaker.data.local.SubmissionEntity
import com.carsondavis.notetaker.data.worker.NoteUploadWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

enum class SubmitResult { SENT, QUEUED }

@Singleton
class NoteRepository @Inject constructor(
    private val api: GitHubApi,
    private val authManager: AuthManager,
    private val submissionDao: SubmissionDao,
    private val pendingNoteDao: PendingNoteDao,
    private val workManager: WorkManager
) {
    val recentSubmissions: Flow<List<SubmissionEntity>> = submissionDao.getRecent()
    val pendingCount: Flow<Int> = pendingNoteDao.getPendingCount()

    suspend fun submitNote(text: String): Result<SubmitResult> {
        val now = ZonedDateTime.now()
        val filename = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmssZ"))

        // Always save to local queue first
        val noteId = pendingNoteDao.insert(
            PendingNoteEntity(
                text = text,
                filename = filename,
                createdAt = System.currentTimeMillis()
            )
        )

        return try {
            val token = authManager.accessToken.first()
                ?: throw Exception("Not authenticated")
            val owner = authManager.repoOwner.first()
                ?: throw Exception("No repo configured")
            val repo = authManager.repoName.first()
                ?: throw Exception("No repo configured")

            val path = "inbox/$filename.md"
            val content = Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP)

            api.createFile(
                auth = "Bearer $token",
                owner = owner,
                repo = repo,
                path = path,
                request = CreateFileRequest(
                    message = "Add note $filename",
                    content = content
                )
            )

            // Success — record in submissions and remove from queue
            submissionDao.insert(
                SubmissionEntity(
                    timestamp = System.currentTimeMillis(),
                    preview = text.take(50),
                    success = true
                )
            )
            pendingNoteDao.delete(noteId)

            Result.success(SubmitResult.SENT)
        } catch (_: Exception) {
            // Failed — schedule WorkManager retry
            scheduleRetry()
            Result.success(SubmitResult.QUEUED)
        }
    }

    private fun scheduleRetry() {
        val request = OneTimeWorkRequestBuilder<NoteUploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(
            "note_upload",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    suspend fun fetchDirectoryContents(path: String): Result<List<GitHubDirectoryEntry>> {
        return try {
            val token = authManager.accessToken.first()
                ?: return Result.failure(Exception("Not authenticated"))
            val owner = authManager.repoOwner.first()
                ?: return Result.failure(Exception("No repo configured"))
            val repo = authManager.repoName.first()
                ?: return Result.failure(Exception("No repo configured"))

            val entries = if (path.isEmpty()) {
                api.getRootContents(auth = "Bearer $token", owner = owner, repo = repo)
            } else {
                api.getDirectoryContents(auth = "Bearer $token", owner = owner, repo = repo, path = path)
            }

            // Sort: dirs first, then alphabetical
            val sorted = entries.sortedWith(compareBy<GitHubDirectoryEntry> { it.type != "dir" }.thenBy { it.name })
            Result.success(sorted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchFileContent(path: String): Result<String> {
        return try {
            val token = authManager.accessToken.first()
                ?: return Result.failure(Exception("Not authenticated"))
            val owner = authManager.repoOwner.first()
                ?: return Result.failure(Exception("No repo configured"))
            val repo = authManager.repoName.first()
                ?: return Result.failure(Exception("No repo configured"))

            val response = api.getFileContent(
                auth = "Bearer $token",
                owner = owner,
                repo = repo,
                path = path
            )

            val decoded = response.content?.let { encoded ->
                String(Base64.decode(encoded.replace("\n", ""), Base64.DEFAULT))
            } ?: ""

            Result.success(decoded)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCurrentTopic(): String? {
        return try {
            val token = authManager.accessToken.first() ?: return null
            val owner = authManager.repoOwner.first() ?: return null
            val repo = authManager.repoName.first() ?: return null

            val response = api.getFileContent(
                auth = "Bearer $token",
                owner = owner,
                repo = repo,
                path = ".current_topic"
            )

            response.content?.let { encoded ->
                String(Base64.decode(encoded.replace("\n", ""), Base64.DEFAULT)).trim()
            }
        } catch (_: Exception) {
            null
        }
    }

}
