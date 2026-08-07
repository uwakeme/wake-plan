package com.wakeplan.app.data.backup

import com.wakeplan.app.data.db.dao.PlanDao
import com.wakeplan.app.data.db.dao.PlanTemplateDao
import com.wakeplan.app.data.db.dao.TaskCompletionDao
import com.wakeplan.app.data.db.dao.TaskDao
import com.wakeplan.app.data.db.dao.TimeSegmentDao
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 备份导出编排 — Phase 4.1
 *
 * 数据流：
 *   5 DAO (Flow)  →  take(1)  →  entity list  →  DTO  →  BackupEnvelope  →  JSON 字符串
 *
 * 取 Flow.first() 是 Room observe 的标准用法：拿当前 snapshot 不订阅变更
 * 备份是一次性快照，不需要持续更新
 *
 * 故意不返回 Flow —— 备份导出是个有副作用的动作（写文件、可能要算 SHA-256），
 * 用 suspend 暴露更明确"会阻塞等 IO"的语义
 */
@Singleton
class BackupExporter @Inject constructor(
    private val planDao: PlanDao,
    private val timeSegmentDao: TimeSegmentDao,
    private val taskDao: TaskDao,
    private val taskCompletionDao: TaskCompletionDao,
    private val planTemplateDao: PlanTemplateDao,
    private val clock: Clock,
    private val appVersionProvider: AppVersionProvider,
) {
    private val json = Json {
        // prettyPrint 方便用户肉眼查看备份文件 + diff
        prettyPrint = true
        // encodeDefaults 避免空 Set / null 字段丢失语义
        encodeDefaults = true
        // 未知 enum 不崩 —— 老版本备份可能多新字段
        ignoreUnknownKeys = true
    }

    /**
     * 读全表 → envelope → 序列化为 pretty JSON
     * @param appVersionName 比如 "0.1.0-alpha"
     * @param appVersionCode 比如 1
     */
    suspend fun export(
        appVersionName: String = appVersionProvider.versionName,
        appVersionCode: Int = appVersionProvider.versionCode,
    ): String {
        val plans = planDao.observeAll().first()
        val segments = timeSegmentDao.observeAll().first()
        val tasks = taskDao.observeAll().first()
        val completions = readAllCompletions()
        val templates = planTemplateDao.observeAll().first()

        val envelope = BackupEnvelope(
            exportedAt = Instant.now(clock),
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            data = BackupData(
                plans = plans.map { it.toDto() },
                segments = segments.map { it.toDto() },
                tasks = tasks.map { it.toDto() },
                taskCompletions = completions.map { it.toDto() },
                planTemplates = templates.map { it.toDto() },
            ),
        )
        return json.encodeToString(envelope)
    }

    /**
     * 读 completion 全表 —— 4 个其他 DAO 用 observeAll().first()，completion 走 getAllSnapshot()
     * v0.1 假设用户 < 1k completion，量大时再分批或加日期边界
     */
    private suspend fun readAllCompletions(): List<com.wakeplan.app.data.db.entity.TaskCompletionEntity> {
        return taskCompletionDao.getAllSnapshot()
    }
}

/**
 * App 版本信息提供 — Phase 4.1
 *
 * 不能直接在 BackupExporter 里读 BuildConfig（避免循环依赖 + 便于测试 mock）
 * 通过 Hilt 注入，由 AppVersionProviderImpl 读 BuildConfig
 */
interface AppVersionProvider {
    val versionName: String
    val versionCode: Int
}
