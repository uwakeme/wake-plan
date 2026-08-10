package com.wakeplan.app.ui.template

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakeplan.app.domain.model.PlanTemplate
import com.wakeplan.app.domain.repository.TemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 模板详情 ViewModel
 *
 * 5.4.1: 预览 segments + tasks（只读，apply 留 5.4.2）
 * 5.4.2: applyTemplate() 调 TemplateRepository.createPlanFromTemplate → emit 新 plan id → UI 跳详情
 */
@HiltViewModel
class TemplateDetailViewModel @Inject constructor(
    templateRepository: TemplateRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val templateId: String = savedStateHandle.get<String>(ARG_TEMPLATE_ID).orEmpty()

    val template: StateFlow<PlanTemplate?> = templateRepository.observeById(templateId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    companion object {
        const val ARG_TEMPLATE_ID = "templateId"
    }
}
