# Time for Bed 🌙

Ứng dụng Android giúp đi ngủ đúng giờ — **không cần root**, chạy nền:

- **Hẹn giờ ngủ**: kéo vòng tròn chọn 0–60 phút, bấm nút tròn xanh **Bắt đầu**; giữa vòng hiện đếm ngược đến từng giây. Hết giờ thì dừng mọi video/nhạc, về màn hình chính và khóa máy. Báo trước 1 phút, có nút **+10 phút** ngay trên thông báo.
- **Tự tắt khi ngủ quên**: trong khung giờ đêm (mặc định **00:00–06:00**), nếu quá **30 phút** không chạm/thao tác thì tự dừng video/nhạc và khóa máy. Khung giờ và ngưỡng chỉnh được, hỗ trợ khung vắt qua nửa đêm (vd 23:00–06:00).
- **Báo thức**: nhiều báo thức, lặp theo thứ, chọn chuông, rung, báo lại 5/10/15 phút; reo toàn màn hình cả khi máy đang khóa; tự đặt lại sau khi khởi động lại máy.
- **Ngoại lệ**: không bao giờ đụng tới app đồng hồ/báo thức và các app ngân hàng, ví điện tử (tự gợi ý VCB, BIDV, VietinBank, MB, Techcombank, VNPT Money, MoMo, ZaloPay…; thêm/bớt được trong tab Cài đặt).

Yêu cầu: Android 8.0 (API 26) trở lên.

Giao diện theo [DESIGN.md](DESIGN.md) — phong cách "nửa đêm tĩnh lặng", viết theo quy trình skill
`taste-design` của [Google Stitch Skills](https://github.com/google-labs-code/stitch-skills).

## Cài đặt

1. Tải `TimeForBed-<phiên bản>.apk` ở mục [Releases](../../releases) và cài (cho phép "Cài app không rõ nguồn gốc").
2. Mở app → tab **Cài đặt** → bật lần lượt các quyền:

| Quyền | Để làm gì |
|---|---|
| Truy cập thông báo | Thấy và tạm dừng trình phát của YouTube, Spotify, TikTok, Zing MP3… |
| Trợ năng (Accessibility) | Phát hiện thao tác trong khung giờ đêm; về màn hình chính + khóa máy |
| Hiển thị thông báo | Đếm ngược, cảnh báo, chuông báo thức |
| Báo thức chính xác (Android 12) | Hẹn giờ/báo thức đúng phút |
| Hiện toàn màn hình (Android 14+) | Màn hình báo thức trên màn hình khóa |
| Bỏ tối ưu pin | Không bị hệ thống tắt khi chạy nền qua đêm |

> **Android 13+ không bật được Trợ năng / Truy cập thông báo?** Đây là chặn "cài đặt bị hạn chế" với app cài ngoài CH Play. Vào *Cài đặt → Ứng dụng → Time for Bed → menu ⋮ → Cho phép cài đặt bị hạn chế*, rồi bật lại.
>
> **Xiaomi / Oppo / Vivo / Realme**: bật *Tự khởi chạy*, đặt Pin = *Không hạn chế*, khóa app trong màn hình đa nhiệm.

## Cách hoạt động (không root)

Dừng nhiều lớp, leo thang cho tới khi im lặng — áp dụng cho **mọi app**, không theo danh sách định trước:

1. **MediaSession** (cần quyền Truy cập thông báo): gửi `pause` cho mọi phiên đang phát (YouTube, Spotify, Zing MP3…).
2. **Phím Tạm dừng** gửi tới phiên phát gần nhất, và **giành audio focus** để app tuân thủ focus tự dừng.
3. **Sau 1,5 giây** còn tiếng → về màn hình chính qua Accessibility: app video không báo trình phát (TikTok, Facebook, trình duyệt…) tự dừng khi bị đẩy xuống nền. Nếu bật *Về màn hình chính và khóa máy* thì làm ngay từ đầu.
4. **Sau 4 giây** vẫn còn tiếng → **tắt tiếng luồng media** của hệ thống (chuông báo thức dùng luồng riêng nên vẫn reo). Âm lượng tự trả lại khi mở khóa máy, hoặc bấm *Bật lại âm thanh* trên thông báo.

Không đụng tới app đồng hồ/báo thức và app trong danh sách loại trừ (ngân hàng…). Mỗi lần dừng có báo cáo ở màn Hẹn giờ; *Cài đặt → Kiểm tra tắt media* để xem app nhìn thấy trình phát nào.

4. **Theo dõi thao tác**: Accessibility service chỉ ghi nhận *thời điểm* chạm/cuộn/gõ phím/mở khóa — không đọc nội dung màn hình (`canRetrieveWindowContent=false`). Mỗi 30 giây kiểm tra: đang trong khung giờ + quá ngưỡng + (có media đang phát hoặc màn hình đang bật) → tắt.
5. Báo thức dùng `AlarmManager.setAlarmClock()` và phát chuông trên luồng **ALARM**, nên không bị chính app tắt.

### Giới hạn

- Bước tắt tiếng media là chốt chặn cuối; nếu một app loại trừ (ngân hàng) đang hiện trên màn hình thì app không tắt tiếng.
- Không bật Trợ năng thì không về màn hình chính / khóa máy được, chỉ còn bước tắt tiếng.
- Android 8.x không có lệnh khóa máy qua Accessibility → chỉ về màn hình chính.
- Một số ROM có thể tự tắt Accessibility sau khi cập nhật/khởi động lại — app hiện cảnh báo đỏ khi thiếu quyền.

## Build

Cần JDK 17 và Android SDK (platform 35, build-tools 35.0.0).

```bash
export JAVA_HOME=/path/to/jdk17 ANDROID_HOME=/path/to/android-sdk
./gradlew testDebugUnitTest lintDebug assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Bản release đọc khóa ký từ `keystore.properties` ở thư mục gốc (không commit):

```properties
storeFile=/đường/dẫn/timeforbed-release.jks
storePassword=...
keyAlias=timeforbed
keyPassword=...
```

```bash
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk (R8 + ký)
```

## Kiểm thử trên máy thật

1. Cấp đủ quyền (tab Cài đặt không còn mục đỏ).
2. Mở YouTube/Spotify → tab *Hẹn giờ* → kéo vòng tới 1 phút → **Bắt đầu** → sau 1 phút media dừng, về màn hình chính, máy khóa.
3. Tab *Ban đêm*: đặt khung giờ bao giờ hiện tại, ngưỡng 5 phút, để video chạy không chạm → phút thứ 4 có cảnh báo, phút thứ 5 tự tắt.
4. Phát âm thanh trong app ngân hàng/đồng hồ đã loại trừ → không bị tác động.
5. Đặt báo thức 2 phút sau, khóa máy → chuông + màn hình báo thức; thử *Báo lại* / *Tắt*; khởi động lại máy → báo thức vẫn còn.

## Cấu trúc mã

```
app/src/main/java/vn/quangns/timeforbed/
  core/     MediaKiller (dừng media), Exclusions (loại trừ), Prefs, Permissions, TimeRules/NightRules
  service/  BedtimeAccessibilityService, MediaNotificationListener, SleepTimer(+Service/Receiver), BootReceiver
  alarm/    Room (AlarmEntity/AlarmDao), AlarmScheduler, AlarmReceiver, AlarmRingService, AlarmRingActivity
  ui/       Compose: Hẹn giờ, Ban đêm, Báo thức, Cài đặt
app/src/test/  Unit test khung giờ, lịch báo thức, quyết định chế độ đêm, danh sách loại trừ
```
