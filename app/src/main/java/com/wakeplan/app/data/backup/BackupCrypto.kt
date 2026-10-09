package com.wakeplan.app.data.backup

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份加密 / 解密 — Phase 4.2
 *
 * 密码学原语：
 * - KDF: PBKDF2-SHA256（OWASP 2023 推荐 ≥ 600,000 iter；v0.1 固定 600,000）
 * - Cipher: AES-256-GCM（authenticated encryption，自带完整性校验）
 * - Salt: 16 字节随机（每次加密重新生成，保证同一明文每次密文不同）
 * - IV: 12 字节随机（GCM 标准 nonce 长度）
 * - Tag: 128 bit（Java GCM 默认；密文末尾 16 字节）
 *
 * 格式：
 *   输入：ByteArray plaintext + CharArray passphrase
 *   输出：EncryptedResult(ciphertext = iv前不拼, ciphertext 包含 tag)
 *   跟 metadata 一起写到 BackupFile：salt + iv 走 base64 单独存，ciphertext 走 base64
 *
 * 安全注意：
 * - passphrase 走 CharArray 是为了用完即清（明文 String 不可擦除，会驻留 JVM 字符串池）
 * - SecureRandom 是 Android 默认的 StrongSecureRandom，OK
 * - 不用 CBC 模式 — GCM 自带认证 tag 能识别篡改
 */
object BackupCrypto {

    /** OWASP 2023 推荐 PBKDF2-SHA256 ≥ 600k iter */
    const val DEFAULT_ITERATIONS = 600_000

    /** 解密接受的迭代上限——防恶意备份声明超大迭代把 KDF 挂死（DoS） */
    const val MAX_ITERATIONS = 2_000_000

    /** AES-256 key = 32 bytes */
    private const val KEY_LENGTH_BITS = 256

    /** GCM IV length */
    private const val GCM_IV_LENGTH_BYTES = 12

    /** GCM authentication tag */
    private const val GCM_TAG_LENGTH_BITS = 128

    /** PBKDF2 salt */
    private const val SALT_LENGTH_BYTES = 16

    private val secureRandom = SecureRandom()

    /**
     * 加密结果 — Phase 4.2
     *
     * @param ciphertext 不含 salt/iv 前缀，但包含 GCM tag 末尾 16 字节
     * @param metadata 写盘时随 BackupFile 的 `encryption` 字段
     */
    data class EncryptedResult(
        val ciphertext: ByteArray,
        val metadata: EncryptionMetadata,
    ) {
        // ByteArray 的 equals 默认按引用比较，加一个值相等
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EncryptedResult) return false
            return ciphertext.contentEquals(other.ciphertext) && metadata == other.metadata
        }

        override fun hashCode(): Int = ciphertext.contentHashCode() * 31 + metadata.hashCode()
    }

    /**
     * 加密 ByteArray 明文
     * @param plaintext 要加密的字节（一般是 BackupEnvelope JSON 字符串的 UTF-8 字节）
     * @param passphrase 用户密码（用完请 clear；不要传 String）
     * @param iterations PBKDF2 迭代次数；测试可调小
     */
    fun encrypt(
        plaintext: ByteArray,
        passphrase: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
    ): EncryptedResult {
        require(passphrase.isNotEmpty()) { "passphrase must not be empty" }
        require(iterations >= 1) { "iterations must be >= 1" }

        val salt = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val key = deriveKey(passphrase, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)

        return EncryptedResult(
            ciphertext = ciphertext,
            metadata = EncryptionMetadata(
                kdf = "PBKDF2-SHA256",
                iterations = iterations,
                cipher = "AES-256-GCM",
                saltB64 = Base64.getEncoder().withoutPadding().encodeToString(salt),
                ivB64 = Base64.getEncoder().withoutPadding().encodeToString(iv),
            ),
        )
    }

    /**
     * 解密 ByteArray 密文
     * @throws javax.crypto.AEADBadTagException 密文被篡改或 tag 校验失败
     * @throws javax.crypto.BadPaddingException 密钥错误
     */
    fun decrypt(
        ciphertext: ByteArray,
        metadata: EncryptionMetadata,
        passphrase: CharArray,
    ): ByteArray {
        require(passphrase.isNotEmpty()) { "passphrase must not be empty" }
        require(metadata.kdf == "PBKDF2-SHA256") { "unsupported kdf: ${metadata.kdf}" }
        require(metadata.cipher == "AES-256-GCM") { "unsupported cipher: ${metadata.cipher}" }
        // 上限封顶：恶意备份文件可声明天文迭代次数把 KDF 挂死（DoS）
        require(metadata.iterations in 1..MAX_ITERATIONS) {
            "iterations out of accepted range: ${metadata.iterations}"
        }

        val salt = Base64.getDecoder().decode(metadata.saltB64)
        val iv = Base64.getDecoder().decode(metadata.ivB64)
        require(iv.size == GCM_IV_LENGTH_BYTES) { "invalid iv length: ${iv.size}" }
        val key = deriveKey(passphrase, salt, metadata.iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_LENGTH_BITS)
        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyBytes = factory.generateSecret(spec).encoded
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
