package vn.quangns.timeforbed.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import vn.quangns.timeforbed.R
import vn.quangns.timeforbed.core.Notifications

/** Phát chuông báo thức (luồng ALARM), rung, và hiện màn hình báo thức toàn màn hình. */
class AlarmRingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var current: AlarmEntity? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> {
                val id = intent.getIntExtra(AlarmReceiver.EXTRA_ID, -1)
                startInForeground(null)
                scope.launch {
                    // Máy chưa mở khóa thì Room chưa mở được → dùng bản sao, không có thì vẫn reo với mặc định.
                    val alarm = withContext(Dispatchers.IO) {
                        runCatching { AppDatabase.get(applicationContext).alarmDao().get(id) }.getOrNull()
                    } ?: AlarmMirror.get(applicationContext, id)?.let {
                        AlarmEntity(id = id, hour = it.hour, minute = it.minute, daysMask = it.daysMask, snoozeMinutes = it.snoozeMinutes)
                    } ?: AlarmEntity(id = id, hour = LocalTime.now().hour, minute = LocalTime.now().minute)
                    ring(alarm)
                }
            }
            ACTION_SNOOZE -> {
                current?.let { AlarmScheduler.scheduleSnooze(this, it) }
                finish()
            }
            ACTION_DISMISS -> finish()
            else -> if (current == null) finish()
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(alarm: AlarmEntity?) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else 0
        ServiceCompat.startForeground(this, Notifications.ID_ALARM_RING, buildNotification(alarm), type)
    }

    private fun ring(alarm: AlarmEntity) {
        stopSound()
        handler.removeCallbacksAndMessages(null) // trước playSound để không xoá vòng bíp dự phòng
        current = alarm
        _ringing.value = alarm
        isRinging = true
        getSystemService(NotificationManager::class.java).notify(Notifications.ID_ALARM_RING, buildNotification(alarm))
        playSound(alarm.ringtoneUri?.let(Uri::parse))
        if (alarm.vibrate) vibrate()
        handler.postDelayed({ finish() }, AUTO_STOP_MS)
    }

    private fun playSound(custom: Uri?) {
        val am = getSystemService(AudioManager::class.java)
        // Âm lượng báo thức đang tắt hẳn thì nâng lên mức vừa để không lỡ giờ.
        if (am.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM) / 2, 0)
        }
        val candidates = listOfNotNull(
            custom,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        for (uri in candidates) {
            try {
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                    setDataSource(applicationContext, uri)
                    isLooping = true
                    prepare()
                    start()
                }
                return
            } catch (e: Exception) {
                Log.w(TAG, "Không phát được chuông $uri", e)
                player?.release()
                player = null
            }
        }
        // Không mở được file chuông nào (vd máy chưa mở khóa nên chưa đọc được nhạc chuông): bíp bằng tiếng hệ thống.
        startFallbackBeep()
    }

    private val beepTick = object : Runnable {
        override fun run() {
            tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1_000)
            handler.postDelayed(this, 1_500)
        }
    }

    private fun startFallbackBeep() {
        tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull() ?: return
        handler.post(beepTick)
    }

    private fun vibrate() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator = v
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))
    }

    private fun stopSound() {
        handler.removeCallbacks(beepTick)
        tone?.release()
        tone = null
        player?.runCatching { stop() }
        player?.release()
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun finish() {
        handler.removeCallbacksAndMessages(null)
        stopSound()
        current = null
        _ringing.value = null
        isRinging = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopSound()
        _ringing.value = null
        isRinging = false
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(alarm: AlarmEntity?): Notification {
        val full = PendingIntent.getActivity(
            this, 0,
            Intent(this, AlarmRingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = alarm?.let { "Báo thức %02d:%02d".format(it.hour, it.minute) } ?: "Báo thức"
        return NotificationCompat.Builder(this, Notifications.CH_ALARM)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(alarm?.label?.ifBlank { null } ?: "Đến giờ dậy rồi!")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(0, "Báo lại", actionIntent(this, ACTION_SNOOZE))
            .addAction(0, "Tắt", actionIntent(this, ACTION_DISMISS))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "AlarmRing"
        private const val AUTO_STOP_MS = 10 * 60_000L

        const val ACTION_RING = "vn.quangns.timeforbed.ALARM_RING"
        const val ACTION_SNOOZE = "vn.quangns.timeforbed.ALARM_SNOOZE"
        const val ACTION_DISMISS = "vn.quangns.timeforbed.ALARM_DISMISS"

        @Volatile
        var isRinging = false
            private set

        private val _ringing = MutableStateFlow<AlarmEntity?>(null)
        val ringing: StateFlow<AlarmEntity?> = _ringing.asStateFlow()

        fun actionIntent(context: Context, action: String): PendingIntent =
            PendingIntent.getService(
                context, action.hashCode(),
                Intent(context, AlarmRingService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        fun send(context: Context, action: String) {
            context.startService(Intent(context, AlarmRingService::class.java).setAction(action))
        }
    }
}
