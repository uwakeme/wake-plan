package com.wakeplan.app.data.db

import android.util.Log
import com.wakeplan.app.data.db.dao.PlanDao
import com.wakeplan.app.data.db.dao.PlanTemplateDao
import com.wakeplan.app.data.db.dao.TaskDao
import com.wakeplan.app.data.db.dao.TimeSegmentDao
import com.wakeplan.app.data.db.entity.PlanEntity
import com.wakeplan.app.data.db.entity.PlanTemplateEntity
import com.wakeplan.app.data.db.entity.TaskEntity
import com.wakeplan.app.data.db.entity.TimeSegmentEntity
import com.wakeplan.app.domain.model.RepeatRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据库初始化 seed
 * - 5.3 决策：3 个内置模板
 * - 仅在 plan_templates 表为空时插入（first install）
 * - 启动时由 App.onCreate 异步调用
 *
 * Demo plan：v0.1 验证用，**TODO 上线前删除**（用 BuildConfig.DEBUG 守卫或删掉）
 * 包括 2 个 plan + 3 个段 + 5 个任务，覆盖 DAILY 段、自动延期冲突场景
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    private val templateDao: PlanTemplateDao,
    private val planDao: PlanDao,
    private val segmentDao: TimeSegmentDao,
    private val taskDao: TaskDao,
) {
    fun seedIfEmpty(applicationScope: CoroutineScope) {
        applicationScope.launch(Dispatchers.IO) {
            val templateCount = templateDao.countAll()
            if (templateCount == 0) {
                BUILT_IN_TEMPLATES.forEach { templateDao.insert(it) }
            }
            val planCount = planDao.count()
            if (planCount == 0) {
                seedDemoPlan()  // v0.1 验证用
            }
        }
    }

    private suspend fun seedDemoPlan() {
        val now = Instant.now()
        val today = LocalDate.now()

        try {
            // Plan 1: 晨练 — DAILY 段 06:00-07:00
            planDao.insert(PlanEntity(
                id = "demo_plan_morning",
                title = "晨练 (demo)",
                colorHex = "#C48520",
                startDate = today,
                isActive = true,
                createdAt = now,
                updatedAt = now,
            ))
            Log.d("DBSeeder", "inserted plan 1")
            segmentDao.insert(TimeSegmentEntity(
                id = "demo_seg_morning",
                planId = "demo_plan_morning",
                title = "晨练段",
                startMinutes = 6 * 60,
                endMinutes = 7 * 60,
                repeatRule = RepeatRule.DAILY,
            ))
            Log.d("DBSeeder", "inserted segment 1")
            taskDao.insert(TaskEntity(
                id = "demo_task_warmup",
                segmentId = "demo_seg_morning",
                title = "热身",
                plannedDurationMinutes = 10,
                orderInSegment = 0,
            ))
            taskDao.insert(TaskEntity(
                id = "demo_task_run",
                segmentId = "demo_seg_morning",
                title = "跑步",
                plannedDurationMinutes = 30,
                autoExtend = true,
                orderInSegment = 1,
            ))
            taskDao.insert(TaskEntity(
                id = "demo_task_stretch",
                segmentId = "demo_seg_morning",
                title = "拉伸",
                plannedDurationMinutes = 10,
                isLocked = true,
                orderInSegment = 2,
            ))
            Log.d("DBSeeder", "inserted tasks 1")

            // Plan 2: 学习 — DAILY 段 09:30-10:30（与晨练最后 30min 冲突，验证 ConflictDetector）
            planDao.insert(PlanEntity(
                id = "demo_plan_study",
                title = "学习 (demo)",
                colorHex = "#3B6FB5",
                startDate = today,
                isActive = true,
                createdAt = now,
                updatedAt = now,
            ))
            segmentDao.insert(TimeSegmentEntity(
                id = "demo_seg_study",
                planId = "demo_plan_study",
                title = "早读段",
                startMinutes = 9 * 60 + 30,
                endMinutes = 10 * 60 + 30,
                repeatRule = RepeatRule.DAILY,
            ))
            taskDao.insert(TaskEntity(
                id = "demo_task_read",
                segmentId = "demo_seg_study",
                title = "阅读",
                plannedDurationMinutes = 40,
                orderInSegment = 0,
            ))
            taskDao.insert(TaskEntity(
                id = "demo_task_review",
                segmentId = "demo_seg_study",
                title = "复习",
                plannedDurationMinutes = 20,
                autoExtend = true,
                orderInSegment = 1,
            ))
            Log.d("DBSeeder", "inserted plan 2 + tasks")
        } catch (e: Exception) {
            Log.e("DBSeeder", "seedDemoPlan failed", e)
        }
    }

    companion object {
        private val BUILT_IN_TEMPLATES = listOf(
            PlanTemplateEntity(
                id = "tpl_morning",
                title = "标准晨练",
                colorHex = "#C48520",  // 晨光金
                isBuiltIn = true,
            ),
            PlanTemplateEntity(
                id = "tpl_study",
                title = "标准学习",
                colorHex = "#3B6FB5",  // 海蓝
                isBuiltIn = true,
            ),
            PlanTemplateEntity(
                id = "tpl_weekend_reading",
                title = "周末阅读",
                colorHex = "#8B5CF6",  // 紫罗兰
                isBuiltIn = true,
            ),
        )
    }
}
