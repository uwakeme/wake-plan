package com.wakeplan.app.domain.model

import java.time.DayOfWeek

/**
 * 任务 Domain Model — v0.3 扁平化
 *
 * v0.3 砍段后，任务自带起止时间 + 重复规则，直接挂在 plan 下。
 * 之前段上的 startMinutes / endMinutes / repeatRule / customDays 全部迁移到任务上。
 * autoExtend 字段砍掉（v0.3 所有任务都参与链式碰撞延期，"不参与"靠 isLocked=true 表达）。
 *
 * isLocked=true：钉死，链式延期到此停止（前面撞不进来，后面不会被推走）
 * isLocked=false（默认）：参与链式碰撞延期
 */
data class Task(
    val id: String,
    val planId: String,
    val title: String,

    /** 当天分钟数 0..1440；endMinutes > 1440 表示跨午夜（落到次日 0:00+） */
    val startMinutes: Int,
    val endMinutes: Int,

    val repeatRule: RepeatRule,
    val customDays: Set<DayOfWeek> = emptySet(),

    /** true = 钉死，链式延期到此停止 */
    val isLocked: Boolean = false,

    @get:JvmName("getOrderInPlan")
    val orderInPlan: Int,
    val note: String? = null,
) {
    val durationMinutes: Int get() = endMinutes - startMinutes
}
