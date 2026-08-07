package com.wakeplan.app.data.backup

import android.content.Context
import android.net.Uri
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 备份文件落盘 — Phase 4.1 + Phase 4.4
 *
 * Phase 4.1 加 Path 版本（Java NIO）
 * Phase 4.4 加 Uri 版本（SAF / DocumentsContract / content://）
 * 两者签名相近，调用方按场景选
 */
@Singleton
class BackupFileWriter @Inject constructor() {

    /**
     * 把 JSON 字符串写到本地路径（Java NIO）
     * @param target 目标文件路径
     * @param json 序列化好的备份 JSON
     * @return 写入的字节数
     * @throws IOException 写盘失败（磁盘满、权限等）
     */
    @Throws(IOException::class)
    fun writeToPath(target: Path, json: String): Long {
        target.parent?.let { Files.createDirectories(it) }
        val bytes = json.toByteArray(Charsets.UTF_8)
        Files.write(
            target,
            bytes,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
        return bytes.size.toLong()
    }

    /**
     * 把 JSON 字符串写到 SAF Uri（content://）
     * @param context Application context
     * @param uri 通过 ActivityResultContracts.CreateDocument() 拿到的目标 Uri
     * @param json 序列化好的备份 JSON
     * @return 写入的字节数
     * @throws IOException 写盘失败
     */
    @Throws(IOException::class)
    fun writeToUri(context: Context, uri: Uri, json: String): Long {
        val bytes = json.toByteArray(Charsets.UTF_8)
        // mode "wt" = write + truncate
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(bytes)
            stream.flush()
        } ?: throw IOException("can't open output stream for $uri")
        return bytes.size.toLong()
    }
}
