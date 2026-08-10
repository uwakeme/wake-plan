package com.wakeplan.app.di

import com.wakeplan.app.data.security.AndroidKeystoreMasterKeyProvider
import com.wakeplan.app.data.security.MasterKeyProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Security Module — Phase 5.8
 *
 * @Binds 把 AndroidKeystoreMasterKeyProvider 暴露成 MasterKeyProvider 接口
 * DatabaseKeyStore 注入 MasterKeyProvider 接口；测试时直接传 fake 进来
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindMasterKeyProvider(impl: AndroidKeystoreMasterKeyProvider): MasterKeyProvider
}
