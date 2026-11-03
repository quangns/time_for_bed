package vn.quangns.timeforbed.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vn.quangns.timeforbed.alarm.AlarmMirror
import vn.quangns.timeforbed.alarm.AlarmScheduler
import vn.quangns.timeforbed.pomodoro.Pomodoro

/** Đặt lại báo thức và hẹn giờ sau khi khởi động lại, đổi giờ hoặc cập nhật app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!AlarmMirror.isUserUnlocked(app)) {
                    // Vừa khởi động, chưa mở khóa: chỉ đặt lại báo thức từ bản sao; phần còn lại chờ BOOT_COMPLETED.
                    AlarmScheduler.rescheduleFromMirror(app)
                    return@launch
                }
                runCatching { AlarmScheduler.rescheduleAll(app) }
                runCatching { SleepTimer.restore(app) }
                runCatching { Pomodoro.restore(app) }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED", // API 31+
        )
    }
}
