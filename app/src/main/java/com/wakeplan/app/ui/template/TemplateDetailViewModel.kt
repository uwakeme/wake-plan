package com.wakeplan.app.ui.template

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakeplan.app.domain.model.PlanTemplate
import com.wakeplan.app.domain.repository.TemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 模板详情 ViewModel
 *
 * 5.4.1: 预览 segments + tasks（只读）
 * 5.4.2: applyTemplate() 调 TemplateRepository.createPlanFromTemplate → emit 新 plan id → UI 跳详情
 */
@HiltViewModel
class TemplateDetailViewModel @Inject constructor(
    private val templateRepository: TemplateRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val templateId: String = savedStateHandle.get<String>(ARG_TEMPLATE_ID).orEmpty()

    val template: StateFlow<PlanTemplate?> = templateRepository.observeById(templateId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /**
     * 应用模板结果
     * - Idle: 没动过 / 重置后
     * - Running: 正在调 createPlanFromTemplate
     * - Success: 创建成功，newPlanId 可用
     * - Error: 创建失败，message 给 UI 展示
     */
    sealed interface ApplyState {
        data object Idle : ApplyState
        data object Running : ApplyState
        data class Success(val newPlanId: String) : ApplyState
        data class Error(val message: String) : ApplyState
    }

    private val _applyState = MutableStateFlow<ApplyState>(ApplyState.Idle)
    val applyState: StateFlow<ApplyState> = _applyState.asStateFlow()

    fun applyTemplate(customTitle: String? = null) {
        if (_applyState.value is ApplyState.Running) return  // 防止重复点
        _applyState.value = ApplyState.Running
        viewModelScope.launch {
            try {
                val plan = templateRepository.createPlanFromTemplate(
                    templateId = templateId,
                    customTitle = customTitle,
                )
                _applyState.value = ApplyState.Success(plan.id)
            } catch (t: Throwable) {
                _applyState.value = ApplyState.Error(t.message ?: "应用模板失败")
            }
        }
    }

    /** UI 在跳到新 plan 详情后调用，重置状态避免反复触发 */
    fun consumeApplyResult() {
        if (_applyState.value is ApplyState.Success || _applyState.value is ApplyState.Error) {
            _applyState.value = ApplyState.Idle
        }
    }

    companion object {
        const val ARG_TEMPLATE_ID = "templateId"
    }
}
