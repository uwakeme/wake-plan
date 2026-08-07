package com.wakeplan.app.data.db.converter

import androidx.room.TypeConverter
import com.wakeplan.app.data.db.entity.RepeatRule
import com.wakeplan.app.data.db.entity.TaskStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

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
    @TypeConverter
    fun fromDayOfWeekSet(value: Set<DayOfWeek>?): String? =
        value?.takeIf { it.isNotEmpty() }?.joinToString(",") { it.name }

    @TypeConverter
    fun toDayOfWeekSet(value: String?): Set<DayOfWeek> =
        value?.takeIf { it.isNotBlank() }
            ?.split(",")
            ?.map { DayOfWeek.valueOf(it) }
            ?.toSet()
            ?: emptySet()

    // ===== List<String> =====
    @TypeConverter
    fun fromStringList(value: List<String>?): String? =
        value?.takeIf { it.isNotEmpty() }
            ?.joinToString(",") { it.replace(",", "␐") } // 简单 CSV 编码避免内部逗号冲突

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
}
