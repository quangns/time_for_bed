package vn.quangns.timeforbed.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.quangns.timeforbed.core.PermissionItem
import vn.quangns.timeforbed.core.Permissions
import vn.quangns.timeforbed.ui.alarm.AlarmScreen
import vn.quangns.timeforbed.MainActivity
import vn.quangns.timeforbed.alarm.AlarmRingService
import vn.quangns.timeforbed.ui.night.NightScreen
import vn.quangns.timeforbed.ui.pomodoro.PomodoroScreen
import vn.quangns.timeforbed.ui.settings.SettingsScreen
import vn.quangns.timeforbed.ui.theme.Night
import vn.quangns.timeforbed.ui.timer.TimerScreen

private data class Tab(val title: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Hẹn giờ", Icons.Filled.Bedtime),
    Tab("Ban đêm", Icons.Filled.NightsStay),
    Tab("Báo thức", Icons.Filled.Alarm),
    Tab("Pomodoro", Icons.Filled.Timer),
    Tab("Cài đặt", Icons.Filled.Settings),
)
const val TAB_SETTINGS = 4

@Composable
fun MainScreen(tabRequest: MutableIntState) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(tabRequest.intValue.coerceAtLeast(0)) }
    // Mở tab theo yêu cầu rồi "tiêu thụ" yêu cầu (-1), để lần bấm thông báo sau vẫn mở được dù cùng tab.
    LaunchedEffect(tabRequest.intValue) {
        val requested = tabRequest.intValue
        if (requested >= 0) {
            tab = requested
            tabRequest.intValue = MainActivity.NO_TAB
        }
    }

    // Trạng thái quyền, làm mới mỗi khi quay lại app (sau khi người dùng bật trong Cài đặt).
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val missing = remember(refresh) {
        Permissions.applicable().filterNot { Permissions.isGranted(context, it) }
    }

    // Lần đầu mở app: hiện chính sách quyền riêng tư trước, rồi mới hỏi quyền thông báo.
    var privacyOk by remember { mutableStateOf(privacySeen(context)) }
    if (!privacyOk) {
        PrivacyDialog(onDismiss = {
            markPrivacySeen(context)
            privacyOk = true
        })
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    var askedNotif by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(privacyOk) {
        if (privacyOk && !askedNotif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !Permissions.isGranted(context, PermissionItem.POST_NOTIFICATIONS)
        ) {
            askedNotif = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Night.Surface, tonalElevation = 0.dp) {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.title, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Night.Green,
                            selectedTextColor = Night.Moonlight,
                            indicatorColor = Night.Green.copy(alpha = 0.16f),
                            unselectedIconColor = Night.Mist,
                            unselectedTextColor = Night.Mist,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Luôn có cách tắt chuông ngay trong app, kể cả khi thông báo bị chặn.
            val ringing by AlarmRingService.ringing.collectAsStateWithLifecycle()
            ringing?.let { RingingBanner(it.snoozeMinutes) }
            if (missing.isNotEmpty() && tab != TAB_SETTINGS) {
                MissingPermissionBanner(missing.size) { tab = TAB_SETTINGS }
            }
            when (tab) {
                0 -> TimerScreen()
                1 -> NightScreen(refreshKey = refresh)
                2 -> AlarmScreen()
                MainActivity.TAB_POMODORO -> PomodoroScreen()
                else -> SettingsScreen(refreshKey = refresh)
            }
        }
    }
}

@Composable
private fun MissingPermissionBanner(count: Int, onClick: () -> Unit) {
    Surface(
        color = Night.Surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, Night.Hairline),
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(ok = false)
            Spacer(Modifier.width(12.dp))
            Text(
                "Còn $count quyền chưa cấp, app có thể chưa tắt được video, nhạc.",
                color = Night.Moonlight,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text("Cấp quyền", color = Night.Green, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun RingingBanner(snoozeMinutes: Int) {
    val context = LocalContext.current
    Surface(
        color = Night.Green,
        contentColor = Night.Ink,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Báo thức đang reo", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { AlarmRingService.send(context, AlarmRingService.ACTION_SNOOZE) }) {
                Text("Báo lại $snoozeMinutes′", color = Night.Ink)
            }
            TextButton(onClick = { AlarmRingService.send(context, AlarmRingService.ACTION_DISMISS) }) {
                Text("Tắt", color = Night.Ink, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
