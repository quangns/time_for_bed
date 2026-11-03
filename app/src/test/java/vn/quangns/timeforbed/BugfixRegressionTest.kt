package vn.quangns.timeforbed

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Looper
import android.os.UserManager
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import vn.quangns.timeforbed.alarm.AlarmEntity
import vn.quangns.timeforbed.alarm.AlarmMirror
import vn.quangns.timeforbed.alarm.AlarmReceiver
import vn.quangns.timeforbed.alarm.AlarmScheduler
import vn.quangns.timeforbed.core.MediaKiller
import vn.quangns.timeforbed.core.Prefs
import vn.quangns.timeforbed.pomodoro.Pomodoro
import vn.quangns.timeforbed.ui.MainScreen
import vn.quangns.timeforbed.ui.markPrivacySeen
import vn.quangns.timeforbed.ui.theme.TimeForBedTheme
import java.time.Duration

/** Test hồi quy cho các lỗi tìm được khi rà soát trước bản 1.3.1. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h860dp", application = android.app.Application::class)
class BugfixRegressionTest {
    @get:Rule
    val compose = createComposeRule()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val alarms get() = shadowOf(ctx.getSystemService(AlarmManager::class.java))
    private val min = 60_000L

    @Before
    fun reset() {
        Prefs.resetForTest()
        Pomodoro.resetForTest()
    }

    // --- Mở tab từ thông báo ---

    @Test
    fun notificationTab_opensEveryTime_evenSameTabTwice() {
        markPrivacySeen(ctx)
        val request = mutableIntStateOf(MainActivity.TAB_POMODORO)
        compose.setContent { TimeForBedTheme { MainScreen(tabRequest = request) } }
        compose.onNodeWithText("Tập trung 25 phút", substring = true).assertIsDisplayed()
        assertEquals(MainActivity.NO_TAB, request.intValue) // yêu cầu đã được "tiêu thụ"

        compose.onNodeWithText("Hẹn giờ").performClick() // người dùng tự chuyển tab
        compose.onNodeWithText("Hẹn giờ ngủ").assertIsDisplayed()

        request.intValue = MainActivity.TAB_POMODORO // bấm thông báo Pomodoro lần nữa
        compose.waitForIdle()
        compose.onNodeWithText("Tập trung 25 phút", substring = true).assertIsDisplayed()
    }

    // --- Pomodoro đổi thời lượng giữa chừng ---

    @Test
    fun pomodoro_changeDurationWhilePaused_keepsCurrentPhaseConsistent() {
        Pomodoro.reset(ctx)
        Pomodoro.start(ctx, now = 0L + 1_000_000)
        Pomodoro.pause(ctx, now = 1_000_000 + 5 * min) // còn 20 phút
        Pomodoro.updateSettings(ctx, Pomodoro.current(ctx).settings.copy(focusMin = 15))
        val s = Pomodoro.current(ctx)
        assertEquals(25 * min, s.totalMs) // giai đoạn đang dở vẫn tính trên 25 phút
        assertEquals(20 * min, s.remainingMs)
        assertFalse(s.fresh) // vẫn hiện "Tiếp tục", không nhầm thành "Bắt đầu"

        Pomodoro.start(ctx, now = 2_000_000)
        Pomodoro.onPhaseEnd(ctx, now = 2_000_000 + 20 * min)
        Pomodoro.skip(ctx, now = 2_000_000 + 21 * min) // bỏ nghỉ → phiên tập trung mới
        assertEquals(15 * min, Pomodoro.current(ctx).totalMs) // giai đoạn sau mới dùng 15 phút
    }

    // --- Không giành audio focus khi không có gì phát ---

    @Test
    fun mediaKiller_nothingPlaying_doesNotTakeAudioFocus() {
        val am = ctx.getSystemService(AudioManager::class.java)
        shadowOf(am).setIsMusicActive(false)
        MediaKiller.stopAll(ctx, "test", lockScreen = false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertNull(shadowOf(am).lastAudioFocusRequest)
    }

    // --- Báo thức sau khi khởi động lại mà chưa mở khóa ---

    @Test
    fun alarmMirror_followsScheduleAndCancel() {
        val a = AlarmEntity(id = 7, hour = 6, minute = 30, daysMask = 0x1F)
        AlarmScheduler.schedule(ctx, a)
        assertEquals(AlarmMirror.Def(7, 6, 30, 0x1F, 5), AlarmMirror.get(ctx, 7))
        AlarmScheduler.cancel(ctx, 7)
        assertNull(AlarmMirror.get(ctx, 7))
    }

    @Test
    fun lockedBoot_reschedulesFromMirror() {
        val a = AlarmEntity(id = 3, hour = 6, minute = 0, daysMask = 0x7F)
        AlarmScheduler.schedule(ctx, a)
        // Giả lập khởi động lại: AlarmManager mất hết báo thức, máy chưa mở khóa.
        ctx.getSystemService(AlarmManager::class.java).cancelAll()
        assertNull(alarms.nextScheduledAlarm)
        shadowOf(ctx.getSystemService(UserManager::class.java)).setUserUnlocked(false)

        ctx.sendBroadcast(Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED).setPackage(ctx.packageName))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        Thread.sleep(300) // BootReceiver làm việc trên Dispatchers.IO
        shadowOf(Looper.getMainLooper()).idle()

        val next = alarms.nextScheduledAlarm
        assertNotNull("báo thức phải được đặt lại khi máy chưa mở khóa", next)
        assertEquals(AlarmScheduler.nextTriggerMillis(a), next!!.triggerAtTime)
    }

    @Test
    fun oneShotFiredWhileLocked_isRememberedForDisable() {
        val a = AlarmEntity(id = 9, hour = 7, minute = 0, daysMask = 0)
        AlarmScheduler.schedule(ctx, a)
        shadowOf(ctx.getSystemService(UserManager::class.java)).setUserUnlocked(false)
        ctx.sendBroadcast(
            Intent(ctx, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE).putExtra(AlarmReceiver.EXTRA_ID, 9),
        )
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(300)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(AlarmMirror.firedOnce(ctx).contains("9"))
        assertNull(AlarmMirror.get(ctx, 9)) // không đặt lại báo thức một lần
    }
}
