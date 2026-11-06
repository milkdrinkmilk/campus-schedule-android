package com.campus.schedule.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.campus.schedule.data.ScheduleItem

/** 闹钟触发时弹出本地通知。 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "日程提醒"
        val location = intent.getStringExtra(EXTRA_LOCATION) ?: ""
        val startTime = intent.getLongExtra(EXTRA_START_TIME, 0L)
        val isAllDay = intent.getBooleanExtra(EXTRA_IS_ALL_DAY, false)
        ReminderScheduler.notify(context, id, title, location, startTime, isAllDay)
    }

    companion object {
        const val EXTRA_ID = "extra_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_LOCATION = "extra_location"
        const val EXTRA_START_TIME = "extra_start_time"
        const val EXTRA_IS_ALL_DAY = "extra_is_all_day"
    }
}
