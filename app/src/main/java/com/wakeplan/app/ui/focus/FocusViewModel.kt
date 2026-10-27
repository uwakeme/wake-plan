package com.wakeplan.app.ui.focus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakeplan.app.data.prefs.UserPreferences
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import com.wakeplan.app.domain.repository.TaskRepository
import com.wakeplan.app.focus.FocusConfig
import com.wakeplan.app.focus.FocusTimerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 专注页 ViewModel — v0.6
 *
 * 状态真源在 [FocusTimerController]（单例，跨屏存活）；VM 只负责
 * 加载目标任务 + 读专注配置 + 把用户操作转发给 controller。
 */
@HiltViewModel
class FocusViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskCompletionRepository: TaskCompletionRepository,
    private val taskRepository: TaskRepository,
    private val focusTimerController: FocusTimerController,
    private val preferences: UserPreferences,
) : ViewModel() {

    val completionId: String = checkNotNull(savedStateHandle.get<String>(ARG_COMPLETION_ID)) {
        "FocusViewModel requires '$ARG_COMPLETION_ID' nav arg"
    }

    /** controller 的全局专注状态（可能属于别的任务） */
    val focusState: StateFlow<com.wakeplan.app.focus.FocusState?> = focusTimerController.state

    data class Target(
        val completion: TaskCompletion,
        val task: Task,
    )

    private val _target = MutableStateFlow<Target?>(null)
    val target: StateFlow<Target?> = _target.asStateFlow()

    private val _config = MutableStateFlow(FocusConfig())
    val config: StateFlow<FocusConfig> = _config.asStateFlow()

    init {
        viewModelScope.launch {
            val completion = taskCompletionRepository.getById(completionId)
            val task = completion?.let { taskRepository.getById(it.taskId) }
            if (completion != null && task != null) {
                _target.value = Target(completion, task)
            }
            _config.value = readConfig()
        }
    }

    fun start() {
        val t = _target.value ?: return
        _startConflict.value = null
        viewModelScope.launch {
            if (focusTimerController.start(t.completion, t.task, readConfig())) return@launch
            // controller 守门拒绝：查出进行中的任务给用户提示（与 Today 点「开始」被拒同语义）
            val running = taskCompletionRepository
                .observeByDateRangeInclusive(t.completion.scheduledDate.minusDays(1), t.completion.scheduledDate)
                .first()
                .firstOrNull { it.status == TaskStatus.IN_PROGRESS && it.id != t.completion.id }
                ?: return@launch
            _startConflict.value = taskRepository.getById(running.taskId)?.title.orEmpty()
        }
    }

    /** start 被守门拒绝时置值：进行中任务标题（空串=未知），UI 据此提示 */
    private val _startConflict = MutableStateFlow<String?>(null)
    val startConflict: StateFlow<String?> = _startConflict.asStateFlow()

    fun advance() = focusTimerController.advance()

    fun skipPhase() = focusTimerController.skipPhase()

    fun completeTask() = focusTimerController.completeTask()

    fun abandon() = focusTimerController.abandon()

    private suspend fun readConfig(): FocusConfig = FocusConfig(
        focusMinutes = preferences.focusMinutes.first(),
        shortBreakMinutes = preferences.focusShortBreakMinutes.first(),
        longBreakMinutes = preferences.focusLongBreakMinutes.first(),
        autoStartNext = preferences.focusAutoStartNext.first(),
    )

    companion object {
        const val ARG_COMPLETION_ID = "completionId"
    }
}
