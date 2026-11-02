package vn.quangns.timeforbed.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.quangns.timeforbed.core.AppInfo
import vn.quangns.timeforbed.core.Exclusions
import vn.quangns.timeforbed.core.PermissionItem
import vn.quangns.timeforbed.core.Permissions
import vn.quangns.timeforbed.core.Prefs
import vn.quangns.timeforbed.ui.Hairline
import vn.quangns.timeforbed.ui.ScreenHeader
import vn.quangns.timeforbed.ui.SectionCard
import vn.quangns.timeforbed.ui.StatusDot
import vn.quangns.timeforbed.ui.theme.Night

@Composable
fun SettingsScreen(refreshKey: Int) {
    val context = LocalContext.current
    val prefs = Prefs.get(context)
    val s by prefs.state.collectAsStateWithLifecycle()
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { Exclusions.launchableApps(context) } }

    val filtered = apps.orEmpty().filter {
        query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true)
    }.sortedByDescending { it.packageName in s.excluded || Exclusions.isClockApp(it.packageName) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { ScreenHeader("Cài đặt", "Quyền hệ thống và danh sách app không bao giờ bị tắt.") }
        item {
            SectionCard("Quyền cần cấp", subtitle = "Bật lần lượt từng mục rồi quay lại app để cập nhật.") {
                Permissions.applicable().forEachIndexed { i, p ->
                    if (i > 0) Hairline()
                    PermissionRow(p, granted = remember(refreshKey) { Permissions.isGranted(context, p) }) {
                        openSafely(context, Permissions.settingsIntent(context, p))
                    }
                }
            }
        }
        item {
            SectionCard("Không bật được Trợ năng / Truy cập thông báo?") {
                Text(
                    "Android 13+ chặn các quyền này với app cài ngoài CH Play. Vào Thông tin ứng dụng → " +
                        "menu ⋮ góc trên → \"Cho phép cài đặt bị hạn chế\", rồi bật lại.\n\n" +
                        "Xiaomi/Oppo/Vivo/Realme: bật \"Tự khởi chạy\", đặt Pin = \"Không hạn chế\" và " +
                        "khóa app trong màn hình đa nhiệm để app không bị tắt qua đêm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Night.Mist,
                )
                TextButton(onClick = { openSafely(context, Permissions.appDetailsIntent(context)) }) {
                    Text("Mở Thông tin ứng dụng", color = Night.Green)
                }
            }
        }
        item {
            SectionCard(
                "Không bao giờ tắt",
                subtitle = "App đồng hồ/báo thức luôn được bỏ qua. Tick thêm app ngân hàng, ví điện tử…",
            ) {
                Row {
                    OutlinedButton(
                        onClick = {
                            val added = Exclusions.suggestFinance(context)
                            prefs.excluded = prefs.excluded + added
                            Toast.makeText(context, "Đã thêm ${added.size} app gợi ý", Toast.LENGTH_SHORT).show()
                        },
                        border = BorderStroke(1.dp, Night.Hairline),
                        shape = MaterialTheme.shapes.medium,
                    ) { Text("Gợi ý app ngân hàng", color = Night.Moonlight) }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Tìm app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                if (apps == null) Text("Đang tải danh sách app…", Modifier.padding(top = 8.dp))
            }
        }
        items(filtered, key = { it.packageName }) { app ->
            val clock = Exclusions.isClockApp(app.packageName)
            val checked = clock || app.packageName in s.excluded
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !clock) {
                    prefs.excluded = if (checked) prefs.excluded - app.packageName else prefs.excluded + app.packageName
                }.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                    enabled = !clock,
                    colors = CheckboxDefaults.colors(
                        checkedColor = Night.Green,
                        checkmarkColor = Night.Ink,
                        uncheckedColor = Night.Mist,
                        disabledCheckedColor = Night.Green.copy(alpha = 0.4f),
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(app.label + if (clock) " (báo thức)" else "", style = MaterialTheme.typography.bodyLarge)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = Night.Mist)
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(item: PermissionItem, granted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !granted, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(ok = granted)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge)
            Text(item.why, style = MaterialTheme.typography.bodySmall, color = Night.Mist)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (granted) "Đã bật" else "Bật",
            style = MaterialTheme.typography.labelLarge,
            color = if (granted) Night.Mist else Night.Green,
        )
    }
}

private fun openSafely(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Permissions.appDetailsIntent(context))
    }
}
