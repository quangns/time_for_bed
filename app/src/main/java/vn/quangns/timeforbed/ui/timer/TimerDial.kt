package vn.quangns.timeforbed.ui.timer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import vn.quangns.timeforbed.ui.DialRing
import vn.quangns.timeforbed.ui.theme.Night

/**
 * Vòng tròn hẹn giờ 0–60 phút.
 * - Chưa chạy: kéo quanh vòng để chọn phút; nút tròn xanh lá ở giữa để bắt đầu.
 * - Đang chạy: cung tròn co dần theo thời gian còn lại, giữa hiện đếm ngược đến giây.
 *
 * Cử chỉ kéo gắn vào lớp vẽ vòng ([DialRing], nằm dưới nút), nên chạm vào nút Bắt đầu không bao giờ
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
    DialRing(
        value = minutes,
        max = DialMath.MAX_MINUTES,
        onValueChange = if (running) null else onMinutesChange,
        description = "Vòng chọn thời gian hẹn giờ, $minutes phút",
        fraction = if (running) DialMath.fraction(remainingMs!!) else minutes / DialMath.MAX_MINUTES.toFloat(),
        modifier = modifier,
    ) {
        AnimatedContent(
            targetState = running,
            transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(350)) },
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
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
