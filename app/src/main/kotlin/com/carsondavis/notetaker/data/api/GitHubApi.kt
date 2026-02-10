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
        @Path("path") path: String
    ): GitHubFileContent

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun createFile(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body request: CreateFileRequest
    ): CreateFileResponse
}

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
