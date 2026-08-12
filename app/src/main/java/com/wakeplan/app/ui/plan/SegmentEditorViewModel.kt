package com.wakeplan.app.ui.plan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.wakeplan.app.ui.plan.PlanDetailViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 段编辑器 ViewModel — v0.3 砍段后整体废弃
 *
 * 整个类在 commit 4 跟 SegmentEditorScreen 一起删。
 * commit 1 阶段保留是为了让 Hilt 注入图能 build（NavGraph 仍引用该 route 名）。
 *
 * 所有方法 throw NotImplementedError。UI 屏幕在 commit 4 前不可用。
 */
@HiltViewModel
class SegmentEditorViewModel @Inject constructor(
    @Suppress("UNUSED_PARAMETER") savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val planId: String = checkNotNull(savedStateHandle.get<String>(PlanDetailViewModel.ARG_PLAN_ID)) {
        "SegmentEditorViewModel requires planId nav arg (deprecated — v0.3 砍段)"
    }

    init {
        error("SegmentEditorViewModel 已废弃（v0.3 砍段）— commit 4 删除")
    }

    companion object {
        const val ARG_SEGMENT_ID = "segmentId"
    }
}
