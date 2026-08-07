package com.wakeplan.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.nio.file.Files

/**
 * UserPreferences 单测 — Phase 3.1
 *
 * 用 in-memory DataStore（临时目录）跑，验证默认值 + set/get 正确回读
 * 不依赖 Android 框架，pure JVM
 */
@DisplayName("UserPreferences")
class UserPreferencesTest {

    private lateinit var tempDir: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefs: UserPreferences

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("wp_prefs_").toFile()
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { File(tempDir, "test.preferences_pb") },
        )
        prefs = UserPreferences(dataStore)
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `默认值 — lead=5 starting=on_due=on completed=off recap=off`() = runTest {
        assertEquals(5, prefs.taskStartingLeadMinutes.first())
        assertTrue(prefs.taskStartingEnabled.first())
        assertTrue(prefs.taskOverdueEnabled.first())
        assertEquals(false, prefs.taskCompletedEnabled.first())
        assertEquals(false, prefs.planRecapEnabled.first())
    }

    @Test
    fun `setTaskStartingLeadMinutes 写入 10 后能读到 10`() = runTest {
        prefs.setTaskStartingLeadMinutes(10)
        assertEquals(10, prefs.taskStartingLeadMinutes.first())
    }

    @Test
    fun `setTaskStartingLeadMinutes 写入 0 (关闭提前) 能读到 0`() = runTest {
        prefs.setTaskStartingLeadMinutes(0)
        assertEquals(0, prefs.taskStartingLeadMinutes.first())
    }

    @Test
    fun `setTaskStartingLeadMinutes 非法值抛 IllegalArgumentException`() = runTest {
        assertThrows<IllegalArgumentException> {
            kotlinx.coroutines.runBlocking { prefs.setTaskStartingLeadMinutes(7) }
        }
        assertThrows<IllegalArgumentException> {
            kotlinx.coroutines.runBlocking { prefs.setTaskStartingLeadMinutes(-1) }
        }
    }

    @Test
    fun `setTaskStartingEnabled 切换 false 后能读到 false`() = runTest {
        prefs.setTaskStartingEnabled(false)
        assertEquals(false, prefs.taskStartingEnabled.first())
    }

    @Test
    fun `setTaskOverdueEnabled 切换 false 后能读到 false`() = runTest {
        prefs.setTaskOverdueEnabled(false)
        assertEquals(false, prefs.taskOverdueEnabled.first())
    }

    @Test
    fun `setTaskCompletedEnabled 切换 true 后能读到 true`() = runTest {
        prefs.setTaskCompletedEnabled(true)
        assertEquals(true, prefs.taskCompletedEnabled.first())
    }

    @Test
    fun `setPlanRecapEnabled 切换 true 后能读到 true`() = runTest {
        prefs.setPlanRecapEnabled(true)
        assertEquals(true, prefs.planRecapEnabled.first())
    }

    @Test
    @DisplayName("hasShownOnboarding 默认 false，set true 后能读到 true")
    fun `hasShownOnboarding default false set true works`() = runTest {
        assertEquals(false, prefs.hasShownOnboarding.first())
        prefs.setHasShownOnboarding(true)
        assertEquals(true, prefs.hasShownOnboarding.first())
    }

    @Test
    @DisplayName("hasShownOnboarding 可以从 true 切回 false（重新看引导）")
    fun `hasShownOnboarding can revert to false`() = runTest {
        prefs.setHasShownOnboarding(true)
        assertEquals(true, prefs.hasShownOnboarding.first())
        prefs.setHasShownOnboarding(false)
        assertEquals(false, prefs.hasShownOnboarding.first())
    }
}
