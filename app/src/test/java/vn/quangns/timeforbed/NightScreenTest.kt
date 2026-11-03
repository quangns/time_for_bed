package vn.quangns.timeforbed

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.quangns.timeforbed.core.Prefs
import vn.quangns.timeforbed.ui.night.NightScreen
import vn.quangns.timeforbed.ui.theme.TimeForBedTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h860dp", application = android.app.Application::class)
class NightScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val prefs get() = Prefs.get(ctx)
    private val raw get() = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    @Before
    fun reset() {
        Prefs.resetForTest()
        prefs.saveNight(enabled = true, start = 0, end = 360, idleMinutes = 30)
        compose.setContent { TimeForBedTheme { NightScreen() } }
    }

    @Test
    fun changes_areDraft_untilSave() {
        compose.onNodeWithText("Đã lưu").performScrollTo().assertIsNotEnabled()

        compose.onNode(isToggleable()).performClick() // tắt chế độ ban đêm
        assertEquals(true, prefs.nightEnabled) // chưa lưu

        compose.onNodeWithText("Lưu thay đổi").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(false, prefs.nightEnabled)
        assertEquals(false, raw.getBoolean("night_enabled", true)) // đã ghi xuống bộ nhớ
        compose.onNodeWithText("Đã lưu").assertIsDisplayed()
    }

    @Test
    fun dragDial_toBottom_is60Minutes_andSaves() {
        compose.onNodeWithTag("idleDial").performScrollTo().performTouchInput {
            // Đáy vòng = nửa vòng = 60 phút trên thang 120.
            down(Offset(centerX, centerY + height * 0.38f))
            up()
        }
        compose.onNodeWithText("60").assertIsDisplayed()
        assertEquals(30, prefs.idleMinutes) // chưa lưu

        compose.onNodeWithText("Lưu thay đổi").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(60, prefs.idleMinutes)
        assertEquals(60, raw.getInt("idle_minutes", 0))
    }

    @Test
    fun undo_restoresSavedValues() {
        compose.onNodeWithTag("idleDial").performScrollTo().performTouchInput {
            down(Offset(centerX + width * 0.38f, centerY)) // 1/4 vòng = 30… chạm bên trái = 90
            up()
        }
        compose.onNodeWithTag("idleDial").performTouchInput {
            down(Offset(centerX - width * 0.38f, centerY))
            up()
        }
        compose.onNodeWithText("90").assertIsDisplayed()
        compose.onNodeWithText("Hoàn tác").performScrollTo().performClick()
        compose.onNodeWithText("30").assertIsDisplayed()
        assertEquals(30, prefs.idleMinutes)
    }
}
