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
import android.util.Log
import vn.quangns.timeforbed.alarm.AlarmRingService
import vn.quangns.timeforbed.service.BedtimeAccessibilityService
import vn.quangns.timeforbed.service.MediaNotificationListener

/**
 * Dừng mọi trình phát video/âm thanh của app khác (trừ danh sách loại trừ) mà không cần root:
 * 1. pause/stop qua MediaSession (cần quyền Notification access);
 * 2. giành audio focus để app không dùng MediaSession tự dừng;
 * 3. về màn hình chính + khóa máy qua Accessibility (nếu bật).
 */
object MediaKiller {
    private const val TAG = "MediaKiller"
    private val main = Handler(Looper.getMainLooper())

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

    fun activeControllers(context: Context): List<MediaController> {
        val msm = context.getSystemService(MediaSessionManager::class.java) ?: return emptyList()
        return try {
            msm.getActiveSessions(ComponentName(context, MediaNotificationListener::class.java))
        } catch (e: SecurityException) {
            Log.w(TAG, "Chưa cấp quyền Notification access", e)
            emptyList()
        }
    }

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

    /**
     * Dừng media. [lockScreen] = về Home + khóa máy sau khi dừng.
     * Trả về danh sách gói đã bị dừng qua MediaSession.
     */
    fun stopAll(context: Context, reason: String, lockScreen: Boolean): List<String> {
        val app = context.applicationContext
        val targets = activeControllers(app).filter {
            it.isPlaying() && !Exclusions.isExcluded(app, it.packageName)
        }
        targets.forEach { c ->
            runCatching { c.transportControls.pause() }
                .onFailure { Log.w(TAG, "pause ${c.packageName} lỗi", it) }
        }
        // Gọi stop sau một nhịp để app kịp xử lý pause (một số app bỏ qua stop khi đang phát).
        main.postDelayed({
            targets.forEach { c -> runCatching { c.transportControls.stop() } }
        }, 600)

        if (!isAlarmRinging(app)) grabAudioFocus(app)

        if (lockScreen) {
            main.postDelayed({ BedtimeAccessibilityService.instance?.goHomeAndLock() }, 300)
        }

        val pkgs = targets.map { it.packageName }.distinct()
        Prefs.get(app).recordAction(reason)
        Log.i(TAG, "stopAll($reason): $pkgs, lock=$lockScreen")
        return pkgs
    }

    /** Xin audio focus vĩnh viễn rồi nhả ra: app tuân thủ focus sẽ dừng hẳn. */
    private fun grabAudioFocus(context: Context) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener { }
            .build()
        runCatching { am.requestAudioFocus(request) }
        main.postDelayed({ runCatching { am.abandonAudioFocusRequest(request) } }, 2_000)
    }
}
