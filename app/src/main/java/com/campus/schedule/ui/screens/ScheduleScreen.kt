package com.campus.schedule.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.campus.schedule.core.CoreDate
import com.campus.schedule.data.ScheduleItem
import com.campus.schedule.ui.ScheduleFilter
import com.campus.schedule.ui.ScheduleMode
import com.campus.schedule.ui.ScheduleUiState
import com.campus.schedule.ui.components.AppButton
import com.campus.schedule.ui.components.ButtonStyle
import com.campus.schedule.ui.components.ConflictDetail
import com.campus.schedule.ui.components.EmptyState
import com.campus.schedule.ui.components.HintBar
import com.campus.schedule.ui.components.LocateButton
import com.campus.schedule.ui.components.Masthead
import com.campus.schedule.ui.components.SegmentedSwitch
import com.campus.schedule.ui.components.TimelineNav
import com.campus.schedule.ui.components.TimelineRow
import com.campus.schedule.ui.components.WeekGrid
import com.campus.schedule.ui.components.pressHighlight
import com.campus.schedule.ui.components.rememberEnterTracker
import com.campus.schedule.ui.components.clickableNoRipple
import com.campus.schedule.ui.components.pressScale
import com.campus.schedule.ui.components.rememberPressInteraction
import com.campus.schedule.ui.components.staggerEnter
import com.campus.schedule.ui.icons.AppIcons
import com.campus.schedule.ui.theme.LocalMetrics
import com.campus.schedule.ui.theme.LocalReducedMotion
import com.campus.schedule.ui.theme.Motion
import com.campus.schedule.ui.theme.Tokens
import com.campus.schedule.ui.toTimelineEntry

/**
 * 日程页。
 *
 * 结构与小程序 `pages/schedule/index` 完全一致：
 * 报头 → 视图切换（课表 / 时间轴）→ 视图主体。
 *
 * 内容密度、留白、层级均按小程序 rpx 还原。
 */
@Composable
fun ScheduleScreen(
    state: ScheduleUiState,
    onModeChange: (ScheduleMode) -> Unit,
    onFilterChange: (ScheduleFilter) -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onThisWeek: () -> Unit,
    onToday: () -> Unit,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onSelectDate: (Long) -> Unit,
    onItemClick: (ScheduleItem) -> Unit,
    onToggleStar: (ScheduleItem) -> Unit,
    onToggleConflict: () -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    val m = LocalMetrics.current
    val reduced = LocalReducedMotion.current
    val tracker = rememberEnterTracker()
    val now = rememberNowMillisSafe()

    // `visibleItems` 是 ScheduleUiState 上的计算属性，每次访问都会全量 filter 一遍。
    // 原来这个页面在一次重组里访问了 6 次，这里只取一次，后面的判断全部复用。
    val visible = state.visibleItems

    // 时间轴的状态标签（进行中 / 未开始 / 已结束）必须跟着「当前时间」走。
    // now 每 30 秒跳一次，按分钟取桶，避免 30 秒内的重复计算。
    val nowBucket = now / 60_000L
    val entries = remember(visible, state.conflictIds, nowBucket) {
        visible.map { it.toTimelineEntry(now, it.id in state.conflictIds) }
    }
    // id → 条目，避免在列表回调里做线性查找
    val itemById = remember(visible) { visible.associateBy { it.id } }

    val isWeek = state.mode == ScheduleMode.WEEK
    val weekRange = "${CoreDate.formatMonthDay(state.weekStart)} – " +
        CoreDate.formatMonthDay(state.weekStart + 6 * CoreDate.DAY)
    val meta = if (isWeek) {
        "$weekRange · ${visible.size} 个日程"
    } else {
        "${CoreDate.formatDate(state.selectedDate)} · ${visible.size} 个日程"
    }

    // 固定在顶部的报头与视图切换：从 LazyColumn 提到外面，
    // 视图切换时它们不跟着滑动，只有下面的主体在动。
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Tokens.TopGradient)
            .padding(horizontal = m.pagePadding)
    ) {
        Masthead(
            kicker = "SCHEDULE / 第 ${CoreDate.isoWeekNumber(state.weekStart)} 周",
            title = "日程",
            meta = meta,
            trailing = {
                LocateButton(
                    text = "今天",
                    active = state.isTodaySelected,
                    onClick = onToday
                )
            }
        )
        SegmentedSwitch(
            labels = listOf("课表", "时间轴"),
            selectedIndex = if (isWeek) 0 else 1,
            onSelect = { index ->
                onModeChange(if (index == 0) ScheduleMode.WEEK else ScheduleMode.TIMELINE)
            },
            modifier = Modifier.padding(bottom = m.rpx(24))
        )
    }

    // ---------- 视图主体 ----------
    // v2.1：课表与时间轴各自是「一屏」，切换时必须有过渡，否则内容会瞬间重排。
    // 用 AnimatedContent：新视图淡入 + 轻微上移，同时高度平滑过渡到新内容的高度；
    // 旧视图直接淡出、不做位移 —— 两个视图高度差很大时，横移会显得很乱。
    AnimatedContent(
        targetState = isWeek,
        transitionSpec = {
            if (reduced) {
                fadeIn(tween(Motion.DurFast)) togetherWith fadeOut(tween(Motion.DurExit))
            } else {
                (fadeIn(tween(Motion.DurBase)) +
                    slideInVertically(
                        animationSpec = tween(Motion.DurBase, easing = Motion.EaseSoft)
                    ) { it / 40 }) togetherWith fadeOut(tween(Motion.DurExit))
            }
        },
        label = "view-switch",
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) { week ->
        // 两套视图直接放在 AnimatedContent 里（不再套 LazyColumn）：
        //   * 课表本身是固定高度的网格 + 越界列表，时间轴只是当天的列表，数据量很小；
        //   * 更重要的是，高度变化能被 AnimatedContent 捕捉到，切换时内容高度平滑过渡，
        //     不会再出现「上面的卡片瞬间跳位」。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = m.rpx(8), bottom = m.rpx(48))
        ) {
            if (week) {
                WeekSection(
                    state = state,
                    visible = visible,
                    now = now,
                    onPrevWeek = onPrevWeek,
                    onNextWeek = onNextWeek,
                    onThisWeek = onThisWeek,
                    onSelectDate = onSelectDate,
                    onItemClick = onItemClick,
                    onToggleConflict = onToggleConflict,
                    onCreateNew = onCreateNew
                )
            } else {
                TimelineSection(
                    state = state,
                    entries = entries,
                    itemById = itemById,
                    tracker = tracker,
                    onPrevDay = onPrevDay,
                    onNextDay = onNextDay,
                    onFilterChange = onFilterChange,
                    onItemClick = onItemClick,
                    onToggleStar = onToggleStar,
                    onToggleConflict = onToggleConflict,
                    onCreateNew = onCreateNew
                )
            }
        }
    }
}


/**
 * 课表视图的整块内容（v2.1 从 ScheduleScreen 里拆出来）。
 *
 * 拆出来的唯一原因是[模式切换]需要把「课表 / 时间轴」各自当成一个整体做转场，
 * 这样两套视图可以交叉滑移、高度平滑过渡，而不是在同一个 LazyColumn 里瞬间重排。
 */
@Composable
private fun WeekSection(
    state: ScheduleUiState,
    visible: List<ScheduleItem>,
    now: Long,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onThisWeek: () -> Unit,
    onSelectDate: (Long) -> Unit,
    onItemClick: (ScheduleItem) -> Unit,
    onToggleConflict: () -> Unit,
    onCreateNew: () -> Unit
) {
    val m = LocalMetrics.current
    val reduced = LocalReducedMotion.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = m.rpx(16)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LocateButton(
                text = "上一周",
                onClick = onPrevWeek,
                leadingIcon = {
                    Icon(
                        painter = painterResource(AppIcons.chevronLeftBrand),
                        contentDescription = null,
                        tint = Tokens.Brand,
                        modifier = Modifier.size(m.iconSm)
                    )
                }
            )
            LocateButton(
                text = "本周",
                active = state.isCurrentWeek,
                onClick = onThisWeek
            )
            LocateButton(
                text = "下一周",
                onClick = onNextWeek,
                trailingIcon = {
                    Icon(
                        painter = painterResource(AppIcons.chevronRightBrand),
                        contentDescription = null,
                        tint = Tokens.Brand,
                        modifier = Modifier.size(m.iconSm)
                    )
                }
            )
        }

        // 切周：新的一周轻微横移 + 淡入，旧的一周反向淡出。
        // 位移只有 1/16 宽度，不会打断纵向滚动，也不会让人觉得页面在跳。
        AnimatedContent(
            targetState = state.weekStart,
            transitionSpec = {
                val forward = targetState > initialState
                if (reduced) {
                    fadeIn(tween(Motion.DurFast)) togetherWith fadeOut(tween(Motion.DurExit))
                } else {
                    (slideInHorizontally(
                        animationSpec = tween(Motion.DurBase, easing = Motion.EaseSoft)
                    ) { if (forward) it / 16 else -it / 16 } +
                        fadeIn(tween(Motion.DurBase))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = tween(Motion.DurExit, easing = Motion.EaseInOut)
                        ) { if (forward) -it / 24 else it / 24 } + fadeOut(tween(Motion.DurExit)))
                }
            },
            label = "week-switch"
        ) { weekStart ->
            // 按该周自己过滤，否则过渡时旧的一周会借用新一周的数据
            val weekItems = remember(weekStart, state.items) {
                state.items.filter {
                    it.startTime >= weekStart && it.startTime < weekStart + 7 * CoreDate.DAY
                }
            }
            WeekGrid(
                weekStart = weekStart,
                items = weekItems,
                selectedDate = state.selectedDate,
                onSelectDay = onSelectDate,
                onItemClick = onItemClick,
                nowMillis = now
            )
        }

        if (state.conflictPairs.isNotEmpty()) {
            Spacer(Modifier.height(m.rpx(16)))
            ConflictBlock(state = state, onToggle = onToggleConflict)
        }

        if (visible.isEmpty()) {
            EmptyState(
                title = "这一周还没有日程",
                body = "新建一条日程，它会同时出现在课表和时间轴里。",
                lineArt = true,
                actions = {
                    AppButton(
                        text = "新建日程",
                        onClick = onCreateNew,
                        style = ButtonStyle.SECONDARY,
                        block = false
                    )
                }
            )
        }
    }
}

/** 时间轴视图的整块内容。 */
@Composable
private fun TimelineSection(
    state: ScheduleUiState,
    entries: List<com.campus.schedule.ui.components.TimelineEntry>,
    itemById: Map<String, ScheduleItem>,
    tracker: com.campus.schedule.ui.components.EnterTracker,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onFilterChange: (ScheduleFilter) -> Unit,
    onItemClick: (ScheduleItem) -> Unit,
    onToggleStar: (ScheduleItem) -> Unit,
    onToggleConflict: () -> Unit,
    onCreateNew: () -> Unit
) {
    val m = LocalMetrics.current

    Column(modifier = Modifier.fillMaxWidth()) {
        TimelineNav(
            dateLabel = CoreDate.formatDate(state.selectedDate),
            hint = "按天查看待办",
            onPrev = onPrevDay,
            onNext = onNextDay
        )

        if (state.conflictPairs.isNotEmpty()) {
            ConflictBlock(state = state, onToggle = onToggleConflict)
        }

        FilterRow(
            selected = state.filter,
            onSelect = onFilterChange,
            modifier = Modifier.padding(bottom = m.rpx(8))
        )

        if (entries.isEmpty()) {
            EmptyState(
                title = "这一天没有待办",
                body = "点右下角的按钮新建一条日程，它会出现在这里。",
                lineArt = true,
                actions = {
                    AppButton(
                        text = "新建日程",
                        onClick = onCreateNew,
                        style = ButtonStyle.SECONDARY,
                        block = false
                    )
                }
            )
        } else {
            entries.forEachIndexed { index, entry ->
                val item = itemById[entry.id]
                TimelineRow(
                    entry = entry,
                    onClick = { item?.let(onItemClick) },
                    onToggleStar = { item?.let(onToggleStar) },
                    modifier = Modifier
                        .staggerEnter(tracker, entry.id, index)
                        .padding(bottom = m.rpx(16))
                )
            }
        }
    }
}

/** 冲突条 + 明细。 */
@Composable
private fun ConflictBlock(
    state: ScheduleUiState,
    onToggle: () -> Unit
) {
    val m = LocalMetrics.current
    Column(modifier = Modifier.padding(bottom = m.rpx(16))) {
        HintBar(
            text = "${state.conflictText}，只提示，不删除",
            onClick = onToggle,
            trailingIcon = {
                Icon(
                    painter = painterResource(
                        if (state.conflictExpanded) AppIcons.chevronUpGold else AppIcons.chevronDownGold
                    ),
                    contentDescription = null,
                    tint = Tokens.GoldText,
                    modifier = Modifier.size(m.iconSm)
                )
            }
        )
        if (state.conflictExpanded) {
            ConflictDetail(pairs = state.conflictPairs)
        }
    }
}

/** 筛选条（全部 / 今天 / 星标）。 */
@Composable
private fun FilterRow(
    selected: ScheduleFilter,
    onSelect: (ScheduleFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val m = LocalMetrics.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(m.rpx(12))
    ) {
        ScheduleFilter.values().forEach { filter ->
            FilterChip(
                text = when (filter) {
                    ScheduleFilter.ALL -> "全部"
                    ScheduleFilter.TODAY -> "今天"
                    ScheduleFilter.STARRED -> "星标"
                },
                active = filter == selected,
                onClick = { onSelect(filter) }
            )
        }
    }
}

@Composable
private fun FilterChip(
    text: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val m = LocalMetrics.current
    val interaction = rememberPressInteraction()
    val shape = RoundedCornerShape(m.radiusPill)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (active) Tokens.BrandTint else Tokens.PaperRaised)
            .pressHighlight(interaction, RoundedCornerShape(m.radiusMd))
            .clickableNoRipple(interaction, onClick = onClick)
            .padding(horizontal = m.rpx(24), vertical = m.rpx(12)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = m.meta.copy(
                color = if (active) Tokens.BrandDeep else Tokens.InkMuted,
                fontWeight = if (active) {
                    androidx.compose.ui.text.font.FontWeight.SemiBold
                } else {
                    androidx.compose.ui.text.font.FontWeight.Normal
                }
            )
        )
    }
}

/** 30 秒刷新一次的「现在」。 */
@Composable
private fun rememberNowMillisSafe(): Long =
    com.campus.schedule.ui.components.rememberNowMillis()
