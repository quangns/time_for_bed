package vn.quangns.timeforbed.core

import android.content.ComponentName
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import vn.quangns.timeforbed.alarm.AlarmRingService
import vn.quangns.timeforbed.service.BedtimeAccessibilityService
import vn.quangns.timeforbed.service.MediaNotificationListener

/**
 * Tạm dừng mọi trình phát video/âm thanh của app khác (trừ danh sách loại trừ) rồi khóa màn hình —
 * không cần root, **không đụng tới âm lượng** (để không ảnh hưởng chuông báo thức).
 * 1. pause qua MediaSession (cần quyền Truy cập thông báo) — YouTube, Spotify, Zing MP3…;
 * 2. phím media PAUSE tới phiên phát gần nhất + giành audio focus;
 * 3. về màn hình chính rồi khóa máy qua Accessibility: app video không báo trình phát (TikTok…)
 *    tự dừng khi bị đẩy xuống nền; khóa màn hình để tiết kiệm pin;
 * 4. kiểm tra lại sau 1,5 s và 4 s: còn phát thì pause lần nữa, chưa khóa được thì thử lại.
 * Kết quả ghi vào [Prefs.lastStopReport] để xem lại trong app.
 */
object MediaKiller {
    private const val TAG = "MediaKiller"
    private val main = Handler(Looper.getMainLooper())
    private val RETRY_DELAYS_MS = longArrayOf(1_500, 4_000)

    private val PLAYING_STATES = setOf(
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING,
        PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING,
        PlaybackState.STATE_SKIPPING_TO_NEXT,
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
    )

    private fun MediaController.isPlaying() = playbackState?.state in PLAYING_STATES

    /** null = chưa có quyền Truy cập thông báo (khác với "có quyền nhưng không có phiên nào"). */
    fun activeControllersOrNull(context: Context): List<MediaController>? {
        val msm = context.getSystemService(MediaSessionManager::class.java) ?: return emptyList()
        return try {
            msm.getActiveSessions(ComponentName(context, MediaNotificationListener::class.java))
        } catch (e: SecurityException) {
            Log.w(TAG, "Chưa cấp quyền Truy cập thông báo", e)
            null
        }
    }

    fun activeControllers(context: Context): List<MediaController> = activeControllersOrNull(context).orEmpty()

    /** Có báo thức (của app này hoặc app khác) đang reo không. */
    fun isAlarmRinging(context: Context): Boolean {
        if (AlarmRingService.isRinging) return true
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        return am.activePlaybackConfigurations.any {
            it.audioAttributes.usage == AudioAttributes.USAGE_ALARM
        }
    }

    /** Có app không thuộc danh sách loại trừ đang phát media không. */
    fun isOtherMediaPlaying(context: Context): Boolean {
        val playing = activeControllers(context).filter { it.isPlaying() }
        if (playing.any { !Exclusions.isExcluded(context, it.packageName) }) return true
        if (playing.isNotEmpty()) return false
        // App không dùng MediaSession (trình duyệt, game…) nhưng vẫn đang phát nhạc.
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        return am.isMusicActive && !isAlarmRinging(context)
    }

    /** Mô tả các phiên phát hiện có, để hiển thị khi người dùng bấm "Kiểm tra". */
    fun describeSessions(context: Context): String {
        val controllers = activeControllersOrNull(context)
            ?: return "Chưa có quyền Truy cập thông báo nên không thấy trình phát nào."
        val am = context.getSystemService(AudioManager::class.java)
        val lines = controllers.map { c ->
            val state = stateName(c.playbackState?.state)
            val tag = if (Exclusions.isExcluded(context, c.packageName)) " (không bao giờ tắt)" else ""
            "• ${appLabel(context, c.packageName)}: $state$tag"
        }
        val music = if (am?.isMusicActive == true) "Có" else "Không"
        return buildString {
            append(if (lines.isEmpty()) "Không thấy phiên phát nào." else lines.joinToString("\n"))
            append("\nĐang có âm thanh phát: ").append(music)
            append("\nTrợ năng (về Home, khóa máy): ")
            append(if (BedtimeAccessibilityService.instance != null) "đang chạy" else "chưa kết nối")
        }
    }

    /**
     * Dừng media. [lockScreen] = về Home + khóa máy sau khi dừng.
     * [onDone] được gọi sau lần kiểm tra cuối (~4 s) — receiver dùng để giữ tiến trình sống tới lúc đó.
     * Trả về danh sách gói đã bị pause qua MediaSession ở lượt đầu.
     */
    fun stopAll(context: Context, reason: String, lockScreen: Boolean, onDone: () -> Unit = {}): List<String> {
        val app = context.applicationContext
        val prefs = Prefs.get(app)
        val am = app.getSystemService(AudioManager::class.java)
        val ringing = isAlarmRinging(app)

        val controllers = activeControllersOrNull(app)
        val playing = controllers.orEmpty().filter { it.isPlaying() }
        val targets = playing.filter { !Exclusions.isExcluded(app, it.packageName) }
        val excludedPlaying = playing.any { Exclusions.isExcluded(app, it.packageName) }
        val musicBefore = am?.isMusicActive == true

        targets.forEach { pause(it) }

        // Phím PAUSE đi tới phiên phát gần nhất; bỏ qua nếu app loại trừ đang phát hoặc báo thức đang reo
        // để không lỡ dừng nhầm chúng.
        val sendKey = !ringing && !excludedPlaying && (targets.isNotEmpty() || musicBefore)
        if (sendKey && am != null) sendPauseKey(am)

        // Giành focus làm app đang giữ focus mất hẳn quyền phát → bỏ qua nếu app loại trừ đang phát
        // (vd app tiếng ồn trắng được cho phép chạy cả đêm) hoặc không có gì đang phát.
        val focus = if (!ringing && !excludedPlaying && am != null && (targets.isNotEmpty() || musicBefore)) {
            grabAudioFocus(am)
        } else null

        // Về màn hình chính + khóa máy sau một nhịp để lệnh pause kịp tới các app.
        var locked = false
        if (lockScreen) {
            main.postDelayed({ locked = BedtimeAccessibilityService.instance?.goHomeAndLock() == true }, 500)
        }

        // Kiểm tra lại: app nào còn phát thì pause lần nữa; chưa khóa được (Trợ năng vừa kết nối lại) thì thử lại.
        RETRY_DELAYS_MS.forEachIndexed { i, delay ->
            main.postDelayed({
                val still = activeControllers(app).filter { it.isPlaying() && !Exclusions.isExcluded(app, it.packageName) }
                still.forEach { pause(it); runCatching { it.transportControls.stop() } }
                if (still.isNotEmpty() && !isAlarmRinging(app) && am != null) sendPauseKey(am)
                if (lockScreen && !locked) locked = BedtimeAccessibilityService.instance?.goHomeAndLock() == true
                if (i == RETRY_DELAYS_MS.lastIndex) {
                    focus?.let { req -> runCatching { am?.abandonAudioFocusRequest(req) } }
                    val audible = !isAlarmRinging(app) && (still.isNotEmpty() || am?.isMusicActive == true)
                    prefs.recordStopReport(
                        buildReport(app, controllers, targets, sendKey, lockScreen, locked, still, audible),
                    )
                    onDone()
                }
            }, delay)
        }

        val pkgs = targets.map { it.packageName }.distinct()
        prefs.recordAction(reason)
        Log.i(TAG, "stopAll($reason): sessions=${controllers?.size}, paused=$pkgs, key=$sendKey, lock=$lockScreen")
        return pkgs
    }

    private fun pause(c: MediaController) {
        runCatching { c.transportControls.pause() }.onFailure { Log.w(TAG, "pause ${c.packageName} lỗi", it) }
    }

    private fun sendPauseKey(am: AudioManager) {
        val t = SystemClock.uptimeMillis()
        runCatching {
            am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0))
            am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0))
        }.onFailure { Log.w(TAG, "Gửi phím PAUSE lỗi", it) }
    }

    /** Xin audio focus vĩnh viễn (nhả ra sau lần kiểm tra cuối): app tuân thủ focus sẽ dừng hẳn. */
    private fun grabAudioFocus(am: AudioManager): AudioFocusRequest? {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener { }
            .build()
        val granted = runCatching { am.requestAudioFocus(request) }.getOrNull() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return if (granted) request else null
    }

    private fun buildReport(
        context: Context,
        controllers: List<MediaController>?,
        targets: List<MediaController>,
        sentKey: Boolean,
        lockScreen: Boolean,
        locked: Boolean,
        stillPlaying: List<MediaController>,
        audible: Boolean,
    ): String = buildString {
        if (controllers == null) {
            append("Chưa có quyền Truy cập thông báo, không thấy được trình phát.\n")
        } else {
            append("Thấy ${controllers.size} phiên phát")
            if (targets.isNotEmpty()) append(", đã tạm dừng: ").append(targets.joinToString { appLabel(context, it.packageName) })
            append(".\n")
        }
        if (sentKey) append("Đã gửi phím Tạm dừng.\n")
        if (lockScreen) {
            append(
                when {
                    locked -> "Đã về màn hình chính và khóa máy.\n"
                    BedtimeAccessibilityService.instance == null -> "Trợ năng chưa kết nối nên chưa khóa được máy.\n"
                    else -> "Đang mở app không bao giờ tắt nên không khóa máy.\n"
                },
            )
        }
        when {
            stillPlaying.isNotEmpty() ->
                append("Vẫn còn phát: ").append(stillPlaying.joinToString { appLabel(context, it.packageName) })
            audible ->
                append("Vẫn còn âm thanh từ một app chạy nền — tắt \"phát nền\" trong app đó.")
            else -> append("Kết quả: đã im lặng.")
        }
    }

    private fun appLabel(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun stateName(state: Int?): String = when (state) {
        PlaybackState.STATE_PLAYING -> "đang phát"
        PlaybackState.STATE_PAUSED -> "tạm dừng"
        PlaybackState.STATE_STOPPED -> "đã dừng"
        PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> "đang tải"
        null -> "không rõ"
        else -> "trạng thái $state"
    }
}
