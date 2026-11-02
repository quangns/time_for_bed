package vn.quangns.timeforbed.core

import java.time.LocalDateTime

/** Các quy tắc thời gian thuần (không phụ thuộc Android) để dễ kiểm thử. */
object TimeRules {

    /**
     * Phút [minuteOfDay] có nằm trong khung [start, end) không. Khung được phép
     * vắt qua nửa đêm (vd 23:00–06:00). start == end nghĩa là cả ngày.
     */
    fun inWindow(start: Int, end: Int, minuteOfDay: Int): Boolean = when {
        start == end -> true
        start < end -> minuteOfDay in start until end
        else -> minuteOfDay >= start || minuteOfDay < end
    }

    /**
     * Lần reo kế tiếp sau [now]. [daysMask]: bit 0 = Thứ Hai … bit 6 = Chủ Nhật;
     * 0 = báo thức một lần.
     */
    fun nextAlarm(now: LocalDateTime, hour: Int, minute: Int, daysMask: Int): LocalDateTime {
        var candidate = now.toLocalDate().atTime(hour, minute)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        if (daysMask and 0x7F == 0) return candidate
        repeat(7) {
            if (daysMask and (1 shl (candidate.dayOfWeek.value - 1)) != 0) return candidate
            candidate = candidate.plusDays(1)
        }
        return candidate
    }

    fun formatMinuteOfDay(m: Int): String = "%02d:%02d".format(m / 60, m % 60)
}

enum class NightAction { NONE, WARN, STOP }

/** Quyết định của chế độ ban đêm cho một lần kiểm tra. */
object NightRules {
    const val WARN_BEFORE_MS = 60_000L

    fun decide(
        enabled: Boolean,
        minuteOfDay: Int,
        start: Int,
        end: Int,
        idleMs: Long,
        thresholdMs: Long,
        screenOn: Boolean,
        mediaPlaying: Boolean,
        foregroundExcluded: Boolean,
        alarmRinging: Boolean,
    ): NightAction {
        if (!enabled || alarmRinging) return NightAction.NONE
        if (!TimeRules.inWindow(start, end, minuteOfDay)) return NightAction.NONE
        // Màn hình tắt và không có gì phát: không cần làm gì.
        if (!mediaPlaying && !screenOn) return NightAction.NONE
        // Đang mở app loại trừ (ngân hàng…) mà không có media khác: để yên.
        if (!mediaPlaying && foregroundExcluded) return NightAction.NONE
        return when {
            idleMs >= thresholdMs -> NightAction.STOP
            idleMs >= thresholdMs - WARN_BEFORE_MS -> NightAction.WARN
            else -> NightAction.NONE
        }
    }
}
