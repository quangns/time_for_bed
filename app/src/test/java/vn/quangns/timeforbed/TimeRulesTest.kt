package vn.quangns.timeforbed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.quangns.timeforbed.core.TimeRules
import java.time.LocalDateTime

class TimeRulesTest {

    @Test
    fun window_0h_to_6h() {
        assertTrue(TimeRules.inWindow(0, 360, 0))
        assertTrue(TimeRules.inWindow(0, 360, 359))
        assertFalse(TimeRules.inWindow(0, 360, 360))
        assertFalse(TimeRules.inWindow(0, 360, 23 * 60 + 59))
    }

    @Test
    fun window_across_midnight() {
        val start = 23 * 60
        val end = 6 * 60
        assertTrue(TimeRules.inWindow(start, end, 23 * 60 + 30))
        assertTrue(TimeRules.inWindow(start, end, 2 * 60))
        assertFalse(TimeRules.inWindow(start, end, 12 * 60))
        assertFalse(TimeRules.inWindow(start, end, 6 * 60))
    }

    @Test
    fun window_equal_means_all_day() {
        assertTrue(TimeRules.inWindow(300, 300, 0))
        assertTrue(TimeRules.inWindow(300, 300, 1000))
    }

    // 2026-11-02 là Thứ Hai.
    private val monday0700 = LocalDateTime.of(2026, 11, 2, 7, 0)

    @Test
    fun oneShot_laterToday() {
        assertEquals(LocalDateTime.of(2026, 11, 2, 8, 30), TimeRules.nextAlarm(monday0700, 8, 30, 0))
    }

    @Test
    fun oneShot_passed_goesTomorrow() {
        assertEquals(LocalDateTime.of(2026, 11, 3, 6, 0), TimeRules.nextAlarm(monday0700, 6, 0, 0))
        // Đúng phút hiện tại cũng tính là đã qua.
        assertEquals(LocalDateTime.of(2026, 11, 3, 7, 0), TimeRules.nextAlarm(monday0700, 7, 0, 0))
    }

    @Test
    fun weekdays_fromFridayEvening_goesMonday() {
        val friday2200 = LocalDateTime.of(2026, 11, 6, 22, 0)
        assertEquals(LocalDateTime.of(2026, 11, 9, 6, 0), TimeRules.nextAlarm(friday2200, 6, 0, 0x1F))
    }

    @Test
    fun sundayOnly() {
        assertEquals(LocalDateTime.of(2026, 11, 8, 9, 0), TimeRules.nextAlarm(monday0700, 9, 0, 1 shl 6))
    }

    @Test
    fun sameDayRepeating_passed_nextWeek() {
        assertEquals(LocalDateTime.of(2026, 11, 9, 6, 0), TimeRules.nextAlarm(monday0700, 6, 0, 1))
    }
}
