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
            tint = Tokens.Brand,
            modifier = Modifier.size(m.iconMd)
        )
    }
}

/**
 * 时间轴条目（`.timeline-row`）。
 *
 * 左侧固定 120rpx 的时间列，右侧是卡片。
 * 卡片顶部是标题 + 状态胶囊，下面是元信息与地点。
 *
 * 文字对齐要点：
 * * 时间列的起止时间用 `includeFontPadding = false` 的等宽数字；
 * * 标题与状态胶囊都用 `Alignment.Top` 对齐，不会因胶囊高度不同而错位。
 */
@Composable
fun TimelineRow(
    entry: TimelineEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleStar: (() -> Unit)? = null
) {
    val m = LocalMetrics.current
    val interaction = rememberPressInteraction()
    val cardShape = RoundedCornerShape(m.radiusMd)

    // 色系：用户在编辑页选的「卡片色系」在这里才真正看得见，
    // 否则选了颜色却毫无反馈，整张列表全是白卡片，显得单调。
    val accentColor = Tokens.accentColor(entry.accent)
    val accentText = Tokens.accentTextColor(entry.accent)

    // 时间列与卡片的分界：时间列宽 132rpx，卡片左内边距 20rpx。
    // 「时间文字」到「卡片文字」的视觉间距 = 16（列间距）+ 20（内边距）= 36rpx，
    // 这个值写死成常量，卡片里每一行文字都从同一条竖线起笔。
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressHighlight(interaction, RoundedCornerShape(m.radiusLg))
            .rippleClick(interaction, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(m.rpx(16)),
        verticalAlignment = Alignment.Top
    ) {
        // ---- 时间列 ----
        // 不再用 padding(top) 硬对齐：开始时间的行高上沿（48rpx）居中到
        // 卡片第一行标题的行高中心，两者基线才会落在同一条水平线上。
        Column(
            modifier = Modifier
                .width(m.rpx(132))
                .padding(top = m.rpx(10)),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = entry.timeText,
                style = m.numeric.copy(
                    fontSize = m.sp(30),
                    lineHeight = m.sp(40),
                    color = accentText,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                ),
                maxLines = 1
            )
            if (entry.endText.isNotBlank()) {
                Text(
                    text = entry.endText,
                    // 结束时间也是等宽数字，且与开始时间左对齐 —— 行高统一 30，
                    // 两行之间不会因为行高不同而出现半个像素的偏移
                    style = m.numeric.copy(
                        fontSize = m.sp(24),
                        lineHeight = m.sp(30),
                        color = Tokens.InkMuted
                    ),
                    modifier = Modifier.padding(top = m.rpx(2)),
                    maxLines = 1
                )
            }
        }

        // ---- 卡片 ----
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(cardShape)
                // 卡片底色是该色系 5% 的着色纸，和外框 2rpx 线条一起构成「牌位」感；
                // 左侧竖条改成上下内缩的圆头短线，不再顶到圆角上（原来直角会溢出圆角）。
                .background(accentColor.copy(alpha = 0.05f))
                .drawBehind {
                    val w = m.rpx(6).toPx()
                    val inset = m.rpx(16).toPx()
                    drawRoundRect(
                        color = accentColor,
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
                    color = if (entry.conflict) {
                        Tokens.Gold
                    } else {
                        accentColor.copy(alpha = 0.28f)
                    },
                    shape = cardShape
                )
                .padding(
                    start = m.rpx(20),
                    end = m.rpx(20),
                    top = m.rpx(18),
                    bottom = m.rpx(18)
                ),
            verticalArrangement = Arrangement.spacedBy(m.rpx(6))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(m.rpx(12)),
                // 标题与胶囊用底边对齐：胶囊高度固定 48rpx，
                // Top 对齐会让胶囊的上下留白压在标题的第一行上方，看起来「浮着」
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = entry.title,
                    style = m.bodyLg.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        lineHeight = m.sp(48)
                    ),
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                StatusPill(text = entry.status, tone = entry.statusTone)
            }

            if (entry.meta.isNotBlank()) {
                Text(
                    text = entry.meta,
                    style = m.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (entry.location.isNotBlank()) {
                Text(
                    text = entry.location,
                    style = m.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (onToggleStar != null) {
            // 星标与标题第一行居中对齐：它是 56rpx 的点击区、36rpx 的图标，
            // 用 padding(top) 对齐到标题行高中心（48rpx/2 - 56rpx/2 + 18rpx 内边距）
            Box(modifier = Modifier.padding(top = m.rpx(8))) {
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
