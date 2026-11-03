package vn.quangns.timeforbed

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
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

/**
 * Trên điện thoại thật (màn cỡ 360×640dp sau khi trừ thanh trạng thái/điều hướng), người dùng kéo vòng
 * rồi phải thấy và bấm được nút Lưu NGAY, không cần cuộn.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp", application = android.app.Application::class)
class NightSaveVisibleTest {
    @get:Rule
    val compose = createComposeRule()

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun reset() {
        Prefs.resetForTest()
        Prefs.get(ctx).saveNight(enabled = true, start = 0, end = 360, idleMinutes = 30)
        compose.setContent { TimeForBedTheme { NightScreen() } }
    }

    @Test
    fun saveButton_visibleWithoutScrolling_afterDragging() {
        // Như người dùng: cuộn tới vòng tròn rồi kéo.
        compose.onNodeWithTag("idleDial").performScrollTo().performTouchInput {
            down(Offset(centerX, centerY + height * 0.38f)) // đáy vòng = 60 phút
            up()
        }
        compose.onNodeWithText("Lưu thay đổi").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(60, Prefs.get(ctx).idleMinutes)
    }
}
