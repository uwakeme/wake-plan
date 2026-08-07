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
 * - `schemaVersion` 整型；导入时按版本路由到对应的 MigrationHandler（Phase 4.3 落地）
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
    val segments: List<BackupSegment>,
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

@Serializable
data class BackupTask(
    val id: String,
    val segmentId: String,
    val title: String,
    val plannedDurationMinutes: Int,
    val autoExtend: Boolean,
    val isLocked: Boolean,
    val orderInSegment: Int,
    val note: String?,
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
