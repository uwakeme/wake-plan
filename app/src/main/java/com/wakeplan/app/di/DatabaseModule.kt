package com.wakeplan.app.di

import android.content.Context
import androidx.room.Room
import com.wakeplan.app.data.db.WakePlanDatabase
import com.wakeplan.app.data.db.dao.PlanDao
import com.wakeplan.app.data.db.dao.PlanTemplateDao
import com.wakeplan.app.data.db.dao.TaskCompletionDao
import com.wakeplan.app.data.db.dao.TaskDao
import com.wakeplan.app.data.security.DatabaseKeyStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

/**
 * Database Module — 注入 Room Database + 4 个 DAO
 *
 * **SQLCipher 集成（Phase 5.8）**：
 * - DB passphrase 由 [DatabaseKeyStore] 管理
 *   - master key 走 AndroidKeyStore（AES-GCM 256，不导出）
 *   - 32 字节随机 passphrase wrap 后存 DataStore
 * - SupportOpenHelperFactory 拿 passphrase 打开加密 DB
 * - App.onCreate 同步 bootstrap（runBlocking < 100ms，OK on cold start）
 *
 * **数据迁移**：
 * - v0.3 schema v3：time_segments 表删除，tasks 字段重定义。
 *   v0.2 → v0.3 alpha 用户数据清零，fallbackToDestructiveMigration 兜底。
 *   真实 release 前再写 proper Migration。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyStore: DatabaseKeyStore,
    ): WakePlanDatabase {
        // App.onCreate 已经同步 bootstrap 过；这里直接拿 cached
        val passphrase = keyStore.getCachedOrThrow()
        val factory = SupportOpenHelperFactory(passphrase)
        return Room.databaseBuilder(
            context,
            WakePlanDatabase::class.java,
            WakePlanDatabase.DB_NAME,
        )
            .openHelperFactory(factory)
            // v0.3 alpha：缺 Migration 时 dropAndRecreate 清表重建
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun providePlanDao(db: WakePlanDatabase): PlanDao = db.planDao()
    @Provides fun provideTaskDao(db: WakePlanDatabase): TaskDao = db.taskDao()
    @Provides fun providePlanTemplateDao(db: WakePlanDatabase): PlanTemplateDao = db.planTemplateDao()
    @Provides fun provideTaskCompletionDao(db: WakePlanDatabase): TaskCompletionDao = db.taskCompletionDao()
}
