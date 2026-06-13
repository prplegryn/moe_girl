package com.prplegryn.moegirl.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "moe_session")

class SessionStore(private val context: Context) {
    val sessionFlow: Flow<Session> = context.sessionDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            Session(
                accessToken = prefs[ACCESS_TOKEN].orEmpty(),
                refreshToken = prefs[REFRESH_TOKEN],
                expiresAtMillis = prefs[EXPIRES_AT],
                phone = prefs[PHONE],
                deviceId = prefs[DEVICE_ID].orEmpty(),
                userName = prefs[USER_NAME],
                avatarUrl = prefs[AVATAR_URL],
                rootId = prefs[ROOT_ID],
                rootName = prefs[ROOT_NAME] ?: "根目录",
            )
        }

    suspend fun getSession(): Session = sessionFlow.first()

    suspend fun getOrCreateDeviceId(): String {
        val existing = context.sessionDataStore.data.first()[DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing
        val generated = generateDid()
        context.sessionDataStore.edit { it[DEVICE_ID] = generated }
        return generated
    }

    suspend fun saveTokens(tokens: SessionTokens, phone: String? = null, deviceId: String? = null) {
        context.sessionDataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = tokens.accessToken
            tokens.refreshToken?.let { prefs[REFRESH_TOKEN] = it }
            tokens.expiresAtMillis?.let { prefs[EXPIRES_AT] = it }
            phone?.let { prefs[PHONE] = it }
            deviceId?.let { prefs[DEVICE_ID] = it }
        }
    }

    suspend fun saveUser(profile: UserProfile) {
        context.sessionDataStore.edit { prefs ->
            prefs[USER_NAME] = profile.name
            profile.phone?.let { prefs[PHONE] = it }
            if (profile.avatarUrl.isNullOrBlank()) {
                prefs.remove(AVATAR_URL)
            } else {
                prefs[AVATAR_URL] = profile.avatarUrl
            }
        }
    }

    suspend fun setRoot(id: String?, name: String) {
        context.sessionDataStore.edit { prefs ->
            if (id.isNullOrBlank()) {
                prefs.remove(ROOT_ID)
                prefs[ROOT_NAME] = "根目录"
            } else {
                prefs[ROOT_ID] = id
                prefs[ROOT_NAME] = name
            }
        }
    }

    suspend fun clearLogin() {
        context.sessionDataStore.edit { prefs ->
            val deviceId = prefs[DEVICE_ID]
            prefs.clear()
            if (!deviceId.isNullOrBlank()) prefs[DEVICE_ID] = deviceId
        }
    }

    suspend fun savePlaybackPosition(fileId: String, positionMs: Long) {
        if (fileId.isBlank() || positionMs <= 0L) return
        context.sessionDataStore.edit { prefs ->
            prefs[playbackKey(fileId)] = positionMs
        }
    }

    suspend fun playbackPosition(fileId: String): Long {
        if (fileId.isBlank()) return 0L
        return context.sessionDataStore.data.first()[playbackKey(fileId)] ?: 0L
    }

    private fun playbackKey(fileId: String) = longPreferencesKey("playback_${fileId.hashCode()}")

    private fun generateDid(): String {
        val random = ByteArray(16)
        SecureRandom().nextBytes(random)
        val digest = MessageDigest.getInstance("MD5").digest(random)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val EXPIRES_AT = longPreferencesKey("expires_at")
        val PHONE = stringPreferencesKey("phone")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val AVATAR_URL = stringPreferencesKey("avatar_url")
        val ROOT_ID = stringPreferencesKey("root_id")
        val ROOT_NAME = stringPreferencesKey("root_name")
    }
}

