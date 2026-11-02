package vn.quangns.timeforbed.core

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ảnh chụp toàn bộ cài đặt để UI quan sát. */
data class Settings(
    val nightEnabled: Boolean,
    val nightStart: Int,
    val nightEnd: Int,
    val idleMinutes: Int,
    val lockAfterStop: Boolean,
    val timerDeadline: Long,
    val lastTimerMinutes: Int,
    val excluded: Set<String>,
    val lastActionAt: Long,
    val lastActionReason: String,
)

/**
 * Lưu cài đặt bằng SharedPreferences: đọc đồng bộ được từ receiver/service
 * (Accessibility, BroadcastReceiver) mà không cần coroutine.
 */
class Prefs private constructor(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _state.value = read()
    }

    init {
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    var nightEnabled: Boolean
        get() = sp.getBoolean(K_NIGHT_ENABLED, true)
        set(v) = sp.edit { putBoolean(K_NIGHT_ENABLED, v) }

    /** Phút trong ngày (0..1439). */
    var nightStart: Int
        get() = sp.getInt(K_NIGHT_START, 0)
        set(v) = sp.edit { putInt(K_NIGHT_START, v) }

    var nightEnd: Int
        get() = sp.getInt(K_NIGHT_END, 6 * 60)
        set(v) = sp.edit { putInt(K_NIGHT_END, v) }

    var idleMinutes: Int
        get() = sp.getInt(K_IDLE, 30)
        set(v) = sp.edit { putInt(K_IDLE, v) }

    var lockAfterStop: Boolean
        get() = sp.getBoolean(K_LOCK, true)
        set(v) = sp.edit { putBoolean(K_LOCK, v) }

    /** Epoch millis hết giờ hẹn ngủ; 0 = không hẹn. */
    var timerDeadline: Long
        get() = sp.getLong(K_TIMER_DEADLINE, 0L)
        set(v) = sp.edit { putLong(K_TIMER_DEADLINE, v) }

    var lastTimerMinutes: Int
        get() = sp.getInt(K_TIMER_LAST, 30)
        set(v) = sp.edit { putInt(K_TIMER_LAST, v) }

    var excluded: Set<String>
        get() = sp.getStringSet(K_EXCLUDED, emptySet())!!.toSet()
        set(v) = sp.edit { putStringSet(K_EXCLUDED, v.toSet()) }

    var exclusionsInitialized: Boolean
        get() = sp.getBoolean(K_EXCL_INIT, false)
        set(v) = sp.edit { putBoolean(K_EXCL_INIT, v) }

    fun recordAction(reason: String, at: Long = System.currentTimeMillis()) = sp.edit {
        putLong(K_LAST_ACTION_AT, at)
        putString(K_LAST_ACTION_REASON, reason)
    }

    private fun read() = Settings(
        nightEnabled = nightEnabled,
        nightStart = nightStart,
        nightEnd = nightEnd,
        idleMinutes = idleMinutes,
        lockAfterStop = lockAfterStop,
        timerDeadline = timerDeadline,
        lastTimerMinutes = lastTimerMinutes,
        excluded = excluded,
        lastActionAt = sp.getLong(K_LAST_ACTION_AT, 0L),
        lastActionReason = sp.getString(K_LAST_ACTION_REASON, "") ?: "",
    )

    companion object {
        private const val K_NIGHT_ENABLED = "night_enabled"
        private const val K_NIGHT_START = "night_start"
        private const val K_NIGHT_END = "night_end"
        private const val K_IDLE = "idle_minutes"
        private const val K_LOCK = "lock_after_stop"
        private const val K_TIMER_DEADLINE = "timer_deadline"
        private const val K_TIMER_LAST = "timer_last_minutes"
        private const val K_EXCLUDED = "excluded_packages"
        private const val K_EXCL_INIT = "excluded_initialized"
        private const val K_LAST_ACTION_AT = "last_action_at"
        private const val K_LAST_ACTION_REASON = "last_action_reason"

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context).also { instance = it }
            }
    }
}
