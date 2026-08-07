package com.wakeplan.app.data.backup

import android.content.Context
import android.net.Uri
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 备份文件读取 — Phase 4.4
 *
 * 跟 BackupFileWriter 对称：Path 版本（Java NIO） + Uri 版本（SAF）
 */
@Singleton
class BackupFileReader @Inject constructor() {

    /**
     * 从本地路径读 BackupFile JSON
     */
    @Throws(IOException::class)
    fun readFromPath(source: Path): String {
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
            String(stream.readBytes(), Charsets.UTF_8)
        } ?: throw IOException("can't open input stream for $uri")
    }
}
