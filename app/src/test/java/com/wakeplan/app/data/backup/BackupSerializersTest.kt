package com.wakeplan.app.data.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * BackupSerializers 5 个自定义序列化器 round-trip — Phase 4.1
 *
 * 用一个简单的 wrapper data class 走 Json.encodeToString / decodeFromString 验证
 * 纯 JVM 单测，验证序列化反序列化不丢精度
 */
@DisplayName("BackupSerializers")
class BackupSerializersTest {

    private val json = Json { encodeDefaults = true }

    @kotlinx.serialization.Serializable
    data class InstantWrapper(
        @kotlinx.serialization.Serializable(with = InstantSerializer::class)
        val v: Instant,
    )

    @kotlinx.serialization.Serializable
    data class LocalDateWrapper(
        @kotlinx.serialization.Serializable(with = LocalDateSerializer::class)
        val v: LocalDate,
    )

    @kotlinx.serialization.Serializable
    data class LocalDateTimeWrapper(
        @kotlinx.serialization.Serializable(with = LocalDateTimeSerializer::class)
        val v: LocalDateTime,
    )

    @kotlinx.serialization.Serializable
    data class DayOfWeekSetWrapper(
        @kotlinx.serialization.Serializable(with = DayOfWeekSetSerializer::class)
        val v: Set<DayOfWeek>,
    )

    @kotlinx.serialization.Serializable
    data class StringListWrapper(
        @kotlinx.serialization.Serializable(with = StringListSerializer::class)
        val v: List<String>,
    )

    @Test
    fun `Instant round-trip 保留纳秒精度`() {
        val original = Instant.parse("2026-08-08T01:23:45.678901234Z")
        val encoded = json.encodeToString(InstantWrapper.serializer(), InstantWrapper(original))
        val decoded = json.decodeFromString(InstantWrapper.serializer(), encoded)
        assertEquals(original, decoded.v)
        // ISO-8601 字符串里保留
        val match = Regex("\"v\":\"([^\"]+)\"").find(encoded)
        assertEquals("2026-08-08T01:23:45.678901234Z", match?.groupValues?.get(1))
    }

    @Test
    fun `LocalDate round-trip`() {
        val original = LocalDate.of(2026, 8, 8)
        val encoded = json.encodeToString(LocalDateWrapper.serializer(), LocalDateWrapper(original))
        val decoded = json.decodeFromString(LocalDateWrapper.serializer(), encoded)
        assertEquals(original, decoded.v)
    }

    @Test
    fun `LocalDateTime round-trip 无时区`() {
        val original = LocalDateTime.of(2026, 8, 8, 9, 30, 0)
        val encoded = json.encodeToString(LocalDateTimeWrapper.serializer(), LocalDateTimeWrapper(original))
        val decoded = json.decodeFromString(LocalDateTimeWrapper.serializer(), encoded)
        assertEquals(original, decoded.v)
    }

    @Test
    fun `DayOfWeekSet 多个值 round-trip — MONDAY,WEDNESDAY,FRIDAY`() {
        val original = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        val encoded = json.encodeToString(DayOfWeekSetWrapper.serializer(), DayOfWeekSetWrapper(original))
        val decoded = json.decodeFromString(DayOfWeekSetWrapper.serializer(), encoded)
        assertEquals(original, decoded.v)
    }

    @Test
    fun `DayOfWeekSet 单个值 round-trip`() {
        val original = setOf(DayOfWeek.SUNDAY)
        val encoded = json.encodeToString(DayOfWeekSetWrapper.serializer(), DayOfWeekSetWrapper(original))
        val decoded = json.decodeFromString(DayOfWeekSetWrapper.serializer(), encoded)
        assertEquals(original, decoded.v)
    }

    @Test
    fun `DayOfWeekSet 空集合 round-trip 为空字符串`() {
        val original = emptySet<DayOfWeek>()
        val encoded = json.encodeToString(DayOfWeekSetWrapper.serializer(), DayOfWeekSetWrapper(original))
        // 编码后是 "v":""，跟 Converters.fromDayOfWeekSet 行为一致
        assertEquals("""{"v":""}""", encoded)
        val decoded = json.decodeFromString(DayOfWeekSetWrapper.serializer(), encoded)
        assertEquals(original, decoded.v)
    }

    @Test
    fun `StringList round-trip 含空字符串和中文`() {
        val original = listOf("", "跑步", "阅读", "🀄")
        val encoded = json.encodeToString(StringListWrapper.serializer(), StringListWrapper(original))
        val decoded = json.decodeFromString(StringListWrapper.serializer(), encoded)
        assertEquals(original, decoded.v)
    }

    @Test
    fun `StringList 空列表 round-trip`() {
        val original = emptyList<String>()
        val encoded = json.encodeToString(StringListWrapper.serializer(), StringListWrapper(original))
        assertEquals("""{"v":[]}""", encoded)
        val decoded = json.decodeFromString(StringListWrapper.serializer(), encoded)
        assertEquals(original, decoded.v)
    }

    @Test
    fun `Instant 解析失败抛异常`() {
        // 用 buildJsonObject 手写非法字符串
        val bad = buildJsonObject { put("v", JsonPrimitive("not-a-real-instant")) }
        try {
            json.decodeFromString(InstantWrapper.serializer(), bad.toString())
            error("expected exception")
        } catch (e: Exception) {
            // 接受 SerializationException / DateTimeException / IllegalArgumentException
            // Instant.parse 抛 DateTimeParseException extends DateTimeException extends RuntimeException
            assert(
                e is kotlinx.serialization.SerializationException ||
                    e is IllegalArgumentException ||
                    e is java.time.DateTimeException,
            ) {
                "got ${e::class.simpleName}: ${e.message}"
            }
        }
    }
}
