package com.wakeplan.app.ui.plan

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.ui.graphics.vector.ImageVector
import com.wakeplan.app.domain.model.Plan

/**
 * Plan 列表的"展示用聚合"数据
 *
 * ViewModel 把 plan + task + completion 三路流合并成这个结构，
 * 直接喂给 [com.wakeplan.app.ui.common.PlanCard]。
 *
 * 不放在 domain — 这是 UI 层的 view-model 概念，不进数据库。
 */
data class PlanCardData(
    val plan: Plan,
    val iconKey: PlanIconKey,
    val status: PlanStatus,
    val totalTaskCount: Int,
    val todayTaskCount: Int,
    val todayDurationMinutes: Int,
    val progressPercent: Int,
)

/**
 * 计划图标分类 — 用计划标题做关键词匹配
 *
 * 未来可以让用户在创建计划时手动选图标（Plan entity 加 iconKey 字段），
 * 现在是简单启发式，让 demo 立刻有视觉差异。
 */
enum class PlanIconKey(val icon: ImageVector) {
    FITNESS(Icons.Outlined.FitnessCenter),
    STUDY(Icons.Outlined.AutoStories),
    WRITING(Icons.Outlined.Edit),
    GENERIC(Icons.Outlined.Circle);

    companion object {
        /**
         * 从计划标题推一个 iconKey。
         * 命中规则：中文关键词优先，再退化到英文 lowercase 包含。
         */
        fun derive(title: String): PlanIconKey {
            val lower = title.lowercase()
            return when {
                title.contains("健") || title.contains("运动") || lower.contains("fitness") || lower.contains("workout") -> FITNESS
                title.contains("学") || title.contains("读") || lower.contains("study") || lower.contains("learn") || lower.contains("read") -> STUDY
                title.contains("写") || title.contains("创作") || lower.contains("writ") || lower.contains("blog") -> WRITING
                else -> GENERIC
            }
        }
    }
}

/**
 * 计划运行状态（决定右上角徽章）
 *
 * - ACTIVE    — 计划启用 + 今日还有 pending/in_progress 任务
 * - SCHEDULED — 计划启用 + 今日任务全部完成 / 跳过（明天还有活儿）
 * - IDLE      — 计划归档 或 今日没有任务实例
 */
enum class PlanStatus {
    ACTIVE,
    SCHEDULED,
    IDLE,
}
