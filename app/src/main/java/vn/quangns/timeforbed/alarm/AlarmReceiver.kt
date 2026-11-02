package vn.quangns.timeforbed.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val id = intent.getIntExtra(EXTRA_ID, -1)
        if (id < 0) return
        // Bật chuông ngay (được phép khởi chạy FGS vì do setAlarmClock kích hoạt).
        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmRingService::class.java)
                .setAction(AlarmRingService.ACTION_RING)
                .putExtra(EXTRA_ID, id),
        )
        val snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
        if (snooze) return
        // Báo thức lặp: đặt lần kế; một lần: tắt đi.
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.get(app).alarmDao()
                val alarm = dao.get(id) ?: return@launch
                if (alarm.daysMask and 0x7F == 0) dao.upsert(alarm.copy(enabled = false))
                else AlarmScheduler.schedule(app, alarm)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "vn.quangns.timeforbed.ALARM_FIRE"
        const val EXTRA_ID = "alarm_id"
        const val EXTRA_SNOOZE = "snooze"
    }
}
