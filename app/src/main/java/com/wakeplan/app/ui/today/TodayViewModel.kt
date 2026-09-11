package com.wakeplan.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakeplan.app.data.prefs.UserPreferences
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.repository.PlanRepository
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import com.wakeplan.app.domain.repository.TaskRepository
import com.wakeplan.app.domain.usecase.CascadeDelayCalculator
import com.wakeplan.app.domain.usecase.ConflictDetector
import com.wakeplan.app.domain.usecase.TaskConflict
import com.wakeplan.app.domain.model.TaskStatus
import com.wakeplan.app.notification.CompletionNotifier
import com.wakeplan.app.notification.ScheduleRefresher
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * TodayViewModel — v0.3 扁平化
 *
 * v0.3 砍段后：
 * - 删 SegmentRepository 引用
 * - TodayPlanItem.segments 字段删掉
 * - ensureTodayCompletions 改成"按 plan 拿 tasks 展开，不再按段铺"
 * - 链式碰撞延期实装见 commit 3 (CascadeDelayCalculator)
 */
data class TodayPlanItem(
    val plan: Plan,
    val tasks: List<Task>,
    val completions: List<TaskCompletion>,
)

/**
 * 今日扁平任务项 — v0.4 视觉刷新后，Today 列表按时间排（不再按 plan 分组）
 *
 * 一个 (plan, task, completion) 三元组 = 列表里一行
 * plan 还在 row 上以"颜色 dot + 名称"形式展示
 */
data class TodayTaskItem(
    val plan: Plan,
    val task: Task,
    val completion: TaskCompletion,
)

data class TodayUiState(
    val date: LocalDate,
    val now: LocalDateTime,
    val planItems: List<TodayPlanItem>,
    /** 跨所有 plan 调整后的 completion（已应用 AutoExtend） */
    val adjustedCompletions: List<TaskCompletion>,
    /** 扁平任务列表：按 completion.plannedStart 升序，跨 plan 混合排 */
    val todayTaskItems: List<TodayTaskItem>,
    val conflicts: List<TaskConflict>,
) {
    companion object {
        val Empty = TodayUiState(
            date = LocalDate.now(),
            now = LocalDateTime.now(),
            planItems = emptyList(),
            adjustedCompletions = emptyList(),
            todayTaskItems = emptyList(),
            conflicts = emptyList(),
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    private val taskRepository: TaskRepository,
    private val taskCompletionRepository: TaskCompletionRepository,
    private val cascadeDelayCalculator: CascadeDelayCalculator,
    private val conflictDetector: ConflictDetector,
    private val startingAlarmScheduler: StartingAlarmScheduler,
    private val scheduleRefresher: ScheduleRefresher,
    private val completionNotifier: CompletionNotifier,
    private val userPreferences: UserPreferences,
    private val clock: Clock,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now(clock)

    private val _selectedDate = MutableStateFlow(today)
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    val isViewingToday: StateFlow<Boolean> = _selectedDate
        .map { it == today }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true,
        )

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

    private val tickFlow: Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now(clock))
            delay(60_000L)
        }
    }

    val uiState: StateFlow<TodayUiState> = _selectedDate
        .flatMapLatest { date ->
            val completionsFlow = taskCompletionRepository.observeByDate(date)
            combine(
                planRepository.observeAll(),
                taskRepository.observeAll(),
                completionsFlow,
                tickFlow,
            ) { plans, tasks, completions, now ->
                buildState(plans, tasks, completions, now, date)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TodayUiState.Empty.copy(date = today, now = LocalDateTime.now(clock)),
        )

    private fun buildState(
        plans: List<Plan>,
        allTasks: List<Task>,
        completions: List<TaskCompletion>,
        now: LocalDateTime,
        date: LocalDate,
    ): TodayUiState {
        // 归档计划不进 Today 聚合（v0.4）
        val activePlans = plans.filter { it.isActive && !it.archived }
        val plansById = activePlans.associateBy { it.id }
        val planItems = activePlans.map { plan ->
            val planTaskIds = allTasks.filter { it.planId == plan.id }.map { it.id }.toSet()
            TodayPlanItem(
                plan = plan,
                tasks = allTasks.filter { it.id in planTaskIds },
                completions = completions.filter { it.planId == plan.id },
            )
        }
        val adjusted = cascadeDelayCalculator(completions, allTasks, date, now)
        val conflicts = conflictDetector(adjusted, date)

        // 扁平任务列表：按 plannedStart 升序，跨 plan 混合
        val tasksById = allTasks.associateBy { it.id }

        
        val todayTaskItems = adjusted
            .mapNotNull { completion ->
                val plan = plansById[completion.planId] ?: return@mapNotNull null
                val task = tasksById[completion.taskId] ?: return@mapNotNull null
                TodayTaskItem(plan = plan, task = task, completion = completion)
            }
            .sortedBy { it.completion.plannedStart }

        return TodayUiState(
            date = date,
            now = now,
            planItems = planItems,
            adjustedCompletions = adjusted,
            todayTaskItems = todayTaskItems,
            conflicts = conflicts,
        )
    }

    /**
     * 给当天缺 PENDING 的 task 补建 completion
     * v0.4：实现下沉到 ScheduleRefresher — BootReceiver 重启后共用同一条
     * 「补实例 + 同步窗口 + 重排闹钟」链路
     */
    fun ensureTodayCompletions() {
        val targetDate = _selectedDate.value
        if (targetDate != today) return
        viewModelScope.launch {
            scheduleRefresher.refresh(today)
        }
    }

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

    fun onCompleteTask(completionId: String) {
        viewModelScope.launch {
            val c = taskCompletionRepository.getById(completionId) ?: return@launch
            if (c.status != TaskStatus.IN_PROGRESS) return@launch
            val now = LocalDateTime.now(clock)
            val overrun = Duration.between(c.plannedEnd, now).toMinutes().coerceAtLeast(0L).toInt()
            val completed = c.copy(
                status = TaskStatus.COMPLETED,
                actualEnd = now,
                overrunMinutes = overrun,
            )
            taskCompletionRepository.upsert(completed)
            // v0.4：task_completed 渠道实装（偏好默认关，开着才推）
            val taskTitle = taskRepository.getById(c.taskId)?.title.orEmpty()
            if (taskTitle.isNotEmpty()) {
                completionNotifier.notifyCompleted(completed, taskTitle)
            }
            _undoableAction.value = UndoableAction.Complete(completionId, c)
        }
    }

    fun onSkipTask(completionId: String) {
        viewModelScope.launch {
            val c = taskCompletionRepository.getById(completionId) ?: return@launch
            if (c.status == TaskStatus.COMPLETED || c.status == TaskStatus.SKIPPED) return@launch
            taskCompletionRepository.upsert(c.copy(status = TaskStatus.SKIPPED))
            startingAlarmScheduler.cancelStartingAlarm(completionId)
            _undoableAction.value = UndoableAction.Skip(completionId, c)
        }
    }

    fun undo() {
        val action = _undoableAction.value ?: return
        _undoableAction.value = null
        viewModelScope.launch {
            taskCompletionRepository.upsert(action.previous)
        }
    }

    fun consumeUndo() {
        _undoableAction.value = null
    }

    fun switchDate(date: LocalDate) {
        if (date == _selectedDate.value) return
        _selectedDate.value = date
    }

    fun goToToday() {
        switchDate(today)
    }
}
