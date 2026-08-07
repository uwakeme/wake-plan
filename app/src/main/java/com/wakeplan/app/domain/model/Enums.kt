package com.wakeplan.app.domain.model

import java.time.DayOfWeek

/**
 * 时间段重复规则 — 5.3 决策：全量 4 枚举
 * - ONCE: 仅一次
 * - WEEKDAYS: 周一到周五
 * - DAILY: 每天
 * - CUSTOM: 用户自选星期（customDays 必有值）
 */
enum class RepeatRule {
    ONCE,
    WEEKDAYS,
    DAILY,
    CUSTOM,
}

/**
 * 任务执行状态机
 * - PENDING: 待开始
 * - IN_PROGRESS: 进行中（同时只允许 1 个）
 * - COMPLETED: 已完成
 * - SKIPPED: 跳过
 */
enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    SKIPPED,
}

/** CUSTOM 重复规则快捷校验：customDays 不能为空 */
fun RepeatRule.validateCustomDays(customDays: Set<DayOfWeek>): String? = when (this) {
    RepeatRule.CUSTOM -> if (customDays.isEmpty()) "CUSTOM 规则必须至少选 1 天" else null
    else -> null
}
