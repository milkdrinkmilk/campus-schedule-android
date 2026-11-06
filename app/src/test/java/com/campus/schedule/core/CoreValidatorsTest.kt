package com.campus.schedule.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CoreValidators 单元测试 —— 规则对齐小程序 `miniprogram/utils/validators.js` 的个人版子集。
 */
class CoreValidatorsTest {

    private fun validForm() = CoreValidators.Form(
        title = "数据结构复习讲座",
        note = "",
        location = "计算机学院 A301",
        category = "学习",
        accent = "ink",
        startTime = 1793068200000L,
        endTime = 1793071800000L,
        isAllDay = false,
        remindBefore = 10,
        starred = false
    )

    @Test
    fun `合法表单通过校验`() {
        val result = CoreValidators.validate(validForm())
        assertTrue(result.valid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `标题过短或过长报错`() {
        val short = CoreValidators.validate(validForm().copy(title = "a"))
        assertFalse(short.valid)
        assertEquals("标题需要 2–60 个字", short.errors["title"])

        val long = CoreValidators.validate(validForm().copy(title = "标".repeat(61)))
        assertFalse(long.valid)
        assertTrue(long.errors.containsKey("title"))
    }

    @Test
    fun `标题首尾空白被裁剪`() {
        val result = CoreValidators.validate(validForm().copy(title = "  数据结构复习讲座  "))
        assertTrue(result.valid)
        assertEquals("数据结构复习讲座", result.value.title)
    }

    @Test
    fun `缺少开始或结束时间报错`() {
        val noStart = CoreValidators.validate(validForm().copy(startTime = 0L))
        assertFalse(noStart.valid)
        assertEquals("请选择开始时间", noStart.errors["startTime"])

        val noEnd = CoreValidators.validate(validForm().copy(endTime = 0L))
        assertFalse(noEnd.valid)
        assertEquals("请选择结束时间", noEnd.errors["endTime"])
    }

    @Test
    fun `结束早于开始报错`() {
        val result = CoreValidators.validate(
            validForm().copy(startTime = 1793071800000L, endTime = 1793068200000L)
        )
        assertFalse(result.valid)
        assertEquals("结束时间不能早于开始时间", result.errors["endTime"])
    }

    @Test
    fun `备注与地点超长报错`() {
        val longNote = CoreValidators.validate(validForm().copy(note = "字".repeat(2001)))
        assertFalse(longNote.valid)
        assertTrue(longNote.errors.containsKey("note"))

        val longLoc = CoreValidators.validate(validForm().copy(location = "地".repeat(101)))
        assertFalse(longLoc.valid)
        assertTrue(longLoc.errors.containsKey("location"))
    }

    @Test
    fun `非法分类与色系报错`() {
        val badCategory = CoreValidators.validate(validForm().copy(category = "不存在"))
        assertFalse(badCategory.valid)
        assertEquals("请选择分类", badCategory.errors["category"])

        val badAccent = CoreValidators.validate(validForm().copy(accent = "rainbow"))
        assertFalse(badAccent.valid)
        assertEquals("请选择卡片色系", badAccent.errors["accent"])
    }

    @Test
    fun `全天日程自动归一化到当天边界`() {
        // 全天是合法输入：不再报「未落在零点」，而是静默归一化到 00:00 – 23:59:59.999
        val form = validForm().copy(isAllDay = true)
        val result = CoreValidators.validate(form)
        assertTrue(result.valid)
        assertEquals(CoreDate.startOfDay(form.startTime), result.value.startTime)
        assertEquals(CoreDate.endOfDay(form.startTime), result.value.endTime)
        assertTrue(result.value.isAllDay)
    }

    @Test
    fun `非全天日程不会改变原时间`() {
        val form = validForm().copy(isAllDay = false)
        val result = CoreValidators.validate(form)
        assertTrue(result.valid)
        assertEquals(form.startTime, result.value.startTime)
        assertEquals(form.endTime, result.value.endTime)
    }

    @Test
    fun `非法提醒时间报错`() {
        val result = CoreValidators.validate(validForm().copy(remindBefore = 33))
        assertFalse(result.valid)
        assertEquals("请选择提醒时间", result.errors["remindBefore"])
    }

    @Test
    fun `提醒为 null 视为不提醒且合法`() {
        val result = CoreValidators.validate(validForm().copy(remindBefore = null))
        assertTrue(result.valid)
        assertEquals(null, result.value.remindBefore)
    }
}
