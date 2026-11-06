package com.campus.schedule.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 应用级键值对存储。
 *
 * v2.7 起扩展为通用偏好存储，支持：
 * - `FLAG_SEEDED`：预置示例数据是否已灌入
 * - `KEY_PALETTE`：用户手动选择的色板索引（空串 = 按周自动轮换）
 * - `KEY_DARK_MODE`：深色模式开关（"1" = 开启，其他 = 关闭）
 *
 * 独立成一张表而不是塞进 SharedPreferences，是因为它和日程数据要在同一个
 * 事务里保持一致：清空全部数据时标记也要一起重置。
 */
@Entity(tableName = "app_flag")
data class AppFlag(
    @PrimaryKey val key: String,
    val value: String
) {
    companion object {
        const val FLAG_SEEDED = "seeded"
        const val KEY_PALETTE = "palette_index"
        const val KEY_DARK_MODE = "dark_mode"
    }
}