package vn.quangns.timeforbed.alarm

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    /** bit 0 = Thứ Hai … bit 6 = Chủ Nhật; 0 = một lần. */
    val daysMask: Int = 0,
    val label: String = "",
    val enabled: Boolean = true,
    /** null = chuông báo thức mặc định của máy. */
    val ringtoneUri: String? = null,
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 5,
    /** Chống ngủ lại: sau khi tắt chuông phải cầm máy vận động đủ, không thì reo lại. */
    @ColumnInfo(defaultValue = "0") val wakeCheck: Boolean = false,
    /** Thời gian kiểm tra sau khi tắt chuông (phút). */
    @ColumnInfo(defaultValue = "5") val wakeCheckMinutes: Int = 5,
    /** Số giây vận động cần đạt trong thời gian kiểm tra. */
    @ColumnInfo(defaultValue = "60") val wakeMoveSeconds: Int = 60,
)

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms")
    suspend fun getAll(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun get(id: Int): AlarmEntity?

    @Upsert
    suspend fun upsert(alarm: AlarmEntity): Long

    @Delete
    suspend fun delete(alarm: AlarmEntity)
}

@Database(
    entities = [AlarmEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)], // v2: thêm 3 cột chống ngủ lại
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** Chỉ dùng trong test: Robolectric tạo Application (và thư mục dữ liệu) mới cho mỗi test. */
        @VisibleForTesting
        fun resetForTest() {
            instance?.close()
            instance = null
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "time_for_bed.db")
                    .build().also { instance = it }
            }
    }
}

val DAY_SHORT = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")

fun AlarmEntity.daysText(): String = when (daysMask and 0x7F) {
    0 -> "Một lần"
    0x7F -> "Hằng ngày"
    0x1F -> "Thứ 2 – Thứ 6"
    0x60 -> "Cuối tuần"
    else -> DAY_SHORT.filterIndexed { i, _ -> daysMask and (1 shl i) != 0 }.joinToString(", ")
}
