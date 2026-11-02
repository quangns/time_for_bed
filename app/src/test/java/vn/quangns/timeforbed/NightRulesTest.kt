package vn.quangns.timeforbed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.quangns.timeforbed.core.Exclusions
import vn.quangns.timeforbed.core.NightAction
import vn.quangns.timeforbed.core.NightRules

class NightRulesTest {
    private val min = 60_000L
    private val threshold = 30 * min

    private fun decide(
        minuteOfDay: Int = 60,
        idleMs: Long = 31 * min,
        screenOn: Boolean = true,
        media: Boolean = true,
        fgExcluded: Boolean = false,
        alarm: Boolean = false,
        enabled: Boolean = true,
    ) = NightRules.decide(enabled, minuteOfDay, 0, 360, idleMs, threshold, screenOn, media, fgExcluded, alarm)

    @Test
    fun stops_whenIdleOverThreshold_inWindow() = assertEquals(NightAction.STOP, decide())

    @Test
    fun warns_oneMinuteBefore() = assertEquals(NightAction.WARN, decide(idleMs = 29 * min + 10_000))

    @Test
    fun nothing_beforeWarning() = assertEquals(NightAction.NONE, decide(idleMs = 20 * min))

    @Test
    fun nothing_outsideWindow() = assertEquals(NightAction.NONE, decide(minuteOfDay = 7 * 60))

    @Test
    fun nothing_whenDisabled() = assertEquals(NightAction.NONE, decide(enabled = false))

    @Test
    fun nothing_whileAlarmRinging() = assertEquals(NightAction.NONE, decide(alarm = true))

    @Test
    fun stops_audioWithScreenOff() = assertEquals(NightAction.STOP, decide(screenOn = false))

    @Test
    fun nothing_screenOffAndSilent() = assertEquals(NightAction.NONE, decide(screenOn = false, media = false))

    @Test
    fun nothing_bankAppInForegroundWithoutMedia() =
        assertEquals(NightAction.NONE, decide(media = false, fgExcluded = true))

    @Test
    fun stops_mediaEvenIfBankInForeground() = assertEquals(NightAction.STOP, decide(fgExcluded = true))

    @Test
    fun exclusions() {
        val self = "vn.quangns.timeforbed"
        assertTrue(Exclusions.isExcluded(self, self, emptySet()))
        assertTrue(Exclusions.isExcluded("com.google.android.deskclock", self, emptySet()))
        assertTrue(Exclusions.isExcluded("com.sec.android.app.clockpackage", self, emptySet()))
        assertTrue(Exclusions.isExcluded("com.VCB", self, setOf("com.VCB")))
        assertFalse(Exclusions.isExcluded("com.google.android.youtube", self, setOf("com.VCB")))
        assertFalse(Exclusions.isExcluded(null, self, emptySet()))
    }

    @Test
    fun financeHeuristic() {
        assertTrue(Exclusions.looksLikeFinance("com.mbmobile", "MB Bank"))
        assertTrue(Exclusions.looksLikeFinance("vn.vnpt.vnptmoney", "VNPT Money"))
        assertTrue(Exclusions.looksLikeFinance("com.example", "Ngân hàng số"))
        assertFalse(Exclusions.looksLikeFinance("com.google.android.youtube", "YouTube"))
        assertFalse(Exclusions.looksLikeFinance("com.spotify.music", "Spotify"))
        assertFalse(Exclusions.looksLikeFinance("com.android.vending", "Google Play Store"))
    }

    // --- Lịch kiểm tra (tiết kiệm pin) ---
    private fun next(
        minuteOfDay: Int = 60,
        msIntoMinute: Long = 0,
        idleMs: Long = 0,
        screenOn: Boolean = true,
        media: Boolean = false,
        enabled: Boolean = true,
    ) = NightRules.nextCheckDelayMs(enabled, minuteOfDay, msIntoMinute, 0, 360, idleMs, threshold, screenOn, media)

    @Test
    fun noSchedule_whenDisabled() = assertNull(next(enabled = false))

    @Test
    fun outsideWindow_wakesAtWindowStart() {
        // 22:30 → 00:00 là 90 phút; đang ở giây thứ 15 của phút.
        assertEquals(90 * min - 15_000, next(minuteOfDay = 22 * 60 + 30, msIntoMinute = 15_000))
        // 06:00 (vừa hết khung) → 18 giờ sau.
        assertEquals(18 * 60 * min, next(minuteOfDay = 6 * 60))
    }

    @Test
    fun screenOffAndSilent_waitsForEvent() = assertNull(next(screenOn = false, media = false))

    @Test
    fun sleepsUntilWarningPoint() {
        // Mới chạm 5 phút trước, ngưỡng 30 → kiểm tra lại sau 24 phút (mốc cảnh báo 29 phút).
        assertEquals(24 * min, next(idleMs = 5 * min))
        // Đang trong phút cảnh báo → đúng lúc hết ngưỡng.
        assertEquals(20_000L, next(idleMs = 29 * min + 40_000))
        // Đã quá ngưỡng (vd đang mở app ngân hàng) → thưa, 60 giây một lần.
        assertEquals(60_000L, next(idleMs = 45 * min))
    }

    @Test
    fun audioWithScreenOff_stillScheduled() = assertEquals(24 * min, next(idleMs = 5 * min, screenOn = false, media = true))
}
