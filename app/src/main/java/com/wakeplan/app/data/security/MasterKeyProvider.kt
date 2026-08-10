package com.wakeplan.app.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * master key 源 — Phase 5.8
 *
 * 生产实现：AndroidKeyStore 里的 AES-256 GCM key（不可导出）
 * 测试实现：固定 SecretKeySpec（不需要真 Keystore）
 *
 * 抽接口的原因：让 DatabaseKeyStore 可 JVM 单测覆盖（pure-Kotlin 不依赖 Android Keystore stub）
 */
fun interface MasterKeyProvider {
    fun getOrCreate(): SecretKey
}

@Singleton
class AndroidKeystoreMasterKeyProvider @Inject constructor() : MasterKeyProvider {
    override fun getOrCreate(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(MASTER_KEY_ALIAS, null)?.let { return it as SecretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(MASTER_KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "wakeplan.db.master"
        private const val MASTER_KEY_SIZE_BITS = 256
    }
}
