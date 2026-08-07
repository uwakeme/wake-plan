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
    val createdAt: Instant,
    val updatedAt: Instant,
)
