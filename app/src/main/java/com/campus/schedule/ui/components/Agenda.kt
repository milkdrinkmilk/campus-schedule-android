package com.campus.schedule.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.campus.schedule.core.CoreDate
import com.campus.schedule.ui.icons.AppIcons
import com.campus.schedule.ui.theme.LocalMetrics
import com.campus.schedule.ui.theme.LocalPalette
import com.campus.schedule.ui.theme.LocalReducedMotion
import com.campus.schedule.ui.theme.Motion
import com.campus.schedule.ui.theme.Tokens

/**
 * 周条（v2.3 新增）。
 *
 * 一周七天的横条：左侧是上一周，右侧是下一周，中间七天各一格。
 *
 * 设计取舍：这里**不**画课表那种「选中就填满整格」的块，而是每天一个
 * 40rpx 的圆 + 下方一颗状态点。原因是从周末看，七个实心块会把整条
 * 视觉重量压在中间，和上方报头抢注意力；圆点轻，翻周时才不会被
 * 一排方块拖着晃。
 *
 * 翻周时整条横移 —— 方向和翻页一致，用户能看出「往左翻的是下一周」。
 */
@Composable
fun WeekStrip(
    weekStart: Long,
    selectedDate: Long,
    nowMillis: Long,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onThisWeek: () -> Unit,
    onSelectDay: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val reduced = LocalReducedMotion.current
    val isCurrentWeek = CoreDate.startOfWeek(nowMillis) == CoreDate.startOfWeek(weekStart)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = m.rpx(8)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StripArrow(
                icon = AppIcons.chevronLeft,
                label = "上一周",
                onClick = onPrevWeek
            )
            AnimatedContent(
                targetState = weekStart,
                transitionSpec = {
                    val forward = targetState > initialState
                    if (reduced) {
                        fadeIn(tween(Motion.DurFast)) togetherWith fadeOut(tween(Motion.DurExit))
                    } else {
                        (slideInHorizontally(
                            animationSpec = tween(Motion.DurBase, easing = Motion.EaseSoft)
                        ) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(Motion.DurBase))) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(Motion.DurExit, easing = Motion.EaseInOut)
                            ) { if (forward) -it / 6 else it / 6 } + fadeOut(tween(Motion.DurExit)))
                    }
                },
                label = "week-strip",
                modifier = Modifier.weight(1f)
            ) { start ->
                val weekDays = remember(start) {
                    val base = CoreDate.startOfWeek(start)
                    (0 until 7).map { base + it * CoreDate.DAY }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    weekDays.forEachIndexed { index, day ->
                        StripDay(
                            timestamp = day,
                            weekdayLabel = WEEK_INITIALS[index],
                            selected = CoreDate.isSameDay(day, selectedDate),
                            today = CoreDate.isToday(day, nowMillis),
                            onClick = { onSelectDay(day) }
                        )
                    }
                }
            }
            StripArrow(
                icon = AppIcons.chevronRight,
                label = "下一周",
                onClick = onNextWeek
            )
        }
        if (!isCurrentWeek) {
            Box(modifier = Modifier.padding(start = m.rpx(4), bottom = m.rpx(4))) {
                Text(
                    text = "回到本周",
                    style = m.micro.copy(
                        fontSize = m.sp(24),
                        lineHeight = m.sp(32),
                        color = p.brandDeep,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(m.radiusXs))
                        .clickableNoRipple(rememberPressInteraction(), onClick = onThisWeek)
                        .padding(horizontal = m.rpx(8), vertical = m.rpx(4)),
                    maxLines = 1
                )
            }
        } else {
            Spacer(modifier = Modifier.height(m.rpx(4)))
        }
    }
}

private val WEEK_INITIALS = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 周条上的一天。
 *
 * 三层状态叠加的顺序：日期数字 → 今天（一圈 / 实心圆）→ 选中（实心圆 + 反白）。
 * 选中和今天同时成立时只显示选中 —— 今天的信息由**圆环外的一颗小点**补足，
 * 这样「今天」和「选中的那天」不会互相抹掉。
 */
@Composable
private fun StripDay(
    timestamp: Long,
    weekdayLabel: String,
    selected: Boolean,
    today: Boolean,
    onClick: () -> Unit
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val reduced = LocalReducedMotion.current
    val interaction = rememberPressInteraction()
    val active = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = Motion.fast(reduced),
        label = "stripDay"
    )
    val shape = CircleShape
    val dot = 60

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(m.radiusMd))
            .pressHighlight(interaction, RoundedCornerShape(m.radiusMd))
            .clickableNoRipple(interaction, onClick = onClick)
            .padding(vertical = m.rpx(4), horizontal = m.rpx(2)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = weekdayLabel,
            style = m.micro.copy(
                fontSize = m.sp(24),
                lineHeight = m.sp(30),
                color = lerp(Tokens.InkMuted, p.brandDeep, active.value)
            ),
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(m.rpx(4)))
        Box(
            modifier = Modifier
                .size(m.rpx(dot))
                .clip(shape)
                .background(
                    when {
                        selected -> p.brand
                        today -> p.brandTint
                        else -> Color.Transparent
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = CoreDate.formatDayNumber(timestamp),
                style = m.numeric.copy(
                    fontSize = m.sp(28),
                    lineHeight = m.sp(36),
                    color = when {
                        selected -> Tokens.PaperRaised
                        today -> p.brandDeep
                        else -> Tokens.InkSecondary
                    },
                    fontWeight = if (selected || today) FontWeight.Bold else FontWeight.Normal
                ),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
        Box(
            modifier = Modifier
                .padding(top = m.rpx(6))
                .size(m.rpx(6))
                .graphicsLayer { alpha = if (today && !selected) 1f else 0f }
                .clip(CircleShape)
                .background(p.brand)
        )
    }
}

/** 周条两侧的翻周箭头：比触摸目标小一圈，靠按下高亮补足反馈。 */
@Composable
private fun StripArrow(icon: Int, label: String, onClick: () -> Unit) {
    val m = LocalMetrics.current
    val interaction = rememberPressInteraction()
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(m.rpx(64))
            .clip(shape)
            .pressHighlight(interaction, shape)
            .clickableNoRipple(interaction, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = Tokens.InkSecondary,
            modifier = Modifier.size(m.rpx(28))
        )
    }
}

// ---------------------------------------------------------------------------
// 时间轴：按天排布的时间刻度 + 事件块
// ---------------------------------------------------------------------------

/**
 * 单日时间轴（v2.3 新增）。
 *
 * 和原有的 [TimelineRow] 卡片列表互补：卡片列表信息密度高但看不出
 * 「一天长什么样」，这根时间轴把当天的事件按真实时间位置画出来，
 * 一眼就能看到空档与重叠。
 *
 * 关键实现点：
 * * 整根轴只用一个 [Box] + 子块 [graphicsLayer] 定位，不产生额外布局层次；
 * * 小时刻度线用 `drawBehind` 一次画完，而不是每行一个 composable；
 * * 轴的范围取当天的「最早与最晚事件」各自向外扩一档，而不是固定 0–24 点，
 *   否则大多数学生的一天只会挤在中间几个像素里。
 */
@Composable
fun TodayTimeline(
    dayStart: Long,
    items: List<com.campus.schedule.data.ScheduleItem>,
    nowMillis: Long,
    onClick: (com.campus.schedule.data.ScheduleItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val m = LocalMetrics.current
    val sorted = remember(items) { items.sortedBy { it.startTime } }
    if (sorted.isEmpty()) return

    val bounds = remember(dayStart, sorted) { timelineBounds(dayStart, sorted) }
    val slotHeight = m.rpx(96)
    val totalHeight = slotHeight * (bounds.endHour - bounds.startHour).toFloat()

    Row(modifier = modifier.padding(top = m.rpx(8), bottom = m.rpx(16))) {
        TimelineTimeRail(
            startHour = bounds.startHour,
            endHour = bounds.endHour,
            slotHeight = slotHeight
        )
        TimelineDayCell(
            dayStart = dayStart,
            items = sorted,
            startHour = bounds.startHour,
            endHour = bounds.endHour,
            slotHeight = slotHeight,
            totalHeight = totalHeight,
            nowMillis = nowMillis,
            onClick = onClick,
            modifier = Modifier.weight(1f)
        )
    }
}

private data class TimelineBounds(val startHour: Int, val endHour: Int)

/**
 * 计算轴的上下界。
 *
 * 向下取整到整点、向上取整到整点，再各留一档余量；最少显示 4 小时，
 * 避免只有一场 30 分钟会议时整根轴被拉成一条细线。
 */
private fun timelineBounds(
    dayStart: Long,
    items: List<com.campus.schedule.data.ScheduleItem>
): TimelineBounds {
    if (items.isEmpty()) return TimelineBounds(8, 18)
    var minHour = 24
    var maxHour = 0
    items.forEach { item ->
        val s = ((item.startTime - dayStart) / 3_600_000L).toInt().coerceIn(0, 24)
        val rawEnd = if (item.endTime > item.startTime) item.endTime else item.startTime + 3_600_000L
        val e = ((rawEnd - dayStart + 3_599_999L) / 3_600_000L).toInt().coerceIn(0, 24)
        if (s < minHour) minHour = s
        if (e > maxHour) maxHour = e
    }
    val start = (minHour - 1).coerceAtLeast(0)
    val end = (maxHour + 1).coerceAtMost(24)
    return if (end - start < 4) {
        val mid = (start + end) / 2
        TimelineBounds((mid - 2).coerceAtLeast(0), (mid + 2).coerceAtMost(24))
    } else {
        TimelineBounds(start, end)
    }
}

/**
 * 时间轴左侧的小时刻度。
 *
 * 每个整点一行文字 + 一条延伸到轴上的 2rpx 细线。线画在 [TimelineDayCell]
 * 的背景里，这里只负责文字，这样两层不会因为各自抗锯齿而错开半像素。
 */
@Composable
fun TimelineTimeRail(
    startHour: Int,
    endHour: Int,
    slotHeight: Dp,
    modifier: Modifier = Modifier
) {
    val m = LocalMetrics.current
    Column(
        modifier = modifier
            .width(m.rpx(88))
            .padding(end = m.rpx(12))
    ) {
        for (hour in startHour until endHour) {
            Box(
                modifier = Modifier
                    .height(slotHeight)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopEnd
            ) {
                Text(
                    text = hourLabel(hour),
                    style = m.micro.copy(
                        fontSize = m.sp(24),
                        lineHeight = m.sp(32),
                        color = Tokens.InkMuted
                    ),
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
            }
        }
    }
}

private fun hourLabel(hour: Int): String = hour.toString().padStart(2, '0') + ":00"

/**
 * 时间轴右侧的事件区。
 *
 * 刻度线用 `drawBehind` 一次画完：整根轴只有 1 个可绘制节点，
 * 而不是「每个小时一个 composable」。
 *
 * 事件块用 `graphicsLayer { translationY }` 定位，**不用** `offset`：
 * 前者只走绘制阶段，后者会重跑布局。
 */
@Composable
fun TimelineDayCell(
    dayStart: Long,
    items: List<com.campus.schedule.data.ScheduleItem>,
    startHour: Int,
    endHour: Int,
    slotHeight: Dp,
    totalHeight: Dp,
    nowMillis: Long,
    onClick: (com.campus.schedule.data.ScheduleItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val m = LocalMetrics.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val hours = (endHour - startHour).coerceAtLeast(1)
    val slotPx = with(density) { slotHeight.toPx() }
    val lineWidth = with(density) { m.rpx(2).toPx() }
    val lineColor = Tokens.Line

    Box(
        modifier = modifier
            .height(totalHeight)
            .drawBehind {
                // 整点刻度：每小时一条通栏细线
                for (i in 0..hours) {
                    val y = (slotPx * i).coerceAtMost(size.height - lineWidth)
                    drawRect(
                        color = lineColor,
                        topLeft = Offset(0f, y),
                        size = Size(size.width, lineWidth)
                    )
                }
            }
    ) {
        val dayEnd = dayStart + CoreDate.DAY
        items.forEach { item ->
            val start = item.startTime.coerceIn(dayStart, dayEnd)
            val rawEnd = if (item.endTime > item.startTime) item.endTime else start + 1_800_000L
            val end = rawEnd.coerceIn(dayStart, dayEnd)
            // 像素 → dp：偏移量本来就在绘制阶段消费，这里只做一次换算
            val topDp = with(density) { (slotPx * ((start - dayStart) / 3_600_000f)).toDp() }
            val heightDp = with(density) { (slotPx * ((end - start) / 3_600_000f)).toDp() }
                .coerceAtLeast(m.rpx(52))
            TimelineEventBlock(
                item = item,
                height = heightDp,
                top = topDp,
                ongoing = item.startTime <= nowMillis && item.endTime >= nowMillis,
                onClick = { onClick(item) }
            )
        }
    }
}

@Composable
private fun TimelineEventBlock(
    item: com.campus.schedule.data.ScheduleItem,
    height: Dp,
    top: Dp,
    ongoing: Boolean,
    onClick: () -> Unit
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val interaction = rememberPressInteraction()
    val accent = Tokens.accentColor(item.accent)
    val shape = RoundedCornerShape(m.radiusMd)
    val translatePx = with(androidx.compose.ui.platform.LocalDensity.current) { top.toPx() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = translatePx }
            .padding(vertical = m.rpx(2))
            .clip(shape)
            .background(if (ongoing) Tokens.PaperTint else accent.copy(alpha = 0.06f))
            .pressHighlight(interaction, shape)
            .clickableNoRipple(interaction, onClick = onClick)
            .height(height)
            .padding(horizontal = m.rpx(16), vertical = m.rpx(10))
    ) {
        Box(
            modifier = Modifier
                .width(m.rpx(5))
                .height(height)
                .clip(RoundedCornerShape(m.rpx(3)))
                .background(accent)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = m.rpx(14))
        ) {
            Text(
                text = item.title.ifBlank { "未命名日程" },
                style = m.meta.copy(
                    color = Tokens.Ink,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = m.sp(38)
                ),
                maxLines = 1
            )
            if (height >= m.rpx(64)) {
                Text(
                    text = if (item.isAllDay) "全天" else
                        "${CoreDate.formatTime(item.startTime)} – ${CoreDate.formatTime(item.endTime)}",
                    style = m.micro.copy(
                        fontSize = m.sp(24),
                        lineHeight = m.sp(32),
                        color = Tokens.InkMuted
                    ),
                    maxLines = 1
                )
            }
        }
        if (ongoing) {
            Box(
                modifier = Modifier
                    .padding(top = m.rpx(8), end = m.rpx(2))
                    .size(m.rpx(8))
                    .clip(CircleShape)
                    .background(p.brand)
            )
        }
    }
}