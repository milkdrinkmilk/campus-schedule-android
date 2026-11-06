package com.campus.schedule.ui.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.campus.schedule.ui.theme.LocalMetrics
import com.campus.schedule.ui.theme.LocalReducedMotion
import com.campus.schedule.ui.theme.Motion
import com.campus.schedule.ui.theme.Tokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlin.math.min

/**
 * 动效基建。
 *
 * ## 为什么上一版会卡
 *
 * 1. 用 `by animateFloatAsState(...)` 取值 —— `by` 会在**组合阶段**读 `.value`，
 *    动画每一帧都触发一次重组，整棵列表跟着重算。
 * 2. 动画改的是 `padding` / `height` / `offset` 这类**布局属性**，
 *    每帧触发重新测量与布局。
 * 3. 条目入场动画挂在 `LaunchedEffect` + `delay` 上，滚动时反复重启。
 *
 * ## 这一版的做法
 *
 * * 所有动画值都写成 `State<Float>`，**只在 `graphicsLayer { }` 的 lambda 里读**。
 *   Compose 对 lambda 内的状态读取是「延迟读取」——动画帧只触发**重绘**，
 *   不触发重组，也不触发重新布局。这是 Compose 官方推荐的动画写法。
 * * 所有变换都是 transform / alpha，不碰布局属性。
 * * 条目入场用 [EnterTracker] 记账，滚动出去再回来**不会重播**。
 */

// ---------------------------------------------------------------------------
// 按压
// ---------------------------------------------------------------------------

/** 记住一个 [MutableInteractionSource]，让按压缩放与涟漪共用同一份交互状态。 */
@Composable
fun rememberPressInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }

/**
 * 按压反馈。
 *
 * v2.1 起**默认不缩放**（[scale] = 1f）：用户反馈「点击时有阴影跳动、很割裂」。
 * 现行方案是不变量法 —— 按下只叠加一层极浅的底色（见 `PressHighlight`），
 * 几何完全不动，不会出现"按下 → 缩起 → 弹回"三次视觉跳变。
 *
 * 仍然保留 scale 参数：需要明显反馈的独立按钮可以显式传 `Motion.ScalePress`，
 * 但**卡片、列表行、Tab 一律用默认值**。
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    scale: Float = 1f,
    enabled: Boolean = true
): Modifier {
    val pressed = interactionSource.collectIsPressedAsState().value
    val reduced = LocalReducedMotion.current
    // 注意：这里**不能**用 `by`，否则每帧都会重组。用 State + 延迟读取才是无卡顿写法。
    val factor = animateFloatAsState(
        targetValue = if (pressed && enabled) scale else 1f,
        animationSpec = Motion.press(reduced),
        label = "pressScale"
    )
    return this.graphicsLayer {
        scaleX = factor.value
        scaleY = factor.value
    }
}

/**
 * 按压高亮（v2.1 的点击反馈）。
 *
 * 按下时叠一层 [Tokens.Line] 级别的浅色，松手淡出。和缩放不同的是：
 * **几何不动**，所以连续的快速点击不会让卡片"跳舞"。
 *
 * @param shape 高亮的形状，通常与卡片自身圆角一致
 */
@Composable
fun Modifier.pressHighlight(
    interactionSource: MutableInteractionSource,
    shape: Shape,
    color: Color = Tokens.Ink.copy(alpha = 0.06f),
    enabled: Boolean = true
): Modifier {
    val pressed = interactionSource.collectIsPressedAsState().value
    val reduced = LocalReducedMotion.current
    val alpha = animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 0f,
        animationSpec = tween(durationMillis = 90, easing = Motion.EaseSoft),
        label = "pressHighlight"
    )
    return this.drawWithContent {
        drawContent()
        val a = alpha.value
        if (a > 0.001f) {
            drawPath(
                path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) },
                color = color.copy(alpha = color.alpha * a),
                style = Fill
            )
        }
    }
}

/** 带涟漪的点击。放在 [pressScale] 之后、形状裁切之内，涟漪才会被正确裁切。 */
@Composable
fun Modifier.rippleClick(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    // 用 Material3 的 LocalIndication 而不是硬编码 ripple()：
    // 后者在 Material3 1.2 起已不再是公开 API。
    val indication = LocalIndication.current
    return this.clickable(
        interactionSource = interactionSource,
        enabled = enabled,
        indication = indication,
        onClick = onClick
    )
}

/** 无涟漪的点击 —— 用于卡片内部的小图标，避免出现两层水波纹。 */
@Composable
fun Modifier.clickableNoRipple(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = this.clickable(
    interactionSource = interactionSource,
    indication = null,
    enabled = enabled,
    onClick = onClick
)

// ---------------------------------------------------------------------------
// 阴影
// ---------------------------------------------------------------------------

/**
 * 小程序的淡蓝阴影（v2.1 起基本停用，保留给浮起态）。
 *
 * `--shadow-1: 0 4rpx 16rpx rgba(37, 99, 235, 0.05)`
 * `--shadow-2: 0 12rpx 32rpx rgba(37, 99, 235, 0.08)`
 * `--shadow-3: 0 24rpx 64rpx rgba(11, 43, 102, 0.12)`
 *
 * 注意：**必须排在 `clip` 之前**，否则阴影会被裁掉等于没有。
 *
 * @param level 0 = 无阴影（v2.1 的默认）。应用主色板是浅蓝纸面，阴影落在上面
 *   只会糊一层灰边，让线条变脏 —— 用户要求「去阴影、做细线条」后，
 *   所有卡片改用 `2rpx` 线条 + 纸色分层来表达层次，阴影仅在必须浮起的
 *   模态卡片上用 [level] = 2。
 */
@Composable
fun Modifier.appShadow(
    level: Int = 0,
    shape: Shape
): Modifier {
    if (level <= 0) return this
    val m = LocalMetrics.current
    val (elevation, color) = when (level) {
        1 -> m.rpx(4) to Color(0x0D2563EB)
        2 -> m.rpx(12) to Color(0x142563EB)
        else -> m.rpx(24) to Color(0x1F0B2B66)
    }
    // clip = false：调用方会自己接 .clip(shape)，
    // 若这里再裁一次，阴影会被切掉等于没有。
    return this.shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = color,
        spotColor = color,
        clip = false
    )
}

// ---------------------------------------------------------------------------
// 入场
// ---------------------------------------------------------------------------

/**
 * 入场记账器：记录哪些条目已经播过入场动画。
 *
 * 小程序的 `stagger-item` 在每次渲染时都会重播，滚动列表时视觉上很跳。
 * 这里改成「只播一次」——条目滚出屏幕再回来时保持原样。
 */
class EnterTracker {
    private val played = mutableSetOf<String>()

    /** 返回 true 表示本次应当播放入场动画（并记账），false 表示已经播过。 */
    fun shouldPlay(key: String): Boolean {
        if (played.contains(key)) return false
        played.add(key)
        return true
    }
}

@Composable
fun rememberEnterTracker(): EnterTracker = remember { EnterTracker() }

/**
 * 错位入场：上浮 + 轻微缩放，间隔 [Motion.StaggerStep] 毫秒。
 *
 * 位移与缩放都写在 `graphicsLayer` 里，不触发布局。
 */
@Composable
fun Modifier.staggerEnter(
    tracker: EnterTracker?,
    key: String,
    index: Int,
    enabled: Boolean = true
): Modifier {
    val reduced = LocalReducedMotion.current
    val m = LocalMetrics.current
    val density = LocalDensity.current
    val enterYPx = with(density) { m.rpx(Motion.EnterYSm).toPx() }

    val progress = remember { Animatable(if (reduced || !enabled) 1f else 0f) }

    LaunchedEffect(key, enabled) {
        if (reduced || !enabled) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        if (tracker != null && !tracker.shouldPlay(key)) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        delay(Motion.StaggerStep * min(index, Motion.StaggerMax).toLong())
        progress.animateTo(1f, Motion.base(reduced))
    }

    return this.graphicsLayer {
        val p = progress.value
        // ⚠️ 必须钳制：入场曲线是带回弹的 cubic-bezier(0.34, 1.56, 0.64, 1)，
        // 输出最大到 1.0978。Compose 的 TweenSpec 只钳制**输入** fraction，
        // 回弹发生在 easing.transform() 之后，所以 progress 会真的超过 1。
        // alpha > 1 传到 Paint.setAlpha 属于「未定义渲染」。
        // （CSS 那边由浏览器自动把 opacity 钳到 [0,1]，所以小程序看不出来。）
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p.coerceIn(0f, 1f)) * enterYPx
        val s = Motion.ScaleEnterSm + (1f - Motion.ScaleEnterSm) * p
        scaleX = s
        scaleY = s
    }
}

// ---------------------------------------------------------------------------
// 页面转场：息屏 → 亮屏
// ---------------------------------------------------------------------------

/**
 * 页面入场（息屏亮屏）。
 *
 * 对应小程序的 `page-wake`：先压暗缩小（0.94），再被点亮并回弹（1.015），最后落回 1。
 */
@Composable
fun Modifier.wakeIn(reduced: Boolean = LocalReducedMotion.current): Modifier {
    val progress = remember { Animatable(if (reduced) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (reduced) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.animateTo(1f, Motion.base(reduced))
    }

    return this.graphicsLayer {
        val p = progress.value
        alpha = if (p < 0.12f) p / 0.12f else 1f
        val scale = when {
            p < 0.38f -> Motion.ScaleWake + (1.015f - Motion.ScaleWake) * (p / 0.38f)
            else -> 1.015f + (1f - 1.015f) * ((p - 0.38f) / 0.62f)
        }
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 二级页面入场（上浮 + 轻微缩放）。
 *
 * 对应小程序的 `.page-enter`（`@keyframes page-in`）：
 * 从 `opacity:0 / translateY(40rpx) / scale(0.96)` 过渡到静止态。
 *
 * 与 [wakeIn] 的区别很关键：
 * * [wakeIn] = 息屏亮屏（Tab 页专用，必须配合全屏遮罩）；
 * * [pageEnter] = 上浮淡入（二级页专用）。
 *
 * 之前编辑页也走了 [wakeIn]，导致「打开编辑页 = 屏幕先暗一下再亮」，
 * 视觉上就是一次莫名其妙的闪屏。
 */
@Composable
fun Modifier.pageEnter(reduced: Boolean = LocalReducedMotion.current): Modifier {
    val m = LocalMetrics.current
    val density = LocalDensity.current
    val enterYPx = with(density) { m.rpx(Motion.EnterY).toPx() }

    val progress = remember { Animatable(if (reduced) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (reduced) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.animateTo(1f, Motion.base(reduced))
    }

    return this.graphicsLayer {
        val p = progress.value.coerceIn(0f, 1f)
        alpha = p
        translationY = (1f - p) * enterYPx
        val s = Motion.ScaleEnter + (1f - Motion.ScaleEnter) * p
        scaleX = s
        scaleY = s
    }
}

/**
 * 页面横向转场（v2.1 取代「息屏 → 亮屏」）。
 *
 * ## 为什么要换掉旧方案
 *
 * 旧方案是「切 Tab 时先盖一层深墨色（#10233F）全屏遮罩再淡出」。
 * 真机上表现为：点 Tab → **整屏黑一下** → 再亮起来。用户直接反馈「闪屏」。
 * 原因有两层：① 遮罩本身是深色，淡出过程就是一次全屏亮度跳变；
 * ② 新页面同时还在从 alpha=0 淡入，两段动画叠在一起，中间必然出现
 * 一个「既不是旧页也不是新页」的空帧。
 *
 * ## 现在的做法
 *
 * 同层横移 + 交叉淡入，全屏亮度恒定：
 * * 进场页从 [fromRight]/[fromLeft] 方向的 1/8 屏宽处滑入，伴随 alpha 0.4 → 1；
 * * 出场页向反方向滑出 1/12 屏宽并淡出；
 * * 只动 transform 和 alpha，不碰任何布局属性，全程零重组。
 *
 * @param fromRight true = 新页从右侧进入（往"后"翻），false = 从左侧进入（往"前"翻）
 */
@Composable
fun enterPageTransition(fromRight: Boolean, reduced: Boolean = LocalReducedMotion.current): EnterTransition {
    if (reduced) return EnterTransition.None
    val width = LocalConfiguration.current.screenWidthDp
    // 幅度刻意做小：太大会像「整页被推走」，这里只是让人感觉到方向。
    val offset = width / 8
    val sign = if (fromRight) 1 else -1
    return slideInHorizontally(animationSpec = tween(Motion.DurPage, easing = Motion.EaseSoft)) { sign * offset } +
        fadeIn(tween(Motion.DurPage, easing = Motion.EaseSoft))
}

/** [enterPageTransition] 的对称出场。 */
@Composable
fun exitPageTransition(fromRight: Boolean, reduced: Boolean = LocalReducedMotion.current): ExitTransition {
    if (reduced) return ExitTransition.None
    val width = LocalConfiguration.current.screenWidthDp
    val offset = width / 12
    val sign = if (fromRight) 1 else -1
    return slideOutHorizontally(animationSpec = tween(Motion.DurPage, easing = Motion.EaseInOut)) { -sign * offset } +
        fadeOut(tween(Motion.DurPage))
}

/**
 * 二级页（编辑页）的进场：从下往上推入 + 淡入。
 *
 * 和横向转场区分开，让人一眼看出这是「叠上来的一层」，不是同级 Tab。
 */
@Composable
fun enterSheetTransition(reduced: Boolean = LocalReducedMotion.current): EnterTransition {
    if (reduced) return EnterTransition.None
    return slideInVertically(animationSpec = tween(Motion.DurPage, easing = Motion.EaseSoft)) { it / 14 } +
        fadeIn(tween(Motion.DurPage, easing = Motion.EaseSoft))
}

/** [enterSheetTransition] 的对称出场。 */
@Composable
fun exitSheetTransition(reduced: Boolean = LocalReducedMotion.current): ExitTransition {
    if (reduced) return ExitTransition.None
    return slideOutVertically(animationSpec = tween(Motion.DurExit, easing = Motion.EaseInOut)) { it / 10 } +
        fadeOut(tween(Motion.DurExit))
}

/**
 * 息屏遮罩的透明度曲线（已废弃，仅为兼容旧单测保留）。
 *
 * v2.1 起 Tab 切换不再使用深色遮罩，见 [enterPageTransition]。
 *
 * ⚠️ 末尾必须 `coerceIn(0f, 1f)`：遮罩动画用的是带回弹的 `--ease-out`
 * （cubic-bezier(0.34, 1.56, 0.64, 1)，与小程序同值），进度会冲到 1.0978，
 * 直接算会得到 **负 alpha**（-0.098），传到 `Paint.setAlpha(-25)` 属于未定义渲染。
 * CSS 的 opacity 由浏览器自动钳到 [0,1]，所以小程序那边看不到这个问题。
 */
@Deprecated("v2.1 已移除深色遮罩，改用 enterPageTransition")
fun veilOpacity(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return when {
        p < 0.45f -> 1f - 0.45f * (p / 0.45f)
        else -> 0.55f * (1f - (p - 0.45f) / 0.55f)
    }
}

// ---------------------------------------------------------------------------
// 其它
// ---------------------------------------------------------------------------

/**
 * 每 [intervalMs] 毫秒刷新一次的「现在」。
 *
 * 用于周视图的当前时间线。用 Flow + collectAsState 实现，
 * 只在真正到期时重组一次，不会在动画帧里反复触发重组。
 */
@Composable
fun rememberNowMillis(intervalMs: Long = 30_000L): Long {
    val flow = remember(intervalMs) {
        flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(intervalMs)
            }
        }
    }
    val now by flow.collectAsState(initial = System.currentTimeMillis())
    return now
}

/** 数字滚动动画：从旧值滚到新值。 */
@Composable
fun animateNumberAsState(
    target: Int,
    reduced: Boolean = LocalReducedMotion.current
): Int {
    val animated = androidx.compose.animation.core.animateIntAsState(
        targetValue = target,
        animationSpec = if (reduced) {
            androidx.compose.animation.core.snap()
        } else {
            androidx.compose.animation.core.tween(Motion.DurBase, easing = FastOutSlowInEasing)
        },
        label = "number"
    )
    return animated.value
}

/** 顶/底渐变遮罩，用于列表边缘的柔和过渡。 */
fun Modifier.fadeEdge(
    color: Color = Tokens.Paper,
    height: Dp = 16.dp,
    fromTop: Boolean = true
): Modifier = this.drawBehind {
    val h = height.toPx()
    val w = this.size.width
    val full = this.size.height
    val brush = if (fromTop) {
        Brush.verticalGradient(0f to color, 1f to Color.Transparent)
    } else {
        Brush.verticalGradient(0f to Color.Transparent, 1f to color)
    }
    drawRect(
        brush = brush,
        size = Size(w, h),
        topLeft = if (fromTop) Offset.Zero else Offset(0f, full - h)
    )
}
