package com.wakeplan.app.domain.repository

import com.wakeplan.app.domain.model.Plan
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

/**
 * 计划仓库 — UI 层通过此接口访问计划数据
 * 实现见 data/repository/PlanRepositoryImpl
 */
interface PlanRepository {

    /** 观察所有计划（按更新时间倒序） */
    fun observeAll(): Flow<List<Plan>>

    /** 观察单个计划 */
    fun observeById(id: String): Flow<Plan?>

    /** 单次查询 */
    suspend fun getById(id: String): Plan?

    /** 创建计划，自动生成 UUID 和时间戳 */
    suspend fun create(
        title: String,
        colorHex: String,
        startDate: LocalDate = LocalDate.now(),
    ): Plan

    /** 更新整个 plan 对象 */
    suspend fun update(plan: Plan)

    /** 删除计划（CASCADE 删段和任务） */
    suspend fun delete(id: String)

    /** 计划总数（用于引导页判断是否首启动） */
    suspend fun count(): Int
}

/** 全局 UUID 工厂（方便测试时注入 fake） */
fun newId(): String = UUID.randomUUID().toString()
