package com.wakeplan.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakeplan.app.R
import com.wakeplan.app.data.backup.BackupImporter
import com.wakeplan.app.data.backup.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.AEADBadTagException
import javax.inject.Inject


@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
    @ApplicationContext private val context: Context,
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
        /** 加密备份但没传密码——UI 弹密码框后带 passphrase 重试（不再靠 message 字符串匹配） */
        data object NeedsPassphrase : ImportState
    }

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    
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


    fun `import`(uri: Uri, passphrase: CharArray? = null) {
        if (_importState.value is ImportState.Running) return
        _importState.update { ImportState.Running }
        viewModelScope.launch {
            try {
                val result = backupManager.importFromUri(uri, passphrase)
                _importState.update { ImportState.Success(result) }
            } catch (e: com.wakeplan.app.data.backup.BackupPassphraseRequiredException) {
                _importState.update { ImportState.NeedsPassphrase }
            } catch (e: AEADBadTagException) {
                _importState.update { ImportState.Error(context.getString(R.string.backup_err_parse_envelope_full)) }
            } catch (e: IllegalStateException) {
                _importState.update { ImportState.Error(e.message ?: context.getString(R.string.backup_err_decrypt_failed_full)) }
            } catch (e: Exception) {
                _importState.update { ImportState.Error(formatError(e)) }
            } finally {
                passphrase?.fill('\u0000')
            }
        }
    }

    
    fun consumeExportError() {
        if (_exportState.value is ExportState.Error) {
            _exportState.update { ExportState.Idle }
        }
    }

    fun consumeImportError() {
        if (_importState.value is ImportState.Error || _importState.value is ImportState.NeedsPassphrase) {
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
            context.getString(R.string.backup_err_permission_runtime, e.message)
        e.message?.contains("space", ignoreCase = true) == true ->
            context.getString(R.string.backup_err_no_space_runtime, e.message)
        e is java.io.IOException ->
            context.getString(R.string.backup_err_write_failed_runtime, e.message ?: context.getString(R.string.backup_err_unknown))
        else ->
            // 未知异常不把技术细节（英文 message / 异常类名）直接甩给用户
            context.getString(R.string.backup_error_generic)
    }

    companion object {
        
        const val MIN_PASSPHRASE_LENGTH = 8
    }
}
