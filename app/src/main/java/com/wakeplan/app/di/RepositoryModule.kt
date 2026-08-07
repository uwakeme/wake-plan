package com.wakeplan.app.di

import com.wakeplan.app.data.repository.PlanRepositoryImpl
import com.wakeplan.app.data.repository.SegmentRepositoryImpl
import com.wakeplan.app.data.repository.TaskCompletionRepositoryImpl
import com.wakeplan.app.data.repository.TaskRepositoryImpl
import com.wakeplan.app.data.repository.TemplateRepositoryImpl
import com.wakeplan.app.domain.repository.PlanRepository
import com.wakeplan.app.domain.repository.SegmentRepository
import com.wakeplan.app.domain.repository.TaskCompletionRepository
import com.wakeplan.app.domain.repository.TaskRepository
import com.wakeplan.app.domain.repository.TemplateRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository Module — 绑定 domain 接口到 data 实现
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindPlanRepository(impl: PlanRepositoryImpl): PlanRepository

    @Binds @Singleton
    abstract fun bindSegmentRepository(impl: SegmentRepositoryImpl): SegmentRepository

    @Binds @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds @Singleton
    abstract fun bindTemplateRepository(impl: TemplateRepositoryImpl): TemplateRepository

    @Binds @Singleton
    abstract fun bindTaskCompletionRepository(impl: TaskCompletionRepositoryImpl): TaskCompletionRepository
}
