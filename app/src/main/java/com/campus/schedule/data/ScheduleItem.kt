package com.campus.schedule.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.campus.schedule.core.CoreConflict

/**
 * 个人日程条目（离线版简化模型）。
 *
 * 对应小程序 `projects` 集合的**个人版子集**：
 * 砍掉 visibleTo / allow* / creator* / contact* / requiredMaterials / attachments /
 * type / level / necessity / audience / summary / content / status 等云端专属字段。
 */
@Entity(tableName = "schedule_item")
data class ScheduleItem(
    @PrimaryKey override val id: String,
    val title: String,
    val note: String = "",
    val location: String = "",
    val category: String = "其他",
    val accent: String = "coral",
    override val startTime: Long,
    override val endTime: Long,
    val isAllDay: Boolean = false,
    val remindBefore: Int? = null,
    val starred: Boolean = false,
    /** 是否为预置示例数据（可一键清空，用户新建的永远为 false）。 */
    val isSample: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) : CoreConflict.TimeRange {

    /**
     * 全天标记。Room 的 `isAllDay` 字段与本 getter 命名冲突，
     * 故用 @Ignore 让 Room 只认构造参数这一处。
     */
    @get:Ignore
    override val allDay: Boolean get() = isAllDay
    override val cancelled: Boolean get() = false
    override val ended: Boolean get() = false
}
