package vn.quangns.timeforbed.core

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import androidx.core.app.NotificationCompat
import vn.quangns.timeforbed.R
import vn.quangns.timeforbed.service.TimerReceiver

/**
 * Chốt chặn cuối cho app phớt lờ mọi lệnh dừng (TikTok, video trong trình duyệt…):
 * tắt tiếng luồng media của hệ thống. Chuông báo thức dùng luồng ALARM nên không bị ảnh hưởng.
 * Âm lượng cũ được lưu lại và trả về khi mở khóa máy hoặc bấm "Bật lại âm thanh" trên thông báo.
 */
object MediaVolume {
    private const val TAG = "MediaVolume"

    /** Tắt tiếng media. Trả về true nếu đã tắt (trước đó đang có âm lượng). */
    fun mute(context: Context): Boolean {
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (current == 0) return false
        val prefs = Prefs.get(context)
        // Đã tắt từ lần trước mà chưa trả lại thì giữ mức cũ nhất.
        if (prefs.mutedPrevVolume <= 0) prefs.mutedPrevVolume = current
        val ok = runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) }
            .onFailure { Log.w(TAG, "Không tắt được tiếng media", it) }.isSuccess
        if (ok) showMutedNotification(context)
        return ok
    }

    /** Trả lại âm lượng đã lưu (nếu người dùng chưa tự chỉnh lại). */
    fun restore(context: Context): Boolean {
        val prefs = Prefs.get(context)
        val prev = prefs.mutedPrevVolume
        if (prev <= 0) return false
        prefs.mutedPrevVolume = 0
        context.getSystemService(NotificationManager::class.java).cancel(Notifications.ID_MUTED)
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        if (am.getStreamVolume(AudioManager.STREAM_MUSIC) != 0) return false
        return runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, prev, 0) }.isSuccess
    }

    private fun showMutedNotification(context: Context) {
        val restore = PendingIntent.getBroadcast(
            context, TimerReceiver.ACTION_RESTORE_VOLUME.hashCode(),
            Intent(context, TimerReceiver::class.java).setAction(TimerReceiver.ACTION_RESTORE_VOLUME),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, Notifications.CH_TIMER)
            .setSmallIcon(R.drawable.ic_moon)
            .setContentTitle("Đã tắt tiếng video, nhạc")
            .setContentText("Âm lượng tự bật lại khi bạn mở khóa máy.")
            .setOnlyAlertOnce(true)
            .addAction(0, "Bật lại âm thanh", restore)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(Notifications.ID_MUTED, n) }
    }
}
