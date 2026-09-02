package com.pockethub.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AccountEntity::class, CachedItemEntity::class, DownloadEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun cacheDao(): CacheDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        /**
         * v3 → v4: adds the OAuth renewal columns to `accounts`.
         *
         * A REAL migration — the previous fallbackToDestructiveMigration would
         * have WIPED every stored account (silent "login lost" for all users
         * exactly when this feature ships). Existing rows keep their token;
         * refreshToken defaults to "" (PAT sessions and already-issued OAuth
         * tokens without a stored refresh token simply never refresh) and
         * tokenExpiresAt defaults to 0 (unknown → never proactively refresh).
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN refreshToken TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE accounts ADD COLUMN tokenExpiresAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val ALL_MIGRATIONS = arrayOf<Migration>(MIGRATION_3_4)
    }
}
