package com.zlearn.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthSessionStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val _token = MutableStateFlow(prefs.getString(KEY_TOKEN, null))
    private val _userId = MutableStateFlow(resolveInitialUserId())
    val token: StateFlow<String?> = _token.asStateFlow()
    val userId: StateFlow<Int?> = _userId.asStateFlow()

    fun saveSession(token: String, userId: Int?) {
        val resolvedUserId = userId ?: parseUserIdFromToken(token)
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putInt(KEY_USER_ID, resolvedUserId ?: NO_USER)
            .apply()
        _token.value = token
        _userId.value = resolvedUserId
    }

    fun saveToken(token: String) = saveSession(token, userId = null)

    fun clearToken() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .apply()
        _token.value = null
        _userId.value = null
    }

    fun getLastUploadUpdatedAt(ownerUserId: Int): Long =
        prefs.getLong(keyLastUploadUpdatedAt(ownerUserId), 0L)

    fun saveLastUploadUpdatedAt(ownerUserId: Int, updatedAt: Long) {
        prefs.edit().putLong(keyLastUploadUpdatedAt(ownerUserId), updatedAt).apply()
    }

    fun getPullCursorUpdatedAt(ownerUserId: Int): Long =
        prefs.getLong(keyPullCursorUpdatedAt(ownerUserId), 0L)

    fun getPullCursorId(ownerUserId: Int): Int =
        prefs.getInt(keyPullCursorId(ownerUserId), 0)

    fun savePullCursor(ownerUserId: Int, updatedAt: Long, id: Int) {
        prefs.edit()
            .putLong(keyPullCursorUpdatedAt(ownerUserId), updatedAt)
            .putInt(keyPullCursorId(ownerUserId), id)
            .apply()
    }

    private fun resolveInitialUserId(): Int? {
        val fromPrefs = prefs.getInt(KEY_USER_ID, NO_USER).takeIf { it != NO_USER }
        if (fromPrefs != null) return fromPrefs
        return parseUserIdFromToken(_token.value)
    }

    private fun parseUserIdFromToken(token: String?): Int? {
        val raw = token ?: return null
        if (!raw.startsWith("token_")) return null
        return raw.removePrefix("token_").toIntOrNull()
    }

    private fun keyLastUploadUpdatedAt(ownerUserId: Int): String =
        "last_upload_updated_at_$ownerUserId"

    private fun keyPullCursorUpdatedAt(ownerUserId: Int): String =
        "pull_cursor_updated_at_$ownerUserId"

    private fun keyPullCursorId(ownerUserId: Int): String =
        "pull_cursor_id_$ownerUserId"

    companion object {
        private const val PREF_NAME = "auth_session"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val NO_USER = -1
    }
}

