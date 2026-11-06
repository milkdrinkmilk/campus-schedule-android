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
}
