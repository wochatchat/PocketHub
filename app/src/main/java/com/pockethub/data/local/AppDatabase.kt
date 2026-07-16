package com.pockethub.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AccountEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
}
