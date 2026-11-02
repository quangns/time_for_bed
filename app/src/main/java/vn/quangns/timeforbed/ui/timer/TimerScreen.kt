package vn.quangns.timeforbed.ui.timer

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vn.quangns.timeforbed.core.MediaKiller
import vn.quangns.timeforbed.core.Prefs
import vn.quangns.timeforbed.service.SleepTimer
import vn.quangns.timeforbed.ui.SectionCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimerScreen() {
    val context = LocalContext.current
    val settings by Prefs.get(context).state.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000 - now % 1_000) // canh đúng đầu mỗi giây để đếm ngược không nhảy cóc
        }
    }
    val active = settings.timerDeadline > now
    var minutes by rememberSaveable { mutableIntStateOf(settings.lastTimerMinutes.coerceIn(0, DialMath.MAX_MINUTES)) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(
            if (active) "Đang hẹn giờ ngủ" else "Hẹn giờ ngủ",
            subtitle = if (active) "Hết giờ sẽ dừng mọi video/nhạc (trừ báo thức, ngân hàng)"
            else "Kéo vòng tròn để chọn 0–60 phút, rồi bấm Bắt đầu",
        ) {
            TimerDial(
                minutes = minutes,
                onMinutesChange = { minutes = it },
                onStart = { if (minutes > 0) SleepTimer.start(context, minutes) },
                remainingMs = if (active) settings.timerDeadline - now else null,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            if (active) {
                Text(
                    "Tắt lúc " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(settings.timerDeadline)),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { SleepTimer.extend(context) }, modifier = Modifier.weight(1f)) {
                        Text("+${SleepTimer.EXTEND_MINUTES} phút")
                    }
                    Button(onClick = { SleepTimer.cancel(context) }, modifier = Modifier.weight(1f)) {
                        Text("Hủy hẹn giờ")
                    }
                }
            }
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
