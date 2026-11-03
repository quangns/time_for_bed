package vn.quangns.timeforbed

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.quangns.timeforbed.ui.timer.DialMath
import vn.quangns.timeforbed.ui.timer.formatCountdown

class DialMathTest {

    @Test
    fun quarterPositions() {
        // y hướng xuống: phải = 15 phút, dưới = 30, trái = 45.
        assertEquals(15, DialMath.minutesAt(100f, 0f, previous = 10))
        assertEquals(30, DialMath.minutesAt(0f, 100f, previous = 30))
        assertEquals(45, DialMath.minutesAt(-100f, 0f, previous = 40))
    }

    @Test
    fun dragPastTop_clampsInsteadOfWrapping() {
        // Đang 58 phút, kéo lố qua đỉnh sang phải một chút -> giữ 60.
        assertEquals(60, DialMath.minutesAt(10f, -100f, previous = 58))
        // Đang 2 phút, kéo ngược qua đỉnh sang trái -> giữ 0.
        assertEquals(0, DialMath.minutesAt(-10f, -100f, previous = 2))
    }

    @Test
    fun fractionOfHour() {
        assertEquals(0.5f, DialMath.fraction(30 * 60_000L), 0.0001f)
        assertEquals(1f, DialMath.fraction(90 * 60_000L), 0.0001f)
        assertEquals(0f, DialMath.fraction(-5L), 0.0001f)
    }

    @Test
    fun countdownFormat() {
        assertEquals("30:00", formatCountdown(30 * 60_000L))
        assertEquals("00:01", formatCountdown(400L))
        assertEquals("00:59", formatCountdown(59_000L))
        assertEquals("1:05:09", formatCountdown((3600 + 5 * 60 + 9) * 1000L))
    }

    @Test
    fun snapToStep() {
        assertEquals(60, DialMath.snap(62, 5, 5, 120))
        assertEquals(65, DialMath.snap(63, 5, 5, 120))
        assertEquals(5, DialMath.snap(0, 5, 5, 120)) // không cho dưới 5 phút
        assertEquals(120, DialMath.snap(120, 5, 5, 120))
    }

    @Test
    fun scale120_quarterPositions() {
        assertEquals(30, DialMath.minutesAt(100f, 0f, previous = 20, max = 120))
        assertEquals(60, DialMath.minutesAt(0f, 100f, previous = 60, max = 120))
        assertEquals(90, DialMath.minutesAt(-100f, 0f, previous = 80, max = 120))
    }
}
