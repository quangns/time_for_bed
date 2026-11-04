package vn.quangns.timeforbed

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import vn.quangns.timeforbed.alarm.AlarmEntity
import vn.quangns.timeforbed.alarm.AlarmReceiver
import vn.quangns.timeforbed.alarm.AlarmRingActivity
import vn.quangns.timeforbed.alarm.AlarmRingService
import vn.quangns.timeforbed.alarm.AppDatabase

/** Chuông reo phải tự mở màn hình báo thức (không bắt người dùng vào app mới tắt được). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RingScreenTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun reset() = AppDatabase.resetForTest()

    @Test
    fun ringing_opensAlarmScreen() {
        runBlocking { AppDatabase.get(ctx).alarmDao().upsert(AlarmEntity(id = 21, hour = 6, minute = 0)) }
        val ring = Intent(ctx, AlarmRingService::class.java).setAction(AlarmRingService.ACTION_RING).putExtra(AlarmReceiver.EXTRA_ID, 21)
        val ctl = Robolectric.buildService(AlarmRingService::class.java, ring).create().startCommand(0, 1)
        val until = System.currentTimeMillis() + 5_000
        while (AlarmRingService.ringing.value == null && System.currentTimeMillis() < until) {
            Thread.sleep(20)
            shadowOf(Looper.getMainLooper()).idle()
        }
        assertNotNull(AlarmRingService.ringing.value)

        val started = shadowOf(ctx as Application).nextStartedActivity
        assertNotNull("chuông reo phải mở màn hình báo thức", started)
        assertEquals(AlarmRingActivity::class.java.name, started.component?.className)
        ctl.destroy()
    }
}
