package com.hikari.app.youtube

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hikari.app.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val JSON = Json { ignoreUnknownKeys = true }

private const val PREFS_NAME = "youtube_auth"

private val Context.youtubeAuthDataStore by preferencesDataStore(name = PREFS_NAME)

@Serializable
data class DeviceCodeResponse(
    val device_code: String,
    val user_code: String,
    val verification_url: String,
    val expires_in: Int,
    val interval: Int,
)

@Serializable
data class TokenResponse(
    val access_token: String?,
    val refresh_token: String?,
    val expires_in: Int?,
    val token_type: String?,
    val scope: String?,
    val error: String?,
    val error_description: String?,
)

@Serializable
data class UserInfoResponse(
    val id: String?,
    val email: String?,
    val name: String?,
    val picture: String?,
)

/** Repository for YouTube device-code OAuth authentication. */
class YouTubeAuthRepository(private val context: Context) {

    private companion object {
        const val CLIENT_ID = "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com"
        const val CLIENT_SECRET = "SboVhoG9s0rNafixCSGGKXAT"
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        const val DEVICE_CODE_URL = "https://oauth2.googleapis.com/device/code"
        const val USER_INFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"
        const val SCOPE = "https://www.googleapis.com/auth/youtube https://www.googleapis.com/auth/youtube.readonly"
        
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_EXPIRES_AT = longPreferencesKey("expires_at")
    }

    private val dataStore = context.youtubeAuthDataStore

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /** Checks if we have a valid access token (refreshing if needed). */
    suspend fun ensureSignedIn(): Boolean {
        val prefs = dataStore.data.first()
        val refreshToken = prefs[KEY_REFRESH_TOKEN] ?: return false
        val expiresAt = prefs[KEY_EXPIRES_AT] ?: 0L
        
        if (System.currentTimeMillis() >= expiresAt - 60_000) {
            return refreshAccessToken(refreshToken)
        }
        return true
    }

    /** Gets the current access token, refreshing if necessary. */
    suspend fun getAccessToken(): String? {
        val prefs = dataStore.data.first()
        var accessToken = prefs[KEY_ACCESS_TOKEN]
        val refreshToken = prefs[KEY_REFRESH_TOKEN]
        val expiresAt = prefs[KEY_EXPIRES_AT] ?: 0L
        
        if (accessToken.isNullOrBlank() || System.currentTimeMillis() >= expiresAt - 60_000) {
            if (refreshToken.isNullOrBlank()) return null
            if (refreshAccessToken(refreshToken)) {
                val freshPrefs = dataStore.data.first()
                accessToken = freshPrefs[KEY_ACCESS_TOKEN]
            } else {
                return null
            }
        }
        return accessToken
    }

    /** Starts the device-code sign-in flow. Returns the user code and verification URL. */
    suspend fun startSignIn(): DeviceCodeResponse? {
        return withContext(Dispatchers.IO) {
            val form = "client_id=$CLIENT_ID&scope=$SCOPE".toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val request = Request.Builder()
                .url(DEVICE_CODE_URL)
                .header("User-Agent", Http.UA)
                .post(form)
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                JSON.decodeFromString<DeviceCodeResponse>(body)
            }
        }
    }

    /** Polls for token after user enters the code. Returns true on success. */
    suspend fun pollForToken(deviceCode: String, interval: Int): Boolean {
        val form = "client_id=$CLIENT_ID&client_secret=$CLIENT_SECRET&device_code=$deviceCode&grant_type=urn:ietf:params:oauth:grant-type:device_code"
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
        
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(TOKEN_URL)
                .header("User-Agent", Http.UA)
                .post(form)
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.contains("authorization_pending")) return@withContext false
                    if (body.contains("slow_down")) {
                        Thread.sleep((interval + 5) * 1000L)
                        return@withContext false
                    }
                    if (body.contains("expired_token") || body.contains("access_denied")) {
                        return@withContext true
                    }
                    return@withContext false
                }
                val body = response.body?.string() ?: ""
                val tokenResponse = JSON.decodeFromString<TokenResponse>(body)
                tokenResponse.access_token?.let { saveTokens(tokenResponse) }
                tokenResponse.access_token != null
            }
        }
    }

    /** Fetches user info to verify sign-in and get display name/avatar. */
    suspend fun getUserInfo(): UserInfoResponse? {
        val token = getAccessToken() ?: return null
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(USER_INFO_URL)
                .header("Authorization", "Bearer $token")
                .header("User-Agent", Http.UA)
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                JSON.decodeFromString<UserInfoResponse>(body)
            }
        }
    }

    /** Signs out by clearing stored tokens. */
    suspend fun signOut(): Boolean {
        return withContext(Dispatchers.IO) {
            dataStore.edit { prefs ->
                prefs.remove(KEY_ACCESS_TOKEN)
                prefs.remove(KEY_REFRESH_TOKEN)
                prefs.remove(KEY_EXPIRES_AT)
            }
            true
        }
    }

    /** Checks if currently signed in (has valid refresh token). */
    suspend fun isSignedIn(): Boolean {
        val prefs = dataStore.data.first()
        return prefs[KEY_REFRESH_TOKEN] != null
    }

    /** Observes sign-in state as a flow. */
    fun observeSignInState(): Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[KEY_REFRESH_TOKEN] != null }
        .distinctUntilChanged()

    private suspend fun refreshAccessToken(refreshToken: String): Boolean {
        return withContext(Dispatchers.IO) {
            val form = "client_id=$CLIENT_ID&client_secret=$CLIENT_SECRET&refresh_token=$refreshToken&grant_type=refresh_token"
                .toRequestBody("application/x-www-form-urlencoded".toMediaType())
            
            val request = Request.Builder()
                .url(TOKEN_URL)
                .header("User-Agent", Http.UA)
                .post(form)
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body?.string() ?: ""
                val tokenResponse = JSON.decodeFromString<TokenResponse>(body)
                tokenResponse.access_token?.let { saveTokens(tokenResponse.copy(refresh_token = refreshToken)) }
                tokenResponse.access_token != null
            }
        }
    }

    private suspend fun saveTokens(tokenResponse: TokenResponse) {
        val expiresAt = System.currentTimeMillis() + (tokenResponse.expires_in ?: 3600) * 1000L
        dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = tokenResponse.access_token ?: ""
            prefs[KEY_REFRESH_TOKEN] = tokenResponse.refresh_token ?: ""
            prefs[KEY_EXPIRES_AT] = expiresAt
        }
    }
}