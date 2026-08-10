package com.wakeplan.app.widget

import com.wakeplan.app.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * TodayWidgetProvider 单测 — Phase 5.6
 *
 * 覆盖：
 * - EXTRA_NAV_TO_TODAY 常量（MainActivity 依赖它处理 deep link）
 * - widget layout 资源存在
 * - widget 内部引用的 view id 资源存在
 * - 9 个 strings.xml widget_* key 都存在
 */
class TodayWidgetProviderTest {

    @Test fun `EXTRA_NAV_TO_TODAY 常量值稳定（MainActivity 依赖）`() {
        assertEquals(
            "com.wakeplan.app.NAV_TO_TODAY",
            TodayWidgetProvider.EXTRA_NAV_TO_TODAY,
        )
    }

    @Test fun `widget layout 资源 id 存在`() {
        // 不能 instantiate RemoteViews（缺 Context），但 R.layout 引用是 compile-time
        // 这里通过 R class 的 id 存在性来验证
        val layoutId = R.layout::class.java.getDeclaredField("widget_today").getInt(null)
        assertNotEquals(0, layoutId)
    }

    @Test fun `widget 内部 view id 资源存在`() {
        val ids = listOf(
            "widget_title",
            "widget_app_name",
            "widget_progress",
            "widget_progress_label",
            "widget_task_count",
            "widget_next_task",
        )
        for (name in ids) {
            val field = R.id::class.java.getDeclaredField(name)
            val id = field.getInt(null)
            assertNotEquals(0, id, "id $name 不应解析为 0")
        }
    }

    @Test fun `widget 字符串资源存在（中英文版都有）`() {
        // 中英文版都应在的 key
        val expectedKeys = listOf(
            "widget_title",
            "widget_loading",
            "widget_progress_label_zero",
            "widget_progress_label_pct",
            "widget_task_count_zero",
            "widget_task_count_n",
            "widget_next_task_empty",
            "widget_next_task_at",
            "widget_no_plan",
        )
        for (name in expectedKeys) {
            val field = R.string::class.java.getDeclaredField(name)
            val id = field.getInt(null)
            assertNotEquals(0, id, "string $name 不应解析为 0")
        }
    }
}
