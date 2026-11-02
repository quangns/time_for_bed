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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.quangns.timeforbed.alarm.AlarmEntity
import vn.quangns.timeforbed.alarm.AlarmScheduler
import vn.quangns.timeforbed.alarm.AppDatabase
import vn.quangns.timeforbed.alarm.DAY_SHORT
import vn.quangns.timeforbed.alarm.daysText
import vn.quangns.timeforbed.ui.formatDuration

@Composable
fun AlarmScreen() {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).alarmDao() }
    val alarms by dao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<AlarmEntity?>(null) }

    fun save(alarm: AlarmEntity) = scope.launch {
        val saved = withContext(Dispatchers.IO) {
            val id = dao.upsert(alarm)
            if (alarm.id == 0) alarm.copy(id = id.toInt()) else alarm
        }
        AlarmScheduler.schedule(context, saved)
        if (saved.enabled) {
            val left = AlarmScheduler.nextTriggerMillis(saved) - System.currentTimeMillis()
            Toast.makeText(context, "Báo thức sau ${formatDuration(left)}", Toast.LENGTH_SHORT).show()
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (alarms.isEmpty()) {
            Text(
                "Chưa có báo thức nào",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(alarms, key = { it.id }) { alarm ->
                AlarmRow(
                    alarm = alarm,
                    onToggle = { save(alarm.copy(enabled = it)) },
                    onClick = { editing = alarm },
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = AlarmEntity(hour = 6, minute = 0) },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("Thêm báo thức") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    editing?.let { current ->
        AlarmEditor(
            initial = current,
            onDismiss = { editing = null },
            onSave = {
                save(it.copy(enabled = true))
                editing = null
            },
            onDelete = if (current.id != 0) {
                {
                    scope.launch {
                        AlarmScheduler.cancel(context, current.id)
                        withContext(Dispatchers.IO) { dao.delete(current) }
                    }
                    editing = null
                }
            } else null,
        )
    }
}

@Composable
private fun AlarmRow(alarm: AlarmEntity, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "%02d:%02d".format(alarm.hour, alarm.minute),
                    fontSize = 40.sp,
                    color = if (alarm.enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    listOf(alarm.label, alarm.daysText()).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AlarmEditor(
    initial: AlarmEntity,
    onDismiss: () -> Unit,
    onSave: (AlarmEntity) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    val time = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    var days by remember { mutableIntStateOf(initial.daysMask) }
    var label by remember { mutableStateOf(initial.label) }
    var vibrate by remember { mutableStateOf(initial.vibrate) }
    var snooze by remember { mutableIntStateOf(initial.snoozeMinutes) }
    var ringtone by remember { mutableStateOf(initial.ringtoneUri) }

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (initial.id == 0) "Báo thức mới" else "Sửa báo thức", Modifier.weight(1f))
                if (onDelete != null) {
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Xóa") }
                }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeInput(state = time)
                Text("Lặp lại", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_SHORT.forEachIndexed { i, d ->
                        FilterChip(
                            selected = days and (1 shl i) != 0,
                            onClick = { days = days xor (1 shl i) },
                            label = { Text(d) },
                        )
                    }
                }
                Text(
                    if (days == 0) "Không chọn ngày: báo một lần" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(40) },
                    label = { Text("Nhãn") },
                    singleLine = true,
                )
                OutlinedButton(onClick = { picker.launch(ringtonePickerIntent(ringtone)) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Chuông: " + ringtoneTitle(context, ringtone))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Rung", Modifier.weight(1f))
                    Switch(checked = vibrate, onCheckedChange = { vibrate = it })
                }
                Text("Báo lại sau", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 10, 15).forEach { m ->
                        FilterChip(selected = snooze == m, onClick = { snooze = m }, label = { Text("$m phút") })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    initial.copy(
                        hour = time.hour, minute = time.minute, daysMask = days, label = label.trim(),
                        vibrate = vibrate, snoozeMinutes = snooze, ringtoneUri = ringtone,
                    )
                )
            }) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
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
