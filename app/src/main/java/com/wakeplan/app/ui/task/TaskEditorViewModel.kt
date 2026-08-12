package com.wakeplan.app.ui.task

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.repository.TaskRepository
import com.wakeplan.app.ui.plan.PlanDetailViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 任务编辑器 ViewModel — v0.3 扁平化
 *
 * v0.3 砍段后：
 * - 删 SegmentRepository 引用
 * - 删 segmentId nav arg（任务直接挂 plan 下）
 * - 删 segmentDurationMinutes / othersTotalMinutes / overloadMinutes（commit 4 加 startTime/endTime 时重写）
 *
 * v0.3 commit 1 阶段：表单状态只保留 title 和 isLocked + note，commit 4 会加 startTime/endTime/repeatRule。
 */
@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    val planId: String = checkNotNull(savedStateHandle.get<String>(PlanDetailViewModel.ARG_PLAN_ID)) {
        "TaskEditorViewModel requires planId nav arg"
    }
    val taskId: String? = savedStateHandle.get<String>(ARG_TASK_ID)

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()

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
                    _isLocked.value = t.isLocked
                    _note.value = t.note.orEmpty()
                }
            }
        }
    }

    fun setTitle(value: String) { _title.value = value }
    fun setIsLocked(value: Boolean) { _isLocked.value = value }
    fun setNote(value: String) { _note.value = value }

    fun save() {
        val titleTrim = _title.value.trim()
        if (titleTrim.isEmpty() || titleTrim.length > MAX_TITLE_LENGTH) return

        viewModelScope.launch {
            if (taskId == null) {
                // commit 4: TaskEditor 加 startTime / endTime / repeatRule 字段后实装 create
                throw NotImplementedError("v0.3 commit 4 实装 TaskEditor.create")
            } else {
                val existing: Task = taskRepository.getById(taskId) ?: return@launch
                taskRepository.update(
                    existing.copy(
                        title = titleTrim,
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
    }
}
