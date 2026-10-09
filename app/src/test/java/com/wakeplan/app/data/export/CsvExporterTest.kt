package com.wakeplan.app.data.export

import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * CsvExporter 单测 — v0.5 阶段四
 *
 * 覆盖：BOM / 表头注入校验 / 转义（逗号/引号/换行）/ 已删计划任务回退 id /
 * 排序 / 空数据
 */
@DisplayName("CsvExporter")
class CsvExporterTest {

    private val exporter = CsvExporter()
    private val date = LocalDate.of(2026, 10, 9)

    private val headers = listOf("日期", "计划", "任务", "计划开始", "计划结束", "实际开始", "实际结束", "状态", "超时（分钟）")

    private fun plan(id: String, title: String) = Plan(
        id = id, title = title, colorHex = "#C48520",
        startDate = date, isActive = true,
        createdAt = java.time.Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = java.time.Instant.parse("2026-01-01T00:00:00Z"),
    )

    private fun task(id: String, planId: String, title: String) = Task(
        id = id, planId = planId, title = title,
        startMinutes = 540, endMinutes = 600,
        repeatRule = RepeatRule.DAILY, orderInPlan = 0,
    )

    private fun completion(
        id: String, taskId: String, planId: String,
        status: TaskStatus = TaskStatus.COMPLETED,
        overrun: Int = 0,
    ) = TaskCompletion(
        id = id, taskId = taskId, planId = planId,
        scheduledDate = date,
        plannedStart = date.atTime(9, 0),
        plannedEnd = date.atTime(10, 0),
        actualStart = date.atTime(9, 2),
        actualEnd = date.atTime(10, 0).plusMinutes(overrun.toLong()),
        status = status, overrunMinutes = overrun,
    )

    @Test
    fun `输出以 UTF-8 BOM 开头`() {
        val csv = exporter.export(emptyList(), emptyList(), emptyList(), headers)
        assertTrue(csv.startsWith(CsvExporter.BOM), "首字符应是 U+FEFF")
        assertEquals(CsvExporter.BOM + "日期,计划,任务,计划开始,计划结束,实际开始,实际结束,状态,超时（分钟）", csv)
    }

    @Test
    fun `空数据 — 只有表头一行`() {
        val csv = exporter.export(emptyList(), emptyList(), emptyList(), headers)
        assertEquals(1, csv.lines().size)
    }

    @Test
    fun `字段带逗号和引号 — RFC4180 转义`() {
        val csv = exporter.export(
            completions = listOf(completion("c1", "t1", "p1")),
            plans = listOf(plan("p1", "健身, 早晨")),
            tasks = listOf(task("t1", "p1", "跑步 \"5km\"")),
            headers = headers,
        )
        val dataLine = csv.lines()[1]
        assertTrue(dataLine.contains("\"健身, 早晨\""), "含逗号字段应加引号: $dataLine")
        assertTrue(dataLine.contains("\"跑步 \"\"5km\"\"\""), "内部引号应翻倍: $dataLine")
    }

    @Test
    fun `字段带换行 — 引号包裹，物理行多一条但首尾字段完整`() {
        val csv = exporter.export(
            completions = listOf(completion("c1", "t1", "p1")),
            plans = listOf(plan("p1", "健身\n早晨")),
            tasks = listOf(task("t1", "p1", "任务")),
            headers = headers,
        )
        // 换行字段被引号包裹：表头之后出现引号，且日期字段仍顶行首
        assertTrue(csv.contains("\"健身"), "换行字段应被引号包裹开头: $csv")
        assertTrue(csv.contains("早晨\""), "引号在换行后闭合: $csv")
        // 最后一个字段（超时分钟）仍完整落在末行
        assertTrue(csv.trimEnd().endsWith(",0"), "末字段完整: ${csv.trimEnd()}")
    }

    @Test
    fun `计划或任务已删除 — 回退为 id 可追溯`() {
        val csv = exporter.export(
            completions = listOf(completion("c1", "t-gone", "p-gone")),
            plans = emptyList(),
            tasks = emptyList(),
            headers = headers,
        )
        val dataLine = csv.lines()[1]
        assertTrue(dataLine.contains("p-gone"), "已删计划回退 id: $dataLine")
        assertTrue(dataLine.contains("t-gone"), "已删任务回退 id: $dataLine")
    }

    @Test
    fun `按日期与计划开始时间升序排列`() {
        val later = completion("c2", "t1", "p1").copy(
            scheduledDate = date,
            plannedStart = date.atTime(11, 0),
            plannedEnd = date.atTime(12, 0),
        )
        val earlier = completion("c1", "t1", "p1")
        val yesterday = completion("c0", "t1", "p1").copy(scheduledDate = date.minusDays(1))
        val csv = exporter.export(listOf(later, earlier, yesterday), emptyList(), emptyList(), headers)
        val ids = csv.lines().drop(1).map { it.substringBefore(",") }
        assertEquals(listOf(date.minusDays(1).toString(), date.toString(), date.toString()), ids)
    }

    @Test
    fun `PENDING 未开始的 — 实际时间为空串`() {
        val pending = completion("c1", "t1", "p1", status = TaskStatus.PENDING)
            .copy(actualStart = null, actualEnd = null)
        val csv = exporter.export(
            completions = listOf(pending),
            plans = listOf(plan("p1", "健身")),
            tasks = listOf(task("t1", "p1", "跑步")),
            headers = headers,
        )
        val fields = csv.lines()[1].split(",")
        assertEquals("", fields[5])
        assertEquals("", fields[6])
        assertEquals("PENDING", fields[7])
    }

    @Test
    fun `表头列数不符 — 抛 IllegalArgumentException`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            exporter.export(emptyList(), emptyList(), emptyList(), listOf("a", "b"))
        }
    }
}
