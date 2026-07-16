package com.pockethub.di

import android.content.Context
import androidx.room.Room
import com.pockethub.data.local.AccountDao
import com.pockethub.data.local.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "pockethub.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideAccountDao(db: AppDatabase): AccountDao = db.accountDao()
}
