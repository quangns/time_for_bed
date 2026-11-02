package vn.quangns.timeforbed.ui.timer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import vn.quangns.timeforbed.ui.theme.Night
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private const val RING_WIDTH_RATIO = 0.07f
private const val CENTER_RATIO = 0.54f

/**
 * Vòng tròn hẹn giờ 0–60 phút.
 * - Chưa chạy: kéo quanh vòng để chọn phút; nút tròn xanh lá ở giữa để bắt đầu.
 * - Đang chạy: cung tròn co dần theo thời gian còn lại, giữa hiện đếm ngược đến giây.
 *
 * Cử chỉ kéo gắn vào lớp vẽ vòng (nằm dưới nút), nên chạm vào nút Bắt đầu không bao giờ
 * đi qua bộ xử lý kéo.
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

    val target = if (running) DialMath.fraction(remainingMs!!) else minutes / DialMath.MAX_MINUTES.toFloat()
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "dial",
    )

    Box(
        modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics { contentDescription = "Vòng chọn thời gian hẹn giờ, $minutes phút" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(running) {
                    if (running) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val radius = min(size.width, size.height) / 2f
                        // Vùng giữa thuộc về nút Bắt đầu.
                        if (hypot(down.position.x - c.x, down.position.y - c.y) < radius * CENTER_RATIO) {
                            return@awaitEachGesture
                        }
                        var last = currentMinutes
                        fun update(p: Offset, first: Boolean) {
                            // Lần chạm đầu nhảy thẳng tới vị trí chạm; khi kéo thì chặn vòng qua đỉnh.
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
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            update(change.position, first = false)
                            change.consume()
                        }
                    }
                },
        ) {
            val d = min(size.width, size.height)
            val stroke = d * RING_WIDTH_RATIO
            val r = (d - stroke) / 2f - d * 0.07f
            val topLeft = Offset(center.x - r, center.y - r)
            val arcSize = Size(r * 2, r * 2)

            // Vạch chia 5 phút, vạch 0/15/30/45 dài hơn.
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0 - 90).toFloat()
                val major = i % 3 == 0
                val outer = r + stroke / 2f + d * 0.05f
                val inner = outer - if (major) d * 0.03f else d * 0.015f
                drawLine(
                    color = Night.Mist.copy(alpha = if (major) 0.8f else 0.4f),
                    start = Offset(center.x + inner * cos(a), center.y + inner * sin(a)),
                    end = Offset(center.x + outer * cos(a), center.y + outer * sin(a)),
                    strokeWidth = if (major) 3f else 2f,
                    cap = StrokeCap.Round,
                )
            }
            drawArc(Night.Hairline, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (fraction > 0.001f) {
                drawArc(Night.Green, -90f, 360f * fraction, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            if (!running) {
                val a = Math.toRadians(360.0 * fraction - 90).toFloat()
                val p = Offset(center.x + r * cos(a), center.y + r * sin(a))
                drawCircle(Night.Moonlight, radius = stroke * 0.8f, center = p)
                drawCircle(Night.Green, radius = stroke * 0.45f, center = p)
            }
        }

        AnimatedContent(
            targetState = running,
            transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(350)) },
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth(CENTER_RATIO).aspectRatio(1f),
            label = "center",
        ) { isRunning ->
            if (isRunning) {
                Countdown(remainingMs ?: 0L)
            } else {
                StartButton(minutes = minutes, onStart = onStart)
            }
        }
    }
}

@Composable
private fun Countdown(remainingMs: Long) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            formatCountdown(remainingMs),
            style = if (remainingMs >= 3_600_000) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayLarge,
            color = Night.Moonlight,
        )
        Text("còn lại", style = MaterialTheme.typography.bodySmall, color = Night.Mist)
    }
}

@Composable
private fun StartButton(minutes: Int, onStart: () -> Unit) {
    val enabled = minutes > 0
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Nhịp "thở" chậm khi đang chờ bấm; co nhẹ khi nhấn.
    val breath by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val press by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f), label = "press")

    Surface(
        onClick = onStart,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) Night.Green else Night.Green.copy(alpha = 0.3f),
        contentColor = Night.Ink,
        interactionSource = interaction,
        modifier = Modifier.fillMaxSize().scale((if (enabled) breath else 1f) * press),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Bắt đầu", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (enabled) "$minutes phút" else "Kéo vòng để chọn",
                style = MaterialTheme.typography.bodySmall,
                color = Night.Ink.copy(alpha = 0.75f),
            )
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
