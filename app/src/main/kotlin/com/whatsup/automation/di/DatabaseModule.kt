package com.whatsup.automation.di

import android.content.Context
import androidx.room.Room
import com.whatsup.automation.data.local.AppDatabase
import com.whatsup.automation.data.local.dao.LogDao
import com.whatsup.automation.data.local.dao.RuleDao
import com.whatsup.automation.data.local.dao.StatusDao
import com.whatsup.automation.data.local.dao.GroupDao
import com.whatsup.automation.data.local.dao.GroupMemberDao
import com.whatsup.automation.data.local.dao.GroupLogDao
import com.whatsup.automation.data.local.entity.SyncedContactDao
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
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addCallback(AppDatabase.createCallback())
            .addMigrations(AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideRuleDao(database: AppDatabase): RuleDao = database.ruleDao()

    @Provides
    fun provideLogDao(database: AppDatabase): LogDao = database.logDao()

    @Provides
    fun provideSyncedContactDao(database: AppDatabase): SyncedContactDao = database.syncedContactDao()

    @Provides
    fun provideStatusDao(database: AppDatabase): StatusDao = database.statusDao()

    @Provides
    fun provideGroupDao(database: AppDatabase): GroupDao = database.groupDao()

    @Provides
    fun provideGroupMemberDao(database: AppDatabase): GroupMemberDao = database.groupMemberDao()

    @Provides
    fun provideGroupLogDao(database: AppDatabase): GroupLogDao = database.groupLogDao()
}
