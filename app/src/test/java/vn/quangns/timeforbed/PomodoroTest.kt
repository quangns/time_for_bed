package vn.quangns.timeforbed

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import vn.quangns.timeforbed.pomodoro.Phase
import vn.quangns.timeforbed.pomodoro.Pomodoro

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PomodoroTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val alarms get() = shadowOf(ctx.getSystemService(AlarmManager::class.java))
    private val t0 = 1_000_000_000L
    private val min = 60_000L

    @Before
    fun reset() {
        Pomodoro.resetForTest()
        Pomodoro.reset(ctx)
    }

    @Test
    fun start_schedulesPhaseEnd() {
        Pomodoro.start(ctx, now = t0)
        val s = Pomodoro.current(ctx)
        assertTrue(s.running)
        assertEquals(t0 + 25 * min, s.endAt)
        assertEquals(t0 + 25 * min, alarms.nextScheduledAlarm!!.triggerAtTime)
    }

    @Test
    fun pause_keepsRemaining_andCancelsAlarm_resumeContinues() {
        Pomodoro.start(ctx, now = t0)
        Pomodoro.pause(ctx, now = t0 + 10 * min)
        var s = Pomodoro.current(ctx)
        assertFalse(s.running)
        assertEquals(15 * min, s.remainingMs)
        assertNull(alarms.nextScheduledAlarm)

        Pomodoro.start(ctx, now = t0 + 20 * min)
        s = Pomodoro.current(ctx)
        assertEquals(t0 + 35 * min, s.endAt)
    }

    @Test
    fun focusEnd_countsToday_andAutoStartsBreak() {
        Pomodoro.start(ctx, now = t0)
        Pomodoro.onPhaseEnd(ctx, now = t0 + 25 * min)
        val s = Pomodoro.current(ctx)
        assertEquals(Phase.SHORT_BREAK, s.phase)
        assertEquals(1, s.doneInCycle)
        assertEquals(1, s.todayCount)
        assertTrue(s.running)
        assertEquals(t0 + 30 * min, s.endAt)
        assertNotNull(alarms.nextScheduledAlarm)
    }

    @Test
    fun noAutoStart_waitsForUser() {
        Pomodoro.updateSettings(ctx, Pomodoro.current(ctx).settings.copy(autoStart = false))
        Pomodoro.start(ctx, now = t0)
        Pomodoro.onPhaseEnd(ctx, now = t0 + 25 * min)
        val s = Pomodoro.current(ctx)
        assertEquals(Phase.SHORT_BREAK, s.phase)
        assertFalse(s.running)
        assertTrue(s.fresh)
    }

    @Test
    fun earlyAlarm_isIgnored() {
        Pomodoro.start(ctx, now = t0)
        Pomodoro.onPhaseEnd(ctx, now = t0 + 10 * min)
        assertEquals(Phase.FOCUS, Pomodoro.current(ctx).phase)
    }

    @Test
    fun skipFocus_doesNotCount() {
        Pomodoro.start(ctx, now = t0)
        Pomodoro.skip(ctx, now = t0 + 5 * min)
        val s = Pomodoro.current(ctx)
        assertEquals(Phase.SHORT_BREAK, s.phase)
        assertEquals(0, s.todayCount)
        assertTrue(s.running) // đang chạy thì giai đoạn mới chạy luôn
    }

    @Test
    fun changeDuration_appliesToFreshPhase() {
        Pomodoro.updateSettings(ctx, Pomodoro.current(ctx).settings.copy(focusMin = 50))
        assertEquals(50 * min, Pomodoro.current(ctx).remainingMs)
    }

    @Test
    fun restoreAfterReboot_phaseAlreadyOver_advances() {
        Pomodoro.start(ctx, now = t0)
        Pomodoro.restore(ctx, now = t0 + 26 * min)
        assertEquals(Phase.SHORT_BREAK, Pomodoro.current(ctx).phase)
        assertEquals(1, Pomodoro.current(ctx).todayCount)
    }
}
