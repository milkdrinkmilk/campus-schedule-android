package com.campus.schedule.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.campus.schedule.core.CoreDate
import com.campus.schedule.data.ScheduleItem
import com.campus.schedule.ui.theme.LocalMetrics
import com.campus.schedule.ui.theme.LocalPalette
import com.campus.schedule.ui.theme.LocalReducedMotion
import com.campus.schedule.ui.theme.Motion
import com.campus.schedule.ui.theme.Tokens
import kotlin.math.max

/** 课表时段。与小程序 `schedule-week-view.js` 的 PERIODS 完全一致。 */
data class Period(val start: Int, val end: Int, val startLabel: String, val endLabel: String)

val PERIODS: List<Period> = listOf(
    Period(8 * 60, 9 * 60 + 40, "08:00", "09:40"),
    Period(9 * 60 + 55, 11 * 60 + 35, "09:55", "11:35"),
    Period(13 * 60 + 30, 15 * 60 + 10, "13:30", "15:10"),
    Period(15 * 60 + 25, 17 * 60 + 5, "15:25", "17:05"),
    Period(18 * 60 + 30, 20 * 60 + 10, "18:30", "20:10"),
    Period(20 * 60 + 15, 21 * 60 + 55, "20:15", "21:55")
)

/** 课表块（定位到网格里的一个日程）。 */
private data class WeekBlock(
    val item: ScheduleItem,
    val row: Int,
    val span: Int,
    val timeText: String
)

/**
 * 周课表（`.week-view`）。
 *
 * 布局与小程序一致：左侧时间轴 + 右侧 7 天列，横向滚动。
 * 条目按开始时间落在对应行，按时长跨行（span）。
 */
@Composable
fun WeekGrid(
    weekStart: Long,
    items: List<ScheduleItem>,
    selectedDate: Long,
    onSelectDay: (Long) -> Unit,
    onItemClick: (ScheduleItem) -> Unit,
    modifier: Modifier = Modifier,
    nowMillis: Long = System.currentTimeMillis(),
    conflictIds: Set<String> = emptySet()
) {
    val m = LocalMetrics.current
    val days = remember(weekStart) { CoreDate.weekDays(weekStart) }
    val scroll = rememberScrollState()

    val cellW = m.rpx(148)
    val cellH = m.rpx(132)
    val gap = m.rpx(8)
    val headerH = m.rpx(88)
    val gutterW = m.rpx(96)
    val bodyH = cellH * PERIODS.size + gap * (PERIODS.size - 1)

    // 计算条目落点
    val (blocks, outOfRange) = remember(weekStart, items) {
        val placed = mutableListOf<WeekBlock>()
        val outside = mutableListOf<ScheduleItem>()
        items.forEach { item ->
            if (item.startTime <= 0L) return@forEach
            val dayIndex = ((CoreDate.startOfDay(item.startTime) - weekStart) / CoreDate.DAY).toInt()
            val p = CoreDate.parts(item.startTime)
            val startMinute = p.hour * 60 + p.minute
            val lastStart = PERIODS.last().start
            if (item.isAllDay || dayIndex < 0 || dayIndex > 6 ||
                startMinute < PERIODS.first().start || startMinute > lastStart + 60
            ) {
                outside += item
                return@forEach
            }
            var rowIndex = 0
            for (i in PERIODS.indices) {
                if (startMinute >= PERIODS[i].start) rowIndex = i
            }
            val duration = max(0L, item.endTime - item.startTime) / 60000L
            val span = minOf(PERIODS.size - rowIndex, max(1L, (duration + 99) / 100).toInt())
            placed += WeekBlock(
                item = item,
                row = rowIndex,
                span = span,
                timeText = CoreDate.formatTime(item.startTime)
            )
        }
        placed to outside
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
        ) {
            // ---- 左侧时间轴 ----
            Column(modifier = Modifier.width(gutterW)) {
                Spacer(Modifier.height(headerH))
                PERIODS.forEach { period ->
                    // 两行时间的总高 = 32 + 30 = 62rpx，始终贴在这一格的**上半部**：
                    //   * 开始时间与格子上边线顶齐 → 眼睛左右一扫，时间与格线对得上；
                    //   * 结束时间紧随其后，不与下一格的开始时间挤在一起。
                    // 行高写成常量让两行块的总高可预测（之前 32+32=64 但靠 padding(top=8) 推，
                    // 变成 8+64=72，格子只高 132，最后一行会显得飘）。
                    Column(
                        modifier = Modifier
                            .height(cellH)
                            .padding(top = m.rpx(2), end = m.rpx(12)),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = period.startLabel,
                            style = m.micro.copy(
                                fontSize = m.sp(24),
                                lineHeight = m.sp(30),
                                color = Tokens.InkSecondary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = period.endLabel,
                            style = m.micro.copy(
                                fontSize = m.sp(24),
                                lineHeight = m.sp(30),
                                color = Tokens.InkMuted
                            ),
                            modifier = Modifier.padding(top = m.rpx(2)),
                            maxLines = 1
                        )
                    }
                }
            }

            // ---- 7 天列 ----
            // 先按天分组：原来在 7 次循环里各 filter 一遍，是 O(7n)
            val blocksByDay = remember(blocks, weekStart) {
                blocks.groupBy { ((CoreDate.startOfDay(it.item.startTime) - weekStart) / CoreDate.DAY).toInt() }
            }
            Row {
                days.forEachIndexed { index, day ->
                    val isToday = CoreDate.isToday(day.timestamp, nowMillis)
                    val isSelected = CoreDate.isSameDay(day.timestamp, selectedDate)
                    val dayBlocks = blocksByDay[index].orEmpty()

                    Column(modifier = Modifier.width(cellW)) {
                        // 表头
                        DayHeader(
                            weekday = day.weekday,
                            dayNumber = day.day,
                            isToday = isToday,
                            isSelected = isSelected,
                            onClick = { onSelectDay(day.timestamp) }
                        )

                        // 网格
                        Box(
                            modifier = Modifier
                                .height(bodyH)
                                .fillMaxWidth()
                        ) {
                            // 底格：一次 drawBehind 画 6 个圆角格，替代 6 个 composable
                            GridBackdrop(isSelected = isSelected, cellH = cellH, gap = gap)

                            // 条目块
                            dayBlocks.forEach { block ->
                                WeekBlockView(
                                    block = block,
                                    top = (cellH + gap) * block.row,
                                    height = cellH * block.span + gap * (block.span - 1),
                                    nowMillis = nowMillis,
                                    conflict = block.item.id in conflictIds,
                                    onClick = { onItemClick(block.item) }
                                )
                            }

                            // 当前时间线
                            if (isToday) {
                                CurrentTimeLine(nowMillis = nowMillis, cellH = cellH, gap = gap)
                            }
                        }
                    }
                }
            }
        }

        // 超出时段的条目
        if (outOfRange.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = m.rpx(16))) {
                Text(
                    text = "不在课表时段内",
                    style = m.micro.copy(fontSize = m.sp(24), lineHeight = m.sp(36), color = Tokens.InkMuted),
                    modifier = Modifier.padding(bottom = m.rpx(8))
                )
                outOfRange.forEach { item ->
                    OutRangeChip(item = item, onClick = { onItemClick(item) })
                }
            }
        }
    }
}

/** 表头单元格。 */
@Composable
private fun DayHeader(
    weekday: String,
    dayNumber: String,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val interaction = rememberPressInteraction()
    val shape = RoundedCornerShape(m.radiusSm)

    // 按压高亮用和本体一致的圆角（radiusSm），不要用更大的 radiusMd，
    // 否则按下时会在表头四角露出方角底色。
    Box(
        modifier = Modifier
            .width(m.rpx(148))
            .height(m.rpx(88))
            .clip(shape)
            .then(if (isSelected) Modifier.background(p.brandGradient) else Modifier)
            .pressHighlight(interaction, shape)
            .rippleClick(interaction, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // 两行字的行高写死（32 + 34 = 66rpx）+ 间距 2rpx = 68rpx，
        // 在 88rpx 的表头里上下各留 10rpx —— 之前 34+36+4=74，上下只剩 7，
        // 稍有大字号或字体差异就会顶到边缘，看起来「往上飘」。
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(m.rpx(2))
        ) {
            Text(
                text = weekday,
                style = m.micro.copy(
                    fontSize = m.sp(22),
                    lineHeight = m.sp(32),
                    color = when {
                        isSelected -> Tokens.PaperRaised
                        isToday -> p.brandDeep
                        else -> Tokens.InkMuted
                    }
                ),
                maxLines = 1
            )
            Text(
                text = dayNumber,
                style = m.numeric.copy(
                    fontSize = m.sp(28),
                    lineHeight = m.sp(34),
                    color = when {
                        isSelected -> Tokens.PaperRaised
                        isToday -> p.brandDeep
                        else -> Tokens.InkSecondary
                    },
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                ),
                maxLines = 1
            )
        }
        // 今天的小圆点：与日期数字右边对齐，下移到日期那一行的高度中心
        if (isToday && !isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(start = m.rpx(56), top = m.rpx(26))
                    .size(m.rpx(8))
                    .clip(CircleShape)
                    .background(p.brand)
            )
        }
    }
}

/** 网格底格：一次 drawBehind 画满 6 行圆角格（原来每列 6 个 composable，全场 42 个）。 */
@Composable
private fun GridBackdrop(isSelected: Boolean, cellH: Dp, gap: Dp) {
    val m = LocalMetrics.current
    val fill = if (isSelected) Tokens.PaperTint else Tokens.PaperRaised
    val stroke = if (isSelected) Tokens.LineStrong else Tokens.Line
    val radius = m.radiusSm
    val lineWidth = m.rpx(2)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val r = CornerRadius(radius.toPx(), radius.toPx())
                val h = cellH.toPx()
                val step = h + gap.toPx()
                val w = size.width
                // 半线宽内缩，避免描边被裁到一半
                val inset = (lineWidth.toPx() / 2f).coerceAtLeast(0f)
                for (i in PERIODS.indices) {
                    val top = step * i + inset
                    val cellH2 = h - inset * 2f
                    drawRoundRect(
                        color = fill,
                        topLeft = Offset(inset, top),
                        size = Size((w - inset * 2f).coerceAtLeast(0f), cellH2),
                        cornerRadius = r
                    )
                    drawRoundRect(
                        color = stroke,
                        topLeft = Offset(inset, top),
                        size = Size((w - inset * 2f).coerceAtLeast(0f), cellH2),
                        cornerRadius = r,
                        style = Stroke(width = lineWidth.toPx())
                    )
                }
            }
    )
}

/** 课表里的日程块。 */
@Composable
private fun WeekBlockView(
    block: WeekBlock,
    top: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    nowMillis: Long,
    conflict: Boolean,
    onClick: () -> Unit
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val density = LocalDensity.current
    val interaction = rememberPressInteraction()
    val shape = RoundedCornerShape(m.radiusSm)
    val accent = Tokens.accentColor(block.item.accent)
    val ongoing = block.item.startTime <= nowMillis && block.item.endTime >= nowMillis

    // v2.4.1 的课块画法（与「每日事务」清单同一套语言）：
    //   * **不再描边** —— 格子本身已有 2rpx 线，块再包一圈就是双重框，
    //     这是「呆板」的另一半来源；块只靠着色纸自己撑起辨识度；
    //   * 底色加深到 10%，左侧 6rpx 实色竖条保留，进行中换品牌浅底 + 圆点；
    //   * 冲突（同格叠放）仍用金色细线，因为那是唯一需要立刻看见的状态。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // v2.6：120Hz 优化。offset 会触发布局测量，在课块密集时导致掉帧。
            // 改用 graphicsLayer.translationY，纯合成层操作，不触发重组。
            .graphicsLayer { translationY = top.toPx() }
            .height(height)
            .clip(shape)
            .background(if (ongoing) p.brandTint else accent.copy(alpha = 0.10f))
            .then(
                if (conflict) {
                    Modifier.border(m.rpx(2), Tokens.Gold, shape)
                } else {
                    Modifier
                }
            )
            .drawBehind {
                val w = m.rpx(6).toPx()
                drawRoundRect(
                    color = accent,
                    size = androidx.compose.ui.geometry.Size(w, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2f, w / 2f)
                )
            }
            .pressHighlight(interaction, shape)
            .rippleClick(interaction, onClick = onClick)
            // 左边距 14rpx 是留给那条 6rpx 竖条的呼吸空间，
            // 时间与标题从同一条竖线起笔，行高 32 + 8 = 40，与「一个 40rpx 小色块」的密度一致
            .padding(start = m.rpx(16), end = m.rpx(12), top = m.rpx(10), bottom = m.rpx(8))
    ) {
        Text(
            text = block.timeText,
            style = m.micro.copy(
                fontSize = m.sp(22),
                lineHeight = m.sp(30),
                color = Tokens.InkSecondary
            ),
            maxLines = 1
        )
        Text(
            text = block.item.title,
            style = m.micro.copy(
                fontSize = m.sp(24),
                lineHeight = m.sp(32),
                color = Tokens.Ink,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            ),
            modifier = Modifier.padding(top = m.rpx(2)),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (ongoing) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = m.rpx(10), end = m.rpx(6))
                    .size(m.rpx(8))
                    .clip(CircleShape)
                    .background(p.brand)
            )
        }
    }
}

/**
 * 把「当天第几分钟」映射到网格的纵向偏移。
 *
 * 课表不是连续的：11:35–13:30、17:05–18:30 是空档，每行之间还有 8rpx 间距。
 * 原来用 (minute-08:00)/(21:55-08:00) 线性映射，红线会整体偏移；
 * 这里按时段分段——时段内按 cellH 线性，空档按 gap 线性。
 */
private fun periodOffset(minute: Int, cellH: Dp, gap: Dp): Dp {
    if (minute <= PERIODS.first().start) return 0.dp
    PERIODS.forEachIndexed { i, period ->
        val top = (cellH + gap) * i
        if (minute <= period.end) {
            val f = ((minute - period.start).toFloat() / (period.end - period.start).coerceAtLeast(1))
                .coerceIn(0f, 1f)
            return top + cellH * f
        }
        val next = PERIODS.getOrNull(i + 1) ?: return top + cellH
        if (minute < next.start) {
            val f = ((minute - period.end).toFloat() / (next.start - period.end).coerceAtLeast(1))
                .coerceIn(0f, 1f)
            return top + cellH + gap * f
        }
    }
    return (cellH + gap) * PERIODS.size - gap
}

/** 当前时间线（今天的列里一条细红线 + 呼吸圆点）。 */
@Composable
private fun CurrentTimeLine(nowMillis: Long, cellH: Dp, gap: Dp) {
    val m = LocalMetrics.current
    val reduced = LocalReducedMotion.current
    val p = CoreDate.parts(nowMillis)
    val minuteOfDay = p.hour * 60 + p.minute
    if (minuteOfDay < PERIODS.first().start || minuteOfDay > PERIODS.last().end) return

    val density = LocalDensity.current
    val y = periodOffset(minuteOfDay, cellH, gap)
    val dotSize = m.rpx(8)

    // 呼吸动画放在叶子节点里，只有这个圆点会重组
    val breathe = if (reduced) {
        1f
    } else {
        // v2.6：120Hz 优化。
        // 呼吸动画周期从 1400ms 缩短到 900ms，让高刷屏上过渡更丝滑；
        // 同时用 LinearEasing 替代 EaseInOut，避免在峰值处减速导致的「顿挫感」。
        // alpha 范围收窄到 0.5→1，减少低透明度帧的视觉闪烁。
        rememberInfiniteTransition(label = "now-dot")
            .animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "now-dot-alpha"
            ).value
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(dotSize)
            .graphicsLayer {
                // 只改 translationY，不触发布局
                translationY = with(density) { (y - dotSize / 2).toPx() }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer { alpha = breathe }
                .clip(CircleShape)
                .background(Tokens.Danger)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(m.rpx(2))
                .background(Tokens.Danger.copy(alpha = 0.7f))
        )
    }
}

/** 超出时段的条目小卡。 */
@Composable
private fun OutRangeChip(item: ScheduleItem, onClick: () -> Unit) {
    val m = LocalMetrics.current
    val interaction = rememberPressInteraction()
    val shape = RoundedCornerShape(m.radiusSm)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = m.rpx(8))
            .clip(shape)
            .background(Tokens.PaperRaised)
            .border(m.rpx(2), Tokens.Line, shape)
            .pressHighlight(interaction, shape)
            .rippleClick(interaction, onClick = onClick)
            .padding(horizontal = m.rpx(16), vertical = m.rpx(10)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(m.rpx(12))
    ) {
        Text(
            text = if (item.isAllDay) "全天" else CoreDate.formatDate(item.startTime),
            style = m.micro.copy(fontSize = m.sp(24), lineHeight = m.sp(36), color = Tokens.InkMuted),
            maxLines = 1
        )
        Text(
            text = item.title,
            style = m.micro.copy(
                fontSize = m.sp(24),
                lineHeight = m.sp(36),
                color = Tokens.Ink,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            ),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
