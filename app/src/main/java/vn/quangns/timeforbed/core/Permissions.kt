package vn.quangns.timeforbed.core

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import vn.quangns.timeforbed.service.BedtimeAccessibilityService

enum class PermissionItem(val title: String, val why: String) {
    NOTIFICATION_ACCESS(
        "Truy cập thông báo",
        "Để thấy và tạm dừng các trình phát video/nhạc (YouTube, Spotify, TikTok…)",
    ),
    ACCESSIBILITY(
        "Trợ năng (Accessibility)",
        "Phát hiện thao tác của bạn trong khung giờ đêm, về màn hình chính và khóa máy",
    ),
    POST_NOTIFICATIONS("Hiển thị thông báo", "Hiện đếm ngược, cảnh báo và chuông báo thức"),
    EXACT_ALARM("Báo thức chính xác", "Hẹn giờ và báo thức đúng phút"),
    FULL_SCREEN("Hiện toàn màn hình", "Màn hình báo thức hiện lên cả khi máy đang khóa"),
    OVERLAY(
        "Hiển thị trên ứng dụng khác",
        "Màn hình báo thức tự bật lên cả khi đang dùng máy, không phải mở app mới tắt được",
    ),
    BATTERY("Bỏ tối ưu pin", "Không bị hệ thống tắt khi chạy nền qua đêm"),
}

object Permissions {

    fun isGranted(context: Context, item: PermissionItem): Boolean = when (item) {
        PermissionItem.NOTIFICATION_ACCESS ->
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        PermissionItem.ACCESSIBILITY -> isAccessibilityEnabled(context)
        PermissionItem.POST_NOTIFICATIONS ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        PermissionItem.EXACT_ALARM -> canScheduleExact(context)
        PermissionItem.FULL_SCREEN ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        PermissionItem.OVERLAY -> Settings.canDrawOverlays(context)
        PermissionItem.BATTERY ->
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(context, BedtimeAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /** Intent mở đúng trang Cài đặt để cấp quyền. POST_NOTIFICATIONS xin bằng runtime permission. */
    @SuppressLint("BatteryLife", "InlinedApi")
    fun settingsIntent(context: Context, item: PermissionItem): Intent {
        val pkgUri = Uri.parse("package:${context.packageName}")
        return when (item) {
            PermissionItem.NOTIFICATION_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            PermissionItem.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            PermissionItem.POST_NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            PermissionItem.EXACT_ALARM -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri)
            PermissionItem.FULL_SCREEN -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri)
            PermissionItem.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)
            PermissionItem.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Quyền nào thực sự áp dụng cho phiên bản Android hiện tại. */
    fun applicable(): List<PermissionItem> = PermissionItem.entries.filter {
        when (it) {
            PermissionItem.POST_NOTIFICATIONS -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            PermissionItem.EXACT_ALARM -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            PermissionItem.FULL_SCREEN -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            else -> true
        }
    }
}
