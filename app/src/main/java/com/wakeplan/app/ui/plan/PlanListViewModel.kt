package com.wakeplan.app.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.repository.PlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 计划列表 ViewModel
 * - plans：StateFlow 暴露给 UI 自动重组
 * - isEmpty：派生自 plans，方便空态 UI
 */
@HiltViewModel
class PlanListViewModel @Inject constructor(
    planRepository: PlanRepository,
) : ViewModel() {

    val plans: StateFlow<List<Plan>> = planRepository.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}
