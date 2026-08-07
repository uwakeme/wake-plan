package com.wakeplan.app.data.backup

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 备份 JSON 序列化用的 5 个 custom serializer — Phase 4.1
 *
 * 全部走 ISO-8601 字符串格式（Instant / LocalDate / LocalDateTime）
 * 跟跨设备 / 跨语言兼容性优先；不混用 epoch 秒/毫秒
 */

/** `java.time.Instant` ↔ ISO-8601 UTC 字符串（如 `2026-08-08T01:23:45.678Z`） */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(DateTimeFormatter.ISO_INSTANT.format(value))
    }

    override fun deserialize(decoder: Decoder): Instant =
        Instant.parse(decoder.decodeString())
}

/** `java.time.LocalDate` ↔ ISO-8601 日期字符串（如 `2026-08-08`） */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }

    override fun deserialize(decoder: Decoder): LocalDate =
        LocalDate.parse(decoder.decodeString())
}

/** `java.time.LocalDateTime` ↔ ISO-8601 本地时间字符串（无时区，如 `2026-08-08T09:30:00`） */
object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.LocalDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeString(value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
    }

    override fun deserialize(decoder: Decoder): LocalDateTime =
        LocalDateTime.parse(decoder.decodeString())
}

/**
 * `Set<DayOfWeek>` ↔ 逗号分隔枚举名（如 `MONDAY,WEDNESDAY,FRIDAY`）
 * 空集合 → 空字符串（跟 Converters.fromDayOfWeekSet 保持一致，避免 NOT NULL 写入失败）
 *
 * 不用 ListSerializer<DayOfWeek> + joinToString，因为 KSP 偶发解析不到
 * DayOfWeek.serializer() 扩展；自己写 string-based 序列化最稳
 */
object DayOfWeekSetSerializer : KSerializer<Set<DayOfWeek>> {
    private const val separator = ","

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Set<DayOfWeek>", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Set<DayOfWeek>) {
        encoder.encodeString(value.joinToString(separator) { it.name })
    }

    override fun deserialize(decoder: Decoder): Set<DayOfWeek> {
        val s = decoder.decodeString()
        if (s.isEmpty()) return emptySet()
        return s.split(separator).mapTo(linkedSetOf()) { DayOfWeek.valueOf(it) }
    }
}

/**
 * `List<String>` ↔ JSON 数组
 * affectedTasks 字段专用 — 显式对象是为了 DTO 字段标注语义化
 *
 * 用 kotlinx.serialization 自带 ListSerializer（走 builtins.serializer<String>() reified）
 */
object StringListSerializer : KSerializer<List<String>> {
    private val delegate: KSerializer<List<String>> = ListSerializer(serializer<String>())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<String>) {
        delegate.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): List<String> = delegate.deserialize(decoder)
}
