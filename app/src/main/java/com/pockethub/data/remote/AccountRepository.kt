package com.pockethub.data.remote

import com.pockethub.data.local.AccountDao
import com.pockethub.data.local.AccountEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages multi-account lifecycle: add, remove, switch, read active token.
 */
@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
) {
    val allAccounts: Flow<List<AccountEntity>> = accountDao.allAccounts()
    val activeAccount: Flow<AccountEntity?> = accountDao.activeAccount()

    /** Get the token of the current active account, or empty. */
    suspend fun getActiveToken(): String = accountDao.getActiveAccountSync()?.token.orEmpty()

    /** Get the current login, or empty. */
    suspend fun getActiveLogin(): String = accountDao.getActiveAccountSync()?.login.orEmpty()

    /** Snapshot of the active row (login + refresh credential) for the refresher. */
    suspend fun getActiveAccount(): AccountEntity? = accountDao.getActiveAccountSync()

    /**
     * Persist refreshed credentials for every row of [login]. Safe against
     * mid-refresh account switches: the write is keyed by login, not by
     * "current active row", so it always lands on the right account.
     */
    suspend fun updateTokensByLogin(login: String, token: String, refreshToken: String, expiresAt: Long): Boolean =
        accountDao.updateTokensByLogin(login, token, refreshToken, expiresAt) > 0

    /** Add a new account and make it active (if first account, auto-activate). */
    suspend fun addAccount(
        login: String,
        token: String,
        tokenType: String = "bearer",
        name: String? = null,
        avatarUrl: String? = null,
        scopes: String = "",
        refreshToken: String = "",
        tokenExpiresAtMs: Long = 0,
    ): Long {
        val existing = accountDao.allAccounts().first()
        // Re-login of a known account updates its row in place — sign-out no
        // longer deletes rows (see signOutActive), so without this dedup every
        // login/logout cycle would grow a duplicate row per account.
        val sameLogin = existing.firstOrNull { it.login.equals(login, ignoreCase = true) }
        val id = if (sameLogin != null) {
            accountDao.update(
                sameLogin.copy(
                    token = token,
                    tokenType = tokenType,
                    name = name,
                    avatarUrl = avatarUrl,
                    scopes = scopes,
                    refreshToken = refreshToken,
                    tokenExpiresAt = tokenExpiresAtMs,
                )
            )
            sameLogin.id
        } else {
            accountDao.insert(
                AccountEntity(
                    login = login,
                    name = name,
                    avatarUrl = avatarUrl,
                    token = token,
                    tokenType = tokenType,
                    isActive = existing.isEmpty(), // first account is active by default
                    scopes = scopes,
                    refreshToken = refreshToken,
                    tokenExpiresAt = tokenExpiresAtMs,
                )
            )
        }
        // Logging in means THIS account becomes the active one — last login
        // wins. (The old "activate only the first row" rule left repeat logins
        // pointing at a stale active row, so sessions didn't survive restart.)
        accountDao.deactivateAll()
        val account = accountDao.getById(id) ?: return id
        accountDao.update(account.copy(isActive = true))
        return id
    }

    /** Switch to another account by id. */
    suspend fun switchAccount(id: Long) {
        accountDao.deactivateAll()
        val account = accountDao.getById(id) ?: return
        accountDao.update(account.copy(isActive = true))
    }

    /** Remove an account. If it was active, switch to the next available one. */
    suspend fun removeAccount(id: Long) {
        val wasActive = accountDao.getActiveAccountSync()?.id == id
        accountDao.deleteById(id)
        if (wasActive) {
            val remaining = accountDao.allAccounts().first()
            if (remaining.isNotEmpty()) {
                switchAccount(remaining.first().id)
            }
        }
    }

    /**
     * Sign out the ACTIVE session — SOFT: deactivate every row so no stale row
     * auto-restores a session on next launch, but never DELETE anything. The
     * old delete-the-active-row behavior made any 401 (including transient
     * ones) permanently destroy the account; rows now stay as history and a
     * re-login refreshes them in place via [addAccount]'s dedup. A dead-token
     * row that gets manually switched back to self-heals: its next API call
     * 401s → refresh attempt → (failed) → soft sign-out again.
     */
    suspend fun signOutActive() {
        accountDao.deactivateAll()
    }

    /** Total number of accounts. */
    suspend fun accountCount(): Int = accountDao.count()
}
