package vn.quangns.timeforbed.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import vn.quangns.timeforbed.ui.theme.Night

/**
 * Chính sách quyền riêng tư hiển thị trong app (lần đầu mở app và trong Cài đặt).
 * Nội dung phải khớp với mã nguồn: app không xin quyền INTERNET, không có SDK bên thứ ba,
 * dịch vụ Trợ năng không đọc nội dung màn hình (canRetrieveWindowContent=false).
 */
const val PRIVACY_UPDATED = "03/11/2026"

const val PRIVACY_LEAD = "Time for Bed không thu thập, không gửi đi và không theo dõi bất kỳ dữ liệu nào của bạn."

val PRIVACY_POINTS = listOf(
    "Không kết nối Internet" to "App không xin quyền Internet nên không thể gửi dữ liệu ra ngoài. Không quảng cáo, không thống kê, không thư viện theo dõi của bên thứ ba.",
    "Không cần tài khoản" to "Không đăng nhập, không dùng vị trí, danh bạ, micro, camera hay ảnh.",
    "Trợ năng chỉ để biết bạn còn thức" to "App chỉ ghi nhận thời điểm bạn vừa chạm, cuộn hoặc gõ phím và tên app đang mở, để tự dừng nhạc/video khi bạn ngủ quên, rồi về màn hình chính và khóa máy. App không đọc nội dung màn hình, không ghi lại chữ hay phím bạn gõ.",
    "Truy cập thông báo chỉ để dừng trình phát" to "Quyền này chỉ giúp app tìm trình phát nhạc/video đang chạy để tạm dừng. App không đọc nội dung thông báo.",
    "Dữ liệu nằm trên máy bạn" to "Cài đặt, báo thức, danh sách app loại trừ và kết quả lần dừng gần nhất chỉ lưu trên điện thoại này; gỡ app là xóa hết. Nếu bạn bật sao lưu của Android, hệ thống có thể sao lưu vào tài khoản Google của chính bạn, nhà phát triển không truy cập được.",
    "Bạn luôn làm chủ" to "Có thể tắt Trợ năng, Truy cập thông báo hoặc gỡ app bất cứ lúc nào trong Cài đặt của Android.",
)

private const val FILE = "privacy"
private const val K_SEEN = "seen_v1"

/** Đã xem thông báo quyền riêng tư lần đầu chưa (lưu file riêng, không đụng Prefs chính). */
fun privacySeen(context: Context): Boolean = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(K_SEEN, false)

fun markPrivacySeen(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean(K_SEEN, true) }

/** Nội dung chính sách, dùng chung cho hộp thoại và ảnh chụp kiểm tra. */
@Composable
fun PrivacyContent(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(PRIVACY_LEAD, style = MaterialTheme.typography.bodyLarge, color = Night.Moonlight)
        PRIVACY_POINTS.forEach { (title, body) ->
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Night.Green)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = Night.Mist, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Text("Cập nhật: $PRIVACY_UPDATED", style = MaterialTheme.typography.bodySmall, color = Night.Mist)
    }
}

@Composable
fun PrivacyDialog(onDismiss: () -> Unit, confirmText: String = "Đã hiểu") {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Night.Surface,
        title = { Text("Quyền riêng tư") },
        text = { PrivacyContent(Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(confirmText, color = Night.Green, fontWeight = FontWeight.SemiBold) } },
    )
}
