package com.enwei.moneymanager.notify

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.R
import com.enwei.moneymanager.data.AppSettings
import com.enwei.moneymanager.ui.MainActivity
import com.enwei.moneymanager.ui.tr

/**
 * App 自己發出的所有通知：分成三大類（記帳、投資、系統）共七種，
 * 每種有自己的系統通知頻道（可在系統設定個別調整聲音、震動），也可以在 App 的「設定 → 通知」個別關閉。
 * 上層功能關閉（例如股票、AI、通知自動記帳）時，底下的通知一律不發。
 */
enum class NoticeKind(
    val channel: String, val label: String, val desc: String, val group: String,
    val importance: Int, val category: String,
) {
    RECORDED("records", "自動記帳結果", "每筆從通知自動記下或待確認的交易，可在通知上直接改分類、寫說明", GROUP_LEDGER,
        NotificationManager.IMPORTANCE_LOW, NotificationCompat.CATEGORY_STATUS),
    BUDGET("budget", "預算與月報", "支出跨過預算 80%、100% 時提醒一次；每月初整理上個月的收支", GROUP_LEDGER,
        NotificationManager.IMPORTANCE_DEFAULT, NotificationCompat.CATEGORY_REMINDER),
    BILLS("bills", "卡費與借貸", "扣款帳戶餘額不足、借貸要收付或快到期", GROUP_LEDGER,
        NotificationManager.IMPORTANCE_DEFAULT, NotificationCompat.CATEGORY_REMINDER),
    STOCK("stock_alerts", "股價提醒", "保本停利、減碼、目標價、停損與除權息", GROUP_INVEST,
        NotificationManager.IMPORTANCE_HIGH, NotificationCompat.CATEGORY_ALARM),
    REPORT("reports", "報告與助理", "投資報告完成或失敗、股票助理回覆", GROUP_INVEST,
        NotificationManager.IMPORTANCE_DEFAULT, NotificationCompat.CATEGORY_MESSAGE),
    PROGRESS("progress", "背景進度", "新聞更新、報告產生中的進度（不會響）", GROUP_INVEST,
        NotificationManager.IMPORTANCE_LOW, NotificationCompat.CATEGORY_PROGRESS),
    BACKUP("backup", "備份", "自動備份失敗時通知", GROUP_SYSTEM,
        NotificationManager.IMPORTANCE_DEFAULT, NotificationCompat.CATEGORY_ERROR),
    UPDATE("updates", "App 更新", "有新版本時通知", GROUP_SYSTEM,
        NotificationManager.IMPORTANCE_DEFAULT, NotificationCompat.CATEGORY_RECOMMENDATION);

    /** 上層功能有沒有開（關閉時這種通知不發、設定頁也不顯示） */
    fun available(s: AppSettings): Boolean = when (this) {
        RECORDED -> s.featureNotif
        STOCK -> s.featureStocks
        REPORT -> s.featureStocks && s.aiEnabled
        // 新聞更新的進度：沒有 AI 時也會蒐集新聞
        PROGRESS -> s.featureStocks
        BUDGET, BILLS, BACKUP -> true
        UPDATE -> com.enwei.moneymanager.BuildConfig.SELF_UPDATE
    }

    /** App 內的開關（股價提醒沿用原本的「股價提醒」開關，關掉時連檢查都不做） */
    fun enabled(s: AppSettings): Boolean = available(s) && name !in s.noticeOff && (this != STOCK || s.stockAlertsOn)
}

const val GROUP_LEDGER = "g_ledger"
const val GROUP_INVEST = "g_invest"
const val GROUP_SYSTEM = "g_system"

object AppNotices {
    val groups = listOf(GROUP_LEDGER to "記帳", GROUP_INVEST to "投資", GROUP_SYSTEM to "系統")

    /** 建立頻道與分組；舊版的「提醒」頻道拆成預算、卡費、股價、報告四個後移除。 */
    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannelGroups(groups.map { (id, l) -> NotificationChannelGroup(id, tr(l)) })
        nm.createNotificationChannels(NoticeKind.entries.map { k ->
            NotificationChannel(k.channel, tr(k.label), k.importance).apply { description = tr(k.desc); group = k.group }
        })
        runCatching { nm.deleteNotificationChannel("alerts") }
    }

    /** 這種通知現在能不能發：App 內開關、系統總開關、系統頻道都要開著。 */
    fun allowed(context: Context, kind: NoticeKind): Boolean {
        if (!kind.enabled(MoneyApp.repo.settings.value)) return false
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return false
        return nm.getNotificationChannel(kind.channel)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** 點通知要打開的畫面：同一個 App 視窗切過去（不另開一份），每則通知各自的 requestCode 才不會互相覆蓋。 */
    fun openIntent(context: Context, route: String, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context, requestCode,
        Intent(context, MainActivity::class.java)
            .setAction("open:$route")
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_ROUTE, route),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * 統一版型：小標（類別）· 標題 · 一行摘要；展開後顯示完整內容（[lines] 每項一行）。
     * [route] 是點通知要打開的畫面。回傳 builder 給呼叫端加按鈕；用 [show] 發出。
     */
    fun builder(
        context: Context, kind: NoticeKind, id: Int, title: String, text: String, route: String,
        lines: List<String> = emptyList(), whenMs: Long? = null,
    ): NotificationCompat.Builder {
        val body = lines.filter { it.isNotBlank() }
        return NotificationCompat.Builder(context, kind.channel)
            .setSmallIcon(R.drawable.ic_stat_ledger)
            .setColor(0xFFF0605A.toInt())
            .setSubText(tr(kind.label))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (body.isEmpty()) text else body.joinToString("\n")))
            .setCategory(kind.category)
            .setGroup(kind.channel)
            .setContentIntent(openIntent(context, route, id))
            .setAutoCancel(true)
            .addExtras(android.os.Bundle().apply { putString(EXTRA_ROUTE_KEY, route) })
            .apply { if (whenMs != null) setWhen(whenMs).setShowWhen(true) }
    }

    fun show(context: Context, kind: NoticeKind, id: Int, b: NotificationCompat.Builder) {
        if (!allowed(context, kind)) return
        context.getSystemService(NotificationManager::class.java).notify(id, b.build())
    }

    fun post(
        context: Context, kind: NoticeKind, id: Int, title: String, text: String, route: String,
        lines: List<String> = emptyList(), whenMs: Long? = null,
    ) = show(context, kind, id, builder(context, kind, id, title, text, route, lines, whenMs))

    /** 打開某個畫面時，收起相關、已經看到的通知。 */
    fun clearFor(context: Context, route: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        runCatching {
            nm.activeNotifications.filter { it.notification.contentIntent != null && it.tag == null }
                .filter { n -> n.notification.extras?.getString(EXTRA_ROUTE_KEY) == route }
                .forEach { nm.cancel(it.id) }
        }
    }

    const val EXTRA_ROUTE_KEY = "ledgerly.route"
}
