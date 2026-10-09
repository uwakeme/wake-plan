package com.wakeplan.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wakeplan.app.R

/**
 * 24 小时制时间选择弹窗 — v0.5 阶段一去重
 *
 * 之前 TaskEditorScreen 与 QuickAddTaskSheet 各持一份近似实现。
 * [stateKey] 供调用方区分 start/end 选择器；内部 key(initialMinutes, stateKey)
 * 让每次重新进入或切目标时强制重建 picker state（避免复用残留）。
 * 返回的 minutes 为当天 0..1439；跨午夜 +24h 语义由调用方处理。
 */
@Composable
fun WpTimePickerDialog(
    title: String,
    initialMinutes: Int,
    stateKey: Any?,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = key(initialMinutes, stateKey) {
        rememberTimePickerState(
            initialHour = (initialMinutes / 60) % 24,
            initialMinute = initialMinutes % 60,
            is24Hour = true,
        )
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                TimePicker(state = state)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                        Text(stringResource(R.string.common_confirm))
                    }
                }
            }
        }
    }
}
