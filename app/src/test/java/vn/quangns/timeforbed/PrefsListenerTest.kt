package vn.quangns.timeforbed

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.quangns.timeforbed.core.Prefs
import java.lang.ref.WeakReference

/**
 * Prefs.state phải tiếp tục cập nhật sau khi bộ dọn rác chạy. Bản ≤1.3.1 giữ listener trong một field mà R8
 * xoá đi ở bản release → listener (chỉ được SharedPreferences giữ yếu) bị dọn rác → tab Ban đêm hiện giá trị cũ.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PrefsListenerTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun listenerIsPrefsItself_soItCannotBeCollected() {
        Prefs.resetForTest()
        val prefs = Prefs.get(ctx)
        assertTrue(prefs is SharedPreferences.OnSharedPreferenceChangeListener)
    }

    @Test
    fun stateUpdates_afterGc_whenWrittenElsewhere() {
        Prefs.resetForTest()
        val prefs = Prefs.get(ctx)
        // Ép dọn rác vài lần.
        repeat(5) {
            val probe = WeakReference(Any())
            while (probe.get() != null) System.gc()
        }
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit(commit = true) { putInt("night_start", 23 * 60) }
        assertEquals(23 * 60, prefs.state.value.nightStart)
    }

    @Test
    fun saveNight_updatesStateImmediately() {
        Prefs.resetForTest()
        val prefs = Prefs.get(ctx)
        prefs.saveNight(enabled = false, start = 22 * 60, end = 7 * 60, idleMinutes = 45)
        val s = prefs.state.value
        assertEquals(listOf(false, 22 * 60, 7 * 60, 45), listOf(s.nightEnabled, s.nightStart, s.nightEnd, s.idleMinutes))
    }
}
