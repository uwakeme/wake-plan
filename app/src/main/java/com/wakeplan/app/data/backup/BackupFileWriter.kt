package com.wakeplan.app.data.backup

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 备份文件落盘 — Phase 4.1
 *
 * Phase 4.1 写本地路径（用 Java NIO，Android API 26+ 支持）
 * Phase 4.4 替换成 SAF 适配：context.contentResolver.openOutputStream(uri)
 * 两者都实现 `writeTo(sink, json)` 形式，便于切换
 */
@Singleton
class BackupFileWriter @Inject constructor() {

    /**
     * 把 JSON 字符串写到本地路径
     * @param target 目标文件路径（一般由调用方决定：cacheDir/wakeplan-backup-{ts}.json 或 SAF Uri 解出来的本地 path）
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
}
