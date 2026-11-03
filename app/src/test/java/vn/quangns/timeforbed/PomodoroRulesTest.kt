package vn.quangns.timeforbed

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.quangns.timeforbed.pomodoro.Phase
import vn.quangns.timeforbed.pomodoro.PomodoroRules

class PomodoroRulesTest {
    @Test
    fun fullCycle_4focus_thenLongBreak() {
        var phase = Phase.FOCUS
        var done = 0
        val seen = mutableListOf<Phase>()
        repeat(9) {
            val (p, d) = PomodoroRules.next(phase, done, longEvery = 4)
            phase = p; done = d; seen += p
        }
        assertEquals(
            listOf(
                Phase.SHORT_BREAK, Phase.FOCUS, Phase.SHORT_BREAK, Phase.FOCUS, Phase.SHORT_BREAK, Phase.FOCUS,
                Phase.LONG_BREAK, Phase.FOCUS, Phase.SHORT_BREAK,
            ),
            seen,
        )
    }

    @Test
    fun skippedFocus_doesNotCount() {
        assertEquals(Phase.SHORT_BREAK to 3, PomodoroRules.next(Phase.FOCUS, 3, 4, counted = false))
        assertEquals(Phase.LONG_BREAK to 4, PomodoroRules.next(Phase.FOCUS, 3, 4, counted = true))
    }

    @Test
    fun longBreak_resetsCycle() = assertEquals(Phase.FOCUS to 0, PomodoroRules.next(Phase.LONG_BREAK, 4, 4))
}
