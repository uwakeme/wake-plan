package com.wakeplan.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import com.wakeplan.app.R
import java.time.format.DateTimeFormatter


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlanScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CreatePlanViewModel = hiltViewModel(),
) {
    val title by viewModel.title.collectAsStateWithLifecycle()
    val colorHex by viewModel.colorHex.collectAsStateWithLifecycle()
    val startDate by viewModel.startDate.collectAsStateWithLifecycle()
    val isValid by viewModel.isValid.collectAsStateWithLifecycle()

    val startDateText = remember(startDate) {
        startDate.format(DateTimeFormatter.ofPattern("M月d日"))
    }

    var showColorPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.savedEvent.collect { onSaved() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plan_create_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TitleField(
                value = title,
                onChange = viewModel::setTitle,
            )

            SectionLabel(stringResource(R.string.plan_create_color))
            ColorPresetGrid(
                selected = colorHex,
                onSelect = viewModel::setColorHex,
                onCustomClick = { showColorPicker = true },
            )

            SectionLabel(stringResource(R.string.plan_create_start_date))
            Text(
                text = stringResource(R.string.plan_create_start_date_default_runtime, startDateText),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Phase 1.3 升级为 DatePicker dialog

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::save,
                enabled = isValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.task_save))
            }
        }
    }

    if (showColorPicker) {
        // 当 colorHex 在 sheet 关闭前被其他方式改（比如用户从 sheet 里"确定"），
        // key 强制重置 sheet 内部 HSV 状态到最新 hex
        key(colorHex) {
            ColorPickerSheet(
                initialHex = colorHex,
                onDismiss = { showColorPicker = false },
                onConfirm = { newHex ->
                    viewModel.setColorHex(newHex)
                    showColorPicker = false
                },
            )
        }
    }
}

@Composable
private fun TitleField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.plan_create_title_label)) },
        placeholder = { Text(stringResource(R.string.plan_create_title_hint)) },
        singleLine = true,
        isError = value.isNotEmpty() && value.length > CreatePlanViewModel.MAX_TITLE_LENGTH,
        supportingText = {
            when {
                value.isEmpty() -> Text(stringResource(R.string.plan_create_title_length_runtime, CreatePlanViewModel.MAX_TITLE_LENGTH))
                value.length > CreatePlanViewModel.MAX_TITLE_LENGTH ->
                    Text(stringResource(R.string.plan_create_title_too_long_runtime, CreatePlanViewModel.MAX_TITLE_LENGTH))
                else -> Text("${value.length} / ${CreatePlanViewModel.MAX_TITLE_LENGTH}")
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * 颜色预设网格：5 列 × N 行
 *  - 前 N 格：来自 PlanColorPresets.PRESETS
 *  - 末尾 1 格：
 *      - 未选自定义色时：描边圆 + "+" 图标（打开 ColorPickerSheet）
 *      - 已选自定义色时：变成该自定义色的预览（与预设色块同款描边 + 勾）
 */
@Composable
private fun ColorPresetGrid(
    selected: String,
    onSelect: (String) -> Unit,
    onCustomClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 当前选中是否落在预设里？不在就当作"自定义色"，让 "+" 格子显示该色预览
    val customPreview: Color? = remember(selected) {
        val inPresets = PlanColorPresets.PRESETS.any { it.equals(selected, ignoreCase = true) }
        if (inPresets) {
            null
        } else {
            runCatching { Color(android.graphics.Color.parseColor(selected)) }.getOrNull()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(PlanColorPresets.PRESETS) { hex ->
            ColorSwatch(
                hex = hex,
                selected = hex.equals(selected, ignoreCase = true),
                onClick = { onSelect(hex) },
            )
        }
        item {
            ColorCustomEntry(
                onClick = onCustomClick,
                previewColor = customPreview,
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    hex: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val swatchColor = Color(android.graphics.Color.parseColor(hex))
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(swatchColor)
            .then(
                if (selected) Modifier.border(
                    width = 3.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape,
                ) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = stringResource(R.string.plan_create_color_selected),
                tint = Color.White,
            )
        }
    }
}

/**
 * "+" 入口圆：打开 HSV 颜色盘
 * - previewColor = null：未选自定义色 → 描边圆 + "+" 图标
 * - previewColor = Color：已选自定义色 → 复用色块样式（纯色 + 3dp 描边 + 白色勾）
 *   再点一次会重新打开颜色盘
 */
@Composable
private fun ColorCustomEntry(
    onClick: () -> Unit,
    previewColor: Color?,
    modifier: Modifier = Modifier,
) {
    if (previewColor == null) {
        Box(
            modifier = modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = stringResource(R.string.plan_create_custom_color_entry),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(previewColor)
                .border(
                    width = 3.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = stringResource(R.string.plan_create_color_selected),
                tint = Color.White,
            )
        }
    }
}
