package vn.quangns.timeforbed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import vn.quangns.timeforbed.ui.MainScreen
import vn.quangns.timeforbed.ui.theme.TimeForBedTheme

class MainActivity : ComponentActivity() {

    private val initialTab = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initialTab.intValue = intent?.getIntExtra(EXTRA_TAB, 0) ?: 0
        setContent {
            TimeForBedTheme {
                MainScreen(tabRequest = initialTab)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.hasExtra(EXTRA_TAB)) initialTab.intValue = intent.getIntExtra(EXTRA_TAB, 0)
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_POMODORO = 3
    }
}
