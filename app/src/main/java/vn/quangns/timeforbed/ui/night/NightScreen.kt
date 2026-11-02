package vn.quangns.timeforbed.ui.night

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import vn.quangns.timeforbed.ui.Hairline
import vn.quangns.timeforbed.ui.NightSwitch
import vn.quangns.timeforbed.ui.ScreenHeader
import vn.quangns.timeforbed.ui.SectionCard
import vn.quangns.timeforbed.ui.SettingRow
import vn.quangns.timeforbed.ui.StatusDot
import vn.quangns.timeforbed.ui.TimePickerDialog
import vn.quangns.timeforbed.ui.theme.Night
import java.time.LocalTime

/** Ô giờ bấm được: nền Raised Dusk, chữ số tabular. */
@Composable
private fun TimePill(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Night.Raised, shape = MaterialTheme.shapes.small) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        ScreenHeader(
            "Ban đêm",
            subtitle = "Ngủ quên khi đang xem? Quá ${s.idleMinutes} phút không chạm vào máy trong khung giờ này, " +
                "app sẽ tắt video, nhạc. Báo trước 1 phút.",
        )

        // Trạng thái: một dòng, chấm xanh = đang theo dõi, chấm cam = cần xử lý.
        val watching = s.nightEnabled && serviceOn && inWindow
        val status = when {
            !s.nightEnabled -> "Đang tắt"
            !serviceOn -> "Chưa bật Trợ năng, chế độ ban đêm chưa hoạt động"
            inWindow -> "Đang theo dõi"
            else -> "Chờ đến " + TimeRules.formatMinuteOfDay(s.nightStart)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(ok = s.nightEnabled && serviceOn)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(status, style = MaterialTheme.typography.bodyLarge)
                if (serviceOn && watching) {
                    val mins = ((now - lastInteraction) / 60_000).coerceAtLeast(0)
                    Text(
                        "Thao tác gần nhất " + if (mins == 0L) "vừa xong" else "$mins phút trước",
                        style = MaterialTheme.typography.bodySmall,
                        color = Night.Mist,
                    )
                }
            }
        }

        SectionCard("Tự tắt khi ngủ quên") {
            SettingRow("Bật chế độ ban đêm") {
                NightSwitch(checked = s.nightEnabled, onCheckedChange = { prefs.nightEnabled = it })
            }
            Hairline()
            SettingRow(
                "Khung giờ",
                description = if (s.nightStart == s.nightEnd) "Giờ bắt đầu trùng giờ kết thúc: áp dụng cả ngày"
                else "Có thể vắt qua nửa đêm, ví dụ 23:00 – 06:00",
            ) {
                TimePill(TimeRules.formatMinuteOfDay(s.nightStart)) { pick = "start" }
                Text("–", color = Night.Mist, modifier = Modifier.padding(horizontal = 6.dp))
                TimePill(TimeRules.formatMinuteOfDay(s.nightEnd)) { pick = "end" }
            }
            Hairline()
            Column(Modifier.padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Không thao tác quá", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${idle.toInt()} phút", style = MaterialTheme.typography.titleLarge, color = Night.Green)
                }
                Slider(
                    value = idle,
                    onValueChange = { idle = (it / 5).toInt().coerceAtLeast(1) * 5f },
                    onValueChangeFinished = { prefs.idleMinutes = idle.toInt() },
                    valueRange = 5f..120f,
                    colors = SliderDefaults.colors(
                        thumbColor = Night.Moonlight,
                        activeTrackColor = Night.Green,
                        inactiveTrackColor = Night.Raised,
                    ),
                )
            }
        }

        SectionCard("Sau khi tắt") {
            SettingRow(
                "Về màn hình chính và khóa máy",
                description = "Áp dụng cả cho hẹn giờ ngủ. Bỏ qua nếu đang mở app ngân hàng.",
            ) {
                NightSwitch(checked = s.lockAfterStop, onCheckedChange = { prefs.lockAfterStop = it })
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
