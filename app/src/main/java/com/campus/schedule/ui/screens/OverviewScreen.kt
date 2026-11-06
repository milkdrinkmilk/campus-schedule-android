package com.campus.schedule.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.campus.schedule.core.CoreDate
import com.campus.schedule.core.CoreFormat
import com.campus.schedule.data.ScheduleItem
import com.campus.schedule.ui.components.HintBar
import com.campus.schedule.ui.components.Masthead
import com.campus.schedule.ui.components.MetaLine
import com.campus.schedule.ui.components.RaisedCard
import com.campus.schedule.ui.components.SectionHeader
import com.campus.schedule.ui.components.TagTone
import com.campus.schedule.ui.components.appShadow
import com.campus.schedule.ui.components.pressHighlight
import com.campus.schedule.ui.components.pressScale
import com.campus.schedule.ui.components.rememberEnterTracker
import com.campus.schedule.ui.components.rememberPressInteraction
import com.campus.schedule.ui.components.rippleClick
import com.campus.schedule.ui.components.staggerEnter
import com.campus.schedule.ui.theme.LocalMetrics
import com.campus.schedule.ui.theme.Motion
import com.campus.schedule.ui.theme.Tokens
import kotlin.math.max

/**
 * 概况页 —— 对应小程序的 `pages/overview`。
 *
 * 离线个人版聚焦三件事：今天怎么样、本周分布如何、哪些要提醒。
 */
@Composable
fun OverviewScreen(
    items: List<ScheduleItem>,
    onItemClick: (ScheduleItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val m = LocalMetrics.current
    val tracker = rememberEnterTracker()
    val now = com.campus.schedule.ui.components.rememberNowMillis()

    val today = remember(items, now) {
        items.filter { CoreDate.isSameDay(it.startTime, now) }.sortedBy { it.startTime }
    }
    val current = remember(today, now) {
        today.firstOrNull { it.startTime <= now && it.endTime >= now }
    }
    val next = remember(today, now) {
        today.firstOrNull { it.startTime > now }
    }
    val weekStart = remember(now) { CoreDate.startOfWeek(now) }
    val weekDays = remember(weekStart) { CoreDate.weekDays(weekStart) }
    val weekCounts = remember(items, weekStart, now) {
        weekDays.map { day -> items.count { CoreDate.isSameDay(it.startTime, day.timestamp) } }
    }
    val categoryCounts = remember(items) {
        items.groupingBy { it.category.ifBlank { "其他" } }.eachCount()
            .toList()
            .sortedByDescending { it.second }
    }
    val reminders = remember(items, now) {
        items.filter { it.remindBefore != null && it.endTime >= now }
            .sortedBy { it.startTime }
            .take(5)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Tokens.TopGradient),
        contentPadding = PaddingValues(
            start = m.pagePadding,
            end = m.pagePadding,
            // 底栏高度已由 Scaffold 的 contentPadding 让出，这里只补一段呼吸空间，
            // 再加一次 tabBarHeight 会在列表末尾留出 160rpx 的死白。
            bottom = m.rpx(48)
        )
    ) {
        // ---------- 报头 ----------
        item(key = "masthead") {
            Masthead(
                kicker = "OVERVIEW / 第 ${CoreDate.isoWeekNumber(now)} 周",
                title = "概况",
                meta = "${CoreDate.formatDate(now)} · 共 ${items.size} 条日程"
            )
        }

        // ---------- 今日概览卡 ----------
        item(key = "hero") {
            OverviewHero(
                todayCount = today.size,
                currentTitle = current?.title,
                currentTime = current?.let { CoreDate.formatTimeRange(it.startTime, it.endTime, it.isAllDay) },
                nextTitle = next?.title,
                nextTime = next?.let { CoreDate.formatTime(it.startTime) },
                modifier = Modifier.staggerEnter(tracker, "hero", 0)
            )
        }

        // ---------- 本周分布 ----------
        item(key = "sec-week") {
            SectionHeader(
                eyebrow = "01 / 本周",
                title = "本周分布",
                modifier = Modifier
                    .staggerEnter(tracker, "sec-week", 1)
                    .padding(top = m.rpx(48))
            )
        }

        item(key = "week-chart") {
            WeekChart(
                dayLabels = weekDays.map { it.weekday.replace("周", "") },
                counts = weekCounts,
                todayIndex = weekDays.indexOfFirst { CoreDate.isToday(it.timestamp, now) },
                modifier = Modifier.staggerEnter(tracker, "week-chart", 2)
            )
        }

        // ---------- 分类分布 ----------
        item(key = "sec-cat") {
            SectionHeader(
                eyebrow = "02 / 分类",
                title = "分类分布",
                modifier = Modifier
                    .staggerEnter(tracker, "sec-cat", 3)
                    .padding(top = m.rpx(48))
            )
        }

        item(key = "cat-list") {
            RaisedCard(
                modifier = Modifier.staggerEnter(tracker, "cat-list", 4),
                contentPadding = m.rpx(32)
            ) {
                if (categoryCounts.isEmpty()) {
                    Text(
                        text = "还没有日程，创建后这里会显示分类占比。",
                        style = m.meta
                    )
                } else {
                    val maxCount = max(1, categoryCounts.maxOf { it.second })
                    categoryCounts.forEachIndexed { index, (category, count) ->
                        if (index > 0) Spacer(Modifier.height(m.rpx(20)))
                        CategoryBar(
                            category = category,
                            count = count,
                            ratio = count.toFloat() / maxCount,
                            total = items.size,
                            accent = Tokens.accentByIndex(index)
                        )
                    }
                }
            }
        }

        // ---------- 提醒 ----------
        item(key = "sec-remind") {
            SectionHeader(
                eyebrow = "03 / 提醒",
                title = "即将提醒",
                modifier = Modifier
                    .staggerEnter(tracker, "sec-remind", 5)
                    .padding(top = m.rpx(48))
            )
        }

        item(key = "remind-list") {
            if (reminders.isEmpty()) {
                HintBar(text = "还没有设置提醒的日程。在编辑页打开「提醒」即可。")
            } else {
                RaisedCard(
                    modifier = Modifier.staggerEnter(tracker, "remind-list", 6),
                    contentPadding = m.rpx(8)
                ) {
                    reminders.forEachIndexed { index, item ->
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(m.rpx(2))
                                    .background(Tokens.Line)
                            )
                        }
                        ReminderRow(item = item, onClick = { onItemClick(item) })
                    }
                }
            }
        }
    }
}

/** 今日概览卡。 */
@Composable
private fun OverviewHero(
    todayCount: Int,
    currentTitle: String?,
    currentTime: String?,
    nextTitle: String?,
    nextTime: String?,
    modifier: Modifier = Modifier
) {
    val m = LocalMetrics.current
    val shape = RoundedCornerShape(m.radiusLg)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Tokens.BrandGradient)
            .drawBehind {
                // 右上角三道斜线：纯线条装饰，交代「日程表」的意象
                val stroke = m.rpx(2).toPx()
                val pad = m.rpx(24).toPx()
                val step = m.rpx(18).toPx()
                for (i in 0..2) {
                    val startX = size.width - pad - step * i
                    drawLine(
                        color = Tokens.PaperRaised.copy(alpha = 0.16f - i * 0.04f),
                        start = androidx.compose.ui.geometry.Offset(startX, pad),
                        end = androidx.compose.ui.geometry.Offset(startX + step * 1.4f, pad + step * 1.4f),
                        strokeWidth = stroke,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
            .padding(m.rpx(32))
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$todayCount",
                style = m.displayXL.copy(color = Tokens.PaperRaised),
                modifier = Modifier.width(m.rpx(90))
            )
            Text(
                text = "个日程",
                style = m.bodyLg.copy(color = Tokens.PaperRaised.copy(alpha = 0.88f)),
                modifier = Modifier.padding(bottom = m.rpx(12))
            )
        }

        Spacer(Modifier.height(m.rpx(24)))

        if (currentTitle != null) {
            HeroLine(label = "进行中", title = currentTitle, detail = currentTime.orEmpty())
            Spacer(Modifier.height(m.rpx(16)))
        }
        if (nextTitle != null) {
            HeroLine(label = "下一场", title = nextTitle, detail = nextTime.orEmpty())
        }
        if (currentTitle == null && nextTitle == null) {
            Text(
                text = "今天没有安排，休息一下。",
                style = m.bodyLg.copy(color = Tokens.PaperRaised.copy(alpha = 0.88f))
            )
        }
    }
}

@Composable
private fun HeroLine(label: String, title: String, detail: String) {
    val m = LocalMetrics.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(m.rpx(8))
                .height(m.rpx(32))
                .clip(RoundedCornerShape(m.rpx(4)))
                .background(Tokens.PaperRaised.copy(alpha = 0.7f))
        )
        Spacer(Modifier.width(m.rpx(12)))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = m.micro.copy(
                    fontSize = m.sp(24),
                    lineHeight = m.sp(36),
                    color = Tokens.PaperRaised.copy(alpha = 0.78f)
                )
            )
            Text(
                text = title,
                style = m.bodyLg.copy(color = Tokens.PaperRaised),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                style = m.meta.copy(color = Tokens.PaperRaised.copy(alpha = 0.88f))
            )
        }
    }
}

/** 本周 7 天柱状分布。 */
@Composable
private fun WeekChart(
    dayLabels: List<String>,
    counts: List<Int>,
    todayIndex: Int,
    modifier: Modifier = Modifier
) {
    val m = LocalMetrics.current
    val maxCount = max(1, counts.maxOrNull() ?: 1)

    RaisedCard(
        modifier = modifier,
        contentPadding = m.rpx(32)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(m.rpx(200)),
            horizontalArrangement = Arrangement.spacedBy(m.rpx(12)),
            verticalAlignment = Alignment.Bottom
        ) {
            dayLabels.forEachIndexed { index, label ->
                val count = counts.getOrElse(index) { 0 }
                val isToday = index == todayIndex
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = "$count",
                        style = m.micro.copy(
                            fontSize = m.sp(24),
                            lineHeight = m.sp(34),
                            color = if (isToday) Tokens.BrandDeep else Tokens.InkMuted,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                    )
                    Spacer(Modifier.height(m.rpx(6)))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(m.rpx(140))
                            .clip(RoundedCornerShape(m.radiusXs))
                            .background(Tokens.PaperSunken),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (count > 0) {
                            // ⚠️ 这里必须用 SolidColor，不能用 Brush.linearGradient(单色)。
                            // Compose 的 linearGradient 在不传 colorStops 时要求 colors.size >= 2，
                            // 单色会在**绘制期**抛 IllegalArgumentException（崩溃点在这里）。
                            // SolidColor 本身就是 Brush 的子类，if/else 两分支类型一致。
                            //
                            // 今天的柱子用品牌渐变（强），其余用淡品牌蓝（弱），
                            // 两级对比清楚；之前非今天的柱子用 PaperTint，
                            // 和轨道底色 PaperSunken 几乎同色，整张图看起来是平的。
                            val barBrush: Brush = if (isToday) {
                                Tokens.BrandGradient
                            } else {
                                SolidColor(Tokens.Brand.copy(alpha = 0.32f))
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight((count.toFloat() / maxCount).coerceIn(0.06f, 1f))
                                    .clip(RoundedCornerShape(m.radiusXs))
                                    .background(barBrush)
                            )
                        }
                    }
                    Spacer(Modifier.height(m.rpx(8)))
                    Text(
                        text = label,
                        style = m.micro.copy(
                            fontSize = m.sp(24),
                            lineHeight = m.sp(34),
                            color = if (isToday) Tokens.BrandDeep else Tokens.InkMuted
                        )
                    )
                }
            }
        }
    }
}

/** 分类占比条。 */
@Composable
private fun CategoryBar(
    category: String,
    count: Int,
    ratio: Float,
    total: Int,
    accent: String
) {
    val m = LocalMetrics.current
    val barColor = Tokens.accentColor(accent)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = category, style = m.body.copy(color = Tokens.Ink))
            Text(
                text = "$count",
                style = m.numeric.copy(color = Tokens.InkMuted)
            )
        }
        Spacer(Modifier.height(m.rpx(8)))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(m.rpx(12))
                .clip(RoundedCornerShape(m.rpx(6)))
                .background(Tokens.PaperSunken)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio.coerceIn(0.04f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(m.rpx(6)))
                    .background(barColor)
            )
        }
        if (total > 0) {
            Spacer(Modifier.height(m.rpx(4)))
            Text(
                // 用浮点除法，避免 1/3 被截断成 0%
                text = "占全部 ${(count * 100f / total).toInt()}%",
                style = m.micro.copy(fontSize = m.sp(24), lineHeight = m.sp(36))
            )
        }
    }
}

/** 提醒行。 */
@Composable
private fun ReminderRow(
    item: ScheduleItem,
    onClick: () -> Unit
) {
    val m = LocalMetrics.current
    val interaction = rememberPressInteraction()
    val now = System.currentTimeMillis()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressHighlight(interaction, RoundedCornerShape(m.radiusMd))
            .rippleClick(interaction, onClick = onClick)
            .padding(vertical = m.rpx(16)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(m.rpx(16))
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = m.bodyLg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(m.rpx(4)))
            MetaLine(
                CoreDate.formatDate(item.startTime),
                CoreDate.formatTime(item.startTime),
                CoreFormat.remindLabel(item.remindBefore)
            )
        }
        com.campus.schedule.ui.components.Tag(
            text = CoreFormat.displayStatus(item.startTime, item.endTime, now),
            tone = if (item.endTime >= now) TagTone.UPCOMING else TagTone.ENDED
        )
    }
}
