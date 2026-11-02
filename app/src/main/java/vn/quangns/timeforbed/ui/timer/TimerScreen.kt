package vn.quangns.timeforbed.ui.timer

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import vn.quangns.timeforbed.ui.ScreenHeader
import vn.quangns.timeforbed.ui.theme.Night
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimerScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val settings by prefs.state.collectAsStateWithLifecycle()

    // Hạn chót đọc thẳng từ SharedPreferences (không chỉ chờ listener) để bấm Bắt đầu là đếm ngay.
    var deadline by remember { mutableLongStateOf(prefs.timerDeadline) }
    LaunchedEffect(settings.timerDeadline) { deadline = settings.timerDeadline }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            deadline = prefs.timerDeadline // đồng bộ khi bấm +10 phút / Hủy từ thông báo
            delay(1_000 - now % 1_000) // canh đầu mỗi giây để đếm ngược không nhảy cóc
        }
    }
    val active = deadline > now
    var minutes by rememberSaveable { mutableIntStateOf(prefs.lastTimerMinutes.coerceIn(0, DialMath.MAX_MINUTES)) }

    fun refresh() {
        now = System.currentTimeMillis()
        deadline = prefs.timerDeadline
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ScreenHeader(
            if (active) "Đang hẹn giờ ngủ" else "Hẹn giờ ngủ",
            if (active) "Hết giờ sẽ dừng mọi video, nhạc — trừ báo thức và app ngân hàng."
            else "Kéo quanh vòng để chọn 0–60 phút, rồi chạm Bắt đầu.",
        )

        TimerDial(
            minutes = minutes,
            onMinutesChange = { minutes = it },
            onStart = {
                if (minutes > 0) {
                    SleepTimer.start(context, minutes)
                    refresh()
                }
            },
            remainingMs = if (active) deadline - now else null,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        if (active) {
            Text(
                "Tắt lúc " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(deadline)),
                style = MaterialTheme.typography.bodyLarge,
                color = Night.Mist,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { SleepTimer.extend(context); refresh() },
                    border = BorderStroke(1.dp, Night.Hairline),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("+${SleepTimer.EXTEND_MINUTES} phút", color = Night.Moonlight) }
                TextButton(
                    onClick = { SleepTimer.cancel(context); refresh() },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("Hủy hẹn giờ", color = Night.Ember) }
            }
        }

        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            TextButton(onClick = {
                val stopped = MediaKiller.stopAll(context, "Tắt thủ công", lockScreen = false)
                val msg = if (stopped.isEmpty()) "Không thấy trình phát nào (hoặc chưa cấp quyền)"
                else "Đã dừng: " + stopped.joinToString()
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }) { Text("Dừng mọi video, nhạc ngay", color = Night.Mist) }
            if (settings.lastActionAt > 0) {
                Text(
                    "Lần tắt gần nhất " +
                        SimpleDateFormat("HH:mm, dd/MM", Locale.getDefault()).format(Date(settings.lastActionAt)) +
                        " · ${settings.lastActionReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Night.Mist.copy(alpha = 0.7f),
                )
            }
        }
    }
}
