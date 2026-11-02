package vn.quangns.timeforbed

import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import vn.quangns.timeforbed.core.Prefs
import vn.quangns.timeforbed.service.TimerReceiver
import java.time.Duration

/** Hết giờ: receiver phải xóa hẹn giờ, chạy đủ các lượt dừng và ghi báo cáo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TimerExpireTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun expire_clearsTimer_andWritesReport() {
        val prefs = Prefs.get(ctx)
        prefs.lockAfterStop = false
        prefs.recordStopReport("")
        prefs.timerDeadline = System.currentTimeMillis() - 1_000

        ctx.sendBroadcast(Intent(ctx, TimerReceiver::class.java).setAction(TimerReceiver.ACTION_EXPIRE))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))

        assertEquals(0L, prefs.timerDeadline)
        assertEquals("Hết giờ hẹn ngủ", prefs.state.value.lastActionReason)
        val report = prefs.state.value.lastStopReport
        assertTrue(report, report.contains("Sau 4 giây") || report.contains("Vẫn còn"))
    }
}
