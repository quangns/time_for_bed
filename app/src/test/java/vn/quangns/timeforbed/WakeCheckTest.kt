package vn.quangns.timeforbed

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager
import vn.quangns.timeforbed.alarm.AlarmEntity
import vn.quangns.timeforbed.alarm.AlarmReceiver
import vn.quangns.timeforbed.alarm.AlarmRingService
import vn.quangns.timeforbed.alarm.AppDatabase
import vn.quangns.timeforbed.alarm.WakeCheckRules
import vn.quangns.timeforbed.alarm.WakeMeter
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WakeCheckTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    // --- Đo vận động ---

    @Test
    fun meter_stillPhone_countsNothing() {
        val m = WakeMeter()
        for (t in 0 until 120_000 step 200) m.onSample(t.toLong(), 0.05f, 0.1f, 9.78f) // nằm yên trên giường
        assertEquals(0, m.activeCount)
    }

    @Test
    fun meter_lightScrollingInBed_countsNothing() {
        val m = WakeMeter()
        for (t in 0 until 60_000 step 200) m.onSample(t.toLong(), 0.6f, 0.4f, 9.3f + (t % 1000) / 1000f) // lệch < 1 m/s²
        assertEquals(0, m.activeCount)
    }

    @Test
    fun meter_walking_countsEachActiveSecondOnce() {
        val m = WakeMeter()
        // Đi bộ cầm máy: gia tốc dao động 2–4 m/s² quanh trọng lực, 5 mẫu mỗi giây, 45 giây.
        for (t in 0 until 45_000 step 200) {
            val bounce = if ((t / 200) % 2 == 0) 3.2f else -2.6f
            m.onSample(t.toLong(), 1.0f, 0.5f, 9.81f + bounce)
        }
        assertEquals(45, m.activeCount)
    }

    @Test
    fun rules_maxRingsAgain() {
        assertTrue(WakeCheckRules.shouldRingAgain(10, 60, 0))
        assertFalse(WakeCheckRules.shouldRingAgain(10, 60, WakeCheckRules.MAX_RINGS_AGAIN))
        assertFalse(WakeCheckRules.shouldRingAgain(60, 60, 0))
    }

    // --- Nâng cấp database v1 → v2 ---

    @Test
    fun migration_v1ToV2_keepsAlarms_andAddsDefaults() {
        val file = ctx.getDatabasePath("time_for_bed.db")
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `alarms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `daysMask` INTEGER NOT NULL, `label` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `ringtoneUri` TEXT, `vibrate` INTEGER NOT NULL, `snoozeMinutes` INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'ee1ef15dd46dc8eb7230618827425bed')")
            db.execSQL("INSERT INTO alarms (id,hour,minute,daysMask,label,enabled,ringtoneUri,vibrate,snoozeMinutes) VALUES (5,6,30,31,'Đi làm',1,NULL,1,10)")
            db.version = 1
        }
        val alarm = runBlocking { AppDatabase.get(ctx).alarmDao().get(5) }
        assertNotNull(alarm)
        assertEquals(AlarmEntity(id = 5, hour = 6, minute = 30, daysMask = 31, label = "Đi làm", snoozeMinutes = 10), alarm)
        assertFalse(alarm!!.wakeCheck)
        assertEquals(5, alarm.wakeCheckMinutes)
        assertEquals(60, alarm.wakeMoveSeconds)
    }

    // --- Quy trình trong dịch vụ chuông ---

    private lateinit var sensors: ShadowSensorManager

    @Before
    fun addAccelerometer() {
        AppDatabase.resetForTest()
        sensors = shadowOf(ctx.getSystemService(SensorManager::class.java))
        sensors.addSensor(ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER))
    }

    private fun ringAndDismiss(alarm: AlarmEntity): org.robolectric.android.controller.ServiceController<AlarmRingService> {
        runBlocking { AppDatabase.get(ctx).alarmDao().upsert(alarm) }
        val ring = Intent(ctx, AlarmRingService::class.java).setAction(AlarmRingService.ACTION_RING).putExtra(AlarmReceiver.EXTRA_ID, alarm.id)
        val ctl = Robolectric.buildService(AlarmRingService::class.java, ring).create().startCommand(0, 1)
        val until = System.currentTimeMillis() + 5_000
        while (AlarmRingService.ringing.value == null && System.currentTimeMillis() < until) {
            Thread.sleep(20)
            shadowOf(Looper.getMainLooper()).idle()
        }
        assertNotNull("chuông phải reo", AlarmRingService.ringing.value)
        ctl.withIntent(Intent(ctx, AlarmRingService::class.java).setAction(AlarmRingService.ACTION_DISMISS)).startCommand(0, 2)
        shadowOf(Looper.getMainLooper()).idle()
        return ctl
    }

    private fun shake(seconds: Int) {
        repeat(seconds * 5) { i ->
            val e = ShadowSensorManager.createSensorEvent(3, Sensor.TYPE_ACCELEROMETER)
            e.values[0] = 1f; e.values[1] = 0.5f; e.values[2] = 9.81f + if (i % 2 == 0) 3.2f else -2.6f
            sensors.sendSensorEventToListeners(e)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        }
    }

    @Test
    fun dismiss_withWakeCheck_walkingEnough_finishes() {
        val ctl = ringAndDismiss(AlarmEntity(id = 11, hour = 6, minute = 0, wakeCheck = true, wakeCheckMinutes = 3, wakeMoveSeconds = 30))
        assertNull("tắt chuông thì màn hình báo thức đóng", AlarmRingService.ringing.value)
        assertNotNull("đang kiểm tra chống ngủ lại", AlarmRingService.wakeCheck.value)
        assertEquals(30, AlarmRingService.wakeCheck.value!!.requiredSeconds)

        shake(32)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertNull("đủ vận động thì kết thúc kiểm tra", AlarmRingService.wakeCheck.value)
        assertNull(AlarmRingService.ringing.value)
        ctl.destroy()
    }

    @Test
    fun dismiss_withWakeCheck_lyingStill_ringsAgain() {
        val ctl = ringAndDismiss(AlarmEntity(id = 12, hour = 6, minute = 0, wakeCheck = true, wakeCheckMinutes = 3, wakeMoveSeconds = 30))
        assertNotNull(AlarmRingService.wakeCheck.value)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(3).plusSeconds(2)) // nằm yên hết 3 phút
        assertNotNull("chưa vận động đủ thì chuông reo lại", AlarmRingService.ringing.value)
        assertNull(AlarmRingService.wakeCheck.value)
        ctl.withIntent(Intent(ctx, AlarmRingService::class.java).setAction(AlarmRingService.ACTION_DISMISS)).startCommand(0, 3)
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull("tắt lần nữa thì kiểm tra lại từ đầu", AlarmRingService.wakeCheck.value)
        ctl.destroy()
    }

    @Test
    fun dismiss_withoutWakeCheck_stopsImmediately() {
        val ctl = ringAndDismiss(AlarmEntity(id = 13, hour = 6, minute = 0, wakeCheck = false))
        assertNull(AlarmRingService.wakeCheck.value)
        assertNull(AlarmRingService.ringing.value)
        ctl.destroy()
    }
}
