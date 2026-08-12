package com.wakeplan.app.data.backup

import com.wakeplan.app.data.db.dao.PlanDao
import com.wakeplan.app.data.db.dao.PlanTemplateDao
import com.wakeplan.app.data.db.dao.TaskCompletionDao
import com.wakeplan.app.data.db.dao.TaskDao
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
 *   4 DAO (Flow)  →  take(1)  →  entity list  →  DTO  →  BackupEnvelope  →  JSON 字符串
 *
 * v0.3 砍段后不再读 time_segments。BackupData.segments 字段保留但恒为空 list，
 * 以兼容老版本（v0.2 schema 1）备份文件。commit 6 会整体重写 envelope schema。
 */
@Singleton
class BackupExporter @Inject constructor(
    private val planDao: PlanDao,
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
        val tasks = taskDao.observeAll().first()
        val completions = readAllCompletions()
        val templates = planTemplateDao.observeAll().first()

        val envelope = BackupEnvelope(
            exportedAt = Instant.now(clock),
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            data = BackupData(
                plans = plans.map { it.toDto() },
                segments = emptyList(),  // v0.3 砍段；占位保持 envelope schema 兼容
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

    /**
     * 导出为 BackupFile（disk-level envelope）— Phase 4.2
     *
     * @param passphrase null → 明文；非空 → 加密（用完请 clear）
     * @return BackupFile JSON 字符串，可以直接写到 .json 文件
     */
    suspend fun exportToBackupFile(
        passphrase: CharArray? = null,
        iterations: Int = BackupCrypto.DEFAULT_ITERATIONS,
        appVersionName: String = appVersionProvider.versionName,
        appVersionCode: Int = appVersionProvider.versionCode,
    ): String {
        val envelopeJson = export(appVersionName, appVersionCode)
        val backupFile = if (passphrase == null) {
            // 明文
            val envelope = json.decodeFromString(BackupEnvelope.serializer(), envelopeJson)
            BackupFile(
                format = BackupFile.FORMAT_PLAIN,
                encryption = null,
                envelope = envelope,
                data = null,
            )
        } else {
            // 加密
            val plaintext = envelopeJson.toByteArray(Charsets.UTF_8)
            val result = BackupCrypto.encrypt(plaintext, passphrase, iterations)
            val ciphertextB64 = java.util.Base64.getEncoder().withoutPadding().encodeToString(result.ciphertext)
            BackupFile(
                format = BackupFile.FORMAT_ENCRYPTED,
                encryption = result.metadata,
                envelope = null,
                data = ciphertextB64,
            )
        }
        return json.encodeToString(backupFile)
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
