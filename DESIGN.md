# Design System: Time for Bed

> Viết theo quy trình skill `taste-design` của [Google Stitch Skills](https://github.com/google-labs-code/stitch-skills).
> Không dùng Stitch MCP sinh ảnh; tài liệu này là nguồn chuẩn duy nhất khi sửa giao diện Compose.

## 1. Visual Theme & Atmosphere

Một căn phòng lúc nửa đêm: tối, tĩnh, chỉ còn một nguồn sáng xanh dịu ở giữa. Giao diện
phải dễ nhìn khi mắt đã mỏi, không làm người dùng tỉnh lại.

- **Density 3** – thoáng, mỗi màn chỉ một việc chính, khoảng trống rộng.
- **Variance 3** – bố cục đối xứng, ổn định; vòng hẹn giờ là trọng tâm duy nhất ở giữa.
- **Motion 4** – chuyển động chậm, mềm (spring), một nhịp "thở" duy nhất trên nút Bắt đầu.

## 2. Color Palette & Roles

- **Night Ink** (#0B0F1A) – nền toàn màn hình. Không bao giờ dùng đen tuyệt đối.
- **Dusk Surface** (#121826) – mặt khối nhóm nội dung (thay cho card nổi).
- **Raised Dusk** (#1A2233) – trạng thái nhấn, thanh điều hướng, ô nhập.
- **Hairline** (#232C3F) – đường kẻ 1dp phân tách hàng, viền khối.
- **Moonlight** (#E7E9EE) – chữ chính, chữ số đếm ngược.
- **Mist** (#8A93A6) – chữ phụ, mô tả, nhãn nhóm.
- **Sleep Green** (#3DBE7A) – **màu nhấn duy nhất**: nút Bắt đầu, cung vòng hẹn giờ, công tắc bật,
  chỉ báo tab đang chọn, trạng thái "đã cấp quyền".
- **Ember** (#E5866B) – chỉ dùng cho cảnh báo chức năng (thiếu quyền, xóa). Không dùng trang trí.

Cấm: gradient neon, tím phát sáng, đổ bóng màu, nhiều hơn một màu nhấn.

## 3. Typography Rules

- **Display (số đếm ngược, giờ báo thức):** họ sans của hệ thống, `Light` (300), tracking -1%,
  **chữ số tabular** (`tnum`) để số không nhảy chiều rộng mỗi giây.
- **Headline (tiêu đề màn):** sans `SemiBold` 24sp, căn trái.
- **Label nhóm:** 12sp, `Medium`, chữ hoa, tracking +8%, màu Mist.
- **Body:** 15sp, leading 22sp, màu Moonlight; mô tả 13sp màu Mist.
- Cấm: serif, chữ gradient, chữ in đậm cả đoạn.

## 4. Component Stylings

- **Nút chính (Bắt đầu):** hình tròn, nền Sleep Green, chữ Night Ink; không glow. Khi nhấn co 0.96
  (spring). Lúc chờ có nhịp thở scale 1.00 → 1.03, chu kỳ 3.2s.
- **Nút phụ:** viền Hairline, chữ Moonlight, bo 16dp, cao ≥ 52dp. Nút huỷ dạng chữ (text button).
- **Khối nội dung (Section):** nền Dusk Surface, bo 24dp, viền Hairline 1dp, **không đổ bóng**.
  Nhãn nhóm nằm phía trên, ngoài khối.
- **Hàng cài đặt:** tiêu đề + mô tả bên trái, điều khiển bên phải, cao ≥ 64dp, ngăn cách bằng Hairline.
- **Công tắc:** track bật Sleep Green, thumb Night Ink; track tắt Raised Dusk.
- **Vòng hẹn giờ:** track Hairline 10% bề rộng; cung Sleep Green đầu tròn; núm kéo trắng viền xanh;
  vạch chia 5 phút màu Mist, vạch 0/15/30/45 dài hơn.
- **Trạng thái rỗng:** biểu tượng nét mảnh + một câu hướng dẫn cách thêm, không chỉ "Không có dữ liệu".
- **Chấm trạng thái:** tròn 8dp, Sleep Green = ổn, Ember = cần xử lý.

## 5. Layout Principles

- Một cột, lề ngang 20dp, khoảng cách giữa nhóm 28dp, trong nhóm 0 (dùng Hairline).
- Tiêu đề màn căn trái ở đầu; vòng hẹn giờ chiếm tối đa 320dp, căn giữa.
- Mọi vùng chạm ≥ 48dp. Không phần tử nào đè lên phần tử khác.
- Thanh điều hướng dưới: nền Raised Dusk, chỉ báo tab là viên thuốc Sleep Green mờ 16%.

## 6. Motion & Interaction

- Spring mặc định: `dampingRatio 0.8, stiffness 200` (mềm, có trọng lượng). Không easing tuyến tính.
- Cung vòng hẹn giờ animate theo spring khi kéo/đổi trạng thái.
- Chuyển giữa "chọn thời gian" và "đang đếm" bằng crossfade 350ms.
- Rung nhẹ (haptic) mỗi khi qua một phút khi kéo vòng.
- Chỉ animate scale/alpha, không animate kích thước layout.

## 7. Anti-Patterns (Banned)

- Không emoji trong giao diện.
- Không đen tuyệt đối (#000000), không glow/neon, không gradient trang trí.
- Không quá một màu nhấn; Ember chỉ cho cảnh báo.
- Không card nổi đổ bóng; không lưới 3 cột bằng nhau.
- Không câu chữ sáo rỗng, không số liệu bịa.
- Không chữ hướng dẫn kiểu "Vuốt để khám phá".
