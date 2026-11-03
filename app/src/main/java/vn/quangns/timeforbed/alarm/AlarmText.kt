package vn.quangns.timeforbed.alarm

/** Mẫu ngày lặp hay dùng: tên hiển thị → mặt nạ ngày (bit 0 = Thứ Hai … bit 6 = Chủ Nhật). */
val DAY_PRESETS = listOf(
    "Một lần" to 0,
    "T2 – T6" to 0x1F,
    "Cuối tuần" to 0x60,
    "Hằng ngày" to 0x7F,
)

/** Khoảng thời gian tới lúc chuông reo, viết bằng chữ: "7 giờ 20 phút", "45 phút", "1 ngày 3 giờ". */
fun untilText(ms: Long): String {
    if (ms < 60_000) return "dưới 1 phút"
    val totalMin = (ms + 59_999) / 60_000
    val days = totalMin / (24 * 60)
    val hours = (totalMin / 60) % 24
    val minutes = totalMin % 60
    return when {
        days > 0 -> listOfNotNull("$days ngày", "$hours giờ".takeIf { hours > 0 }).joinToString(" ")
        hours > 0 -> listOfNotNull("$hours giờ", "$minutes phút".takeIf { minutes > 0 }).joinToString(" ")
        else -> "$minutes phút"
    }
}
