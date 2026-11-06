package com.campus.schedule.core

/**
 * 表单校验与枚举。
 *
 * 对应小程序 `miniprogram/utils/validators.js` 的**离线个人版子集**：
 * 群组可见范围、管理员发布限制、必带材料、附件、标签、申请与反馈表单均已剔除，
 * 只保留个人日程真正需要的规则（标题/时间/地点/备注/分类/提醒/色系）。
 */
object CoreValidators {

    val CATEGORIES = listOf("学习", "活动", "考试", "会议", "社团", "实习", "其他")
    val ACCENTS = listOf("coral", "sage", "gold", "ink")
    val REMIND_OPTIONS = listOf(0, 10, 60, 1440)

    const val TITLE_MIN = 2
    const val TITLE_MAX = 60
    const val NOTE_MAX = 2000
    const val LOCATION_MAX = 100

    data class Form(
        val title: String = "",
        val note: String = "",
        val location: String = "",
        val category: String = "其他",
        val accent: String = "coral",
        val startTime: Long = 0L,
        val endTime: Long = 0L,
        val isAllDay: Boolean = false,
        val remindBefore: Int? = null,
        val starred: Boolean = false
    )

    data class Result(
        val valid: Boolean,
        val errors: Map<String, String> = emptyMap(),
        val value: Form = Form()
    )

    fun validate(form: Form): Result {
        val errors = linkedMapOf<String, String>()

        val title = form.title.trim()
        val note = form.note.trim()
        val location = form.location.trim()

        if (title.length < TITLE_MIN || title.length > TITLE_MAX) {
            errors["title"] = "标题需要 $TITLE_MIN–$TITLE_MAX 个字"
        }
        if (note.length > NOTE_MAX) errors["note"] = "备注不能超过 $NOTE_MAX 个字"
        if (location.length > LOCATION_MAX) errors["location"] = "地点不能超过 $LOCATION_MAX 个字"
        if (CATEGORIES.none { it == form.category }) errors["category"] = "请选择分类"
        if (ACCENTS.none { it == form.accent }) errors["accent"] = "请选择卡片色系"

        // 全天日程的起止时间是「派生值」：开始 = 当天 00:00，结束 = 当天 23:59:59。
        // 这里直接归一化而不是报错 —— 之前把「全天却不在 00:00」写进 errors，
        // 导致用户一打开「全天」开关就永远保存不了（valid=false 被 UI 直接拦下）。
        val allDay = form.isAllDay && form.startTime != 0L
        val startTime = if (allDay) CoreDate.startOfDay(form.startTime) else form.startTime
        val endTime = if (allDay) CoreDate.endOfDay(form.startTime) else form.endTime

        if (form.startTime == 0L) errors["startTime"] = "请选择开始时间"
        if (!allDay && form.endTime == 0L) errors["endTime"] = "请选择结束时间"
        if (!allDay && form.startTime != 0L && form.endTime != 0L && form.endTime < form.startTime) {
            errors["endTime"] = "结束时间不能早于开始时间"
        }

        if (form.remindBefore != null && REMIND_OPTIONS.none { it == form.remindBefore }) {
            errors["remindBefore"] = "请选择提醒时间"
        }

        return Result(
            valid = errors.isEmpty(),
            errors = errors,
            value = form.copy(
                title = title,
                note = note,
                location = location,
                startTime = startTime,
                endTime = endTime
            )
        )
    }
}
