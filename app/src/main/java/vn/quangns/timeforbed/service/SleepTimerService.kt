package vn.quangns.timeforbed.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import vn.quangns.timeforbed.MainActivity
import vn.quangns.timeforbed.R
import vn.quangns.timeforbed.core.Notifications
import vn.quangns.timeforbed.core.Prefs

/** Foreground service chỉ để hiển thị đếm ngược (chronometer) — việc tắt do AlarmManager đảm nhận. */
class SleepTimerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deadline = Prefs.get(this).timerDeadline
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(this, Notifications.ID_TIMER, build(this, deadline), type)
        if (deadline <= System.currentTimeMillis()) stopSelf()
        return START_NOT_STICKY
    }

    companion object {
        fun build(context: Context, deadline: Long): Notification {
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(context, Notifications.CH_TIMER)
                .setSmallIcon(R.drawable.ic_moon)
                .setContentTitle("Hẹn giờ ngủ đang chạy")
                .setContentText("Sẽ tắt video/nhạc khi hết giờ")
                .setWhen(deadline)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .addAction(0, "+${SleepTimer.EXTEND_MINUTES} phút", TimerReceiver.actionIntent(context, TimerReceiver.ACTION_EXTEND))
                .addAction(0, "Hủy", TimerReceiver.actionIntent(context, TimerReceiver.ACTION_CANCEL))
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        }
    }
}
