package com.campus.schedule.data

import kotlinx.coroutines.flow.Flow

/**
 * 本地数据仓库 —— 替代小程序的 `services/api.js` + `mock/index.js`。
 *
 * 原来 37 个云接口收敛为这里的本地方法；所有数据都在本机 SQLite，无网络依赖。
 */
class ScheduleRepository(private val dao: ScheduleDao) {

    fun observeAll(): Flow<List<ScheduleItem>> = dao.observeAll()

    suspend fun findById(id: String): ScheduleItem? = dao.findById(id)

    suspend fun create(item: ScheduleItem): ScheduleItem {
        val now = System.currentTimeMillis()
        val saved = item.copy(
            id = item.id.ifBlank { "item_${now}_${(now % 100000).toString().padStart(5, '0')}" },
            isSample = false,
            createdAt = now,
            updatedAt = now
        )
        dao.insert(saved)
        return saved
    }

    suspend fun update(item: ScheduleItem): ScheduleItem {
        val saved = item.copy(updatedAt = System.currentTimeMillis())
        dao.update(saved)
        return saved
    }

    suspend fun delete(item: ScheduleItem) = dao.delete(item)

    suspend fun count(): Int = dao.count()

    /** 首次启动预置示例数据（仅当库为空时）。 */
    suspend fun seedIfEmpty(now: Long = System.currentTimeMillis()): Boolean {
        if (dao.count() > 0) return false
        dao.insertAll(SeedData.build(now))
        return true
    }

    /** 一键清空全部数据（含示例）。 */
    suspend fun clearAll() = dao.deleteAll()

    /** 只清空预置示例，保留用户自己创建的日程。 */
    suspend fun clearSamples() = dao.deleteSamples()

    suspend fun findWithReminder(): List<ScheduleItem> = dao.findWithReminder()

    suspend fun importAll(items: List<ScheduleItem>) = dao.insertAll(items)
}
