package vn.quangns.timeforbed.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import vn.quangns.timeforbed.MainActivity
import vn.quangns.timeforbed.core.TimeRules
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Đặt báo thức bằng setAlarmClock(): chính xác, vượt Doze, hiện biểu tượng báo thức trên thanh trạng thái. */
object AlarmScheduler {
    private const val SNOOZE_OFFSET = 100_000

    fun nextTriggerMillis(alarm: AlarmEntity, now: Long = System.currentTimeMillis()): Long {
        val zone = ZoneId.systemDefault()
        val next = TimeRules.nextAlarm(
            LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone), alarm.hour, alarm.minute, alarm.daysMask,
        )
        return next.atZone(zone).toInstant().toEpochMilli()
    }

    fun schedule(context: Context, alarm: AlarmEntity) {
        if (!alarm.enabled) {
            cancel(context, alarm.id)
            return
        }
        setAt(context, nextTriggerMillis(alarm), alarm.id, alarm.id, snooze = false)
    }

    fun scheduleSnooze(context: Context, alarm: AlarmEntity) {
        val at = System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L
        setAt(context, at, alarm.id + SNOOZE_OFFSET, alarm.id, snooze = true)
    }

    fun cancel(context: Context, alarmId: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(firePending(context, alarmId, alarmId, false))
        am.cancel(firePending(context, alarmId + SNOOZE_OFFSET, alarmId, true))
    }

    suspend fun rescheduleAll(context: Context) {
        AppDatabase.get(context).alarmDao().getAll().forEach { schedule(context, it) }
    }

    private fun setAt(context: Context, at: Long, requestCode: Int, alarmId: Int, snooze: Boolean) {
        val am = context.getSystemService(AlarmManager::class.java)
        val show = PendingIntent.getActivity(
            context, requestCode, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, 2),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val fire = firePending(context, requestCode, alarmId, snooze)
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), fire)
        } catch (e: SecurityException) {
            // Android 12 khi người dùng thu hồi quyền báo thức chính xác: vẫn đặt, chấp nhận lệch vài phút.
            Log.w("AlarmScheduler", "Không có quyền exact alarm", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire)
        }
    }

    private fun firePending(context: Context, requestCode: Int, alarmId: Int, snooze: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_FIRE)
                .putExtra(AlarmReceiver.EXTRA_ID, alarmId)
                .putExtra(AlarmReceiver.EXTRA_SNOOZE, snooze),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
