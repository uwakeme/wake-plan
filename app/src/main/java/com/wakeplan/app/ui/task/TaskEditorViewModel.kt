package com.wakeplan.app.ui.task

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakeplan.app.domain.model.RepeatRule
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.util.UUID
import javax.inject.Inject

/**
 * 任务编辑器 ViewModel — v0.3 扁平化
 *
 * 字段（v0.3）：
 * - title          必填
 * - startMinutes   0..1440 (当天分钟数)
 * - endMinutes     0..2880（可 > 1440 表示跨午夜）
 * - repeatRule     ONCE / WEEKDAYS / DAILY / CUSTOM
 * - customDays     CUSTOM 时必填非空
 * - autoExtend     UI 正面表达，默认 true（= v0.3 域模型 isLocked = false，参与链式碰撞）
 * - note           可选
 *
 * 域模型里仍存 isLocked（"钉死"是底层语义）；UI 翻转成"自动延期"便于用户理解。
 * 转换：isLocked = !autoExtend
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

    private val _startMinutes = MutableStateFlow(DEFAULT_START_MINUTES)
    val startMinutes: StateFlow<Int> = _startMinutes.asStateFlow()

    private val _endMinutes = MutableStateFlow(DEFAULT_END_MINUTES)
    val endMinutes: StateFlow<Int> = _endMinutes.asStateFlow()

    private val _repeatRule = MutableStateFlow(RepeatRule.DAILY)
    val repeatRule: StateFlow<RepeatRule> = _repeatRule.asStateFlow()

    private val _customDays = MutableStateFlow<Set<DayOfWeek>>(emptySet())
    val customDays: StateFlow<Set<DayOfWeek>> = _customDays.asStateFlow()

    /**
     * UI 正面语义：自动延期。默认 ON（v0.3 推荐行为：任务超时顺延后续）。
     * 内部映射：isLocked = !autoExtend
     */
    private val _autoExtend = MutableStateFlow(true)
    val autoExtend: StateFlow<Boolean> = _autoExtend.asStateFlow()

    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()

    /**
     * isValid: title 非空 + 时长合法 + CUSTOM 时 customDays 非空
     * endMinutes > startMinutes 即可（endMinutes > 1440 自动理解为跨午夜）
     */
    val isValid: StateFlow<Boolean> = combine(
        _title, _startMinutes, _endMinutes, _repeatRule, _customDays,
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val title = args[0] as String
        val startMin = args[1] as Int
        val endMin = args[2] as Int
        val rule = args[3] as RepeatRule
        val days = args[4] as Set<DayOfWeek>
        title.isNotBlank() &&
            title.length <= MAX_TITLE_LENGTH &&
            endMin > startMin &&
            endMin <= MAX_END_MINUTES &&
            (rule != RepeatRule.CUSTOM || days.isNotEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _savedEvent = Channel<Unit>(Channel.BUFFERED)
    val savedEvent: Flow<Unit> = _savedEvent.receiveAsFlow()

    init {
        if (taskId != null) {
            viewModelScope.launch {
                val t = taskRepository.getById(taskId) ?: return@launch
                _title.value = t.title
                _startMinutes.value = t.startMinutes
                _endMinutes.value = t.endMinutes
                _repeatRule.value = t.repeatRule
                _customDays.value = t.customDays
                _autoExtend.value = !t.isLocked
                _note.value = t.note.orEmpty()
            }
        }
    }

    fun setTitle(value: String) { _title.value = value }
    fun setStartMinutes(value: Int) { _startMinutes.value = value.coerceIn(0, MAX_END_MINUTES) }
    fun setEndMinutes(value: Int) { _endMinutes.value = value.coerceIn(0, MAX_END_MINUTES) }
    fun setRepeatRule(value: RepeatRule) {
        _repeatRule.value = value
        if (value != RepeatRule.CUSTOM) _customDays.value = emptySet()
    }
    fun toggleCustomDay(day: DayOfWeek) {
        _customDays.value = _customDays.value.toMutableSet().apply {
            if (!add(day)) remove(day)
        }
    }
    fun setAutoExtend(value: Boolean) { _autoExtend.value = value }
    fun setNote(value: String) { _note.value = value }

    fun save() {
        val titleTrim = _title.value.trim()
        if (titleTrim.isEmpty() || titleTrim.length > MAX_TITLE_LENGTH) return
        if (_endMinutes.value <= _startMinutes.value) return
        if (_endMinutes.value > MAX_END_MINUTES) return
        if (_repeatRule.value == RepeatRule.CUSTOM && _customDays.value.isEmpty()) return

        val noteTrim = _note.value.trim().ifEmpty { null }
        val isLocked = !_autoExtend.value  // UI 翻转

        viewModelScope.launch {
            if (taskId == null) {
                taskRepository.create(
                    planId = planId,
                    title = titleTrim,
                    startMinutes = _startMinutes.value,
                    endMinutes = _endMinutes.value,
                    repeatRule = _repeatRule.value,
                    customDays = _customDays.value,
                    isLocked = isLocked,
                    note = noteTrim,
                )
            } else {
                val existing: Task = taskRepository.getById(taskId) ?: return@launch
                taskRepository.update(
                    existing.copy(
                        title = titleTrim,
                        startMinutes = _startMinutes.value,
                        endMinutes = _endMinutes.value,
                        repeatRule = _repeatRule.value,
                        customDays = if (_repeatRule.value == RepeatRule.CUSTOM) _customDays.value else emptySet(),
                        isLocked = isLocked,
                        note = noteTrim,
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

        /** 默认开始 9:00 = 540 分钟 */
        const val DEFAULT_START_MINUTES = 9 * 60

        /** 默认结束 10:00 = 600 分钟（默认 1 小时任务） */
        const val DEFAULT_END_MINUTES = 10 * 60

        /** 跨午夜上限：endMinutes 最大 48:00 = 2880 分钟（次日凌晨次日） */
        const val MAX_END_MINUTES = 48 * 60

        @Suppress("unused")
        private fun newId(): String = UUID.randomUUID().toString()
    }
}
