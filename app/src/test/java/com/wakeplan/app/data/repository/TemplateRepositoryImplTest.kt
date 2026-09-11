package com.wakeplan.app.data.repository

import androidx.room.withTransaction
import com.wakeplan.app.data.db.WakePlanDatabase
import com.wakeplan.app.data.db.dao.PlanTemplateDao
import com.wakeplan.app.data.db.entity.PlanTemplateEntity
import com.wakeplan.app.data.repository.mapper.toDomain
import com.wakeplan.app.domain.model.BuiltInTemplates
import com.wakeplan.app.domain.model.Plan
import com.wakeplan.app.domain.model.PlanTemplate
import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.repository.PlanRepository
import com.wakeplan.app.domain.repository.TaskRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * TemplateRepositoryImpl 模板应用测试 — v0.4 实装"建 plan + 复制任务"
 *
 * mock DB（withTransaction 静态直通）+ 仓库/DAO，验证：
 * - 应用内置模板：plan 用模板 title/color 创建，任务按 spec 顺序逐条 create
 * - customTitle 覆盖模板 title；color 用模板的
 * - spec 字段透传（isLocked / CUSTOM+customDays / note）
 * - 未知模板 id 抛 IllegalStateException
 * - plan + tasks 在同一个 Room 事务块内创建
 * - observeAll 对内置模板 enrich 出任务预览
 */
@DisplayName("TemplateRepositoryImpl 应用模板")
class TemplateRepositoryImplTest {

    private val db: WakePlanDatabase = mockk()
    private val templateDao: PlanTemplateDao = mockk()
    private val planRepository: PlanRepository = mockk()
    private val taskRepository: TaskRepository = mockk()

    @BeforeEach
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { db.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    private fun repository() = TemplateRepositoryImpl(
        db = db,
        templateDao = templateDao,
        planRepository = planRepository,
        taskRepository = taskRepository,
    )

    private fun builtinTemplateEntity(id: String) = PlanTemplateEntity(
        id = id,
        title = "标准晨练",
        colorHex = "#C48520",
        isBuiltIn = true,
        schemaVersion = 1,
    )

    private fun stubPlanCreate(log: MutableList<String>? = null) {
        coEvery {
            planRepository.create(title = any(), colorHex = any(), startDate = any())
        } answers {
            log?.add("create-plan")
            Plan(
                id = "new-plan",
                title = firstArg(),
                colorHex = secondArg(),
                startDate = thirdArg(),
                isActive = true,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        }
    }

    private fun stubTaskCreate(log: MutableList<String>? = null) {
        val ids = ArrayDeque(listOf("task-a", "task-b", "task-c", "task-d", "task-e", "task-f"))
        coEvery {
            taskRepository.create(
                planId = any(), title = any(), startMinutes = any(), endMinutes = any(),
                repeatRule = any(), customDays = any(), isLocked = any(), note = any(),
            )
        } answers {
            log?.add("create-task")
            Task(
                id = ids.removeFirstOrNull() ?: "task-extra",
                planId = firstArg(),
                title = arg(1),
                startMinutes = arg(2),
                endMinutes = arg(3),
                repeatRule = arg(4),
                customDays = arg(5),
                isLocked = arg(6),
                orderInPlan = 0,
                note = arg(7),
            )
        }
    }

    @Test
    fun `应用内置模板 — 建 plan 并按 spec 顺序复制全部任务`() = runTest {
        coEvery { templateDao.findById("tpl_morning") } returns builtinTemplateEntity("tpl_morning")
        stubPlanCreate()
        stubTaskCreate()

        val plan = repository().createPlanFromTemplate("tpl_morning")

        assertEquals("new-plan", plan.id)
        val specs = BuiltInTemplates.specFor("tpl_morning")!!.tasks
        coVerify(exactly = specs.size) {
            taskRepository.create(any(), any(), any(), any(), any(), any(), any(), any())
        }
        coVerifyOrder {
            specs.forEach { s ->
                taskRepository.create(
                    planId = "new-plan",
                    title = s.title,
                    startMinutes = s.startMinutes,
                    endMinutes = s.endMinutes,
                    repeatRule = s.repeatRule,
                    customDays = s.customDays,
                    isLocked = s.isLocked,
                    note = s.note,
                )
            }
        }
    }

    @Test
    fun `customTitle 覆盖模板 title，color 用模板的`() = runTest {
        coEvery { templateDao.findById("tpl_morning") } returns builtinTemplateEntity("tpl_morning")
        stubPlanCreate()
        stubTaskCreate()

        repository().createPlanFromTemplate("tpl_morning", customTitle = "我的晨练")

        coVerify(exactly = 1) {
            planRepository.create(title = "我的晨练", colorHex = "#C48520", startDate = any())
        }
    }

    @Test
    fun `spec 字段透传 — 跑步钉死、周末任务 CUSTOM 周六日、note 带过去`() = runTest {
        coEvery { templateDao.findById("tpl_morning") } returns builtinTemplateEntity("tpl_morning")
        stubPlanCreate()
        stubTaskCreate()
        repository().createPlanFromTemplate("tpl_morning")

        coVerify(exactly = 1) {
            taskRepository.create(
                planId = "new-plan", title = "跑步 40 分钟",
                startMinutes = 6 * 60 + 25, endMinutes = 7 * 60 + 5,
                repeatRule = RepeatRule.DAILY, customDays = emptySet(),
                isLocked = true, note = "钉死：超时也不顺延，宁可少跑",
            )
        }

        coEvery {
            templateDao.findById("tpl_weekend_reading")
        } returns PlanTemplateEntity(
            id = "tpl_weekend_reading",
            title = "周末阅读",
            colorHex = "#8B5CF6",
            isBuiltIn = true,
            schemaVersion = 1,
        )
        repository().createPlanFromTemplate("tpl_weekend_reading")

        val weekendDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        coVerify(exactly = 4) {
            taskRepository.create(
                planId = "new-plan", title = any(),
                startMinutes = any(), endMinutes = any(),
                repeatRule = RepeatRule.CUSTOM, customDays = weekendDays,
                isLocked = false, note = null,
            )
        }
    }

    @Test
    fun `未知模板 id 抛 IllegalStateException`() = runTest {
        coEvery { templateDao.findById("nope") } returns null

        assertThrows<IllegalStateException> {
            repository().createPlanFromTemplate("nope")
        }
    }

    @Test
    fun `plan + tasks 在同一个 withTransaction 块内创建`() = runTest {
        coEvery { templateDao.findById("tpl_morning") } returns builtinTemplateEntity("tpl_morning")
        val log = mutableListOf<String>()
        stubPlanCreate(log)
        stubTaskCreate(log)
        coEvery { db.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            log.add("tx-begin")
            secondArg<suspend () -> Any?>().invoke()
            log.add("tx-end")
        }

        repository().createPlanFromTemplate("tpl_morning")

        val firstTask = log.indexOf("create-task")
        val lastTask = log.lastIndexOf("create-task")
        assertTrue(log.indexOf("tx-begin") < log.indexOf("create-plan"))
        assertTrue(log.indexOf("create-plan") < firstTask)
        assertTrue(lastTask < log.indexOf("tx-end"))
        assertEquals("tx-end", log.last())
    }

    @Test
    fun `observeAll 把内置模板任务补进预览`() = runTest {
        coEvery { templateDao.observeAll() } returns kotlinx.coroutines.flow.flowOf(
            listOf(builtinTemplateEntity("tpl_morning")),
        )

        val templates = repository().observeAll().first()

        val template = templates.single()
        assertEquals("标准晨练", template.title)
        assertEquals(
            BuiltInTemplates.specFor("tpl_morning")!!.tasks.size,
            template.tasks.size,
        )
    }
}
