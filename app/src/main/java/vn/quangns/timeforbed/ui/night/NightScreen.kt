package vn.quangns.timeforbed.ui.night

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vn.quangns.timeforbed.core.Permissions
import vn.quangns.timeforbed.core.Prefs
import vn.quangns.timeforbed.core.TimeRules
import vn.quangns.timeforbed.service.BedtimeAccessibilityService
import vn.quangns.timeforbed.ui.SectionCard
import vn.quangns.timeforbed.ui.TimePickerDialog
import java.time.LocalTime

@Composable
fun NightScreen() {
    val context = LocalContext.current
    val prefs = Prefs.get(context)
    val s by prefs.state.collectAsStateWithLifecycle()
    var pick by remember { mutableStateOf<String?>(null) }
    var idle by remember(s.idleMinutes) { mutableFloatStateOf(s.idleMinutes.toFloat()) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(5_000)
        }
    }
    val lastInteraction by BedtimeAccessibilityService.lastInteraction.collectAsStateWithLifecycle()
    val serviceOn = Permissions.isAccessibilityEnabled(context)
    val t = LocalTime.now()
    val inWindow = TimeRules.inWindow(s.nightStart, s.nightEnd, t.hour * 60 + t.minute)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(
            "Tự tắt khi ngủ quên",
            subtitle = "Trong khung giờ dưới đây, nếu quá ${s.idleMinutes} phút không chạm/thao tác thì tắt " +
                "mọi video, nhạc (trừ báo thức, ngân hàng). Báo trước 1 phút.",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bật chế độ ban đêm", Modifier.weight(1f))
                Switch(checked = s.nightEnabled, onCheckedChange = { prefs.nightEnabled = it })
            }
        }

        SectionCard("Khung giờ", subtitle = "Có thể vắt qua nửa đêm, ví dụ 23:00 → 06:00") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pick = "start" }, modifier = Modifier.weight(1f)) {
                    Text("Từ " + TimeRules.formatMinuteOfDay(s.nightStart))
                }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = { pick = "end" }, modifier = Modifier.weight(1f)) {
                    Text("Đến " + TimeRules.formatMinuteOfDay(s.nightEnd))
                }
            }
            if (s.nightStart == s.nightEnd) {
                Text("Giờ bắt đầu = giờ kết thúc: áp dụng cả ngày", style = MaterialTheme.typography.bodySmall)
            }
        }

        SectionCard("Ngưỡng không thao tác") {
            Text("${idle.toInt()} phút", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Slider(
                value = idle,
                onValueChange = { idle = (it / 5).toInt().coerceAtLeast(1) * 5f },
                onValueChangeFinished = { prefs.idleMinutes = idle.toInt() },
                valueRange = 5f..120f,
            )
        }

        SectionCard("Sau khi tắt media") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Về màn hình chính và khóa máy")
                    Text(
                        "Áp dụng cho cả hẹn giờ ngủ. Bỏ qua nếu đang mở app ngân hàng.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = s.lockAfterStop, onCheckedChange = { prefs.lockAfterStop = it })
            }
        }

        SectionCard("Trạng thái") {
            val status = when {
                !s.nightEnabled -> "Đang tắt"
                !serviceOn -> "Chưa bật Trợ năng — chế độ ban đêm không hoạt động"
                inWindow -> "Đang trong khung giờ — đang theo dõi"
                else -> "Ngoài khung giờ — chờ đến " + TimeRules.formatMinuteOfDay(s.nightStart)
            }
            Text(status)
            if (serviceOn) {
                val mins = ((now - lastInteraction) / 60_000).coerceAtLeast(0)
                Text(
                    "Thao tác gần nhất: " + if (mins == 0L) "vừa xong" else "$mins phút trước",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    when (pick) {
        "start" -> TimePickerDialog("Bắt đầu", s.nightStart, { pick = null }) {
            prefs.nightStart = it
            pick = null
        }
        "end" -> TimePickerDialog("Kết thúc", s.nightEnd, { pick = null }) {
            prefs.nightEnd = it
            pick = null
        }
    }
}
