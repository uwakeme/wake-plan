package com.wakeplan.app.data.backup

import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.TaskStatus
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * BackupFile 磁盘文件级 round-trip — Phase 4.2
 *
 * 测试 plain / encrypted 两种 format 的 JSON 编解码
 */
@DisplayName("BackupFile")
class BackupFileTest {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private fun sampleEnvelope() = BackupEnvelope(
        schemaVersion = 1,
        exportedAt = Instant.parse("2026-08-08T01:00:00Z"),
        appVersionName = "0.1.0-alpha",
        appVersionCode = 1,
        data = BackupData(
            plans = listOf(
                BackupPlan(
                    id = "plan-1",
                    title = "晨练",
                    colorHex = "#C48520",
                    startDate = LocalDate.of(2026, 8, 1),
                    isActive = true,
                    createdAt = Instant.parse("2026-08-01T00:00:00Z"),
                    updatedAt = Instant.parse("2026-08-07T12:00:00Z"),
                ),
            ),
            segments = emptyList(),
            tasks = emptyList(),
            taskCompletions = emptyList(),
            planTemplates = emptyList(),
        ),
    )

    @Test
    fun `明文 BackupFile round-trip format=plain-v1`() {
        val original = BackupFile(
            format = BackupFile.FORMAT_PLAIN,
            encryption = null,
            envelope = sampleEnvelope(),
            data = null,
        )
        val encoded = json.encodeToString(BackupFile.serializer(), original)
        val decoded = json.decodeFromString(BackupFile.serializer(), encoded)
        assertEquals(original, decoded)
        assertEquals("plain-v1", decoded.format)
        assertNull(decoded.encryption)
        assertNotNull(decoded.envelope)
        assertNull(decoded.data)
    }

    @Test
    fun `加密 BackupFile round-trip format=encrypted-v1`() {
        val metadata = EncryptionMetadata(
            kdf = "PBKDF2-SHA256",
            iterations = 600_000,
            cipher = "AES-256-GCM",
            saltB64 = "AAAAAAAAAAAAAAAAAAAAAA==",
            ivB64 = "AAAAAAAAAAAAAAAA",
        )
        val original = BackupFile(
            format = BackupFile.FORMAT_ENCRYPTED,
            encryption = metadata,
            envelope = null,
            data = "base64ciphertexthere",
        )
        val encoded = json.encodeToString(BackupFile.serializer(), original)
        val decoded = json.decodeFromString(BackupFile.serializer(), encoded)
        assertEquals(original, decoded)
        assertEquals("encrypted-v1", decoded.format)
        assertNotNull(decoded.encryption)
        assertEquals(600_000, decoded.encryption!!.iterations)
        assertEquals("AES-256-GCM", decoded.encryption!!.cipher)
        assertNull(decoded.envelope)
        assertEquals("base64ciphertexthere", decoded.data)
    }

    @Test
    fun `明文文件 JSON 包含 fileSchemaVersion 和 format 字段`() {
        val original = BackupFile(
            format = BackupFile.FORMAT_PLAIN,
            envelope = sampleEnvelope(),
        )
        val encoded = json.encodeToString(BackupFile.serializer(), original)
        assertTrue(encoded.contains("\"fileSchemaVersion\": 1"))
        assertTrue(encoded.contains("\"format\": \"plain-v1\""))
        assertTrue(encoded.contains("\"envelope\""))
        // encryption 字段存在但值为 null
        assertTrue(encoded.contains("\"encryption\": null"))
    }

    @Test
    fun `加密文件 JSON 包含 encryption metadata + data 字段`() {
        val metadata = EncryptionMetadata(
            kdf = "PBKDF2-SHA256",
            iterations = 600_000,
            cipher = "AES-256-GCM",
            saltB64 = "AAAAAAAAAAAAAAAAAAAAAA==",
            ivB64 = "AAAAAAAAAAAAAAAA",
        )
        val original = BackupFile(
            format = BackupFile.FORMAT_ENCRYPTED,
            encryption = metadata,
            data = "ZW5jcnlwdGVkZGF0YQ==",
        )
        val encoded = json.encodeToString(BackupFile.serializer(), original)
        assertTrue(encoded.contains("\"format\": \"encrypted-v1\""))
        assertTrue(encoded.contains("\"PBKDF2-SHA256\""))
        assertTrue(encoded.contains("\"AES-256-GCM\""))
        assertTrue(encoded.contains("\"data\": \"ZW5jcnlwdGVkZGF0YQ==\""))
        // envelope 字段存在但值为 null
        assertTrue(encoded.contains("\"envelope\": null"))
    }

    @Test
    fun `FILE_SCHEMA_VERSION 常量等于 1`() {
        assertEquals(1, BackupFile.FILE_SCHEMA_VERSION)
    }

    @Test
    fun `FORMAT_PLAIN 和 FORMAT_ENCRYPTED 字符串固定`() {
        assertEquals("plain-v1", BackupFile.FORMAT_PLAIN)
        assertEquals("encrypted-v1", BackupFile.FORMAT_ENCRYPTED)
    }
}
