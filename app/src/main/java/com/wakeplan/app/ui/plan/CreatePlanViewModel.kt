package com.wakeplan.app.ui.plan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakeplan.app.domain.repository.PlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * 新建/编辑计划 ViewModel
 * - 表单状态：title / colorHex / startDate
 * - 派生：isValid（标题 1-30 字）
 * - savedEvent：保存成功事件，UI 收到后 popBackStack
 * - 当 SavedStateHandle 含有 ARG_PLAN_ID 时进入编辑模式，加载现有 plan 并在 save() 时 update
 */
@HiltViewModel
class CreatePlanViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val editingPlanId: String? = savedStateHandle[ARG_PLAN_ID]
    val isEditMode: Boolean = editingPlanId != null

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _colorHex = MutableStateFlow(PlanColorPresets.DEFAULT)
    val colorHex: StateFlow<String> = _colorHex.asStateFlow()

    private val _startDate = MutableStateFlow(LocalDate.now())
    val startDate: StateFlow<LocalDate> = _startDate.asStateFlow()

    val isValid: StateFlow<Boolean> = _title
        .map { it.isNotBlank() && it.length <= MAX_TITLE_LENGTH }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    // 单次事件用 Channel，比 SharedFlow 防止重放更稳
    private val _savedEvent = Channel<Unit>(Channel.BUFFERED)
    val savedEvent = _savedEvent.receiveAsFlow()

    init {
        if (editingPlanId != null) {
            viewModelScope.launch {
                planRepository.getById(editingPlanId)?.let { existing ->
                    _title.value = existing.title
                    _colorHex.value = existing.colorHex
                    _startDate.value = existing.startDate
                }
            }
        }
    }

    fun setTitle(value: String) {
        _title.value = value.take(MAX_TITLE_LENGTH + 5) // 允许超长输入以便 isError 显示
    }

    fun setColorHex(hex: String) {
        if (PlanColorPresets.isValidHex(hex)) {
            _colorHex.value = hex.uppercase()
        }
    }

    fun setStartDate(date: LocalDate) {
        _startDate.value = date
    }

    fun save() {
        val currentTitle = _title.value.trim()
        if (currentTitle.isEmpty() || currentTitle.length > MAX_TITLE_LENGTH) return

        viewModelScope.launch {
            if (editingPlanId != null) {
                val existing = planRepository.getById(editingPlanId) ?: return@launch
                planRepository.update(
                    existing.copy(
                        title = currentTitle,
                        colorHex = _colorHex.value,
                        startDate = _startDate.value,
                    )
                )
            } else {
                planRepository.create(
                    title = currentTitle,
                    colorHex = _colorHex.value,
                    startDate = _startDate.value,
                )
            }
            _savedEvent.send(Unit)
        }
    }

    companion object {
        const val MAX_TITLE_LENGTH = 30
        const val ARG_PLAN_ID = "planId"
    }
}
