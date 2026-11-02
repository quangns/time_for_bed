package vn.quangns.timeforbed.ui.timer

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vn.quangns.timeforbed.core.MediaKiller
import vn.quangns.timeforbed.core.Prefs
import vn.quangns.timeforbed.service.SleepTimer
import vn.quangns.timeforbed.ui.SectionCard
import vn.quangns.timeforbed.ui.formatDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PRESETS = listOf(15, 30, 45, 60, 90, 120)

@Composable
fun TimerScreen() {
    val context = LocalContext.current
    val settings by Prefs.get(context).state.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val active = settings.timerDeadline > now

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (active) {
            SectionCard("Đang hẹn giờ ngủ", subtitle = "Hết giờ sẽ dừng mọi video/nhạc (trừ báo thức, ngân hàng)") {
                Text(
                    formatDuration(settings.timerDeadline - now),
                    fontSize = 64.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Text(
                    "Tắt lúc " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(settings.timerDeadline)),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { SleepTimer.extend(context) }, modifier = Modifier.weight(1f)) {
                        Text("+${SleepTimer.EXTEND_MINUTES} phút")
                    }
                    Button(onClick = { SleepTimer.cancel(context) }, modifier = Modifier.weight(1f)) {
                        Text("Hủy hẹn giờ")
                    }
                }
            }
        } else {
            StartTimerCard(lastMinutes = settings.lastTimerMinutes) { SleepTimer.start(context, it) }
        }

        SectionCard("Tắt ngay", subtitle = "Dừng video/nhạc đang phát bây giờ") {
            OutlinedButton(
                onClick = {
                    val stopped = MediaKiller.stopAll(context, "Tắt thủ công", lockScreen = false)
                    val msg = if (stopped.isEmpty()) "Không thấy trình phát nào (hoặc chưa cấp quyền)"
                    else "Đã dừng: " + stopped.joinToString()
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Dừng tất cả media") }
        }

        if (settings.lastActionAt > 0) {
            Text(
                "Lần tắt gần nhất: " +
                    SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(settings.lastActionAt)) +
                    " — ${settings.lastActionReason}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartTimerCard(lastMinutes: Int, onStart: (Int) -> Unit) {
    var minutes by remember { mutableFloatStateOf(lastMinutes.toFloat()) }
    SectionCard("Hẹn giờ ngủ", subtitle = "Đếm ngược rồi tự tắt video, nhạc; về màn hình chính và khóa máy") {
        Text(
            "${minutes.toInt()} phút",
            fontSize = 48.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Slider(
            value = minutes,
            onValueChange = { minutes = (it / 5).toInt() * 5f },
            valueRange = 5f..240f,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESETS.forEach { p ->
                FilterChip(
                    selected = minutes.toInt() == p,
                    onClick = { minutes = p.toFloat() },
                    label = { Text(if (p % 60 == 0) "${p / 60} giờ" else "$p phút") },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onStart(minutes.toInt().coerceAtLeast(1)) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Bắt đầu", fontSize = 18.sp)
        }
        TextButton(onClick = { onStart(1) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Thử nhanh: 1 phút")
        }
    }
}
