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
import androidx.compose.ui.res.stringResource
import com.wakeplan.app.R




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

    // hoist strings used in onClick lambda
    val errEmpty = stringResource(R.string.backup_passphrase_empty)
    val errTooShort = stringResource(R.string.backup_passphrase_too_short_template, BackupViewModel.MIN_PASSPHRASE_LENGTH)
    val errMismatch = stringResource(R.string.backup_passphrase_mismatch)
    val confirmLabel = stringResource(R.string.common_confirm)
    val cancelLabel = stringResource(R.string.common_cancel)

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
                    label = { Text(stringResource(R.string.backup_passphrase_label)) },
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
                        label = { Text(stringResource(R.string.backup_passphrase_repeat)) },
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
                    passphrase.isEmpty() -> showError = errEmpty
                    passphrase.length < BackupViewModel.MIN_PASSPHRASE_LENGTH -> showError = errTooShort
                    requireConfirmation && passphrase != confirmation -> showError = errMismatch
                    else -> onConfirm(passphrase.toCharArray())
                }
            }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelLabel)
            }
        },
    )
}


@Composable
fun ImportConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cancelLabel = stringResource(R.string.common_cancel)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_import_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.backup_import_confirm_text),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.backup_import_confirm_subtext),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.backup_import_confirm_action), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelLabel)
            }
        },
    )
}
