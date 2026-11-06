package com.campus.schedule.data

import com.campus.schedule.core.CoreDate

/**
 * 预置示例数据。
 *
 * 来源：小程序 `database/seed/projects.json` 的 15 条校园项目，
 * 转换为个人日程并**重新锚定到当前日期**（原数据时间戳是 2026 年 10–11 月的固定值，
 * 直接搬过来会让示例全部过期）。
 *
 * 全部标记 `isSample = true`，可在「我的 → 清空示例数据」一键移除。
 */
object SeedData {

    private data class Sample(
        val title: String,
        val note: String,
        val location: String,
        val category: String,
        val accent: String,
        /** 相对今天的天数偏移（正数=未来，0=今天，负数=过去）。 */
        val dayOffset: Int,
        val hour: Int,
        val minute: Int,
        val durationMinutes: Int,
        val isAllDay: Boolean = false,
        val remindBefore: Int? = null
    )

    private val samples = listOf(
        Sample(
            title = "数据结构期中复习讲座",
            note = "覆盖树、图与动态规划，配合历年真题讲解。",
            location = "计算机学院 A301",
            category = "学习",
            accent = "ink",
            dayOffset = 0, hour = 14, minute = 0, durationMinutes = 120,
            remindBefore = 30
        ),
        Sample(
            title = "AI 前沿工作坊",
            note = "大模型微调实战，需自带笔记本。",
            location = "计算机学院 B102",
            category = "学习",
            accent = "gold",
            dayOffset = 0, hour = 15, minute = 0, durationMinutes = 90,
            remindBefore = 30
        ),
        Sample(
            title = "计算机学院学生会周例会",
            note = "本周议题：社团招新场地与志愿者排班。",
            location = "计算机学院 A301",
            category = "会议",
            accent = "ink",
            dayOffset = 1, hour = 19, minute = 30, durationMinutes = 90,
            remindBefore = 10
        ),
        Sample(
            title = "摄影社器材使用测试",
            note = "暗房冲印实操，自带胶卷。",
            location = "艺术楼 暗房",
            category = "学习",
            accent = "sage",
            dayOffset = 2, hour = 9, minute = 0, durationMinutes = 120
        ),
        Sample(
            title = "社区图书整理志愿活动",
            note = "整理与上架 donated 图书，需要 3 名志愿者。",
            location = "青禾社区图书室",
            category = "活动",
            accent = "sage",
            dayOffset = 3, hour = 10, minute = 0, durationMinutes = 180,
            remindBefore = 60
        ),
        Sample(
            title = "大学英语四级模拟考试",
            note = "携带身份证与准考证，提前 30 分钟入场。",
            location = "教学楼 B201-B205",
            category = "考试",
            accent = "gold",
            dayOffset = 5, hour = 9, minute = 0, durationMinutes = 140,
            remindBefore = 1440
        ),
        Sample(
            title = "高等数学作业 3",
            note = "习题册第 5–7 章，线上提交。",
            location = "线上提交",
            category = "学习",
            accent = "gold",
            dayOffset = 6, hour = 0, minute = 0, durationMinutes = 60,
            remindBefore = 1440
        ),
        Sample(
            title = "校园歌手大赛初赛",
            note = "自选曲目，伴奏需提前 30 分钟交给组委会。",
            location = "大学生活动中心 剧场",
            category = "活动",
            accent = "coral",
            dayOffset = 7, hour = 19, minute = 0, durationMinutes = 120
        ),
        Sample(
            title = "2026 秋季校园编程马拉松",
            note = "两人至四人组队，48 小时完成一个可演示的校园场景作品。",
            location = "创新创业中心 B201",
            category = "活动",
            accent = "coral",
            dayOffset = 9, hour = 9, minute = 0, durationMinutes = 2880
        ),
        Sample(
            title = "社团招新市集",
            note = "各社团摆台宣传与现场报名。",
            location = "中心广场",
            category = "活动",
            accent = "coral",
            dayOffset = 12, hour = 10, minute = 0, durationMinutes = 240
        ),
        Sample(
            title = "校园摄影展征稿",
            note = "线上投稿，截止前可多次修改。",
            location = "线上投稿 + 图书馆一层展厅",
            category = "社团",
            accent = "ink",
            dayOffset = 14, hour = 0, minute = 0, durationMinutes = 60
        ),
        Sample(
            title = "夜跑俱乐部值班表",
            note = "本周值班，负责器材借还与打卡点登记。",
            location = "东区田径场",
            category = "社团",
            accent = "sage",
            dayOffset = -2, hour = 20, minute = 0, durationMinutes = 90
        ),
        Sample(
            title = "暑期实习内推：后端开发实习生",
            note = "面向 2027 届投递简历。",
            location = "线上投递",
            category = "实习",
            accent = "gold",
            dayOffset = 16, hour = 0, minute = 0, durationMinutes = 60
        )
    )

    /**
     * 生成示例日程，锚定到 [now] 所在的周。
     * 返回的条目 isSample 均为 true。
     */
    fun build(now: Long = System.currentTimeMillis()): List<ScheduleItem> {
        val today = CoreDate.startOfDay(now)
        val createdAt = now
        return samples.mapIndexed { index, s ->
            val day = CoreDate.addDays(today, s.dayOffset)
            val start = if (s.isAllDay) {
                day
            } else {
                CoreDate.makeTimestamp(
                    CoreDate.parts(day).year,
                    CoreDate.parts(day).month - 1,
                    CoreDate.parts(day).day,
                    s.hour, s.minute, 0, 0
                )
            }
            val end = start + s.durationMinutes * 60 * 1000L
            ScheduleItem(
                id = "sample_${index.toString().padStart(3, '0')}",
                title = s.title,
                note = s.note,
                location = s.location,
                category = s.category,
                accent = s.accent,
                startTime = start,
                endTime = end,
                isAllDay = s.isAllDay,
                remindBefore = s.remindBefore,
                starred = false,
                isSample = true,
                createdAt = createdAt,
                updatedAt = createdAt
            )
        }
    }
}
