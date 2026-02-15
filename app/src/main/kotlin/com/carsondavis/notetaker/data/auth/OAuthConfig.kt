package com.carsondavis.notetaker.data.auth

import android.util.Base64
import com.carsondavis.notetaker.BuildConfig
import java.security.MessageDigest
import java.security.SecureRandom

object OAuthConfig {
    val CLIENT_ID: String = BuildConfig.GITHUB_CLIENT_ID
    val CLIENT_SECRET: String = BuildConfig.GITHUB_CLIENT_SECRET

    const val REDIRECT_URI_HTTPS = "https://madebycarson.com/gitjot-oauth/callback"
    const val REDIRECT_URI_APP = "notetaker://callback"

    const val APP_INSTALL_URL = "https://github.com/apps/gitjot-oauth/installations/select_target"
    const val AUTHORIZE_URL = "https://github.com/login/oauth/authorize"
    const val TOKEN_URL = "https://github.com/login/oauth/access_token"

    fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun generateState(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
