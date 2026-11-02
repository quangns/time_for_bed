package vn.quangns.timeforbed.core

import android.content.Context
import android.content.Intent

data class AppInfo(val packageName: String, val label: String)

/** Danh sách app không bao giờ bị tắt (báo thức, ngân hàng, ví điện tử…). */
object Exclusions {

    /** App đồng hồ/báo thức hệ thống của các hãng phổ biến — luôn loại trừ. */
    val CLOCK_PACKAGES = setOf(
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.miui.clock",
        "com.android.alarmclock",
        "com.coloros.alarmclock",
        "com.oplus.alarmclock",
        "com.oneplus.deskclock",
        "com.huawei.deskclock",
        "com.hihonor.deskclock",
        "com.vivo.alarmclock",
        "com.bbk.alarmclock",
        "com.asus.deskclock",
        "com.lge.clock",
        "com.sonyericsson.organizer",
        "com.motorola.timeweatherwidget",
        "com.transsion.deskclock",
        "com.realme.alarmclock",
    )

    /** Gói ngân hàng/ví điện tử Việt Nam thường gặp (gợi ý khi đã cài). */
    val KNOWN_FINANCE = setOf(
        "com.VCB",
        "com.vnpay.bidv",
        "com.vietinbank.ipay",
        "vn.com.techcombank.bb.app",
        "com.mbmobile",
        "mobile.acb.com.vn",
        "com.tpb.mb.gprsandroid",
        "com.vnpay.vpbankonline",
        "com.vnpay.Agribank3g",
        "com.sacombank.ewallet",
        "com.vib.myvib2",
        "com.vnpay.hdbank",
        "vn.shb.mbanking",
        "vn.com.ocb.awe",
        "com.msb.mbanking",
        "com.seabank.mbank",
        "com.lpb.lienviet24h",
        "vn.cake.app",
        "vn.timo.mobile",
        "com.mservice.momotransfer",
        "vn.com.vng.zalopay",
        "vn.vnpt.vnptmoney",
        "com.vnpt.vnptpay",
        "com.viettel.vtpay",
        "com.shopeepay.vn",
    )

    private val FINANCE_KEYWORDS = listOf(
        "bank", "banking", "pay", "wallet", "money", "momo", "ngân hàng", "ngan hang", "ví ",
    )

    fun isClockApp(pkg: String): Boolean =
        pkg in CLOCK_PACKAGES || pkg.contains("deskclock") || pkg.contains("alarmclock") ||
            pkg.contains("clockpackage")

    fun looksLikeFinance(pkg: String, label: String): Boolean {
        if (pkg in KNOWN_FINANCE) return true
        val p = pkg.lowercase()
        val l = label.lowercase()
        return FINANCE_KEYWORDS.any { p.contains(it) || l.contains(it) }
    }

    /** Có được bỏ qua (không tắt) gói này không. */
    fun isExcluded(pkg: String?, selfPackage: String, userExcluded: Set<String>): Boolean {
        if (pkg == null) return false
        return pkg == selfPackage || isClockApp(pkg) || pkg in userExcluded
    }

    fun isExcluded(context: Context, pkg: String?): Boolean =
        isExcluded(pkg, context.packageName, Prefs.get(context).excluded)

    /** App có biểu tượng trên launcher, sắp theo tên. */
    fun launchableApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun suggestFinance(context: Context): Set<String> =
        launchableApps(context).filter { looksLikeFinance(it.packageName, it.label) }
            .map { it.packageName }.toSet()

    /** Lần chạy đầu: tự tick sẵn các app ngân hàng/ví đã cài. */
    fun initDefaults(context: Context) {
        val prefs = Prefs.get(context)
        if (prefs.exclusionsInitialized) return
        prefs.excluded = suggestFinance(context)
        prefs.exclusionsInitialized = true
    }
}
