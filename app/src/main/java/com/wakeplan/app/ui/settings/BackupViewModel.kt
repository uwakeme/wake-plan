package com.wakeplan.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakeplan.app.data.backup.BackupImporter
import com.wakeplan.app.data.backup.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.AEADBadTagException
import javax.inject.Inject

/**
 * 备份 ViewModel — Phase 4.4
 *
 * UI 状态机（exportState / importState 各跑各的）：
 * - Idle: 待机
 * - Running: 跑中（显示 spinner）
 * - Success: 成功（带摘要，可 Snackbar 提示）
 * - Error: 失败（带 message，可再试）
 *
 * passphrase 走 CharArray 调用者持有，import 用完清；ViewModel 不持久化
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
) : ViewModel() {

    sealed interface ExportState {
        data object Idle : ExportState
        data object Running : ExportState
        data class Success(val bytesWritten: Long) : ExportState
        data class Error(val message: String) : ExportState
    }

    sealed interface ImportState {
        data object Idle : ImportState
        data object Running : ImportState
        data class Success(val result: BackupImporter.ImportResult) : ImportState
        data class Error(val message: String) : ImportState
    }

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    /**
     * 导出到用户选的 Uri
     * @param uri SAF 给的目标 Uri
     * @param passphrase null = 明文；非空 = 加密（调用方负责用完清）
     */
    fun export(uri: Uri, passphrase: CharArray? = null) {
        if (_exportState.value is ExportState.Running) return  // 防止重复点
        _exportState.update { ExportState.Running }
        viewModelScope.launch {
            try {
                val bytes = backupManager.exportToUri(uri, passphrase)
                _exportState.update { ExportState.Success(bytes) }
            } catch (e: Exception) {
                _exportState.update { ExportState.Error(formatError(e)) }
            } finally {
                passphrase?.fill('\u0000')
            }
        }
    }

    /**
     * 从用户选的 Uri 导入
     */
    fun `import`(uri: Uri, passphrase: CharArray? = null) {
        if (_importState.value is ImportState.Running) return
        _importState.update { ImportState.Running }
        viewModelScope.launch {
            try {
                val result = backupManager.importFromUri(uri, passphrase)
                _importState.update { ImportState.Success(result) }
            } catch (e: AEADBadTagException) {
                _importState.update { ImportState.Error("密码错误或文件被篡改") }
            } catch (e: IllegalStateException) {
                _importState.update { ImportState.Error(e.message ?: "导入失败") }
            } catch (e: Exception) {
                _importState.update { ImportState.Error(formatError(e)) }
            } finally {
                passphrase?.fill('\u0000')
            }
        }
    }

    /** 错误信息复位到 Idle（UI 关闭 Snackbar / Dialog 时调） */
    fun consumeExportError() {
        if (_exportState.value is ExportState.Error) {
            _exportState.update { ExportState.Idle }
        }
    }

    fun consumeImportError() {
        if (_importState.value is ImportState.Error) {
            _importState.update { ImportState.Idle }
        }
    }

    fun consumeExportSuccess() {
        if (_exportState.value is ExportState.Success) {
            _exportState.update { ExportState.Idle }
        }
    }

    fun consumeImportSuccess() {
        if (_importState.value is ImportState.Success) {
            _importState.update { ImportState.Idle }
        }
    }

    private fun formatError(e: Exception): String = when {
        e.message?.contains("permission", ignoreCase = true) == true ->
            "权限不足：${e.message}"
        e.message?.contains("space", ignoreCase = true) == true ->
            "磁盘空间不足：${e.message}"
        e is java.io.IOException ->
            "文件读写失败：${e.message ?: "未知 IO 错误"}"
        else ->
            e.message ?: e::class.simpleName ?: "未知错误"
    }

    companion object {
        /** 最低密码长度 — 加密备份建议 8 字符以上 */
        const val MIN_PASSPHRASE_LENGTH = 8
    }
}
