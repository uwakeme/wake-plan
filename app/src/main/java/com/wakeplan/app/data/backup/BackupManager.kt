package com.wakeplan.app.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 备份编排 — Phase 4.4
 *
 * ViewModel 跟 I/O 之间的中间层：
 * - ViewModel 调 exportToUri / importFromUri
 * - 内部组合 BackupExporter / BackupImporter / BackupFileWriter / BackupFileReader
 * - ApplicationContext 走 Hilt 注入，避免 ViewModel 直接拿 Activity context
 *
 * 出错语义：抛异常（IOException / SerializationException / AEADBadTagException /
 * IllegalStateException 等），由 ViewModel 捕获后映射成 BackupState.Error
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exporter: BackupExporter,
    private val importer: BackupImporter,
    private val fileWriter: BackupFileWriter,
    private val fileReader: BackupFileReader,
) {
    /**
     * 导出到 SAF Uri
     * @param uri 目标 content:// Uri（用户从 SAF CreateDocument 选的）
     * @param passphrase null = 明文；非空 = 加密（用完请 clear）
     * @return 写入字节数
     */
    suspend fun exportToUri(uri: Uri, passphrase: CharArray? = null): Long {
        val json = exporter.exportToBackupFile(passphrase = passphrase)
        return fileWriter.writeToUri(context, uri, json)
    }

    /**
     * 从 SAF Uri 导入
     * @param uri 源 content:// Uri（用户从 SAF OpenDocument 选的）
     * @param passphrase 加密备份必填；明文传 null
     * @return 导入条数
     */
    suspend fun importFromUri(uri: Uri, passphrase: CharArray? = null): BackupImporter.ImportResult {
        val json = fileReader.readFromUri(context, uri)
        return importer.import(json, passphrase)
    }
}
