package com.wakeplan.app.ui.common

import java.time.LocalDateTime

/**
 * 时间显示格式化 — v0.5 阶段一去重
 *
 * 之前四个 Screen 各自实现近似函数（PlanDetail 的 formatTimeShort /
 * TemplateDetail 的 formatHHmm / Today 的 formatHm / QuickAdd 的 TIME_FMT）。
 * 统一到这里，语义保持各处原有行为：
 * - [minutesOfDay]：当天分钟数 → "HH:mm"（跨午夜折算到 0..1439，调用方自己标 +1）
 * - [dateTime]：LocalDateTime → "HH:mm"
 * - [rawRange]：TaskEditor 专用——结束侧小时可 ≥24，跨午夜任务显示 "23:00 - 25:00"
 */
object TimeFormat {

    fun hm(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

    /** 当天分钟数（支持 >1440 的跨午夜值，按钟面折算）→ "HH:mm" */
    fun minutesOfDay(minutes: Int): String {
        val total = ((minutes % 1440) + 1440) % 1440
        return hm(total / 60, total % 60)
    }

    fun dateTime(dt: LocalDateTime): String = hm(dt.hour, dt.minute)

    /** 起止区间：开始原样，结束保留 ≥24 小时（跨午夜任务直观显示"25:00"） */
    fun rawRange(startMinutes: Int, endMinutes: Int): String {
        val start = "%d:%02d".format(startMinutes.coerceAtLeast(0) / 60, startMinutes.coerceAtLeast(0) % 60)
        val end = "%d:%02d".format(endMinutes.coerceAtLeast(0) / 60, endMinutes.coerceAtLeast(0) % 60)
        return "$start - $end"
    }
}
