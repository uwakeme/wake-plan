package com.wakeplan.app.di

import android.content.Context
import android.util.Log
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
 * SQLCipher 4.6.x 加密：占位 passphrase（32 字节 0）打通链路；
 * 完整版用 Android Keystore 派生（Phase 0 收尾 TODO）。
 *
 * **4.5.x → 4.6.x 加密格式不兼容兜底**：如果旧设备上还有 4.5.x 时代的 .db 文件，
 * 4.6.x SupportOpenHelperFactory 打开会失败（page size 4KB → 16KB 改了加密格式）。
 * 第一次 build 失败时 deleteDatabase + 重建（旧数据会丢，MVP 阶段可接受）。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val TAG = "WakePlanDatabase"

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): WakePlanDatabase {
        // TODO(Phase 0 收尾): passphrase 从 Keystore 派生；现在用占位字符串让编译链路通
        val placeholderPassphrase = ByteArray(32) // 32 字节 0；Phase 0 收尾前不允许 release build
        val factory = SupportOpenHelperFactory(placeholderPassphrase)

        val builder = Room.databaseBuilder(
            context,
            WakePlanDatabase::class.java,
            WakePlanDatabase.DB_NAME,
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration() // MVP 阶段；v0.2 加 Migration 测试后去掉

        return try {
            builder.build()
        } catch (e: Exception) {
            // 大概率是 4.5.x 时代的旧加密 .db 跟 4.6.x 不兼容；删了重建
            Log.w(TAG, "Database build 失败，尝试 deleteDatabase 重建: ${e.message}", e)
            val deleted = context.deleteDatabase(WakePlanDatabase.DB_NAME)
            Log.w(TAG, "deleteDatabase 返回 $deleted")
            try {
                builder.build()
            } catch (e2: Exception) {
                Log.e(TAG, "重建仍然失败: ${e2.message}", e2)
                throw e2
            }
        }
    }

    @Provides fun providePlanDao(db: WakePlanDatabase): PlanDao = db.planDao()
    @Provides fun provideTimeSegmentDao(db: WakePlanDatabase): TimeSegmentDao = db.timeSegmentDao()
    @Provides fun provideTaskDao(db: WakePlanDatabase): TaskDao = db.taskDao()
    @Provides fun providePlanTemplateDao(db: WakePlanDatabase): PlanTemplateDao = db.planTemplateDao()
    @Provides fun provideTaskCompletionDao(db: WakePlanDatabase): TaskCompletionDao = db.taskCompletionDao()
}

