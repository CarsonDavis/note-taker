package com.carsondavis.notetaker.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path

interface GitHubApi {

    // --- User ---

    @GET("user")
    suspend fun getUser(
        @Header("Authorization") auth: String
    ): GitHubUser

    // --- Contents API ---

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileContent(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String
    ): GitHubFileContent

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getDirectoryContents(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String
    ): List<GitHubDirectoryEntry>

    @GET("repos/{owner}/{repo}/contents/")
    suspend fun getRootContents(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<GitHubDirectoryEntry>

    // --- Repository ---

    @GET("repos/{owner}/{repo}")
    suspend fun getRepository(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRepository

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun createFile(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Body request: CreateFileRequest
    ): CreateFileResponse
}

// --- Repository model ---

@Serializable
data class GitHubRepository(
    val id: Long,
    @SerialName("full_name") val fullName: String
)

// --- Request/Response models ---

@Serializable
data class GitHubUser(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

@Serializable
data class GitHubFileContent(
    val content: String? = null,
    val encoding: String? = null,
    val sha: String? = null
)

@Serializable
data class GitHubDirectoryEntry(
    val name: String,
    val path: String,
    val type: String, // "file" or "dir"
    val size: Long = 0
)

@Serializable
data class CreateFileRequest(
    val message: String,
    val content: String
)

@Serializable
data class CreateFileResponse(
    val content: GitHubFileContentRef? = null
)

@Serializable
data class GitHubFileContentRef(
    val name: String? = null,
    val path: String? = null,
    val sha: String? = null
)
