package com.campus.schedule.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * 设计 token —— 与小程序 `miniprogram/styles/tokens.wxss` 一一对应。
 *
 * ## 为什么要 rpx 动态缩放
 *
 * 小程序用 rpx：750rpx 恒等于屏幕宽度，所以同一份样式在任何机型上**比例一致**。
 * Android 的 dp 是绝对单位，直接把 28rpx 写死成 14dp，在 360dp 和 430dp 的手机上
 * 视觉比例会差 19%。因此这里保留 rpx 作为唯一尺寸真源：
 *
 * ```
 * scale = 屏幕宽度(dp) / 750
 * rpx(28) = 28 * scale dp
 * ```
 *
 * 文字同理：rpx 数值直接当 sp 用，既跟随系统字体大小设置，
 * 又保证在小程序同尺寸机型（375dp 宽）上与小程序**像素级对齐**（1rpx = 0.5dp = 0.5sp）。
 *
 * ## 文本错位的根因与解法
 *
 * Android 字体默认带上下留白（font padding），同一行里字号不同的两段文字
 * 视觉基线就会错位。本文件所有 [TextStyle] 统一设置
 * `platformStyle = PlatformTextStyle(includeFontPadding = false)`，
 * 去掉这段留白，所有行内元素用 `Alignment.CenterVertically` 对齐。
 */

// ---------------------------------------------------------------------------
// 颜色
// ---------------------------------------------------------------------------

object Tokens {

    // 表面
    val Paper = Color(0xFFF6F9FF)
    val PaperRaised = Color(0xFFFFFFFF)
    val PaperSunken = Color(0xFFEEF4FF)
    val PaperTint = Color(0xFFEAF2FF)

    // 文本
    val Ink = Color(0xFF10233F)
    val InkSecondary = Color(0xFF54657C)
    val InkMuted = Color(0xFF6B7A90)
    val InkFaint = Color(0xFF9AA6B8)

    // 线条
    val Line = Color(0x1410233F)
    val LineStrong = Color(0x1F10233F)

    // 品牌蓝
    val Brand = Color(0xFF2F6FED)
    val BrandStrong = Color(0xFF2563EB)
    val BrandDeep = Color(0xFF1E56C7)
    val BrandTint = Color(0xFFEAF2FF)
    val BrandWash = Color(0xFFF6F9FF)

    // 语义色
    val Sage = Color(0xFF148A63)
    val SageText = Color(0xFF126B4B)
    val SageTint = Color(0xFFE7F8F1)
    val Gold = Color(0xFFF5A623)
    val GoldText = Color(0xFFA55B00)
    val GoldTint = Color(0xFFFFF4E3)
    val Danger = Color(0xFFC63F4E)
    val DangerTint = Color(0xFFFFF0F2)

    // 功能
    val Mask = Color(0x5207142A)
    val Transparent = Color(0x00FFFFFF)

    // 渐变
    val BrandGradient = Brush.linearGradient(
        colors = listOf(Brand, BrandStrong, BrandDeep),
        start = androidx.compose.ui.geometry.Offset.Zero,
        end = androidx.compose.ui.geometry.Offset.Infinite
    )
    val SoftGradient = Brush.linearGradient(
        colors = listOf(BrandTint, Color(0xFFD7E6FF)),
        start = androidx.compose.ui.geometry.Offset.Zero,
        end = androidx.compose.ui.geometry.Offset.Infinite
    )
    val TopGradient = Brush.verticalGradient(
        0f to Color(0xFFEAF3FF),
        0.62f to Paper,
        1f to Paper
    )

    // 卡片配色（accent）
    fun accentColor(accent: String): Color = when (accent) {
        "coral" -> Brand
        "sage" -> Sage
        "gold" -> Gold
        "ink" -> InkSecondary
        else -> Brand
    }

    fun accentTint(accent: String): Color = when (accent) {
        "coral" -> BrandTint
        "sage" -> SageTint
        "gold" -> GoldTint
        "ink" -> PaperSunken
        else -> BrandTint
    }

    /**
     * accent 对应的**文字色**。
     *
     * 与 [accentColor] 的区别：文字色必须在白底上可读，
     * 所以 gold 用的是深琥珀（[GoldText]）而不是亮金色。
     */
    fun accentTextColor(accent: String): Color = when (accent) {
        "coral" -> BrandDeep
        "sage" -> SageText
        "gold" -> GoldText
        "ink" -> InkSecondary
        else -> BrandDeep
    }

    /** 4 色系配色序列（与小程序 `ACCENTS` 一致）。 */
    private val accentPalette = listOf("coral", "sage", "gold", "ink")

    /** 按索引取配色，用于分类占比条这类「按序上色」的场景。 */
    fun accentByIndex(index: Int): String = accentPalette[((index % accentPalette.size) + accentPalette.size) % accentPalette.size]
}

/**
 * 会轮换的配色（v2.4 新增）。
 *
 * ## 为什么需要它
 *
 * 上一版的界面所有页面共用一套蓝，用了几天就会觉得「腻」—— 不是因为配色
 * 难看，而是因为**每天看到的东西一模一样**。换一套配色又会让老用户找不到
 * 自己熟悉的位置，所以这里的做法是：**主色按周轮换**，布局、字号、线条粗细
 * 一个字都不动。视觉记忆里的「形状」全保留，只有「色相」在换。
 *
 * ## 轮换规则
 *
 * 以 2026-01-05（周一）为第 0 周，每周换一个色板，四套循环。四套都在同一个
 * 明度/饱和度区间内，保证换成哪一套，正文对比度都不会掉下去。
 */
data class Palette(
    /** 主色：指示器、选中态、时间轴当前时刻的强调色。 */
    val brand: Color,
    /** 主色在深色文字上的可用版本（对白底可读）。 */
    val brandDeep: Color,
    /** 主色的浅底（选中项背景、标签底）。 */
    val brandTint: Color,
    /** 主色的较浅过渡色，用于渐变。 */
    val brandStrong: Color
) {
    /** 主色渐变：只由本套色板推导，换色时自动跟着换。 */
    val brandGradient: Brush
        get() = Brush.linearGradient(
            colors = listOf(brand, brandStrong, brandDeep),
            start = androidx.compose.ui.geometry.Offset.Zero,
            end = androidx.compose.ui.geometry.Offset.Infinite
        )
}

object Palettes {

    /**
     * v2.6：五套色板（蓝/绿/黑/白/粉），按周轮换。
     * 设计参考 Material Design 3 color system + DigiCal/Taskeet 等优秀日程 App。
     * 每套色板的 brand/brandDeep/brandStrong/brandTint 都经过对比度校验，
     * 确保在白底(PaperRaised)和浅底(Paper)上都可读。
     */
    val all: List<Palette> = listOf(
        // 1. 蓝 —— 经典商务蓝，沉稳可信
        Palette(
            brand = Color(0xFF2979FF),
            brandStrong = Color(0xFF2962FF),
            brandDeep = Color(0xFF1565C0),
            brandTint = Color(0xFFE3F2FD)
        ),
        // 2. 绿 —— 自然清新，适合学习/健康类日程
        Palette(
            brand = Color(0xFF00C853),
            brandStrong = Color(0xFF00BFA5),
            brandDeep = Color(0xFF2E7D32),
            brandTint = Color(0xFFE8F5E9)
        ),
        // 3. 黑 —— 高级感，夜间模式友好，文字用 Ink 而非纯黑
        Palette(
            brand = Color(0xFF37474F),
            brandStrong = Color(0xFF455A64),
            brandDeep = Color(0xFF263238),
            brandTint = Color(0xFFECEFF1)
        ),
        // 4. 白 —— 极简纯净，brand 用浅灰描边色，强调「留白」本身
        Palette(
            brand = Color(0xFF90A4AE),
            brandStrong = Color(0xFFB0BEC5),
            brandDeep = Color(0xFF546E7A),
            brandTint = Color(0xFFF5F5F5)
        ),
        // 5. 粉 —— 柔和温暖，适合生活/社交类日程
        Palette(
            brand = Color(0xFFEC407A),
            brandStrong = Color(0xFFE91E63),
            brandDeep = Color(0xFFC2185B),
            brandTint = Color(0xFFFCE4EC)
        )
    )

    /** 第 0 周的周一：用来把「现在」换算成周序号。固定值，不随用户时区漂移。 */
    private const val EPOCH_MONDAY = 1767571200000L // 2026-01-05 00:00:00 +08:00

    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    /** 给定时间落在第几周（从 0 开始，负数向 0 取整）。 */
    fun weekIndexOf(nowMillis: Long): Long {
        val diff = nowMillis - EPOCH_MONDAY
        val weeks = if (diff >= 0) diff / WEEK_MS else (diff - WEEK_MS + 1) / WEEK_MS
        return weeks
    }

    /** 取当周色板。 */
    fun of(nowMillis: Long): Palette {
        val index = ((weekIndexOf(nowMillis) % all.size) + all.size) % all.size
        return all[index.toInt()]
    }

    /** 取指定色板；[fixed] 非空时锁定该套（调试或用户手动选择用）。 */
    fun resolve(nowMillis: Long, fixed: Int?): Palette =
        if (fixed == null) of(nowMillis) else all[((fixed % all.size) + all.size) % all.size]
}

// ---------------------------------------------------------------------------
// 尺寸与排版
// ---------------------------------------------------------------------------

/**
 * 排版整体放大系数（v2.1 引入）。
 *
 * 750rpx 等比体系下，`28rpx` 在 392dp 屏只有 ~14.6sp，比 Android 正文下限
 * （15–16sp）还小；`22rpx` 的 micro 更是只有 ~11.5sp。原生端没有浏览器的
 * 最小字号兜底，所以整体放大 15%，字号档位相应的行高同步放大。
 */
private const val TYPE_SCALE = 1.15f

/**
 * 随屏宽缩放的度量集合。
 *
 * 一律通过 `LocalMetrics.current` 取得，**不要**在文件顶层写死 dp 数值，
 * 否则不同机型上比例会漂移。
 */
@Immutable
class Metrics(private val scale: Float) {

    /** rpx → Dp。 */
    fun rpx(value: Number): Dp = Dp(value.toFloat() * scale)

    /**
     * rpx → sp。
     *
     * 刻意**不**做 dp→sp 的密度换算：rpx 的语义是「屏幕宽度的 1/750」，
     * 直接当 sp 使用才能与小程序等比，同时保留系统字体大小设置的无障碍能力。
     *
     * v2.1：额外乘 [TYPE_SCALE] —— 750rpx 的等比字号在 392dp 手机上只有 14.6sp，
     * 正式版读起来偏小（小程序靠浏览器最小字号兜底，原生没有这层兜底）。
     * 布局尺寸不受影响，只有字变大。
     */
    fun sp(value: Number): TextUnit = (value.toFloat() * scale * TYPE_SCALE).sp

    // ---- 圆角 ----
    val radiusXs: Dp get() = rpx(10)
    val radiusSm: Dp get() = rpx(16)
    val radiusMd: Dp get() = rpx(22)
    val radiusLg: Dp get() = rpx(30)
    val radiusXl: Dp get() = rpx(40)
    val radiusPill: Dp get() = rpx(999)

    fun radiusShape(value: Number): Shape = RoundedCornerShape(rpx(value))

    // ---- 图标尺寸 ----
    val iconXs: Dp get() = rpx(24)
    val iconSm: Dp get() = rpx(28)
    val iconMd: Dp get() = rpx(32)
    val iconLg: Dp get() = rpx(40)
    val iconXl: Dp get() = rpx(48)
    val iconTile: Dp get() = rpx(56)

    // ---- 布局 ----
    val pagePadding: Dp get() = rpx(32)
    val tabBarHeight: Dp get() = rpx(112)
    val actionBarHeight: Dp get() = rpx(112)
    val touchMin: Dp get() = rpx(88)

    // ---- 排版 ----
    //
    // 每一档都同时给出 fontSize 与 lineHeight（与 tokens.wxss 的 --leading-* 一致），
    // 并关闭 font padding —— 这是行内文字不错位的关键。

    private val noPadding = PlatformTextStyle(includeFontPadding = false)

    val displayXL: TextStyle
        get() = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = sp(64),
            lineHeight = sp(76),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = sp(-0.4f),
            platformStyle = noPadding,
            color = Tokens.Ink
        )

    val displayL: TextStyle
        get() = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = sp(48),
            lineHeight = sp(60),
            fontWeight = FontWeight.Bold,
            letterSpacing = sp(-0.6f),
            platformStyle = noPadding,
            color = Tokens.Ink
        )

    val h1: TextStyle
        get() = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = sp(44),
            lineHeight = sp(58),
            fontWeight = FontWeight.SemiBold,
            platformStyle = noPadding,
            color = Tokens.Ink
        )

    val h2: TextStyle
        get() = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = sp(36),
            lineHeight = sp(50),
            fontWeight = FontWeight.Bold,
            letterSpacing = sp(-0.4f),
            platformStyle = noPadding,
            color = Tokens.Ink
        )

    val h3: TextStyle
        get() = TextStyle(
            fontSize = sp(32),
            lineHeight = sp(46),
            fontWeight = FontWeight.SemiBold,
            platformStyle = noPadding,
            color = Tokens.Ink
        )

    val bodyLg: TextStyle
        get() = TextStyle(
            fontSize = sp(30),
            lineHeight = sp(46),
            fontWeight = FontWeight.Normal,
            platformStyle = noPadding,
            color = Tokens.Ink
        )

    val body: TextStyle
        get() = TextStyle(
            fontSize = sp(28),
            lineHeight = sp(44),
            fontWeight = FontWeight.Normal,
            platformStyle = noPadding,
            color = Tokens.Ink
        )

    val meta: TextStyle
        get() = TextStyle(
            fontSize = sp(26),
            lineHeight = sp(40),
            fontWeight = FontWeight.Normal,
            platformStyle = noPadding,
            color = Tokens.InkMuted
        )

    val caption: TextStyle
        get() = TextStyle(
            fontSize = sp(24),
            lineHeight = sp(36),
            fontWeight = FontWeight.Normal,
            platformStyle = noPadding,
            color = Tokens.InkMuted
        )

    val micro: TextStyle
        get() = TextStyle(
            fontSize = sp(22),
            lineHeight = sp(32),
            fontWeight = FontWeight.Normal,
            platformStyle = noPadding,
            color = Tokens.InkMuted
        )

    /** 数字排版：等宽数字，避免列表里数字跳动。 */
    val numeric: TextStyle
        get() = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = sp(28),
            lineHeight = sp(40),
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = "tnum",
            platformStyle = noPadding,
            color = Tokens.Ink
        )

    val numericLarge: TextStyle
        get() = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = sp(44),
            lineHeight = sp(48),
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = "tnum",
            platformStyle = noPadding,
            color = Tokens.BrandStrong
        )

    /** 全大写小标签（眉标）。 */
    val kicker: TextStyle
        get() = TextStyle(
            fontSize = sp(22),
            lineHeight = sp(32),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = sp(2),
            platformStyle = noPadding,
            color = Tokens.BrandStrong
        )

    val center: TextAlign get() = TextAlign.Center
}
