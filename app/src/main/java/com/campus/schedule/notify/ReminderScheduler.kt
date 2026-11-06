package com.campus.schedule.notify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.campus.schedule.MainActivity
import com.campus.schedule.R
import com.campus.schedule.core.CoreDate
import com.campus.schedule.data.ScheduleItem

/**
 * 本地通知提醒 —— 替代小程序的云端订阅消息。
 *
 * 优势：不依赖服务端存活，提醒更可靠。
 * 需要处理：
 *  - Android 13+ 运行时通知权限（POST_NOTIFICATIONS）
 *  - Android 12+ 精确闹钟权限（SCHEDULE_EXACT_ALARM）
 *  - 重启后重建闹钟队列（BootReceiver）
 */
object ReminderScheduler {

    const val CHANNEL_ID = "schedule_reminder"
    private const val REQUEST_CODE_BASE = 10_000

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "日程提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "日程开始前的提醒通知"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * 为单个日程安排提醒。
     *
     * 无论后面是否真的会排上闹钟，都**先取消旧闹钟**：
     * 用户把提醒从「提前 10 分钟」改成「不提醒」、或把时间改到过去，
     * 若不取消，那个旧闹钟会继续在原来的时刻响 —— 这是最容易被忽略的残留 bug。
     */
    fun schedule(context: Context, item: ScheduleItem) {
        cancel(context, item.id)

        val before = item.remindBefore ?: return
        val triggerAt = CoreDate.addMinutes(item.startTime, -before)
        if (triggerAt <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_ID, item.id)
            putExtra(ReminderReceiver.EXTRA_TITLE, item.title)
            putExtra(ReminderReceiver.EXTRA_LOCATION, item.location)
            putExtra(ReminderReceiver.EXTRA_START_TIME, item.startTime)
            putExtra(ReminderReceiver.EXTRA_IS_ALL_DAY, item.isAllDay)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(item.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 有精确闹钟权限就用精确闹钟，否则退回不精确闹钟（系统会择机触发）。
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancel(context: Context, itemId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_ID, itemId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(itemId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /** 重建全部闹钟（应用启动 / 设备重启后调用）。 */
    fun rescheduleAll(context: Context, items: List<ScheduleItem>) {
        items.forEach { schedule(context, it) }
    }

    /** 批量取消（清空数据 / 删除示例数据时调用，避免清完还弹旧的提醒）。 */
    fun cancelAll(context: Context, items: List<ScheduleItem>) {
        items.forEach { cancel(context, it.id) }
    }

    fun notify(context: Context, itemId: String, title: String, location: String, startTime: Long, isAllDay: Boolean) {
        createChannel(context)

        val timeText = CoreDate.formatTimeRange(startTime, startTime, isAllDay)
        val body = if (location.isBlank()) timeText else "$timeText · $location"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_FOCUS_ID, itemId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode(itemId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Android 13+ 未授权时 notify 会抛异常，这里静默跳过。
        try {
            NotificationManagerCompat.from(context).notify(requestCode(itemId), notification)
        } catch (_: SecurityException) {
            // 权限未授予，忽略。
        }
    }

    /**
     * 用 id 的哈希做 requestCode。
     *
     * 掩码从 0x0FFF（4096 个槽，几百条日程就会有 ~3% 的碰撞概率，碰撞后
     * 后建的闹钟会覆盖先建的、取消也会误伤）放宽到 0x3FFF_FFFF，
     * 碰撞概率降到可忽略。AlarmManager 的 requestCode 是 Int，正数足够用。
     */
    private fun requestCode(itemId: String): Int = REQUEST_CODE_BASE + (itemId.hashCode() and 0x3FFFFFFF)
}
