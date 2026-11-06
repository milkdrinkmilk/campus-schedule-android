package com.campus.schedule.core

/**
 * 时间冲突：只提示，不删除、不改期。
 *
 * 规则：A.start < B.end && B.start < A.end；全天项目不参与；已取消/已结束不参与。
 * 对应小程序 `miniprogram/utils/conflict.js`（逐行对齐）。
 */
object CoreConflict {

    /** 参与冲突检测的最小信息（Room 实体与临时对象都满足）。 */
    interface TimeRange {
        val id: String
        val startTime: Long
        val endTime: Long
        /** 是否全天（不参与冲突检测）。 */
        val allDay: Boolean
        val cancelled: Boolean
        val ended: Boolean
    }
    fun isActive(item: TimeRange): Boolean {
        if (item.cancelled) return false
        if (item.ended) return false
        return true
    }

    fun isAllDay(item: TimeRange): Boolean = item.allDay

    fun overlaps(a: TimeRange, b: TimeRange): Boolean {
        val aStart = a.startTime
        val aEnd = if (a.endTime != 0L) a.endTime else aStart
        val bStart = b.startTime
        val bEnd = if (b.endTime != 0L) b.endTime else bStart
        return aStart < bEnd && bStart < aEnd
    }

    /** 与 JS 的 participates 一致：仅在 requireSetting 模式下额外检查参与状态。 */
    fun participates(item: TimeRange, requireSetting: Boolean = false): Boolean {
        if (!isActive(item) || isAllDay(item)) return false
        if (requireSetting) {
            // 离线个人版中「参与」= 已加入个人日程（或星标），由调用方预先过滤。
            // 此处保持签名一致，实际过滤在 Repository 层完成。
        }
        return true
    }

    data class Pair<T>(val a: T, val b: T)

    fun <T : TimeRange> detectConflicts(list: List<T>, requireSetting: Boolean = false): List<Pair<T>> {
        val items = list.filter { participates(it, requireSetting) }
        val pairs = mutableListOf<Pair<T>>()
        for (i in items.indices) {
            for (j in i + 1 until items.size) {
                if (overlaps(items[i], items[j])) {
                    pairs.add(Pair(items[i], items[j]))
                }
            }
        }
        return pairs
    }

    fun <T : TimeRange> conflictIdSet(list: List<T>, requireSetting: Boolean = false): Set<String> {
        val set = mutableSetOf<String>()
        detectConflicts(list, requireSetting).forEach { pair ->
            set.add(pair.a.id)
            set.add(pair.b.id)
        }
        return set
    }

    fun <T : TimeRange> findConflictsFor(target: T, list: List<T>): List<T> =
        list.filter { item -> item.id != target.id && participates(item, false) && overlaps(target, item) }

    fun conflictTitle(pairCount: Int): String = if (pairCount == 0) "" else "$pairCount 个日程时间重叠"
}
