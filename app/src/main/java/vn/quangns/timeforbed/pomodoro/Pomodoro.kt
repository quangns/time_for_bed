package vn.quangns.timeforbed.pomodoro

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.quangns.timeforbed.MainActivity
import vn.quangns.timeforbed.R
import vn.quangns.timeforbed.core.Notifications
import vn.quangns.timeforbed.core.Permissions
import java.time.LocalDate

data class PomodoroState(
    val phase: Phase,
    val running: Boolean,
    /** Thời điểm hết giai đoạn (khi đang chạy). */
    val endAt: Long,
    /** Thời gian còn lại khi đang dừng/tạm dừng. */
    val remainingMs: Long,
    val doneInCycle: Int,
    val todayCount: Int,
    /** Ngày (yyyy-MM-dd) của [todayCount] — để giao diện tự về 0 khi qua nửa đêm. */
    val todayDate: String,
    val settings: PomodoroSettings,
    /** Tổng thời lượng của giai đoạn HIỆN TẠI, chốt lúc vào giai đoạn (đổi cài đặt giữa chừng không ảnh hưởng). */
    val totalMs: Long,
) {
    fun remainingAt(now: Long): Long = if (running) (endAt - now).coerceAtLeast(0) else remainingMs

    fun todayCountOn(date: String): Int = if (todayDate == date) todayCount else 0
    /** Chưa chạy lần nào trong giai đoạn này (để hiện nút "Bắt đầu" thay vì "Tiếp tục"). */
    val fresh: Boolean get() = !running && remainingMs >= totalMs
}

/**
 * Điều khiển Pomodoro. Tiết kiệm pin: không có service chạy nền — hết giai đoạn do AlarmManager
 * đánh thức [PomodoroReceiver]; thông báo đếm ngược dùng chronometer của hệ thống.
 * Báo hết giai đoạn bằng âm thanh thông báo, không đụng tới âm lượng hay chuông báo thức.
 */
object Pomodoro {
    private const val TAG = "Pomodoro"
    const val ACTION_END = "vn.quangns.timeforbed.POMO_END"
    const val ACTION_PAUSE = "vn.quangns.timeforbed.POMO_PAUSE"
    const val ACTION_RESUME = "vn.quangns.timeforbed.POMO_RESUME"
    const val ACTION_SKIP = "vn.quangns.timeforbed.POMO_SKIP"

    private const val K_PHASE = "phase"
    private const val K_RUNNING = "running"
    private const val K_END_AT = "end_at"
    private const val K_REMAINING = "remaining"
    private const val K_DONE = "done_in_cycle"
    private const val K_TODAY = "today_count"
    private const val K_TODAY_DATE = "today_date"
    private const val K_FOCUS = "focus_min"
    private const val K_SHORT = "short_min"
    private const val K_LONG = "long_min"
    private const val K_LONG_EVERY = "long_every"
    private const val K_AUTO = "auto_start"
    private const val K_PHASE_TOTAL = "phase_total"

    private var sp: SharedPreferences? = null
    private val _state = MutableStateFlow<PomodoroState?>(null)

    private fun prefs(context: Context): SharedPreferences =
        sp ?: context.applicationContext.getSharedPreferences("pomodoro", Context.MODE_PRIVATE).also {
            sp = it
            _state.value = read(it)
        }

    fun state(context: Context): StateFlow<PomodoroState?> {
        prefs(context)
        return _state.asStateFlow()
    }

    fun current(context: Context): PomodoroState = read(prefs(context))

    @VisibleForTesting
    fun resetForTest() {
        sp = null
        _state.value = null
    }

    private fun read(p: SharedPreferences): PomodoroState {
        val settings = PomodoroSettings(
            focusMin = p.getInt(K_FOCUS, 25),
            shortMin = p.getInt(K_SHORT, 5),
            longMin = p.getInt(K_LONG, 15),
            longEvery = p.getInt(K_LONG_EVERY, 4),
            autoStart = p.getBoolean(K_AUTO, true),
        )
        val phase = runCatching { Phase.valueOf(p.getString(K_PHASE, null) ?: "FOCUS") }.getOrDefault(Phase.FOCUS)
        val today = LocalDate.now().toString()
        val total = p.getLong(K_PHASE_TOTAL, PomodoroRules.durationMs(phase, settings))
        return PomodoroState(
            phase = phase,
            running = p.getBoolean(K_RUNNING, false),
            endAt = p.getLong(K_END_AT, 0L),
            remainingMs = p.getLong(K_REMAINING, total).coerceAtMost(total),
            doneInCycle = p.getInt(K_DONE, 0),
            todayCount = if (p.getString(K_TODAY_DATE, "") == today) p.getInt(K_TODAY, 0) else 0,
            todayDate = today,
            settings = settings,
            totalMs = total,
        )
    }

    private fun write(context: Context, block: SharedPreferences.Editor.() -> Unit) {
        val p = prefs(context)
        p.edit(commit = true, action = block)
        _state.value = read(p)
    }

    // --- Điều khiển ---

    fun start(context: Context, now: Long = System.currentTimeMillis()) {
        val s = current(context)
        if (s.running) return
        val endAt = now + s.remainingMs.coerceAtLeast(1_000)
        write(context) {
            putBoolean(K_RUNNING, true)
            putLong(K_END_AT, endAt)
        }
        scheduleEnd(context, endAt)
        showOngoing(context)
    }

    fun pause(context: Context, now: Long = System.currentTimeMillis()) {
        val s = current(context)
        if (!s.running) return
        write(context) {
            putBoolean(K_RUNNING, false)
            putLong(K_REMAINING, (s.endAt - now).coerceAtLeast(0))
        }
        cancelEnd(context)
        showOngoing(context)
    }

    /** Sang giai đoạn kế; bỏ qua giữa phiên tập trung thì không tính là đã xong. */
    fun skip(context: Context, now: Long = System.currentTimeMillis()) = advance(context, now, counted = false, alert = false)

    fun reset(context: Context) {
        val s = current(context)
        write(context) {
            putString(K_PHASE, Phase.FOCUS.name)
            putBoolean(K_RUNNING, false)
            val focusMs = PomodoroRules.durationMs(Phase.FOCUS, s.settings)
            putLong(K_REMAINING, focusMs)
            putLong(K_PHASE_TOTAL, focusMs)
            putInt(K_DONE, 0)
        }
        cancelEnd(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(Notifications.ID_POMODORO)
        nm.cancel(Notifications.ID_POMODORO_ALERT)
    }

    fun updateSettings(context: Context, settings: PomodoroSettings) {
        val old = current(context)
        write(context) {
            putInt(K_FOCUS, settings.focusMin)
            putInt(K_SHORT, settings.shortMin)
            putInt(K_LONG, settings.longMin)
            putInt(K_LONG_EVERY, settings.longEvery)
            putBoolean(K_AUTO, settings.autoStart)
            // Chưa bắt đầu giai đoạn hiện tại thì áp dụng thời lượng mới ngay; đang chạy thì từ giai đoạn sau.
            if (old.fresh) {
                val ms = PomodoroRules.durationMs(old.phase, settings)
                putLong(K_REMAINING, ms)
                putLong(K_PHASE_TOTAL, ms)
            }
        }
    }

    /** Gọi từ [PomodoroReceiver] khi AlarmManager báo hết giai đoạn. */
    fun onPhaseEnd(context: Context, now: Long = System.currentTimeMillis()) {
        val s = current(context)
        if (!s.running) return
        if (now < s.endAt - 2_000) { // báo sớm bất thường: hẹn lại đúng giờ
            scheduleEnd(context, s.endAt)
            return
        }
        advance(context, now, counted = true, alert = true)
    }

    /** Sau khởi động lại máy / đổi giờ: hẹn lại hoặc xử lý giai đoạn đã hết trong lúc tắt máy. */
    fun restore(context: Context, now: Long = System.currentTimeMillis()) {
        val s = current(context)
        if (!s.running) return
        if (s.endAt > now) {
            scheduleEnd(context, s.endAt)
            showOngoing(context)
        } else {
            advance(context, now, counted = true, alert = false)
        }
    }

    private fun advance(context: Context, now: Long, counted: Boolean, alert: Boolean) {
        val s = current(context)
        val (nextPhase, done) = PomodoroRules.next(s.phase, s.doneInCycle, s.settings.longEvery, counted)
        val finishedFocus = counted && s.phase == Phase.FOCUS
        val today = LocalDate.now().toString()
        // Hết giờ: tự chạy tiếp nếu bật "Tự chuyển giai đoạn". Bỏ qua: giữ nguyên trạng thái chạy/tạm dừng.
        val keepRunning = if (counted) s.settings.autoStart else s.running
        val nextMs = PomodoroRules.durationMs(nextPhase, s.settings)
        write(context) {
            putString(K_PHASE, nextPhase.name)
            putInt(K_DONE, done)
            putLong(K_REMAINING, nextMs)
            putLong(K_PHASE_TOTAL, nextMs)
            putBoolean(K_RUNNING, false)
            if (finishedFocus) {
                putString(K_TODAY_DATE, today)
                putInt(K_TODAY, s.todayCount + 1)
            }
        }
        cancelEnd(context)
        if (alert) alertPhaseEnd(context, s.phase, nextPhase, s.settings)
        if (keepRunning) start(context, now) else showOngoing(context)
    }

    // --- AlarmManager ---

    private fun endIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, ACTION_END.hashCode(),
        Intent(context, PomodoroReceiver::class.java).setAction(ACTION_END),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun scheduleEnd(context: Context, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = endIntent(context)
        try {
            if (Permissions.canScheduleExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            Log.w(TAG, "Không có quyền báo thức chính xác", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancelEnd(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(endIntent(context))
    }

    // --- Thông báo ---

    private fun action(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context, action.hashCode(),
        Intent(context, PomodoroReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, Notifications.ID_POMODORO,
        Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_POMODORO),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Thông báo đếm ngược im lặng; đồng hồ do hệ thống tự chạy (chronometer), app không phải thức dậy. */
    private fun showOngoing(context: Context) {
        val s = current(context)
        val b = NotificationCompat.Builder(context, Notifications.CH_TIMER)
            .setSmallIcon(R.drawable.ic_moon)
            .setContentTitle("Pomodoro · ${s.phase.label}")
            .setContentIntent(openApp(context))
            .setOnlyAlertOnce(true)
            .setSilent(true)
        if (s.running) {
            b.setContentText("Phiên ${s.doneInCycle + if (s.phase == Phase.FOCUS) 1 else 0}/${s.settings.longEvery}")
                .setWhen(s.endAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
                .setOngoing(true)
                .addAction(0, "Tạm dừng", action(context, ACTION_PAUSE))
                .addAction(0, "Bỏ qua", action(context, ACTION_SKIP))
        } else {
            val m = s.remainingMs / 60_000
            val sec = (s.remainingMs / 1_000) % 60
            b.setContentText("Đang tạm dừng · còn %d:%02d".format(m, sec))
                .addAction(0, "Tiếp tục", action(context, ACTION_RESUME))
                .addAction(0, "Bỏ qua", action(context, ACTION_SKIP))
        }
        runCatching { context.getSystemService(NotificationManager::class.java).notify(Notifications.ID_POMODORO, b.build()) }
    }

    private fun alertPhaseEnd(context: Context, ended: Phase, next: Phase, s: PomodoroSettings) {
        val title = if (ended == Phase.FOCUS) "Hết giờ tập trung" else "Hết giờ nghỉ"
        val text = "Tiếp theo: ${next.label} ${PomodoroRules.durationMs(next, s) / 60_000} phút" +
            if (s.autoStart) " (đã tự bắt đầu)" else ""
        val n = NotificationCompat.Builder(context, Notifications.CH_POMODORO)
            .setSmallIcon(R.drawable.ic_moon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(Notifications.ID_POMODORO_ALERT, n) }
    }
}
