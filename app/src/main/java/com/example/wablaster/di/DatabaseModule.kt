package com.example.wablaster.di

import android.content.Context
import androidx.room.Room
import com.example.wablaster.data.local.AppDatabase
import com.example.wablaster.data.local.dao.CampaignDao
import com.example.wablaster.data.local.dao.ContactDao
import com.example.wablaster.data.local.dao.SendLogDao
import com.example.wablaster.data.repo.ContactRepository
import com.example.wablaster.data.repo.ContactRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindContactRepository(impl: ContactRepositoryImpl): ContactRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "wablaster.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideContactDao(database: AppDatabase): ContactDao = database.contactDao()

    @Provides
    fun provideCampaignDao(database: AppDatabase): CampaignDao = database.campaignDao()

    @Provides
    fun provideSendLogDao(database: AppDatabase): SendLogDao = database.sendLogDao()
}
