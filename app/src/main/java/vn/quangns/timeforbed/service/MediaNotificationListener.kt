package vn.quangns.timeforbed.service

import android.service.notification.NotificationListenerService

/**
 * Không xử lý thông báo nào. Chỉ cần được bật để hệ thống cho phép
 * [android.media.session.MediaSessionManager.getActiveSessions] trả về phiên phát của app khác,
 * đồng thời giúp tiến trình app được giữ sống ở nền.
 */
class MediaNotificationListener : NotificationListenerService()
