package com.wakeplan.app.data.backup

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException

/**
 * BackupCrypto 加解密测试 — Phase 4.2
 *
 * **用小 iteration (1000)** 避免 600k iter 把单测拖慢 200ms+
 * 默认 iter 行为另开一个 sanity 测
 */
@DisplayName("BackupCrypto")
class BackupCryptoTest {

    private val testPassphrase = "correct-horse-battery-staple".toCharArray()
    private val testIterations = 1_000

    @BeforeEach
    fun setUp() {
        // 每次跑前重置
    }

    @AfterEach
    fun tearDown() {
        testPassphrase.fill('\u0000')
    }

    @Test
    fun `round-trip — 同一 passphrase 解密后等于原文`() {
        val plaintext = """{"hello":"world","data":[1,2,3]}""".toByteArray(Charsets.UTF_8)
        val result = BackupCrypto.encrypt(plaintext, testPassphrase, testIterations)

        val decrypted = BackupCrypto.decrypt(result.ciphertext, result.metadata, testPassphrase)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `每次加密 salt 跟 iv 都不同 — 同一明文两次密文不同`() {
        val plaintext = "same data".toByteArray(Charsets.UTF_8)
        val r1 = BackupCrypto.encrypt(plaintext, testPassphrase, testIterations)
        val r2 = BackupCrypto.encrypt(plaintext, testPassphrase, testIterations)

        // salt 必不同
        assertNotEquals(r1.metadata.saltB64, r2.metadata.saltB64)
        // iv 必不同
        assertNotEquals(r1.metadata.ivB64, r2.metadata.ivB64)
        // 密文必不同
        assertTrue(!r1.ciphertext.contentEquals(r2.ciphertext))
    }

    @Test
    fun `错误 passphrase 解密失败抛异常`() {
        val plaintext = "secret message".toByteArray(Charsets.UTF_8)
        val result = BackupCrypto.encrypt(plaintext, testPassphrase, testIterations)

        val wrongPassphrase = "wrong-passphrase-here".toCharArray()
        try {
            assertThrows<javax.crypto.AEADBadTagException> {
                BackupCrypto.decrypt(result.ciphertext, result.metadata, wrongPassphrase)
            }
        } finally {
            wrongPassphrase.fill('\u0000')
        }
    }

    @Test
    fun `篡改 ciphertext 1 字节 解密失败（tag 校验）`() {
        val plaintext = "integrity check".toByteArray(Charsets.UTF_8)
        val result = BackupCrypto.encrypt(plaintext, testPassphrase, testIterations)

        // 改密文中间一字节
        val tampered = result.ciphertext.copyOf()
        tampered[tampered.size / 2] = (tampered[tampered.size / 2].toInt() xor 0x01).toByte()

        assertThrows<AEADBadTagException> {
            BackupCrypto.decrypt(tampered, result.metadata, testPassphrase)
        }
    }

    @Test
    fun `篡改 ciphertext 最后 16 字节（tag）解密失败`() {
        val plaintext = "tag check".toByteArray(Charsets.UTF_8)
        val result = BackupCrypto.encrypt(plaintext, testPassphrase, testIterations)

        // 改最后 1 字节（tag 区域）
        val tampered = result.ciphertext.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0xff).toByte()

        assertThrows<AEADBadTagException> {
            BackupCrypto.decrypt(tampered, result.metadata, testPassphrase)
        }
    }

    @Test
    fun `不同 iterations 派生不同 key`() {
        val plaintext = "data".toByteArray(Charsets.UTF_8)
        val r1 = BackupCrypto.encrypt(plaintext, testPassphrase, 1_000)
        val r2 = BackupCrypto.encrypt(plaintext, testPassphrase, 2_000)

        // salt 跟 iv 都不同，所以 ciphertext 一定不同
        assertNotEquals(r1.metadata.saltB64, r2.metadata.saltB64)
        // iterations 字段被记录
        assertEquals(1_000, r1.metadata.iterations)
        assertEquals(2_000, r2.metadata.iterations)
    }

    @Test
    fun `ciphertext 长度 = plaintext 长度 + 16（GCM tag）`() {
        val plaintext = ByteArray(100) { it.toByte() }  // 100 字节
        val result = BackupCrypto.encrypt(plaintext, testPassphrase, testIterations)

        // AES-GCM 是 stream cipher，密文 = 明文 + tag(16)
        assertEquals(plaintext.size + 16, result.ciphertext.size)
    }

    @Test
    fun `salt 长度 16 字节 — Base64 解出来 16 字节`() {
        val plaintext = "x".toByteArray(Charsets.UTF_8)
        val result = BackupCrypto.encrypt(plaintext, testPassphrase, testIterations)

        val salt = Base64.getDecoder().decode(result.metadata.saltB64)
        val iv = Base64.getDecoder().decode(result.metadata.ivB64)
        assertEquals(16, salt.size)
        assertEquals(12, iv.size)
    }

    @Test
    fun `空 passphrase 抛 IllegalArgumentException`() {
        val plaintext = "data".toByteArray(Charsets.UTF_8)
        val empty = CharArray(0)
        assertThrows<IllegalArgumentException> {
            BackupCrypto.encrypt(plaintext, empty, testIterations)
        }
    }

    @Test
    fun `iterations 小于 1 抛 IllegalArgumentException`() {
        val plaintext = "data".toByteArray(Charsets.UTF_8)
        assertThrows<IllegalArgumentException> {
            BackupCrypto.encrypt(plaintext, testPassphrase, 0)
        }
        assertThrows<IllegalArgumentException> {
            BackupCrypto.encrypt(plaintext, testPassphrase, -1)
        }
    }

    @Test
    fun `decrypt 拒绝不支持的 kdf`() {
        val metadata = EncryptionMetadata(
            kdf = "PBKDF2-SHA1",
            iterations = 1_000,
            cipher = "AES-256-GCM",
            saltB64 = "AAAAAAAAAAAAAAAAAAAAAA==",
            ivB64 = "AAAAAAAAAAAAAAAA",
        )
        assertThrows<IllegalArgumentException> {
            BackupCrypto.decrypt(ByteArray(0), metadata, testPassphrase)
        }
    }

    @Test
    fun `decrypt 拒绝不支持的 cipher`() {
        val metadata = EncryptionMetadata(
            kdf = "PBKDF2-SHA256",
            iterations = 1_000,
            cipher = "AES-256-CBC",
            saltB64 = "AAAAAAAAAAAAAAAAAAAAAA==",
            ivB64 = "AAAAAAAAAAAAAAAA",
        )
        assertThrows<IllegalArgumentException> {
            BackupCrypto.decrypt(ByteArray(0), metadata, testPassphrase)
        }
    }

    @Test
    fun `中文明文 round-trip 不乱码`() {
        val plaintext = """{"plans":[{"title":"晨练","note":"5km 慢跑 心率 < 150"}]}""".toByteArray(Charsets.UTF_8)
        val result = BackupCrypto.encrypt(plaintext, testPassphrase, testIterations)
        val decrypted = BackupCrypto.decrypt(result.ciphertext, result.metadata, testPassphrase)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `大文件 100KB 加密解密仍然正确`() {
        val rng = SecureRandom()
        val plaintext = ByteArray(100 * 1024).also { rng.nextBytes(it) }
        val result = BackupCrypto.encrypt(plaintext, testPassphrase, testIterations)
        assertEquals(100 * 1024 + 16, result.ciphertext.size)
        val decrypted = BackupCrypto.decrypt(result.ciphertext, result.metadata, testPassphrase)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `DEFAULT_ITERATIONS 等于 600000（OWASP 2023 推荐）`() {
        assertEquals(600_000, BackupCrypto.DEFAULT_ITERATIONS)
    }

    @Test
    fun `600k iter 派生 key 实际耗时合理（小于 2s）`() {
        // sanity check — 在 JVM 上 600k iter SHA-256 应该在 200-1000ms 完成
        val pass = "perf-test".toCharArray()
        val salt = ByteArray(16) { it.toByte() }
        val start = System.nanoTime()
        // 直接 deriveKey 通过反射 / 包内访问比较难，这里走 encrypt 整流程
        val result = BackupCrypto.encrypt("x".toByteArray(Charsets.UTF_8), pass, BackupCrypto.DEFAULT_ITERATIONS)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        // 600k 应该 < 2000ms
        assertTrue(elapsedMs < 2_000, "600k iter took ${elapsedMs}ms, expected < 2000ms")
        pass.fill('\u0000')
        // sanity check: still correct
        val decrypted = BackupCrypto.decrypt(result.ciphertext, result.metadata, "perf-test".toCharArray())
        assertArrayEquals("x".toByteArray(Charsets.UTF_8), decrypted)
    }
}
