package com.wakeplan.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Repository Module — Phase 0 占位
 * Phase 1 实装 PlanRepository / TaskRepository / TemplateRepository 时，绑定 interface -> impl
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule
