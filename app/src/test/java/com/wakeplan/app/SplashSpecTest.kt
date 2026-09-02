package com.wakeplan.app

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * v0.4 启动动画 — 资源 / 主题 / Compose overlay 的结构测试。
 *
 * 两层 splash 结构：
 * 1. 系统 splash（Theme.WakePlan.Splash + 静态 ic_splash_logo）：防白屏，系统默认移除
 * 2. Compose 层 BrandedSplash overlay：日出动画，淡出后自移除
 *
 * 本类用 JVM 直接解析 res / 源码文本，把不变量锁住，防止回归到
 * "setKeepOnScreenCondition 纯定时 + exit listener 不 remove()" 的卡死写法。
 */
class SplashSpecTest {

    @Test
    fun `splash logo drawable is static sunrise with sun and horizon centered`() {
        val drawable = readResource("res/drawable/ic_splash_logo.xml")
        // 日出两元素：太阳 + 地平线
        assertTrue(drawable.contains("""android:name="sun""""),
            "ic_splash_logo.xml 必须声明 sun path（品牌色太阳）")
        assertTrue(drawable.contains("""android:name="horizon""""),
            "ic_splash_logo.xml 必须声明 horizon path（地平线弧）")
        // 太阳圆心必须是 (54,52) — 居中于 viewport(108)，否则会被系统圆形 mask 裁切
        assertTrue(drawable.contains("M54,52"),
            "太阳圆心必须在 (54,52) 附近（viewport 中心），防止被 splash 圆形 mask 裁掉")
    }

    @Test
    fun `no animated-vector or legacy exit anim remains in resources`() {
        // API 31+ 上 AVD 只播一次、keep-condition 重评估依赖 view 重绘 —— 回归防护
        assertFalse(resourceExists("res/drawable/ic_splash_logo_animated.xml"),
            "ic_splash_logo_animated.xml 不应存在：系统侧动画已移到 Compose 层 BrandedSplash")
        assertFalse(resourceExists("res/anim/splash_exit.xml"),
            "res/anim/splash_exit.xml 不应存在：退出动画由 Compose overlay fade 完成")
    }

    @Test
    fun `light splash theme uses static icon and posts to Theme WakePlan`() {
        val themes = readResource("res/values/themes.xml")
        assertTrue(themes.contains("""name="Theme.WakePlan.Splash""""),
            "values/themes.xml 必须定义 Theme.WakePlan.Splash")
        assertTrue(themes.contains("""<item name="postSplashScreenTheme">@style/Theme.WakePlan</item>"""),
            "Theme.WakePlan.Splash 的 postSplashScreenTheme 必须 = @style/Theme.WakePlan")
        assertTrue(themes.contains("""<item name="windowSplashScreenAnimatedIcon">@drawable/ic_splash_logo</item>"""),
            "windowSplashScreenAnimatedIcon 必须指向静态 @drawable/ic_splash_logo（不是 AVD）")
        assertFalse(themes.contains("ic_splash_logo_animated"),
            "主题里不得再引用已删除的 AVD drawable")
    }

    @Test
    fun `night splash theme mirrors light splash config`() {
        val night = readResource("res/values-night/themes.xml")
        assertTrue(night.contains("""name="Theme.WakePlan.Splash""""),
            "values-night/themes.xml 必须定义 Theme.WakePlan.Splash")
        assertTrue(night.contains("""<item name="postSplashScreenTheme">@style/Theme.WakePlan</item>"""))
        assertTrue(night.contains("""<item name="windowSplashScreenAnimatedIcon">@drawable/ic_splash_logo</item>"""))
        assertTrue(night.contains("@color/splash_bg"),
            "夜间 splash 背景必须仍是 @color/splash_bg（Wake Plan 强制深色）")
    }

    @Test
    fun `branded splash first frame matches system splash and self-dismisses via onFinished`() {
        val source = readResource(
            "../java/com/wakeplan/app/ui/splash/BrandedSplash.kt",
            baseFromRes = true,
        )
        assertTrue(source.contains("fun BrandedSplash(onFinished: () -> Unit)"),
            "BrandedSplash 必须暴露 onFinished 回调（淡出后自移除 overlay）")
        assertTrue(source.contains("onFinished()"),
            "BrandedSplash 必须在淡出动画结束后调用 onFinished()，否则 overlay 永久遮挡")
        // 日出视觉两要素
        assertTrue(source.contains("drawCircle"), "BrandedSplash 必须绘制太阳（drawCircle）")
        assertTrue(source.contains("drawArc"), "BrandedSplash 必须绘制地平线弧（drawArc）")
        // 必须用 app 名作为标题
        assertTrue(source.contains("R.string.app_name"),
            "BrandedSplash 标题必须用 stringResource(R.string.app_name)")

        // 衔接护栏：首帧（progress=0）logo 必须完整不透明，与系统 splash 静态画面一致。
        // 禁止回归到"Canvas alpha=progress / scaleX 展开入场"——那会让系统 splash 移除后
        // 出现空白帧，用户感知为"停 0.5s 后动画才重新开始"。
        // "alpha = progress" 后不跟 ".value" 的才是旧写法（标题淡入用的是 progress.value，合法）
        assertFalse(Regex("alpha = progress(?!\\.value)").containsMatchIn(source),
            "logo 首帧必须不透明：不得把徽标 alpha 直接绑定到 progress（标题淡入用 progress.value）")
        assertFalse(source.contains("scaleX = 0.6f"),
            "不得用 scaleX 展开入场：系统 splash 已是完成态构图，首帧必须与之相同")
        assertTrue(source.contains("translationY"),
            "logo 必须用 translationY 上移作为衔接动画（首帧 y=0 与系统 splash 同位）")
    }

    @Test
    fun `main activity does not use keep-on-screen condition or exit animation listener`() {
        // 卡死回归防护：纯定时 keep-condition 不会被重评估；exit listener 不 remove() 会永久遮挡
        val source = readResource(
            "../java/com/wakeplan/app/MainActivity.kt",
            baseFromRes = true,
        )
        // 匹配真实调用形式（".xxx"），KDoc 警告注释里的裸词不算
        assertFalse(source.contains(".setKeepOnScreenCondition"),
            "MainActivity 不得使用 setKeepOnScreenCondition 做定时（重评估依赖 view 重绘，会卡死）")
        assertFalse(source.contains(".setOnExitAnimationListener"),
            "MainActivity 不得设置 setOnExitAnimationListener（API 31+ 必须手动 remove，易漏）")
        assertFalse(source.contains("SplashController"),
            "SplashController 已删除，不应再被引用")
        assertTrue(source.contains("BrandedSplash(onFinished"),
            "MainActivity 必须挂载 BrandedSplash overlay 并传 onFinished 回调")
        assertTrue(source.contains("installSplashScreen()"),
            "仍需 installSplashScreen() 完成 postSplashScreenTheme 主题切换")
    }

    @Test
    fun `application theme stays the App theme in manifest`() {
        val manifest = readResource("AndroidManifest.xml")
        val appLine = manifest.lines().first { it.contains("android:theme=") }
        assertTrue(appLine.contains("@style/Theme.WakePlan\""),
            "application 的 android:theme 必须是 Theme.WakePlan（避免后端组件被 splash 主题染色）")
    }

    // region 资源读取工具
    // JVM 单测 cwd 通常是 app/，资源在 src/main/ 下；
    // baseFromRes=true 时 relativePath 相对 src/main/res/ 解析（用于跳到 src/main/java/）
    private fun readResource(relativePath: String, baseFromRes: Boolean = false): String {
        val cwd = System.getProperty("user.dir") ?: "."
        val bases = if (baseFromRes) {
            listOf("src/main/res/$relativePath", "app/src/main/res/$relativePath")
        } else {
            listOf("src/main/$relativePath", "app/src/main/$relativePath")
        }
        for (path in bases) {
            val file = java.io.File(cwd, path)
            if (file.exists()) return file.readText(Charsets.UTF_8)
        }
        throw AssertionError("找不到资源文件（相对 cwd=$cwd）：\n  候选 = $bases")
    }

    private fun resourceExists(relativePath: String): Boolean {
        val cwd = System.getProperty("user.dir") ?: "."
        return listOf("src/main/$relativePath", "app/src/main/$relativePath")
            .any { java.io.File(cwd, it).exists() }
    }
    // endregion
}
