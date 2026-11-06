package com.campus.schedule.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
 * 启动画面（v2.4 新增）。
 *
 * ## 为什么要它
 *
 * 冷启动时从「点图标」到「第一帧内容」之间有系统白屏。原来靠
 * `windowBackground` 换成纸色来遮，但遮住的是**空白**，不是**交代**：
 * 眼睛看到一块纸色突然换成列表，说不上错，但也没有「应用正在启动」的信号。
 *
 * ## 动画怎么设计
 *
 * 只有三段，全部是「线条生长」，和全站的线描语言一致：
 *
 * 1. 顶部一条 56rpx 的短横线从 0 画到满宽（220ms）；
 * 2. 应用名「校园日程」从下往上浮起 12rpx 并淡入（180ms，比横线晚 90ms）；
 * 3. 副标题「今天，从容一点」跟着淡入（160ms，再晚 90ms）。
 *
 * 三段加起来约 500ms，然后整块淡出交给主界面。
 *
 * ## 为什么不用系统 SplashScreen API
 *
 * `androidx.core:core-splashscreen` 需要新增依赖，且它规定的是「图标居中缩放」，
 * 和这里要的线描风格不符。用 Compose 自己画一层覆盖视图，代价只是多一个
 * 组合，且不引入任何依赖。
 */
@Composable
fun LaunchOverlay(onFinished: () -> Unit) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val reduced = LocalReducedMotion.current
    // 像素密度：graphicsLayer 里 translationY 的单位是 px，rpx 要先换算
    val titleOffsetPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        m.rpx(12).toPx()
    }

    // 整块覆盖层的淡出进度（0 → 1 表示「已经淡出到透明」）
    val fade = remember { Animatable(0f) }
    // 内容入场
    val lineGrow = remember { Animatable(0f) }
    val titleIn = remember { Animatable(0f) }
    val subIn = remember { Animatable(0f) }
    // 是否还在显示：淡出结束后置 false，把节点整个移除
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (reduced) {
            // 减弱动效：两段都直接跳到终值，只保留一次极短的停留
            lineGrow.snapTo(1f)
            titleIn.snapTo(1f)
            subIn.snapTo(1f)
            delay(180)
            fade.snapTo(1f)
            visible = false
            onFinished()
            return@LaunchedEffect
        }
        // 两段并行：横线生长 + 两行文字错峰浮起
        launch {
            delay(90)
            titleIn.animateTo(1f, tween(180, easing = Motion.EaseSoft))
            delay(90)
            subIn.animateTo(1f, tween(160, easing = Motion.EaseSoft))
        }
        lineGrow.animateTo(1f, tween(220, easing = Motion.EaseSoft))
        // 入场做完先放开主界面（它自己也在做 scene transition），
        // 再停留一小会儿，然后整块淡出。
        onFinished()
        delay(420)
        fade.animateTo(1f, tween(Motion.DurExit, easing = Motion.EaseInOut))
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
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 一条会生长的横线：启动动画唯一「动」的元素
            Box(
                modifier = Modifier
                    .width(m.rpx(56))
                    .height(m.rpx(4))
                    .graphicsLayer {
                        scaleX = lineGrow.value
                        alpha = lineGrow.value
                    }
                    .background(p.brand, RoundedCornerShape(m.rpx(2)))
            )
            Text(
                text = "校园日程",
                style = m.h1.copy(color = Tokens.Ink, fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .padding(top = m.rpx(28))
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
                    .padding(top = m.rpx(8))
                    .graphicsLayer { alpha = subIn.value },
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}