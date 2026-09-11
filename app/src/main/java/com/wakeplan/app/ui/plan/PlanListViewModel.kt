package com.wakeplan.app.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import com.wakeplan.app.domain.repository.PlanRepository
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import com.wakeplan.app.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * 计划列表 ViewModel
 *
 * 把 plan + task + completion 三路流合并成 `List<PlanCardData>`，
 * UI 层只关心这个聚合 view-model，不需要自己 join 三个仓库。
 *
 * v0.4 归档：plansWithStats 只出活跃计划，归档的走 [archivedPlans]；
 * [setArchived] 归档 / 取消归档。
 *
 * 视觉刷新 v0.4 之后，列表项用 [com.wakeplan.app.ui.common.PlanCard]，
 * 传入 [PlanCardData] 而不是裸 [Plan]。
 */
@HiltViewModel
class PlanListViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    private val taskRepository: TaskRepository,
    private val taskCompletionRepository: TaskCompletionRepository,
) : ViewModel() {

    /**
     * 计划 + 统计聚合（喂给 PlanCard）
     *
     * 三流合并：
     *   - plans：所有 plan
     *   - tasks：所有 task（按 planId 聚合用）
     *   - completions：今日所有 TaskCompletion
     */
    private val allWithStats: StateFlow<List<PlanCardData>> = combine(
        planRepository.observeAll(),
        taskRepository.observeAll(),
        taskCompletionRepository.observeByDate(LocalDate.now()),
    ) { plans: List<Plan>, tasks, completions: List<TaskCompletion> ->
        val tasksById = tasks.associateBy { it.id }
        plans.map { plan -> plan.toCardData(tasks, tasksById, completions) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /** 活跃计划（不含归档） */
    val plansWithStats: StateFlow<List<PlanCardData>> = allWithStats
        .map { list -> list.filter { !it.plan.archived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 已归档计划（v0.4） */
    val archivedPlans: StateFlow<List<PlanCardData>> = allWithStats
        .map { list -> list.filter { it.plan.archived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 归档 / 取消归档 */
    fun setArchived(planId: String, archived: Boolean) {
        viewModelScope.launch {
            val plan = planRepository.getById(planId) ?: return@launch
            planRepository.update(plan.copy(archived = archived))
        }
    }
}

private fun Plan.toCardData(
    allTasks: List<com.wakeplan.app.domain.model.Task>,
    tasksById: Map<String, com.wakeplan.app.domain.model.Task>,
    todayCompletions: List<TaskCompletion>,
): PlanCardData {
    val planTasks = allTasks.filter { it.planId == this.id }
    val myCompletions = todayCompletions.filter { it.planId == this.id }
    val todayCount = myCompletions.size
    val completedCount = myCompletions.count { it.status == TaskStatus.COMPLETED }
    val todayDurationMinutes = myCompletions.sumOf { completion ->
        tasksById[completion.taskId]?.let { it.endMinutes - it.startMinutes } ?: 0
    }
    val progressPercent = if (todayCount == 0) 0 else (completedCount * 100 / todayCount)
    val status = when {
        this.archived -> PlanStatus.IDLE
        !this.isActive -> PlanStatus.IDLE
        todayCount == 0 -> PlanStatus.IDLE
        completedCount < todayCount -> PlanStatus.ACTIVE
        else -> PlanStatus.SCHEDULED
    }
    return PlanCardData(
        plan = this,
        iconKey = PlanIconKey.derive(this.title),
        status = status,
        totalTaskCount = planTasks.size,
        todayTaskCount = todayCount,
        todayDurationMinutes = todayDurationMinutes,
        progressPercent = progressPercent,
    )
}
