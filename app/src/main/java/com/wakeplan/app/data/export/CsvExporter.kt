package com.wakeplan.app.data.export

import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskCompletion
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 任务执行记录 CSV 导出 — v0.5 阶段四
 *
 * 纯函数（JVM 可单测）：completions + plans + tasks → CSV 字符串。
 * - UTF-8 带 BOM（Excel 双击打开中文不乱码）
 * - RFC 4180 转义：含逗号/引号/换行的字段用双引号包裹，内部引号翻倍
 * - 计划/任务名当期 join，已删除的回退为 id（CSV 是给用户做二次分析的，id 可追溯）
 * - 状态列用 enum 名（COMPLETED / SKIPPED / ...）：稳定、可筛选，不做本地化
 */
@Singleton
class CsvExporter @Inject constructor() {

    /** 表头由调用方注入（走 strings 资源跟随 App 语言） */
    fun export(
        completions: List<TaskCompletion>,
        plans: List<Plan>,
        tasks: List<Task>,
        headers: List<String>,
    ): String {
        require(headers.size == COLUMN_COUNT) { "headers must have $COLUMN_COUNT columns" }
        val plansById = plans.associateBy { it.id }
        val tasksById = tasks.associateBy { it.id }

        val sb = StringBuilder()
        sb.append(BOM)
        sb.append(headers.joinToString(",") { escape(it) })
        completions
            .sortedWith(compareBy({ it.scheduledDate }, { it.plannedStart }))
            .forEach { c ->
                sb.append('\n')
                sb.append(
                    listOf(
                        c.scheduledDate.toString(),
                        plansById[c.planId]?.title ?: c.planId,
                        tasksById[c.taskId]?.title ?: c.taskId,
                        c.plannedStart.format(TIME_FMT),
                        c.plannedEnd.format(TIME_FMT),
                        c.actualStart?.format(TIME_FMT) ?: "",
                        c.actualEnd?.format(TIME_FMT) ?: "",
                        c.status.name,
                        c.overrunMinutes.toString(),
                    ).joinToString(",") { escape(it) },
                )
            }
        return sb.toString()
    }

    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    companion object {
        private val TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        /** UTF-8 BOM（U+FEFF） */
        const val BOM = "\uFEFF"

        const val COLUMN_COUNT = 9
    }
}
