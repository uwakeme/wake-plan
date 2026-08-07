package com.wakeplan.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakeplan.app.data.prefs.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 顶层根 ViewModel — Phase 3.3
 *
 * 决定 MainActivity 显示 OnboardingScreen 还是 AppNavHost
 * 读 `UserPreferences.hasShownOnboarding` Flow
 */
@HiltViewModel
class AppRootViewModel @Inject constructor(
    private val preferences: UserPreferences,
) : ViewModel() {

    val shouldShowOnboarding: Flow<Boolean> = preferences.hasShownOnboarding
        .map { !it }

    fun markOnboardingDone() {
        viewModelScope.launch { preferences.setHasShownOnboarding(true) }
    }
}
