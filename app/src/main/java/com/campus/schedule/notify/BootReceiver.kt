package com.campus.schedule.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.campus.schedule.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 设备重启或应用更新后重建全部闹钟队列。 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.createChannel(appContext)
                val dao = AppDatabase.get(appContext).scheduleDao()
                val items = dao.findWithReminder()
                ReminderScheduler.rescheduleAll(appContext, items)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
