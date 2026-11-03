package vn.quangns.timeforbed.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vn.quangns.timeforbed.ui.theme.TimeForBedTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Màn hình báo thức hiện trên màn hình khóa. */
class AlarmRingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Mở từ thông báo dự phòng (dịch vụ chuông bị hệ thống chặn lúc báo thức kích hoạt):
        // màn hình đang hiện nên được phép bật dịch vụ chuông ngay bây giờ.
        val id = intent?.getIntExtra(AlarmReceiver.EXTRA_ID, -1) ?: -1
        if (id >= 0 && !AlarmRingService.isRinging && savedInstanceState == null) {
            runCatching {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, AlarmRingService::class.java).setAction(AlarmRingService.ACTION_RING).putExtra(AlarmReceiver.EXTRA_ID, id),
                )
            }
        }

        setContent {
            TimeForBedTheme {
                val alarm by AlarmRingService.ringing.collectAsStateWithLifecycle()
                var everRang by remember { mutableStateOf(false) }
                LaunchedEffect(alarm) {
                    if (alarm != null) everRang = true
                    // Chuông đã dừng (bấm từ thông báo / tự hết giờ) thì đóng màn hình.
                    else if (everRang) finish()
                }
                RingScreen(
                    label = alarm?.label.orEmpty(),
                    snoozeMinutes = alarm?.snoozeMinutes ?: 5,
                    onSnooze = {
                        AlarmRingService.send(this, AlarmRingService.ACTION_SNOOZE)
                        finish()
                    },
                    onDismiss = {
                        AlarmRingService.send(this, AlarmRingService.ACTION_DISMISS)
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun RingScreen(label: String, snoozeMinutes: Int, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1_000)
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Alarm, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 80.sp)
            Text(label.ifBlank { "Đến giờ dậy rồi!" }, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(64.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onSnooze, modifier = Modifier.height(64.dp)) {
                    Text("Báo lại $snoozeMinutes phút", fontSize = 18.sp)
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("Tắt", fontSize = 18.sp)
                }
            }
        }
    }
}
