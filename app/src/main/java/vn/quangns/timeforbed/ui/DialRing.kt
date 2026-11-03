package vn.quangns.timeforbed.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vn.quangns.timeforbed.ui.theme.Night
import vn.quangns.timeforbed.ui.timer.DialMath
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private const val RING_WIDTH_RATIO = 0.07f

/**
 * Vòng tròn kéo thả chọn số phút (dùng cho Hẹn giờ ngủ và ngưỡng "Không thao tác quá").
 * 0 ở đỉnh, tăng theo chiều kim đồng hồ, một vòng = [max] phút, bước nhảy [step].
 * [onValueChange] = null → chỉ hiển thị (vd đang đếm ngược), không kéo được.
 * Vùng giữa (tỉ lệ [centerRatio]) dành cho [content] — chạm vào đó không đổi giá trị.
 */
@Composable
fun DialRing(
    value: Int,
    max: Int,
    onValueChange: ((Int) -> Unit)?,
    description: String,
    modifier: Modifier = Modifier,
    fraction: Float = value / max.toFloat(),
    step: Int = 1,
    minValue: Int = 0,
    maxWidth: Dp = 320.dp,
    centerRatio: Float = 0.54f,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val current by rememberUpdatedState(value)
    val onChange by rememberUpdatedState(onValueChange)
    val editable = onValueChange != null
    val shown by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "dial",
    )

    Box(
        modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(editable, max, step, minValue) {
                    if (!editable) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val radius = min(size.width, size.height) / 2f
                        if (hypot(down.position.x - c.x, down.position.y - c.y) < radius * centerRatio) {
                            return@awaitEachGesture
                        }
                        var last = current
                        fun update(p: Offset, first: Boolean) {
                            // Lần chạm đầu nhảy thẳng tới vị trí chạm; khi kéo thì chặn vòng qua đỉnh.
                            val prev = if (first) max / 2 else last
                            val m = DialMath.snap(DialMath.minutesAt(p.x - c.x, p.y - c.y, prev, max), step, minValue, max)
                            if (m != last) {
                                last = m
                                onChange?.invoke(m)
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

            // 12 vạch chia, vạch ở 0/¼/½/¾ vòng dài hơn.
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
            if (shown > 0.001f) {
                drawArc(Night.Green, -90f, 360f * shown, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            if (editable) {
                val a = Math.toRadians(360.0 * shown - 90).toFloat()
                val p = Offset(center.x + r * cos(a), center.y + r * sin(a))
                drawCircle(Night.Moonlight, radius = stroke * 0.8f, center = p)
                drawCircle(Night.Green, radius = stroke * 0.45f, center = p)
            }
        }
        Box(Modifier.fillMaxWidth(centerRatio).aspectRatio(1f), contentAlignment = Alignment.Center) {
            content()
        }
    }
}
