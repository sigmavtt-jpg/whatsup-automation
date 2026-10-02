package com.whatsup.automation.di

import com.whatsup.automation.data.repository.LogRepositoryImpl
import com.whatsup.automation.data.repository.RuleRepositoryImpl
import com.whatsup.automation.domain.repository.LogRepository
import com.whatsup.automation.domain.repository.RuleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindRuleRepository(
        impl: RuleRepositoryImpl
    ): RuleRepository

    @Binds
    @Singleton
    abstract fun bindLogRepository(
        impl: LogRepositoryImpl
    ): LogRepository

    
    @Binds
    @Singleton
    abstract fun bindStatusRepository(
        impl: com.whatsup.automation.data.repository.StatusRepositoryImpl
    ): com.whatsup.automation.domain.repository.StatusRepository

    @Binds
    @Singleton
    abstract fun bindGroupRepository(
        impl: com.whatsup.automation.data.repository.GroupRepositoryImpl
    ): com.whatsup.automation.domain.repository.GroupRepository

    @Binds
    @Singleton
    abstract fun bindContactFormattingRepository(
        impl: com.whatsup.automation.data.repository.ContactFormattingRepositoryImpl
    ): com.whatsup.automation.domain.repository.ContactFormattingRepository

    @Binds
    @Singleton
    abstract fun bindAntiBanRepository(
        impl: com.whatsup.automation.data.repository.AntiBanRepositoryImpl
    ): com.whatsup.automation.domain.repository.AntiBanRepository

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(
        impl: com.whatsup.automation.data.repository.NotificationRepositoryImpl
    ): com.whatsup.automation.domain.repository.NotificationRepository
}

