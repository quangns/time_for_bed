package vn.quangns.timeforbed.alarm

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager
import androidx.core.content.edit

/**
 * Bản sao gọn của các báo thức trong vùng lưu trữ "device protected" — đọc được cả khi máy vừa khởi động
 * lại mà CHƯA mở khóa (lúc đó Room/SharedPreferences thường chưa mở được). Nhờ vậy báo thức vẫn được
 * đặt lại và vẫn reo sau khi máy tự khởi động lại trong đêm.
 */
object AlarmMirror {
    data class Def(val id: Int, val hour: Int, val minute: Int, val daysMask: Int, val snoozeMinutes: Int)

    private fun sp(context: Context): SharedPreferences =
        context.createDeviceProtectedStorageContext().getSharedPreferences("alarm_mirror", Context.MODE_PRIVATE)

    fun isUserUnlocked(context: Context): Boolean =
        context.getSystemService(UserManager::class.java)?.isUserUnlocked ?: true

    fun put(context: Context, a: AlarmEntity) = sp(context).edit {
        putString("def:${a.id}", "${a.hour}|${a.minute}|${a.daysMask}|${a.snoozeMinutes}")
    }

    fun remove(context: Context, alarmId: Int) = sp(context).edit {
        remove("def:$alarmId")
        remove("snooze:$alarmId")
    }

    fun get(context: Context, alarmId: Int): Def? = parse(alarmId, sp(context).getString("def:$alarmId", null))

    fun defs(context: Context): List<Def> = sp(context).all.mapNotNull { (k, v) ->
        if (!k.startsWith("def:")) null else parse(k.removePrefix("def:").toIntOrNull() ?: return@mapNotNull null, v as? String)
    }

    fun putSnooze(context: Context, alarmId: Int, at: Long) = sp(context).edit { putLong("snooze:$alarmId", at) }
    fun clearSnooze(context: Context, alarmId: Int) = sp(context).edit { remove("snooze:$alarmId") }
    fun snoozes(context: Context): Map<Int, Long> = sp(context).all.mapNotNull { (k, v) ->
        if (!k.startsWith("snooze:")) null else (k.removePrefix("snooze:").toIntOrNull() ?: return@mapNotNull null) to (v as Long)
    }.toMap()

    /** Báo thức một lần đã reo khi máy còn khóa: cần tắt trong Room sau khi mở khóa. */
    fun markFiredOnce(context: Context, alarmId: Int) = sp(context).edit {
        putStringSet("fired_once", firedOnce(context) + alarmId.toString())
    }

    fun firedOnce(context: Context): Set<String> = sp(context).getStringSet("fired_once", emptySet())!!.toSet()
    fun clearFiredOnce(context: Context) = sp(context).edit { remove("fired_once") }

    private fun parse(id: Int, raw: String?): Def? {
        val p = raw?.split('|')?.mapNotNull { it.toIntOrNull() } ?: return null
        if (p.size < 4) return null
        return Def(id, p[0], p[1], p[2], p[3])
    }
}
