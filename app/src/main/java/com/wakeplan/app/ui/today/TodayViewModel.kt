package com.wakeplan.app.ui.today

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TimeSegment
import com.wakeplan.app.domain.repository.PlanRepository
import com.wakeplan.app.domain.repository.SegmentRepository
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import com.wakeplan.app.domain.repository.TaskRepository
import com.wakeplan.app.domain.usecase.AutoExtendCalculator
import com.wakeplan.app.domain.usecase.ConflictDetector
import com.wakeplan.app.domain.usecase.TaskCompletionInstantiator
import com.wakeplan.app.domain.usecase.TaskConflict
import com.wakeplan.app.domain.model.TaskStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * TodayViewModel — Phase 2.4a
 *
 * 数据流：
 * 1. observeAll 拉所有 plan / segment / task
 * 2. observeByDate(today) 拉当天所有 completion
 * 3. tickFlow 每 60s 刷新 now → AutoExtend 重算
 *
 * 副作用：
 * - ensureTodayCompletions() 按 RepeatRule 给缺 PENDING 的 task 补建 completion
 *   （懒生成 vs WorkManager 预生成 — Phase 2 选懒生成，Phase 4 备份导出时再评估）
 */
data class TodayPlanItem(
    val plan: Plan,
    val segments: List<TimeSegment>,
    val tasks: List<Task>,
    val completions: List<TaskCompletion>,
)

data class TodayUiState(
    val date: LocalDate,
    val now: LocalDateTime,
    val planItems: List<TodayPlanItem>,
    /** 跨所有 plan 调整后的 completion（已应用 AutoExtend） */
    val adjustedCompletions: List<TaskCompletion>,
    val conflicts: List<TaskConflict>,
) {
    companion object {
        val Empty = TodayUiState(
            date = LocalDate.now(),
            now = LocalDateTime.now(),
            planItems = emptyList(),
            adjustedCompletions = emptyList(),
            conflicts = emptyList(),
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    private val segmentRepository: SegmentRepository,
    private val taskRepository: TaskRepository,
    private val taskCompletionRepository: TaskCompletionRepository,
    private val taskCompletionInstantiator: TaskCompletionInstantiator,
    private val autoExtendCalculator: AutoExtendCalculator,
    private val conflictDetector: ConflictDetector,
    private val clock: Clock,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now(clock)

    /** 每 60s tick 一次，刷新 now；首次进入立即 emit */
    private val tickFlow: Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now(clock))
            delay(60_000L)
        }
    }

    val uiState: StateFlow<TodayUiState> = combine(
        planRepository.observeAll(),
        segmentRepository.observeAll(),
        taskRepository.observeAll(),
        taskCompletionRepository.observeByDate(today),
        tickFlow,
    ) { plans, segments, tasks, completions, now ->
        buildState(plans, segments, tasks, completions, now)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TodayUiState.Empty.copy(date = today, now = LocalDateTime.now(clock)),
        )

    private fun buildState(
        plans: List<Plan>,
        allSegments: List<TimeSegment>,
        allTasks: List<Task>,
        completions: List<TaskCompletion>,
        now: LocalDateTime,
    ): TodayUiState {
        val activePlans = plans.filter { it.isActive }
        val planItems = activePlans.map { plan ->
            val planSegments = allSegments.filter { it.planId == plan.id }
            val planTaskIds = allTasks
                .filter { t -> planSegments.any { it.id == t.segmentId } }
                .map { it.id }
                .toSet()
            TodayPlanItem(
                plan = plan,
                segments = planSegments,
                tasks = allTasks.filter { it.id in planTaskIds },
                completions = completions.filter { it.planId == plan.id },
            )
        }
        val adjusted = autoExtendCalculator(completions, allTasks, now)
        val conflicts = conflictDetector(adjusted, today)
        return TodayUiState(
            date = today,
            now = now,
            planItems = planItems,
            adjustedCompletions = adjusted,
            conflicts = conflicts,
        )
    }

    /**
     * 给当天缺 PENDING 的 task 补建 completion
     * upsert 后 Room Flow 重新 emit，下一轮 buildState 看到新数据
     */
    fun ensureTodayCompletions() {
        viewModelScope.launch {
            val plans = planRepository.observeAll().first()
            val segments = segmentRepository.observeAll().first()
            val tasks = taskRepository.observeAll().first()
            val existing = taskCompletionRepository.observeByDate(today).first()
            val existingTaskIds = existing.map { it.taskId }.toSet()
            Log.d("TodayViewModel", "ensure: plans=${plans.size} segments=${segments.size} tasks=${tasks.size} existing=${existing.size}")

            plans.filter { it.isActive }.forEach { plan ->
                val planSegments = segments.filter { it.planId == plan.id }
                val planTasks = tasks.filter { t -> planSegments.any { it.id == t.segmentId } }
                val pending = taskCompletionInstantiator(
                    plan = plan,
                    segments = planSegments,
                    tasks = planTasks,
                    date = today,
                ).filter { it.taskId !in existingTaskIds }
                Log.d("TodayViewModel", "ensure: plan=${plan.title} pending=${pending.size}")
                pending.forEach { taskCompletionRepository.upsert(it) }
            }
        }
    }

    // ---- 任务状态机交互（Phase 2.4b）----

    /** PENDING → IN_PROGRESS，写 actualStart = now */
    fun onStartTask(completionId: String) {
        viewModelScope.launch {
            val c = taskCompletionRepository.getById(completionId) ?: return@launch
            if (c.status != TaskStatus.PENDING) return@launch
            val now = LocalDateTime.now(clock)
            taskCompletionRepository.upsert(c.copy(
                status = TaskStatus.IN_PROGRESS,
                actualStart = now,
            ))
        }
    }

    /** IN_PROGRESS → COMPLETED，写 actualEnd = now + overrun */
    fun onCompleteTask(completionId: String) {
        viewModelScope.launch {
            val c = taskCompletionRepository.getById(completionId) ?: return@launch
            if (c.status != TaskStatus.IN_PROGRESS) return@launch
            val now = LocalDateTime.now(clock)
            val overrun = Duration.between(c.plannedEnd, now).toMinutes().coerceAtLeast(0L).toInt()
            taskCompletionRepository.upsert(c.copy(
                status = TaskStatus.COMPLETED,
                actualEnd = now,
                overrunMinutes = overrun,
            ))
        }
    }

    /** PENDING / IN_PROGRESS → SKIPPED（不写 actual 时间） */
    fun onSkipTask(completionId: String) {
        viewModelScope.launch {
            val c = taskCompletionRepository.getById(completionId) ?: return@launch
            if (c.status == TaskStatus.COMPLETED || c.status == TaskStatus.SKIPPED) return@launch
            taskCompletionRepository.upsert(c.copy(status = TaskStatus.SKIPPED))
        }
    }
}
