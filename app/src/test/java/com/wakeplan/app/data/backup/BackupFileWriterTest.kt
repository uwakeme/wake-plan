package com.wakeplan.app.data.backup

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * BackupFileWriter 落盘测试 — Phase 4.1
 *
 * 写一个 temp 文件，读回来验证字节数 + 内容
 * 用 Java NIO；纯 JVM 单测
 *
 * 不用 Files.readString / Files.writeString（Java 11+），用 readAllBytes / write
 * 保证测试在 Java 8 stdlib 也能跑
 */
@DisplayName("BackupFileWriter")
class BackupFileWriterTest {

    private lateinit var tempDir: Path
    private lateinit var writer: BackupFileWriter

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("wp_backup_writer_")
        writer = BackupFileWriter()
    }

    @AfterEach
    fun tearDown() {
        if (Files.exists(tempDir)) {
            Files.walk(tempDir).use { stream ->
                stream.sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun readUtf8(path: Path): String = String(Files.readAllBytes(path), Charsets.UTF_8)
    private fun writeUtf8(path: Path, content: String) {
        Files.write(path, content.toByteArray(Charsets.UTF_8))
    }

    @Test
    fun `写入后读回字节数一致`() {
        val json = """{"schemaVersion":1,"data":{}}"""
        val target = tempDir.resolve("backup.json")

        val written = writer.writeToPath(target, json)

        assertEquals(json.toByteArray(Charsets.UTF_8).size.toLong(), written)
        assertTrue(Files.exists(target))
        assertEquals(json, readUtf8(target))
    }

    @Test
    fun `UTF-8 中文写入读回不乱码`() {
        val json = """{"plans":[{"title":"晨练","note":"5km 慢跑"}]}"""
        val target = tempDir.resolve("backup-cn.json")

        writer.writeToPath(target, json)
        val readBack = readUtf8(target)

        assertEquals(json, readBack)
        assertTrue(readBack.contains("晨练"))
        assertTrue(readBack.contains("慢跑"))
    }

    @Test
    fun `覆盖写 — 第二次写覆盖第一次`() {
        val target = tempDir.resolve("backup.json")
        writer.writeToPath(target, "first")
        writer.writeToPath(target, "second-longer")
        assertEquals("second-longer", readUtf8(target))
    }

    @Test
    fun `子目录不存在时自动创建`() {
        val nested = tempDir.resolve("a/b/c/backup.json")
        assertTrue(!Files.exists(nested.parent))
        writer.writeToPath(target = nested, json = "data")
        assertTrue(Files.exists(nested))
        assertEquals("data", readUtf8(nested))
    }

    @Test
    fun `父目录为 null（根目录写）不崩`() {
        val bareName = "test-${System.nanoTime()}.json"
        val target = Paths.get(bareName)
        try {
            writer.writeToPath(target, "bare")
            assertTrue(Files.exists(target))
            assertEquals("bare", readUtf8(target))
        } finally {
            Files.deleteIfExists(target)
        }
    }

    @Test
    fun `parent 是 file 不是 dir 时抛 IOException`() {
        // 在 tempDir 下建一个普通文件，然后试图在它下面建 nested 文件 → 应抛 IOException
        val blocker = tempDir.resolve("blocker")
        writeUtf8(blocker, "i am a file not a dir")
        val target = blocker.resolve("nested/backup.json")
        assertThrows<IOException> {
            writer.writeToPath(target, "should fail")
        }
    }
}
