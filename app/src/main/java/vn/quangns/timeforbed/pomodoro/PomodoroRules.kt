package vn.quangns.timeforbed.pomodoro

enum class Phase(val label: String) {
    FOCUS("Tập trung"),
    SHORT_BREAK("Nghỉ ngắn"),
    LONG_BREAK("Nghỉ dài"),
}

data class PomodoroSettings(
    val focusMin: Int = 25,
    val shortMin: Int = 5,
    val longMin: Int = 15,
    /** Số phiên tập trung trước một lần nghỉ dài. */
    val longEvery: Int = 4,
    /** Hết giai đoạn thì tự chạy giai đoạn kế. */
    val autoStart: Boolean = true,
)

/** Quy tắc chu kỳ Pomodoro (thuần Kotlin để kiểm thử). */
object PomodoroRules {

    fun durationMs(phase: Phase, s: PomodoroSettings): Long = 60_000L * when (phase) {
        Phase.FOCUS -> s.focusMin
        Phase.SHORT_BREAK -> s.shortMin
        Phase.LONG_BREAK -> s.longMin
    }

    /**
     * Giai đoạn kế tiếp. [doneInCycle] = số phiên tập trung đã xong trong chu kỳ hiện tại.
     * [counted] = false khi người dùng bấm "Bỏ qua" giữa phiên tập trung (không tính là đã xong).
     */
    fun next(phase: Phase, doneInCycle: Int, longEvery: Int, counted: Boolean = true): Pair<Phase, Int> = when (phase) {
        Phase.FOCUS -> {
            val done = if (counted) doneInCycle + 1 else doneInCycle
            if (counted && done >= longEvery) Phase.LONG_BREAK to done else Phase.SHORT_BREAK to done
        }
        Phase.SHORT_BREAK -> Phase.FOCUS to doneInCycle
        Phase.LONG_BREAK -> Phase.FOCUS to 0
    }
}
