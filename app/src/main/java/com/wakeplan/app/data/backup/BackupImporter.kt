package com.wakeplan.app.data.backup

import com.wakeplan.app.data.db.dao.PlanDao
import com.wakeplan.app.data.db.dao.PlanTemplateDao
import com.wakeplan.app.data.db.dao.TaskCompletionDao
import com.wakeplan.app.data.db.dao.TaskDao
import com.wakeplan.app.data.db.dao.TimeSegmentDao
import kotlinx.serialization.json.Json
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton

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
 *    - 删 5 张表（按 FK-safe 顺序：completions → tasks → segments → templates → plans）
 *    - 按 FK 顺序 insert 5 张表（templates → plans → segments → tasks → completions）
 *
 * **不包事务**：跨多个 DAO 的 Room 事务需要 `database.withTransaction {}`，先把单步测稳，v0.2 再加事务
 * 失败后用户可以再导一次（最坏情况 = 旧数据没了，但新数据也没进来），比"旧数据 + 新数据混着"更可控
 */
@Singleton
class BackupImporter @Inject constructor(
    private val planDao: PlanDao,
    private val timeSegmentDao: TimeSegmentDao,
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
        val segmentsImported: Int,
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
                val pass = passphrase ?: error("encrypted file requires passphrase")
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

    private suspend fun applyV1(envelope: BackupEnvelope): ImportResult {
        // 完全覆盖 — 先 wipe（按 FK 依赖的反序：先删依赖别人的）
        wipeAll()
        // 再 insert（按 FK 依赖的正序：先插被依赖的）
        envelope.data.planTemplates.forEach { planTemplateDao.insert(it.toEntity()) }
        envelope.data.plans.forEach { planDao.insert(it.toEntity()) }
        envelope.data.segments.forEach { timeSegmentDao.insert(it.toEntity()) }
        envelope.data.tasks.forEach { taskDao.insert(it.toEntity()) }
        envelope.data.taskCompletions.forEach { taskCompletionDao.insert(it.toEntity()) }

        return ImportResult(
            plansImported = envelope.data.plans.size,
            segmentsImported = envelope.data.segments.size,
            tasksImported = envelope.data.tasks.size,
            completionsImported = envelope.data.taskCompletions.size,
            templatesImported = envelope.data.planTemplates.size,
            schemaVersion = envelope.schemaVersion,
        )
    }

    private suspend fun wipeAll() {
        // FK-safe 顺序：completions 依赖 tasks/plans → 先删
        //              tasks 依赖 segments → 第二个
        //              segments 依赖 plans → 第三个
        //              templates 独立
        //              plans 最后（CASCADE 会带走剩下的，但显式删更可控）
        taskCompletionDao.deleteAll()
        taskDao.deleteAll()
        timeSegmentDao.deleteAll()
        planTemplateDao.deleteAll()
        planDao.deleteAll()
    }
}
