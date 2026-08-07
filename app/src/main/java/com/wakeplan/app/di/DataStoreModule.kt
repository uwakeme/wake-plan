package com.wakeplan.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Singleton

/**
 * DataStore Module — Hilt 全局单例 Preferences DataStore
 *
 * 文件路径：`/data/data/com.wakeplan.app/files/datastore/user_prefs.preferences_pb`
 * （DataStore 自动用 protobuf 序列化）
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    private const val DATASTORE_FILE = "user_prefs.preferences_pb"

    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { File(context.filesDir, "datastore/$DATASTORE_FILE") },
        )
    }
}
