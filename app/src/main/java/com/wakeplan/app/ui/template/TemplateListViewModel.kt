package com.wakeplan.app.ui.template

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
 * 模板库 ViewModel
 */
@HiltViewModel
class TemplateListViewModel @Inject constructor(
    templateRepository: TemplateRepository,
) : ViewModel() {

    val templates: StateFlow<List<PlanTemplate>> = templateRepository.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}
