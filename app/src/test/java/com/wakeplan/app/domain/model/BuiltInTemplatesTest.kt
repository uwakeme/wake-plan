package com.wakeplan.app.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek

/**
 * BuiltInTemplates 单测 — Phase 5.4.1
 *
 * 覆盖：
 * - 3 个 spec 都存在且字段正确
 * - specFor 查得到 / 查不到
 * - toPlanTemplate(spec) → PlanTemplate 字段全 + 占位 id 正确
 * - toPlanTemplate(meta) merge：用户模板 v0.2 保持 empty；内置模板拿到 spec
 * - 段时长 > 任务总时长（一致性）
 */
class BuiltInTemplatesTest {

    @Test fun `3 个内置模板都存在`() {
        val all = BuiltInTemplates.ALL
        assertEquals(3, all.size)
        assertTrue(all.any { it.id == "tpl_morning" })
        assertTrue(all.any { it.id == "tpl_study" })
        assertTrue(all.any { it.id == "tpl_weekend_reading" })
    }

    @Test fun `specFor 找到 + 找不到`() {
        assertNotNull(BuiltInTemplates.specFor("tpl_morning"))
        assertNotNull(BuiltInTemplates.specFor("tpl_study"))
        assertNotNull(BuiltInTemplates.specFor("tpl_weekend_reading"))
        assertNull(BuiltInTemplates.specFor("tpl_unknown"))
        assertNull(BuiltInTemplates.specFor(""))
    }

    @Test fun `晨练模板数据正确`() {
        val spec = BuiltInTemplates.specFor("tpl_morning")!!
        assertEquals("标准晨练", spec.title)
        assertEquals("#C48520", spec.colorHex)
        assertEquals(1, spec.segments.size)
        val seg = spec.segments[0]
        assertEquals("晨练段", seg.title)
        assertEquals(6 * 60, seg.startMinutes)
        assertEquals(7 * 60, seg.endMinutes)
        assertEquals(RepeatRule.DAILY, seg.repeatRule)
        assertEquals(3, spec.tasks.size)
        assertEquals(listOf("热身", "跑步", "拉伸"), spec.tasks.map { it.title })
        assertEquals(listOf(10, 30, 10), spec.tasks.map { it.plannedDurationMinutes })
    }

    @Test fun `学习模板数据正确`() {
        val spec = BuiltInTemplates.specFor("tpl_study")!!
        assertEquals("标准学习", spec.title)
        assertEquals(1, spec.segments.size)
        assertEquals(9 * 60 + 30, spec.segments[0].startMinutes)
        assertEquals(2, spec.tasks.size)
        assertEquals(listOf("阅读", "复习"), spec.tasks.map { it.title })
        assertEquals(listOf(40, 20), spec.tasks.map { it.plannedDurationMinutes })
    }

    @Test fun `周末阅读模板数据正确`() {
        val spec = BuiltInTemplates.specFor("tpl_weekend_reading")!!
        assertEquals("周末阅读", spec.title)
        assertEquals(1, spec.segments.size)
        val seg = spec.segments[0]
        assertEquals(RepeatRule.CUSTOM, seg.repeatRule)
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), seg.customDays)
        assertEquals(3, spec.tasks.size)
        assertEquals(listOf("精读", "整理笔记", "杂项"), spec.tasks.map { it.title })
        assertEquals(listOf(60, 30, 30), spec.tasks.map { it.plannedDurationMinutes })
    }

    @Test fun `toPlanTemplate(spec) 把 spec 展开成带占位 id 的 TimeSegment + Task`() {
        val spec = BuiltInTemplates.specFor("tpl_morning")!!
        val tpl = BuiltInTemplates.toPlanTemplate(spec)

        assertEquals("tpl_morning", tpl.id)
        assertEquals("标准晨练", tpl.title)
        assertEquals("#C48520", tpl.colorHex)
        assertTrue(tpl.isBuiltIn)
        // 占位 id：tplId + "_seg_0"
        assertEquals(listOf("tpl_morning_seg_0"), tpl.segments.map { it.id })
        assertEquals("tpl_morning", tpl.segments[0].planId)
        // task id 跟 segmentId 关联
        assertEquals(listOf("tpl_morning_seg_0"), tpl.tasks.map { it.segmentId }.distinct())
        assertEquals(3, tpl.tasks.size)
        // orderInSegment 按 spec 顺序：0, 1, 2
        assertEquals(listOf(0, 1, 2), tpl.tasks.map { it.orderInSegment })
    }

    @Test fun `toPlanTemplate(meta) 内置模板 merge spec；用户模板保留 empty`() {
        // 内置：拿 meta → 拿到 spec 数据
        val morningMeta = PlanTemplate(id = "tpl_morning", title = "我的晨练", colorHex = "#FFFFFF")
        val enriched = BuiltInTemplates.toPlanTemplate(morningMeta)
        assertEquals("我的晨练", enriched.title)  // meta 标题覆盖
        assertEquals(1, enriched.segments.size)
        assertEquals(3, enriched.tasks.size)

        // 用户模板：unknown id → segments/tasks 保持 empty
        val userMeta = PlanTemplate(id = "user_tpl_xyz", title = "我的模板", colorHex = "#000000", isBuiltIn = false)
        val userEnriched = BuiltInTemplates.toPlanTemplate(userMeta)
        assertTrue(userEnriched.segments.isEmpty())
        assertTrue(userEnriched.tasks.isEmpty())
    }

    @Test fun `每段任务总时长不超过段时长（避免 overrun）`() {
        BuiltInTemplates.ALL.forEach { spec ->
            spec.segments.forEachIndexed { segIdx, seg ->
                val segMinutes = seg.endMinutes - seg.startMinutes
                val tasksInSeg = spec.tasks.filter { it.segmentIndex == segIdx }
                val totalTaskMinutes = tasksInSeg.sumOf { it.plannedDurationMinutes }
                assertTrue(
                    totalTaskMinutes <= segMinutes,
                    "模板 ${spec.id} 段 $segIdx 总时长 ${segMinutes}min, 任务总 ${totalTaskMinutes}min, 不应超过段时长",
                )
            }
        }
    }

    @Test fun `任务 segmentIndex 都引用了有效段下标`() {
        BuiltInTemplates.ALL.forEach { spec ->
            spec.tasks.forEach { task ->
                assertTrue(
                    task.segmentIndex in spec.segments.indices,
                    "模板 ${spec.id} 任务 ${task.title} segmentIndex=${task.segmentIndex}, 段数=${spec.segments.size}",
                )
            }
        }
    }
}
