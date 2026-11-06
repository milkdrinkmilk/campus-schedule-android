package com.campus.schedule

import android.app.Application
import com.campus.schedule.data.AppDatabase
import com.campus.schedule.notify.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScheduleApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.createChannel(this)
        // 应用启动时重建全部闹钟（覆盖应用被杀死后系统未保留的情况）
        appScope.launch {
            try {
                val dao = AppDatabase.get(this@ScheduleApp).scheduleDao()
                ReminderScheduler.rescheduleAll(this@ScheduleApp, dao.findWithReminder())
            } catch (_: Exception) {
                // 忽略：数据库尚未就绪时下次启动会重试
            }
        }
    }
}
