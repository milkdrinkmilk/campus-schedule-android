package com.campus.schedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.campus.schedule.ui.icons.AppIcons
import com.campus.schedule.ui.theme.LocalMetrics
import com.campus.schedule.ui.theme.LocalPalette
import com.campus.schedule.ui.theme.Motion
import com.campus.schedule.ui.theme.Tokens

/** 时间轴一行所需的全部展示数据。 */
data class TimelineEntry(
    val id: String,
    val title: String,
    val timeText: String,
    val endText: String,
    val meta: String,
    val location: String,
    val status: String,
    val statusTone: TagTone,
    val ongoing: Boolean,
    val starred: Boolean,
    val conflict: Boolean,
    /** 卡片色系（coral / sage / gold / ink），决定时间列文字与卡片左侧色条的颜色。 */
    val accent: String = "coral"
)

/**
 * 日期导航（`.timeline-nav`）。
 *
 * 左右各一个圆形箭头按钮，中间是日期 + 提示。
 */
@Composable
fun TimelineNav(
    dateLabel: String,
    hint: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val m = LocalMetrics.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = m.rpx(88))
            .padding(bottom = m.rpx(16)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        NavArrowButton(onClick = onPrev, forward = false)
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(m.rpx(2))
        ) {
            Text(
                text = dateLabel,
                style = m.h3.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            )
            Text(text = hint, style = m.micro.copy(fontSize = m.sp(24), lineHeight = m.sp(36)))
        }
        NavArrowButton(onClick = onNext, forward = true)
    }
}

@Composable
private fun NavArrowButton(
    onClick: () -> Unit,
    forward: Boolean,
    modifier: Modifier = Modifier
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val interaction = rememberPressInteraction()
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(m.rpx(92))
            .clip(shape)
            .background(Tokens.PaperRaised)
            .border(m.rpx(2), Tokens.Line, shape)
            // 按压高亮必须用和本体一致的圆：之前用 radiusMd 的圆角矩形，
            // 按下时会在圆钮内部露出一块方形色块，正是「图标不齐」的观感来源之一
            .pressHighlight(interaction, shape)
            .rippleClick(interaction, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(
                if (forward) AppIcons.chevronRightBrand else AppIcons.chevronLeftBrand
            ),
            contentDescription = if (forward) "下一天" else "上一天",
            tint = p.brand,
            modifier = Modifier.size(m.iconMd)
        )
    }
}

/**
 * 一条日程（v2.4.1：无框清单行）。
 *
 * ## 为什么不再是卡片
 *
 * v2.4 的行是「每条日程一个圆角矩形」：白底 + 2rpx 描边 + 左侧色条。
 * 一天十几件事排下来，就是十几个一模一样的盒子摞在一起 —— 用户说的
 * 「项目框架过于呆板」正是这个：**框把注意力从内容上抢走了**。
 *
 * v2.4.1 改成「清单」而不是「卡片墙」：
 *
 * * 整行**没有边框、没有底色**，只有一条贯穿的 2rpx 发丝分隔线
 *   （最后一项不画），像印刷时刻在纸上的栏线；
 * * 时间槽右对齐成一条竖线，日期数字与时间共用同一列 ——
 *   扫读节奏由「左边的数字塔」建立，而不是靠一个个框；
 * * 状态不再装进胶囊，直接用着色文字 + 一个小状态点；
 * * 进行中的条目用**左侧 6rpx 品牌竖条 + 极浅底**标出来，
 *   全页最多一处「有底色」，视线自然落在当下；
 * * 冲突是唯一保留描边的状态（金色细线），因为它需要被立刻看见。
 *
 * ## 对齐要点
 *
 * * 三列 `Alignment.Top`，首行行高统一 46rpx，基线天然齐平；
 * * 时间列定宽 132rpx 右对齐；正文左起 28rpx；星标点击区 56rpx；
 * * 分隔线在 padding 之外绘制（inset 左右各 0），保证通栏。
 */

/**
 * v2.5 日程条目 —— 「便签卡」风格。
 *
 * 设计意图（对应用户反馈「边框全部重做、换风格、复杂有趣、玩梗」）：
 * - 不再是 v2.4.1 的无框清单行（太素），也不是 v2.3 的描边卡片（太呆板）。
 * - 改成一张微微着色的便签：左侧 4rpx 圆头色条交代分类，右上角一个 emoji
 *   小印章给点趣味，底部 2rpx 虚线像手撕便签的齿孔。
 * - 三个 Tab 各自有不同的装饰语言：日程页是便签，概况页是数据面板，
 *   我的页是身份卡 —— 视觉上不再「三页长得一样」。
 */
@Composable
fun TimelineRow(
    entry: TimelineEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleStar: (() -> Unit)? = null,
    showDivider: Boolean = true
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val interaction = rememberPressInteraction()

    val accentColor = Tokens.accentColor(entry.accent)
    val accentTint = Tokens.accentTint(entry.accent)
    val accentText = Tokens.accentTextColor(entry.accent)
    val ongoing = entry.statusTone == TagTone.ONGOING
    val ended = entry.statusTone == TagTone.ENDED

    // 玩梗：根据状态选一个 emoji 印章，让列表不那么「工具感」
    val moodEmoji = when {
        ongoing -> "🔥"
        entry.conflict -> "⚠️"
        entry.starred -> "⭐"
        ended -> "✅"
        else -> "📌"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(m.radiusMd))
            .background(accentTint.copy(alpha = if (ongoing) 0.55f else 0.28f))
            .drawBehind {
                // 左侧色条：4rpx 圆头竖线，比 v2.4.1 的 6rpx 细一点，更精致
                val barW = m.rpx(4).toPx()
                val barInset = m.rpx(10).toPx()
                drawRoundRect(
                    color = accentColor,
                    topLeft = androidx.compose.ui.geometry.Offset(barInset, barInset),
                    size = androidx.compose.ui.geometry.Size(
                        barW,
                        (size.height - barInset * 2).coerceAtLeast(0f)
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f, barW / 2f)
                )
                // 底部虚线：手撕便签的齿孔感，比实线有趣
                if (showDivider) {
                    val dashW = m.rpx(8).toPx()
                    val gapW = m.rpx(6).toPx()
                    val y = size.height - m.rpx(1).toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawRect(
                            color = Tokens.Line,
                            topLeft = androidx.compose.ui.geometry.Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(dashW, m.rpx(1).toPx())
                        )
                        x += dashW + gapW
                    }
                }
            }
            .then(
                if (entry.conflict) {
                    Modifier.border(
                        width = m.rpx(2),
                        color = Tokens.Gold,
                        shape = RoundedCornerShape(m.radiusMd)
                    )
                } else {
                    Modifier
                }
            )
            .pressHighlight(interaction, RoundedCornerShape(m.radiusMd))
            .clickableNoRipple(interaction, onClick = onClick)
            .padding(
                start = m.rpx(28),
                end = m.rpx(18),
                top = m.rpx(20),
                bottom = m.rpx(20)
            ),
        verticalAlignment = Alignment.Top
    ) {
        // ---- 时间槽 ----
        Column(
            modifier = Modifier.width(m.rpx(128)),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = entry.timeText,
                style = m.numeric.copy(
                    fontSize = m.sp(30),
                    lineHeight = m.sp(46),
                    color = when {
                        ongoing -> p.brandDeep
                        ended -> Tokens.InkFaint
                        else -> accentText
                    },
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                ),
                maxLines = 1
            )
            if (entry.endText.isNotBlank()) {
                Text(
                    text = entry.endText,
                    style = m.numeric.copy(
                        fontSize = m.sp(24),
                        lineHeight = m.sp(32),
                        color = Tokens.InkMuted
                    ),
                    modifier = Modifier.padding(top = m.rpx(2)),
                    maxLines = 1
                )
            }
        }

        // ---- 正文 ----
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = m.rpx(24)),
            verticalArrangement = Arrangement.spacedBy(m.rpx(6))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(m.rpx(10)),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = entry.title,
                    style = m.bodyLg.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        lineHeight = m.sp(46),
                        color = if (ended) Tokens.InkSecondary else Tokens.Ink
                    ),
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // emoji 印章：比纯文字状态更有「人味」
                Text(
                    text = moodEmoji,
                    style = m.meta.copy(fontSize = m.sp(28), lineHeight = m.sp(36)),
                    modifier = Modifier.padding(bottom = m.rpx(2))
                )
            }

            // 状态 + 详情合并成一行，减少视觉层级
            val detailParts = listOfNotNull(
                entry.status.takeIf { it.isNotBlank() },
                entry.meta.takeIf { it.isNotBlank() },
                entry.location.takeIf { it.isNotBlank() }
            )
            if (detailParts.isNotEmpty()) {
                Text(
                    text = detailParts.joinToString(" · "),
                    style = m.caption.copy(
                        color = if (ongoing) p.brandDeep else Tokens.InkSecondary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (onToggleStar != null) {
            Box(modifier = Modifier.padding(start = m.rpx(4), top = m.rpx(4))) {
                StarButton(starred = entry.starred, onClick = onToggleStar)
            }
        }
    }
}

/** 星标按钮：切换时回弹一下。 */
@Composable
fun StarButton(
    starred: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val m = LocalMetrics.current
    val interaction = rememberPressInteraction()
    Box(
        modifier = modifier
            .size(m.rpx(56))
            .clip(CircleShape)
            .pressHighlight(interaction, RoundedCornerShape(m.radiusMd))
            .rippleClick(interaction, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(AppIcons.star),
            contentDescription = if (starred) "取消关注" else "特别关注",
            tint = if (starred) Tokens.Gold else Tokens.InkFaint,
            modifier = Modifier.size(m.rpx(36))
        )
    }
}

/** 冲突明细（`.timeline-conflict__detail`）。 */
@Composable
fun ConflictDetail(
    pairs: List<String>,
    modifier: Modifier = Modifier
) {
    val m = LocalMetrics.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = m.rpx(16))
            .clip(RoundedCornerShape(bottomStart = m.radiusSm, bottomEnd = m.radiusSm))
            .background(Tokens.GoldTint)
            .padding(horizontal = m.rpx(24))
    ) {
        pairs.forEach { pair ->
            Text(
                text = pair,
                style = m.caption.copy(color = Tokens.GoldText),
                modifier = Modifier.padding(top = m.rpx(8))
            )
        }
        Spacer(Modifier.height(m.rpx(8)))
    }
}
