package com.campus.schedule.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.campus.schedule.ui.theme.LocalMetrics
import com.campus.schedule.ui.theme.LocalPalette
import com.campus.schedule.ui.theme.LocalReducedMotion
import com.campus.schedule.ui.theme.Motion
import com.campus.schedule.ui.theme.Tokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 启动画面（v2.4.1 重做）。
 *
 * ## v2.4 的两个问题
 *
 * 1. **太短**：入场约 500ms 就结束，用户还没看清字就切走了。
 * 2. **卡顿**：真正的元凶是「首帧组合风暴」——`onFinished()` 一触发，
 *    `SceneTransition.enabled` 翻转 + 主界面整棵树首次组合 + Room 首查返回
 *    触发全页重组，全部挤在同几帧里；而覆盖层自己的淡出恰好撞上这段时间，
 *    看上去就是「动画在最后掉帧」。另外横线用 `scaleX` 生长，缩放一条
 *    4rpx 高的矩形会连圆角一起拉扁，观感也像卡了一下。
 *
 * ## v2.4.1 的做法
 *
 * * **主界面先预热再交接**：`LaunchedEffect(Unit)` 第一帧就调 `onFinished()`
 *   （SceneTransition 开始收拢、页面在主界面下完整组合），覆盖层的线条
 *   同时开始画 —— 两条时间线并行，等线条画完，页面早已稳定，淡出时不再
 *   有任何重组抢帧。
 * * **线条改成描边生长**：和空态插画同一套语言（Path + PathMeasure），
 *   画的是「一张日程卡」的轮廓 —— 外框 + 表头分隔线 + 三条待办短线。
 *   生长过程是逐段 stroke，不是缩放，不会有形变毛刺。
 * * **节奏放慢但不拖沓**：线条约 780ms，文字错峰浮起，整块停留到约
 *   1150ms 才开始淡出，全程约 1400ms。
 * * 所有动画只动 `graphicsLayer` / Canvas 绘制内容，零布局参与。
 */
@Composable
fun LaunchOverlay(onFinished: () -> Unit) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val reduced = LocalReducedMotion.current
    val titleOffsetPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        m.rpx(14).toPx()
    }

    // 覆盖层整体淡出（0 → 1 = 完全透明）
    val fade = remember { Animatable(0f) }
    // 线条生长 0..1
    val lineGrow = remember { Animatable(0f) }
    val titleIn = remember { Animatable(0f) }
    val subIn = remember { Animatable(0f) }
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (reduced) {
            onFinished()
            delay(200)
            visible = false
            return@LaunchedEffect
        }
        // ① 立刻交接：主界面从这一帧开始组合/收拢，与线条并行预热
        onFinished()
        // ② 让出一帧，确保 SceneTransition 的首次收拢不与本组件的
        //    第一次绘制抢同一帧（这是 v2.4 「卡顿」的直接来源之一）
        delay(32)
        launch {
            delay(160)
            titleIn.animateTo(1f, tween(220, easing = LinearOutSlowInEasing))
            delay(120)
            subIn.animateTo(1f, tween(200, easing = LinearOutSlowInEasing))
        }
        lineGrow.animateTo(1f, tween(780, easing = Motion.EaseSoft))
        // ③ 线条完成后停一会儿再退场；此时页面已预热完毕，
        //    淡出期间没有任何重组，不会再掉帧。
        //    （MainActivity 会在约 1300ms 时把本组件从树里移除，
        //     与这里的淡出结束时间基本对齐。）
        delay(320)
        fade.animateTo(1f, tween(260, easing = Motion.EaseInOut))
        visible = false
    }

    if (!visible) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - fade.value }
            .background(Tokens.Paper),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 一张正在被「画出来」的日程卡轮廓
            Canvas(
                modifier = Modifier
                    .width(m.rpx(120))
                    .height(m.rpx(96))
            ) {
                val grow = lineGrow.value
                val w = size.width
                val h = size.height
                val strokeW = m.rpx(3).toPx()

                val left = w * 0.08f
                val top = h * 0.08f
                val right = w * 0.92f
                val bottom = h * 0.92f
                val radius = (bottom - top) * 0.18f

                // 外框：占生长的前 55%
                val frame = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = left, top = top, right = right, bottom = bottom,
                            cornerRadius = CornerRadius(radius, radius)
                        )
                    )
                }
                drawGrowing(frame, (grow / 0.55f).coerceIn(0f, 1f), p.brand.copy(alpha = 0.5f), strokeW)

                // 表头分隔线：35%~60%
                val headerY = top + (bottom - top) * 0.30f
                val header = Path().apply {
                    moveTo(left + (right - left) * 0.12f, headerY)
                    lineTo(right - (right - left) * 0.12f, headerY)
                }
                drawGrowing(header, ((grow - 0.35f) / 0.25f).coerceIn(0f, 1f), p.brand, strokeW)

                // 三条待办短线：55%/70%/85% 起步，每条 0.2 的窗口
                val x0 = left + (right - left) * 0.12f
                val rows = listOf(0.50f to 0.72f, 0.64f to 0.88f, 0.78f to 0.58f)
                rows.forEachIndexed { i, (yFrac, lenFrac) ->
                    val y = top + (bottom - top) * yFrac
                    val line = Path().apply {
                        moveTo(x0, y)
                        lineTo(x0 + (right - left - (x0 - left)) * lenFrac, y)
                    }
                    val begin = 0.55f + i * 0.15f
                    drawGrowing(
                        line,
                        ((grow - begin) / 0.2f).coerceIn(0f, 1f),
                        Tokens.InkMuted.copy(alpha = 0.55f),
                        strokeW
                    )
                }
            }

            Text(
                text = "校园日程",
                style = m.h1.copy(color = Tokens.Ink, fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .padding(top = m.rpx(32))
                    .graphicsLayer {
                        alpha = titleIn.value
                        translationY = (1f - titleIn.value) * titleOffsetPx
                    },
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = "今天，从容一点",
                style = m.meta.copy(color = Tokens.InkMuted),
                modifier = Modifier
                    .padding(top = m.rpx(10))
                    .graphicsLayer { alpha = subIn.value },
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
