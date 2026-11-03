package vn.quangns.timeforbed

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.quangns.timeforbed.alarm.AlarmEntity
import vn.quangns.timeforbed.alarm.DAY_PRESETS
import vn.quangns.timeforbed.alarm.daysText
import vn.quangns.timeforbed.alarm.untilText

class AlarmTextTest {
    private val min = 60_000L

    @Test fun untilTextReadable() {
        assertEquals("dưới 1 phút", untilText(30_000))
        assertEquals("45 phút", untilText(45 * min))
        assertEquals("7 giờ 20 phút", untilText((7 * 60 + 20) * min))
        assertEquals("2 giờ", untilText(120 * min))
        assertEquals("1 ngày 3 giờ", untilText((27 * 60) * min))
        assertEquals("6 ngày", untilText(6 * 24 * 60 * min))
        // Làm tròn lên phút: còn 59 phút 10 giây vẫn ghi 1 giờ
        assertEquals("1 giờ", untilText(59 * min + 10_000))
    }

    @Test fun presetsMatchDayText() {
        val texts = DAY_PRESETS.map { (_, mask) -> AlarmEntity(hour = 6, minute = 0, daysMask = mask).daysText() }
        assertEquals(listOf("Một lần", "Thứ 2 – Thứ 6", "Cuối tuần", "Hằng ngày"), texts)
    }
}
