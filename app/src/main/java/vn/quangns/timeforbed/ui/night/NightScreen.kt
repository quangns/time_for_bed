package vn.quangns.timeforbed.ui.night

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import vn.quangns.timeforbed.core.Permissions
import vn.quangns.timeforbed.core.Prefs
import vn.quangns.timeforbed.core.TimeRules
import vn.quangns.timeforbed.service.BedtimeAccessibilityService
import vn.quangns.timeforbed.ui.DialRing
import vn.quangns.timeforbed.ui.Hairline
import vn.quangns.timeforbed.ui.NightSwitch
import vn.quangns.timeforbed.ui.ScreenHeader
import vn.quangns.timeforbed.ui.SectionCard
import vn.quangns.timeforbed.ui.SettingRow
import vn.quangns.timeforbed.ui.StatusDot
import vn.quangns.timeforbed.ui.TimePickerDialog
import vn.quangns.timeforbed.ui.theme.Night
import java.time.Instant
import java.time.ZoneId

const val IDLE_MIN = 5
const val IDLE_MAX = 120
private const val IDLE_STEP = 5

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

/**
 * Tab Ban đêm. Mọi thay đổi là bản nháp, chỉ ghi khi bấm "Lưu thay đổi" (ghi đồng bộ cả 4 giá trị),
 * nên không còn chuyện kéo xong bị trả về giá trị cũ hay không biết đã lưu chưa.
 */
@Composable
fun NightScreen(refreshKey: Int = 0) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val s by prefs.state.collectAsStateWithLifecycle()

    // Bản nháp: tạo lại mỗi khi giá trị đã lưu đổi (vd vừa bấm Lưu).
    var enabled by rememberSaveable(s.nightEnabled) { mutableStateOf(s.nightEnabled) }
    var start by rememberSaveable(s.nightStart) { mutableIntStateOf(s.nightStart) }
    var end by rememberSaveable(s.nightEnd) { mutableIntStateOf(s.nightEnd) }
    var idle by rememberSaveable(s.idleMinutes) { mutableIntStateOf(s.idleMinutes.coerceIn(IDLE_MIN, IDLE_MAX)) }
    val dirty = enabled != s.nightEnabled || start != s.nightStart || end != s.nightEnd || idle != s.idleMinutes
    var pick by remember { mutableStateOf<String?>(null) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(30_000) // chỉ để cập nhật dòng "thao tác gần nhất"; dừng khi app xuống nền
            }
        }
    }
    val lastInteraction by BedtimeAccessibilityService.lastInteraction.collectAsStateWithLifecycle()
    // Kiểm tra lại khi quay về app (vừa bật Trợ năng trong Cài đặt) và theo nhịp `now`.
    val serviceOn = remember(refreshKey) { Permissions.isAccessibilityEnabled(context) }
    val t = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalTime()
    val inWindow = TimeRules.inWindow(s.nightStart, s.nightEnd, t.hour * 60 + t.minute)

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            ScreenHeader(
                "Ban đêm",
                subtitle = "Ngủ quên khi đang xem? Quá $idle phút không chạm vào máy trong khung giờ này, " +
                    "app sẽ tạm dừng video, nhạc rồi khóa màn hình. Báo trước 1 phút.",
            )

            // Trạng thái theo giá trị ĐÃ LƯU: chấm xanh = đang theo dõi, chấm cam = cần xử lý.
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
                    NightSwitch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Hairline()
                SettingRow(
                    "Khung giờ",
                    description = if (start == end) "Giờ bắt đầu trùng giờ kết thúc: áp dụng cả ngày"
                    else "Có thể vắt qua nửa đêm, ví dụ 23:00 – 06:00",
                ) {
                    TimePill(TimeRules.formatMinuteOfDay(start)) { pick = "start" }
                    Text("–", color = Night.Mist, modifier = Modifier.padding(horizontal = 6.dp))
                    TimePill(TimeRules.formatMinuteOfDay(end)) { pick = "end" }
                }
                Hairline()
                Text(
                    "Không thao tác quá",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    "Kéo quanh vòng để chọn $IDLE_MIN–$IDLE_MAX phút",
                    style = MaterialTheme.typography.bodySmall,
                    color = Night.Mist,
                )
                DialRing(
                    value = idle,
                    max = IDLE_MAX,
                    onValueChange = { idle = it },
                    description = "Vòng chọn thời gian không thao tác, $idle phút",
                    step = IDLE_STEP,
                    minValue = IDLE_MIN,
                    maxWidth = 210.dp,
                    centerRatio = 0.6f,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp).testTag("idleDial"),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$idle", style = MaterialTheme.typography.displayMedium, color = Night.Moonlight)
                        Text("phút", style = MaterialTheme.typography.bodySmall, color = Night.Mist)
                    }
                }
            }

        }

        // Thanh Lưu ghim ở đáy, ngoài vùng cuộn: đổi gì là thấy ngay, không phải cuộn xuống
        // (vuốt trên vòng tròn chỉ đổi số phút, không cuộn trang).
        AnimatedVisibility(visible = dirty, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Surface(color = Night.Surface, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Hairline()
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                enabled = s.nightEnabled; start = s.nightStart; end = s.nightEnd; idle = s.idleMinutes
                            },
                            modifier = Modifier.height(52.dp),
                        ) { Text("Hoàn tác", color = Night.Mist) }
                        Button(
                            onClick = {
                                prefs.saveNight(enabled, start, end, idle)
                                Toast.makeText(context, "Đã lưu cài đặt ban đêm", Toast.LENGTH_SHORT).show()
                            },
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = Night.Green, contentColor = Night.Ink),
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) { Text("Lưu thay đổi") }
                    }
                }
            }
        }
    }

    when (pick) {
        "start" -> TimePickerDialog("Bắt đầu", start, { pick = null }) {
            start = it
            pick = null
        }
        "end" -> TimePickerDialog("Kết thúc", end, { pick = null }) {
            end = it
            pick = null
        }
    }
}
