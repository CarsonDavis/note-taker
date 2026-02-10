package com.carsondavis.notetaker.data.repository

import android.util.Base64
import com.carsondavis.notetaker.data.api.CreateFileRequest
import com.carsondavis.notetaker.data.api.GitHubApi
import com.carsondavis.notetaker.data.auth.AuthManager
import com.carsondavis.notetaker.data.local.SubmissionDao
import com.carsondavis.notetaker.data.local.SubmissionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val api: GitHubApi,
    private val authManager: AuthManager,
    private val submissionDao: SubmissionDao
) {
    val recentSubmissions: Flow<List<SubmissionEntity>> = submissionDao.getRecent()

    suspend fun submitNote(text: String): Result<Unit> {
        return try {
            val token = authManager.accessToken.first()
                ?: return Result.failure(Exception("Not authenticated"))
            val owner = authManager.repoOwner.first()
                ?: return Result.failure(Exception("No repo configured"))
            val repo = authManager.repoName.first()
                ?: return Result.failure(Exception("No repo configured"))

            val now = ZonedDateTime.now()
            val filename = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmssZ"))
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

            submissionDao.insert(
                SubmissionEntity(
                    timestamp = System.currentTimeMillis(),
                    preview = text.take(50),
                    success = true
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            submissionDao.insert(
                SubmissionEntity(
                    timestamp = System.currentTimeMillis(),
                    preview = text.take(50),
                    success = false
                )
            )
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
