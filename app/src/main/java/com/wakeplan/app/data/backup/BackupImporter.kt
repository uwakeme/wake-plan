package com.wakeplan.app.data.backup

import androidx.room.withTransaction
import com.wakeplan.app.data.db.WakePlanDatabase
import com.wakeplan.app.data.db.dao.PlanDao
import com.wakeplan.app.data.db.dao.PlanTemplateDao
import com.wakeplan.app.data.db.dao.TaskCompletionDao
import com.wakeplan.app.data.db.dao.TaskDao
import kotlinx.serialization.json.Json
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton

/** 加密备份导入但调用方没传 passphrase —— UI 收到它应弹密码输入框再重试 */
class BackupPassphraseRequiredException : IllegalStateException("encrypted file requires passphrase")

/**
 * 备份导入 — Phase 4.3
 *
 * 流程：
 * 1. 解析 BackupFile JSON
 * 2. 路由：
 *    - format=plain-v1 → 直接用 envelope 字段
 *    - format=encrypted-v1 → 用 passphrase 解密 data，再 parse 内部 envelope
 * 3. 按 envelope.schemaVersion 路由（v0.1 只支持 v1）
 * 4. 完全覆盖策略（PRD 5.3 #7 + 用户选择）：
 *    - 删 4 张表（按 FK-safe 顺序：completions → tasks → templates → plans）
 *    - 按 FK 顺序 insert 4 张表（templates → plans → tasks → completions）
 *
 * v0.3 砍段后 segments 字段保留但忽略（兼容老 v0.2 备份）
 *
 * v0.4：wipe + insert 包在 `db.withTransaction` 里，任一步失败整体回滚，
 * 不会出现"旧数据清了、新数据只进来一半"的中间态。
 */
@Singleton
class BackupImporter @Inject constructor(
    private val db: WakePlanDatabase,
    private val planDao: PlanDao,
    private val taskDao: TaskDao,
    private val taskCompletionDao: TaskCompletionDao,
    private val planTemplateDao: PlanTemplateDao,
) {
    private val json = Json {
        prettyPrint = false  // 解析不在意格式
        ignoreUnknownKeys = true
    }

    /**
     * 导入结果
     */
    data class ImportResult(
        val plansImported: Int,
        val segmentsImported: Int,  // v0.3 恒为 0（segments 字段忽略）
        val tasksImported: Int,
        val completionsImported: Int,
        val templatesImported: Int,
        val schemaVersion: Int,
    )

    /**
     * 从 BackupFile JSON 字符串导入
     *
     * @param fileJson 磁盘文件内容（plain 或 encrypted）
     * @param passphrase 加密备份必填；明文备份传 null
     * @return 导入结果
     *
     * @throws kotlinx.serialization.SerializationException JSON 格式错
     * @throws IllegalArgumentException format / schemaVersion 不支持
     * @throws IllegalStateException envelope / encryption 字段缺失
     * @throws AEADBadTagException 密文被篡改或 passphrase 错
     */
    suspend fun import(fileJson: String, passphrase: CharArray? = null): ImportResult {
        val file = json.decodeFromString(BackupFile.serializer(), fileJson)
        val envelope = when (file.format) {
            BackupFile.FORMAT_PLAIN -> {
                file.envelope ?: error("plain file missing envelope field")
            }
            BackupFile.FORMAT_ENCRYPTED -> {
                val meta = file.encryption ?: error("encrypted file missing encryption metadata")
                val data = file.data ?: error("encrypted file missing data field")
                // 类型化异常：UI 靠它弹密码框（v0.4.1 前靠 message 字符串 contains 匹配，
                // 文案一改流程就断）
                val pass = passphrase ?: throw BackupPassphraseRequiredException()
                val ciphertext = java.util.Base64.getDecoder().decode(data)
                val plaintextBytes = BackupCrypto.decrypt(ciphertext, meta, pass)
                val innerJson = String(plaintextBytes, Charsets.UTF_8)
                json.decodeFromString(BackupEnvelope.serializer(), innerJson)
            }
            else -> error("unsupported format: ${file.format}")
        }

        // schemaVersion 路由
        return when (envelope.schemaVersion) {
            1 -> applyV1(envelope)
            else -> error("unsupported schemaVersion: ${envelope.schemaVersion}")
        }
    }

    private suspend fun applyV1(envelope: BackupEnvelope): ImportResult = db.withTransaction {
        // 完全覆盖 — 先 wipe（按 FK 依赖的反序：先删依赖别人的）
        wipeAll()
        // 再 insert（按 FK 依赖的正序：先插被依赖的）
        // v0.3 砍段：envelope.data.segments 忽略不导入
        envelope.data.planTemplates.forEach { planTemplateDao.insert(it.toEntity()) }
        envelope.data.plans.forEach { planDao.insert(it.toEntity()) }
        envelope.data.tasks.forEach { taskDao.insert(it.toEntity()) }
        envelope.data.taskCompletions.forEach { taskCompletionDao.insert(it.toEntity()) }

        ImportResult(
            plansImported = envelope.data.plans.size,
            segmentsImported = 0,  // v0.3 砍段
            tasksImported = envelope.data.tasks.size,
            completionsImported = envelope.data.taskCompletions.size,
            templatesImported = envelope.data.planTemplates.size,
            schemaVersion = envelope.schemaVersion,
        )
    }

    private suspend fun wipeAll() {
        // FK-safe 顺序：completions 依赖 tasks/plans → 先删
        //              tasks 依赖 plans → 第二个
        //              templates 独立
        //              plans 最后（CASCADE 会带走剩下的，但显式删更可控）
        taskCompletionDao.deleteAll()
        taskDao.deleteAll()
        planTemplateDao.deleteAll()
        planDao.deleteAll()
    }
}
