package com.wakeplan.app.domain.model

import java.time.Instant

/**
 * 专注轮记录 — v0.6 番茄钟（绑任务的专注页）
 *
 * 一条 = 一次完整的专注计时（专注阶段从开始到走完或放弃）。
 * 休息阶段不产生记录。专注时长进统计（按天 / 按计划）。
 *
 * 注意：completionId **故意不建外键**——task_completions 走 REPLACE 更新
 * （删旧插新），FK CASCADE 会把关联的专注记录一起删掉。
 */
data class FocusSession(
    val id: String,
    val completionId: String,
    val planId: String,
    val startedAt: Instant,
    val endedAt: Instant,
    /** 配置的专注时长（分钟），统计"计划 vs 实际"用 */
    val plannedMinutes: Int,
    /** 实际专注时长（分钟），放弃也计入 */
    val actualMinutes: Int,
    val outcome: FocusOutcome,
)
