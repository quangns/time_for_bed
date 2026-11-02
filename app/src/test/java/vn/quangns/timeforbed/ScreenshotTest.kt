package vn.quangns.timeforbed

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.quangns.timeforbed.core.Prefs
import vn.quangns.timeforbed.ui.night.NightScreen
import vn.quangns.timeforbed.ui.settings.SettingsScreen
import vn.quangns.timeforbed.ui.theme.Night
import vn.quangns.timeforbed.ui.theme.TimeForBedTheme
import vn.quangns.timeforbed.ui.timer.TimerScreen
import java.io.File

/** Chụp ảnh từng màn để xem lại giao diện (ảnh ở app/build/screenshots), không so sánh tự động. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi", application = android.app.Application::class)
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val prefs get() = Prefs.get(ApplicationProvider.getApplicationContext())

    @Before
    fun reset() {
        prefs.timerDeadline = 0L
        prefs.lastTimerMinutes = 25
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            TimeForBedTheme { Surface(Modifier.fillMaxSize(), color = Night.Ink) { content() } }
        }
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun timerIdle() = shot("1-timer-idle") { TimerScreen() }

    @Test fun timerRunning() {
        prefs.timerDeadline = System.currentTimeMillis() + 17 * 60_000L + 42_000L
        shot("2-timer-running") { TimerScreen() }
    }

    @Test fun night() = shot("3-night") { NightScreen() }

    @Test fun settings() = shot("4-settings") { SettingsScreen(refreshKey = 0) }
}
