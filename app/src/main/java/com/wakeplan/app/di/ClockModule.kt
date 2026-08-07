package com.wakeplan.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * 时间源 — Hilt 全局单例 Clock
 *
 * 默认 systemDefaultZone；测试时可以替换成 Clock.fixed(...)
 * TodayViewModel 用它决定 AutoExtend 算法的"现在"
 */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
