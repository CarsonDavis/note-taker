package com.carsondavis.notetaker.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubApi {

    // --- Device Flow Auth (form-encoded, not JSON) ---

    @FormUrlEncoded
    @Headers("Accept: application/json")
    @POST("https://github.com/login/device/code")
    suspend fun requestDeviceCode(
        @Field("client_id") clientId: String,
        @Field("scope") scope: String = "repo"
    ): DeviceCodeResponse

    @FormUrlEncoded
    @Headers("Accept: application/json")
    @POST("https://github.com/login/oauth/access_token")
    suspend fun pollAccessToken(
        @Field("client_id") clientId: String,
        @Field("device_code") deviceCode: String,
        @Field("grant_type") grantType: String = "urn:ietf:params:oauth:grant-type:device_code"
    ): AccessTokenResponse

    // --- User & Repos ---

    @GET("user")
    suspend fun getUser(
        @Header("Authorization") auth: String
    ): GitHubUser

    @GET("user/repos")
    suspend fun getUserRepos(
        @Header("Authorization") auth: String,
        @Query("sort") sort: String = "updated",
        @Query("per_page") perPage: Int = 100
    ): List<GitHubRepo>

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
data class DeviceCodeRequest(
    @SerialName("client_id") val clientId: String,
    val scope: String = "repo"
)

@Serializable
data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int
)

@Serializable
data class AccessTokenRequest(
    @SerialName("client_id") val clientId: String,
    @SerialName("device_code") val deviceCode: String,
    @SerialName("grant_type") val grantType: String = "urn:ietf:params:oauth:grant-type:device_code"
)

@Serializable
data class AccessTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null
)

@Serializable
data class GitHubUser(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

@Serializable
data class GitHubRepo(
    val name: String,
    @SerialName("full_name") val fullName: String,
    val private: Boolean = false
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
