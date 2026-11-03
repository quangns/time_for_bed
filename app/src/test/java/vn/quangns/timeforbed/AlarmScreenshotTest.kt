package vn.quangns.timeforbed

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.quangns.timeforbed.alarm.AlarmEntity
import vn.quangns.timeforbed.alarm.AppDatabase
import vn.quangns.timeforbed.ui.alarm.AlarmEditor
import vn.quangns.timeforbed.ui.alarm.AlarmScreen
import vn.quangns.timeforbed.ui.theme.Night
import vn.quangns.timeforbed.ui.theme.TimeForBedTheme
import java.io.File

/** Ảnh tab Báo thức (app/build/screenshots) + kiểm tra nút xóa ngay trên danh sách. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi", application = android.app.Application::class)
class AlarmScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val dao get() = AppDatabase.get(ApplicationProvider.getApplicationContext()).alarmDao()

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            TimeForBedTheme { Surface(Modifier.fillMaxSize(), color = Night.Ink) { content() } }
        }
        compose.waitForIdle()
        save(name)
    }

    private fun save(name: String) {
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun alarmListAndDelete() {
        runBlocking {
            dao.getAll().forEach { dao.delete(it) }
            dao.upsert(AlarmEntity(hour = 6, minute = 0, daysMask = 0x1F, label = "Đi làm"))
            dao.upsert(AlarmEntity(hour = 8, minute = 30, daysMask = 0x60, enabled = false))
        }
        shot("5-alarm-list") { AlarmScreen() }
        compose.onNodeWithContentDescription("Xóa báo thức 08:30").performClick()
        compose.waitForIdle()
        Thread.sleep(300)
        compose.waitForIdle()
        assertEquals(listOf("06:00"), runBlocking { dao.getAll() }.map { "%02d:%02d".format(it.hour, it.minute) })
        save("6-alarm-deleted")
    }

    @Test fun alarmEditor() = shot("7-alarm-editor") {
        AlarmEditor(
            initial = AlarmEntity(id = 3, hour = 6, minute = 30, daysMask = 0x1F, label = "Đi làm"),
            onDismiss = {}, onSave = {}, onDelete = {},
        )
    }
}
