package com.campus.schedule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.campus.schedule.core.CoreConflict
import com.campus.schedule.core.CoreDate
import com.campus.schedule.core.CoreFormat
import com.campus.schedule.core.CoreValidators
import com.campus.schedule.data.ScheduleItem
import com.campus.schedule.data.ScheduleRepository
import com.campus.schedule.notify.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 日程页的展示模式。 */
enum class ScheduleMode { TIMELINE, WEEK }

/** 时间轴顶部的筛选。 */
enum class ScheduleFilter { ALL, TODAY, STARRED }

/** 筛选条上的标签。 */
val ScheduleFilter.labels: List<String>
    get() = listOf("全部", "今天", "星标")

data class ScheduleUiState(
    val mode: ScheduleMode = ScheduleMode.TIMELINE,
    val filter: ScheduleFilter = ScheduleFilter.ALL,
    val weekStart: Long = CoreDate.startOfWeek(System.currentTimeMillis()),
    val selectedDate: Long = CoreDate.startOfDay(System.currentTimeMillis()),
    val isCurrentWeek: Boolean = true,
    val isTodaySelected: Boolean = true,
    val items: List<ScheduleItem> = emptyList(),
    val conflictIds: Set<String> = emptySet(),
    val conflictPairs: List<String> = emptyList(),
    val conflictExpanded: Boolean = false,
    val loaded: Boolean = false
) {
    /** 当前视图下应展示的条目（已按筛选条件过滤，且按开始时间升序）。 */
    val visibleItems: List<ScheduleItem>
        get() {
            val base = when (mode) {
                // v2.4：每日事务改成「按时间排序的清单」，顺序在这里定死，
                // UI 层不再自己排 —— 免得两个地方各排一次还排得不一样。
                ScheduleMode.TIMELINE -> items.filter { CoreDate.isSameDay(it.startTime, selectedDate) }
                ScheduleMode.WEEK -> items.filter {
                    it.startTime in weekStart..(weekStart + 7 * CoreDate.DAY - 1)
                }
            }
            val filtered = when (filter) {
                ScheduleFilter.ALL -> base
                ScheduleFilter.TODAY -> base.filter { CoreDate.isToday(it.startTime, System.currentTimeMillis()) }
                ScheduleFilter.STARRED -> base.filter { it.starred }
            }
            // 已结束的一律沉到最后：当天清单里「接下来要做的」永远在上面。
            // 用 (是否已结束, 开始时间) 做二级排序，结束状态按读取时刻算，
            // 所以条目过了结束时间会自己往下沉一位，不需要额外刷新。
            val now = System.currentTimeMillis()
            return filtered.sortedWith(
                compareBy<ScheduleItem> { if (it.endTime >= now) 0 else 1 }
                    .thenBy { it.startTime }
            )
        }

    /** 今天的条目数。 */
    val todayCount: Int
        get() = items.count { CoreDate.isToday(it.startTime, System.currentTimeMillis()) }

    /** 进行中或最近一场。 */
    val currentItem: ScheduleItem?
        get() {
            val now = System.currentTimeMillis()
            val today = items
                .filter { CoreDate.isSameDay(it.startTime, now) }
                .sortedBy { it.startTime }
            return today.firstOrNull { it.endTime >= now } ?: today.lastOrNull()
        }

    val weekDays: List<CoreDate.WeekDay> get() = CoreDate.weekDays(weekStart)

    /** 冲突条文案。 */
    val conflictText: String
        get() = if (conflictPairs.isEmpty()) "" else "${conflictPairs.size} 个日程时间重叠"

    /** 今天零点 —— 时间轴分页以它为原点，避免每天重算。 */
    fun todayStart(): Long = CoreDate.startOfDay(System.currentTimeMillis())

    /**
     * 时间轴是否「正跟随所选日期」。
     *
     * 时间轴按天显示，日期来源只有一个：[selectedDate]。当用户从周条/月历
     * 或左右箭头指定了某一天，时间轴必须直接显示那一天 —— 这是**同一级**
     * 的日期变化，不该再叠加一层翻页动画，否则会出现「先横移再竖移」的
     * 双重位移。这个属性捕获的就是这段同步期。
     */
    val isTimelineFollowsSelection: Boolean
        get() = mode != ScheduleMode.WEEK
}

/**
 * 日程 ViewModel。
 *
 * 对应小程序的 `pages/schedule/index.js`，去掉云端依赖后改为直接读本地 Room。
 */
class ScheduleViewModel(
    private val repo: ScheduleRepository,
    private val appContext: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    val allItems: StateFlow<List<ScheduleItem>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repo.seedIfEmpty()
            repo.observeAll().collect { list ->
                _uiState.value = _uiState.value.copy(
                    items = list,
                    conflictIds = CoreConflict.conflictIdSet(list),
                    loaded = true
                )
                recomputeConflicts()
            }
        }
    }

    fun setMode(mode: ScheduleMode) {
        _uiState.value = _uiState.value.copy(mode = mode)
        recomputeConflicts()
    }

    fun onSelectFilter(filter: ScheduleFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
        recomputeConflicts()
    }

    fun onToggleConflict() {
        val expanded = !_uiState.value.conflictExpanded
        _uiState.value = _uiState.value.copy(conflictExpanded = expanded)
    }

    /** 重新计算当前视图下的冲突明细。 */
    private fun recomputeConflicts() {
        val state = _uiState.value
        val scope = when (state.mode) {
            ScheduleMode.TIMELINE -> state.items.filter { CoreDate.isSameDay(it.startTime, state.selectedDate) }
            ScheduleMode.WEEK -> state.items.filter {
                it.startTime in state.weekStart..(state.weekStart + 7 * CoreDate.DAY - 1)
            }
        }
        val pairs = CoreConflict.detectConflicts(scope).map { pair ->
            "「${pair.a.title}」与「${pair.b.title}」时间有重叠，仍可都保留。"
        }
        _uiState.value = _uiState.value.copy(conflictPairs = pairs)
    }

    // ------------------------------------------------------------------
    // 导航信号（v2.3）
    //
    // 以前「翻周」和「换天」绑在一起：翻周会把 selectedDate 重置到周一，
    // 于是从时间轴（按天）翻到课表（按周）时页面会跳两下。现在拆成两个
    // 互不干涉的信号 —— 见 ScheduleScreen 的类注释。
    // ------------------------------------------------------------------

    fun onPrevWeek() = shiftWeek(-7)

    fun onNextWeek() = shiftWeek(7)

    /** 翻周：只动 weekStart，**不**改 selectedDate。 */
    private fun shiftWeek(days: Int) {
        val now = System.currentTimeMillis()
        val weekStart = CoreDate.addDays(_uiState.value.weekStart, days)
        _uiState.value = _uiState.value.copy(
            weekStart = weekStart,
            isCurrentWeek = weekStart == CoreDate.startOfWeek(now)
        )
        recomputeConflicts()
    }

    /** 回到本周：只把 weekStart 拉回，选中日期保持不变。 */
    fun onBackToThisWeek() {
        val now = System.currentTimeMillis()
        val weekStart = CoreDate.startOfWeek(now)
        _uiState.value = _uiState.value.copy(
            weekStart = weekStart,
            isCurrentWeek = true,
            selectedDate = _uiState.value.selectedDate
        )
        recomputeConflicts()
    }

    /** 回到今天：周和日一起归位 —— 这是唯一允许同时改动两个信号的入口。 */
    fun onBackToToday() {
        val now = System.currentTimeMillis()
        applyWeekState(CoreDate.startOfWeek(now), CoreDate.startOfDay(now))
    }

    fun onPrevDay() {
        selectDay(CoreDate.addDays(_uiState.value.selectedDate, -1))
    }

    fun onNextDay() {
        selectDay(CoreDate.addDays(_uiState.value.selectedDate, 1))
    }

    /**
     * 选中某一天。
     *
     * * 时间轴模式：只改 selectedDate，不动 weekStart。
     * * 课表模式：同时把 weekStart 对齐到那一天所在周 —— 用户在课表里
     *   从月历点了 14 号，课表就该翻到 14 号那一周，否则选中的高亮
     *   根本不在当前屏幕上。这是一次「同一级」的位移，不是跳级。
     */
    fun onSelectDate(timestamp: Long) {
        selectDay(timestamp)
    }

    private fun selectDay(timestamp: Long) {
        val now = System.currentTimeMillis()
        val day = CoreDate.startOfDay(timestamp)
        val state = _uiState.value
        val sameWeek = CoreDate.startOfWeek(day) == state.weekStart
        val nextWeekStart = if (state.mode == ScheduleMode.WEEK) CoreDate.startOfWeek(day) else state.weekStart
        _uiState.value = state.copy(
            selectedDate = day,
            weekStart = nextWeekStart,
            isCurrentWeek = nextWeekStart == CoreDate.startOfWeek(now),
            // 「今天」按钮的高亮要跟随所选日期变化
            isTodaySelected = CoreDate.isSameDay(day, now)
        )
        if (!sameWeek) recomputeConflicts()
    }

    private fun applyWeekState(weekStart: Long, selectedDate: Long) {
        val now = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(
            weekStart = weekStart,
            selectedDate = selectedDate,
            isCurrentWeek = weekStart == CoreDate.startOfWeek(now),
            isTodaySelected = CoreDate.isSameDay(selectedDate, now)
        )
        recomputeConflicts()
    }

    fun onCreate(form: CoreValidators.Form): Boolean {
        val result = CoreValidators.validate(form)
        if (!result.valid) {
            _toast.value = result.errors.values.firstOrNull() ?: "请检查填写内容"
            return false
        }
        viewModelScope.launch {
            val saved = repo.create(
                ScheduleItem(
                    id = "",
                    title = result.value.title,
                    note = result.value.note,
                    location = result.value.location,
                    category = result.value.category,
                    accent = result.value.accent,
                    startTime = result.value.startTime,
                    endTime = result.value.endTime,
                    isAllDay = result.value.isAllDay,
                    remindBefore = result.value.remindBefore,
                    starred = result.value.starred
                )
            )
            ReminderScheduler.schedule(appContext, saved)
            _toast.value = "已创建"
        }
        return true
    }

    fun onUpdate(item: ScheduleItem, form: CoreValidators.Form): Boolean {
        val result = CoreValidators.validate(form)
        if (!result.valid) {
            _toast.value = result.errors.values.firstOrNull() ?: "请检查填写内容"
            return false
        }
        viewModelScope.launch {
            val saved = repo.update(
                item.copy(
                    title = result.value.title,
                    note = result.value.note,
                    location = result.value.location,
                    category = result.value.category,
                    accent = result.value.accent,
                    startTime = result.value.startTime,
                    endTime = result.value.endTime,
                    isAllDay = result.value.isAllDay,
                    remindBefore = result.value.remindBefore,
                    starred = result.value.starred
                )
            )
            ReminderScheduler.schedule(appContext, saved)
            _toast.value = "已保存"
        }
        return true
    }

    fun onDelete(item: ScheduleItem) {
        viewModelScope.launch {
            repo.delete(item)
            ReminderScheduler.cancel(appContext, item.id)
            _toast.value = "已删除"
        }
    }

    fun onToggleStar(item: ScheduleItem) {
        viewModelScope.launch {
            repo.update(item.copy(starred = !item.starred))
        }
    }

    fun clearSamples() {
        viewModelScope.launch {
            // 先撤销示例数据挂着的闹钟，否则清空后仍会弹出已删除日程的提醒。
            ReminderScheduler.cancelAll(appContext, _uiState.value.items.filter { it.isSample })
            repo.clearSamples()
            _toast.value = "已清空示例数据"
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            ReminderScheduler.cancelAll(appContext, _uiState.value.items)
            repo.clearAll()
            _toast.value = "已清空全部数据"
        }
    }

    fun consumeToast() {
        _toast.value = null
    }

    class Factory(
        private val repo: ScheduleRepository,
        private val context: android.content.Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ScheduleViewModel(repo, context.applicationContext) as T
    }
}

/**
 * 把日程条目转成时间轴展示数据。
 *
 * 放在这里是为了让页面只关心「画什么」，不关心状态判定与文案拼接。
 */
fun ScheduleItem.toTimelineEntry(
    now: Long = System.currentTimeMillis(),
    conflict: Boolean = false
): com.campus.schedule.ui.components.TimelineEntry {
    val status = CoreFormat.displayStatus(startTime, endTime, now)
    val timeText = if (isAllDay) "全天" else CoreDate.formatTime(startTime)
    val endText = if (isAllDay || endTime <= 0L || endTime == startTime) {
        ""
    } else {
        CoreDate.formatTime(endTime)
    }
    val metaParts = listOfNotNull(
        category.ifBlank { null },
        CoreFormat.remindLabel(remindBefore).takeIf { it != "不提醒" }
    )
    return com.campus.schedule.ui.components.TimelineEntry(
        id = id,
        title = title,
        timeText = timeText,
        endText = endText,
        meta = metaParts.joinToString(" · "),
        location = location,
        status = status,
        statusTone = when (status) {
            "进行中" -> com.campus.schedule.ui.components.TagTone.ONGOING
            "未开始" -> com.campus.schedule.ui.components.TagTone.UPCOMING
            "已结束" -> com.campus.schedule.ui.components.TagTone.ENDED
            else -> com.campus.schedule.ui.components.TagTone.NEUTRAL
        },
        ongoing = status == "进行中",
        starred = starred,
        conflict = conflict,
        accent = accent
    )
}
