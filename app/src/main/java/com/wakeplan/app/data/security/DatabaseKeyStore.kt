package com.wakeplan.app.data.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据库加密密钥管理 — Phase 5.8
 *
 * 流程：
 * 1. MasterKeyProvider 拿到 master key（生产 = AndroidKeyStore AES-GCM 256；测试 = SecretKeySpec）
 * 2. 首次启动：随机 32 字节 DB passphrase
 *    → 用 master key wrap（IV 12B || ciphertext + GCM tag 16B）→ 存 DataStore
 * 3. 后续启动：unwrap 取出明文 → 缓存
 * 4. SupportOpenHelperFactory 接 passphrase 打开 SQLCipher DB
 *
 * 关键属性：
 * - master key 不可导出（生产实现用 AndroidKeyStore）
 * - DataStore 本身是明文（应用沙盒内），但 wrap 后的密文 + IV 即使被读也无法解密
 * - 卸载后 AndroidKeyStore 自动清理，DB 文件无法再读
 *
 * 线程安全：
 * - masterKey 是 `by lazy`（thread-safe by default in Kotlin）
 * - cachedPassphrase 是 `@Volatile`
 * - mutex 保护 read-modify-write 临界区（不能用 synchronized — Kotlin 禁止 suspend 在其中）
 *
 * 错误恢复（v0.4.1 实装）：
 * - unwrap 失败（DataStore 损坏 / master key 被系统失效等）不再向上抛——旧版直接
 *   抛进启动崩溃循环。现在重新生成 passphrase 并删除旧 DB 文件（新 passphrase 打不开
 *   旧库，留着只会让 Room 抛错）。代价：本地数据丢失，可从备份导入恢复。
 */
@Singleton
class DatabaseKeyStore @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val masterKeyProvider: MasterKeyProvider,
    private val dataStore: DataStore<Preferences>,
) {

    private val masterKey: SecretKey by lazy { masterKeyProvider.getOrCreate() }
    private val mutex = Mutex()

    @Volatile
    private var cachedPassphrase: ByteArray? = null

    /**
     * 取出（或生成）32 字节的 DB passphrase
     *
     * 多次调用：第一次真做 Keystore + DataStore 操作，之后走 cachedPassphrase
     * 调用方负责在合适线程（Dispatchers.IO）跑；App.onCreate 用 runBlocking 强制同步
     */
    suspend fun getOrCreatePassphrase(): ByteArray {
        cachedPassphrase?.let { return it }
        return mutex.withLock {
            cachedPassphrase?.let { return@withLock it }
            val stored = dataStore.data.first()[KEY_WRAPPED_PASSPHRASE]
            val passphrase = if (stored == null) {
                generateAndStore()
            } else {
                try {
                    unwrapWithKey(stored, masterKey)
                } catch (e: Exception) {
                    // master key 失效 / DataStore 损坏：重生成 + 删旧库，避免崩溃循环
                    deleteDatabaseFiles()
                    generateAndStore()
                }
            }
            cachedPassphrase = passphrase
            passphrase
        }
    }

    private suspend fun generateAndStore(): ByteArray {
        val fresh = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val wrapped = wrapWithKey(fresh, masterKey)
        dataStore.edit { it[KEY_WRAPPED_PASSPHRASE] = wrapped }
        return fresh
    }

    /** 新 passphrase 打不开旧加密库，连同 wal/shm/journal 一起删，Room 重建空库 */
    private fun deleteDatabaseFiles() {
        val dbFile = context.getDatabasePath(com.wakeplan.app.data.db.WakePlanDatabase.DB_NAME)
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            val f = java.io.File(dbFile.parentFile, dbFile.name + suffix)
            if (f.exists()) f.delete()
        }
    }

    /**
     * 同步取 cached passphrase。Cache miss 时**自动**同步 bootstrap（runBlocking + IO 50ms）。
     *
     * 这个方法必须能在 Hilt 注入期间同步调用：Hilt 解析 `DatabaseKeyStore` → `WakePlanDatabase`
     * 链时，`provideDatabase` 会立刻执行，那时 App.onCreate 的 warm-up 还没跑。
     * 用 runBlocking 把 Keystore + DataStore 阻塞起来（< 100ms 在主线程可接受）。
     *
     * App.onCreate 里仍建议显式调一次 `getOrCreatePassphrase()` 做 warm-up（避免后续访问首延迟）。
     */
    fun getCachedOrThrow(): ByteArray = cachedPassphrase ?: runBlocking(Dispatchers.IO) {
        getOrCreatePassphrase()
    }

    /**
     * 清空缓存和 DataStore（开发用：reset DB 时配套调用）
     * 不会动 AndroidKeyStore 里的 master key（重新 wrap 时仍用同一 key）
     */
    suspend fun reset() {
        mutex.withLock {
            cachedPassphrase?.fill(0)
            cachedPassphrase = null
            dataStore.edit { it.remove(KEY_WRAPPED_PASSPHRASE) }
        }
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = 16  // 128 bits = 16 bytes
        private const val PASSPHRASE_BYTES = 32

        private val KEY_WRAPPED_PASSPHRASE = stringPreferencesKey("db_passphrase_wrapped")

        /**
         * 测试可见：AES-GCM wrap with any SecretKey。
         * 生产用 AndroidKeyStore 的 master key；测试用 SecretKeySpec 注入固定字节。
         */
        @JvmStatic
        fun wrapWithKey(plaintext: ByteArray, key: SecretKey): String {
            val cipher = javax.crypto.Cipher.getInstance(TRANSFORMATION)
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext)
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
            return java.util.Base64.getEncoder().withoutPadding().encodeToString(combined)
        }

        @JvmStatic
        fun unwrapWithKey(wrapped: String, key: SecretKey): ByteArray {
            val combined = java.util.Base64.getDecoder().decode(wrapped)
            require(combined.size > GCM_IV_BYTES + GCM_TAG_BYTES) { "Wrapped DB passphrase too short" }
            val iv = combined.copyOfRange(0, GCM_IV_BYTES)
            val ciphertext = combined.copyOfRange(GCM_IV_BYTES, combined.size)
            val cipher = javax.crypto.Cipher.getInstance(TRANSFORMATION)
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(GCM_TAG_BITS, iv))
            return cipher.doFinal(ciphertext)
        }
    }
}
