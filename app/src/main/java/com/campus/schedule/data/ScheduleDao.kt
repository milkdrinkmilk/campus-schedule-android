package com.campus.schedule.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM schedule_item ORDER BY startTime ASC, endTime ASC")
    fun observeAll(): Flow<List<ScheduleItem>>

    @Query("SELECT * FROM schedule_item WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ScheduleItem?

    @Query("SELECT * FROM schedule_item WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ScheduleItem?>

    @Query("SELECT COUNT(*) FROM schedule_item")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM schedule_item WHERE isSample = 1")
    suspend fun countSamples(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ScheduleItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ScheduleItem)

    @Update
    suspend fun update(item: ScheduleItem)

    @Delete
    suspend fun delete(item: ScheduleItem)

    @Query("DELETE FROM schedule_item")
    suspend fun deleteAll()

    @Query("DELETE FROM schedule_item WHERE isSample = 1")
    suspend fun deleteSamples()

    /** 到期提醒需要重建闹钟，返回所有设置了提醒的条目。 */
    @Query("SELECT * FROM schedule_item WHERE remindBefore IS NOT NULL")
    suspend fun findWithReminder(): List<ScheduleItem>

    // ---------------------------------------------------------------- 应用级标记
    //
    // 「预置示例只灌一次」这件事必须落盘。只靠 `count() == 0` 判断的话，
    // 用户把示例数据一条条删干净、再杀掉进程重进，表又空了 —— 示例会
    // 原样长回来，表现就是「删了重启还在」。所以单独存一行标记。

    @Query("SELECT value FROM app_flag WHERE key = :key LIMIT 1")
    suspend fun flag(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setFlag(flag: AppFlag)

    @Query("DELETE FROM app_flag")
    suspend fun clearFlags()
}