package com.wakeplan.app.di

import android.content.Context
import androidx.room.Room
import com.wakeplan.app.data.db.WakePlanDatabase
import com.wakeplan.app.data.db.dao.PlanDao
import com.wakeplan.app.data.db.dao.PlanTemplateDao
import com.wakeplan.app.data.db.dao.TaskCompletionDao
import com.wakeplan.app.data.db.dao.TaskDao
import com.wakeplan.app.data.db.dao.TimeSegmentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Database Module — 注入 Room Database + 5 个 DAO
 *
 * **SQLCipher 状态（v0.1 临时）**：
 * 阿里云 maven 镜像的 SQLCipher 4.6.1 AAR 损坏（classes 跟 native lib 不同步，RegisterNatives 失败）。
 * 4.5.x 系列 16KB page size 不兼容 Android 15+。Maven Central HTTPS 443 网络限制访问不到。
 *
 * **临时方案**：v0.1 阶段不用 SQLCipher，Room 默认 SQLite（数据明文存）。
 * Phase 4 备份导出时**重新设计加密方案**：
 *   - 方案 A：自己编译 SQLCipher 4.6.1 本地 AAR
 *   - 方案 B：换 androidx.sqlite 集成方式
 *   - 方案 C：换其他加密方案（sqlcypher + Room 直接集成）
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): WakePlanDatabase {
        return Room.databaseBuilder(
            context,
            WakePlanDatabase::class.java,
            WakePlanDatabase.DB_NAME,
        )
            // 暂不传 openHelperFactory：v0.1 用 Room 默认 SQLite（无加密）
            .fallbackToDestructiveMigration() // MVP 阶段；v0.2 加 Migration 测试后去掉
            .build()
    }

    @Provides fun providePlanDao(db: WakePlanDatabase): PlanDao = db.planDao()
    @Provides fun provideTimeSegmentDao(db: WakePlanDatabase): TimeSegmentDao = db.timeSegmentDao()
    @Provides fun provideTaskDao(db: WakePlanDatabase): TaskDao = db.taskDao()
    @Provides fun providePlanTemplateDao(db: WakePlanDatabase): PlanTemplateDao = db.planTemplateDao()
    @Provides fun provideTaskCompletionDao(db: WakePlanDatabase): TaskCompletionDao = db.taskCompletionDao()
}
