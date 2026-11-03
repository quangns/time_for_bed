package vn.quangns.timeforbed.pomodoro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Hết giai đoạn (AlarmManager) và các nút trên thông báo Pomodoro. */
class PomodoroReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Pomodoro.ACTION_END -> Pomodoro.onPhaseEnd(context)
            Pomodoro.ACTION_PAUSE -> Pomodoro.pause(context)
            Pomodoro.ACTION_RESUME -> Pomodoro.start(context)
            Pomodoro.ACTION_SKIP -> Pomodoro.skip(context)
        }
    }
}
