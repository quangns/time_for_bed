package vn.quangns.timeforbed.ui.pomodoro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import vn.quangns.timeforbed.pomodoro.Phase
import vn.quangns.timeforbed.pomodoro.Pomodoro
import vn.quangns.timeforbed.pomodoro.PomodoroSettings
import vn.quangns.timeforbed.ui.DialRing
import vn.quangns.timeforbed.ui.Hairline
import vn.quangns.timeforbed.ui.NightSwitch
import vn.quangns.timeforbed.ui.ScreenHeader
import vn.quangns.timeforbed.ui.SectionCard
import vn.quangns.timeforbed.ui.SettingRow
import vn.quangns.timeforbed.ui.theme.Night
import vn.quangns.timeforbed.ui.timer.formatCountdown

@Composable
fun PomodoroScreen() {
    val context = LocalContext.current
    val state by Pomodoro.state(context).collectAsStateWithLifecycle()
    val s = state ?: Pomodoro.current(context)

    // Đếm từng giây chỉ khi màn này đang hiện và đồng hồ đang chạy (tiết kiệm pin).
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, s.running) {
        if (!s.running) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1_000 - now % 1_000)
            }
        }
    }
    val remaining = s.remainingAt(now)
    // Tính lại theo ngày hiện tại để qua nửa đêm thì tự về 0 dù màn hình vẫn mở.
    val todayCount = s.todayCountOn(java.time.LocalDate.now().toString())

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ScreenHeader(
            "Pomodoro",
            subtitle = "Tập trung ${s.settings.focusMin} phút, nghỉ ${s.settings.shortMin} phút; " +
                "sau ${s.settings.longEvery} phiên nghỉ dài ${s.settings.longMin} phút.",
        )

        DialRing(
            value = (remaining / 60_000).toInt(),
            max = (s.totalMs / 60_000).toInt().coerceAtLeast(1),
            onValueChange = null,
            description = "${s.phase.label}, còn ${formatCountdown(remaining)}",
            fraction = (remaining.toFloat() / s.totalMs).coerceIn(0f, 1f),
            maxWidth = 280.dp,
            centerRatio = 0.7f,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.phase.label, style = MaterialTheme.typography.bodyLarge, color = Night.Mist)
                Text(formatCountdown(remaining), style = MaterialTheme.typography.displayMedium, color = Night.Moonlight)
                CycleDots(done = s.doneInCycle, total = s.settings.longEvery, focusRunning = s.phase == Phase.FOCUS)
            }
        }

        // Nút điều khiển theo trạng thái.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val green = ButtonDefaults.buttonColors(containerColor = Night.Green, contentColor = Night.Ink)
            when {
                s.running -> {
                    OutlinedButton(
                        onClick = { Pomodoro.pause(context) },
                        border = BorderStroke(1.dp, Night.Hairline),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("Tạm dừng", color = Night.Moonlight) }
                    TextButton(onClick = { Pomodoro.skip(context) }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text("Bỏ qua", color = Night.Mist)
                    }
                }
                s.fresh -> Button(
                    onClick = { Pomodoro.start(context) },
                    colors = green,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(if (s.phase == Phase.FOCUS) "Bắt đầu tập trung" else "Bắt đầu nghỉ") }
                else -> {
                    Button(
                        onClick = { Pomodoro.start(context) },
                        colors = green,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("Tiếp tục") }
                    TextButton(onClick = { Pomodoro.reset(context) }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text("Đặt lại", color = Night.Ember)
                    }
                }
            }
        }

        Text(
            if (todayCount == 0) "Hôm nay chưa xong phiên tập trung nào."
            else "Hôm nay: $todayCount phiên tập trung.",
            style = MaterialTheme.typography.bodySmall,
            color = Night.Mist,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        SettingsCard(s.settings) { Pomodoro.updateSettings(context, it) }
    }
}

/** Chấm tiến độ chu kỳ: xanh = đã xong, viền = chưa. */
@Composable
private fun CycleDots(done: Int, total: Int, focusRunning: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
        repeat(total) { i ->
            val filled = i < done
            val current = focusRunning && i == done
            Box(
                Modifier.size(8.dp).background(
                    when {
                        filled -> Night.Green
                        current -> Night.Green.copy(alpha = 0.4f)
                        else -> Night.Hairline
                    },
                    CircleShape,
                ),
            )
        }
    }
}

/** Thời lượng: chọn bằng chip, áp dụng ngay (chip đang chọn luôn hiện rõ). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsCard(settings: PomodoroSettings, onChange: (PomodoroSettings) -> Unit) {
    SectionCard("Thời lượng", subtitle = "Đổi khi đang chạy thì áp dụng từ giai đoạn sau.") {
        ChipRow("Tập trung", listOf(15, 20, 25, 30, 45, 50), settings.focusMin, "phút") { onChange(settings.copy(focusMin = it)) }
        Hairline()
        ChipRow("Nghỉ ngắn", listOf(3, 5, 10), settings.shortMin, "phút") { onChange(settings.copy(shortMin = it)) }
        Hairline()
        ChipRow("Nghỉ dài", listOf(10, 15, 20, 30), settings.longMin, "phút") { onChange(settings.copy(longMin = it)) }
        Hairline()
        ChipRow("Nghỉ dài sau", listOf(2, 3, 4, 5), settings.longEvery, "phiên") { onChange(settings.copy(longEvery = it)) }
        Hairline()
        SettingRow("Tự chuyển giai đoạn", description = "Hết giờ tập trung tự bắt đầu nghỉ và ngược lại") {
            NightSwitch(checked = settings.autoStart, onCheckedChange = { onChange(settings.copy(autoStart = it)) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(title: String, options: List<Int>, selected: Int, unit: String, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            options.forEach { v ->
                FilterChip(
                    selected = v == selected,
                    onClick = { onSelect(v) },
                    label = { Text("$v $unit") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Night.Green,
                        selectedLabelColor = Night.Ink,
                        labelColor = Night.Moonlight,
                    ),
                )
            }
        }
    }
}
