package com.wakeplan.app.domain.model

import java.time.DayOfWeek
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * BuiltInTemplates 单测 — v0.4 实装完整 spec
 *
 * 覆盖：
 * - 3 个元数据都存在
 * - specFor 内置 id 返回完整 spec（非空任务），未知 id 返回 null
 * - toPlanTemplate(meta) 对内置模板 enrich 出任务，用户模板原样返回
 * - toPlanTemplate(spec) 的任务占位 id 唯一（详情页列表 key 依赖）
 * - spec 数据合法性：时间窗有序、CUSTOM 必填 customDays
 */
class BuiltInTemplatesTest {

    @Test fun `3 个内置模板元数据都存在`() {
        val all = BuiltInTemplates.ALL
        assertEquals(3, all.size)
        val byId = all.associateBy { it.id }
        assertNotNull(byId["tpl_morning"])
        assertNotNull(byId["tpl_study"])
        assertNotNull(byId["tpl_weekend_reading"])
    }

    @Test fun `3 个内置模板 title 和 colorHex 正确`() {
        val morning = BuiltInTemplates.ALL.single { it.id == "tpl_morning" }
        assertEquals("标准晨练", morning.title)
        assertEquals("#C48520", morning.colorHex)

        val study = BuiltInTemplates.ALL.single { it.id == "tpl_study" }
        assertEquals("标准学习", study.title)
        assertEquals("#3B6FB5", study.colorHex)

        val weekend = BuiltInTemplates.ALL.single { it.id == "tpl_weekend_reading" }
        assertEquals("周末阅读", weekend.title)
        assertEquals("#8B5CF6", weekend.colorHex)
    }

    @Test fun `specFor 内置 id 返回非空任务 spec — 未知 id 返回 null`() {
        BuiltInTemplates.ALL.forEach { spec ->
            val found = BuiltInTemplates.specFor(spec.id)
            assertNotNull(found, spec.id)
            assertTrue(found!!.tasks.isNotEmpty(), "${spec.id} 任务列表为空")
        }
        assertNull(BuiltInTemplates.specFor("tpl_unknown"))
        assertNull(BuiltInTemplates.specFor(""))
    }

    @Test fun `spec 数据合法 — 时间窗有序且 CUSTOM 必填 customDays`() {
        BuiltInTemplates.ALL.forEach { spec ->
            spec.tasks.forEach { t ->
                assertTrue(t.startMinutes < t.endMinutes, "${spec.id}/${t.title} 时间窗无序")
                assertTrue(t.endMinutes <= 2880, "${spec.id}/${t.title} endMinutes 超界")
                if (t.repeatRule == RepeatRule.CUSTOM) {
                    assertTrue(t.customDays.isNotEmpty(), "${spec.id}/${t.title} CUSTOM 缺 customDays")
                }
            }
        }
        // 周末阅读任务必须只落在周末
        val weekend = BuiltInTemplates.specFor("tpl_weekend_reading")!!
        weekend.tasks.forEach { t ->
            assertEquals(RepeatRule.CUSTOM, t.repeatRule, "${t.title} 应为 CUSTOM")
            assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), t.customDays, "${t.title} 应只在周末")
        }
    }

    @Test fun `toPlanTemplate(meta) 对内置模板 enrich 出任务，用户模板原样返回`() {
        val builtinMeta = PlanTemplate(
            id = "tpl_morning",
            title = "标准晨练",
            colorHex = "#C48520",
            isBuiltIn = true,
        )
        val enriched = BuiltInTemplates.toPlanTemplate(builtinMeta)
        assertTrue(enriched.tasks.isNotEmpty())
        assertEquals("跑步 40 分钟", enriched.tasks.first { it.isLocked }.title)

        val userMeta = PlanTemplate(
            id = "tpl_user",
            title = "用户模板",
            colorHex = "#999999",
            isBuiltIn = false,
        )
        assertSame(userMeta, BuiltInTemplates.toPlanTemplate(userMeta))
    }

    @Test fun `toPlanTemplate(spec) 任务字段映射正确且占位 id 唯一`() {
        val spec = BuiltInTemplates.specFor("tpl_study")!!
        val result = BuiltInTemplates.toPlanTemplate(spec)
        assertEquals("tpl_study", result.id)
        assertEquals("标准学习", result.title)
        assertEquals("#3B6FB5", result.colorHex)
        assertEquals(true, result.isBuiltIn)

        assertEquals(spec.tasks.size, result.tasks.size)
        assertEquals(spec.tasks.map { it.title }, result.tasks.map { it.title })
        assertEquals(spec.tasks.mapIndexed { i, _ -> i }, result.tasks.map { it.orderInPlan })
        // 详情页 items(key = task.id)：占位 id 必须唯一
        assertEquals(result.tasks.size, result.tasks.map { it.id }.toSet().size)
        result.tasks.zip(spec.tasks).forEach { (task, s) ->
            assertEquals(s.startMinutes, task.startMinutes)
            assertEquals(s.endMinutes, task.endMinutes)
            assertEquals(s.repeatRule, task.repeatRule)
            assertEquals(s.customDays, task.customDays)
            assertEquals(s.isLocked, task.isLocked)
            assertEquals(s.note, task.note)
            assertEquals(spec.id, task.planId)
        }
    }
}
