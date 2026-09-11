package com.wakeplan.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.repository.PlanRepository
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import com.wakeplan.app.domain.repository.TaskRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Widget 数据驱动刷新 — v0.4
 *
 * 之前 widget 只在系统 30min 周期 / 打开 App 时刷，打卡、延期后桌面组件滞后。
 * 现在 app 进程存活期间 collect 当日数据流（completion / plan / task 任一变化），
 * 防抖 300ms 合并连续写入（铺实例、级联延期都是多条 upsert），然后重建 RemoteViews
 * 推给全部 widget 实例。
 *
 * 日期翻页依赖"collect 启动时取 today"：跨天由系统 30min 周期兜底，进程被杀重启后
 * 这里重新取当天。widget 未添加（ids 空）时只 collect 不推送，开销可忽略。
 */
@Singleton
class WidgetDataRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val planRepository: PlanRepository,
    private val taskRepository: TaskRepository,
    private val taskCompletionRepository: TaskCompletionRepository,
) {

    fun start(scope: CoroutineScope): Job = scope.launch(Dispatchers.IO) {
        widgetRefreshSignal(
            completions = taskCompletionRepository.observeByDate(java.time.LocalDate.now()),
            plans = planRepository.observeAll(),
            tasks = taskRepository.observeAll(),
        ).collect {
            pushUpdateToAllWidgets()
        }
    }

    /** 重建 RemoteViews 并推给全部 widget 实例 */
    private suspend fun pushUpdateToAllWidgets() {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
        if (ids == null || ids.isEmpty()) return
        val views = buildTodayWidgetViews(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}

/** 任一数据源有变化就发一个刷新信号；连续变化经 debounce 合并（虚拟时间可测） */
@OptIn(kotlinx.coroutines.FlowPreview::class)
internal fun widgetRefreshSignal(
    completions: Flow<List<TaskCompletion>>,
    plans: Flow<List<Plan>>,
    tasks: Flow<List<Task>>,
): Flow<Unit> = combine(completions, plans, tasks) { _, _, _ -> }
    .debounce(DEBOUNCE_MILLIS)

private const val DEBOUNCE_MILLIS = 300L
