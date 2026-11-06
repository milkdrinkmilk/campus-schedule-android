package com.campus.schedule.core

import java.util.Calendar
import java.util.TimeZone

/**
 * 校园时间工具：固定 UTC+8，不依赖设备时区。
 *
 * 对应小程序 `miniprogram/utils/date.js`（逐行对齐，行为一致）。
 * 存储一律毫秒时间戳；展示用这里的方法。
 *
 * 注意 Java `Calendar.DAY_OF_WEEK` 是 1=周日…7=周六，
 * 而 JS `getUTCDay()` 是 0=周日…6=周六，两者相差 1，
 * 因此本文件的星期换算统一走 [jsDayOfWeek]。
 */
object CoreDate {

    const val DAY: Long = 24 * 60 * 60 * 1000L
    const val TZ_OFFSET: Long = 8 * 60 * 60 * 1000L
    val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

    private fun toNumber(value: Long?): Long = value ?: 0L

    /** 把时间戳转成"+08:00 的 UTC 视图"，之后只用 UTC 字段。 */
    private fun utcView(ts: Long): Calendar =
        Calendar.getInstance(UTC).apply { timeInMillis = toNumber(ts) + TZ_OFFSET }

    /** JS `Date.UTC(...)`：纯 UTC，不带 +8 偏移。 */
    private fun plainUtcMillis(year: Int, monthIndex: Int, day: Int): Long =
        Calendar.getInstance(UTC).apply {
            clear()
            set(year, monthIndex, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /**
     * JS `d.getUTCDay()` 的等价：0=周日 … 6=周六。
     * Java 的 DAY_OF_WEEK 是 1=周日…7=周六，故减 1 对齐。
     */
    private fun jsDayOfWeek(cal: Calendar): Int = cal.get(Calendar.DAY_OF_WEEK) - 1

    /** 对应 JS 的 makeTimestamp(year, monthIndex, day, ...) —— monthIndex 为 0 基。 */
    fun makeTimestamp(
        year: Int,
        monthIndex: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
        second: Int = 0,
        ms: Int = 0
    ): Long = Calendar.getInstance(UTC).apply {
        clear()
        set(year, monthIndex, day, hour, minute, second)
        set(Calendar.MILLISECOND, ms)
    }.timeInMillis - TZ_OFFSET

    fun startOfDay(ts: Long): Long {
        val d = utcView(ts)
        return makeTimestamp(
            d.get(Calendar.YEAR),
            d.get(Calendar.MONTH),
            d.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun endOfDay(ts: Long): Long = startOfDay(ts) + DAY - 1

    /** 周一 = 0 … 周日 = 6 */
    fun getWeekdayIndex(ts: Long): Int {
        val d = utcView(ts)
        return (jsDayOfWeek(d) + 6) % 7
    }

    fun startOfWeek(ts: Long): Long = startOfDay(ts) - getWeekdayIndex(ts) * DAY

    fun endOfWeek(ts: Long): Long = startOfWeek(ts) + 7 * DAY - 1

    fun addDays(ts: Long, days: Int): Long = toNumber(ts) + days * DAY

    fun addMinutes(ts: Long, minutes: Int): Long = toNumber(ts) + minutes * 60 * 1000L

    fun isSameDay(a: Long, b: Long): Boolean = startOfDay(a) == startOfDay(b)

    fun toDateKey(ts: Long): String {
        val d = utcView(ts)
        val month = (d.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
        val day = d.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
        return "${d.get(Calendar.YEAR)}-$month-$day"
    }

    fun fromDateKey(key: String): Long {
        // JS: /^(\d{4})-(\d{2})-(\d{2})$/ —— 用 matches() 保证整串匹配
        val match = Regex("(\\d{4})-(\\d{2})-(\\d{2})").matchEntire(key.trim()) ?: return 0L
        val (y, m, d) = match.destructured
        return makeTimestamp(y.toInt(), m.toInt() - 1, d.toInt())
    }

    data class Parts(
        val year: Int,
        val month: Int,
        val day: Int,
        val weekdayIndex: Int,
        val weekday: String,
        val hour: Int,
        val minute: Int,
        val second: Int,
        val dateKey: String
    )

    fun parts(ts: Long): Parts {
        val d = utcView(ts)
        val weekdayIndex = (jsDayOfWeek(d) + 6) % 7
        return Parts(
            year = d.get(Calendar.YEAR),
            month = d.get(Calendar.MONTH) + 1,
            day = d.get(Calendar.DAY_OF_MONTH),
            weekdayIndex = weekdayIndex,
            weekday = WEEKDAYS[weekdayIndex],
            hour = d.get(Calendar.HOUR_OF_DAY),
            minute = d.get(Calendar.MINUTE),
            second = d.get(Calendar.SECOND),
            dateKey = toDateKey(ts)
        )
    }

    private fun pad2(n: Int): String = n.toString().padStart(2, '0')

    fun formatMonthDay(ts: Long): String {
        val p = parts(ts)
        return "${p.month}月${p.day}日"
    }

    fun formatMonth(ts: Long): String = "${parts(ts).month}月"

    fun formatDayNumber(ts: Long): String = "${parts(ts).day}"

    fun formatWeekday(ts: Long): String = parts(ts).weekday

    fun formatDate(ts: Long): String = "${formatMonthDay(ts)} ${formatWeekday(ts)}"

    fun formatTime(ts: Long): String {
        val p = parts(ts)
        return "${pad2(p.hour)}:${pad2(p.minute)}"
    }

    fun formatDateTime(ts: Long): String = "${formatDate(ts)} ${formatTime(ts)}"

    fun formatTimeRange(startTime: Long, endTime: Long, isAllDay: Boolean): String {
        if (isAllDay) return "全天"
        if (startTime == 0L) return "时间待定"
        if (endTime == 0L || endTime == startTime) return formatTime(startTime)
        if (isSameDay(startTime, endTime)) {
            return "${formatTime(startTime)}–${formatTime(endTime)}"
        }
        return "${formatMonthDay(startTime)} ${formatTime(startTime)} – ${formatMonthDay(endTime)} ${formatTime(endTime)}"
    }

    fun formatWeekLabel(ts: Long): String = "第 ${isoWeekNumber(ts)} 周"

    /**
     * ISO 周号。对应 JS：
     * ```
     * const d = shiftedDate(startOfWeek(ts));
     * const thursday = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate() + 3));
     * const yearStart = new Date(Date.UTC(thursday.getUTCFullYear(), 0, 1));
     * return Math.ceil(((thursday - yearStart) / DAY + 1) / 7);
     * ```
     * 注意 thursday / yearStart 都是**纯 UTC**，不带 +8 偏移。
     */
    fun isoWeekNumber(ts: Long): Int {
        val d = utcView(startOfWeek(ts))
        val thursday = plainUtcMillis(
            d.get(Calendar.YEAR),
            d.get(Calendar.MONTH),
            d.get(Calendar.DAY_OF_MONTH) + 3
        )
        val thursdayCal = Calendar.getInstance(UTC).apply { timeInMillis = thursday }
        val yearStart = plainUtcMillis(thursdayCal.get(Calendar.YEAR), 0, 1)
        return Math.ceil(((thursday - yearStart).toDouble() / DAY + 1.0) / 7.0).toInt()
    }

    fun daysUntil(targetTs: Long, nowTs: Long): Int =
        Math.round((startOfDay(targetTs) - startOfDay(nowTs)).toDouble() / DAY).toInt()

    fun deadlineText(endTime: Long, nowTs: Long): String {
        val diff = daysUntil(endTime, nowTs)
        return when {
            diff < 0 -> "已截止"
            diff == 0 -> "今天截止"
            diff == 1 -> "明天截止"
            diff <= 7 -> "还剩 $diff 天"
            else -> "${formatMonthDay(endTime)}截止"
        }
    }

    data class WeekDay(
        val index: Int,
        val timestamp: Long,
        val dateKey: String,
        val weekday: String,
        val day: String,
        val monthDay: String
    )

    fun weekDays(weekStartTs: Long): List<WeekDay> {
        val start = startOfWeek(weekStartTs)
        return (0 until 7).map { i ->
            val ts = start + i * DAY
            WeekDay(
                index = i,
                timestamp = ts,
                dateKey = toDateKey(ts),
                weekday = WEEKDAYS[i],
                day = formatDayNumber(ts),
                monthDay = formatMonthDay(ts)
            )
        }
    }

    fun isToday(ts: Long, nowTs: Long): Boolean = isSameDay(ts, nowTs)

    fun isPast(ts: Long, nowTs: Long): Boolean = toNumber(ts) < nowTs
}
