package com.wakeplan.app.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * 计划 Domain Model — 纯 Kotlin，不依赖 Android / Room
 * 数据层 entity 通过 mapper 转换到这里
 */
data class Plan(
    val id: String,
    val title: String,
    val colorHex: String,        // "#RRGGBB"，由 5.3 决策：10 预设 + 自定义 hex
    val startDate: LocalDate,    // 计划起始日期
    val isActive: Boolean,

    /** v0.4 归档：true = 已归档，不进 Today 聚合 / 不再铺 completion，但保留历史统计 */
    val archived: Boolean = false,

    val createdAt: Instant,
    val updatedAt: Instant,
)
