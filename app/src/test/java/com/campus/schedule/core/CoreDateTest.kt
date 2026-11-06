package com.campus.schedule.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CoreDate 单元测试 —— 期望值全部来自小程序 `miniprogram/utils/date.js` 的真实输出。
 *
 * 验证 Kotlin 翻译与原 JS 行为一致（固定 UTC+8 时区）。
 */
class CoreDateTest {

    // 2026-10-27 是周二 10:30 (UTC+8)。基准值 1793068200000 来自小程序 JS 实跑输出。
    private val base = 1793068200000L

    @Test
    fun `makeTimestamp 对齐 UTC 加 8 小时偏移`() {
        // JS: Date.UTC(2026, 9, 27, 10, 30) - 8h
        val expected = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            .apply { clear(); set(2026, 9, 27, 10, 30, 0) }.timeInMillis - CoreDate.TZ_OFFSET
        assertEquals(expected, base)
        assertEquals(1793068200000L, base)
    }

    @Test
    fun `startOfDay 与 endOfDay 覆盖整天`() {
        val start = CoreDate.startOfDay(base)
        val end = CoreDate.endOfDay(base)
        // JS 实跑：startOfDay = 1793030400000
        assertEquals(1793030400000L, start)
        assertEquals(base, start + 10 * 3600_000L + 30 * 60_000L)
        assertEquals(CoreDate.DAY - 1, end - start)
    }

    @Test
    fun `getWeekdayIndex 周一为 0 周日为 6`() {
        // 2026-10-27 是周二
        assertEquals(1, CoreDate.getWeekdayIndex(base))
        // 2026-10-26 周一
        val monday = CoreDate.makeTimestamp(2026, 9, 26, 12, 0, 0, 0)
        assertEquals(0, CoreDate.getWeekdayIndex(monday))
        // 2026-11-01 周日
        val sunday = CoreDate.makeTimestamp(2026, 10, 1, 12, 0, 0, 0)
        assertEquals(6, CoreDate.getWeekdayIndex(sunday))
    }

    @Test
    fun `startOfWeek 返回周一零点`() {
        val monday = CoreDate.makeTimestamp(2026, 9, 26, 0, 0, 0, 0)
        assertEquals(monday, CoreDate.startOfWeek(base))
        assertEquals(CoreDate.DAY * 7 - 1, CoreDate.endOfWeek(base) - CoreDate.startOfWeek(base))
    }

    @Test
    fun `toDateKey 与 fromDateKey 互逆`() {
        val key = CoreDate.toDateKey(base)
        assertEquals("2026-10-27", key)
        assertEquals(CoreDate.startOfDay(base), CoreDate.fromDateKey(key))
        assertEquals(0L, CoreDate.fromDateKey("bad-key"))
    }

    @Test
    fun `formatTimeRange 覆盖全天 待定 同日 跨日`() {
        assertEquals("全天", CoreDate.formatTimeRange(base, base, true))
        assertEquals("时间待定", CoreDate.formatTimeRange(0L, 0L, false))
        assertEquals("10:30", CoreDate.formatTimeRange(base, base, false))

        val sameDayEnd = base + 2 * 3600_000L
        assertEquals("10:30–12:30", CoreDate.formatTimeRange(base, sameDayEnd, false))

        val nextDay = CoreDate.addDays(base, 1)
        assertEquals(
            "10月27日 10:30 – 10月28日 10:30",
            CoreDate.formatTimeRange(base, nextDay, false)
        )
    }

    @Test
    fun `formatDate 与 parts 一致`() {
        val p = CoreDate.parts(base)
        assertEquals("10月27日", CoreDate.formatMonthDay(base))
        assertEquals("10月", CoreDate.formatMonth(base))
        assertEquals("27", CoreDate.formatDayNumber(base))
        assertEquals("周二", CoreDate.formatWeekday(base))
        assertEquals("10月27日 周二", CoreDate.formatDate(base))
        assertEquals("10:30", CoreDate.formatTime(base))
        assertEquals(2026, p.year)
        assertEquals(10, p.month)
        assertEquals(27, p.day)
    }

    @Test
    fun `isoWeekNumber 与 weekDays 一致`() {
        // JS 实跑：2026-10-27 属于第 44 周（ISO）
        assertEquals(44, CoreDate.isoWeekNumber(base))
        assertEquals("第 44 周", CoreDate.formatWeekLabel(base))
        val days = CoreDate.weekDays(base)
        assertEquals(7, days.size)
        assertEquals("2026-10-26", days[0].dateKey)
        assertEquals("周一", days[0].weekday)
        assertEquals("2026-11-01", days[6].dateKey)
        assertEquals("周日", days[6].weekday)
        // JS 实跑：weekDays[0].timestamp = 1792944000000
        assertEquals(1792944000000L, days[0].timestamp)
    }

    @Test
    fun `isoWeekNumber 覆盖年初年末边界`() {
        // 期望值全部来自小程序 date.js 实跑输出
        assertEquals(1, CoreDate.isoWeekNumber(CoreDate.fromDateKey("2026-01-01")))
        assertEquals(53, CoreDate.isoWeekNumber(CoreDate.fromDateKey("2026-12-28")))
        assertEquals(1, CoreDate.isoWeekNumber(CoreDate.fromDateKey("2027-01-04")))
        assertEquals(14, CoreDate.isoWeekNumber(CoreDate.fromDateKey("2026-03-30")))
    }

    @Test
    fun `weekdayIndex 覆盖年初边界`() {
        // JS 实跑：2026-01-01 是周四
        assertEquals(3, CoreDate.getWeekdayIndex(CoreDate.fromDateKey("2026-01-01")))
        assertEquals("周四", CoreDate.formatWeekday(CoreDate.fromDateKey("2026-01-01")))
        // JS 实跑：2026-12-28 是周一
        assertEquals(0, CoreDate.getWeekdayIndex(CoreDate.fromDateKey("2026-12-28")))
        assertEquals("周一", CoreDate.formatWeekday(CoreDate.fromDateKey("2026-12-28")))
        // JS 实跑：2027-01-04 是周一
        assertEquals(0, CoreDate.getWeekdayIndex(CoreDate.fromDateKey("2027-01-04")))
    }

    @Test
    fun `daysUntil 与 deadlineText 覆盖各档位`() {
        val today = CoreDate.startOfDay(base)
        assertEquals(0, CoreDate.daysUntil(today, today))
        assertEquals(1, CoreDate.daysUntil(today + CoreDate.DAY, today))
        assertEquals(-1, CoreDate.daysUntil(today - CoreDate.DAY, today))

        assertEquals("今天截止", CoreDate.deadlineText(today, today))
        assertEquals("明天截止", CoreDate.deadlineText(today + CoreDate.DAY, today))
        assertEquals("已截止", CoreDate.deadlineText(today - CoreDate.DAY, today))
        assertEquals("还剩 3 天", CoreDate.deadlineText(today + 3 * CoreDate.DAY, today))
        assertEquals("11月5日截止", CoreDate.deadlineText(today + 9 * CoreDate.DAY, today))
    }

    @Test
    fun `isToday 与 isPast 判定正确`() {
        assertTrue(CoreDate.isToday(base, base))
        assertFalse(CoreDate.isToday(base, base + CoreDate.DAY))
        assertTrue(CoreDate.isPast(base - 1, base))
        assertFalse(CoreDate.isPast(base, base))
    }

    @Test
    fun `addDays 与 addMinutes 正确偏移`() {
        assertEquals(base + CoreDate.DAY, CoreDate.addDays(base, 1))
        assertEquals(base - CoreDate.DAY, CoreDate.addDays(base, -1))
        assertEquals(base + 30 * 60_000L, CoreDate.addMinutes(base, 30))
    }
}
