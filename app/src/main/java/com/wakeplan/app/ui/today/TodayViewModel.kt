package com.wakeplan.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakeplan.app.data.prefs.UserPreferences
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
import com.wakeplan.app.domain.usecase.ScheduleManager
import com.wakeplan.app.domain.usecase.TaskCompletionInstantiator
import com.wakeplan.app.domain.usecase.TaskConflict
import com.wakeplan.app.domain.model.TaskStatus
import com.wakeplan.app.notification.StartingAlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val scheduleManager: ScheduleManager,
    private val startingAlarmScheduler: StartingAlarmScheduler,
    private val userPreferences: UserPreferences,
    private val clock: Clock,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now(clock)

    /**
     * 可撤销动作 — Phase 5.1
     *
     * 用户做状态机操作后，ViewModel 把"操作前的快照"emit 出来，UI 弹 Snackbar
     * 用户点"撤销"时调 [undo] 把快照写回；不点则 5s 后 UI 自动 consume 掉
     * 只存最近一个动作（不做撤销栈，简化 v0.2）
     */
    sealed interface UndoableAction {
        val completionId: String
        val timestamp: Long
        val previous: TaskCompletion

        data class Start(override val completionId: String, override val previous: TaskCompletion) : UndoableAction {
            override val timestamp: Long = System.currentTimeMillis()
        }
        data class Complete(override val completionId: String, override val previous: TaskCompletion) : UndoableAction {
            override val timestamp: Long = System.currentTimeMillis()
        }
        data class Skip(override val completionId: String, override val previous: TaskCompletion) : UndoableAction {
            override val timestamp: Long = System.currentTimeMillis()
        }
    }

    private val _undoableAction = MutableStateFlow<UndoableAction?>(null)
    val undoableAction: StateFlow<UndoableAction?> = _undoableAction.asStateFlow()

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

            plans.filter { it.isActive }.forEach { plan ->
                val planSegments = segments.filter { it.planId == plan.id }
                val planTasks = tasks.filter { t -> planSegments.any { it.id == t.segmentId } }
                val pending = taskCompletionInstantiator(
                    plan = plan,
                    segments = planSegments,
                    tasks = planTasks,
                    date = today,
                ).filter { it.taskId !in existingTaskIds }
                pending.forEach { taskCompletionRepository.upsert(it) }
            }
            // 同步：重新拿一次当天的 completions（含新建的 PENDING） + 调度 starting alarms
            val todaysCompletions = taskCompletionRepository.observeByDate(today).first()
            val now = LocalDateTime.now(clock)
            val leadMinutes = userPreferences.taskStartingLeadMinutes.first()
            val startingEnabled = userPreferences.taskStartingEnabled.first()
            val plan = scheduleManager(
                completions = todaysCompletions,
                tasks = tasks,
                now = now,
                leadMinutes = leadMinutes,
                startingEnabled = startingEnabled,
            )
            startingAlarmScheduler.scheduleAll(plan)
        }
    }

    // ---- 任务状态机交互（Phase 2.4b）----

    /** PENDING → IN_PROGRESS，写 actualStart = now；取消 starting alarm */
    fun onStartTask(completionId: String) {
        viewModelScope.launch {
            val c = taskCompletionRepository.getById(completionId) ?: return@launch
            if (c.status != TaskStatus.PENDING) return@launch
            val now = LocalDateTime.now(clock)
            taskCompletionRepository.upsert(c.copy(
                status = TaskStatus.IN_PROGRESS,
                actualStart = now,
            ))
            startingAlarmScheduler.cancelStartingAlarm(completionId)
            _undoableAction.value = UndoableAction.Start(completionId, c)
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
            _undoableAction.value = UndoableAction.Complete(completionId, c)
        }
    }

    /** PENDING / IN_PROGRESS → SKIPPED（不写 actual 时间）；取消 starting alarm */
    fun onSkipTask(completionId: String) {
        viewModelScope.launch {
            val c = taskCompletionRepository.getById(completionId) ?: return@launch
            if (c.status == TaskStatus.COMPLETED || c.status == TaskStatus.SKIPPED) return@launch
            taskCompletionRepository.upsert(c.copy(status = TaskStatus.SKIPPED))
            startingAlarmScheduler.cancelStartingAlarm(completionId)
            _undoableAction.value = UndoableAction.Skip(completionId, c)
        }
    }

    // ===== Phase 5.1 撤销 =====

    /**
     * 撤销最近一个动作（如果有）
     * 把 [UndoableAction.previous] 快照写回 repository
     * 副作用：可能跟 next tick 的 AutoExtend 重算冲突（用户撤销"complete"后 next tick 又会重算）
     * 但这种情况用户预期是"撤销 = 回到之前的状态"，符合直觉
     */
    fun undo() {
        val action = _undoableAction.value ?: return
        _undoableAction.value = null
        viewModelScope.launch {
            // 直接 upsert 回去；如果当前 state 已经被人手动改了，previous 是旧值，写回就是用户要的
            taskCompletionRepository.upsert(action.previous)
            // 撤销 PENDING 的话不需要 re-schedule（用户撤回到 PENDING，auto-extend 下次 tick 会重算）
            // 撤销 IN_PROGRESS 也是同理
            // 撤销 COMPLETED → SKIPPED 同理
            // 简单起见不重设 alarm；用户下次进 app 触发 ensureTodayCompletions 会重排
        }
    }

    /**
     * 清除 UndoableAction（UI 在 5s 过期或用户点其他地方时调）
     */
    fun consumeUndo() {
        _undoableAction.value = null
    }
}
