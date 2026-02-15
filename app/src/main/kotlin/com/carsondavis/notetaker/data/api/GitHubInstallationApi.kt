package com.carsondavis.notetaker.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface GitHubInstallationApi {

    @GET("user/installations")
    suspend fun getInstallations(
        @Header("Authorization") auth: String
    ): InstallationsResponse

    @GET("user/installations/{installation_id}/repositories")
    suspend fun getInstallationRepos(
        @Header("Authorization") auth: String,
        @Path("installation_id") installationId: Long
    ): InstallationReposResponse
}

@Serializable
data class InstallationsResponse(
    val installations: List<Installation>
)

@Serializable
data class Installation(
    val id: Long,
    @SerialName("app_id") val appId: Long
)

@Serializable
data class InstallationReposResponse(
    val repositories: List<InstallationRepo>
)

@Serializable
data class InstallationRepo(
    val id: Long,
    @SerialName("full_name") val fullName: String,
    val name: String,
    val owner: RepoOwner
)

@Serializable
data class RepoOwner(
    val login: String
)
