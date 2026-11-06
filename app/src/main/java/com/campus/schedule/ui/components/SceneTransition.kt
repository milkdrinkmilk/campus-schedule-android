package com.campus.schedule.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.campus.schedule.ui.theme.LocalReducedMotion
import com.campus.schedule.ui.theme.Motion

/**
 * 主界面的「交接」包装（v2.4 新增，配合 [LaunchOverlay]）。
 *
 * ## 解决什么问题
 *
 * 启动画面消失的那一瞬间，如果主界面是「刚从零开始组合」的，用户会看到
 * 纸色底板 → 列表突然出现。哪怕同色，也还是「凭空出现」。
 *
 * 正确的做法是：启动画面还在的时候，主界面**已经在下面组合好了**，
 * 只是稍微缩一点、稍微淡一点；启动画面淡出的同时，它同步收拢到位。
 * 两个动画时间完全重叠，视觉上是一次连续的运动，而不是两次切换。
 *
 * ## 实现
 *
 * `enabled` 由调用方（MainActivity）在启动覆盖层通知「入场结束」时翻转为 true。
 * 翻转前 `progress = 0`：内容缩放 0.985、透明度 0.92（**不是 0** ——
 * 底下必须能透出一点内容，否则又变成白纸）。
 * 翻转后 `progress` 跑到 1：缩放归 1、透明度归 1。
 *
 * 只改 `graphicsLayer` 的 scaleX/scaleY/alpha，不触发布局，也不引发重组 ——
 * progress 在 graphicsLayer lambda 里读取，值变化只走重绘。
 *
 * 系统开启「减弱动画」时直接渲染内容，不做缩放。
 */
@Composable
fun SceneTransition(enabled: Boolean, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    if (reduced) {
        content()
        return
    }

    val progress = remember { Animatable(0f) }

    LaunchedEffect(enabled) {
        if (enabled) {
            progress.animateTo(1f, tween(Motion.DurPage, easing = Motion.EaseSoft))
        }
    }

    // v2.5：去掉 scaleX/scaleY。
    // 120Hz 设备上，每帧对整棵子树做缩放会触发离屏渲染 + 重绘，肉眼可见掉帧。
    // 只保留 alpha 过渡（纯合成层操作，GPU 友好），视觉上仍然有「内容从雾中浮现」
    // 的入场感，但不再卡。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = 0.92f + 0.08f * progress.value
            }
    ) {
        content()
    }
}