package com.wakeplan.app.data.backup

import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.TaskStatus
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 备份文件顶层结构 — Phase 4.1
 *
 * ```json
 * {
 *   "schemaVersion": 1,
 *   "exportedAt": "2026-08-08T01:23:45.678Z",
 *   "appVersionName": "0.1.0-alpha",
 *   "appVersionCode": 1,
 *   "data": { "plans": [...], "segments": [...], "tasks": [...], "taskCompletions": [...], "planTemplates": [...] }
 * }
 * ```
 *
 * v0.3 砍段：`data.segments` 字段保留（兼容老 v0.2 备份），但恒为空 list。
 * 真正的 segments 字段移除要等 commit 6 整体重写 envelope schema。
 *
 * - `schemaVersion` 整型；导入时按版本路由到对应的 MigrationHandler
 * - `data` 包一层是预留扩展位：未来加 `preferences` / `deviceInfo` 不破坏顶层
 * - DTO 字段名跟 entity 一致，但不带 Room 注解，序列化规则独立可控
 */
@Serializable
data class BackupEnvelope(
    val schemaVersion: Int = SCHEMA_VERSION,
    @Serializable(with = InstantSerializer::class)
    val exportedAt: Instant,
    val appVersionName: String,
    val appVersionCode: Int,
    val data: BackupData,
) {
    companion object {
        /** 当前备份格式版本；改 DTO 字段时 +1 */
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class BackupData(
    val plans: List<BackupPlan>,
    /** v0.3 砍段后恒为空 list；保留字段以兼容老版本备份文件 */
    val segments: List<BackupSegment> = emptyList(),
    val tasks: List<BackupTask>,
    val taskCompletions: List<BackupCompletion>,
    val planTemplates: List<BackupTemplate>,
)

@Serializable
data class BackupPlan(
    val id: String,
    val title: String,
    val colorHex: String,
    @Serializable(with = LocalDateSerializer::class)
    val startDate: LocalDate,
    val isActive: Boolean,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant,
)

/**
 * v0.3 砍段后 segments 恒为空 list；此 DTO 保留只为兼容老备份文件解析（不会真写入）
 */
@Serializable
data class BackupSegment(
    val id: String,
    val planId: String,
    val title: String?,
    val startMinutes: Int,
    val endMinutes: Int,
    val repeatRule: RepeatRule,
    @Serializable(with = DayOfWeekSetSerializer::class)
    val customDays: Set<DayOfWeek>,
)

/**
 * v0.3 扁平化：任务自带 startMinutes / endMinutes / repeatRule / customDays
 * 字段名对应 TaskEntity（去掉 segmentId / plannedDurationMinutes / autoExtend / orderInSegment）
 */
@Serializable
data class BackupTask(
    val id: String,
    val planId: String,
    val title: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val repeatRule: RepeatRule,
    @Serializable(with = DayOfWeekSetSerializer::class)
    val customDays: Set<DayOfWeek> = emptySet(),
    val isLocked: Boolean = false,
    val orderInPlan: Int,
    val note: String? = null,
)

@Serializable
data class BackupCompletion(
    val id: String,
    val taskId: String,
    val planId: String,
    @Serializable(with = LocalDateSerializer::class)
    val scheduledDate: LocalDate,
    @Serializable(with = LocalDateTimeSerializer::class)
    val plannedStart: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class)
    val plannedEnd: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class)
    val actualStart: LocalDateTime?,
    @Serializable(with = LocalDateTimeSerializer::class)
    val actualEnd: LocalDateTime?,
    val status: TaskStatus,
    val overrunMinutes: Int,
    @Serializable(with = StringListSerializer::class)
    val affectedTasks: List<String>,
)

@Serializable
data class BackupTemplate(
    val id: String,
    val title: String,
    val colorHex: String,
    val isBuiltIn: Boolean,
    val schemaVersion: Int,
)

/**
 * 加密元数据 — Phase 4.2
 *
 * 写盘时跟 BackupFile 一起存；解析时决定走 decrypt 还是直接读 envelope
 *
 * @param saltB64 16 字节 salt 的 Base64 编码（无换行）
 * @param ivB64 12 字节 IV 的 Base64 编码
 * @param iterations PBKDF2 迭代次数（OWASP 2023 600k，测试可小）
 */
@Serializable
data class EncryptionMetadata(
    val kdf: String,
    val iterations: Int,
    val cipher: String,
    val saltB64: String,
    val ivB64: String,
)

/**
 * 磁盘文件顶层结构 — Phase 4.2
 *
 * 跟 BackupEnvelope 区别：这是写到磁盘文件的"外层包装"
 * BackupEnvelope 是数据本身的 schema；BackupFile 是"这个文件怎么读"
 *
 * ```json
 * // 明文备份
 * { "fileSchemaVersion": 1, "format": "plain-v1", "encryption": null,
 *   "envelope": { schemaVersion, exportedAt, ..., data: { plans, segments, ... } } }
 *
 * // 加密备份
 * { "fileSchemaVersion": 1, "format": "encrypted-v1",
 *   "encryption": { kdf, iterations, cipher, saltB64, ivB64 },
 *   "envelope": null, "data": "base64(ciphertext)" }
 * ```
 *
 * 导入时按 `format` 路由：
 * - "plain-v1"：直接读 envelope
 * - "encrypted-v1"：用 metadata 解密 data，再 parse 内部 JSON 为 BackupEnvelope
 *
 * `fileSchemaVersion` 跟 envelope.schemaVersion 分开：前者管"文件怎么读"，后者管"数据是什么版本"
 */
@Serializable
data class BackupFile(
    val fileSchemaVersion: Int = FILE_SCHEMA_VERSION,
    val format: String,    // "plain-v1" 或 "encrypted-v1"
    val encryption: EncryptionMetadata? = null,
    val envelope: BackupEnvelope? = null,
    val data: String? = null,    // base64 ciphertext（仅 encrypted-v1 用）
) {
    companion object {
        const val FILE_SCHEMA_VERSION = 1
        const val FORMAT_PLAIN = "plain-v1"
        const val FORMAT_ENCRYPTED = "encrypted-v1"
    }
}
