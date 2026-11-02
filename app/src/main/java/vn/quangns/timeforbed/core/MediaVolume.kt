package vn.quangns.timeforbed.core

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager

/**
 * Bản 1.2.2 từng tắt tiếng luồng media làm chốt chặn cuối; từ 1.2.3 app không đụng tới âm lượng nữa.
 * Chỉ còn việc trả lại âm lượng đã lưu (nếu máy cập nhật lên khi đang bị tắt tiếng).
 */
object MediaVolume {
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
}
