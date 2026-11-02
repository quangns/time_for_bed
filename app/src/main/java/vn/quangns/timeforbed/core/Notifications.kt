package vn.quangns.timeforbed.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Notifications {
    const val CH_TIMER = "timer"
    const val CH_WARNING = "warning"
    const val CH_ALARM = "alarm"

    const val ID_TIMER = 1001
    const val ID_TIMER_WARNING = 1002
    const val ID_NIGHT_WARNING = 1003
    const val ID_ALARM_RING = 1004

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_TIMER, "Hẹn giờ ngủ", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Đếm ngược thời gian còn lại trước khi tắt media"
                    setShowBadge(false)
                },
                NotificationChannel(CH_WARNING, "Cảnh báo sắp tắt", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Báo trước 1 phút khi sắp tắt video/nhạc"
                },
                NotificationChannel(CH_ALARM, "Báo thức", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Chuông báo thức"
                    setSound(null, null) // chuông do AlarmRingService tự phát
                    setBypassDnd(true)
                    setShowBadge(false)
                },
            )
        )
    }
}
