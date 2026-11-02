package vn.quangns.timeforbed.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.quangns.timeforbed.MainActivity
import vn.quangns.timeforbed.R
import vn.quangns.timeforbed.core.Exclusions
import vn.quangns.timeforbed.core.MediaKiller
import vn.quangns.timeforbed.core.MediaVolume
import vn.quangns.timeforbed.core.NightAction
import vn.quangns.timeforbed.core.NightRules
import vn.quangns.timeforbed.core.Notifications
import vn.quangns.timeforbed.core.Prefs
import java.time.LocalTime

/**
 * Theo dõi thời điểm người dùng thao tác lần cuối và kiểm tra chế độ ban đêm mỗi 30 giây.
 * Không đọc nội dung màn hình (canRetrieveWindowContent=false).
 */
class BedtimeAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var foregroundPackage: String? = null
    private var warningShown = false

    private val tick = object : Runnable {
        override fun run() {
            checkNight()
            handler.postDelayed(this, TICK_MS)
        }
    }

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                markInteraction()
                MediaVolume.restore(context) // đã thức dậy mở máy: trả lại âm lượng media
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        markInteraction()
        ContextCompat.registerReceiver(
            this, unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, TICK_MS)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString()
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Thông báo nổi/heads-up của SystemUI tự xuất hiện, không phải thao tác người dùng.
                if (pkg != null && pkg != SYSTEM_UI) {
                    foregroundPackage = pkg
                    markInteraction()
                }
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> markInteraction()
            else -> Unit
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        markInteraction()
        return false // không chặn phím
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(unlockReceiver) }
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun markInteraction() {
        _lastInteraction.value = System.currentTimeMillis()
        if (warningShown) cancelWarning()
    }

    private fun checkNight() {
        val prefs = Prefs.get(this)
        val now = System.currentTimeMillis()
        val time = LocalTime.now()
        val mediaPlaying = MediaKiller.isOtherMediaPlaying(this)
        val action = NightRules.decide(
            enabled = prefs.nightEnabled,
            minuteOfDay = time.hour * 60 + time.minute,
            start = prefs.nightStart,
            end = prefs.nightEnd,
            idleMs = now - _lastInteraction.value,
            thresholdMs = prefs.idleMinutes * 60_000L,
            screenOn = getSystemService(PowerManager::class.java).isInteractive,
            mediaPlaying = mediaPlaying,
            foregroundExcluded = Exclusions.isExcluded(this, foregroundPackage),
            alarmRinging = MediaKiller.isAlarmRinging(this),
        )
        when (action) {
            NightAction.NONE -> if (warningShown) cancelWarning()
            NightAction.WARN -> if (!warningShown) showWarning()
            NightAction.STOP -> {
                cancelWarning()
                MediaKiller.stopAll(
                    this,
                    reason = "Ban đêm: ${prefs.idleMinutes} phút không thao tác",
                    lockScreen = prefs.lockAfterStop,
                )
                // Bắt đầu đếm lại để không tắt liên tục nếu người dùng mở lại.
                _lastInteraction.value = now
            }
        }
    }

    /** App đang hiện trên màn hình có thuộc danh sách loại trừ không (null = chưa biết). */
    fun foregroundExcluded(): Boolean? = foregroundPackage?.let { Exclusions.isExcluded(this, it) }

    /** Về màn hình chính (app video bị đẩy xuống nền sẽ tự dừng), trừ khi đang mở app loại trừ. */
    fun goHome(): Boolean {
        if (Exclusions.isExcluded(this, foregroundPackage)) return false
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /** Về màn hình chính rồi khóa máy, trừ khi đang mở app loại trừ (ngân hàng…). */
    fun goHomeAndLock(): Boolean {
        if (Exclusions.isExcluded(this, foregroundPackage)) return false
        performGlobalAction(GLOBAL_ACTION_HOME)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) }, 800)
        }
        return true
    }

    private fun showWarning() {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(this, Notifications.CH_WARNING)
            .setSmallIcon(R.drawable.ic_moon)
            .setContentTitle("Sắp tắt video/nhạc")
            .setContentText("Không có thao tác. Chạm vào màn hình để tiếp tục xem.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(NightRules.WARN_BEFORE_MS + 30_000)
            .build()
        runCatching {
            getSystemService(NotificationManager::class.java).notify(Notifications.ID_NIGHT_WARNING, n)
        }
        warningShown = true
    }

    private fun cancelWarning() {
        getSystemService(NotificationManager::class.java).cancel(Notifications.ID_NIGHT_WARNING)
        warningShown = false
    }

    companion object {
        private const val TICK_MS = 30_000L
        private const val SYSTEM_UI = "com.android.systemui"

        @Volatile
        var instance: BedtimeAccessibilityService? = null
            private set

        private val _lastInteraction = MutableStateFlow(System.currentTimeMillis())
        val lastInteraction: StateFlow<Long> = _lastInteraction.asStateFlow()
    }
}
