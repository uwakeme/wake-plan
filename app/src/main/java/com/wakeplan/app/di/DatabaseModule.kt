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
 * **数据迁移（v0.4.1 起）**：
 * - 升级迁移全部显式声明（见 [WakePlanDatabase.ALL_MIGRATIONS]），缺路径直接抛错，
 *   不再 fallbackToDestructiveMigration 静默清库
 * - 仅降级（debug 装旧版本）允许破坏性重建
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
            .addMigrations(*WakePlanDatabase.ALL_MIGRATIONS)
            // debug 降级安装时允许清库；升级永远走显式 Migration
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides fun providePlanDao(db: WakePlanDatabase): PlanDao = db.planDao()
    @Provides fun provideTaskDao(db: WakePlanDatabase): TaskDao = db.taskDao()
    @Provides fun providePlanTemplateDao(db: WakePlanDatabase): PlanTemplateDao = db.planTemplateDao()
    @Provides fun provideTaskCompletionDao(db: WakePlanDatabase): TaskCompletionDao = db.taskCompletionDao()
    @Provides fun provideFocusSessionDao(db: WakePlanDatabase): com.wakeplan.app.data.db.dao.FocusSessionDao =
        db.focusSessionDao()
}
