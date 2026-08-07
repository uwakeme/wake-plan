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
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Database Module — 注入 Room Database + 5 个 DAO
 *
 * SQLCipher 加密：Phase 0 先用空 passphrase（passphrase 占位）打通链路，
 * Phase 0 收尾时把 passphrase 接到 Android Keystore 派生。
 *
 * **Phase 0 注意**：v0.1 阶段必须用 Keystore 派生密钥；占位仅为了让 build 跑通。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): WakePlanDatabase {
        // TODO(Phase 0 收尾): passphrase 从 Keystore 派生；现在用占位字符串让编译链路通
        val placeholderPassphrase = ByteArray(32) // 32 字节 0；Phase 0 收尾前不允许 release build
        val factory = SupportOpenHelperFactory(placeholderPassphrase)

        return Room.databaseBuilder(
            context,
            WakePlanDatabase::class.java,
            WakePlanDatabase.DB_NAME,
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration() // MVP 阶段；v0.2 加 Migration 测试后去掉
            .build()
    }

    @Provides fun providePlanDao(db: WakePlanDatabase): PlanDao = db.planDao()
    @Provides fun provideTimeSegmentDao(db: WakePlanDatabase): TimeSegmentDao = db.timeSegmentDao()
    @Provides fun provideTaskDao(db: WakePlanDatabase): TaskDao = db.taskDao()
    @Provides fun providePlanTemplateDao(db: WakePlanDatabase): PlanTemplateDao = db.planTemplateDao()
    @Provides fun provideTaskCompletionDao(db: WakePlanDatabase): TaskCompletionDao = db.taskCompletionDao()
}
