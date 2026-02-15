package com.carsondavis.notetaker.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class OAuthTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    val scope: String = ""
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
}
