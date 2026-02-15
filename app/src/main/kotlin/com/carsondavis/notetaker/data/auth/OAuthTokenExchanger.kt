package com.carsondavis.notetaker.data.auth

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class OAuthTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    val scope: String = ""
)

@Serializable
data class RevokeTokenRequest(
    @SerialName("access_token") val accessToken: String
)

@Singleton
class OAuthTokenExchanger @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {
    suspend fun exchangeCode(code: String, codeVerifier: String): OAuthTokenResponse {
        return withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("client_id", OAuthConfig.CLIENT_ID)
                .add("client_secret", OAuthConfig.CLIENT_SECRET)
                .add("code", code)
                .add("redirect_uri", OAuthConfig.REDIRECT_URI_HTTPS)
                .add("code_verifier", codeVerifier)
                .build()

            val request = Request.Builder()
                .url(OAuthConfig.TOKEN_URL)
                .header("Accept", "application/json")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: throw Exception("Empty response from token exchange")

            if (!response.isSuccessful) {
                throw Exception("Token exchange failed (${response.code}): $responseBody")
            }

            json.decodeFromString<OAuthTokenResponse>(responseBody)
        }
    }

    suspend fun revokeToken(accessToken: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val credentials = "${OAuthConfig.CLIENT_ID}:${OAuthConfig.CLIENT_SECRET}"
                val basicAuth = "Basic " + Base64.encodeToString(
                    credentials.toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP
                )

                val requestBody = json.encodeToString(RevokeTokenRequest(accessToken))
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("https://api.github.com/applications/${OAuthConfig.CLIENT_ID}/token")
                    .header("Authorization", basicAuth)
                    .header("Accept", "application/vnd.github+json")
                    .delete(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                response.close()
                response.code == 204
            }
        } catch (_: Exception) {
            false
        }
    }
}
