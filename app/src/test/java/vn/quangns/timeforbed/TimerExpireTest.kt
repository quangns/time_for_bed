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
import vn.quangns.timeforbed.core.MediaVolume
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
        assertTrue(report, report.contains("Kết quả") || report.contains("Vẫn còn"))
    }

    /** App như TikTok: không có MediaSession, phớt lờ phím/focus, âm thanh vẫn phát → phải tắt tiếng media. */
    @Test
    fun appIgnoringEverything_getsMuted_thenRestored() {
        val prefs = Prefs.get(ctx)
        prefs.lockAfterStop = false
        prefs.mutedPrevVolume = 0
        val am = ctx.getSystemService(AudioManager::class.java)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, 9, 0)
        shadowOf(am).setIsMusicActive(true)
        prefs.timerDeadline = System.currentTimeMillis() - 1_000

        ctx.sendBroadcast(Intent(ctx, TimerReceiver::class.java).setAction(TimerReceiver.ACTION_EXPIRE))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))

        assertEquals(0, am.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertEquals(9, prefs.mutedPrevVolume)
        assertTrue(prefs.state.value.lastStopReport.contains("đã tắt tiếng media"))

        // Mở khóa máy / bấm "Bật lại âm thanh": trả đúng mức cũ.
        assertTrue(MediaVolume.restore(ctx))
        assertEquals(9, am.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertEquals(0, prefs.mutedPrevVolume)
    }

    @Test
    fun silentAfterStop_doesNotMute() {
        val prefs = Prefs.get(ctx)
        prefs.lockAfterStop = false
        prefs.mutedPrevVolume = 0
        val am = ctx.getSystemService(AudioManager::class.java)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, 7, 0)
        shadowOf(am).setIsMusicActive(false)
        prefs.timerDeadline = System.currentTimeMillis() - 1_000

        ctx.sendBroadcast(Intent(ctx, TimerReceiver::class.java).setAction(TimerReceiver.ACTION_EXPIRE))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))

        assertEquals(7, am.getStreamVolume(AudioManager.STREAM_MUSIC))
    }
}
