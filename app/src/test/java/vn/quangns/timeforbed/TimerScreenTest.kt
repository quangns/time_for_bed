package vn.quangns.timeforbed

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.quangns.timeforbed.core.Prefs
import vn.quangns.timeforbed.ui.theme.TimeForBedTheme
import vn.quangns.timeforbed.ui.timer.TimerScreen

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TimerScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun reset() {
        val prefs = Prefs.get(ApplicationProvider.getApplicationContext())
        prefs.timerDeadline = 0L
        prefs.lastTimerMinutes = 30
    }

    @Test
    fun clickStart_showsCountdown() {
        compose.setContent { TimeForBedTheme { TimerScreen() } }
        compose.onNodeWithText("Bắt đầu").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("còn lại").assertIsDisplayed()
        compose.onNodeWithText("30:00", substring = false).assertIsDisplayed()
    }
}
