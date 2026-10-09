package com.wakeplan.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.repository.PlanRepository
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import com.wakeplan.app.domain.usecase.StatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * 统计屏 ViewModel — Phase 5.2.1
 *
 * 一次性拉数据：planRepo.observeAll().first() + taskCompletionRepo.getByDateRange
 * Range 切换时重新拉 + 重算
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val planRepo: PlanRepository,
    private val taskRepo: com.wakeplan.app.domain.repository.TaskRepository,
    private val taskCompletionRepo: TaskCompletionRepository,
    private val statsUseCase: StatsUseCase,
    private val clock: Clock,
) : ViewModel() {

    data class StatsUiState(
        val range: StatsUseCase.Range = StatsUseCase.Range.LAST_7_DAYS,
        val result: StatsUseCase.StatsResult? = null,
        val isLoading: Boolean = true,
    )

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        recompute(_uiState.value.range)
    }

    fun selectRange(range: StatsUseCase.Range) {
        if (range == _uiState.value.range) return
        _uiState.update { it.copy(range = range, isLoading = true) }
        recompute(range)
    }

    private fun recompute(range: StatsUseCase.Range) {
        viewModelScope.launch {
            val today = LocalDate.now(clock)  // 每次重算取当前日期，不固化在构造时
            val start = today.minusDays((range.days - 1).toLong())
            val end = today.plusDays(1)  // 半开区间到明天 00:00（覆盖今天的全天）
            val completions: List<TaskCompletion> = taskCompletionRepo.getByDateRange(start, end)
            val plans: List<Plan> = planRepo.observeAll().first()
            val tasks = taskRepo.observeAll().first()
            val result = statsUseCase.compute(completions, plans, tasks, range, today)
            _uiState.update { it.copy(result = result, isLoading = false) }
        }
    }
}
