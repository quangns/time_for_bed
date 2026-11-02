package vn.quangns.timeforbed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
