package com.wakeplan.app.ui.plan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TimeSegment
import com.wakeplan.app.domain.repository.PlanRepository
import com.wakeplan.app.domain.repository.SegmentRepository
import com.wakeplan.app.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 计划详情 ViewModel
 * - plan: 单个计划
 * - segments: 计划下所有段（按 startMinutes 升序）
 * - tasks: 计划下所有任务（join segments）
 * - tasksBySegment: 段 id -> 任务列表
 */
@HiltViewModel
class PlanDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val planRepository: PlanRepository,
    private val segmentRepository: SegmentRepository,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    val planId: String = checkNotNull(savedStateHandle.get<String>(ARG_PLAN_ID)) {
        "PlanDetailViewModel requires '$ARG_PLAN_ID' nav arg"
    }

    val plan: StateFlow<Plan?> = planRepository.observeById(planId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val segments: StateFlow<List<TimeSegment>> = segmentRepository.observeByPlan(planId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tasksBySegment: StateFlow<Map<String, List<Task>>> = taskRepository
        .observeByPlan(planId)
        .map { list -> list.groupBy { it.segmentId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun deletePlan(onDone: () -> Unit) {
        viewModelScope.launch {
            planRepository.delete(planId)
            onDone()
        }
    }

    fun deleteSegment(segmentId: String) {
        viewModelScope.launch { segmentRepository.delete(segmentId) }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch { taskRepository.delete(taskId) }
    }

    companion object {
        const val ARG_PLAN_ID = "planId"
    }
}
