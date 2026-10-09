package com.wakeplan.app.data.db.converter

import androidx.room.TypeConverter
import com.wakeplan.app.domain.model.FocusOutcome
import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.TaskStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Room TypeConverters — 跨版本 java.time 稳定做法
 *
 * 时间字段：
 *   - LocalDate / LocalDateTime 用 ISO-8601 String 存（可读 + 跨设备兼容，PRD 5.2 风险表）
 *   - Instant 用 epoch millis (Long) 存（精确 + 索引友好）
 *
 * 集合字段：
 *   - Set<DayOfWeek> 用逗号分隔字符串 (1,3,5)
 *   - List<String> 用 JSON 数组字符串
 *
 * Enum 字段：直接用 enum.name() 存
 */
class Converters {

    // ===== LocalDate =====
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString() // ISO yyyy-MM-dd

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    // ===== LocalDateTime =====
    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? = value?.toString() // ISO

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)

    // ===== Instant =====
    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    // ===== Set<DayOfWeek> =====
    // 空集返回 "" 而非 null — Room 字段 NOT NULL 约束
    @TypeConverter
    fun fromDayOfWeekSet(value: Set<DayOfWeek>?): String =
        value?.joinToString(",") { it.name } ?: ""

    @TypeConverter
    fun toDayOfWeekSet(value: String?): Set<DayOfWeek> =
        value?.takeIf { it.isNotBlank() }
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.map { DayOfWeek.valueOf(it) }
            ?.toSet()
            ?: emptySet()

    // ===== List<String> =====
    // 空列表返回 "" 而非 null — Room 字段 NOT NULL 约束
    @TypeConverter
    fun fromStringList(value: List<String>?): String =
        value?.joinToString(",") { it.replace(",", "␐") } ?: ""

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        value?.takeIf { it.isNotBlank() }
            ?.split(",")
            ?.map { it.replace("␐", ",") }
            ?: emptyList()

    // ===== Enum: RepeatRule =====
    @TypeConverter
    fun fromRepeatRule(value: RepeatRule?): String? = value?.name

    @TypeConverter
    fun toRepeatRule(value: String?): RepeatRule? = value?.let { RepeatRule.valueOf(it) }

    // ===== Enum: TaskStatus =====
    @TypeConverter
    fun fromTaskStatus(value: TaskStatus?): String? = value?.name

    @TypeConverter
    fun toTaskStatus(value: String?): TaskStatus? = value?.let { TaskStatus.valueOf(it) }

    // ===== Enum: FocusOutcome（v0.6 focus_sessions）=====
    @TypeConverter
    fun fromFocusOutcome(value: FocusOutcome?): String? = value?.name

    @TypeConverter
    fun toFocusOutcome(value: String?): FocusOutcome? = value?.let { FocusOutcome.valueOf(it) }
}
