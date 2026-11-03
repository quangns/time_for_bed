package vn.quangns.timeforbed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import vn.quangns.timeforbed.ui.alarm.WheelPicker
import vn.quangns.timeforbed.ui.theme.TimeForBedTheme

/** Nút "+15 phút" đổi giá trị từ bên ngoài: bánh xe phải tới đúng số mới, không lưu nhầm số lướt qua. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WheelPickerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun externalJump_endsOnTarget_withoutIntermediateValues() {
        var minute by mutableIntStateOf(10)
        val reported = mutableListOf<Int>()
        compose.setContent {
            TimeForBedTheme {
                WheelPicker(count = 60, value = minute, onValueChange = { reported += it; minute = it }, contentDescription = "Phút")
            }
        }
        compose.waitForIdle()
        reported.clear()

        minute = 25 // như bấm "+15 phút"
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()

        assertEquals(25, minute)
        assertEquals("không được báo các số lướt qua", emptyList<Int>(), reported.filter { it != 25 })
    }
}
