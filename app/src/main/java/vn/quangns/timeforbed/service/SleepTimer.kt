package vn.quangns.timeforbed.service

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import vn.quangns.timeforbed.core.Notifications
import vn.quangns.timeforbed.core.Permissions
import vn.quangns.timeforbed.core.Prefs

/** Điều khiển hẹn giờ ngủ: lưu hạn chót, đặt AlarmManager và bật service đếm ngược. */
object SleepTimer {
    const val EXTEND_MINUTES = 10
    private const val WARN_BEFORE_MS = 60_000L

    fun start(context: Context, minutes: Int) {
        val prefs = Prefs.get(context)
        prefs.lastTimerMinutes = minutes
        setDeadline(context, System.currentTimeMillis() + minutes * 60_000L)
    }

    fun extend(context: Context, minutes: Int = EXTEND_MINUTES) {
        val current = Prefs.get(context).timerDeadline
        val base = maxOf(current, System.currentTimeMillis())
        setDeadline(context, base + minutes * 60_000L)
    }

    fun cancel(context: Context) {
        Prefs.get(context).timerDeadline = 0L
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pending(context, TimerReceiver.ACTION_EXPIRE))
        am.cancel(pending(context, TimerReceiver.ACTION_WARN))
        context.getSystemService(NotificationManager::class.java).cancel(Notifications.ID_TIMER_WARNING)
        context.stopService(Intent(context, SleepTimerService::class.java))
    }

    /** Khôi phục sau khi khởi động lại máy / cập nhật app. */
    fun restore(context: Context) {
        val deadline = Prefs.get(context).timerDeadline
        if (deadline == 0L) return
        if (deadline <= System.currentTimeMillis()) cancel(context) else setDeadline(context, deadline)
    }

    private fun setDeadline(context: Context, deadline: Long) {
        Prefs.get(context).timerDeadline = deadline
        context.getSystemService(NotificationManager::class.java).cancel(Notifications.ID_TIMER_WARNING)
        schedule(context, deadline, TimerReceiver.ACTION_EXPIRE)
        val warnAt = deadline - WARN_BEFORE_MS
        val am = context.getSystemService(AlarmManager::class.java)
        if (warnAt > System.currentTimeMillis()) schedule(context, warnAt, TimerReceiver.ACTION_WARN)
        else am.cancel(pending(context, TimerReceiver.ACTION_WARN))
        ContextCompat.startForegroundService(context, Intent(context, SleepTimerService::class.java))
    }

    private fun schedule(context: Context, at: Long, action: String) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, action)
        if (Permissions.canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pending(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, TimerReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
