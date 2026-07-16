package com.pockethub.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AccountEntity::class, CachedItemEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun cacheDao(): CacheDao
}
