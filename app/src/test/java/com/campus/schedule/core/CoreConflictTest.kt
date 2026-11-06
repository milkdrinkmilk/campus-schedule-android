package com.campus.schedule.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CoreConflict 单元测试 —— 期望值来自小程序 `miniprogram/utils/conflict.js` 的规则。
 */
class CoreConflictTest {

    private fun item(
        id: String,
        start: Long,
        end: Long,
        allDay: Boolean = false,
        cancelled: Boolean = false,
        ended: Boolean = false
    ) = TestItem(id, start, end, allDay, cancelled, ended)

    data class TestItem(
        override val id: String,
        override val startTime: Long,
        override val endTime: Long,
        override val allDay: Boolean,
        override val cancelled: Boolean,
        override val ended: Boolean
    ) : CoreConflict.TimeRange

    @Test
    fun `重叠判定 使用左闭右开区间`() {
        val a = item("a", 1000, 2000)
        val b = item("b", 2000, 3000)
        val c = item("c", 1500, 2500)
        val d = item("d", 2500, 3000)

        // 端点相接不算重叠
        assertFalse(CoreConflict.overlaps(a, b))
        // 真正相交
        assertTrue(CoreConflict.overlaps(a, c))
        assertTrue(CoreConflict.overlaps(c, a))
        assertFalse(CoreConflict.overlaps(a, d))
    }

    @Test
    fun `全天日程不参与冲突检测`() {
        val allDay = item("all", 1000, 2000, allDay = true)
        val normal = item("n", 1000, 2000)
        assertFalse(CoreConflict.participates(allDay))
        assertTrue(CoreConflict.participates(normal))
        // 即使时间重叠，全天也不进冲突列表
        val pairs = CoreConflict.detectConflicts(listOf(allDay, normal))
        assertEquals(0, pairs.size)
    }

    @Test
    fun `已取消与已结束不参与冲突检测`() {
        val cancelled = item("c", 1000, 2000, cancelled = true)
        val ended = item("e", 1000, 2000, ended = true)
        assertFalse(CoreConflict.participates(cancelled))
        assertFalse(CoreConflict.participates(ended))
    }

    @Test
    fun `detectConflicts 返回所有重叠对`() {
        val list = listOf(
            item("a", 1000, 2000),
            item("b", 1500, 2500),
            item("c", 3000, 4000)
        )
        val pairs = CoreConflict.detectConflicts(list)
        assertEquals(1, pairs.size)
        assertEquals("a", pairs[0].a.id)
        assertEquals("b", pairs[0].b.id)
    }

    @Test
    fun `conflictIdSet 收集冲突双方 id`() {
        val list = listOf(
            item("a", 1000, 2000),
            item("b", 1500, 2500),
            item("c", 1600, 2600),
            item("d", 5000, 6000)
        )
        val set = CoreConflict.conflictIdSet(list)
        assertEquals(setOf("a", "b", "c"), set)
    }

    @Test
    fun `findConflictsFor 排除自身并强制不要求 setting`() {
        val target = item("a", 1000, 2000)
        val list = listOf(
            target,
            item("b", 1500, 2500),
            item("c", 8000, 9000)
        )
        val found = CoreConflict.findConflictsFor(target, list)
        assertEquals(1, found.size)
        assertEquals("b", found[0].id)
    }

    @Test
    fun `conflictTitle 输出重叠数量`() {
        assertEquals("", CoreConflict.conflictTitle(0))
        assertEquals("2 个日程时间重叠", CoreConflict.conflictTitle(2))
    }
}
