package com.wakeplan.app.ui.settings

import com.wakeplan.app.data.backup.BackupImporter
import com.wakeplan.app.data.backup.BackupManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * BackupViewModel state machine 测试 — Phase 4.4
 *
 * Mock BackupManager 验证：
 * - export/import 启动时 Running
 * - 成功后 Success（带摘要）
 * - 失败后 Error（带 message）
 * - consumeXxx 复位回 Idle
 * - 跑中重复点不会重入
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("BackupViewModel")
class BackupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val backupManager: BackupManager = mockk()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = BackupViewModel(backupManager)

    @Test
    fun `初始 exportState 和 importState 都是 Idle`() = runTest(testDispatcher) {
        val vm = viewModel()
        assertEquals(BackupViewModel.ExportState.Idle, vm.exportState.value)
        assertEquals(BackupViewModel.ImportState.Idle, vm.importState.value)
    }

    @Test
    fun `export 成功 — state 流转 Idle → Running → Success`() = runTest(testDispatcher) {
        coEvery { backupManager.exportToUri(any(), any()) } returns 12345L
        val vm = viewModel()
        val fakeUri = mockk<android.net.Uri>(relaxed = true)

        vm.export(fakeUri)
        // 启动后 state 应该是 Running
        assertEquals(BackupViewModel.ExportState.Running, vm.exportState.value)

        advanceUntilIdle()
        val state = vm.exportState.value
        assertTrue(state is BackupViewModel.ExportState.Success, "expected Success but was $state")
        assertEquals(12345L, (state as BackupViewModel.ExportState.Success).bytesWritten)
    }

    @Test
    fun `export 失败 — state 流转 Idle → Running → Error 带 message`() = runTest(testDispatcher) {
        coEvery { backupManager.exportToUri(any(), any()) } throws IOException("磁盘满了")
        val vm = viewModel()
        val fakeUri = mockk<android.net.Uri>(relaxed = true)

        vm.export(fakeUri)
        advanceUntilIdle()

        val state = vm.exportState.value
        assertTrue(state is BackupViewModel.ExportState.Error, "expected Error but was $state")
        assertTrue((state as BackupViewModel.ExportState.Error).message.contains("磁盘"))
    }

    @Test
    fun `Running 期间重复 export 不重入`() = runTest(testDispatcher) {
        // 让 export 挂起不返回（等 export coroutine 跑起来后再 cancel）
        coEvery { backupManager.exportToUri(any(), any()) } coAnswers { kotlinx.coroutines.awaitCancellation() }
        val vm = viewModel()
        val uri = mockk<android.net.Uri>(relaxed = true)

        vm.export(uri)
        // 让第一个 export coroutine 真的跑起来
        advanceUntilIdle()
        assertEquals(BackupViewModel.ExportState.Running, vm.exportState.value)
        // 此时 manager 已被调 1 次（且在挂起）
        coVerify(exactly = 1) { backupManager.exportToUri(any(), any()) }

        // 第二次 export 不应启动
        vm.export(uri)
        advanceUntilIdle()
        // 还是 1 次
        coVerify(exactly = 1) { backupManager.exportToUri(any(), any()) }
    }

    @Test
    fun `import 成功 — state 流转 Idle → Running → Success 带 counts`() = runTest(testDispatcher) {
        val result = BackupImporter.ImportResult(
            plansImported = 3,
            segmentsImported = 5,
            tasksImported = 10,
            completionsImported = 50,
            templatesImported = 2,
            schemaVersion = 1,
        )
        coEvery { backupManager.importFromUri(any(), any()) } returns result
        val vm = viewModel()
        val fakeUri = mockk<android.net.Uri>(relaxed = true)

        vm.import(fakeUri)
        advanceUntilIdle()

        val state = vm.importState.value
        assertTrue(state is BackupViewModel.ImportState.Success, "expected Success but was $state")
        val success = state as BackupViewModel.ImportState.Success
        assertEquals(3, success.result.plansImported)
        assertEquals(10, success.result.tasksImported)
    }

    @Test
    fun `import 错 passphrase — state 走 Error '密码错误或文件被篡改'`() = runTest(testDispatcher) {
        coEvery { backupManager.importFromUri(any(), any()) } throws javax.crypto.AEADBadTagException("tag mismatch")
        val vm = viewModel()
        val fakeUri = mockk<android.net.Uri>(relaxed = true)

        vm.import(fakeUri)
        advanceUntilIdle()

        val state = vm.importState.value
        assertTrue(state is BackupViewModel.ImportState.Error, "expected Error but was $state")
        assertEquals("密码错误或文件被篡改", (state as BackupViewModel.ImportState.Error).message)
    }

    @Test
    fun `import 缺 passphrase — state 走 Error 带 requires passphrase 标识`() = runTest(testDispatcher) {
        coEvery { backupManager.importFromUri(any(), any()) } throws IllegalStateException("encrypted file requires passphrase")
        val vm = viewModel()
        val fakeUri = mockk<android.net.Uri>(relaxed = true)

        vm.import(fakeUri)
        advanceUntilIdle()

        val state = vm.importState.value
        assertTrue(state is BackupViewModel.ImportState.Error, "expected Error but was $state")
        val err = state as BackupViewModel.ImportState.Error
        assertTrue(err.message.contains("requires passphrase"), "expected 'requires passphrase' in: ${err.message}")
    }

    @Test
    fun `consumeExportError 复位 Error 到 Idle`() = runTest(testDispatcher) {
        coEvery { backupManager.exportToUri(any(), any()) } throws RuntimeException("boom")
        val vm = viewModel()
        vm.export(mockk(relaxed = true))
        advanceUntilIdle()
        assertTrue(vm.exportState.value is BackupViewModel.ExportState.Error)

        vm.consumeExportError()
        assertEquals(BackupViewModel.ExportState.Idle, vm.exportState.value)
    }

    @Test
    fun `consumeImportError 复位 Error 到 Idle`() = runTest(testDispatcher) {
        coEvery { backupManager.importFromUri(any(), any()) } throws javax.crypto.AEADBadTagException("x")
        val vm = viewModel()
        vm.import(mockk(relaxed = true))
        advanceUntilIdle()
        assertTrue(vm.importState.value is BackupViewModel.ImportState.Error)

        vm.consumeImportError()
        assertEquals(BackupViewModel.ImportState.Idle, vm.importState.value)
    }

    @Test
    fun `consumeExportSuccess 复位 Success 到 Idle`() = runTest(testDispatcher) {
        coEvery { backupManager.exportToUri(any(), any()) } returns 100L
        val vm = viewModel()
        vm.export(mockk(relaxed = true))
        advanceUntilIdle()
        assertTrue(vm.exportState.value is BackupViewModel.ExportState.Success)

        vm.consumeExportSuccess()
        assertEquals(BackupViewModel.ExportState.Idle, vm.exportState.value)
    }

    @Test
    fun `consumeImportSuccess 复位 Success 到 Idle`() = runTest(testDispatcher) {
        coEvery { backupManager.importFromUri(any(), any()) } returns BackupImporter.ImportResult(0, 0, 0, 0, 0, 1)
        val vm = viewModel()
        vm.import(mockk(relaxed = true))
        advanceUntilIdle()
        assertTrue(vm.importState.value is BackupViewModel.ImportState.Success)

        vm.consumeImportSuccess()
        assertEquals(BackupViewModel.ImportState.Idle, vm.importState.value)
    }

    @Test
    fun `consumeXxx 在 Idle 或 Running 状态不动作`() = runTest(testDispatcher) {
        val vm = viewModel()
        // Idle
        vm.consumeExportError()
        vm.consumeImportError()
        vm.consumeExportSuccess()
        vm.consumeImportSuccess()
        assertEquals(BackupViewModel.ExportState.Idle, vm.exportState.value)
        assertEquals(BackupViewModel.ImportState.Idle, vm.importState.value)
    }

    @Test
    fun `export 抛一般 RuntimeException — message 走格式化的 error mapper`() = runTest(testDispatcher) {
        coEvery { backupManager.exportToUri(any(), any()) } throws RuntimeException("random failure")
        val vm = viewModel()
        vm.export(mockk(relaxed = true))
        advanceUntilIdle()
        val state = vm.exportState.value
        assertTrue(state is BackupViewModel.ExportState.Error)
        assertEquals("random failure", (state as BackupViewModel.ExportState.Error).message)
    }

    @Test
    fun `MIN_PASSPHRASE_LENGTH = 8 常量`() {
        assertEquals(8, BackupViewModel.MIN_PASSPHRASE_LENGTH)
    }
}
