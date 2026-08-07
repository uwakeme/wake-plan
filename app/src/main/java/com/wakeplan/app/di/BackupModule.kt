package com.wakeplan.app.di

import com.wakeplan.app.data.backup.AppVersionProvider
import com.wakeplan.app.data.backup.AppVersionProviderImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 备份 Module — Phase 4.1
 *
 * 把 AppVersionProvider 接口绑到 BuildConfig 实现
 * BackupExporter 拿 AppVersionProvider 不直接 new 实现，便于测试时换 mock
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {

    @Binds @Singleton
    abstract fun bindAppVersionProvider(impl: AppVersionProviderImpl): AppVersionProvider
}
