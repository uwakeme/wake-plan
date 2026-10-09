package com.wakeplan.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.RepeatRule
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * TodayViewModel — v0.3 扁平化，v0.4.1 修跨天与延期落库
 *
 * v0.4.1 关键修正：
 * - `today` 不再是构造时快照：tickFlow 每分钟刷新日期，跨午夜后自动把
 *   "正在看的昨天"翻到新一天并补当天实例（长存 VM 之前会一直显示昨天）
 * - 数据源从 observeByDate 扩为 [昨天, 今天]：昨天 23:00–今天 00:30 的跨午夜
 *   尾巴参与冲突检测 / 列表展示（此前完全不可见）
 * - 完成任务时把链式延期结果落库 + 重排下游闹钟（此前只在内存里算，
 *   AC-06 的 overrun 从未传导到下游任务）
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
    /** 跨所有 plan 调整后的 completion（已应用链式延期） */
    val adjustedCompletions: List<TaskCompletion>,
    /** 扁平任务列表：按 completion.plannedStart 升序，跨 plan 混合排（含昨日跨午夜尾巴） */
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

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
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
    minuteTicker: com.wakeplan.app.domain.time.MinuteTicker,
    private val clock: Clock,
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now(clock))
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    val isViewingToday: StateFlow<Boolean> = _selectedDate
        .map { it == LocalDate.now(clock) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true,
        )

    sealed interface UndoableAction {
        val completionId: String
        val previous: TaskCompletion

        data class Start(override val completionId: String, override val previous: TaskCompletion) : UndoableAction
        data class Complete(override val completionId: String, override val previous: TaskCompletion) : UndoableAction
        data class Skip(override val completionId: String, override val previous: TaskCompletion) : UndoableAction
    }

    private val _undoableAction = MutableStateFlow<UndoableAction?>(null)
    val undoableAction: StateFlow<UndoableAction?> = _undoableAction.asStateFlow()

    /** 每分钟心跳（可注入，测试换有限流避免虚拟时间挂死） */
    private val tickFlow: Flow<LocalDateTime> = minuteTicker.now

    /** 跨午夜检测：跟上次已知日期比，翻到新一天时自动把视图带过去并补实例 */
    private var lastKnownToday: LocalDate = LocalDate.now(clock)

    init {
        viewModelScope.launch {
            tickFlow
                .map { it.toLocalDate() }
                .distinctUntilChanged()
                .collect { newToday ->
                    if (newToday != lastKnownToday) {
                        if (_selectedDate.value == lastKnownToday) {
                            _selectedDate.value = newToday
                        }
                        lastKnownToday = newToday
                        scheduleRefresher.refresh(newToday)
                    }
                }
        }
    }

    val uiState: StateFlow<TodayUiState> = _selectedDate
        .flatMapLatest { date ->
            // [昨天, 今天]：把昨天延伸到今天的跨午夜尾巴带进冲突检测与列表
            val completionsFlow = taskCompletionRepository.observeByDateRangeInclusive(
                date.minusDays(1), date,
            )
            combine(
                planRepository.observeAll(),
                taskRepository.observeAll(),
                completionsFlow,
                tickFlow,
            ) { plans, tasks, rangeCompletions, now ->
                val completions = rangeCompletions.filter {
                    it.scheduledDate == date || it.plannedEnd.isAfter(date.atStartOfDay())
                }
                buildState(plans, tasks, completions, now, date)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TodayUiState.Empty.copy(
                date = LocalDate.now(clock),
                now = LocalDateTime.now(clock),
            ),
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
     * 「补实例 + 重排闹钟」链路
     */
    fun ensureTodayCompletions() {
        val today = LocalDate.now(clock)
        if (_selectedDate.value != today) return
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
            // 链式延期落库：晚完成把同计划下游任务推后并持久化（AC-06），
            // 同时重排下游 starting 闹钟（AC-06.4）
            persistCascade(c.scheduledDate)
            // v0.4：task_completed 渠道实装（偏好默认关，开着才推）
            val taskTitle = taskRepository.getById(c.taskId)?.title.orEmpty()
            if (taskTitle.isNotEmpty()) {
                completionNotifier.notifyCompleted(completed, taskTitle)
            }
            _undoableAction.value = UndoableAction.Complete(completionId, c)
        }
    }

    /**
     * 把当天链式延期结果写回 DB（此前只算在内存里：界面一刷就弹回原时间，
     * 闹钟与统计也永远按原时间）。计算器幂等，重复调用安全。
     */
    private suspend fun persistCascade(date: LocalDate) {
        val completions = taskCompletionRepository.observeByDate(date).first()
        if (completions.isEmpty()) return
        val tasks = taskRepository.observeAll().first()
        val adjusted = cascadeDelayCalculator(completions, tasks, date, LocalDateTime.now(clock))
        val originalById = completions.associateBy { it.id }
        var changed = false
        adjusted.forEach { adj ->
            val orig = originalById[adj.id] ?: return@forEach
            if (orig.plannedStart != adj.plannedStart ||
                orig.plannedEnd != adj.plannedEnd ||
                orig.affectedTasks != adj.affectedTasks
            ) {
                taskCompletionRepository.upsert(adj)
                changed = true
            }
        }
        if (changed) scheduleRefresher.refresh(date)
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
            if (action is UndoableAction.Complete) {
                // 撤销完成：下游被推后的 PENDING 窗口重置回任务定义 + 重排闹钟
                taskRepository.resyncPlanCompletions(
                    action.previous.planId,
                    action.previous.scheduledDate,
                )
                scheduleRefresher.refresh(action.previous.scheduledDate)
            }
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
        switchDate(LocalDate.now(clock))
    }

    /**
     * 快速录入（v0.4）：Today FAB 直接建任务，保存后立刻铺当天实例 + 重排闹钟。
     * ONCE + createdEpochDay=今天 → 只今天生效，明天不会再冒出来（v0.4.1 修正）
     * 标题空或时间窗非法直接忽略（UI 已挡）。
     */
    fun quickAddTask(planId: String, title: String, startMinutes: Int, endMinutes: Int) {
        if (title.isBlank() || endMinutes <= startMinutes) return
        viewModelScope.launch {
            taskRepository.create(
                planId = planId,
                title = title.trim(),
                startMinutes = startMinutes,
                endMinutes = endMinutes,
                repeatRule = RepeatRule.ONCE,
            )
            scheduleRefresher.refresh(LocalDate.now(clock))
        }
    }
}
