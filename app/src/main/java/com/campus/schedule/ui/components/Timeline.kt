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
 * 一条日程（v2.4：改为「每日事务」行）。
 *
 * ## 为什么不再是时间轴
 *
 * 之前左侧有一条 132rpx 的时间列 + 右侧卡片，本质是「时间轴视图」：
 * 纵向按整点铺开、卡片按小时定位。实际用下来问题是：
 *
 * * 一天只有两三件事时，屏幕大部分是空的刻度线，信息密度太低；
 * * 事件时间不规整（14:37 这种）时，卡片在刻度上永远是倾斜的，看着难受；
 * * 用户真正想要的是「今天要做什么，按先后顺序」，不是「这一天的时间分布」。
 *
 * 所以这里改成一行一条的清单：**左侧定宽时间槽 + 中间正文 + 右侧星标**，
 * 按开始时间从早到晚排（排序在 ViewModel 里做，见 `visibleItems`）。
 * 时间列只显示开始时间，跨天/全天显示「全天」。
 *
 * ## 对齐要点（一行内三列必须共用一条基线）
 *
 * * 三列都是 `Alignment.Top`，不再用 `padding(top)` 去「凑」中线；
 * * 时间槽的行高（48rpx）与正文标题的行高（46rpx）几乎相等，
 *   差值 2rpx 用时间槽的 `padding(top = 1rpx)` 抹平，肉眼对齐；
 * * 星标点击区 56rpx 比图标大一圈，图标本身在点击区正中，
 *   所以星标容器顶部对齐后，图标中心比标题中心低 5rpx，用 `padding(top = 6rpx)` 抵回。
 */
@Composable
fun TimelineRow(
    entry: TimelineEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleStar: (() -> Unit)? = null
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val interaction = rememberPressInteraction()
    val shape = RoundedCornerShape(m.radiusMd)

    // 色系：用户在编辑页选的「卡片色系」在这里才真正看得见。
    // 只有卡片左侧 6rpx 的短竖条着色，面积很小，
    // 既保留「每条日程有自己的颜色」的辨识度，又不会让整页花掉。
    val accentColor = Tokens.accentColor(entry.accent)
    val accentText = Tokens.accentTextColor(entry.accent)
    // 进行中的条目换成当前色板的浅底，一眼能看到「正在发生的事」
    val ongoing = entry.statusTone == TagTone.ONGOING

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (ongoing) p.brandTint.copy(alpha = 0.7f) else Tokens.PaperRaised)
            .drawBehind {
                // 左侧短竖条：上下内缩，圆头，不顶到圆角上
                val w = m.rpx(6).toPx()
                val inset = m.rpx(18).toPx()
                drawRoundRect(
                    color = if (ongoing) p.brand else accentColor,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, inset),
                    size = androidx.compose.ui.geometry.Size(
                        w,
                        (size.height - inset * 2).coerceAtLeast(0f)
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2f, w / 2f)
                )
            }
            .border(
                width = m.rpx(2),
                color = when {
                    entry.conflict -> Tokens.Gold
                    ongoing -> p.brand.copy(alpha = 0.35f)
                    else -> Tokens.Line
                },
                shape = shape
            )
            .pressHighlight(interaction, shape)
            .clickableNoRipple(interaction, onClick = onClick)
            .padding(
                start = m.rpx(22),
                end = m.rpx(18),
                top = m.rpx(18),
                bottom = m.rpx(18)
            ),
        verticalAlignment = Alignment.Top
    ) {
        // ---- 时间槽 ----
        // 定宽 120rpx：能放下「08:00」四位数，两行「09:55 / 已结束」也不会撑开。
        Column(
            modifier = Modifier
                .width(m.rpx(120))
                .padding(top = m.rpx(1)),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = entry.timeText,
                style = m.numeric.copy(
                    fontSize = m.sp(30),
                    lineHeight = m.sp(46),
                    color = if (ongoing) p.brandDeep else accentText,
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
                .padding(start = m.rpx(14)),
            verticalArrangement = Arrangement.spacedBy(m.rpx(6))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(m.rpx(12)),
                // 标题与胶囊底边对齐：胶囊高度固定，Top 对齐会让它压在标题上方
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = entry.title,
                    style = m.bodyLg.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        // 与时间槽首行同高，两列第一行落在同一条基线上
                        lineHeight = m.sp(46)
                    ),
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                StatusPill(text = entry.status, tone = entry.statusTone)
            }

            val detail = listOf(entry.meta, entry.location)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = m.caption.copy(color = Tokens.InkSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (onToggleStar != null) {
            // 星标与标题首行视觉居中对齐（见上面的对齐说明）
            Box(
                modifier = Modifier
                    .padding(start = m.rpx(4), top = m.rpx(6))
            ) {
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
