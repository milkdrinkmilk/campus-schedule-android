package com.campus.schedule.ui.theme

import android.animation.ValueAnimator
import android.content.Context
import android.database.ContentObserver
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/** 随屏宽缩放的度量集合。 */
val LocalMetrics = staticCompositionLocalOf { Metrics(0.5f) }

/** 是否开启「移除动画」（系统级减弱动效）。 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * 读取系统「移除动画」设置。
 *
 * 三种来源任一命中即视为减弱动效：
 * 1. `Settings.Global.ANIMATOR_DURATION_SCALE == 0`
 * 2. `ValueAnimator.areAnimatorsEnabled() == false`（Android 13+ 开发者选项）
 * 3. 「移除动画」开关被打开（系统内部会关掉 Animator）
 *
 * 通过 ContentObserver 监听设置变化，用户在系统里改完立即生效，不用重启应用。
 */
@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember { mutableStateOf(readReducedMotion(context)) }

    DisposableEffect(context) {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                reduced = readReducedMotion(context)
            }
        }
        try {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer
            )
        } catch (_: Exception) {
            // 某些定制系统不允许读该设置，忽略即可
        }
        onDispose {
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (_: Exception) {
                // 忽略
            }
        }
    }
    return reduced
}

private fun readReducedMotion(context: Context): Boolean {
    return try {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        scale == 0f || !ValueAnimator.areAnimatorsEnabled()
    } catch (_: Exception) {
        false
    }
}

/**
 * 应用主题。
 *
 * 只提供浅色主题，与小程序的蓝白 iOS 观感一致。
 */
@Composable
fun CampusScheduleTheme(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    // 750rpx = 屏幕宽 ⇒ 1rpx 对应的 dp 数。
    // 上下限兜底：超窄屏（<330dp）不至于挤成一团，超宽屏（>480dp）不至于拉得太散。
    val scale = (configuration.screenWidthDp / 750f).coerceIn(0.44f, 0.64f)
    val metrics = remember(scale) { Metrics(scale) }
    val reduced = rememberReducedMotion()

    CompositionLocalProvider(
        LocalMetrics provides metrics,
        LocalReducedMotion provides reduced
    ) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Tokens.Brand,
                onPrimary = Tokens.PaperRaised,
                secondary = Tokens.BrandStrong,
                background = Tokens.Paper,
                onBackground = Tokens.Ink,
                surface = Tokens.PaperRaised,
                onSurface = Tokens.Ink,
                error = Tokens.Danger
            ),
            typography = Typography(),
            content = content
        )
    }
}
