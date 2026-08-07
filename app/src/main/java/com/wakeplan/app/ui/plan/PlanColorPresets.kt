package com.wakeplan.app.ui.plan

/**
 * 计划颜色预设 — 5.3 决策：10 预设 + 自定义 hex
 * 选色与 design/colors_and_type.css 令牌呼应 + 暖冷中性各 2-3 个
 */
object PlanColorPresets {

    val PRESETS: List<String> = listOf(
        "#C48520", // 1. 晨光金 (brand primary)
        "#2E9B5E", // 2. 薄荷绿 (success)
        "#D4901A", // 3. 暖阳橙 (warning)
        "#D87093", // 4. 樱粉
        "#3B6FB5", // 5. 海蓝 (info)
        "#8B5CF6", // 6. 紫罗兰
        "#14B8A6", // 7. 翡翠青
        "#C84545", // 8. 玫瑰红 (error)
        "#A0826D", // 9. 砂棕
        "#6E695E", // 10. 烟灰 (text secondary)
    )

    /** 默认色：晨光金 */
    val DEFAULT: String = PRESETS.first()

    /** hex 格式校验：`#RRGGBB` */
    private val HEX_PATTERN = Regex("^#[0-9A-Fa-f]{6}$")

    fun isValidHex(hex: String): Boolean = HEX_PATTERN.matches(hex)
}
