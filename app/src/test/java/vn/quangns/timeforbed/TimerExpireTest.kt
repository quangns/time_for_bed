package vn.quangns.timeforbed

import android.content.Context
import android.content.Intent
import android.media.AudioManager
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

/** Hết giờ: receiver phải xóa hẹn giờ, chạy đủ các lượt dừng, ghi báo cáo — và không đụng tới âm lượng. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TimerExpireTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val am get() = ctx.getSystemService(AudioManager::class.java)

    private fun expire() {
        Prefs.get(ctx).timerDeadline = System.currentTimeMillis() - 1_000
        ctx.sendBroadcast(Intent(ctx, TimerReceiver::class.java).setAction(TimerReceiver.ACTION_EXPIRE))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
    }

    @Test
    fun expire_clearsTimer_andWritesReport() {
        val prefs = Prefs.get(ctx)
        prefs.recordStopReport("")
        expire()

        assertEquals(0L, prefs.timerDeadline)
        assertEquals("Hết giờ hẹn ngủ", prefs.state.value.lastActionReason)
        val report = prefs.state.value.lastStopReport
        assertTrue(report, report.contains("Kết quả: đã im lặng"))
        // Không có Trợ năng trong test → báo rõ là chưa khóa được máy.
        assertTrue(report, report.contains("Trợ năng chưa kết nối"))
    }

    /** App như TikTok vẫn còn tiếng: không được giảm âm lượng (ảnh hưởng chuông báo thức), chỉ báo lại. */
    @Test
    fun audioStillPlaying_volumeUntouched() {
        am.setStreamVolume(AudioManager.STREAM_MUSIC, 9, 0)
        am.setStreamVolume(AudioManager.STREAM_ALARM, 6, 0)
        shadowOf(am).setIsMusicActive(true)
        expire()

        assertEquals(9, am.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertEquals(6, am.getStreamVolume(AudioManager.STREAM_ALARM))
        val report = Prefs.get(ctx).state.value.lastStopReport
        assertTrue(report, report.contains("Vẫn còn âm thanh"))
    }
}
