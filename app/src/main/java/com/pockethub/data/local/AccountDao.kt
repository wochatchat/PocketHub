package com.pockethub.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY createdAt DESC")
    fun allAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isActive = 1 LIMIT 1")
    fun activeAccount(): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveAccountSync(): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("UPDATE accounts SET isActive = 0")
    suspend fun deactivateAll()

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int
}
