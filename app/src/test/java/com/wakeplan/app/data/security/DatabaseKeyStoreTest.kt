package com.wakeplan.app.data.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * DatabaseKeyStore 单测 — Phase 5.8
 *
 * 用 in-memory DataStore + SecretKeySpec 固定 master key（不走 AndroidKeyStore — JVM 单测没有）
 * 覆盖：
 * - wrap/unwrap AES-GCM round-trip
 * - getOrCreatePassphrase 首次（生成 + 写）+ 二次（读 + 解）
 * - reset
 * - getCachedOrThrow 边界
 */
@DisplayName("DatabaseKeyStore")
class DatabaseKeyStoreTest {

    private lateinit var tempDir: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var keyStore: DatabaseKeyStore
    private lateinit var testKey: SecretKey

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("wp_dbkey_").toFile()
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { File(tempDir, "test.preferences_pb") },
        )
        // 32 字节 (256-bit) AES key
        val rawKey = ByteArray(32) { (it * 7).toByte() }
        testKey = SecretKeySpec(rawKey, "AES")
        keyStore = DatabaseKeyStore(
            masterKeyProvider = MasterKeyProvider { testKey },
            dataStore = dataStore,
        )
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // ---- wrapWithKey / unwrapWithKey 静态方法 ----

    @Test
    fun `wrap and unwrap — round-trip returns original bytes`() {
        val original = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val wrapped = DatabaseKeyStore.wrapWithKey(original, testKey)
        val unwrapped = DatabaseKeyStore.unwrapWithKey(wrapped, testKey)
        assertArrayEquals(original, unwrapped, "AES-GCM 解密应该跟原文一致")
    }

    @Test
    fun `wrap — 同一明文两次 wrap 输出不同（IV 随机）`() {
        val plaintext = ByteArray(32) { 1 }
        val w1 = DatabaseKeyStore.wrapWithKey(plaintext, testKey)
        val w2 = DatabaseKeyStore.wrapWithKey(plaintext, testKey)
        assertNotEquals(w1, w2, "GCM IV 应该随机，否则 key 复用会被攻击者推断")
    }

    @Test
    fun `unwrap — 用错 key 抛 GCM 认证失败`() {
        val plaintext = ByteArray(32) { 1 }
        val wrapped = DatabaseKeyStore.wrapWithKey(plaintext, testKey)
        val wrongKey = SecretKeySpec(ByteArray(32) { (it * 13).toByte() }, "AES")
        assertThrows<Exception> { DatabaseKeyStore.unwrapWithKey(wrapped, wrongKey) }
    }

    @Test
    fun `unwrap — 短 wrapped 字符串抛 IllegalArgumentException`() {
        // IV(12) + tag(16) = 28B 最小长度；少了就 require() 失败
        val tooShort = "AAAA"  // Base64 解码后 < 28 字节
        assertThrows<IllegalArgumentException> { DatabaseKeyStore.unwrapWithKey(tooShort, testKey) }
    }

    // ---- getOrCreatePassphrase 行为 ----

    @Test
    fun `getOrCreatePassphrase 首次调用 — 生成 32 字节随机 + 存 DataStore + 缓存`() = runTest {
        val pass = keyStore.getOrCreatePassphrase()
        assertEquals(32, pass.size, "DB passphrase 必须是 32 字节")
        assertNotNull(pass, "必须返回非空")

        // 二次调用应直接走缓存
        val pass2 = keyStore.getOrCreatePassphrase()
        assertArrayEquals(pass, pass2, "二次调用必须返回相同字节（缓存）")

        // DataStore 必须存了 wrapped
        val stored = dataStore.data.first()[KEY_WRAPPED_PASSPHRASE]
        assertNotNull(stored, "首次调用后 DataStore 必须有 wrapped passphrase")
    }

    @Test
    fun `getOrCreatePassphrase 二次启动 — 从 DataStore unwrap 出原 passphrase`() = runTest {
        val first = keyStore.getOrCreatePassphrase()

        // 模拟重启：新建 keyStore 实例（empty cache），用同一 DataStore + 同一 master key
        val keyStore2 = DatabaseKeyStore(
            masterKeyProvider = MasterKeyProvider { testKey },
            dataStore = dataStore,
        )
        val second = keyStore2.getOrCreatePassphrase()
        assertArrayEquals(first, second, "重启后 unwrap 必须得到原 passphrase")
    }

    @Test
    fun `getOrCreatePassphrase 不同 key — unwrap 失败抛异常`() = runTest {
        val first = keyStore.getOrCreatePassphrase()
        assertNotNull(first)

        // 模拟：master key 被外部重置（uninstall 之外的场景）
        val wrongKey = SecretKeySpec(ByteArray(32) { (it * 17).toByte() }, "AES")
        val keyStore2 = DatabaseKeyStore(
            masterKeyProvider = MasterKeyProvider { wrongKey },
            dataStore = dataStore,
        )
        assertThrows<Exception> { keyStore2.getOrCreatePassphrase() }
    }

    // ---- getCachedOrThrow ----

    @Test
    fun `getCachedOrThrow — bootstrap 前自动同步 bootstrap，返回 32 字节 passphrase`() = runTest {
        // 现在不抛异常，而是同步 bootstrap（runBlocking < 50ms）
        val cached = keyStore.getCachedOrThrow()
        assertEquals(32, cached.size, "自动 bootstrap 后应返回 32 字节 passphrase")
    }

    @Test
    fun `getCachedOrThrow — bootstrap 后直接返回 cached（不重复 bootstrap）`() = runTest {
        val pass = keyStore.getOrCreatePassphrase()
        val cached = keyStore.getCachedOrThrow()
        assertArrayEquals(pass, cached)
    }

    // ---- reset ----

    @Test
    fun `reset — 清缓存 + 删 DataStore key，二次 getOrCreate 生成新的`() = runTest {
        val first = keyStore.getOrCreatePassphrase()
        keyStore.reset()

        val second = keyStore.getOrCreatePassphrase()
        // 新生成的应该跟旧的不同（SecureRandom 32 字节碰撞概率忽略）
        assertNotEquals(
            first.toList(),
            second.toList(),
            "reset 后重新生成的 passphrase 应该不同",
        )

        val stored = dataStore.data.first()[KEY_WRAPPED_PASSPHRASE]
        assertNotNull(stored, "reset + getOrCreate 后 DataStore 必须重新有值")
    }

    private companion object {
        // 必须跟生产 DatabaseKeyStore.KEY_WRAPPED_PASSPHRASE 字符串一致
        val KEY_WRAPPED_PASSPHRASE = stringPreferencesKey("db_passphrase_wrapped")
    }
}
