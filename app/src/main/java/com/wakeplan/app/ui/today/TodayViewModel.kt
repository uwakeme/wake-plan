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
    private val completeTaskUseCase: com.wakeplan.app.domain.usecase.CompleteTaskUseCase,
    private val startingAlarmScheduler: StartingAlarmScheduler,
    private val scheduleRefresher: ScheduleRefresher,
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

    /**
     * 撤销队列 — v0.5 阶段五（此前是单槽：上一条未消费就被新 action 覆盖，无法撤销）。
     * 深度 [MAX_UNDO_DEPTH]，超出丢最旧；队首即"当前待撤销"，Snackbar 逐条出队。
     */
    private val _undoQueue = MutableStateFlow<List<UndoableAction>>(emptyList())
    val undoableAction: StateFlow<UndoableAction?> = _undoQueue
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

            // 守 TaskStatus.IN_PROGRESS 的不变量「同时只允许 1 个」（domain/model/Enums.kt）：
            // 已有别的任务在跑时拒绝启动——两个 IN_PROGRESS 会让 CascadeDelayCalculator
            // 同时跑两条链，下游被重复后推。排除自己，否则进行中的任务无法再次触发。
            val running = taskCompletionRepository
                .observeByDateRangeInclusive(c.scheduledDate.minusDays(1), c.scheduledDate)
                .first()
                .firstOrNull { it.status == TaskStatus.IN_PROGRESS && it.id != completionId }
            if (running != null) {
                val title = taskRepository.getById(running.taskId)?.title.orEmpty()
                _inProgressConflict.value = InProgressConflict(running.id, title)
                return@launch
            }

            val now = LocalDateTime.now(clock)
            taskCompletionRepository.upsert(c.copy(
                status = TaskStatus.IN_PROGRESS,
                actualStart = now,
            ))
            startingAlarmScheduler.cancelStartingAlarm(completionId)
            enqueueUndo(UndoableAction.Start(completionId, c))
        }
    }

    /** 启动被守门拒绝时的提示载荷——UI 层据此弹 Snackbar 引导用户先处理旧任务 */
    data class InProgressConflict(val runningCompletionId: String, val runningTitle: String)

    private val _inProgressConflict = MutableStateFlow<InProgressConflict?>(null)
    val inProgressConflict: StateFlow<InProgressConflict?> = _inProgressConflict.asStateFlow()

    /** Snackbar 消费后调用，清掉提示避免重复弹出 */
    fun consumeInProgressConflict() {
        _inProgressConflict.value = null
    }

    fun onCompleteTask(completionId: String) {
        viewModelScope.launch {
            // 完成语义（级联落库 + 闹钟重排 + 完成通知）抽到 CompleteTaskUseCase，
            // v0.6 专注页打卡与这里共用同一条链
            val result = completeTaskUseCase(completionId) ?: return@launch
            enqueueUndo(UndoableAction.Complete(completionId, result.previous))
        }
    }

    fun onSkipTask(completionId: String) {
        viewModelScope.launch {
            val c = taskCompletionRepository.getById(completionId) ?: return@launch
            if (c.status == TaskStatus.COMPLETED || c.status == TaskStatus.SKIPPED) return@launch
            taskCompletionRepository.upsert(c.copy(status = TaskStatus.SKIPPED))
            startingAlarmScheduler.cancelStartingAlarm(completionId)
            enqueueUndo(UndoableAction.Skip(completionId, c))
        }
    }

    fun undo() {
        val action = _undoQueue.value.firstOrNull() ?: return
        _undoQueue.value = _undoQueue.value.drop(1)
        viewModelScope.launch {
            taskCompletionRepository.upsert(action.previous)
            if (action is UndoableAction.Complete) {
                // 撤销完成：下游被推后的 PENDING 窗口重置回任务定义 + 重排闹钟
                taskRepository.resyncPlanCompletions(
                    action.previous.planId,
                    action.previous.scheduledDate,
                )
                scheduleRefresher.refresh(action.previous.scheduledDate)
            } else {
                // 撤销 Start / Skip：这两个动作都 cancelStartingAlarm 过，任务回到 PENDING 后
                // 同样需要重排，否则本次会话内该任务不再提醒（对称性：取消有，恢复也要有）
                scheduleRefresher.refresh(action.previous.scheduledDate)
            }
        }
    }

    fun consumeUndo() {
        // 弹出队首（未点撤销的直接跳过），下一条自动顶上
        _undoQueue.value = _undoQueue.value.drop(1)
    }

    private fun enqueueUndo(action: UndoableAction) {
        _undoQueue.value = (_undoQueue.value + action).takeLast(MAX_UNDO_DEPTH)
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

    /** 快速录入保存前的冲突预览——UI 实时提示用，不阻断保存 */
    data class QuickAddConflict(
        val planTitle: String,
        val taskTitle: String,
        val overlapMinutes: Int,
    )

    /**
     * 预演 (planId, start, end) 保存后当天会跟谁撞——复用卡片红条同一套
     * ConflictDetector + 链式延期后的 adjusted 时间，保证"预览的"就是"保存后标红的"。
     * 对端 completion 不在今日列表（如归档计划）时 taskTitle 为空，UI 退化按计划名提示。
     */
    fun previewQuickAddConflict(planId: String, startMinutes: Int, endMinutes: Int): QuickAddConflict? {
        if (endMinutes <= startMinutes) return null
        val state = uiState.value
        val synthetic = TaskCompletion(
            id = QUICK_ADD_PREVIEW_ID,
            taskId = "",
            planId = planId,
            scheduledDate = state.date,
            plannedStart = state.date.atStartOfDay().plusMinutes(startMinutes.toLong()),
            plannedEnd = state.date.atStartOfDay().plusMinutes(endMinutes.toLong()),
        )
        val top = conflictDetector(state.adjustedCompletions + synthetic, state.date)
            .filter { it.taskA.id == QUICK_ADD_PREVIEW_ID || it.taskB.id == QUICK_ADD_PREVIEW_ID }
            .maxByOrNull { it.overlapMinutes }
            ?: return null
        val other = if (top.taskA.id == QUICK_ADD_PREVIEW_ID) top.taskB else top.taskA
        val otherItem = state.todayTaskItems.firstOrNull { it.completion.id == other.id }
        return QuickAddConflict(
            planTitle = otherItem?.plan?.title.orEmpty(),
            taskTitle = otherItem?.task?.title.orEmpty(),
            overlapMinutes = top.overlapMinutes,
        )
    }

    companion object {
        /** 撤销队列深度：超出丢最旧 */
        const val MAX_UNDO_DEPTH = 5

        /** 冲突预演用的 synthetic completion id，只存在于内存，不落库 */
        private const val QUICK_ADD_PREVIEW_ID = "quick-add-preview"
    }
}
