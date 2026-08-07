package com.wakeplan.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions

/**
 * 备份对话框（Compose）— Phase 4.4
 *
 * 包含 3 个对话框：
 * - PassphraseDialog：让用户输入密码（明文 → 跳过；加密 → 必填 + 二次确认）
 * - ImportConfirmDialog：导入前的"会覆盖现有数据"提示
 * - 暂时不抽 ResultDialog，success / error 走 Snackbar
 */

/**
 * 密码输入对话框
 * @param title 标题，比如 "加密备份" 或 "导入加密备份"
 * @param message 副标题
 * @param onConfirm 确认回调，传入密码（用完 ViewModel 会清）；传 null 表示明文导出
 * @param onDismiss 取消回调
 * @param requireConfirmation 是否需要二次输入确认（默认 true 防止输错）
 */
@Composable
fun PassphraseDialog(
    title: String,
    message: String,
    onConfirm: (CharArray?) -> Unit,
    onDismiss: () -> Unit,
    requireConfirmation: Boolean = true,
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it; showError = null },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (requireConfirmation) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it; showError = null },
                        label = { Text("再输入一次") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (showError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        showError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    passphrase.isEmpty() -> showError = "密码不能为空"
                    passphrase.length < BackupViewModel.MIN_PASSPHRASE_LENGTH ->
                        showError = "密码至少 ${BackupViewModel.MIN_PASSPHRASE_LENGTH} 位"
                    requireConfirmation && passphrase != confirmation ->
                        showError = "两次输入不一致"
                    else -> onConfirm(passphrase.toCharArray())
                }
            }) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

/**
 * 导入确认对话框 — 强提示"会覆盖现有数据"
 */
@Composable
fun ImportConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认导入？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "导入备份会完全覆盖当前所有计划、段、任务和完成记录。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "此操作不可撤销。建议先导出一份当前数据的备份。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("覆盖导入", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
