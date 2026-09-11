package com.wakeplan.app.ui.plan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskStatus
import com.wakeplan.app.domain.repository.PlanRepository
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import com.wakeplan.app.domain.repository.TaskRepository
import com.wakeplan.app.domain.usecase.ConflictDetector
import com.wakeplan.app.domain.usecase.TaskConflict
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

data class PlanDetailStats(
    val todayCompleted: Int = 0,
    val weekCompleted: Int = 0,
    val totalCompleted: Int = 0,
)

@HiltViewModel
class PlanDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val planRepository: PlanRepository,
    private val taskRepository: TaskRepository,
    private val completionRepository: TaskCompletionRepository,
    private val conflictDetector: ConflictDetector,
) : ViewModel() {

    val planId: String = checkNotNull(savedStateHandle.get<String>(ARG_PLAN_ID)) {
        "PlanDetailViewModel requires '$ARG_PLAN_ID' nav arg"
    }

    val plan: StateFlow<Plan?> = planRepository.observeById(planId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tasks: StateFlow<List<Task>> = taskRepository.observeByPlan(planId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 今天本计划任务参与的时间冲突（taskId → 首个冲突）— v0.4 内联冲突标注
     * 跨 plan 检测（拿全天 completion 合并算），只保留涉及本计划的
     */
    val todayConflicts: StateFlow<Map<String, TaskConflict>> = combine(
        taskRepository.observeByPlan(planId),
        completionRepository.observeByDate(LocalDate.now()),
    ) { planTasks, todayCompletions ->
        if (planTasks.isEmpty()) return@combine emptyMap()
        val planTaskIds = planTasks.map { it.id }.toSet()
        val result = linkedMapOf<String, TaskConflict>()
        conflictDetector(todayCompletions, LocalDate.now()).forEach { c ->
            if (c.taskA.taskId in planTaskIds) result.putIfAbsent(c.taskA.taskId, c)
            if (c.taskB.taskId in planTaskIds) result.putIfAbsent(c.taskB.taskId, c)
        }
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _stats = MutableStateFlow(PlanDetailStats())
    val stats: StateFlow<PlanDetailStats> = _stats.asStateFlow()

    init {
        viewModelScope.launch {
            val today = LocalDate.now()
            val weekStart = today.with(DayOfWeek.MONDAY)

            completionRepository.observeByPlanAndDate(planId, today).collect { todayCompletions ->
                val currentPlan = plan.value
                val todayCompleted = todayCompletions.count { it.status == TaskStatus.COMPLETED }

                var weekCompleted = todayCompleted
                var totalCompleted = todayCompleted

                if (currentPlan != null) {
                    val weekCompletions = completionRepository.getByDateRange(weekStart, today.plusDays(1))
                    weekCompleted = weekCompletions.count { it.planId == planId && it.status == TaskStatus.COMPLETED }

                    val totalCompletions = completionRepository.getByDateRange(currentPlan.startDate, today.plusDays(1))
                    totalCompleted = totalCompletions.count { it.planId == planId && it.status == TaskStatus.COMPLETED }
                }

                _stats.value = PlanDetailStats(
                    todayCompleted = todayCompleted,
                    weekCompleted = weekCompleted,
                    totalCompleted = totalCompleted,
                )
            }
        }
    }

    fun deletePlan(onDone: () -> Unit) {
        viewModelScope.launch {
            planRepository.delete(planId)
            onDone()
        }
    }

    /**
     * 归档 / 取消归档（v0.4）
     * 归档成功后回调 onDone（调用方一般用来返回列表页）
     */
    fun setArchived(archived: Boolean, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val current = plan.value ?: planRepository.getById(planId) ?: return@launch
            planRepository.update(current.copy(archived = archived))
            if (archived) onDone()
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch { taskRepository.delete(taskId) }
    }

    companion object {
        const val ARG_PLAN_ID = "planId"
    }
}
