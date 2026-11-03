package vn.quangns.timeforbed.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import vn.quangns.timeforbed.core.Permissions

/**
 * Đánh thức lần kiểm tra chế độ ban đêm khi màn hình tắt. Handler của dịch vụ Trợ năng không chạy
 * khi CPU ngủ sâu, nên lúc đó lịch kiểm tra được hẹn thêm bằng AlarmManager.
 */
class NightCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION) BedtimeAccessibilityService.instance?.runScheduledCheck()
    }

    companion object {
        private const val ACTION = "vn.quangns.timeforbed.NIGHT_CHECK"

        private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, ACTION.hashCode(),
            Intent(context, NightCheckReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun schedule(context: Context, at: Long) {
            val am = context.getSystemService(AlarmManager::class.java)
            val pi = pending(context)
            try {
                if (Permissions.canScheduleExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (_: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }

        fun cancel(context: Context) = context.getSystemService(AlarmManager::class.java).cancel(pending(context))
    }
}
