package com.wakeplan.app.data.repository

import com.wakeplan.app.data.db.dao.PlanTemplateDao
import com.wakeplan.app.data.db.entity.PlanTemplateEntity
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TimeSegment
import com.wakeplan.app.domain.repository.PlanRepository
import com.wakeplan.app.domain.repository.SegmentRepository
import com.wakeplan.app.domain.repository.TaskRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/**
 * TemplateRepositoryImpl.createPlanFromTemplate 单测 — Phase 5.4.2
 *
 * 覆盖：
 * - Plan / Segment / Task 写入顺序（plan → segments → tasks）
 * - placeholder segmentId → 真 segmentId 替换正确（tasks 引用真 segment）
 * - 模板没有 segments 时只创建空 plan
 * - customTitle 覆盖模板 title
 * - 模板不存在抛异常
 */
class TemplateRepositoryImplTest {

    private val templateDao: PlanTemplateDao = mockk()
    private val planRepository: PlanRepository = mockk()
    private val segmentRepository: SegmentRepository = mockk()
    private val taskRepository: TaskRepository = mockk()

    private val repo = TemplateRepositoryImpl(
        templateDao = templateDao,
        planRepository = planRepository,
        segmentRepository = segmentRepository,
        taskRepository = taskRepository,
    )

    @Test fun `createPlanFromTemplate 走 plan → segment → task 顺序，placeholder id 替换正确`() = runTest {
        // Given: 模板"标准学习"（1 段 + 2 任务，占位 id "tpl_study_seg_0"）
        val templateId = "tpl_study"
        val entity = PlanTemplateEntity(
            id = templateId, title = "标准学习", colorHex = "#3B6FB5", isBuiltIn = true,
        )
        coEvery { templateDao.findById(templateId) } returns entity

        // 模拟 dao 创建真 segment 返回真 id
        val realSegId = "seg_real_xyz"
        coEvery {
            segmentRepository.create(
                planId = any(),
                title = "早读段",
                startMinutes = 9 * 60 + 30,
                endMinutes = 10 * 60 + 30,
                repeatRule = RepeatRule.DAILY,
                customDays = emptySet(),
            )
        } returns TimeSegment(
            id = realSegId, planId = "p1", title = "早读段",
            startMinutes = 9 * 60 + 30, endMinutes = 10 * 60 + 30,
            repeatRule = RepeatRule.DAILY, customDays = emptySet(),
        )

        val createdTaskArgs = mutableListOf<Pair<String, String>>()  // (title, segmentId)
        coEvery {
            taskRepository.create(
                segmentId = any(),
                title = any(),
                plannedDurationMinutes = any(),
                autoExtend = any(),
                isLocked = any(),
                note = any(),
            )
        } answers {
            val args = it.invocation.args
            val segId = args[0] as String
            val title = args[1] as String
            createdTaskArgs.add(title to segId)
            Task(
                id = "task_${createdTaskArgs.size - 1}",
                segmentId = segId,
                title = title,
                plannedDurationMinutes = args[2] as Int,
                autoExtend = args[3] as Boolean,
                isLocked = args[4] as Boolean,
                orderInSegment = createdTaskArgs.size - 1,
                note = args[5] as String?,
            )
        }

        val newPlan = samplePlan(id = "p1", title = "标准学习")
        coEvery {
            planRepository.create(
                title = "标准学习",
                colorHex = "#3B6FB5",
                startDate = any(),
            )
        } returns newPlan

        // When
        val result = repo.createPlanFromTemplate(templateId)

        // Then: 返回新 plan
        assertEquals("p1", result.id)
        assertEquals("标准学习", result.title)

        // plan 创 1 次
        coVerify(exactly = 1) { planRepository.create(title = "标准学习", colorHex = "#3B6FB5", startDate = any()) }
        // 1 个 segment 创 1 次
        coVerify(exactly = 1) { segmentRepository.create(any(), any(), any(), any(), any(), any()) }
        // 2 个 task 创 2 次
        coVerify(exactly = 2) { taskRepository.create(any(), any(), any(), any(), any(), any()) }

        // 任务顺序：阅读、复习；segmentId 都引用真 segmentId（不是 placeholder "tpl_study_seg_0"）
        assertEquals(2, createdTaskArgs.size)
        assertEquals("阅读", createdTaskArgs[0].first)
        assertEquals("复习", createdTaskArgs[1].first)
        assertEquals(realSegId, createdTaskArgs[0].second)
        assertEquals(realSegId, createdTaskArgs[1].second)
        // 关键：placeholder id 绝对不能泄漏到 task
        createdTaskArgs.forEach { (title, segId) ->
            assertTrue(
                !segId.startsWith("tpl_"),
                "task '$title' 不应引用 placeholder id '$segId'",
            )
        }
    }

    @Test fun `createPlanFromTemplate 没有 segments 时只创建空 plan`() = runTest {
        // Given: 用户模板（id 在 BuiltInTemplates 找不到，segments 空）
        val templateId = "user_custom_xyz"
        val entity = PlanTemplateEntity(
            id = templateId, title = "我的自定义", colorHex = "#999999", isBuiltIn = false,
        )
        coEvery { templateDao.findById(templateId) } returns entity

        val newPlan = samplePlan(id = "p2", title = "我的自定义")
        coEvery { planRepository.create(title = "我的自定义", colorHex = "#999999", startDate = any()) } returns newPlan

        // When
        val result = repo.createPlanFromTemplate(templateId)

        // Then
        assertEquals("p2", result.id)
        coVerify(exactly = 1) { planRepository.create(any(), any(), any()) }
        coVerify(exactly = 0) { segmentRepository.create(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { taskRepository.create(any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `createPlanFromTemplate customTitle 覆盖模板 title`() = runTest {
        val templateId = "tpl_morning"
        val entity = PlanTemplateEntity(
            id = templateId, title = "标准晨练", colorHex = "#C48520", isBuiltIn = true,
        )
        coEvery { templateDao.findById(templateId) } returns entity
        coEvery {
            segmentRepository.create(any(), any(), any(), any(), any(), any())
        } returns TimeSegment(
            id = "s1", planId = "p3", title = "晨练段",
            startMinutes = 360, endMinutes = 420,
            repeatRule = RepeatRule.DAILY, customDays = emptySet(),
        )
        coEvery {
            taskRepository.create(any(), any(), any(), any(), any(), any())
        } returns Task(
            id = "t1", segmentId = "s1", title = "热身", plannedDurationMinutes = 10,
            autoExtend = true, isLocked = false, orderInSegment = 0, note = null,
        )
        coEvery { planRepository.create(title = "我的晨练", colorHex = "#C48520", startDate = any()) } returns
            samplePlan(id = "p3", title = "我的晨练")

        val result = repo.createPlanFromTemplate(templateId, customTitle = "我的晨练")
        assertEquals("p3", result.id)
        coVerify(exactly = 1) { planRepository.create(title = "我的晨练", colorHex = "#C48520", startDate = any()) }
    }

    @Test fun `createPlanFromTemplate 模板不存在抛 IllegalStateException`() = runTest {
        val templateId = "tpl_unknown_999"
        coEvery { templateDao.findById(templateId) } returns null

        var thrown: Throwable? = null
        try {
            repo.createPlanFromTemplate(templateId)
        } catch (t: Throwable) {
            thrown = t
        }
        assertNotNull("应该抛异常", thrown)
        assertTrue((thrown?.message ?: "").contains(templateId), "异常 message 应包含 templateId")
        coVerify(exactly = 0) { planRepository.create(any(), any(), any()) }
    }

    // ============================================================
    // helpers
    // ============================================================

    private fun samplePlan(id: String, title: String): Plan = Plan(
        id = id,
        title = title,
        colorHex = "#3B6FB5",
        startDate = LocalDate.of(2026, 8, 10),
        isActive = true,
        createdAt = Instant.parse("2026-08-10T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-10T00:00:00Z"),
    )
}
