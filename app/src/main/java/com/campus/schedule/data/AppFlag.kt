package com.campus.schedule.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 应用级的一次性标记（键值对）。
 *
 * 目前只用来记住「预置示例数据已经灌过了」，见 [ScheduleRepository.seedIfEmpty]。
 * 独立成一张表而不是塞进 SharedPreferences，是因为它和日程数据要在同一个
 * 事务里保持一致：清空全部数据时标记也要一起重置，否则会出现
 * 「清空了数据却再也不给示例」。
 */
@Entity(tableName = "app_flag")
data class AppFlag(
    @PrimaryKey val key: String,
    val value: String
)