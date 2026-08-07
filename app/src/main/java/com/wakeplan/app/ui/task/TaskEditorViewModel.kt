package com.wakeplan.app.ui.task

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.repository.SegmentRepository
import com.wakeplan.app.domain.repository.TaskRepository
import com.wakeplan.app.ui.plan.PlanDetailViewModel
import com.wakeplan.app.ui.plan.SegmentEditorViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 任务编辑器 ViewModel
 * - planId / segmentId from nav arg
 * - taskId nullable（create / edit 模式）
 * - 表单状态：title / plannedDurationMinutes / autoExtend / isLocked / note
 * - 段超载检测：当前 segment 段时长 - 同段其他任务总时长 - 当前任务时长
 * - savedEvent：保存成功事件
 */
@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val segmentRepository: SegmentRepository,
) : ViewModel() {

    val planId: String = checkNotNull(savedStateHandle.get<String>(PlanDetailViewModel.ARG_PLAN_ID)) {
        "TaskEditorViewModel requires planId nav arg"
    }
    val segmentId: String = checkNotNull(savedStateHandle.get<String>(SegmentEditorViewModel.ARG_SEGMENT_ID)) {
        "TaskEditorViewModel requires segmentId nav arg"
    }
    val taskId: String? = savedStateHandle.get<String>(ARG_TASK_ID)

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _plannedDurationMinutes = MutableStateFlow(DEFAULT_DURATION_MIN)
    val plannedDurationMinutes: StateFlow<Int> = _plannedDurationMinutes.asStateFlow()

    private val _autoExtend = MutableStateFlow(true)
    val autoExtend: StateFlow<Boolean> = _autoExtend.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()

    /** 当前 segment 的时长（分钟） */
    val segmentDurationMinutes: StateFlow<Int> = segmentRepository.observeByPlan(planId)
        .map { segs -> segs.firstOrNull { it.id == segmentId }?.durationMinutes ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 同段内其他任务总时长（不含当前正在编辑的任务） */
    val othersTotalMinutes: StateFlow<Int> = taskRepository.observeBySegment(segmentId)
        .map { tasks -> tasks.filter { it.id != taskId }.sumOf { it.plannedDurationMinutes } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 段超载：正数 = 超过段时长 */
    val overloadMinutes: StateFlow<Int> = combine(
        segmentDurationMinutes, othersTotalMinutes, _plannedDurationMinutes,
    ) { seg, others, current ->
        (others + current) - seg
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val isValid: StateFlow<Boolean> = _title
        .map { it.isNotBlank() && it.length <= MAX_TITLE_LENGTH }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _savedEvent = Channel<Unit>(Channel.BUFFERED)
    val savedEvent: Flow<Unit> = _savedEvent.receiveAsFlow()

    init {
        if (taskId != null) {
            viewModelScope.launch {
                val t = taskRepository.getById(taskId)
                if (t != null) {
                    _title.value = t.title
                    _plannedDurationMinutes.value = t.plannedDurationMinutes
                    _autoExtend.value = t.autoExtend
                    _isLocked.value = t.isLocked
                    _note.value = t.note.orEmpty()
                }
            }
        }
    }

    fun setTitle(value: String) { _title.value = value }
    fun setPlannedDurationMinutes(value: Int) { _plannedDurationMinutes.value = value.coerceAtLeast(0) }
    fun setAutoExtend(value: Boolean) { _autoExtend.value = value }
    fun setIsLocked(value: Boolean) { _isLocked.value = value }
    fun setNote(value: String) { _note.value = value }

    fun save() {
        val titleTrim = _title.value.trim()
        if (titleTrim.isEmpty() || titleTrim.length > MAX_TITLE_LENGTH) return

        viewModelScope.launch {
            if (taskId == null) {
                taskRepository.create(
                    segmentId = segmentId,
                    title = titleTrim,
                    plannedDurationMinutes = _plannedDurationMinutes.value,
                    autoExtend = _autoExtend.value,
                    isLocked = _isLocked.value,
                    note = _note.value.trim().ifEmpty { null },
                )
            } else {
                val existing = taskRepository.getById(taskId) ?: return@launch
                taskRepository.update(
                    existing.copy(
                        title = titleTrim,
                        plannedDurationMinutes = _plannedDurationMinutes.value,
                        autoExtend = _autoExtend.value,
                        isLocked = _isLocked.value,
                        note = _note.value.trim().ifEmpty { null },
                    )
                )
            }
            _savedEvent.send(Unit)
        }
    }

    fun delete() {
        val id = taskId ?: return
        viewModelScope.launch {
            taskRepository.delete(id)
            _savedEvent.send(Unit)
        }
    }

    companion object {
        const val ARG_TASK_ID = "taskId"
        const val MAX_TITLE_LENGTH = 30
        private const val DEFAULT_DURATION_MIN = 30
    }
}
