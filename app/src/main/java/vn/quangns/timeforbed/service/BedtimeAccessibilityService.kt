package vn.quangns.timeforbed.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
 * Theo dõi thời điểm người dùng thao tác lần cuối và kiểm tra chế độ ban đêm.
 * Không đọc nội dung màn hình (canRetrieveWindowContent=false).
 *
 * Tiết kiệm pin: không kiểm tra định kỳ. Mỗi lần kiểm tra xong tính sẵn lần kế tiếp
 * ([NightRules.nextCheckDelayMs]) — ngoài khung giờ thì ngủ tới giờ bắt đầu; màn hình tắt và im lặng
 * thì ngừng hẳn, chỉ thức dậy khi bật màn hình, mở khóa, có âm thanh phát hoặc đổi cài đặt.
 */
class BedtimeAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var foregroundPackage: String? = null
    private var warningShown = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val tick = Runnable {
        checkNight()
        scheduleNext()
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                markInteraction()
                MediaVolume.restore(context) // dọn âm lượng bản 1.2.2 từng tắt (nếu có)
            }
            scheduleNext()
        }
    }

    /** Có âm thanh bắt đầu/dừng phát (kể cả khi màn hình tắt) → tính lại lịch kiểm tra. */
    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) = scheduleNext()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        markInteraction()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        getSystemService(AudioManager::class.java)?.registerAudioPlaybackCallback(playbackCallback, handler)
        // Đổi khung giờ / ngưỡng / bật tắt → tính lại lịch ngay.
        scope.launch {
            Prefs.get(this@BedtimeAccessibilityService).state
                .map { Triple(it.nightEnabled, it.nightStart to it.nightEnd, it.idleMinutes) }
                .distinctUntilChanged()
                .collect { scheduleNext() }
        }
    }

    /** Hẹn lần kiểm tra kế tiếp (hoặc không hẹn nếu chỉ cần chờ sự kiện). */
    private fun scheduleNext() {
        handler.removeCallbacks(tick)
        val prefs = Prefs.get(this)
        val now = System.currentTimeMillis()
        val time = LocalTime.now()
        val screenOn = getSystemService(PowerManager::class.java).isInteractive
        val delay = NightRules.nextCheckDelayMs(
            enabled = prefs.nightEnabled,
            minuteOfDay = time.hour * 60 + time.minute,
            msIntoMinute = time.second * 1_000L + time.nano / 1_000_000,
            start = prefs.nightStart,
            end = prefs.nightEnd,
            idleMs = now - _lastInteraction.value,
            thresholdMs = prefs.idleMinutes * 60_000L,
            screenOn = screenOn,
            // Chỉ hỏi hệ thống media khi màn hình tắt (lúc đó mới ảnh hưởng tới quyết định).
            mediaPlaying = !screenOn && MediaKiller.isOtherMediaPlaying(this),
        )
        if (delay != null) handler.postDelayed(tick, delay)
        nextCheckAt = if (delay != null) now + delay else 0L
        // Handler dùng đồng hồ uptime, đứng yên khi CPU ngủ sâu (màn hình tắt, nhạc phát kiểu offload).
        // Lúc màn hình tắt thì hẹn thêm bằng AlarmManager để chắc chắn được đánh thức đúng giờ.
        if (delay != null && !screenOn) NightCheckReceiver.schedule(this, now + delay) else NightCheckReceiver.cancel(this)
    }

    /** Được [NightCheckReceiver] gọi khi AlarmManager đánh thức lúc màn hình tắt. */
    fun runScheduledCheck() = tick.run()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString()
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Thông báo nổi/heads-up của SystemUI tự xuất hiện, không phải thao tác người dùng.
                // Cùng app tự mở màn mới (quảng cáo, tự phát video kế) cũng không tính — chỉ khi đổi app.
                if (pkg != null && pkg != SYSTEM_UI) {
                    if (pkg != foregroundPackage) markInteraction()
                    foregroundPackage = pkg
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
        NightCheckReceiver.cancel(this)
        scope.cancel()
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { getSystemService(AudioManager::class.java)?.unregisterAudioPlaybackCallback(playbackCallback) }
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
                    lockScreen = true,
                )
                // Bắt đầu đếm lại để không tắt liên tục nếu người dùng mở lại.
                _lastInteraction.value = now
            }
        }
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
        /** Thời điểm hẹn kiểm tra kế tiếp (0 = đang chờ sự kiện) — chỉ để hiển thị/gỡ lỗi. */
        @Volatile
        var nextCheckAt: Long = 0L
            private set
        private const val SYSTEM_UI = "com.android.systemui"

        @Volatile
        var instance: BedtimeAccessibilityService? = null
            private set

        private val _lastInteraction = MutableStateFlow(System.currentTimeMillis())
        val lastInteraction: StateFlow<Long> = _lastInteraction.asStateFlow()
    }
}
