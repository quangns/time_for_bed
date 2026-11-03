package vn.quangns.timeforbed

import android.app.Application
import vn.quangns.timeforbed.alarm.AlarmMirror
import vn.quangns.timeforbed.core.Exclusions
import vn.quangns.timeforbed.core.MediaVolume
import vn.quangns.timeforbed.core.Notifications
import vn.quangns.timeforbed.core.Prefs

class TimeForBedApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        // Máy vừa khởi động chưa mở khóa (chỉ báo thức chạy được): chưa đọc được cài đặt của app.
        if (!AlarmMirror.isUserUnlocked(this)) return
        Prefs.get(this)
        Exclusions.initDefaults(this)
        MediaVolume.restore(this)
    }
}
