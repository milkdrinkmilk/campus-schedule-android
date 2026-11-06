package com.campus.schedule.core

/**
 * 展示层格式化。
 *
 * 对应小程序 `miniprogram/utils/format.js` 的**离线个人版子集**：
 * 群组可见、角色标签、申请/反馈等云端专属逻辑已剔除。
 */
object CoreFormat {

    private val STATUS_CLASS = mapOf(
        "未开始" to "upcoming",
        "进行中" to "ongoing",
        "已结束" to "ended",
        "已取消" to "cancelled"
    )

    private val REMIND_LABELS = mapOf(
        0 to "开始时",
        10 to "提前 10 分钟",
        60 to "提前 1 小时",
        1440 to "提前 1 天"
    )

    fun statusClass(status: String): String = STATUS_CLASS[status] ?: "neutral"

    /** minutes 为 null 表示不提醒。 */
    fun remindLabel(minutes: Int?): String {
        if (minutes == null) return "不提醒"
        return REMIND_LABELS[minutes] ?: "提前 $minutes 分钟"
    }

    fun timeText(startTime: Long, endTime: Long, isAllDay: Boolean): String =
        CoreDate.formatTimeRange(startTime, endTime, isAllDay)

    fun initials(name: String?): String {
        val text = (name ?: "").trim()
        if (text.isEmpty()) return "我"
        return text.take(1)
    }

    fun truncate(text: String?, max: Int): String {
        val value = text ?: ""
        if (max <= 0 || value.length <= max) return value
        return value.take(max) + "…"
    }

    /** 状态判定：未开始 / 进行中 / 已结束。 */
    fun displayStatus(startTime: Long, endTime: Long, nowTs: Long): String = when {
        nowTs < startTime -> "未开始"
        endTime in 1..(nowTs) -> "已结束"
        else -> "进行中"
    }

    fun a11yLabel(
        title: String,
        category: String?,
        startTime: Long,
        endTime: Long,
        isAllDay: Boolean,
        location: String?,
        displayStatus: String
    ): String = listOfNotNull(
        title.ifBlank { null },
        category?.ifBlank { null },
        timeText(startTime, endTime, isAllDay),
        location?.ifBlank { null },
        displayStatus
    ).joinToString("，")
}
