package vn.quangns.timeforbed.alarm

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Đo mức vận động từ cảm biến gia tốc (thuần Kotlin để kiểm thử).
 * Mỗi giây được tính là "có vận động" nếu trong giây đó độ lớn gia tốc lệch khỏi trọng lực quá
 * [thresholdMs2] — cầm máy đi lại hoặc để trong túi khi đi thì lệch 2–5 m/s²; máy nằm yên trên giường
 * hay lướt nhẹ màn hình lúc nằm thì lệch dưới ~1 m/s² nên không được tính.
 */
class WakeMeter(private val thresholdMs2: Float = 1.6f) {
    private val activeSeconds = HashSet<Long>()

    val activeCount: Int get() = activeSeconds.size

    fun onSample(timeMs: Long, x: Float, y: Float, z: Float) {
        val magnitude = sqrt(x * x + y * y + z * z)
        if (abs(magnitude - GRAVITY) > thresholdMs2) activeSeconds += timeMs / 1_000
    }

    companion object {
        const val GRAVITY = 9.80665f
    }
}

/** Trạng thái kiểm tra đã dậy, hiển thị trong app và trên thông báo. */
data class WakeCheckState(
    val alarm: AlarmEntity,
    val activeSeconds: Int,
    val requiredSeconds: Int,
    val remainingMs: Long,
    /** Lần kiểm tra thứ mấy (1 = sau lần tắt đầu tiên). */
    val round: Int,
)

object WakeCheckRules {
    /** Số lần tối đa chuông reo lại vì chưa vận động đủ — tránh kẹt vô hạn. */
    const val MAX_RINGS_AGAIN = 3

    val WINDOW_MINUTES = listOf(3, 5, 10)
    val LEVELS = listOf(30 to "Ít", 60 to "Vừa", 120 to "Nhiều")

    fun passed(activeSeconds: Int, required: Int) = activeSeconds >= required

    /** Hết thời gian mà chưa đủ → reo lại, trừ khi đã reo lại đủ số lần tối đa. */
    fun shouldRingAgain(activeSeconds: Int, required: Int, ringsAgainSoFar: Int) =
        !passed(activeSeconds, required) && ringsAgainSoFar < MAX_RINGS_AGAIN
}
