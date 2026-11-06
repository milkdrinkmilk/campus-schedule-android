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

    /**
     * 首次启动预置示例数据。
     *
     * 判断条件**不是**「表里有没有数据」，而是「有没有灌过」：
     *
     * * 旧实现只看 `count() > 0`，用户把示例一条条删光 → 表空了 → 下次冷启动
     *   又被当成首次启动，示例原样长回来。表现就是「删了，重启还在」。
     * * 现在在 `app_flag` 里落一个 `seeded` 标记，灌过就不再灌。
     *   用户在「我的」里点「清空全部数据」时，标记会一起重置（见 [clearAll]），
     *   所以「清空」之后仍然可以重新看到示例。
     */
    suspend fun seedIfEmpty(now: Long = System.currentTimeMillis()): Boolean {
        if (dao.flag(AppFlag.FLAG_SEEDED) != null) return false
        val inserted = if (dao.count() == 0) {
            dao.insertAll(SeedData.build(now))
            true
        } else {
            // 老版本升级上来的用户：表里已经有数据，只是还没有标记。
            // 补一个标记，避免他之后删光数据时示例又冒出来。
            false
        }
        dao.setFlag(AppFlag(AppFlag.FLAG_SEEDED, "1"))
        return inserted
    }

    /** 一键清空全部数据（含示例），并把「已灌过示例」标记复位。 */
    suspend fun clearAll() {
        dao.deleteAll()
        dao.clearFlags()
    }

    /** 只清空预置示例，保留用户自己创建的日程；标记保持，示例不会自己回来。 */
    suspend fun clearSamples() {
        dao.deleteSamples()
        dao.setFlag(AppFlag(AppFlag.FLAG_SEEDED, "1"))
    }

    suspend fun findWithReminder(): List<ScheduleItem> = dao.findWithReminder()

    suspend fun importAll(items: List<ScheduleItem>) = dao.insertAll(items)

    // ── v2.7 偏好存储 ──────────────────────────────────────────────

    /** 获取用户手动选择的色板索引；null = 按周自动轮换。 */
    suspend fun getPaletteIndex(): Int? =
        dao.flag(AppFlag.KEY_PALETTE)?.toIntOrNull()

    /** 设置色板索引；null = 恢复自动轮换。 */
    suspend fun setPaletteIndex(index: Int?) {
        if (index == null) {
            dao.setFlag(AppFlag(AppFlag.KEY_PALETTE, ""))
        } else {
            dao.setFlag(AppFlag(AppFlag.KEY_PALETTE, index.toString()))
        }
    }

    /** 获取深色模式开关。 */
    suspend fun getDarkMode(): Boolean =
        dao.flag(AppFlag.KEY_DARK_MODE) == "1"

    /** 设置深色模式开关。 */
    suspend fun setDarkMode(enabled: Boolean) {
        dao.setFlag(AppFlag(AppFlag.KEY_DARK_MODE, if (enabled) "1" else "0"))
    }
}