package com.enwei.moneymanager.notify

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.R
import com.enwei.moneymanager.core.ScreenRecordParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 錢包畫面讀取：使用者打開街口、悠遊付等 App 的「交易紀錄」頁時，讀取畫面文字並匯入交易。
 * 只處理使用者在設定中勾選的 App，其餘 App 的畫面一律忽略。
 */
class WalletScreenReader : AccessibilityService() {
    private var pendingJob: Job? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in MoneyApp.repo.settings.value.screenPackages) return
        // 錢包畫面讀取：使用者在測試頁時記錄每一次讀取的結果
        // 畫面捲動、載入時會連續觸發，等畫面穩定 1.2 秒後再讀
        pendingJob?.cancel()
        pendingJob = MoneyApp.instance.appScope.launch {
            delay(1200)
            val texts = runCatching { collectTexts(pkg) }.getOrNull() ?: return@launch
            if (texts.isEmpty()) return@launch
            var records = ScreenRecordParser.parse(texts)
            var byAi = false
            // 版面規則看不懂：同一個畫面只請 AI 整理一次（畫面沒變就不重送）
            if (records.isEmpty() && com.enwei.moneymanager.net.GroqAi.enabled) {
                val sig = texts.hashCode()
                if (aiSeen[pkg] != sig) {
                    aiSeen[pkg] = sig
                    records = com.enwei.moneymanager.net.GroqAi.parseScreen(texts, java.time.LocalDate.now())
                    byAi = records.isNotEmpty()
                }
            }
            if (records.isEmpty()) {
                record(ScanEntry(System.currentTimeMillis(), pkg, texts.size, emptyList(), null, "讀到 ${texts.size} 行文字，但沒有符合交易紀錄的格式（請打開「交易紀錄／明細」頁）"))
                return@launch
            }
            val test = testMode.value
            val r = runCatching { MoneyApp.repo.ingestScreenRecords(pkg, records, dryRun = test) }.getOrNull()
            record(ScanEntry(System.currentTimeMillis(), pkg, texts.size, records, r, null, byAi))
            if (!test && r != null && r.created > 0) notifyImported(r.created)
            if (!test && r != null && (r.created > 0 || r.merged > 0)) MoneyApp.repo.scheduleAutoTidy(MoneyApp.instance.appScope)
        }
    }

    private fun collectTexts(pkg: String): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        // 等待期間使用者可能已切到別的 App：只讀取仍屬於該錢包的畫面
        if (root.packageName?.toString() != pkg) return emptyList()
        val out = ArrayList<String>()
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 40) return
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { t -> t.split('\n').forEach { out += it } }
                ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() && node.childCount == 0 }?.let { out += it }
            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
        }
        walk(root, 0)
        return out
    }

    private fun notifyImported(count: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        nm.notify(
            9100,
            NotificationCompat.Builder(this, MoneyApp.CHANNEL_RECORDS)
                .setSmallIcon(R.drawable.ic_stat_ledger)
                .setContentTitle("已從錢包畫面讀取 $count 筆交易")
                .setContentText(if (MoneyApp.repo.settings.value.notifAutoConfirm) "已自動入帳" else "請到「待確認」檢查")
                .setAutoCancel(true)
                .build(),
        )
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
    }

    override fun onDestroy() {
        connected = false
        super.onDestroy()
    }

    /** 一次讀取的紀錄（測試頁顯示用）。 */
    data class ScanEntry(
        val time: Long, val pkg: String, val lines: Int,
        val records: List<com.enwei.moneymanager.core.ScreenRecord>,
        val result: com.enwei.moneymanager.data.Repository.ScreenIngestResult?, val problem: String?,
        /** 版面規則看不懂，由 AI 從畫面文字整理出來的 */
        val byAi: Boolean = false,
    )

    companion object {
        /** 測試模式：只讀取與辨識，不寫入任何交易 */
        val testMode = kotlinx.coroutines.flow.MutableStateFlow(false)
        /** 最近 10 次讀取 */
        val scans = kotlinx.coroutines.flow.MutableStateFlow<List<ScanEntry>>(emptyList())
        @Volatile var connected = false
            private set

        /** 每個 App 上次請 AI 整理的畫面（文字的 hash），畫面沒變不重送 */
        private val aiSeen = java.util.concurrent.ConcurrentHashMap<String, Int>()

        private fun record(e: ScanEntry) { scans.value = (listOf(e) + scans.value).take(10) }

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(context, WalletScreenReader::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        fun openSettings(context: Context) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
