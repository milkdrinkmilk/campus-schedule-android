package com.campus.schedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import com.campus.schedule.ui.icons.AppIcons
import com.campus.schedule.ui.theme.LocalMetrics
import com.campus.schedule.ui.theme.LocalPalette
import com.campus.schedule.ui.theme.LocalReducedMotion
import com.campus.schedule.ui.theme.Motion
import com.campus.schedule.ui.theme.Tokens

/**
 * 通用 UI 组件 —— 与小程序 `styles/base.wxss` + 各页面 wxss 一一对应。
 *
 * 所有尺寸都走 `LocalMetrics.rpx(...)`，保证与小程序等比；
 * 所有文字样式都带 `includeFontPadding = false`，保证行内元素不错位。
 */

// ---------------------------------------------------------------------------
// 报头
// ---------------------------------------------------------------------------

/**
 * 页面报头（`.masthead`）。
 *
 * @param kicker 眉标，小号大写品牌蓝文字
 * @param title 主标题
 * @param meta 副信息
 * @param trailing 标题右侧的操作区（通常是「今天」这类胶囊按钮）
 */
@Composable
fun Masthead(
    kicker: String,
    title: String,
    meta: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    val m = LocalMetrics.current
    // v2.2：三段文字全部改成**基线对齐的下沿锚点**，不再用 padding 硬推。
    // 之前 kicker 用 8rpx 间距、meta 又用 8rpx，而两者的行高分别是 32 / 40，
    // 叠出来的间距一个 40 一个 48，看上去就是「上下不匀」。
    Column(modifier = modifier.padding(bottom = m.rpx(36))) {
        Text(
            text = kicker,
            style = m.kicker,
            maxLines = 1
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = m.rpx(10)),
            // 大标题和右侧胶囊按钮按底边对齐：文字的行高上下留白比按钮高，
            // CenterVertically 会让按钮看起来偏高一点，Bottom 才是视觉齐平。
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = m.displayL,
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = m.rpx(2)),
                maxLines = 1
            )
            trailing?.invoke()
        }
        Text(
            text = meta,
            style = m.meta.copy(color = Tokens.InkSecondary),
            modifier = Modifier.padding(top = m.rpx(10)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------
// 分区标题
// ---------------------------------------------------------------------------

/** 分区标题（`.section-header`）。 */
@Composable
fun SectionHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    Column(modifier = modifier.padding(bottom = m.rpx(24))) {
        // 眉标前的短线用 Alignment.CenterVertically 与文字行高中心对齐；
        // 线高 4rpx 远小于行高 32rpx，靠 center 才不会看起来「挂着」
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(m.rpx(12))
        ) {
            Box(
                modifier = Modifier
                    .width(m.rpx(32))
                    .height(m.rpx(4))
                    .clip(RoundedCornerShape(m.rpx(4)))
                    .background(p.brandGradient)
            )
            Text(text = eyebrow, style = m.kicker, maxLines = 1)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = m.rpx(10)),
            // 标题与右侧动作按底边对齐，避免 h2（行高 50）与 meta（行高 40）重心不齐
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(m.rpx(16))
        ) {
            // v2.4.1：weight(1f) + maxLines。之前标题不占权重也不截断，
            // 「03 / 提醒 · 最近要发生的 3 件事」这种长眉标会把标题挤到
            // LazyColumn 边界外被裁成半截字 —— 概况页「本周分布」显示不全就是这个。
            Text(
                text = title,
                style = m.h2,
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = m.rpx(2)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            action?.invoke()
        }
    }
}

/** 分区右侧的文字操作（`.section-action`）。 */
@Composable
fun SectionAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null
) {
    val m = LocalMetrics.current
    val interaction = rememberPressInteraction()
    Row(
        modifier = modifier
            .heightIn(min = m.touchMin)
            .pressHighlight(interaction, RoundedCornerShape(m.radiusMd))
            .rippleClick(interaction, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(m.rpx(4))
    ) {
        icon?.invoke()
        Text(text = text, style = m.meta.copy(color = Tokens.InkSecondary))
    }
}

// ---------------------------------------------------------------------------
// 标签
// ---------------------------------------------------------------------------

/** 标签色系。 */
enum class TagTone {
    NEUTRAL, UPCOMING, ONGOING, ENDED, CANCELLED, PUBLIC, GROUP, FAVORITE, WARNING, DANGER
}

@Composable
private fun tagColors(tone: TagTone): Pair<Color, Color> {
    val p = LocalPalette.current
    return when (tone) {
        TagTone.NEUTRAL -> Tokens.PaperSunken to Tokens.InkSecondary
        TagTone.UPCOMING -> p.brandTint to p.brandDeep
        TagTone.ONGOING -> p.brandTint to p.brandDeep
        TagTone.ENDED -> Tokens.PaperSunken to Tokens.InkSecondary
        TagTone.CANCELLED -> Tokens.Transparent to Tokens.InkMuted
        TagTone.PUBLIC -> Tokens.GoldTint to Tokens.GoldText
        TagTone.GROUP -> Tokens.SageTint to Tokens.SageText
        TagTone.FAVORITE -> Tokens.GoldTint to Tokens.GoldText
        TagTone.WARNING -> Tokens.GoldTint to Tokens.GoldText
        TagTone.DANGER -> Tokens.DangerTint to Tokens.Danger
    }
}

/** 小标签（`.tag`）。 */
@Composable
fun Tag(
    text: String,
    tone: TagTone = TagTone.NEUTRAL,
    modifier: Modifier = Modifier,
    withDot: Boolean = false
) {
    val m = LocalMetrics.current
    val (bg, fg) = tagColors(tone)
    // 高度交给内容决定：文字行高 34 + 上下各 6 = 46rpx。
    // 原来写死 heightIn(min = 40) 且 vertical padding 只有 4rpx，
    // 字号放大到 24 之后文字会顶到描边。
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(m.radiusXs))
            .background(bg)
            .padding(horizontal = m.rpx(16), vertical = m.rpx(10)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(m.rpx(6))
    ) {
        if (withDot) {
            Box(
                modifier = Modifier
                    .size(m.rpx(8))
                    .clip(CircleShape)
                    .background(fg)
            )
        }
        Text(
            text = text,
            style = m.micro.copy(
                fontSize = m.sp(24),
                lineHeight = m.sp(34),
                color = fg,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                textDecoration = if (tone == TagTone.CANCELLED) TextDecoration.LineThrough else null
            )
        )
    }
}

/** 状态胶囊（`.timeline-row__status`）：圆角药丸 + 状态点。 */
@Composable
fun StatusPill(
    text: String,
    tone: TagTone,
    modifier: Modifier = Modifier,
    filled: Boolean = true
) {
    val m = LocalMetrics.current
    val (bg0, fg) = tagColors(tone)
    // v2.4.1：filled = false 时不铺底色，只留状态点 + 着色文字。
    // 新的「每日事务」行要求行内干净无框，胶囊底会重新造出一层盒子。
    val bg = if (filled) bg0 else Color.Transparent
    // 胶囊高度 = 文字行高 32 + 上下各 9 = 50rpx。
    // 之前固定 40rpx 而文字行高 32，上下只剩 4rpx，字号放大到 24 之后文字
    // 就会贴边甚至被裁 —— 这是「文本不齐」里最显眼的一处。
    Row(
        modifier = modifier
            .height(m.rpx(50))
            .clip(RoundedCornerShape(m.radiusPill))
            .background(bg)
            .padding(horizontal = m.rpx(14)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(m.rpx(8))
    ) {
        Box(
            modifier = Modifier
                .size(m.rpx(8))
                .clip(CircleShape)
                .background(fg)
        )
        Text(
            text = text,
            style = m.micro.copy(
                fontSize = m.sp(24),
                lineHeight = m.sp(32),
                color = fg,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            ),
            maxLines = 1
        )
    }
}

/** 行内元信息，用「·」分隔（`.meta-line`）。 */
@Composable
fun MetaLine(
    vararg parts: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle? = null
) {
    val m = LocalMetrics.current
    val base = style ?: m.micro.copy(fontSize = m.sp(24), lineHeight = m.sp(36), color = Tokens.InkSecondary)
    // v2.4.1：整行必须受父级宽度约束（fillMaxWidth）。之前无约束时，
    // 在 LazyColumn 里一旦内容超宽就会把后面的元素顶出屏幕 ——
    // 概况页「即将提醒」第三段文字显示不全就是这个。
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(m.rpx(6))
    ) {
        val list = parts.filter { it.isNotBlank() }
        list.forEachIndexed { index, part ->
            if (index > 0) {
                Text(text = "·", style = base.copy(color = Tokens.InkFaint))
            }
            Text(
                text = part,
                style = base,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 卡片
// ---------------------------------------------------------------------------

/**
 * 纸面卡片（`.paper-card`）—— v2.1 美术重做的基准样式。
 *
 * 这一版的所有纵向层次只靠三件事表达，**不再用阴影**：
 * 1. `PaperRaised` 白纸底，压在浅蓝页面底色（`TopGradient`）上自然浮起；
 * 2. 一圈 `2rpx` 的 `Tokens.Line` 细线（约 10% 墨色），是「简约线条」的主体；
 * 3. 左侧一条 `4rpx` 的纸色竖条（`accent`），只在调用方显式传色时出现。
 *
 * @param accent 可选的左侧竖条颜色，传 null 则不出竖条
 */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp? = null,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val m = LocalMetrics.current
    val shape = RoundedCornerShape(m.radiusLg)
    val interaction = rememberPressInteraction()

    var base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(Tokens.PaperRaised)
        .border(m.rpx(2), Tokens.Line, shape)

    if (accent != null) {
        base = base.drawBehind {
            val w = m.rpx(4).toPx()
            val half = m.rpx(14).toPx()
            drawRoundRect(
                color = accent,
                topLeft = androidx.compose.ui.geometry.Offset(0f, half),
                size = androidx.compose.ui.geometry.Size(w, size.height - half * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2f, w / 2f)
            )
        }
    }

    if (onClick != null) {
        base = base
            .pressHighlight(interaction, shape)
            .rippleClick(interaction, onClick = onClick)
    }

    Column(
        modifier = base.padding(contentPadding ?: m.rpx(32)),
        content = content
    )
}

/**
 * 主卡片 —— 有序号/题卡感的分块容器（身份卡、统计条、编辑表单）。
 *
 * 与 [PaperCard] 的差别只有一点：标题区与内容区之间由调用方自己插
 * [CardHeader]，这里只保证外框、内边距和按压反馈一致。
 */
@Composable
fun RaisedCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp? = null,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    PaperCard(
        modifier = modifier,
        contentPadding = contentPadding,
        onClick = onClick,
        accent = accent,
        content = content
    )
}

/**
 * 卡片标题行：一个小方块编号 + 标题 + 右侧可选动作。
 *
 * 小方块是 v2.1 的「美术记号」——2rpx 描边的空心方块，
 * 比实心色块更接近用户要的「简约线条」。
 */
@Composable
fun CardHeader(
    index: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = m.rpx(20)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(m.rpx(14))
    ) {
        Box(
            modifier = Modifier
                .size(m.rpx(40))
                .clip(RoundedCornerShape(m.rpx(10)))
                .border(m.rpx(2), Tokens.LineStrong, RoundedCornerShape(m.rpx(10))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = index,
                style = m.micro.copy(
                    fontSize = m.sp(24),
                    lineHeight = m.sp(26),
                    color = p.brandDeep,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                maxLines = 1
            )
        }
        Text(
            text = title,
            style = m.h3.copy(
                color = Tokens.Ink,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            ),
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        trailing?.invoke()
    }
}

// ---------------------------------------------------------------------------
// 按钮
// ---------------------------------------------------------------------------

/** 按钮样式。 */
enum class ButtonStyle { PRIMARY, SECONDARY, GHOST, DANGER, DISABLED }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    block: Boolean = true,
    small: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val interaction = rememberPressInteraction()

    val minHeight = if (small) m.rpx(88) else m.rpx(96)
    val padH = if (small) m.rpx(24) else m.rpx(32)

    val fg = when (style) {
        ButtonStyle.PRIMARY -> Tokens.PaperRaised
        ButtonStyle.SECONDARY -> p.brandDeep
        ButtonStyle.GHOST -> Tokens.InkSecondary
        ButtonStyle.DANGER -> Tokens.Danger
        ButtonStyle.DISABLED -> Tokens.InkMuted
    }
    val textStyle = if (small) m.meta else m.bodyLg
    val shape = RoundedCornerShape(m.radiusMd)
    val disabled = style == ButtonStyle.DISABLED

    val buttonModifier = if (block) {
        modifier.fillMaxWidth()
    } else {
        modifier
    }

    Row(
        modifier = buttonModifier
            .heightIn(min = minHeight)
            .clip(shape)
            .then(
                when (style) {
                    ButtonStyle.PRIMARY -> Modifier.background(p.brandGradient)
                    // 次要按钮改用「纸色 + 2rpx 墨线」，和卡片同一套线条语言；
                    // 原来用 PaperTint 描边，白底上几乎看不见。
                    ButtonStyle.SECONDARY -> Modifier
                        .background(Tokens.PaperRaised)
                        .border(m.rpx(2), Tokens.LineStrong, shape)
                    ButtonStyle.GHOST -> Modifier
                    ButtonStyle.DANGER -> Modifier
                        .background(Tokens.DangerTint)
                        .border(m.rpx(2), Tokens.Danger.copy(alpha = 0.24f), shape)
                    ButtonStyle.DISABLED -> Modifier.background(Tokens.PaperSunken)
                }
            )
            .pressHighlight(interaction, shape, enabled = !disabled)
            .rippleClick(interaction, enabled = !disabled, onClick = onClick)
            // 上下内边距 14rpx + 文字行高 46/40，块高落在 96~100rpx；
            // 之前上下各 16 会顶到 minHeight，文字在按钮里偏下（行高留白不对称）
            .padding(horizontal = padH, vertical = m.rpx(12)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            leadingIcon.invoke()
            Spacer(Modifier.width(m.rpx(8)))
        }
        Text(
            text = text,
            style = textStyle.copy(
                color = fg,
                fontWeight = if (style == ButtonStyle.PRIMARY) {
                    androidx.compose.ui.text.font.FontWeight.SemiBold
                } else {
                    androidx.compose.ui.text.font.FontWeight.SemiBold
                }
            ),
            maxLines = 1
        )
    }
}

/**
 * 定位胶囊按钮（`.locate-btn`）。
 *
 * 选中态换成品牌渐变填充，是全站统一的「回到今天 / 本周」入口。
 */
@Composable
fun LocateButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val reduced = LocalReducedMotion.current
    val interaction = rememberPressInteraction()
    val shape = RoundedCornerShape(m.radiusPill)

    // 选中态的品牌渐变用**图层透明度**过渡，而不是改背景色：
    // 透明度写在 graphicsLayer 里，动画帧只重绘，不重组。
    val overlay = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = Motion.fast(reduced),
        label = "locateOverlay"
    )

    Box(
        modifier = modifier
            .heightIn(min = m.rpx(92))
            .clip(shape)
            .background(Tokens.PaperRaised)
            .border(
                width = m.rpx(2),
                color = if (active) Color.Transparent else Tokens.LineStrong,
                shape = shape
            )
    ) {
        // 渐变覆盖层
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = overlay.value }
                .background(p.brandGradient)
        )
        Row(
            modifier = Modifier
                .matchParentSize()
                .pressHighlight(interaction, shape)
                .rippleClick(interaction, onClick = onClick)
                .padding(horizontal = m.rpx(28)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(m.rpx(8))
        ) {
            leadingIcon?.invoke()
            Text(
                text = text,
                style = m.body.copy(
                    color = if (active) Tokens.PaperRaised else p.brandDeep,
                    fontWeight = if (active) {
                        androidx.compose.ui.text.font.FontWeight.Bold
                    } else {
                        androidx.compose.ui.text.font.FontWeight.SemiBold
                    }
                ),
                maxLines = 1
            )
            trailingIcon?.invoke()
        }
    }
}

// ---------------------------------------------------------------------------
// 分段切换
// ---------------------------------------------------------------------------

/**
 * 分段切换器（`.view-switch`）。
 *
 * 白色药丸底 + 品牌渐变指示器横向滑动。
 * 指示器用 `graphicsLayer { translationX }` 位移，动画期间只重绘不重排。
 */
@Composable
fun SegmentedSwitch(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val reduced = LocalReducedMotion.current
    val shape = RoundedCornerShape(m.radiusPill)

    // 轨道高 112rpx、内边距 8rpx，指示器实际高 96rpx；
    // 文字行高 44rpx 在其内部垂直居中后，上下留白各 26rpx —— 视觉上才会「站得正」。
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(m.rpx(112))
            .clip(shape)
            .background(Tokens.PaperSunken)
            .padding(m.rpx(8))
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val indicatorWidth = maxWidth / 2
            val offset = animateFloatAsState(
                targetValue = with(density) { selectedIndex * indicatorWidth.toPx() },
                animationSpec = Motion.tab(reduced),
                label = "segmentIndicator"
            )
            Box(
                modifier = Modifier
                    .width(indicatorWidth)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(p.brandGradient)
                    .graphicsLayer { translationX = offset.value }
            )
            Row(modifier = Modifier.fillMaxSize()) {
                labels.forEachIndexed { index, label ->
                    val interaction = rememberPressInteraction()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .rippleClick(interaction) { onSelect(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = m.body.copy(
                                color = if (index == selectedIndex) {
                                    Tokens.PaperRaised
                                } else {
                                    Tokens.InkSecondary
                                },
                                fontWeight = if (index == selectedIndex) {
                                    androidx.compose.ui.text.font.FontWeight.Bold
                                } else {
                                    androidx.compose.ui.text.font.FontWeight.SemiBold
                                }
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 空态
// ---------------------------------------------------------------------------

/** 空态印章（`.empty-state__seal`）。 */
@Composable
fun EmptySeal(
    modifier: Modifier = Modifier,
    variant: SealVariant = SealVariant.DOT
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val size = m.rpx(112)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(m.rpx(36)))
            .background(Tokens.SoftGradient)
            .border(m.rpx(2), Tokens.Line, RoundedCornerShape(m.rpx(36)))
    ) {
        when (variant) {
            SealVariant.DOT -> Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(m.rpx(36))
                    .clip(CircleShape)
                    .background(p.brand)
            )
            SealVariant.ERROR -> Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(m.rpx(56))
                    .height(m.rpx(4))
                    .clip(RoundedCornerShape(m.rpx(4)))
                    .background(p.brand)
            )
            SealVariant.LOCKED -> Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(m.rpx(38))
                    .height(m.rpx(30))
                    .clip(RoundedCornerShape(m.rpx(8)))
                    .border(m.rpx(6), p.brandDeep, RoundedCornerShape(m.rpx(8)))
            )
        }
    }
}

enum class SealVariant { DOT, ERROR, LOCKED }

/** 空态（`.empty-state`）。 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    variant: SealVariant = SealVariant.DOT,
    lineArt: Boolean = false,
    actions: @Composable (() -> Unit)? = null,
    inline: Boolean = false
) {
    val m = LocalMetrics.current
    // v2.4.1：inline 变体。原来空态只有「居中大插画」一种，出现在清单中间时
    // 像一整页海报，和上下文完全脱节。inline 把它压成一段左对齐的编辑排版：
    // 一根短线 + 标题 + 一句正文，不抢屏幕中心。
    if (inline) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = m.rpx(8), vertical = m.rpx(40)),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(m.rpx(10))
        ) {
            Box(
                modifier = Modifier
                    .width(m.rpx(40))
                    .height(m.rpx(4))
                    .clip(RoundedCornerShape(m.rpx(2)))
                    .background(Tokens.InkFaint.copy(alpha = 0.6f))
            )
            Text(
                text = title,
                style = m.h3.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                maxLines = 2
            )
            Text(
                text = body,
                style = m.meta.copy(color = Tokens.InkSecondary),
                modifier = Modifier.widthIn(max = m.rpx(560))
            )
            if (actions != null) {
                Row(modifier = Modifier.padding(top = m.rpx(14))) { actions() }
            }
        }
        return
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = m.rpx(48), vertical = m.rpx(96)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(m.rpx(16))
    ) {
        if (lineArt) EmptyLineArt() else EmptySeal(variant = variant)
        Text(
            text = title,
            style = m.h3.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            modifier = Modifier.padding(top = m.rpx(8)),
            textAlign = TextAlign.Center
        )
        Text(
            text = body,
            style = m.meta.copy(color = Tokens.InkSecondary),
            modifier = Modifier.widthIn(max = m.rpx(520)),
            textAlign = TextAlign.Center
        )
        if (actions != null) {
            Column(
                modifier = Modifier.padding(top = m.rpx(16)),
                verticalArrangement = Arrangement.spacedBy(m.rpx(16))
            ) {
                actions()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 空态插画
// ---------------------------------------------------------------------------

/**
 * 空态线条插画：一张日程卡的极简线稿。
 *
 * 三条线依次「生长」出来——用 PathMeasure 取段，进度只在 draw 阶段读取，
 * 不会触发重组；系统开了「移除动画」时直接画完整线稿。
 */
@Composable
private fun EmptyLineArt() {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(0f) }
    // 画笔里的进度叫 grow，避免和色板局部变量 p 撞名
    val grow = progress.value

    LaunchedEffect(reduced) {
        if (reduced) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(900, easing = Motion.EaseSoft))
        }
    }

    Canvas(
        modifier = Modifier
            .width(m.rpx(220))
            .height(m.rpx(160))
    ) {
        val w = size.width
        val h = size.height
        val hair = m.rpx(2).toPx()
        val strokeW = hair * 1.6f

        // 卡片外框
        val left = w * 0.06f
        val top = h * 0.14f
        val right = w * 0.94f
        val bottom = h * 0.90f
        val radius = (bottom - top) * 0.22f

        val card = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom,
                    cornerRadius = CornerRadius(radius, radius)
                )
            )
        }
        drawGrowing(card, grow, p.brand.copy(alpha = 0.55f), strokeW)

        // 顶部一条分隔线
        val headerY = top + (bottom - top) * 0.30f
        val header = Path().apply {
            moveTo(left + (right - left) * 0.16f, headerY)
            lineTo(right - (right - left) * 0.16f, headerY)
        }
        drawGrowing(header, (grow - 0.25f).coerceIn(0f, 1f) / 0.75f, p.brand, strokeW)

        // 两条「待办」短线
        val lineX0 = left + (right - left) * 0.16f
        val first = Path().apply {
            moveTo(lineX0, top + (bottom - top) * 0.55f)
            lineTo(left + (right - left) * 0.62f, top + (bottom - top) * 0.55f)
        }
        drawGrowing(first, (grow - 0.45f).coerceIn(0f, 1f) / 0.55f, Tokens.InkFaint, strokeW)

        val second = Path().apply {
            moveTo(lineX0, top + (bottom - top) * 0.72f)
            lineTo(left + (right - left) * 0.46f, top + (bottom - top) * 0.72f)
        }
        drawGrowing(second, (grow - 0.65f).coerceIn(0f, 1f) / 0.35f, Tokens.InkFaint, strokeW)
    }
}

/** 按进度描出路径的一段；progress 为 1 时直接整条画出。 */
/** v2.4.1：改 internal，启动画面与空态插画共用同一套「描边生长」。 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrowing(
    path: Path,
    progress: Float,
    color: Color,
    strokeWidth: Float
) {
    if (progress <= 0f) return
    val style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    if (progress >= 1f) {
        drawPath(path, color, style = style)
        return
    }
    val measure = PathMeasure()
    measure.setPath(path, false)
    val dest = Path()
    measure.getSegment(0f, measure.length * progress, dest, true)
    drawPath(dest, color, style = style)
}

// ---------------------------------------------------------------------------
// 提示条
// ---------------------------------------------------------------------------

/** 金色提示条（`.conflict-bar` / `.hint-bar`）。 */
@Composable
fun HintBar(
    text: String,
    modifier: Modifier = Modifier,
    trailingIcon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val m = LocalMetrics.current
    val interaction = rememberPressInteraction()
    val shape = RoundedCornerShape(m.radiusSm)

    var base = modifier
        .fillMaxWidth()
        .heightIn(min = m.rpx(88))
        .clip(shape)
        .background(Tokens.GoldTint)

    // 左侧 6rpx 金色竖条
    base = base.drawBehindGoldMark(m)

    if (onClick != null) {
        base = base
            .pressHighlight(interaction, RoundedCornerShape(m.radiusMd))
            .rippleClick(interaction, onClick = onClick)
    }

    Row(
        modifier = base.padding(horizontal = m.rpx(24), vertical = m.rpx(16)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(m.rpx(12))
    ) {
        Text(
            text = text,
            style = m.meta.copy(color = Tokens.GoldText),
            modifier = Modifier.weight(1f)
        )
        trailingIcon?.invoke()
    }
}

/** 在 HintBar 左侧画一条金色竖标。 */
private fun Modifier.drawBehindGoldMark(m: com.campus.schedule.ui.theme.Metrics): Modifier =
    this.drawBehind {
        val w = m.rpx(6).toPx()
        val h = this.size.height
        drawRect(
            color = Tokens.Gold,
            topLeft = androidx.compose.ui.geometry.Offset.Zero,
            size = androidx.compose.ui.geometry.Size(w, h)
        )
    }

// ---------------------------------------------------------------------------
// 快捷入口
// ---------------------------------------------------------------------------

/** 快捷入口行（`.entry-row`）。 */
@Composable
fun EntryRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    count: String? = null,
    value: String? = null,
    iconTint: Color? = null,
    iconBackground: Color = Tokens.PaperTint
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val interaction = rememberPressInteraction()

    // 行内每一件元素都用同一套高度（图标块 56、文字行高 46、箭头 28），
    // 并统一 CenterVertically；右侧的数值与箭头之间用 8rpx 的最小间距，
    // 之前没有间距，中文数字会贴着箭头，看起来「挤在右边」。
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = m.rpx(112))
            .clip(RoundedCornerShape(m.radiusLg))
            .pressHighlight(interaction, RoundedCornerShape(m.radiusLg))
            .rippleClick(interaction, onClick = onClick)
            .padding(horizontal = m.rpx(20), vertical = m.rpx(14)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(m.rpx(16))
    ) {
        if (iconRes != null) {
            Box(
                modifier = Modifier
                    .size(m.iconTile)
                    .clip(RoundedCornerShape(m.radiusSm))
                    .background(iconBackground),
                contentAlignment = Alignment.Center
            ) {
                // 图标撑满一个内缩 12rpx 的方框，四边等距
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = iconTint ?: p.brand,
                    modifier = Modifier.size(m.rpx(32))
                )
            }
        }
        Text(
            text = label,
            style = m.bodyLg,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (count != null) {
            Text(
                text = count,
                style = m.meta.copy(color = Tokens.InkMuted),
                maxLines = 1
            )
        }
        if (value != null) {
            Text(
                text = value,
                style = m.micro.copy(
                    fontSize = m.sp(24),
                    lineHeight = m.sp(32),
                    color = Tokens.InkMuted
                ),
                maxLines = 1
            )
        }
        Icon(
            painter = painterResource(AppIcons.chevronRight),
            contentDescription = null,
            tint = Tokens.InkFaint,
            modifier = Modifier.size(m.iconSm)
        )
    }
}

/** 入口列表容器（`.entry-list`）：上下都有细分隔线。 */
@Composable
fun EntryList(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val m = LocalMetrics.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = m.rpx(2)),
        content = content
    )
}

/** 入口行之间的分隔线。 */
@Composable
fun EntryDivider(modifier: Modifier = Modifier) {
    val m = LocalMetrics.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(m.rpx(2))
            .background(Tokens.Line)
    )
}

// ---------------------------------------------------------------------------
// 统计
// ---------------------------------------------------------------------------

/** 统计条（`.stats`）。 */
@Composable
fun StatsRow(
    stats: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    val m = LocalMetrics.current
    val shape = RoundedCornerShape(m.radiusLg)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Tokens.PaperRaised)
            .border(m.rpx(2), Tokens.Line, shape)
            .padding(vertical = m.rpx(26), horizontal = m.rpx(20)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        stats.forEachIndexed { index, (value, label) ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(m.rpx(2))
                        .height(m.rpx(56))
                        .background(Tokens.Line)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(m.rpx(6))
            ) {
                // 数值 48 行高、标签 32 行高：两行块 86rpx，
                // 分隔线高 56rpx 正好压在数值与标签之间的视觉中线上
                Text(text = value, style = m.numericLarge, maxLines = 1)
                Text(
                    text = label,
                    style = m.micro.copy(
                        fontSize = m.sp(24),
                        lineHeight = m.sp(32),
                        color = Tokens.InkSecondary
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 身份卡
// ---------------------------------------------------------------------------

/** 身份卡（`.identity`）。 */
@Composable
fun IdentityCard(
    name: String,
    initial: String,
    subline: String,
    hint: String,
    tagText: String? = null,
    tagTone: TagTone = TagTone.NEUTRAL,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val shape = RoundedCornerShape(m.radiusLg)
    val interaction = rememberPressInteraction()

    var base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(Tokens.PaperRaised)
        .border(m.rpx(2), Tokens.Line, shape)

    if (onClick != null) {
        base = base
            .pressHighlight(interaction, shape)
            .rippleClick(interaction, onClick = onClick)
    }

    Row(
        modifier = base.padding(m.rpx(32)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(m.rpx(24))
    ) {
        Box(
            modifier = Modifier
                .size(m.rpx(104))
                .clip(RoundedCornerShape(m.rpx(32)))
                .background(p.brandGradient),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                // 行高收紧到接近字面高度，首字在 104rpx 方块里才是几何居中
                style = m.h1.copy(
                    lineHeight = m.sp(46),
                    color = Tokens.PaperRaised,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                maxLines = 1
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(m.rpx(6))
        ) {
            // 名字行高 60 但实际字面只有 48，减掉 4rpx 纵向留白后
            // 与下面标签行的视觉间距才等于 6rpx
            Text(
                text = name,
                style = m.displayL.copy(lineHeight = m.sp(58)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(m.rpx(12))
            ) {
                if (tagText != null) {
                    Tag(text = tagText, tone = tagTone)
                }
                Text(
                    text = subline,
                    style = m.caption.copy(lineHeight = m.sp(30)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = hint,
                style = m.micro.copy(
                    fontSize = m.sp(24),
                    lineHeight = m.sp(32),
                    color = Tokens.InkSecondary
                ),
                maxLines = 2
            )
        }
    }
}

/**
 * 全站统一的二次确认浮层（v2.1 重做）。
 *
 * ## 旧版为什么「割裂」
 *
 * 1. 卡片用 `scaleIn(0.96) + fadeIn` 从屏幕正中「长出来」，遮罩同时淡入 ——
 *    两个动画节奏不同，弹窗像是从界面里挤出来的一块，与界面没有任何空间关系；
 * 2. 弹窗用 0.96 缩放，但界面本身一点不动，缺少「被压下去」的层次感；
 * 3. 遮罩是全屏纯色，弹窗一出现，屏幕亮度骤然下降，像换了一个界面。
 *
 * ## 现在
 *
 * 采用 Android 惯用的**底部面板**：从屏幕底边滑上来，语义上明确是「附加动作」，
 * 不会和页面内容争夺焦点。三段动画共用一条减速曲线，时间轴对齐：
 * * 遮罩：alpha 0 → 0.72（不是整屏纯色，而是可透过的灰），240ms；
 * * 面板：translationY 从底边外 100% → 0，同时 alpha 0 → 1，280ms；
 * * 内容：标题 → 正文 → 按钮 三段错位 40ms 淡入上移，面板到位时刚好收齐。
 *
 * 物理返回键、点遮罩、点取消都关闭面板。
 */
@Composable
fun ConfirmOverlay(
    visible: Boolean,
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelText: String = "取消",
    style: ButtonStyle = ButtonStyle.DANGER,
    modifier: Modifier = Modifier
) {
    val m = LocalMetrics.current
    val reduced = LocalReducedMotion.current

    // 弹窗是「页面之上」的一层，物理返回应当先关它。
    BackHandler(enabled = visible, onBack = onDismiss)

    val interaction = rememberPressInteraction()

    // 遮罩与面板共用同一个可见性，但用不同的时长/曲线：
    // 遮罩稍快（240ms）先铺满，面板稍慢（280ms）随后到位，视觉上是「先变暗、再浮起」。
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(Motion.DurOverlay, easing = Motion.EaseSoft)),
        exit = fadeOut(tween(Motion.DurExit))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // ---- 遮罩：只覆盖一层灰，不遮死 ----
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Tokens.Ink.copy(alpha = 0.42f))
                    .clickableNoRipple(interaction, onClick = onDismiss)
            )

            // ---- 面板：从底边滑入 ----
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = if (reduced) {
                    EnterTransition.None
                } else {
                    slideInVertically(tween(Motion.DurOverlay, easing = Motion.EaseSoft)) { it } +
                        fadeIn(tween(Motion.DurOverlay, easing = Motion.EaseSoft))
                },
                exit = if (reduced) {
                    ExitTransition.None
                } else {
                    slideOutVertically(tween(Motion.DurExit, easing = Motion.EaseInOut)) { it } +
                        fadeOut(tween(Motion.DurExit))
                }
            ) {
                val sheetShape = RoundedCornerShape(
                    topStart = m.rpx(40),
                    topEnd = m.rpx(40)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(sheetShape)
                        .background(Tokens.PaperRaised)
                        .navigationBarsPadding()
                        .padding(
                            start = m.rpx(40),
                            end = m.rpx(40),
                            top = m.rpx(32),
                            bottom = m.rpx(40)
                        )
                ) {
                    // 顶部抓手条：一眼认出这是可以下滑/点外部关闭的面板
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = m.rpx(28))
                            .width(m.rpx(64))
                            .height(m.rpx(6))
                            .clip(RoundedCornerShape(m.rpx(3)))
                            .background(Tokens.LineStrong)
                    )
                    StaggeredFade(index = 0) {
                        Text(
                            text = title,
                            style = m.h2
                        )
                    }
                    Spacer(Modifier.height(m.rpx(12)))
                    StaggeredFade(index = 1) {
                        Text(
                            text = body,
                            style = m.body.copy(color = Tokens.InkSecondary)
                        )
                    }
                    Spacer(Modifier.height(m.rpx(36)))
                    StaggeredFade(index = 2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(m.rpx(16))) {
                            AppButton(
                                text = cancelText,
                                onClick = onDismiss,
                                style = ButtonStyle.SECONDARY,
                                block = false,
                                modifier = Modifier.weight(1f)
                            )
                            AppButton(
                                text = confirmText,
                                onClick = onConfirm,
                                style = style,
                                block = false,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 面板内容的错位淡入：每段延迟 40ms，只动 alpha 与小位移。 */
@Composable
private fun StaggeredFade(
    index: Int,
    content: @Composable () -> Unit
) {
    val m = LocalMetrics.current
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (reduced) return@LaunchedEffect
        kotlinx.coroutines.delay(60L + 40L * index)
        progress.animateTo(1f, Motion.overlay(false))
    }

    Box(
        modifier = Modifier.graphicsLayer {
            val p = progress.value.coerceIn(0f, 1f)
            alpha = p
            translationY = (1f - p) * m.rpx(16).toPx()
        }
    ) {
        content()
    }
}
