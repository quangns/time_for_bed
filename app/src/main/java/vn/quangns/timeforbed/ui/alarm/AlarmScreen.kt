package vn.quangns.timeforbed.ui.alarm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.quangns.timeforbed.alarm.AlarmEntity
import vn.quangns.timeforbed.alarm.AlarmScheduler
import vn.quangns.timeforbed.alarm.AppDatabase
import vn.quangns.timeforbed.alarm.DAY_PRESETS
import vn.quangns.timeforbed.alarm.DAY_SHORT
import vn.quangns.timeforbed.alarm.daysText
import vn.quangns.timeforbed.alarm.untilText
import vn.quangns.timeforbed.ui.Hairline
import vn.quangns.timeforbed.ui.NightSwitch
import vn.quangns.timeforbed.ui.ScreenHeader
import vn.quangns.timeforbed.ui.SectionCard
import vn.quangns.timeforbed.ui.SettingRow
import vn.quangns.timeforbed.ui.theme.Night
import java.time.LocalTime

/** Giờ hiện tại (ms), cập nhật mỗi 30 giây để dòng "reo sau …" luôn đúng khi màn đang mở. */
@Composable
private fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
fun AlarmScreen() {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).alarmDao() }
    val alarms by dao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<AlarmEntity?>(null) }
    val now = rememberNow()

    fun save(alarm: AlarmEntity, announce: Boolean = true) = scope.launch {
        val saved = withContext(Dispatchers.IO) {
            val id = dao.upsert(alarm)
            if (alarm.id == 0) alarm.copy(id = id.toInt()) else alarm
        }
        AlarmScheduler.schedule(context, saved)
        if (saved.enabled && announce) {
            val left = AlarmScheduler.nextTriggerMillis(saved) - System.currentTimeMillis()
            Toast.makeText(context, "Chuông sẽ reo sau ${untilText(left)}", Toast.LENGTH_SHORT).show()
        }
    }

    /** Xóa ngay, cho phép hoàn tác bằng thanh thông báo. */
    fun delete(alarm: AlarmEntity) = scope.launch {
        AlarmScheduler.cancel(context, alarm.id)
        withContext(Dispatchers.IO) { dao.delete(alarm) }
        val res = snackbar.showSnackbar(
            "Đã xóa báo thức %02d:%02d".format(alarm.hour, alarm.minute),
            actionLabel = "Hoàn tác",
            duration = SnackbarDuration.Short,
        )
        if (res == SnackbarResult.ActionPerformed) save(alarm, announce = false)
    }

    val next = alarms.filter { it.enabled }.minByOrNull { AlarmScheduler.nextTriggerMillis(it, now) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader(
                    "Báo thức",
                    subtitle = if (next != null) {
                        "Chuông kế tiếp %02d:%02d, reo sau %s".format(
                            next.hour, next.minute, untilText(AlarmScheduler.nextTriggerMillis(next, now) - now),
                        )
                    } else {
                        "Chuông reo cả khi máy đang khóa. Hẹn giờ ngủ không bao giờ tắt báo thức."
                    },
                )
            }
            if (alarms.isEmpty()) {
                item { EmptyAlarms() }
            }
            items(alarms, key = { it.id }) { alarm ->
                SwipeToDelete(onDelete = { delete(alarm) }) {
                    AlarmRow(
                        alarm = alarm,
                        untilMs = AlarmScheduler.nextTriggerMillis(alarm, now) - now,
                        onToggle = { save(alarm.copy(enabled = it)) },
                        onClick = { editing = alarm },
                        onDelete = { delete(alarm) },
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp)) {
            Snackbar(it, containerColor = Night.Raised, contentColor = Night.Moonlight, actionColor = Night.Green)
        }
        ExtendedFloatingActionButton(
            onClick = { editing = AlarmEntity(hour = 6, minute = 0) },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("Thêm báo thức") },
            containerColor = Night.Green,
            contentColor = Night.Ink,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    editing?.let { current ->
        Dialog(
            onDismissRequest = { editing = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            AlarmEditor(
                initial = current,
                onDismiss = { editing = null },
                onSave = {
                    save(it.copy(enabled = true))
                    editing = null
                },
                onDelete = if (current.id != 0) {
                    {
                        delete(current)
                        editing = null
                    }
                } else null,
            )
        }
    }
}

/** Vuốt hàng sang trái để xóa (nền Ember + biểu tượng thùng rác lộ ra phía sau). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) onDelete()
            it == SwipeToDismissBoxValue.EndToStart
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Night.Ember.copy(alpha = 0.18f), MaterialTheme.shapes.large)
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Xóa", color = Night.Ember, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.Delete, null, tint = Night.Ember)
                }
            }
        },
    ) { content() }
}

@Composable
internal fun AlarmRow(
    alarm: AlarmEntity,
    untilMs: Long,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Night.Surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, Night.Hairline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "%02d:%02d".format(alarm.hour, alarm.minute),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (alarm.enabled) Night.Moonlight else Night.Mist.copy(alpha = 0.6f),
                )
                Text(
                    listOf(alarm.label, alarm.daysText()).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Night.Mist,
                )
                if (alarm.enabled) {
                    Text("Reo sau ${untilText(untilMs)}", style = MaterialTheme.typography.bodySmall, color = Night.Green)
                }
            }
            NightSwitch(checked = alarm.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete, modifier = Modifier.semantics { contentDescription = "Xóa báo thức %02d:%02d".format(alarm.hour, alarm.minute) }) {
                Icon(Icons.Outlined.Delete, null, tint = Night.Ember)
            }
        }
    }
}

/** Trạng thái rỗng: biểu tượng nét mảnh + hướng dẫn cách thêm. */
@Composable
private fun EmptyAlarms() {
    Column(
        Modifier.fillMaxWidth().padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Alarm, null, tint = Night.Mist, modifier = Modifier.size(48.dp))
        Text(
            "Chưa có báo thức",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            "Chạm \"Thêm báo thức\" ở góc dưới để đặt giờ dậy.",
            style = MaterialTheme.typography.bodySmall,
            color = Night.Mist,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Màn đặt báo thức toàn màn hình: bánh xe giờ/phút, nút cộng nhanh, chọn ngày lặp, tùy chọn chuông.
 * Tách khỏi Dialog để chụp ảnh kiểm tra giao diện được.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AlarmEditor(
    initial: AlarmEntity,
    onDismiss: () -> Unit,
    onSave: (AlarmEntity) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    var days by remember { mutableIntStateOf(initial.daysMask) }
    var label by remember { mutableStateOf(initial.label) }
    var vibrate by remember { mutableStateOf(initial.vibrate) }
    var snooze by remember { mutableIntStateOf(initial.snoozeMinutes) }
    var ringtone by remember { mutableStateOf(initial.ringtoneUri) }
    val now = rememberNow()

    val draft = initial.copy(
        hour = hour, minute = minute, daysMask = days, label = label.trim(),
        vibrate = vibrate, snoozeMinutes = snooze, ringtoneUri = ringtone, enabled = true,
    )
    val untilMs = AlarmScheduler.nextTriggerMillis(draft, now) - now

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            ringtone = uri?.toString()
        }
    }

    Surface(color = Night.Ink, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Hủy", color = Night.Mist) }
                Text(
                    if (initial.id == 0) "Báo thức mới" else "Sửa báo thức",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Button(
                    onClick = { onSave(draft) },
                    colors = ButtonDefaults.buttonColors(containerColor = Night.Green, contentColor = Night.Ink),
                ) { Text("Lưu", fontWeight = FontWeight.SemiBold) }
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                // Bánh xe giờ : phút
                Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WheelPicker(count = 24, value = hour, onValueChange = { hour = it }, contentDescription = "Giờ")
                        Text(":", style = MaterialTheme.typography.displaySmall, color = Night.Moonlight, modifier = Modifier.padding(horizontal = 12.dp))
                        WheelPicker(count = 60, value = minute, onValueChange = { minute = it }, contentDescription = "Phút")
                    }
                    Text(
                        "Chuông reo sau ${untilText(untilMs)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Night.Green,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        listOf(15 to "+15 phút", 30 to "+30 phút", 60 to "+1 giờ", 480 to "+8 giờ").forEach { (m, text) ->
                            OutlinedButton(
                                onClick = {
                                    val t = LocalTime.now().plusMinutes(m.toLong())
                                    hour = t.hour
                                    minute = t.minute
                                },
                                border = BorderStroke(1.dp, Night.Hairline),
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text(text, color = Night.Moonlight) }
                        }
                    }
                }

                SectionCard("Lặp lại", subtitle = if (days == 0) "Chỉ reo một lần rồi tự tắt." else draft.daysText()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DAY_SHORT.forEachIndexed { i, d ->
                            DayToggle(d, selected = days and (1 shl i) != 0, onClick = { days = days xor (1 shl i) })
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                        DAY_PRESETS.forEach { (name, mask) ->
                            FilterChip(
                                selected = days == mask,
                                onClick = { days = mask },
                                label = { Text(name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Night.Green.copy(alpha = 0.16f),
                                    selectedLabelColor = Night.Green,
                                ),
                            )
                        }
                    }
                }

                SectionCard("Tùy chọn") {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it.take(40) },
                        label = { Text("Nhãn (không bắt buộc)") },
                        placeholder = { Text("VD: Đi làm") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                    SettingRow(
                        "Chuông",
                        description = ringtoneTitle(context, ringtone),
                        modifier = Modifier.clickable(role = Role.Button) { picker.launch(ringtonePickerIntent(ringtone)) },
                    ) { Text("Đổi", color = Night.Green, style = MaterialTheme.typography.labelLarge) }
                    Hairline()
                    SettingRow("Rung") { NightSwitch(checked = vibrate, onCheckedChange = { vibrate = it }) }
                    Hairline()
                    SettingRow("Báo lại sau", description = "Khi bấm \"Báo lại\" lúc chuông reo") {}
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15).forEach { m ->
                            FilterChip(
                                selected = snooze == m,
                                onClick = { snooze = m },
                                label = { Text("$m phút") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Night.Green.copy(alpha = 0.16f),
                                    selectedLabelColor = Night.Green,
                                ),
                            )
                        }
                    }
                }

                if (onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        border = BorderStroke(1.dp, Night.Ember),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Icon(Icons.Outlined.Delete, null, tint = Night.Ember)
                        Spacer(Modifier.width(8.dp))
                        Text("Xóa báo thức", color = Night.Ember)
                    }
                }
                Spacer(Modifier.size(8.dp))
            }
        }
    }
}

/** Nút tròn chọn một ngày trong tuần; vùng chạm 48dp. */
@Composable
private fun DayToggle(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .background(if (selected) Night.Green else Night.Raised, CircleShape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { contentDescription = "$text ${if (selected) "đã chọn" else "chưa chọn"}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Night.Ink else Night.Moonlight,
        )
    }
}

private fun ringtonePickerIntent(current: String?) = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Chọn chuông báo thức")
    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current?.let(Uri::parse))

private fun ringtoneTitle(context: Context, uri: String?): String {
    if (uri == null) return "Mặc định"
    return runCatching { RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context) }
        .getOrNull() ?: "Tùy chọn"
}
