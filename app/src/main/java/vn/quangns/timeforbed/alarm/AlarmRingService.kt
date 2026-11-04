package vn.quangns.timeforbed.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import android.os.SystemClock
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
import vn.quangns.timeforbed.MainActivity
import vn.quangns.timeforbed.R
import vn.quangns.timeforbed.core.Notifications
import vn.quangns.timeforbed.service.BedtimeAccessibilityService

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
                    } ?: AlarmMirror.get(applicationContext, id)?.toEntity() ?: AlarmEntity(id = id, hour = LocalTime.now().hour, minute = LocalTime.now().minute)
                    ring(alarm)
                }
            }
            ACTION_SNOOZE -> {
                current?.let { AlarmScheduler.scheduleSnooze(this, it) }
                finish()
            }
            ACTION_DISMISS -> {
                val alarm = current
                // Chống ngủ lại: chưa dừng hẳn, chuyển sang kiểm tra đã cầm máy vận động đủ chưa.
                if (alarm != null && alarm.wakeCheck && wake == null) startWakeCheck(alarm) else finish()
            }
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
        stopWakeCheck()
        stopSound()
        handler.removeCallbacksAndMessages(null) // trước playSound để không xoá vòng bíp dự phòng
        current = alarm
        _ringing.value = alarm
        isRinging = true
        getSystemService(NotificationManager::class.java).notify(Notifications.ID_ALARM_RING, buildNotification(alarm))
        showRingScreen()
        playSound(alarm.ringtoneUri?.let(Uri::parse))
        if (alarm.vibrate) vibrate()
        handler.postDelayed({ finish() }, AUTO_STOP_MS)
    }

    /**
     * Mở thẳng màn hình báo thức, kể cả khi đang dùng máy ở màn hình chính.
     * Thông báo toàn màn hình chỉ tự bật màn hình báo thức khi máy đang tắt màn hình/khoá; lúc đang dùng máy
     * hệ thống chỉ hiện một thông báo nhỏ (dễ bị ẩn nếu chưa cấp quyền) nên người dùng phải mở app mới tắt được.
     * Android cho phép mở màn hình từ nền khi app có dịch vụ Trợ năng đang chạy hoặc có quyền
     * "Hiển thị trên ứng dụng khác"; thiếu cả hai thì vẫn còn thông báo toàn màn hình như trước.
     */
    private fun showRingScreen() {
        val intent = Intent(this, AlarmRingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        val launcher: Context = BedtimeAccessibilityService.instance ?: this
        runCatching { launcher.startActivity(intent) }.onFailure { Log.w(TAG, "Không mở được màn hình báo thức", it) }
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
        stopWakeCheck()
        stopSound()
        current = null
        _ringing.value = null
        isRinging = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopWakeCheck()
        stopSound()
        _ringing.value = null
        isRinging = false
        scope.cancel()
        super.onDestroy()
    }

    // --- Chống ngủ lại ---

    private class WakeSession(val alarm: AlarmEntity, val meter: WakeMeter, val deadline: Long, val listener: SensorEventListener)

    private var wake: WakeSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    /** Số lần đã reo lại vì chưa vận động đủ (trong lần báo thức này). */
    private var ringsAgain = 0

    private val wakeTick = object : Runnable {
        override fun run() {
            val w = wake ?: return
            val now = SystemClock.uptimeMillis()
            val required = w.alarm.wakeMoveSeconds
            val active = w.meter.activeCount
            when {
                WakeCheckRules.passed(active, required) -> {
                    showWakeDone()
                    finish()
                }
                now >= w.deadline -> {
                    if (WakeCheckRules.shouldRingAgain(active, required, ringsAgain)) {
                        ringsAgain++
                        ring(w.alarm) // chưa dậy hẳn: reo lại, tắt lần nữa sẽ kiểm tra lại từ đầu
                    } else {
                        finish()
                    }
                }
                else -> {
                    val state = WakeCheckState(w.alarm, active, required, w.deadline - now, ringsAgain + 1)
                    _wakeCheck.value = state
                    runCatching {
                        getSystemService(NotificationManager::class.java).notify(Notifications.ID_ALARM_RING, buildWakeNotification(state))
                    }
                    handler.postDelayed(this, 1_000)
                }
            }
        }
    }

    private fun startWakeCheck(alarm: AlarmEntity) {
        val sm = getSystemService(SensorManager::class.java)
        val accel = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accel == null) { // máy không có cảm biến gia tốc: không kiểm tra được
            finish()
            return
        }
        handler.removeCallbacksAndMessages(null) // huỷ hẹn tự tắt chuông sau 10 phút
        stopSound()
        _ringing.value = null // đóng màn hình báo thức
        isRinging = false
        val meter = WakeMeter()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) =
                meter.onSample(SystemClock.uptimeMillis(), e.values[0], e.values[1], e.values[2])
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val windowMs = alarm.wakeCheckMinutes * 60_000L
        sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_NORMAL, handler)
        // Cảm biến không gửi số liệu khi CPU ngủ → giữ CPU thức trong lúc kiểm tra (tối đa ~10 phút).
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "timeforbed:wakecheck")
            .apply { acquire(windowMs + 60_000L) }
        wake = WakeSession(alarm, meter, SystemClock.uptimeMillis() + windowMs, listener)
        wakeTick.run()
    }

    private fun stopWakeCheck() {
        handler.removeCallbacks(wakeTick)
        wake?.let { getSystemService(SensorManager::class.java)?.unregisterListener(it.listener) }
        wake = null
        _wakeCheck.value = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun buildWakeNotification(s: WakeCheckState): Notification {
        val open = PendingIntent.getActivity(
            this, Notifications.ID_ALARM_RING,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, 2),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val left = s.remainingMs / 1_000
        return NotificationCompat.Builder(this, Notifications.CH_ALARM)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Chống ngủ lại: cầm máy đi lại")
            .setContentText("Đã vận động ${s.activeSeconds}/${s.requiredSeconds} giây · còn %d:%02d".format(left / 60, left % 60))
            .setProgress(s.requiredSeconds, s.activeSeconds.coerceAtMost(s.requiredSeconds), false)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    private fun showWakeDone() {
        val n = NotificationCompat.Builder(this, Notifications.CH_TIMER)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Bạn đã dậy rồi")
            .setContentText("Đã vận động đủ, chúc một ngày tốt lành!")
            .setTimeoutAfter(30_000)
            .setAutoCancel(true)
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(Notifications.ID_WAKE_DONE, n) }
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

        private val _wakeCheck = MutableStateFlow<WakeCheckState?>(null)
        /** Đang kiểm tra chống ngủ lại (null = không). */
        val wakeCheck: StateFlow<WakeCheckState?> = _wakeCheck.asStateFlow()

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
