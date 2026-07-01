package com.tripplanner.app.data.auth

import android.content.Context
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

data class AuthSession(
    val accountId: String,
    val displayName: String,
    val provider: AuthProvider
)

enum class AuthProvider {
    LOCAL,
    GOOGLE
}

data class AuthOperationResult(
    val session: AuthSession? = null,
    val errorMessage: String? = null
)

class AuthRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun currentSession(): AuthSession? {
        val accountId = preferences.getString(KEY_SESSION_ACCOUNT_ID, null) ?: return null
        val displayName = preferences.getString(KEY_SESSION_DISPLAY_NAME, null) ?: accountId
        val provider = preferences.getString(KEY_SESSION_PROVIDER, null)
            ?.let { runCatching { AuthProvider.valueOf(it) }.getOrNull() }
            ?: AuthProvider.LOCAL
        return AuthSession(
            accountId = accountId,
            displayName = displayName,
            provider = provider
        )
    }

    fun createLocalAccount(
        accountName: String,
        password: String
    ): AuthOperationResult {
        val normalizedAccountName = accountName.normalizedAccountName()
        if (normalizedAccountName.isBlank()) {
            return AuthOperationResult(errorMessage = "Enter an account name")
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            return AuthOperationResult(errorMessage = "Use at least $MIN_PASSWORD_LENGTH characters")
        }
        if (preferences.contains(hashKey(normalizedAccountName))) {
            return AuthOperationResult(errorMessage = "Account already exists")
        }

        val salt = UUID.randomUUID().toString()
        val passwordHash = hashPassword(salt = salt, password = password)
        preferences.edit()
            .putString(saltKey(normalizedAccountName), salt)
            .putString(hashKey(normalizedAccountName), passwordHash)
            .putString(displayNameKey(normalizedAccountName), accountName.trim())
            .apply()

        return signInLocal(accountName = accountName, password = password)
    }

    fun signInLocal(
        accountName: String,
        password: String
    ): AuthOperationResult {
        val normalizedAccountName = accountName.normalizedAccountName()
        val salt = preferences.getString(saltKey(normalizedAccountName), null)
            ?: return AuthOperationResult(errorMessage = "Local account not found")
        val storedHash = preferences.getString(hashKey(normalizedAccountName), null)
            ?: return AuthOperationResult(errorMessage = "Local account not found")
        val passwordHash = hashPassword(salt = salt, password = password)
        if (passwordHash != storedHash) {
            return AuthOperationResult(errorMessage = "Wrong password")
        }

        val displayName = preferences.getString(displayNameKey(normalizedAccountName), null)
            ?: accountName.trim()
        preferences.edit()
            .putString(KEY_SESSION_ACCOUNT_ID, normalizedAccountName)
            .putString(KEY_SESSION_DISPLAY_NAME, displayName)
            .putString(KEY_SESSION_PROVIDER, AuthProvider.LOCAL.name)
            .apply()

        return AuthOperationResult(
            session = AuthSession(
                accountId = normalizedAccountName,
                displayName = displayName,
                provider = AuthProvider.LOCAL
            )
        )
    }

    fun signInGoogle(
        accountId: String,
        displayName: String
    ): AuthOperationResult {
        val normalizedAccountId = accountId.normalizedAccountName()
        if (normalizedAccountId.isBlank()) {
            return AuthOperationResult(errorMessage = "Google account did not include an email")
        }
        preferences.edit()
            .putString(KEY_SESSION_ACCOUNT_ID, normalizedAccountId)
            .putString(KEY_SESSION_DISPLAY_NAME, displayName.ifBlank { normalizedAccountId })
            .putString(KEY_SESSION_PROVIDER, AuthProvider.GOOGLE.name)
            .apply()

        return AuthOperationResult(
            session = AuthSession(
                accountId = normalizedAccountId,
                displayName = displayName.ifBlank { normalizedAccountId },
                provider = AuthProvider.GOOGLE
            )
        )
    }

    fun signOut() {
        preferences.edit()
            .remove(KEY_SESSION_ACCOUNT_ID)
            .remove(KEY_SESSION_DISPLAY_NAME)
            .remove(KEY_SESSION_PROVIDER)
            .apply()
    }

    private fun String.normalizedAccountName(): String {
        return trim().lowercase(Locale.US)
    }

    private fun saltKey(accountName: String): String = "local.$accountName.salt"

    private fun hashKey(accountName: String): String = "local.$accountName.hash"

    private fun displayNameKey(accountName: String): String = "local.$accountName.displayName"

    private fun hashPassword(
        salt: String,
        password: String
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest("$salt:$password".toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val PREFERENCES_NAME = "trip_planner_auth"
        const val KEY_SESSION_ACCOUNT_ID = "session.accountId"
        const val KEY_SESSION_DISPLAY_NAME = "session.displayName"
        const val KEY_SESSION_PROVIDER = "session.provider"
        const val MIN_PASSWORD_LENGTH = 4
    }
}
