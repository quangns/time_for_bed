package vn.quangns.timeforbed.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import vn.quangns.timeforbed.R
import vn.quangns.timeforbed.core.MediaKiller
import vn.quangns.timeforbed.core.MediaVolume
import vn.quangns.timeforbed.core.Notifications
import vn.quangns.timeforbed.core.Prefs

class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_EXPIRE -> {
                val prefs = Prefs.get(context)
                if (prefs.timerDeadline == 0L) return
                SleepTimer.cancel(context)
                // Giữ tiến trình sống tới lần kiểm tra cuối (~4 s), kể cả khi app đang ở nền/đã bị đóng.
                val pending = goAsync()
                MediaKiller.stopAll(context, reason = "Hết giờ hẹn ngủ", lockScreen = prefs.lockAfterStop) {
                    pending?.finish()
                }
            }
            ACTION_WARN -> if (Prefs.get(context).timerDeadline != 0L) showWarning(context)
            ACTION_EXTEND -> SleepTimer.extend(context)
            ACTION_CANCEL -> SleepTimer.cancel(context)
            ACTION_RESTORE_VOLUME -> MediaVolume.restore(context)
        }
    }

    private fun showWarning(context: Context) {
        val n = NotificationCompat.Builder(context, Notifications.CH_WARNING)
            .setSmallIcon(R.drawable.ic_moon)
            .setContentTitle("Còn 1 phút nữa sẽ tắt video/nhạc")
            .setContentText("Bấm +${SleepTimer.EXTEND_MINUTES} phút nếu bạn vẫn đang xem")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter(70_000)
            .addAction(0, "+${SleepTimer.EXTEND_MINUTES} phút", actionIntent(context, ACTION_EXTEND))
            .addAction(0, "Hủy hẹn giờ", actionIntent(context, ACTION_CANCEL))
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java).notify(Notifications.ID_TIMER_WARNING, n)
        }
    }

    companion object {
        const val ACTION_EXPIRE = "vn.quangns.timeforbed.TIMER_EXPIRE"
        const val ACTION_WARN = "vn.quangns.timeforbed.TIMER_WARN"
        const val ACTION_EXTEND = "vn.quangns.timeforbed.TIMER_EXTEND"
        const val ACTION_CANCEL = "vn.quangns.timeforbed.TIMER_CANCEL"
        const val ACTION_RESTORE_VOLUME = "vn.quangns.timeforbed.RESTORE_VOLUME"

        fun actionIntent(context: Context, action: String): PendingIntent =
            PendingIntent.getBroadcast(
                context, action.hashCode(),
                Intent(context, TimerReceiver::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
