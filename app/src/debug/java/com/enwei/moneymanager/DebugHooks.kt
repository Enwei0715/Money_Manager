package com.enwei.moneymanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.enwei.moneymanager.core.Market
import com.enwei.moneymanager.core.StockSide
import com.enwei.moneymanager.data.AccountEntity
import com.enwei.moneymanager.data.AccountType
import com.enwei.moneymanager.data.CategoryEntity
import com.enwei.moneymanager.data.PlaceHints
import com.enwei.moneymanager.data.Repository
import com.enwei.moneymanager.data.StockTradeEntity
import com.enwei.moneymanager.data.TxnEntity
import com.enwei.moneymanager.data.TxnSource
import com.enwei.moneymanager.data.TxnType
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * 只在測試版（debug）才有的自動化介面檢查入口（正式版沒有這個檔案）：
 * - DEMO：灌入一組假資料（帳戶、分類、交易、店家位置、股票），並關掉第一次使用的導覽；
 * - NOTIFY：模擬收到一則通知（extras：pkg、title、text），走和真的通知完全相同的記帳流程。
 */
class DebugHooks : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        MoneyApp.instance.appScope.launch {
            try {
                when (intent.action) {
                    "com.enwei.moneymanager.DEMO" -> demo(context)
                    "com.enwei.moneymanager.NOTIFY" -> MoneyApp.repo.ingestNotificationBatch(listOf(Repository.NotificationInput(
                        intent.getStringExtra("pkg") ?: "com.test.bank", intent.getStringExtra("title"), intent.getStringExtra("text"), System.currentTimeMillis(),
                    )))
                }
            } finally { pending.finish() }
        }
    }

    private suspend fun demo(context: Context) {
        val repo = MoneyApp.repo
        repo.ensureSeedData()
        if (repo.db.accounts().all().any { it.name == "測試銀行" }) return
        // 導覽與頁面介紹全部略過，畫面才看得到內容
        context.getSharedPreferences("page_tours", Context.MODE_PRIVATE).edit().putBoolean("__all__", true).apply()
        repo.settings.update {
            it.copy(onboarded = true, locationHints = true, featureNotif = true, featureSplit = true, featureStocks = true, language = 1,
                notifPackages = it.notifPackages + setOf("jp.naver.line.android", "com.test.bank", "com.test.card"),
                watchlist = listOf("2317", "2454"))
        }
        val bank = repo.saveAccount(AccountEntity(name = "測試銀行", type = AccountType.BANK, initialBalance = 5_000_000, cardLast4 = "1234"))
        val cash = repo.saveAccount(AccountEntity(name = "錢包現金", type = AccountType.CASH, initialBalance = 300_000))
        val card = repo.saveAccount(AccountEntity(name = "測試信用卡", type = AccountType.CREDIT_CARD, cardLast4 = "5908", statementDay = 23, dueDay = 7, autopayFromId = bank))
        val line = repo.saveAccount(AccountEntity(name = "LINE Pay Money", type = AccountType.EWALLET, linkedAccountId = bank))
        val broker = repo.saveAccount(AccountEntity(name = "測試證券", type = AccountType.BROKERAGE))
        repo.saveAccount(AccountEntity(name = "悠遊卡", type = AccountType.TRANSIT_CARD, initialBalance = 50_000))
        // 和真實使用者一樣的兩層分類：飲食／早餐、早午餐、午餐、晚餐、飲料；旅遊／旅遊餐飲
        suspend fun cat(name: String, parent: Long? = null) = repo.saveCategory(CategoryEntity(name = name, kind = TxnType.EXPENSE, icon = "", parentId = parent))
        val food = cat("飲食")
        val breakfast = cat("早餐", food); cat("早午餐", food); val lunch = cat("午餐", food); val dinner = cat("晚餐", food); val drink = cat("飲料", food)
        val travel = cat("旅遊"); cat("旅遊餐飲", travel)
        val daily = repo.db.categories().all().first { it.name == "日用品" }.id
        val zone = ZoneId.systemDefault()
        fun at(daysAgo: Long, h: Int, m: Int = 0) = LocalDate.now().minusDays(daysAgo).atTime(h, m).atZone(zone).toInstant().toEpochMilli()
        suspend fun tx(daysAgo: Long, h: Int, amount: Long, merchant: String, note: String?, catId: Long, acc: Long) =
            repo.saveTxn(TxnEntity(type = TxnType.EXPENSE, amount = amount * 100, accountId = acc, categoryId = catId, merchant = merchant, note = note, date = at(daysAgo, h), source = TxnSource.MANUAL))
        // 習慣：攤位寫說明、店家寫餐廳
        listOf(1L, 3L, 5L).forEach { tx(it, 12, 95, "台科大第一餐廳", "泰刻", lunch, cash) }
        listOf(2L, 6L).forEach { tx(it, 18, 120, "台科大第一餐廳", "溫州大餛飩", dinner, cash) }
        listOf(4L, 8L, 9L).forEach { tx(it, 12, 85, "台科大第二餐廳", "京都日式料理", lunch, card) }
        listOf(1L, 2L, 4L, 7L).forEach { tx(it, 8, 45, "萊爾富 台科大店", null, breakfast, line) }
        listOf(3L, 10L).forEach { tx(it, 19, 420, "全聯福利中心 永和永福店", "食材", daily, card) }
        tx(5, 15, 60, "Family", "豆漿", drink, line)
        // 店家位置（之後在這附近記帳會自動帶入）
        PlaceHints.setMerchantPin("台科大第一餐廳", 25.01330, 121.54060, "測試")
        PlaceHints.setMerchantPin("台科大第二餐廳", 25.01290, 121.54150, "測試")
        PlaceHints.setMerchantPin("萊爾富 台科大店", 25.01390, 121.54100, "測試")
        // 股票：持股兩檔
        repo.saveTrade(StockTradeEntity(market = Market.TW, symbol = "2330", name = "台積電", side = StockSide.BUY, shares = 10.0, price = 1000.0, fee = 20.0,
            date = LocalDate.now().minusDays(30).toEpochDay(), cashAccountId = bank, brokerAccountId = broker))
        repo.saveTrade(StockTradeEntity(market = Market.TW, symbol = "2344", name = "華邦電", side = StockSide.BUY, shares = 100.0, price = 150.0, fee = 21.0,
            date = LocalDate.now().minusDays(20).toEpochDay(), cashAccountId = bank, brokerAccountId = broker))
    }
}
