package com.campus.schedule.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.campus.schedule.ui.components.PERIODS
import com.campus.schedule.ui.components.TimelineDayCell
import com.campus.schedule.ui.components.TimelineRow
import com.campus.schedule.ui.components.TimelineTimeRail
import com.campus.schedule.ui.components.WeekGrid
import com.campus.schedule.ui.components.WeekStrip
import com.campus.schedule.ui.components.clickableNoRipple
import com.campus.schedule.ui.components.pressHighlight
import com.campus.schedule.ui.components.rememberEnterTracker
import com.campus.schedule.ui.components.rememberPressInteraction
import com.campus.schedule.ui.components.staggerEnter
import com.campus.schedule.ui.icons.AppIcons
import com.campus.schedule.ui.theme.LocalMetrics
import com.campus.schedule.ui.theme.LocalPalette
import com.campus.schedule.ui.theme.LocalReducedMotion
import com.campus.schedule.ui.theme.Motion
import com.campus.schedule.ui.theme.Tokens
import com.campus.schedule.ui.toTimelineEntry
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 日程页（v2.3 重做）。
 *
 * ## 三层结构
 *
 * | 层 | 内容 | 会不会动 |
 * |---|---|---|
 * | 顶部报头 | 「日程」大标题 + 第 N 周 + 今天 | 只在时间维度真正变化时做一次图表交叉淡入 |
 * | 时间带 | 圈选层（月/周）+ 周条 / 月历 | 随圈选层切换：周条 ↔ 月历 |
 * | 内容 | 课表网格 或 当天时间轴 | 随「课表/时间轴」切换 |
 *
 * ## 「返回只返回上一级」
 *
 * 以前三种导航信号共用一个 Times 线性的 `Mode`（周/日），于是「换一天」和
 * 「切视图」看起来是同一级操作。现在拆成三个独立信号：
 *
 * * [weekStart]：周条翻页、上/下一周按钮 —— 只在 [ScheduleMode.WEEK] 响应；
 * * [selectedDate]：周条点一天、月历点一天、时间轴上一/下一天 —— 只在
 *   [ScheduleMode.TIMELINE] 响应；
 * * [mode]：课表 ↔ 时间轴由底部分段条控制，**不再受前面两个信号影响**。
 *
 * 计算属性 [ScheduleUiState.isTimelineFollowsSelection] 捕获了「时间轴正在
 * 向我刚选的那一天靠拢」这个瞬间，此时不触发翻页动画，避免出现双重重排。
 */
/**
 * 日程页（v2.3 重做）。
 *
 * 结构：报头 → 时间带（周条 / 月历）→ 内容（课表 / 时间轴）。
 * 三层都各自带转场，但只动 transform 与 alpha，不触发布局。
 */
@Composable
fun ScheduleScreen(
    state: ScheduleUiState,
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

    // `visibleItems` 是计算属性，每次访问都全量 filter 一次，这里只取一次。
    val visible = state.visibleItems

    // 状态标签（进行中 / 未开始 / 已结束）必须跟着当前时间走；
    // 按分钟取桶，避免 30 秒一跳的重组。
    val nowBucket = now / 60_000L
    val entries = remember(visible, state.conflictIds, nowBucket) {
        visible.map { it.toTimelineEntry(now, it.id in state.conflictIds) }
    }

    var scope by remember { mutableIntStateOf(SCOPE_WEEK) }
    val isWeekMode = state.mode == ScheduleMode.WEEK

    // 上一周 / 下一周按钮只在课表下出现；时间轴下同位置是日期导航。
    LaunchedEffect(isWeekMode) { scope = SCOPE_WEEK }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Tokens.TopGradient)
    ) {
        // ---------- 报头 ----------
        Column(modifier = Modifier.padding(horizontal = m.pagePadding)) {
            MastheadBlock(
                weekStart = state.weekStart,
                weekCount = visible.size,
                today = state.isTodaySelected,
                onToday = onToday
            )
        }

        // ---------- 时间带 ----------
        Box(modifier = Modifier.padding(horizontal = m.pagePadding)) {
            ScopeSwitch(
                scope = scope,
                onSelect = { scope = it }
            )
        }

        AnimatedContent(
            targetState = scope,
            transitionSpec = {
                val forward = targetState > initialState
                if (reduced) {
                    fadeIn(tween(Motion.DurFast)) togetherWith fadeOut(tween(Motion.DurExit))
                } else {
                    (fadeIn(tween(Motion.DurBase)) +
                        slideInVertically(
                            animationSpec = tween(Motion.DurBase, easing = Motion.EaseSoft)
                        ) { if (forward) it / 6 else -it / 6 }) togetherWith
                        (fadeOut(tween(Motion.DurExit)) +
                            slideOutVertically(
                                animationSpec = tween(Motion.DurExit, easing = Motion.EaseInOut)
                            ) { if (forward) -it / 10 else it / 10 })
                }
            },
            label = "scope-switch"
        ) { current ->
            Column {
                if (current == SCOPE_WEEK) {
                    WeekStrip(
                        weekStart = state.weekStart,
                        selectedDate = state.selectedDate,
                        nowMillis = now,
                        onPrevWeek = onPrevWeek,
                        onNextWeek = onNextWeek,
                        onThisWeek = onThisWeek,
                        onSelectDay = onSelectDate
                    )
                } else {
                    MonthCalendar(
                        anchor = state.selectedDate,
                        selectedDate = state.selectedDate,
                        nowMillis = now,
                        onSelectDay = onSelectDate
                    )
                }
            }
        }

        // ---------- 内容 ----------
        // 课表：任何时候都按周显示（weekStart 驱动）。
        // 时间轴：任何时候都按当天显示（selectedDate 驱动）。
        AnimatedContent(
            targetState = isWeekMode,
            transitionSpec = {
                if (reduced) {
                    fadeIn(tween(Motion.DurFast)) togetherWith fadeOut(tween(Motion.DurExit))
                } else {
                    (fadeIn(tween(Motion.DurBase)) +
                        slideInVertically(
                            animationSpec = tween(Motion.DurBase, easing = Motion.EaseSoft)
                        ) { it / 24 }) togetherWith fadeOut(tween(Motion.DurExit))
                }
            },
            label = "view-switch",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { week ->
            if (week) {
                WeekModeContent(
                    state = state,
                    visible = visible,
                    now = now,
                    onSelectDate = onSelectDate,
                    onItemClick = onItemClick,
                    onToggleConflict = onToggleConflict,
                    onCreateNew = onCreateNew
                )
            } else {
                TimelineModeContent(
                    state = state,
                    entries = entries,
                    tracker = tracker,
                    now = now,
                    onPrevDay = onPrevDay,
                    onNextDay = onNextDay,
                    onSelectDate = onSelectDate,
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

private const val SCOPE_WEEK = 0
private const val SCOPE_MONTH = 1

// ---------------------------------------------------------------------------
// 报头
// ---------------------------------------------------------------------------

/**
 * 报头：标题 + 周次 + 今天。
 *
 * 这里的文本有几种状态（本周 / 第 N 周 / 今天与否、日程条数），
 * 用 [AnimatedContent] 做「滑入滑出」会显得页面在跳；
 * 改成纵向老式翻页（新值从下往上顶上来），是报头这类「仪表」层该有的力度。
 */
@Composable
private fun MastheadBlock(
    weekStart: Long,
    weekCount: Int,
    today: Boolean,
    onToday: () -> Unit
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val reduced = LocalReducedMotion.current
    val weekLabel = "第 ${CoreDate.isoWeekNumber(weekStart)} 周"

    Column(modifier = Modifier.padding(top = m.rpx(8), bottom = m.rpx(20))) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "日程",
                    style = m.displayL.copy(color = Tokens.Ink, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Row(
                    modifier = Modifier.padding(top = m.rpx(10)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(m.rpx(10))
                ) {
                    // 周次：只在这一个数字里做翻牌，页面上其他东西不动
                    Box {
                        AnimatedContent(
                            targetState = weekLabel,
                            transitionSpec = {
                                if (reduced) {
                                    fadeIn(tween(Motion.DurFast)) togetherWith
                                        fadeOut(tween(Motion.DurFast))
                                } else {
                                    (slideInVertically(
                                        animationSpec = tween(Motion.DurBase, easing = Motion.EaseSoft)
                                    ) { it } + fadeIn(tween(Motion.DurFast))) togetherWith
                                        (slideOutVertically(
                                            animationSpec = tween(Motion.DurFast, easing = Motion.EaseInOut)
                                        ) { -it } + fadeOut(tween(Motion.DurFast)))
                                }
                            },
                            label = "week-label"
                        ) { label ->
                            Text(
                                text = label,
                                style = m.meta.copy(
                                    color = p.brandDeep,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(m.rpx(6))
                            .clip(CircleShape)
                            .background(Tokens.InkFaint)
                    )
                    Text(
                        text = "$weekCount 个日程",
                        style = m.meta.copy(color = Tokens.InkSecondary),
                        maxLines = 1
                    )
                }
            }
            LocateButton(
                text = "今天",
                active = today,
                onClick = onToday
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 时间带：圈选层
// ---------------------------------------------------------------------------

/**
 * 圈选层切换：周 / 月。
 *
 * 不做「滑块药剂」那种重指示器 —— 这一层只是「时间带显示多长」，
 * 用文字 + 下方一条 24×4rpx 的短横线表示即可，线条比面块更符合整体美术。
 */
@Composable
private fun ScopeSwitch(scope: Int, onSelect: (Int) -> Unit) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val reduced = LocalReducedMotion.current
    val labels = listOf("周", "月")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = m.rpx(8)),
        horizontalArrangement = Arrangement.spacedBy(m.rpx(28)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == scope
            val interaction = rememberPressInteraction()
            val weight = animateFloatAsState(
                targetValue = if (active) 1f else 0f,
                animationSpec = Motion.fast(reduced),
                label = "scopeTab"
            )
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(m.radiusXs))
                    .pressHighlight(interaction, RoundedCornerShape(m.radiusXs))
                    .clickableNoRipple(interaction) { onSelect(index) }
                    .padding(vertical = m.rpx(6)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    style = m.meta.copy(
                        color = lerp(Tokens.InkMuted, Tokens.Ink, weight.value),
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                    ),
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .padding(top = m.rpx(6))
                        .width(m.rpx(24))
                        .height(m.rpx(4))
                        .graphicsLayer { alpha = weight.value }
                        .clip(RoundedCornerShape(m.rpx(2)))
                        .background(p.brand)
                )
            }
        }
    }
}

/**
 * 月历（月视图）。
 *
 * 6 行 × 7 列固定网格：行数是固定的，点不同月份时高度不变，
 * 不会让下面的内容跟着上下跳。选中日期用品牌色实心圆，
 * 今天但未选中用一圈 2rpx 细描边 —— 两者同时成立时只显示选中态，
 * 今天的信息由圆心外的一颗小点补足。
 *
 * [anchor] 决定显示哪个月：换月时整块横移，方向按月份先后。
 */
@Composable
private fun MonthCalendar(
    anchor: Long,
    selectedDate: Long,
    nowMillis: Long,
    onSelectDay: (Long) -> Unit
) {
    val m = LocalMetrics.current
    val reduced = LocalReducedMotion.current
    val monthKey = remember(anchor) { CoreDate.parts(anchor).let { it.year * 100 + it.month } }

    AnimatedContent(
        targetState = monthKey,
        transitionSpec = {
            val forward = targetState > initialState
            if (reduced) {
                fadeIn(tween(Motion.DurFast)) togetherWith fadeOut(tween(Motion.DurExit))
            } else {
                (slideInHorizontally(
                    animationSpec = tween(Motion.DurBase, easing = Motion.EaseSoft)
                ) { if (forward) it / 5 else -it / 5 } + fadeIn(tween(Motion.DurBase))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(Motion.DurExit, easing = Motion.EaseInOut)
                    ) { if (forward) -it / 8 else it / 8 } + fadeOut(tween(Motion.DurExit)))
            }
        },
        label = "month-switch"
    ) { key ->
        val monthStart = remember(key) { CoreDate.startOfMonth(anchor) }
        val cells = remember(monthStart) { CoreDate.monthCells(monthStart) }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = CoreDate.formatMonth(monthStart),
                style = m.h3.copy(color = Tokens.Ink, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(bottom = m.rpx(8)),
                maxLines = 1
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                WEEKDAY_LABELS.forEach { label ->
                    Text(
                        text = label,
                        style = m.micro.copy(
                            fontSize = m.sp(24),
                            lineHeight = m.sp(34),
                            color = Tokens.InkMuted
                        ),
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = m.rpx(2))
                ) {
                    week.forEach { day ->
                        if (day == null) {
                            Spacer(modifier = Modifier.weight(1f).height(m.rpx(76)))
                        } else {
                            MonthDayCell(
                                timestamp = day,
                                selected = CoreDate.isSameDay(day, selectedDate),
                                today = CoreDate.isToday(day, nowMillis),
                                inMonth = CoreDate.isSameMonth(day, monthStart),
                                onClick = { onSelectDay(day) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
private fun MonthDayCell(
    timestamp: Long,
    selected: Boolean,
    today: Boolean,
    inMonth: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val interaction = rememberPressInteraction()
    val circle = 56
    val shape = CircleShape
    val day = CoreDate.parts(timestamp).day

    Box(
        modifier = modifier.height(m.rpx(76)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(m.rpx(circle))
                .clip(shape)
                .then(
                    when {
                        selected -> Modifier.background(p.brand)
                        today -> Modifier.background(p.brandTint)
                        else -> Modifier
                    }
                )
                .pressHighlight(interaction, shape)
                .clickableNoRipple(interaction, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.toString(),
                style = m.numeric.copy(
                    fontSize = m.sp(26),
                    lineHeight = m.sp(34),
                    color = when {
                        selected -> Tokens.PaperRaised
                        today -> p.brandDeep
                        inMonth -> Tokens.InkSecondary
                        else -> Tokens.InkFaint
                    },
                    fontWeight = if (selected || today) FontWeight.Bold else FontWeight.Normal
                ),
                maxLines = 1
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 课表模式
// ---------------------------------------------------------------------------

@Composable
private fun WeekModeContent(
    state: ScheduleUiState,
    visible: List<ScheduleItem>,
    now: Long,
    onSelectDate: (Long) -> Unit,
    onItemClick: (ScheduleItem) -> Unit,
    onToggleConflict: () -> Unit,
    onCreateNew: () -> Unit
) {
    val m = LocalMetrics.current
    val reduced = LocalReducedMotion.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = m.pagePadding, vertical = m.rpx(8))
    ) {
        // 只响应 weekStart：翻周时整块横移，点某一天不会让课表重新排布。
        AnimatedContent(
            targetState = state.weekStart,
            transitionSpec = {
                val forward = targetState > initialState
                if (reduced) {
                    fadeIn(tween(Motion.DurFast)) togetherWith fadeOut(tween(Motion.DurExit))
                } else {
                    (slideInHorizontally(
                        animationSpec = tween(Motion.DurBase, easing = Motion.EaseSoft)
                    ) { if (forward) it / 7 else -it / 7 } + fadeIn(tween(Motion.DurBase))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = tween(Motion.DurExit, easing = Motion.EaseInOut)
                        ) { if (forward) -it / 12 else it / 12 } + fadeOut(tween(Motion.DurExit)))
                }
            },
            label = "week-switch"
        ) { weekStart ->
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
                nowMillis = now,
                conflictIds = state.conflictIds
            )
        }

        if (state.conflictPairs.isNotEmpty()) {
            Spacer(Modifier.height(m.rpx(12)))
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
        Spacer(Modifier.height(m.rpx(80)))
    }
}

// ---------------------------------------------------------------------------
// 时间轴模式
// ---------------------------------------------------------------------------

@Composable
private fun TimelineModeContent(
    state: ScheduleUiState,
    entries: List<com.campus.schedule.ui.components.TimelineEntry>,
    tracker: com.campus.schedule.ui.components.EnterTracker,
    now: Long,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onSelectDate: (Long) -> Unit,
    onFilterChange: (ScheduleFilter) -> Unit,
    onItemClick: (ScheduleItem) -> Unit,
    onToggleStar: (ScheduleItem) -> Unit,
    onToggleConflict: () -> Unit,
    onCreateNew: () -> Unit
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val reduced = LocalReducedMotion.current
    val selectedDate = state.selectedDate
    val isToday = CoreDate.isToday(selectedDate, now)

    // v2.4：这里从「时间轴」改成了「每日事务」。
    //
    // 原来的做法是 HorizontalPager 横排 361 天，每页里面上半截是本地时间刻度
    // （TodayTimeline）、下半截又铺一遍同一批数据（TimelineRow）。同一件事在
    // 屏幕上出现两次，还带一堆空刻度 —— 用户反馈要的就是「只看今天做什么」。
    //
    // 现在：一次只渲染一天，`entries` 已经由 ViewModel 按 `startTime` 升序排好，
    // 直接按序铺成清单。翻天的两个箭头保留，点周条/月历换日期同样生效。
    //
    // 截面切换用「旧的一天上移淡出、新的一天从下方浮起」，配合视图本身的
    // AnimatedContent（view-switch），两层节奏保持同一时长，不会互相打架。
    AnimatedContent(
        targetState = selectedDate,
        transitionSpec = {
            val forward = targetState > initialState
            if (reduced) {
                fadeIn(tween(Motion.DurFast)) togetherWith fadeOut(tween(Motion.DurExit))
            } else {
                (fadeIn(tween(Motion.DurBase)) +
                    slideInVertically(
                        animationSpec = tween(Motion.DurBase, easing = Motion.EaseSoft)
                    ) { if (forward) it / 12 else -it / 12 }) togetherWith
                    (fadeOut(tween(Motion.DurExit)) +
                        slideOutVertically(
                            animationSpec = tween(Motion.DurExit, easing = Motion.EaseInOut)
                        ) { if (forward) -it / 16 else it / 16 })
            }
        },
        label = "day-switch",
        modifier = Modifier.fillMaxSize()
    ) { day ->
        // 每一页都用自己这一天算条目，切天时不会先看到旧数据。
        // 排序已经在 `state.visibleItems` 里做过（按「是否已结束 → 开始时间」），
        // 这里只按日期过滤，顺序保持原样。
        val dayEntries = remember(entries, day) {
            val ids = state.items
                .filter { CoreDate.isSameDay(it.startTime, day) }
                .mapTo(HashSet()) { it.id }
            entries.filter { it.id in ids }
        }
        val dayCount = dayEntries.size

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = m.pagePadding)
        ) {
            if (state.conflictPairs.isNotEmpty()) {
                ConflictBlock(state = state, onToggle = onToggleConflict)
            }

            // v2.4.1 日期导航：标题块左对齐（编辑排版），右侧并排翻页箭头。
            // 之前日期居中，而整页内容都是左起一条线，居中的标题会打断扫读节奏；
            // 现在与 TimelineRow 的时间栏共用左边距。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = m.rpx(4), bottom = m.rpx(14)),
                horizontalArrangement = Arrangement.spacedBy(m.rpx(12)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = CoreDate.formatDate(day),
                        style = m.h3.copy(color = Tokens.Ink, fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    Text(
                        text = if (CoreDate.isToday(day, now)) "今天 · " + CoreDate.formatMonthDay(day) else CoreDate.formatMonthDay(day),
                        style = m.micro.copy(
                            fontSize = m.sp(24),
                            lineHeight = m.sp(32),
                            color = if (CoreDate.isToday(day, now)) p.brandDeep else Tokens.InkMuted
                        ),
                        modifier = Modifier.padding(top = m.rpx(2)),
                        maxLines = 1
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(m.rpx(8))) {
                    DateNavButton(
                        icon = AppIcons.chevronLeft,
                        label = "前一天",
                        enabled = true,
                        onClick = onPrevDay
                    )
                    DateNavButton(
                        icon = AppIcons.chevronRight,
                        label = "后一天",
                        enabled = true,
                        onClick = onNextDay
                    )
                }
            }

            DayHeaderRow(
                count = dayCount,
                filter = state.filter,
                onFilterChange = onFilterChange
            )

            if (dayCount == 0) {
                EmptyState(
                    title = "这一天还没有安排",
                    body = "新建一条，它会按开始时间排进这一天的清单。",
                    inline = true,
                    actions = {
                        AppButton(
                            text = "新建日程",
                            onClick = onCreateNew,
                            style = ButtonStyle.SECONDARY,
                            block = false,
                            small = true
                        )
                    }
                )
            } else {
                // v2.4.1：清单不再是「卡片墙」。所有条目装进一张**浅色纸面**
                // （PaperTint 40% + 上下圆角 26rpx），行与行之间只有发丝线；
                // 进行中的那条在纸面上用更深的浅底 + 左侧品牌竖条自己跳出来。
                // 这样一天的事是「一叠纸上的一个段落」，不是十几个一模一样的框。
                val sheetShape = RoundedCornerShape(m.rpx(26))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(sheetShape)
                        .background(Tokens.PaperTint.copy(alpha = 0.40f))
                ) {
                    dayEntries.forEachIndexed { index, entry ->
                        val item = state.items.firstOrNull { it.id == entry.id }
                        TimelineRow(
                            entry = entry,
                            onClick = { item?.let(onItemClick) },
                            onToggleStar = { item?.let(onToggleStar) },
                            showDivider = index < dayEntries.lastIndex,
                            modifier = Modifier.staggerEnter(tracker, entry.id, index)
                        )
                    }
                }
            }

            Spacer(Modifier.height(m.rpx(96)))
        }
    }
}


/** 时间轴左右翻天的圆形箭头按钮（92rpx 触摸尺寸 + 线条风格）。 */
@Composable
private fun DateNavButton(
    icon: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val m = LocalMetrics.current
    val interaction = rememberPressInteraction()
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(m.rpx(92))
            .clip(shape)
            .background(Tokens.PaperRaised)
            .pressHighlight(interaction, shape, enabled = enabled)
            .clickableNoRipple(interaction, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = if (enabled) Tokens.InkSecondary else Tokens.InkFaint,
            modifier = Modifier.size(m.rpx(32))
        )
    }
}

@Composable
private fun DayHeaderRow(
    count: Int,
    filter: ScheduleFilter,
    onFilterChange: (ScheduleFilter) -> Unit
) {
    val m = LocalMetrics.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = m.rpx(12)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$count 个"
                .takeIf { count > 0 } ?: "空闲",
            style = m.meta.copy(color = Tokens.InkMuted),
            maxLines = 1
        )
        Row(horizontalArrangement = Arrangement.spacedBy(m.rpx(12))) {
            ScheduleFilter.values().forEach { value ->
                FilterChip(
                    text = when (value) {
                        ScheduleFilter.ALL -> "全部"
                        ScheduleFilter.TODAY -> "今天"
                        ScheduleFilter.STARRED -> "星标"
                    },
                    active = value == filter,
                    onClick = { onFilterChange(value) }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 共用小件
// ---------------------------------------------------------------------------

/** 冲突条 + 明细。 */
@Composable
private fun ConflictBlock(
    state: ScheduleUiState,
    onToggle: () -> Unit
) {
    val m = LocalMetrics.current
    Column(modifier = Modifier.padding(bottom = m.rpx(12))) {
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

@Composable
private fun FilterChip(
    text: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val m = LocalMetrics.current
    val p = LocalPalette.current
    val interaction = rememberPressInteraction()
    val shape = RoundedCornerShape(m.radiusPill)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (active) p.brandTint else Color.Transparent)
            .pressHighlight(interaction, shape)
            .clickableNoRipple(interaction, onClick = onClick)
            .padding(horizontal = m.rpx(20), vertical = m.rpx(10)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = m.meta.copy(
                color = if (active) p.brandDeep else Tokens.InkMuted,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
            ),
            maxLines = 1
        )
    }
}

/** 30 秒刷新一次的「现在」。 */
@Composable
private fun rememberNowMillisSafe(): Long =
    com.campus.schedule.ui.components.rememberNowMillis()