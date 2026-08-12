package com.wakeplan.app.ui.plan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.repository.PlanRepository
import com.wakeplan.app.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 计划详情 ViewModel — v0.3 扁平化
 *
 * v0.3 砍段后：
 * - 删 SegmentRepository 引用
 * - segments 状态字段删掉（commit 4 UI 重写时会整体调整）
 * - tasks 列表直接来自 TaskRepository（不再 groupBy segmentId）
 *
 * 业务方法暂时保留但只暴露 plan + tasks 状态。deletePlan / deleteTask 仍可用。
 */
@HiltViewModel
class PlanDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val planRepository: PlanRepository,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    val planId: String = checkNotNull(savedStateHandle.get<String>(ARG_PLAN_ID)) {
        "PlanDetailViewModel requires '$ARG_PLAN_ID' nav arg"
    }

    val plan: StateFlow<Plan?> = planRepository.observeById(planId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tasks: StateFlow<List<Task>> = taskRepository.observeByPlan(planId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deletePlan(onDone: () -> Unit) {
        viewModelScope.launch {
            planRepository.delete(planId)
            onDone()
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch { taskRepository.delete(taskId) }
    }

    companion object {
        const val ARG_PLAN_ID = "planId"
    }
}
