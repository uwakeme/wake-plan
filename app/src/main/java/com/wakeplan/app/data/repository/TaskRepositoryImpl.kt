package com.wakeplan.app.data.repository

import androidx.room.withTransaction
import com.wakeplan.app.data.db.WakePlanDatabase
import com.wakeplan.app.data.db.dao.TaskDao
import com.wakeplan.app.data.repository.mapper.toDomain
import com.wakeplan.app.data.repository.mapper.toEntity
import com.wakeplan.app.domain.model.RepeatRule
import com.wakeplan.app.domain.model.Task
import com.wakeplan.app.domain.model.TaskCompletion
import com.wakeplan.app.domain.model.TaskStatus
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import com.wakeplan.app.domain.repository.TaskRepository
import com.wakeplan.app.domain.repository.newId
import com.wakeplan.app.domain.usecase.TaskWindowCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val db: WakePlanDatabase,
    private val taskDao: TaskDao,
    private val taskCompletionRepository: TaskCompletionRepository,
    private val taskWindowCalculator: TaskWindowCalculator,
    private val clock: Clock,
) : TaskRepository {

    override fun observeByPlan(planId: String): Flow<List<Task>> =
        taskDao.observeByPlan(planId).map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<Task>> =
        taskDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): Task? =
        taskDao.findById(id)?.toDomain()

    override suspend fun create(
        planId: String,
        title: String,
        startMinutes: Int,
        endMinutes: Int,
        repeatRule: RepeatRule,
        customDays: Set<DayOfWeek>,
        isLocked: Boolean,
        note: String?,
    ): Task = db.withTransaction {
        // 并发建任务时 maxOrderInPlan 会撞号，包事务保证原子
        val nextOrder = (taskDao.maxOrderInPlan(planId) ?: -1) + 1
        val task = Task(
            id = newId(),
            planId = planId,
            title = title,
            startMinutes = startMinutes,
            endMinutes = endMinutes,
            repeatRule = repeatRule,
            customDays = if (repeatRule == RepeatRule.CUSTOM) customDays else emptySet(),
            isLocked = isLocked,
            createdEpochDay = LocalDate.now(clock).toEpochDay(),
            orderInPlan = nextOrder,
            note = note,
        )
        taskDao.insert(task.toEntity())
        task
    }

    override suspend fun update(task: Task) = db.withTransaction {
        taskDao.update(task.toEntity())
        // 任务定义变了：未来未开始的实例窗口对齐新定义
        syncTaskCompletions(task, from = LocalDate.now(clock), to = LocalDate.now(clock).plusDays(30))
    }

    override suspend fun resyncPlanCompletions(planId: String, date: LocalDate) = db.withTransaction {
        val tasks = taskDao.observeByPlan(planId).first().map { it.toDomain() }
        val rows = taskCompletionRepository
            .getByDateRange(date, date.plusDays(1))
            .filter { it.planId == planId }
        rows.forEach { row ->
            tasks.find { it.id == row.taskId }?.let { syncRow(row, it) }
        }
    }

    /**
     * 同步 [from, to] 日期窗口内该任务的未开始 PENDING 实例：
     * - 任务在该日期不再生效（如 DAILY 改 WEEKDAYS 后撞上周末）→ 删掉未开始实例
     * - 窗口与定义不一致（改时间、改时长）→ 重置为定义窗口（清掉链式延期位移）
     * 已开始/已完成的实例保持不动（用户在跑的任务不应被打乱）
     */
    private suspend fun syncTaskCompletions(task: Task, from: LocalDate, to: LocalDate) {
        val rows = taskCompletionRepository
            .getByDateRange(from, to.plusDays(1))
            .filter { it.taskId == task.id }
        rows.forEach { syncRow(it, task) }
    }

    private suspend fun syncRow(row: TaskCompletion, task: Task) {
        if (row.status != TaskStatus.PENDING || row.actualStart != null) return
        val window = taskWindowCalculator(task, row.scheduledDate)
        if (window == null) {
            taskCompletionRepository.delete(row.id)
            return
        }
        if (row.plannedStart != window.plannedStart ||
            row.plannedEnd != window.plannedEnd ||
            row.affectedTasks.isNotEmpty()
        ) {
            taskCompletionRepository.upsert(
                row.copy(
                    plannedStart = window.plannedStart,
                    plannedEnd = window.plannedEnd,
                    affectedTasks = emptyList(),
                )
            )
        }
    }

    override suspend fun delete(id: String) {
        // 外键 CASCADE 会自动删除 task_completions；这里仅删 task
        taskDao.deleteById(id)
    }

    override suspend fun reorder(planId: String, orderedTaskIds: List<String>) {
        taskDao.reorder(planId, orderedTaskIds)
    }
}
