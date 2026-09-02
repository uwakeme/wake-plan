package com.wakeplan.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wakeplan.app.R
import kotlinx.coroutines.delay

/**
 * 品牌启动动画（Compose 层）— v0.4
 *
 * 两层 splash 的衔接原则：**Compose 首帧必须与系统 splash 最后一帧是同一个画面**。
 * 系统 splash（Theme.WakePlan.Splash）显示静态日出 logo、居中、无文字；
 * 本 overlay 首帧（progress=0）绘制完全相同的构图 —— logo 居中、完整不透明、无文字，
 * 因此系统 splash 移除时没有任何跳变 / 空白帧。
 *
 * 动画从这个共同状态无缝接续：
 * - 0 → [LOGO_MOVE_MS]：logo 从屏幕正中向上平移 [LOGO_LIFT_DP]，同时下方标题淡入
 * - 停留 [STAY_VISIBLE_MS]
 * - [EXIT_FADE_MS]：整层淡出后回调 [onFinished] 自移除
 *
 * 节奏要求：系统 splash 本身有一段进程启动的静态停留（真机 200~400ms，无法移除），
 * 因此 Compose 接续动画的**前 100ms 必须有肉眼可见的位移**——入场用
 * [LinearOutSlowInEasing]（起步即快），标题淡入比位移更早到位，
 * 避免 FastOutSlowIn 起步过缓造成"又停了 0.5s 才动"的错觉。
 *
 * 【不要】把首帧做成 alpha=0 / 元素未就位再"入场"——
 * 那样系统 splash（完成态 logo）与 Compose 首帧（空白）之间会闪一下，
 * 用户感知为"停 0.5s 后动画才重新开始"。
 *
 * @param onFinished 淡出结束回调 — 调用方把 overlay 从 composition 移除
 */
@Composable
fun BrandedSplash(onFinished: () -> Unit) {
    // 0 = 与系统 splash 一致（logo 居中、无文字）；1 = logo 上移、标题显示
    val progress = remember { Animatable(0f) }
    // 整层退出进度 0→1（alpha 1→0）
    val exit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // 首帧即为 progress=0（与系统 splash 画面衔接），随即开始上移 + 标题淡入。
        // LinearOutSlowIn：起步斜率最大，接续系统 splash 的静态停留后立刻有可见位移。
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = LOGO_MOVE_MS, easing = LinearOutSlowInEasing),
        )
        delay(STAY_VISIBLE_MS)
        exit.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = EXIT_FADE_MS, easing = FastOutSlowInEasing),
        )
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .graphicsLayer { alpha = 1f - exit.value },
        contentAlignment = Alignment.Center,
    ) {
        // 日出徽标：居中；progress 0→1 时整体上移 LOGO_LIFT_DP（首帧 y=0，与系统 splash 同位）
        SunriseMark(
            sunColor = MaterialTheme.colorScheme.primary,
            horizonColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f),
            modifier = Modifier.graphicsLayer {
                translationY = -LOGO_LIFT_DP.dp.toPx() * progress.value
            },
        )

        // 标题：固定在徽标下方，随 progress 淡入。
        // 首帧 alpha=0 不可见（与系统 splash 无文字一致），位置用绝对 offset 不顶推徽标居中。
        // 淡入斜率比位移更陡（× TITLE_ALPHA_LEAD）：文字在前 60% 行程内就全亮，
        // 动画启动后用户第一时间看到"标题浮现 + logo 上移"两个变化，没有静止段。
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = TITLE_OFFSET_DP.dp)
                .graphicsLayer {
                    alpha = (progress.value * TITLE_ALPHA_LEAD).coerceIn(0f, 1f)
                },
        )
    }
}

/**
 * 日出徽标（静态构图）：太阳 + 地平线弧。
 *
 * 与 res/drawable/ic_splash_logo.xml（系统 splash 图标）在屏幕上的绝对渲染尺寸对齐
 * （系统会把 108dp viewport 的图标放大约 2.2 倍渲染，故此处按放大后的 dp 值绘制）：
 * - 画布 160dp，太阳圆心 (80dp, 76dp)、半径 24dp（系统侧 ≈24.4dp）
 * - 地平线弧：圆心 (80dp, 88dp)、半径 53dp 的下半弧（U 形开口朝上；系统侧 ≈53.3dp）
 *
 * 位移 / 透明度由调用方通过 [modifier] 控制，本组件自身不做入场动画。
 */
@Composable
private fun SunriseMark(
    sunColor: Color,
    horizonColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = Modifier.size(160.dp).then(modifier)) {
        val strokeWidth = 3.dp.toPx()

        // 地平线弧：圆心 (80dp, 88dp)、半径 53dp 的下半弧（U 形开口朝上）
        val arcRadius = 53.dp.toPx()
        val arcCenterX = size.width / 2f
        val arcCenterY = 88.dp.toPx()
        drawArc(
            color = horizonColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(arcCenterX - arcRadius, arcCenterY - arcRadius),
            size = Size(arcRadius * 2f, arcRadius * 2f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        // 太阳：圆心 (80dp, 76dp)、半径 24dp
        drawCircle(
            color = sunColor,
            radius = 24.dp.toPx(),
            center = Offset(size.width / 2f, 76.dp.toPx()),
        )
    }
}

/** logo 上移距离（dp）：为标题腾出空间 */
private const val LOGO_LIFT_DP = 56

/** 标题中心相对屏幕中心的下移偏移（dp）：logo 上移后位于其下方 */
private const val TITLE_OFFSET_DP = 80

/** logo 上移 + 标题淡入时长 */
private const val LOGO_MOVE_MS = 480

/** 标题淡入斜率：标题 alpha 取 progress 乘该值（截顶到 1），让文字早于位移到位 */
private const val TITLE_ALPHA_LEAD = 1.6f

/** 标题显示后的停留时长 */
private const val STAY_VISIBLE_MS = 550L

/** 整层淡出时长 */
private const val EXIT_FADE_MS = 300
