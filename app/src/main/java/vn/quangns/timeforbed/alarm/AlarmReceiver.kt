package vn.quangns.timeforbed.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vn.quangns.timeforbed.R
import vn.quangns.timeforbed.core.Notifications

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val id = intent.getIntExtra(EXTRA_ID, -1)
        if (id < 0) return
        val snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
        startRinging(context, id)
        if (snooze) {
            AlarmMirror.clearSnooze(context, id)
            return
        }
        // Báo thức lặp: đặt lần kế; một lần: tắt đi. Dùng bản sao nên làm được cả khi máy chưa mở khóa.
        val def = AlarmMirror.get(context, id)
        if (def != null && def.daysMask and 0x7F != 0) {
            AlarmScheduler.schedule(context, def.toEntity())
        }
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!AlarmMirror.isUserUnlocked(app)) {
                    // Room chưa mở được: ghi nhớ để tắt báo thức một lần sau khi mở khóa.
                    if (def == null || def.daysMask and 0x7F == 0) AlarmMirror.markFiredOnce(app, id)
                    if (def != null && def.daysMask and 0x7F == 0) AlarmMirror.remove(app, id)
                    return@launch
                }
                val dao = AppDatabase.get(app).alarmDao()
                val alarm = dao.get(id) ?: return@launch
                if (alarm.daysMask and 0x7F == 0) {
                    dao.upsert(alarm.copy(enabled = false))
                    AlarmMirror.remove(app, id)
                } else if (def == null) {
                    AlarmScheduler.schedule(app, alarm)
                }
            } catch (e: Exception) {
                Log.w("AlarmReceiver", "Cập nhật báo thức sau khi reo lỗi", e)
            } finally {
                pending?.finish()
            }
        }
    }

    /**
     * Bật chuông. setAlarmClock cho phép khởi chạy foreground service; nếu hệ thống vẫn chặn
     * (vd Android 12 khi đã tắt quyền báo thức chính xác) thì hiện thông báo toàn màn hình —
     * màn hình báo thức sẽ tự bật chuông khi mở lên.
     */
    private fun startRinging(context: Context, id: Int) {
        val ring = Intent(context, AlarmRingService::class.java).setAction(AlarmRingService.ACTION_RING).putExtra(EXTRA_ID, id)
        try {
            ContextCompat.startForegroundService(context, ring)
        } catch (e: Exception) {
            Log.w("AlarmReceiver", "Không khởi chạy được dịch vụ chuông, dùng thông báo toàn màn hình", e)
            val open = PendingIntent.getActivity(
                context, id,
                Intent(context, AlarmRingActivity::class.java).putExtra(EXTRA_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, Notifications.CH_ALARM)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle("Báo thức")
                .setContentText("Chạm để mở báo thức")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(open, true)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { context.getSystemService(NotificationManager::class.java).notify(Notifications.ID_ALARM_RING, n) }
        }
    }

    companion object {
        const val ACTION_FIRE = "vn.quangns.timeforbed.ALARM_FIRE"
        const val EXTRA_ID = "alarm_id"
        const val EXTRA_SNOOZE = "snooze"
    }
}
