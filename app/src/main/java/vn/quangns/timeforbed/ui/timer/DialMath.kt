package vn.quangns.timeforbed.ui.timer

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt

/** Quy đổi vị trí chạm trên vòng tròn hẹn giờ ra số phút (thuần Kotlin để kiểm thử). */
object DialMath {
    const val MAX_MINUTES = 60

    /**
     * [dx], [dy]: tọa độ điểm chạm so với tâm (trục y hướng xuống, như màn hình).
     * 0 phút ở đỉnh, tăng theo chiều kim đồng hồ. [previous] dùng để chặn nhảy vòng qua đỉnh:
     * đang ở gần 60 mà kéo vượt đỉnh thì giữ 60, đang ở gần 0 mà kéo ngược thì giữ 0.
     */
    fun minutesAt(dx: Float, dy: Float, previous: Int, max: Int = MAX_MINUTES): Int {
        var angle = atan2(dx.toDouble(), -dy.toDouble()) // 0 ở đỉnh, dương theo chiều kim đồng hồ
        if (angle < 0) angle += 2 * PI
        val raw = (angle / (2 * PI) * max).roundToInt() % (max + 1)
        val quarter = max / 4
        return when {
            previous >= max - quarter && raw < quarter -> max
            previous <= quarter && raw > max - quarter -> 0
            else -> raw.coerceIn(0, max)
        }
    }

    /** Tỉ lệ cung tròn (0..1) cho thời gian còn lại, thang 60 phút. */
    fun fraction(remainingMs: Long, max: Int = MAX_MINUTES): Float =
        (remainingMs.toFloat() / (max * 60_000f)).coerceIn(0f, 1f)
}
