package vn.quangns.timeforbed.ui.timer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private val StartGreen = Color(0xFF22C55E)
private const val RING_WIDTH_RATIO = 0.075f
private const val CENTER_RATIO = 0.56f

/**
 * Vòng tròn hẹn giờ 0–60 phút.
 * - Chưa chạy: kéo quanh vòng để chọn phút; nút tròn xanh lá ở giữa để bắt đầu.
 * - Đang chạy: cung tròn co dần theo thời gian còn lại, giữa hiện đếm ngược đến giây.
 */
@Composable
fun TimerDial(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    onStart: () -> Unit,
    remainingMs: Long?,
    modifier: Modifier = Modifier,
) {
    val running = remainingMs != null
    val haptic = LocalHapticFeedback.current
    val currentMinutes by rememberUpdatedState(minutes)
    val onChange by rememberUpdatedState(onMinutesChange)

    val track = MaterialTheme.colorScheme.surfaceVariant
    val arc = MaterialTheme.colorScheme.primary
    val tick = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics { contentDescription = "Vòng chọn thời gian hẹn giờ, $minutes phút" }
            .pointerInput(running) {
                if (running) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val c = Offset(this.size.width / 2f, this.size.height / 2f)
                    val radius = min(this.size.width, this.size.height) / 2f
                    // Chạm vào nút giữa thì để nút xử lý, không đổi thời gian.
                    if (hypot(down.position.x - c.x, down.position.y - c.y) < radius * CENTER_RATIO * 1.02f) {
                        return@awaitEachGesture
                    }
                    var last = currentMinutes
                    fun update(p: Offset, first: Boolean = false) {
                        // Lần chạm đầu: nhảy thẳng tới vị trí chạm, không áp dụng chặn vòng qua đỉnh.
                        val prev = if (first) DialMath.MAX_MINUTES / 2 else last
                        val m = DialMath.minutesAt(p.x - c.x, p.y - c.y, prev)
                        if (m != last) {
                            last = m
                            onChange(m)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                    update(down.position, first = true)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        update(change.position)
                        change.consume()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val fraction = if (running) DialMath.fraction(remainingMs!!) else minutes / DialMath.MAX_MINUTES.toFloat()
        Canvas(Modifier.fillMaxSize()) {
            val d = min(this.size.width, this.size.height)
            val stroke = d * RING_WIDTH_RATIO
            val r = (d - stroke) / 2f - d * 0.06f
            val topLeft = Offset(center.x - r, center.y - r)
            val arcSize = Size(r * 2, r * 2)

            // Vạch chia: 5 phút một vạch, vạch dài ở 0/15/30/45.
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0 - 90).toFloat()
                val major = i % 3 == 0
                val outer = r + stroke * 0.5f + d * 0.045f
                val inner = outer - if (major) d * 0.035f else d * 0.018f
                drawLine(
                    color = tick.copy(alpha = if (major) 0.9f else 0.5f),
                    start = Offset(center.x + inner * cos(a), center.y + inner * sin(a)),
                    end = Offset(center.x + outer * cos(a), center.y + outer * sin(a)),
                    strokeWidth = if (major) 3f else 2f,
                    cap = StrokeCap.Round,
                )
            }
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (fraction > 0f) {
                drawArc(arc, -90f, 360f * fraction, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            // Núm kéo ở đầu cung (chỉ khi đang chọn).
            if (!running) {
                val a = Math.toRadians(360.0 * fraction - 90).toFloat()
                val p = Offset(center.x + r * cos(a), center.y + r * sin(a))
                drawCircle(Color.White, radius = stroke * 0.85f, center = p)
                drawCircle(arc, radius = stroke * 0.55f, center = p)
            }
        }

        if (running) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatCountdown(remainingMs!!),
                    fontSize = if (remainingMs >= 3_600_000) 44.sp else 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text("còn lại", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Surface(
                onClick = onStart,
                enabled = minutes > 0,
                shape = CircleShape,
                color = if (minutes > 0) StartGreen else StartGreen.copy(alpha = 0.35f),
                contentColor = Color.White,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(CENTER_RATIO).aspectRatio(1f),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Bắt đầu", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (minutes > 0) "$minutes phút" else "Kéo vòng để chọn",
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }
        }
    }
}

/** MM:SS, hoặc H:MM:SS nếu còn từ 1 giờ trở lên (sau khi bấm +10 phút). */
fun formatCountdown(ms: Long): String {
    val total = ((ms + 999) / 1000).coerceAtLeast(0) // làm tròn lên để không hiện 00:00 khi còn <1 giây
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
