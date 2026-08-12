package com.wakeplan.app.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * BuiltInTemplates 单测 — v0.3 扁平化
 *
 * v0.3 砍段后模板元数据简化：只保留 id / title / colorHex，segments + tasks 完整 spec
 * 留 commit 6 整体重写模板系统时再实装。
 *
 * 覆盖：
 * - 3 个元数据都存在
 * - specFor 全部返回 null（v0.3 简化）
 * - toPlanTemplate(meta) 直接返回 meta（不 enrich）
 * - toPlanTemplate(spec) 返回带 spec 元数据的 PlanTemplate
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

    @Test fun `specFor v0_3 全部返回 null（commit 6 整体重写）`() {
        // v0.3 砍段：完整 spec 暂不实装；specFor 全部 null
        assertNull(BuiltInTemplates.specFor("tpl_morning"))
        assertNull(BuiltInTemplates.specFor("tpl_study"))
        assertNull(BuiltInTemplates.specFor("tpl_weekend_reading"))
        assertNull(BuiltInTemplates.specFor("tpl_unknown"))
        assertNull(BuiltInTemplates.specFor(""))
    }

    @Test fun `toPlanTemplate(meta) 直接返回 meta（v0_3 不 enrich）`() {
        val meta = PlanTemplate(
            id = "tpl_user",
            title = "用户模板",
            colorHex = "#999999",
            isBuiltIn = false,
        )
        val result = BuiltInTemplates.toPlanTemplate(meta)
        // v0.3：返回原 meta
        assertSame(meta, result)
    }

    @Test fun `toPlanTemplate(spec) 返回带 spec 元数据的 PlanTemplate（tasks 列表空）`() {
        val spec = BuiltInTemplateSpec(
            id = "tpl_morning",
            title = "标准晨练",
            colorHex = "#C48520",
        )
        val result = BuiltInTemplates.toPlanTemplate(spec)
        assertEquals("tpl_morning", result.id)
        assertEquals("标准晨练", result.title)
        assertEquals("#C48520", result.colorHex)
        assertEquals(true, result.isBuiltIn)
        // v0.3：tasks 列表空（commit 6 实装完整 spec 时再填）
        assertEquals(0, result.tasks.size)
    }
}
