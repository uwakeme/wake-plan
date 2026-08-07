package com.wakeplan.app.ui.plan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakeplan.app.data.repository.SegmentRepositoryImpl
import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.TimeSegment
import com.wakeplan.app.domain.repository.SegmentRepository
import com.wakeplan.app.domain.model.validateCustomDays
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

/**
 * 段编辑器 ViewModel
 * - 支持 create (segmentId == null) / edit (segmentId != null) 两种模式
 * - 表单状态：title / startMinutes / endMinutes / repeatRule / customDays
 * - 派生：overlappingSegments（与同 plan 内其他段重叠的段列表，内联提示用）
 * - 派生：isValid
 * - saveEvent：保存成功事件
 */
@HiltViewModel
class SegmentEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val segmentRepository: SegmentRepository,
    // 显式依赖 Impl（不是接口）以便访问 findOverlapping 扩展方法
    private val segmentRepositoryImpl: SegmentRepositoryImpl,
) : ViewModel() {

    val planId: String = checkNotNull(savedStateHandle.get<String>(PlanDetailViewModel.ARG_PLAN_ID)) {
        "SegmentEditorViewModel requires planId nav arg"
    }
    val segmentId: String? = savedStateHandle.get<String>(ARG_SEGMENT_ID)

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _startMinutes = MutableStateFlow(DEFAULT_START_MIN)
    val startMinutes: StateFlow<Int> = _startMinutes.asStateFlow()

    private val _endMinutes = MutableStateFlow(DEFAULT_END_MIN)
    val endMinutes: StateFlow<Int> = _endMinutes.asStateFlow()

    private val _repeatRule = MutableStateFlow(RepeatRule.WEEKDAYS)
    val repeatRule: StateFlow<RepeatRule> = _repeatRule.asStateFlow()

    private val _customDays = MutableStateFlow<Set<DayOfWeek>>(emptySet())
    val customDays: StateFlow<Set<DayOfWeek>> = _customDays.asStateFlow()

    /** 重叠段列表（按当前 startMinutes/endMinutes 实时查） */
    val overlappingSegments: StateFlow<List<TimeSegment>> = combine(
        _startMinutes, _endMinutes,
    ) { start, end ->
        segmentRepositoryImpl.findOverlapping(
            planId = planId,
            startMinutes = start,
            endMinutes = end,
            excludeId = segmentId.orEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val isValid: StateFlow<Boolean> = combine(
        _title, _startMinutes, _endMinutes, _repeatRule, _customDays,
    ) { args ->
        val title = args[0] as String
        val start = args[1] as Int
        val end = args[2] as Int
        val rule = args[3] as RepeatRule
        val days = args[4] as @Suppress("UNCHECKED_CAST") Set<DayOfWeek>
        end > start && rule.validateCustomDays(days) == null
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    private val _loaded = MutableStateFlow(false)
    private val _savedEvent = Channel<Unit>(Channel.BUFFERED)
    val savedEvent: kotlinx.coroutines.flow.Flow<Unit> = _savedEvent.receiveAsFlow()

    init {
        if (segmentId != null) {
            viewModelScope.launch {
                val seg = segmentRepository.getById(segmentId)
                if (seg != null) {
                    _title.value = seg.title.orEmpty()
                    _startMinutes.value = seg.startMinutes
                    _endMinutes.value = seg.endMinutes
                    _repeatRule.value = seg.repeatRule
                    _customDays.value = seg.customDays
                }
                _loaded.value = true
            }
        } else {
            _loaded.value = true
        }
    }

    fun setTitle(value: String) { _title.value = value }
    fun setStartMinutes(value: Int) { _startMinutes.value = value }
    fun setEndMinutes(value: Int) { _endMinutes.value = value }
    fun setRepeatRule(rule: RepeatRule) {
        _repeatRule.value = rule
        if (rule != RepeatRule.CUSTOM) _customDays.value = emptySet()
    }

    fun toggleCustomDay(day: DayOfWeek) {
        _customDays.value = _customDays.value.toMutableSet().apply {
            if (!add(day)) remove(day)
        }
    }

    fun save() {
        viewModelScope.launch {
            val current = _repeatRule.value
            val title = _title.value.trim().ifEmpty { null }
            if (segmentId == null) {
                segmentRepository.create(
                    planId = planId,
                    title = title,
                    startMinutes = _startMinutes.value,
                    endMinutes = _endMinutes.value,
                    repeatRule = current,
                    customDays = if (current == RepeatRule.CUSTOM) _customDays.value else emptySet(),
                )
            } else {
                val existing = segmentRepository.getById(segmentId) ?: return@launch
                segmentRepository.update(
                    existing.copy(
                        title = title,
                        startMinutes = _startMinutes.value,
                        endMinutes = _endMinutes.value,
                        repeatRule = current,
                        customDays = if (current == RepeatRule.CUSTOM) _customDays.value else emptySet(),
                    )
                )
            }
            _savedEvent.send(Unit)
        }
    }

    fun delete() {
        val id = segmentId ?: return
        viewModelScope.launch {
            segmentRepository.delete(id)
            _savedEvent.send(Unit)
        }
    }

    companion object {
        const val ARG_SEGMENT_ID = "segmentId"
        private const val DEFAULT_START_MIN = 9 * 60  // 09:00
        private const val DEFAULT_END_MIN = 12 * 60   // 12:00
    }
}
