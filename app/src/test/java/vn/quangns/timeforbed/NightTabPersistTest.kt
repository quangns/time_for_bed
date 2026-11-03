package vn.quangns.timeforbed

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import vn.quangns.timeforbed.ui.MainScreen
import vn.quangns.timeforbed.ui.markPrivacySeen
import vn.quangns.timeforbed.ui.theme.TimeForBedTheme

/** Đúng thao tác người dùng báo lỗi: đổi → Lưu → sang tab khác → quay lại Ban đêm phải thấy giá trị mới. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h860dp", application = android.app.Application::class)
class NightTabPersistTest {
    @get:Rule
    val compose = createComposeRule()
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Prefs.resetForTest()
        Prefs.get(ctx).saveNight(enabled = true, start = 0, end = 360, idleMinutes = 30)
        markPrivacySeen(ctx)
        val request = mutableIntStateOf(1)
        compose.setContent { TimeForBedTheme { MainScreen(tabRequest = request) } }
    }

    @Test
    fun savedValues_survive_tabSwitch() {
        compose.onNodeWithTag("idleDial").performScrollTo().performTouchInput {
            down(Offset(centerX, centerY + height * 0.38f)) // 60 phút
            up()
        }
        compose.onNode(isToggleable()).performClick() // tắt chế độ ban đêm
        compose.onNodeWithText("Lưu thay đổi").performClick()
        compose.waitForIdle()
        assertEquals(60, Prefs.get(ctx).idleMinutes)

        compose.onNodeWithText("Hẹn giờ").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ban đêm").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("60").performScrollTo().assertIsDisplayed()
        compose.onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun savedStartTime_survives_tabSwitch() {
        compose.onNodeWithText("00:00").performScrollTo().performClick() // mở hộp thoại giờ bắt đầu
        compose.waitForIdle()
        compose.onNodeWithContentDescription("22 hours").performClick() // chọn giờ 22 trên mặt đồng hồ
        compose.waitForIdle()
        compose.onNodeWithText("Xong").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("22:00").assertIsDisplayed() // bản nháp đã đổi
        compose.onNodeWithText("Lưu thay đổi").performClick()
        compose.waitForIdle()
        assertEquals(22 * 60, Prefs.get(ctx).nightStart)

        compose.onNodeWithText("Hẹn giờ").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ban đêm").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("22:00").performScrollTo().assertIsDisplayed()
    }
}
