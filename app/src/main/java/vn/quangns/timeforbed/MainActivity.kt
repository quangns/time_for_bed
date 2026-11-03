package vn.quangns.timeforbed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import vn.quangns.timeforbed.ui.MainScreen
import vn.quangns.timeforbed.ui.theme.TimeForBedTheme

class MainActivity : ComponentActivity() {

    /** Yêu cầu mở tab từ thông báo/intent; -1 = không có (MainScreen đặt về -1 sau khi đã mở). */
    private val tabRequest = mutableIntStateOf(NO_TAB)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Chỉ đọc intent lúc mở mới; xoay màn hình/đổi cấu hình thì giữ tab đang xem (rememberSaveable).
        if (savedInstanceState == null) tabRequest.intValue = intent?.getIntExtra(EXTRA_TAB, NO_TAB) ?: NO_TAB
        setContent {
            TimeForBedTheme {
                MainScreen(tabRequest = tabRequest)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra(EXTRA_TAB)) tabRequest.intValue = intent.getIntExtra(EXTRA_TAB, NO_TAB)
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_POMODORO = 3
        const val NO_TAB = -1
    }
}
