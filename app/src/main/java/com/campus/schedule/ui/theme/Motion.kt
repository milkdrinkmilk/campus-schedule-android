package com.campus.schedule.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * 动效 token —— 与小程序 `miniprogram/styles/motion.wxss` 对齐。
 *
 * 曲线直接沿用小程序的 cubic-bezier 数值（Compose 的 [CubicBezierEasing] 与 CSS
 * `cubic-bezier()` 语义一致），因此安卓端与小程序端的运动观感完全相同。
 *
 * ## 性能铁律（这版重写的重点）
 *
 * 动效只允许改 **transform / alpha**，且必须写在 `graphicsLayer { }` 的 lambda 里
 * （延迟读取，动画期间不触发重组）。凡是会引发重新布局的属性（padding / size /
 * offset / 高度）一律不许动画 —— 这是上一版「动效卡顿」的主因。
 */
object Motion {

    // ---- 时长（ms），与 --dur-* 一致 ----
    const val DurPress = 220
    const val DurFast = 260
    const val DurBase = 300
    const val DurSheet = 300
    const val DurExit = 240
    const val DurToastEnter = 280
    const val DurToastExit = 240
    const val DurTab = 260
    const val DurWake = 260

    /**
     * 页面级横向转场（v2.1）。
     *
     * 比 [DurTab] 长一点：三个 Tab 之间做的是「同层横移 + 交叉淡入」，
     * 位移幅度大（整屏的 1/8），太快会显得硬。
     */
    const val DurPage = 320

    /** 覆盖层（提示框 / 弹层）进出场。 */
    const val DurOverlay = 280

    /** 错位入场步进（ms）。 */
    const val StaggerStep = 90

    /** 参与错位入场的最大条目数，超出后不再累加延迟。 */
    const val StaggerMax = 8

    // ---- 曲线 ----
    /** 带回弹的入场曲线（cubic-bezier(0.34, 1.56, 0.64, 1)）。 */
    val EaseSpring: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val EaseOut: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val EaseInOut: Easing = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
    val EaseExit: Easing = CubicBezierEasing(0.55f, 0f, 1f, 0.45f)
    val EaseSoft: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    // ---- 缩放幅度 ----
    const val ScalePress = 0.92f
    const val ScaleCard = 0.95f
    const val ScaleEnter = 0.96f
    const val ScaleEnterSm = 0.94f
    const val ScaleWake = 0.94f

    // ---- 位移幅度（rpx，由 Metrics 换算） ----
    const val EnterY = 40
    const val EnterYSm = 32

    // ---- 常用动画规格 ----

    /**
     * 按压（v2.1：不再缩放，只做颜色/透明度过渡，所以用柔和曲线而不是回弹）。
     */
    fun press(reduced: Boolean): FiniteAnimationSpec<Float> =
        if (reduced) tween(0) else tween(160, easing = EaseSoft)

    /** 颜色 / 透明度过渡（260ms，柔和）。 */
    fun fast(reduced: Boolean): FiniteAnimationSpec<Float> =
        if (reduced) tween(0) else tween(DurFast, easing = EaseSoft)

    /** 页面级过渡（300ms）。 */
    fun base(reduced: Boolean): FiniteAnimationSpec<Float> =
        if (reduced) tween(0) else tween(DurBase, easing = EaseSpring)

    /**
     * Tab 切换（260ms，**不过冲**）。
     *
     * v2.1 起不再用 [EaseSpring]：9.78% 的过冲会让指示器冲过头再弹回，
     * 在底部这种窄条上看起来像"抽搐"。改用减速曲线，一次到位。
     */
    fun tab(reduced: Boolean): FiniteAnimationSpec<Float> =
        if (reduced) tween(0) else tween(DurTab, easing = EaseInOut)

    /** 页面横向转场（320ms，柔和减速）。 */
    fun page(reduced: Boolean): FiniteAnimationSpec<Float> =
        if (reduced) tween(0) else tween(DurPage, easing = EaseSoft)

    /** 覆盖层进出（280ms）。 */
    fun overlay(reduced: Boolean): FiniteAnimationSpec<Float> =
        if (reduced) tween(0) else tween(DurOverlay, easing = EaseSoft)

    /** 退场（240ms）。 */
    fun exit(reduced: Boolean): FiniteAnimationSpec<Float> =
        if (reduced) tween(0) else tween(DurExit, easing = EaseExit)

    /** 颜色过渡（260ms）。`animateColorAsState` 需要 Color 专用规格。 */
    fun color(reduced: Boolean): FiniteAnimationSpec<androidx.compose.ui.graphics.Color> =
        if (reduced) {
            tween(0)
        } else {
            tween(DurFast, easing = EaseSoft)
        }

    /** FAB 弹性出现。 */
    fun bouncy(reduced: Boolean): FiniteAnimationSpec<Float> =
        if (reduced) {
            tween(0)
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
        }
}
