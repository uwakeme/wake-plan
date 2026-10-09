package com.wakeplan.app.data.backup

import android.content.Context
import android.net.Uri
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 备份文件读取 — Phase 4.4，v0.4.1 加大小上限
 *
 * 跟 BackupFileWriter 对称：Path 版本（Java NIO） + Uri 版本（SAF）
 * 正常备份（≤ 1k completion）只有几百 KB；超上限的多半是选错文件或恶意文件，
 * 拒绝读入避免整文件进内存后 JSON 解析 OOM
 */
@Singleton
class BackupFileReader @Inject constructor() {

    /**
     * 从本地路径读 BackupFile JSON
     */
    @Throws(IOException::class)
    fun readFromPath(source: Path): String {
        val size = Files.size(source)
        if (size > MAX_FILE_BYTES) throw IOException(backupTooLargeMessage(size))
        return String(Files.readAllBytes(source), Charsets.UTF_8)
    }

    /**
     * 从 SAF Uri 读 BackupFile JSON
     * @param context Application context
     * @param uri 通过 ActivityResultContracts.OpenDocument() 拿到的源 Uri
     */
    @Throws(IOException::class)
    fun readFromUri(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            if (bytes.size > MAX_FILE_BYTES) throw IOException(backupTooLargeMessage(bytes.size.toLong()))
            String(bytes, Charsets.UTF_8)
        } ?: throw IOException("can't open input stream for $uri")
    }

    private fun backupTooLargeMessage(size: Long): String =
        "backup file too large: $size bytes (max ${MAX_FILE_BYTES / 1024 / 1024}MB)"

    companion object {
        /** 64MB 上限——设计容量 5 年 ≤ 50MB（PRD 5.2），留出余量 */
        const val MAX_FILE_BYTES = 64L * 1024 * 1024
    }
}
