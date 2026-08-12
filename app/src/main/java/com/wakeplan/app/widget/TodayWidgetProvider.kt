package com.wakeplan.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.wakeplan.app.MainActivity
import com.wakeplan.app.R
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.wakeplan.app.data.db.WakePlanDatabase
import com.wakeplan.app.domain.repository.PlanRepository
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import com.wakeplan.app.domain.repository.TaskRepository
import com.wakeplan.app.domain.model.TaskStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 今日 mini timeline 桌面小组件 — Phase 5.6
 *
 * 实现：
 * - 4x2 widget（[widget_today.xml]）
 * - 启动时拉今天 plan + task + completion
 * - 显示标题（今日 + 日期）+ 24h progress bar + 任务数 + 下一个 PENDING task
 * - 点击 → PendingIntent 启动 MainActivity + EXTRA_NAV_TO_TODAY → 跳 Today tab
 * - update 周期 30min（系统最小）；onEnabled/onDisabled 创建/销毁 Hilt scope
 *
 * 设计 v0.2 简化：v0.3 可考虑加 Glance Compose 或更精细的 mini timeline
 */
class TodayWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun planRepository(): PlanRepository
        fun taskRepository(): TaskRepository
        fun taskCompletionRepository(): TaskCompletionRepository
        fun database(): WakePlanDatabase
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { widgetId ->
            scope.launch {
                val views = buildRemoteViews(context)
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }

    private suspend fun buildRemoteViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today)

        // 启动 MainActivity + 跳 Today tab
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_NAV_TO_TODAY, true)
        }
        val pending = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_title, pending)
        views.setOnClickPendingIntent(R.id.widget_next_task, pending)

        // 拉数据
        val entry = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val today = LocalDate.now()
        val completions = entry.taskCompletionRepository().observeByDate(today).first()
        val plans = entry.planRepository().observeAll().first().filter { it.isActive }
        val taskCount = completions.count { it.status != TaskStatus.SKIPPED }
        val now = LocalDateTime.now()
        val progressPct = ((now.hour * 60 + now.minute) * 100) / (24 * 60)

        // 标题：今日 + 日期
        val dateStr = today.format(DATE_FMT)
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title) + " " + dateStr)

        // 进度
        views.setProgressBar(R.id.widget_progress, 100, progressPct, false)
        views.setTextViewText(
            R.id.widget_progress_label,
            context.getString(R.string.widget_progress_label_pct, progressPct),
        )

        // 任务数
        views.setTextViewText(
            R.id.widget_task_count,
            if (taskCount == 0) context.getString(R.string.widget_task_count_zero)
            else context.getString(R.string.widget_task_count_n, taskCount),
        )

        // 下一个 PENDING 任务（按 plannedStart 升序）
        val nextTask = completions
            .filter { it.status == TaskStatus.PENDING || it.status == TaskStatus.IN_PROGRESS }
            .minByOrNull { it.plannedStart }
        if (nextTask != null) {
            val taskId = nextTask.taskId
            val taskTitle = entry.taskRepository().observeAll().first()
                .firstOrNull { it.id == taskId }?.title ?: ""
            val timeStr = nextTask.plannedStart.format(TIME_FMT)
            views.setTextViewText(
                R.id.widget_next_task,
                context.getString(R.string.widget_next_task_at, taskTitle, timeStr),
            )
        } else if (plans.isEmpty()) {
            views.setTextViewText(R.id.widget_next_task, context.getString(R.string.widget_no_plan))
        } else {
            views.setTextViewText(R.id.widget_next_task, context.getString(R.string.widget_next_task_empty))
        }
        return views
    }

    companion object {
        const val EXTRA_NAV_TO_TODAY = "com.wakeplan.app.NAV_TO_TODAY"
        private val DATE_FMT = DateTimeFormatter.ofPattern("M月d日")
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
    }
}
