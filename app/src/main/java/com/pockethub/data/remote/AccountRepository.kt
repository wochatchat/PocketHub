package com.pockethub.data.remote

import com.pockethub.data.local.AccountDao
import com.pockethub.data.local.AccountEntity
import com.pockethub.data.local.TokenCipher
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
    private val cipher: TokenCipher,
) {
    val allAccounts: Flow<List<AccountEntity>> = accountDao.allAccounts()
    val activeAccount: Flow<AccountEntity?> = accountDao.activeAccount()

    /** Get the token of the current active account, or empty. */
    suspend fun getActiveToken(): String { val a=accountDao.getActiveAccountSync()?:return ""; val t=cipher.decrypt(a.token); if(t.isNotBlank()&&!a.token.startsWith("v1:"))accountDao.update(a.copy(token=cipher.encrypt(t))); return t }
    suspend fun loginStoredAccount(id:Long):Boolean { val a=accountDao.getById(id)?:return false; val t=cipher.decrypt(a.token); if(t.isBlank())return false; if(!a.token.startsWith("v1:"))accountDao.update(a.copy(token=cipher.encrypt(t))); accountDao.deactivateAll(); accountDao.activate(id); return true }
    suspend fun logout(){accountDao.deactivateAll()}

    /** Get the current login, or empty. */
    suspend fun getActiveLogin(): String = accountDao.getActiveAccountSync()?.login.orEmpty()

    /** Add a new account and make it active (if first account, auto-activate). */
    suspend fun addAccount(
        login: String,
        token: String,
        tokenType: String = "bearer",
        name: String? = null,
        avatarUrl: String? = null,
        scopes: String = "",
    ): Long {
        val existing = accountDao.allAccounts().first()
        val id = accountDao.insert(
            AccountEntity(
                login = login,
                name = name,
                avatarUrl = avatarUrl,
                token = cipher.encrypt(token),
                tokenType = tokenType,
                isActive = existing.isEmpty(), // first account is active by default
                scopes = scopes,
            )
        )
        // If this is the first account, set it active explicitly
        if (existing.isEmpty()) {
            accountDao.deactivateAll()
            val account = accountDao.getById(id) ?: return id
            accountDao.update(account.copy(isActive = true))
        }
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

    /** Total number of accounts. */
    suspend fun accountCount(): Int = accountDao.count()
}
