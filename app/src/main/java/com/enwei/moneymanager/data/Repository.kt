package com.enwei.moneymanager.data

import androidx.room.withTransaction
import com.enwei.moneymanager.core.CardCycle
import com.enwei.moneymanager.core.CategoryGuesser
import com.enwei.moneymanager.core.CategorySuggester
import com.enwei.moneymanager.core.BankDirectory
import com.enwei.moneymanager.core.FxConverter
import com.enwei.moneymanager.core.ParsedKind
import com.enwei.moneymanager.core.ScreenRecord
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.core.NotificationParser
import com.enwei.moneymanager.core.ParsedType
import com.enwei.moneymanager.core.Recurrence
import com.enwei.moneymanager.core.StockCalc
import com.enwei.moneymanager.core.StockSide
import com.enwei.moneymanager.core.Market
import com.enwei.moneymanager.core.TradeRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

fun LocalDate.toMillis(time: LocalTime = LocalTime.NOON): Long =
    atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

data class AccountBalance(val account: AccountEntity, val balance: Long)

data class HoldingView(
    val market: Market,
    val symbol: String,
    val name: String?,
    val currency: String,
    val shares: Double,
    val avgCost: Double,
    val cost: Double,
    val price: Double?,
    val previousClose: Double?,
    /** 已實現損益（先進先出；已出清部分的股利也算在內） */
    val realizedPnl: Double,
    /** 目前庫存已領的現金股利（除息），從成本扣除 */
    val dividends: Double,
    /** 現在全部賣出的預估手續費＋交易稅（逐批計算，同券商） */
    val estSellCost: Double = 0.0,
    /** 每一批買進的庫存（券商「台股庫存明細」） */
    val lots: List<com.enwei.moneymanager.core.Lot> = emptyList(),
    /** 每一批的預估損益（key 為買進交易 id） */
    val lotPnl: Map<Long, Double> = emptyMap(),
) {
    val marketValue: Double get() = shares * (price ?: avgCost)
    private val open get() = shares > 0.0001
    val openDividends: Double get() = if (open) dividends else 0.0
    /** 券商「原幣成本」合計：價金＋手續費－已領股利 */
    val netCost: Double get() = cost - openDividends
    /** 預估損益＝各批（市值－賣出手續費與交易稅－原幣成本）加總 */
    val unrealized: Double get() = if (price == null || !open) 0.0 else lotPnl.values.sum()
    val realizedTotal: Double get() = realizedPnl
    val unrealizedPct: Double get() = if (netCost > 0) unrealized / netCost * 100 else 0.0
    val dayChange: Double get() = if (price != null && previousClose != null) (price - previousClose) * shares else 0.0
}

@Serializable
data class Backup(
    val version: Int = 1,
    val exportedAt: Long,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TxnEntity>,
    val recurring: List<RecurringEntity>,
    val trades: List<StockTradeEntity>,
    val rules: List<MerchantRuleEntity>,
    val bookmarks: List<Bookmark> = emptyList(),
    val memos: List<Memo> = emptyList(),
    val budgets: Map<Long, Long> = emptyMap(),
    val notifPackages: Set<String> = emptySet(),
    val notifAccountMap: Map<String, Long> = emptyMap(),
    val brokerRebates: Map<Long, RebateRule> = emptyMap(),
    val learnedSources: List<LearnedSource> = emptyList(),
    val cashCounts: Map<Long, Map<Int, Int>> = emptyMap(),
    val cardShift: Map<Long, String> = emptyMap(),
    val cardBank: Map<Long, String> = emptyMap(),
    val cardDueOffset: Map<Long, Int> = emptyMap(),
)

data class CardStatus(val payable: Long, val outstanding: Long, val dueDate: LocalDate?, val autopayDate: LocalDate? = null)

private const val TRANSFER_TAG = "#轉帳"
private const val GMAIL_PACKAGE = "com.google.android.gm"
private const val AI_TAG = "#AI"

data class IngestResult(val kind: String, val txnId: Long? = null, val message: String)

class Repository(internal val db: AppDatabase, val settings: SettingsStore) {
    val accounts = db.accounts().observeAll()
    val categories = db.categories().observeAll()
    val transactions = db.txns().observeAll()
    val pending = db.txns().observePending()
    val recurring = db.recurring().observeAll()
    val trades = db.stocks().observeTrades()
    val quotes = db.stocks().observeQuotes()
    val fxRates = db.fx().observeAll()
    val rules = db.rules().observeAll()
    val notificationLog = db.notificationLog().observeRecent()

    val fx: Flow<FxConverter> = combine(fxRates, settings.flow) { rates, s ->
        FxConverter(s.baseCurrency, rates.associate { it.currency to it.rate })
    }

    fun transactionsInRange(from: Long, to: Long) = db.txns().observeRange(from, to)

    // ---------- 餘額與淨資產 ----------

    val balances: Flow<List<AccountBalance>> = combine(accounts, transactions, trades, fx) { accs, txns, trades, fx ->
        computeBalances(accs, txns, trades, fx)
    }

    private fun computeBalances(
        accs: List<AccountEntity>, txns: List<TxnEntity>, trades: List<StockTradeEntity>, fx: FxConverter,
    ): List<AccountBalance> {
        val currencyOf = accs.associate { it.id to it.currency }
        val delta = HashMap<Long, Long>()
        // 交易幣別與帳戶幣別不同（例如台幣信用卡刷美金）時，依匯率換算成帳戶幣別
        fun inAcc(amount: Long, cur: String, acc: Long) = fx.convert(amount, cur, currencyOf[acc] ?: cur)
        txns.filter { it.status == TxnStatus.CONFIRMED }.forEach { t ->
            when (t.type) {
                TxnType.EXPENSE -> t.accountId?.let { delta.merge(it, -inAcc(t.amount, t.currency, it), ::sumLong) }
                TxnType.INCOME -> t.accountId?.let { delta.merge(it, inAcc(t.amount, t.currency, it), ::sumLong) }
                TxnType.TRANSFER -> {
                    t.accountId?.let { delta.merge(it, -inAcc(t.amount, t.currency, it), ::sumLong) }
                    t.toAccountId?.let { delta.merge(it, t.toAmount ?: inAcc(t.amount, t.currency, it), ::sumLong) }
                }
            }
        }
        trades.forEach { tr ->
            val acc = tr.cashAccountId ?: return@forEach
            // 美股交割到台幣帳戶等跨幣別情況：依匯率換算成交割帳戶幣別
            val flow = Money.toMinor(StockCalc.cashFlow(tr.toRecord()))
            delta.merge(acc, fx.convert(flow, tr.currency, currencyOf[acc] ?: tr.currency), ::sumLong)
        }
        return accs.map { AccountBalance(it, it.initialBalance + (delta[it.id] ?: 0)) }
    }

    // 手續費設定變更時也要重算（預估賣出費用）
    val holdings: Flow<List<HoldingView>> = combine(trades, quotes, accounts, settings.flow) { trades, quotes, accs, s ->
        buildHoldings(trades, quotes, accs, s.feeSettings)
    }

    /** 各證券帳戶的持股（key 為證券帳戶 id，null 表示未指定）。 */
    val holdingsByBroker: Flow<Map<Long?, List<HoldingView>>> = combine(trades, quotes, accounts, settings.flow) { trades, quotes, accs, s ->
        // 指定給某一批的股利跟著那一批的證券帳戶
        val brokerOf = trades.associate { it.id to it.brokerAccountId }
        trades.groupBy { t -> t.lotTradeId?.let { brokerOf[it] } ?: t.brokerAccountId }
            .mapValues { (_, list) -> buildHoldings(list, quotes, accs, s.feeSettings) }
    }

    private fun buildHoldings(
        trades: List<StockTradeEntity>, quotes: List<QuoteEntity>, accounts: List<AccountEntity>, fees: com.enwei.moneymanager.core.FeeSettings,
    ): List<HoldingView> {
        val quoteMap = quotes.associateBy { it.key }
        val accMap = accounts.associateBy { it.id }
        return trades.groupBy { it.market to it.symbol.uppercase() }.mapNotNull { (key, list) ->
            val book = com.enwei.moneymanager.core.LotCalc.build(list.map { it.toRecord().copy(symbol = key.second) })
            if (list.none { it.side == StockSide.BUY || it.side == StockSide.STOCK_DIVIDEND }) return@mapNotNull null
            val q = quoteMap[quoteKey(key.first, key.second)]
            val broker = list.lastOrNull { it.brokerAccountId != null }?.brokerAccountId?.let { accMap[it] }
            val fs = broker?.feeDiscount?.let { fees.copy(discount = it) } ?: fees
            val price = q?.price
            HoldingView(
                market = key.first,
                symbol = key.second,
                name = list.firstNotNullOfOrNull { it.name } ?: q?.name,
                currency = list.first().currency,
                shares = book.shares,
                avgCost = if (book.shares > 0) book.cost / book.shares else 0.0,
                cost = book.cost,
                price = price,
                previousClose = q?.previousClose,
                realizedPnl = book.realizedPnl,
                dividends = book.dividends,
                estSellCost = price?.let { p -> book.lots.sumOf { it.sellCost(key.first, key.second, p, fs) } } ?: 0.0,
                lots = book.lots,
                lotPnl = price?.let { p -> book.lots.associate { it.tradeId to it.pnl(key.first, key.second, p, fs) } }.orEmpty(),
            )
        }.sortedByDescending { it.marketValue }
    }

    // ---------- 交易 ----------

    suspend fun getTxn(id: Long) = db.txns().get(id)
    suspend fun allTxns() = db.txns().all()

    suspend fun saveTxn(t: TxnEntity): Long {
        val r = db.txns().upsert(t)
        if (t.status == TxnStatus.CONFIRMED) learnMerchant(t)
        return if (t.id == 0L) r else t.id
    }

    suspend fun deleteTxn(id: Long) = db.txns().delete(id)

    /** 最近 90 天最常用的分類（通知上的一鍵分類按鈕用）。 */
    suspend fun frequentCategories(type: TxnType, limit: Int, exclude: Long?): List<CategoryEntity> {
        val since = System.currentTimeMillis() - 90L * 24 * 3600 * 1000
        val cats = db.categories().all().filter { it.kind == type && !it.archived }.associateBy { it.id }
        val counted = db.txns().all().filter { it.type == type && it.date >= since && it.categoryId != null }
            .groupingBy { it.categoryId!! }.eachCount()
        return counted.entries.sortedByDescending { it.value }.mapNotNull { cats[it.key] }
            .filter { it.id != exclude }.let { list -> (list + cats.values.filter { it.parentId == null && it.id != exclude }).distinctBy { it.id } }
            .take(limit)
    }

    /** 從通知上改分類／加說明：更新交易並記住商家分類。 */
    suspend fun quickUpdateTxn(id: Long, categoryId: Long? = null, note: String? = null) {
        val t = db.txns().get(id) ?: return
        val u = t.copy(
            categoryId = categoryId ?: t.categoryId,
            note = note?.trim()?.takeIf { it.isNotEmpty() }?.let { n -> listOfNotNull(t.note?.takeIf { it.isNotBlank() }, n).joinToString("　") } ?: t.note,
            status = if (categoryId != null) TxnStatus.CONFIRMED else t.status,
        )
        db.txns().upsert(u)
        if (categoryId != null) learnMerchant(u)
    }

    suspend fun confirmAllPending() {
        db.withTransaction {
            db.txns().all().filter { it.status == TxnStatus.PENDING }.forEach {
                db.txns().upsert(it.copy(status = TxnStatus.CONFIRMED))
                learnMerchant(it)
            }
        }
    }

    private suspend fun learnMerchant(t: TxnEntity) {
        val merchant = t.merchant?.trim()?.takeIf { it.length >= 2 && !CategoryGuesser.isGenericMerchant(it) } ?: return
        val cat = t.categoryId ?: return
        if (t.type == TxnType.TRANSFER) return
        db.rules().upsert(MerchantRuleEntity(merchant, cat))
    }

    suspend fun deleteRule(keyword: String) = db.rules().delete(keyword)

    /** 自動分類（含子分類）：商家規則 → 過去紀錄 → 內建關鍵字 → 同金額固定支出，詳見 [CategorySuggester]。 */
    suspend fun suggestCategory(merchant: String?, type: TxnType, at: Long, amountMinor: Long = 0, accountId: Long? = null): CategorySuggester.Suggestion? {
        if (type == TxnType.TRANSFER) return null
        val cats = db.categories().all().filter { it.kind == type && !it.archived }
            .map { CategorySuggester.Cat(it.id, it.name, it.parentId) }
        if (cats.isEmpty()) return null
        val rules = db.rules().all().associate { it.keyword to it.categoryId }
        val history = db.txns().all().filter { it.type == type && it.categoryId != null && it.status == TxnStatus.CONFIRMED }
            .map { CategorySuggester.Past(it.merchant ?: it.note, it.categoryId!!, it.date.toLocalDateTime(), it.amount, it.accountId) }
        return CategorySuggester.suggest(merchant, at.toLocalDateTime(), cats, rules, history, amountMinor, accountId)
    }

    /** 分類的完整名稱（大分類 / 子分類）。 */
    suspend fun categoryPath(id: Long): String? {
        val all = db.categories().all()
        val c = all.find { it.id == id } ?: return null
        return c.parentId?.let { pid -> all.find { it.id == pid }?.let { "${it.name} / ${c.name}" } } ?: c.name
    }

    private fun Long.toLocalDateTime(): java.time.LocalDateTime =
        java.time.Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

    // ---------- 帳戶 / 分類 ----------

    suspend fun saveAccount(a: AccountEntity): Long {
        val r = db.accounts().upsert(a)
        return if (a.id == 0L) r else a.id
    }

    suspend fun deleteAccount(a: AccountEntity) {
        db.withTransaction {
            db.txns().detachAccount(a.id)
            db.txns().detachToAccount(a.id)
            db.stocks().detachCash(a.id)
            db.stocks().detachBroker(a.id)
            db.accounts().detachAutopay(a.id)
            db.accounts().detachLinked(a.id)
            db.recurring().detachAccount(a.id)
            db.accounts().delete(a)
        }
        settings.update { s ->
            s.copy(
                defaultAccountId = s.defaultAccountId.takeIf { it != a.id },
                notifAccountMap = s.notifAccountMap.filterValues { it != a.id },
                cashCounts = s.cashCounts - a.id,
                cardShift = s.cardShift - a.id,
                cardBank = s.cardBank - a.id,
                cardDueOffset = s.cardDueOffset - a.id,
                brokerRebates = s.brokerRebates - a.id,
                learnedSources = s.learnedSources.map { if (it.accountId == a.id) it.copy(accountId = null) else it },
            )
        }
    }

    suspend fun saveCategory(c: CategoryEntity) = db.categories().upsert(c)

    /** 依序寫回排序。 */
    suspend fun reorderCategories(list: List<CategoryEntity>) = db.withTransaction {
        list.forEachIndexed { i, c -> if (c.sortOrder != i) db.categories().upsert(c.copy(sortOrder = i)) }
    }

    /**
     * 刪除分類：子分類的交易併入所屬大分類；大分類連同子分類刪除，交易變成未分類。
     */
    suspend fun deleteCategory(c: CategoryEntity) {
        val ids = listOf(c.id) + db.categories().all().filter { it.parentId == c.id }.map { it.id }
        val target = c.parentId
        db.withTransaction {
            ids.forEach { id ->
                db.categories().reassignTxns(id, target)
                db.categories().reassignRecurring(id, target)
                db.categories().deleteRules(id)
                db.categories().delete(id)
            }
        }
        settings.update { s ->
            s.copy(
                budgets = s.budgets - ids.toSet(),
                bookmarks = s.bookmarks.map { if (it.categoryId in ids) it.copy(categoryId = target) else it },
            )
        }
    }

    suspend fun ensureSeedData() {
        if (db.categories().count() > 0) return
        val expense = listOf("餐飲", "便利商店", "日用品", "交通", "購物", "娛樂", "居家", "電信網路", "醫療", "保險", "教育", "社交", "美容", "寵物", "旅遊", "稅費", "其他")
        val income = listOf("薪資", "獎金", "投資收益", "利息", "退款", "副業", "其他收入")
        db.categories().insertAll(
            expense.mapIndexed { i, n -> CategoryEntity(name = n, kind = TxnType.EXPENSE, icon = "", sortOrder = i) } +
                income.mapIndexed { i, n -> CategoryEntity(name = n, kind = TxnType.INCOME, icon = "", sortOrder = i) },
        )
        // 不再自動建立預設帳戶（避免重新安裝後匯入資料時多出「現金」「銀行帳戶」）
        if (!settings.value.seedCleaned) {
            removeUnusedSeedAccounts()
            settings.update { it.copy(seedCleaned = true) }
        }
    }

    /**
     * 清掉舊版自動建立、從未使用的「現金」「銀行帳戶」：餘額 0、沒有任何交易、股票、週期、連結或點鈔紀錄，
     * 而且還有其他帳戶時才刪除。
     */
    private suspend fun removeUnusedSeedAccounts() {
        val accounts = db.accounts().all()
        val txns = db.txns().all()
        val trades = db.stocks().allTrades()
        val recurring = db.recurring().all()
        val s = settings.value
        val candidates = accounts.filter { a ->
            (a.name == "現金" && a.type == AccountType.CASH || a.name == "銀行帳戶" && a.type == AccountType.BANK) &&
                a.initialBalance == 0L &&
                txns.none { it.accountId == a.id || it.toAccountId == a.id } &&
                trades.none { it.cashAccountId == a.id || it.brokerAccountId == a.id } &&
                recurring.none { it.accountId == a.id || it.toAccountId == a.id } &&
                accounts.none { it.autopayFromId == a.id || it.linkedAccountId == a.id } &&
                (s.cashCounts[a.id]?.values?.sum() ?: 0) == 0
        }
        if (candidates.isEmpty() || accounts.size - candidates.size < 1) return
        candidates.forEach { deleteAccount(it) }
        // 預設帳戶被刪掉時改用第一個現金帳戶
        if (settings.value.defaultAccountId == null) {
            db.accounts().all().firstOrNull { it.type == AccountType.CASH && !it.archived }?.let { c ->
                settings.update { it.copy(defaultAccountId = c.id) }
            }
        }
    }

    // ---------- 通知自動記帳 ----------

    /** Android 15 對含驗證碼的通知，第三方監聽只會收到「已隱藏敏感內容」的替代文字。 */
    val SENSITIVE_ADB = "adb shell appops set com.enwei.moneymanager RECEIVE_SENSITIVE_NOTIFICATIONS allow"

    fun isRedacted(text: String?): Boolean = text != null &&
        (text.contains("notification content hidden", ignoreCase = true) || (text.contains("敏感") && text.contains("隱藏")))

    /** [force]：使用者在「測試辨識」手動按「記下這筆」，略過來源是否啟用的檢查。 */
    /**
     * 試算一則通知會被怎麼處理（不寫入任何資料），用來在不實際消費的情況下確認辨識是否正確。
     * 回傳 (是否會記帳, 說明)。
     */
    suspend fun previewNotification(pkg: String, title: String?, text: String?): Pair<Boolean, String> {
        val s = settings.value
        if (pkg == LINE_PACKAGE && !isLineWallet(title, text)) return false to "LINE 聊天訊息，不處理"
        if (pkg == GMAIL_PACKAGE) return false to previewEmail(title, text)
        if (isRedacted(text)) return false to "內容被 Android 隱藏：Android 15 起，系統判斷含驗證碼（OTP）的通知會對第三方 App 隱藏內容，" +
            "App 收不到金額。一般的消費通知不含驗證碼，通常不會被隱藏。若一定要讀取，可在電腦用 adb 執行下方指令開放。"
        val learned = s.learnedFor(pkg, title, text)
        val bankSrc = sourceBank(pkg, title)
        val enabled = pkg == LINE_PACKAGE || learned != null || pkg in s.notifPackages ||
            (bankSrc != null && db.accounts().all().any { !it.archived && BankDirectory.matches(bankSrc, it.name) })
        NotificationParser.parseStockFill(title, text)?.let { f ->
            val msg = "股票成交：${if (f.side == StockSide.SELL) "賣出" else "買進"} ${f.symbol} ${f.shares.toLong()} 股 @ ${f.price}"
            return enabled to (msg + if (enabled) "" else "\n這個 App 尚未勾選監聽，勾選後才會自動建立")
        }
        var aiParsed = false
        val p = learned?.parse(title, text)
            ?: NotificationParser.parse(title, text)
            ?: (if (enabled && com.enwei.moneymanager.net.GroqAi.enabled) com.enwei.moneymanager.net.GroqAi.parseNotification(title, text)?.also { aiParsed = true } else null)
            ?: return false to "看不出是交易（找不到金額，或沒有「消費／付款／入帳」等字樣）。若這是交易通知，按「教它辨識」。"
        val accounts = db.accounts().all().filter { !it.archived }
        val raw0 = listOfNotNull(title, text).joinToString("\n")
        val account = resolveAccount(pkg, p, raw0, learned, bankSrc, accounts)
        val what = when (p.kind) {
            ParsedKind.WITHDRAWAL -> "提款 → 記成轉帳到現金"
            ParsedKind.TOPUP_OUT -> "電支儲值扣款 → 記成轉帳到錢包"
            ParsedKind.TOPUP_IN -> "錢包儲值入帳"
            ParsedKind.TRANSFER_OUT -> "轉出（有對應轉入時合併為轉帳）"
            ParsedKind.TRANSFER_IN -> "轉入（有對應轉出時合併為轉帳）"
            ParsedKind.CARD_PAYMENT -> "信用卡款扣繳"
            else -> if (p.type == ParsedType.INCOME) "收入" else "支出"
        }
        val type = if (p.type == ParsedType.INCOME) TxnType.INCOME else TxnType.EXPENSE
        val at = p.occurredAt?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: System.currentTimeMillis()
        var cat = if (p.kind == ParsedKind.NORMAL) suggestCategory(p.merchant, type, at, p.amountMinor, account?.id)
            ?.let { sg -> categoryPath(sg.categoryId)?.let { "$it（${sg.reason}）" } } else null
        var aiName: String? = null
        if (p.kind == ParsedKind.NORMAL && p.merchant != null && (cat == null || looksRaw(p.merchant))) {
            aiNameAndCategory(p.merchant, type, at)?.let { (name, c) ->
                if (looksRaw(p.merchant)) aiName = name
                if (cat == null && c != null) cat = categoryPath(c)?.let { "$it（AI 推測）" }
            }
        }
        val lines = listOfNotNull(
            "$what ${Money.format(p.amountMinor, p.currency)} ${p.currency}",
            "帳戶：${account?.name ?: "（未指定，請設定預設帳戶）"}" + (p.cardLast4?.let { "（卡號末四碼 $it）" } ?: ""),
            p.merchant?.let { "商家：$it" + (aiName?.let { n -> " → AI 命名「$n」" } ?: "") },
            if (aiParsed) "（規則看不懂，由 AI 判讀；記帳時會放到「待確認」）" else null,
            "分類：" + (cat ?: "無法判斷（沒有商家名稱或沒有相似紀錄），記帳後可在通知上一鍵選分類"),
            p.occurredAt?.let { "交易時間：${it.toLocalDate()} ${"%02d:%02d".format(it.hour, it.minute)}" },
            learned?.let { l -> "（依你教過的格式辨識：${l.label}" + (l.toAccountId?.let { id -> accounts.firstOrNull { it.id == id }?.let { a -> if (l.parsedKind == ParsedKind.TRANSFER_IN) "，從「${a.name}」" else "，到「${a.name}」" } } ?: "") + "）" },
            if (!enabled) "這個 App 尚未勾選監聽，勾選後才會自動記帳" else null,
        )
        return (enabled && account != null) to lines.joinToString("\n")
    }

    /** 這則通知要記到哪個帳戶：教過的格式 → 卡號末四碼 → 指定的帳戶 → LINE → 同一家銀行的帳戶 → 預設帳戶。 */
    private fun resolveAccount(
        pkg: String, p: com.enwei.moneymanager.core.ParsedTransaction, raw: String, learned: LearnedSource?,
        bankSrc: BankDirectory.Bank?, accounts: List<AccountEntity>,
    ): AccountEntity? {
        val s = settings.value
        return learned?.accountId?.let { id -> accounts.firstOrNull { it.id == id } }
            ?: p.cardLast4?.let { l4 -> accounts.firstOrNull { it.cardLast4 == l4 } }
            ?: s.notifAccountMap[pkg]?.let { id -> accounts.firstOrNull { it.id == id } }
            ?: (if (pkg == LINE_PACKAGE) accounts.firstOrNull { it.type != AccountType.CREDIT_CARD && it.name.contains("line", ignoreCase = true) } else null)
            ?: bankSrc?.let { bankAccount(it, wantsCard(p, raw), accounts) }
            ?: accounts.firstOrNull { it.id == s.defaultAccountId }
    }

    /** 一則通知在測試頁的試算結果。 */
    data class NotePreview(val pkg: String, val title: String?, val text: String?, val postedAt: Long)

    /**
     * 試算通知列上的一批通知，並和實際記帳一樣推算轉帳：
     * 一邊轉出、一邊轉入（金額相同、10 分鐘內、不同帳戶）會合併成一筆轉帳；
     * 銀行的轉帳 Email 寫有轉入銀行時，會把同一家銀行剛剛的轉出改成轉帳。
     */
    suspend fun previewBatch(list: List<NotePreview>): List<Pair<Boolean, String>> {
        val base = list.map { previewNotification(it.pkg, it.title, it.text) }.toMutableList()
        val s = settings.value
        val accounts = db.accounts().all().filter { !it.archived }
        data class Side(val i: Int, val out: Boolean, val amount: Long, val at: Long, val account: AccountEntity?, val bank: BankDirectory.Bank?)
        val sides = list.mapIndexedNotNull { i, n ->
            if (n.pkg == GMAIL_PACKAGE || isRedacted(n.text)) return@mapIndexedNotNull null
            val learned = s.learnedFor(n.pkg, n.title, n.text)
            val p = learned?.parse(n.title, n.text)
                ?: NotificationParser.parse(n.title, n.text) ?: return@mapIndexedNotNull null
            if (p.kind != ParsedKind.TRANSFER_OUT && p.kind != ParsedKind.TRANSFER_IN) return@mapIndexedNotNull null
            val bank = sourceBank(n.pkg, n.title)
            val at = p.occurredAt?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: n.postedAt
            Side(i, p.kind == ParsedKind.TRANSFER_OUT, p.amountMinor, at, resolveAccount(n.pkg, p, listOfNotNull(n.title, n.text).joinToString("\n"), learned, bank, accounts), bank)
        }
        val paired = HashSet<Int>()
        sides.filter { it.out }.forEach { o ->
            val inn = sides.firstOrNull { it.i !in paired && !it.out && it.amount == o.amount && kotlin.math.abs(it.at - o.at) <= 10 * 60_000 &&
                it.account != null && o.account != null && it.account.id != o.account.id } ?: return@forEach
            paired += o.i; paired += inn.i
            val msg = "→ 會和另一則通知合併成轉帳：${o.account!!.name} → ${inn.account!!.name} ${Money.format(o.amount, "TWD")}"
            base[o.i] = true to base[o.i].second + "\n" + msg
            base[inn.i] = true to base[inn.i].second + "\n" + msg
        }
        // 轉帳 Email：補上轉入銀行
        list.forEachIndexed { i, n ->
            if (n.pkg != GMAIL_PACKAGE) return@forEachIndexed
            val src = BankDirectory.of(n.title) ?: return@forEachIndexed
            val dest = BankDirectory.transferDestination(n.text) ?: return@forEachIndexed
            val to = bankAccount(dest, false, accounts)
            val amount = NotificationParser.findAmount(listOfNotNull(n.title, n.text).joinToString("\n"))?.first
            val o = sides.filter { it.out && it.i !in paired && it.bank?.key == src.key && kotlin.math.abs(it.at - n.postedAt) <= 20 * 60_000 &&
                (amount == null || it.amount == amount) }.minByOrNull { kotlin.math.abs(it.at - n.postedAt) } ?: return@forEachIndexed
            if (to == null || o.account == null || to.id == o.account.id) {
                base[i] = false to base[i].second + "\n（你沒有「${dest.key}」的帳戶，無法記成轉帳）"
                return@forEachIndexed
            }
            paired += o.i
            val msg = "→ 依 Email 的轉入銀行記成轉帳：${o.account.name} → ${to.name} ${Money.format(o.amount, "TWD")}"
            base[o.i] = true to base[o.i].second + "\n" + msg
            base[i] = true to base[i].second + "\n" + msg
        }
        return base
    }

    /** 通知來自哪家銀行：Gmail 看寄件者（標題），其他 App 看 App 名稱與套件名稱。 */
    private fun sourceBank(pkg: String, title: String?): BankDirectory.Bank? {
        if (pkg == GMAIL_PACKAGE) return BankDirectory.of(title)
        val label = runCatching {
            val pm = com.enwei.moneymanager.MoneyApp.instance.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrNull()
        return BankDirectory.of(listOfNotNull(label, pkg).joinToString(" "))
    }

    /** 刷卡消費才記到信用卡；轉帳、出帳、入帳、提款一律是存款（簽帳）帳戶。 */
    private fun wantsCard(p: com.enwei.moneymanager.core.ParsedTransaction, raw: String): Boolean =
        p.kind == ParsedKind.NORMAL && (p.cardLast4 != null || listOf("刷卡", "信用卡", "簽帳金融卡消費").any { raw.contains(it) } && !raw.contains("轉出"))

    /** 這家銀行對應的帳戶：信用卡消費找信用卡，其餘找存款帳戶（優先銀行類）。 */
    private fun bankAccount(bank: BankDirectory.Bank, card: Boolean, accounts: List<AccountEntity>): AccountEntity? {
        val list = accounts.filter { BankDirectory.matches(bank, it.name) }
        return if (card) list.firstOrNull { it.type == AccountType.CREDIT_CARD }
        else list.filter { it.type != AccountType.CREDIT_CARD }.let { l -> l.firstOrNull { it.type == AccountType.BANK } ?: l.firstOrNull() }
    }

    /**
     * 銀行的轉帳結果 Email（例如國泰「CUBE App轉帳通知」寫有「轉入銀行 807永豐銀行」）：
     * 找到剛剛同一家銀行的轉出，改成轉帳到你在轉入銀行的帳戶。
     */
    private suspend fun enrichFromEmail(title: String?, text: String?, postedAt: Long): IngestResult? {
        val src = BankDirectory.of(title) ?: return null
        val dest = BankDirectory.transferDestination(text) ?: return null
        val accounts = db.accounts().all().filter { !it.archived }
        val from = bankAccount(src, false, accounts) ?: return null
        val to = bankAccount(dest, false, accounts)?.takeIf { it.id != from.id } ?: return null
        val raw = listOfNotNull(title, text).joinToString("\n")
        val amount = NotificationParser.findAmount(raw)?.first
        val window = 20 * 60_000L
        val candidates = db.txns().all().filter {
            it.source == TxnSource.NOTIFICATION && it.accountId == from.id && kotlin.math.abs(it.date - postedAt) <= window &&
                it.type == TxnType.EXPENSE && it.rawText?.startsWith(TRANSFER_TAG) == true && (amount == null || it.amount == amount)
        }
        // 沒有金額時只在唯一一筆時套用，避免改錯
        val t = (if (amount == null) candidates.singleOrNull() else candidates.minByOrNull { kotlin.math.abs(it.date - postedAt) }) ?: return null
        db.txns().upsert(t.copy(type = TxnType.TRANSFER, toAccountId = to.id, categoryId = null, merchant = "轉帳",
            rawText = listOfNotNull(t.rawText, raw).joinToString("\n—\n")))
        db.notificationLog().insert(NotificationLogEntity(packageName = GMAIL_PACKAGE, title = title, text = text, postedAt = postedAt, result = "MERGED", txnId = t.id))
        return IngestResult("RECORDED", t.id, "轉帳 ${Money.format(t.amount, t.currency)}：${from.name} → ${to.name}（依 Email 的轉入銀行）")
    }

    private fun previewEmail(title: String?, text: String?): String {
        val src = BankDirectory.of(title) ?: return "Email：寄件者不是銀行，不處理"
        val dest = BankDirectory.transferDestination(text)
            ?: return "${src.key}的 Email：沒有「轉入銀行」資訊，不處理（Email 只用來補充轉帳對象，不另外記帳）"
        return "${src.key}的轉帳 Email：轉入${dest.key}。若剛剛有${src.key}的轉出紀錄，會改成轉帳到你的${dest.key}帳戶（不另外記帳）"
    }

    suspend fun ingestNotification(pkg: String, title: String?, text: String?, postedAt: Long, force: Boolean = false): IngestResult? {
        // LINE：只處理「LINE錢包」付款通知（LINE Bank Money／LINE Pay），聊天訊息一律略過；不需另外勾選 LINE
        val isLine = pkg == LINE_PACKAGE
        if (isLine && !isLineWallet(title, text)) return null
        val s = settings.value
        val log = db.notificationLog()
        // 同一則通知可能因更新而重複送達
        if (!force && log.countSame(pkg, title, text, postedAt - 10 * 60_000) > 0) return null
        log.prune(System.currentTimeMillis() - 60L * 24 * 3600 * 1000)
        // 銀行寄的 Email：只用來補充轉帳的轉入銀行，不另外記帳（其他 Email 一律不處理）
        if (pkg == GMAIL_PACKAGE) return enrichFromEmail(title, text, postedAt)
        val learned = s.learnedFor(pkg, title, text)
        val bankSrc = sourceBank(pkg, title)
        val allAccounts = db.accounts().all().filter { !it.archived }
        // 來源 App 是你有帳戶的銀行（例如「大戶 DAWHO」對應帳戶「大戶投」）：不用另外勾選
        val enabled = force || isLine || learned != null || pkg in s.notifPackages ||
            (bankSrc != null && allAccounts.any { BankDirectory.matches(bankSrc, it.name) })
        fun logAs(result: String, txnId: Long? = null) =
            NotificationLogEntity(packageName = pkg, title = title, text = text, postedAt = postedAt, result = result, txnId = txnId)

        val fill = NotificationParser.parseStockFill(title, text)
        var parsed = if (fill != null) null else learned?.parse(title, text) ?: NotificationParser.parse(title, text)
        // 規則看不懂、但來自已啟用的銀行／支付 App：請 AI 判讀（結果一律放「待確認」）
        var aiParsed = false
        if (fill == null && parsed == null && enabled && !isRedacted(text) && com.enwei.moneymanager.net.GroqAi.enabled) {
            parsed = com.enwei.moneymanager.net.GroqAi.parseNotification(title, text)
            aiParsed = parsed != null
        }
        if (fill == null && parsed == null) {
            if (enabled) log.insert(logAs("UNPARSED"))
            return null
        }
        if (!enabled) {
            // 看起來像交易通知但未啟用的 App：記錄下來讓使用者決定是否啟用
            log.insert(logAs("CANDIDATE"))
            return null
        }

        if (fill != null) {
            val market = Market.TW
            val (fee, tax) = StockCalc.estimate(market, fill.symbol, fill.side, fill.shares, fill.price, s.feeSettings)
            val brokers = db.accounts().all().filter { it.type == AccountType.BROKERAGE && !it.archived }
            val broker = s.notifAccountMap[pkg]?.let { id -> brokers.firstOrNull { it.id == id } }
                ?: bankSrc?.let { b -> brokers.firstOrNull { BankDirectory.matches(b, it.name) } }
                ?: brokers.firstOrNull()
            val (bFee, bTax) = if (broker?.feeDiscount != null)
                StockCalc.estimate(market, fill.symbol, fill.side, fill.shares, fill.price, s.feeSettings.copy(discount = broker.feeDiscount)) else fee to tax
            val id = db.stocks().insertTrade(
                StockTradeEntity(
                    market = market, symbol = fill.symbol, name = fill.name, side = fill.side, shares = fill.shares,
                    price = fill.price, fee = bFee, tax = bTax, date = postedAt.toLocalDate().toEpochDay(),
                    cashAccountId = broker?.linkedAccountId ?: broker?.id, brokerAccountId = broker?.id,
                    source = TxnSource.NOTIFICATION, note = "由成交通知自動建立",
                    externalKey = "N:${fill.symbol}:${fill.side}:${fill.shares}:${fill.price}:${postedAt / 60_000}",
                ),
            )
            if (id <= 0) { log.insert(logAs("DUPLICATE")); return null }
            log.insert(logAs("STOCK"))
            val sideLabel = if (fill.side == StockSide.SELL) "賣出" else "買進"
            return IngestResult("STOCK", id, "$sideLabel ${fill.symbol} ${fill.shares.toLong()} 股 @ ${fill.price}")
        }

        val p = parsed!!
        val occurred = p.occurredAt?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: postedAt
        // 通知內有交易時間時，以「金額＋時間」去重：同一筆交易的通知晚點再跳一次也不會重複記帳
        // 方向要算進去：自己兩個帳戶互轉時，轉出與轉入通知常是同一秒、同金額，不能被當成重複
        val sign = if (p.type == ParsedType.INCOME) "+" else "-"
        val key = p.occurredAt?.let { "N:$sign${p.amountMinor}:${it.withNano(0)}" }
        if (key != null && db.txns().byExternalKey(key) != null) { log.insert(logAs("DUPLICATE")); return null }

        val raw0 = listOfNotNull(title, text).joinToString("\n")
        // 同一則通知隔幾小時又跳出（內文完全相同）：已經記過或已合併成轉帳的不再處理
        val dayMs = 24L * 3600 * 1000
        val rawWindow = if (p.occurredAt != null) dayMs else 30L * 60 * 1000
        if (db.txns().all().any { it.rawText != null && kotlin.math.abs(it.date - occurred) <= rawWindow && it.rawText.contains(raw0) }) {
            log.insert(logAs("DUPLICATE")); return null
        }
        val accounts = db.accounts().all().filter { !it.archived }
        val account = resolveAccount(pkg, p, raw0, learned, bankSrc, accounts)
        // 銀行的股利入帳通知：已由「自動除息」記到這個帳戶時不再重複記收入
        if (p.type == ParsedType.INCOME && account != null && listOf("股利", "股息", "配息").any { raw0.contains(it) }) {
            val day = java.time.Instant.ofEpochMilli(occurred).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
            val autoDiv = db.stocks().allTrades().filter {
                it.side == StockSide.CASH_DIVIDEND && it.cashAccountId == account.id && it.externalKey?.startsWith("DIV") == true &&
                    day - it.date in -15..75
            }.groupBy { it.date }.values.map { l -> Money.toMinor(l.sumOf { it.cashAmount }) }
            if (autoDiv.any { kotlin.math.abs(it - p.amountMinor) <= 100 }) { log.insert(logAs("DUPLICATE")); return null }
        }
        val status = if (s.notifAutoConfirm && !aiParsed) TxnStatus.CONFIRMED else TxnStatus.PENDING
        val raw = listOfNotNull(title, text).joinToString("\n")
        val window = 10 * 60_000L
        val nearby = db.txns().all().filter {
            it.source == TxnSource.NOTIFICATION && it.amount == p.amountMinor && kotlin.math.abs(it.date - occurred) <= window
        }

        suspend fun insert(t: TxnEntity, result: String, message: String): IngestResult? {
            val id = db.txns().insert(t)
            if (id <= 0) { log.insert(logAs("DUPLICATE")); return null }
            log.insert(logAs(result, id))
            log.prune(System.currentTimeMillis() - 60L * 24 * 3600 * 1000)
            return IngestResult("RECORDED", id, message)
        }
        suspend fun merged(existing: TxnEntity, updated: TxnEntity, message: String): IngestResult {
            // 合併時保留這則通知原文，之後同一則通知再跳出時可辨識為重複
            db.txns().upsert(updated.copy(rawText = listOfNotNull(updated.rawText, raw).joinToString("\n—\n")))
            log.insert(logAs("MERGED", existing.id))
            return IngestResult("RECORDED", existing.id, message)
        }
        fun wallet(): AccountEntity? {
            val wallets = accounts.filter { it.type == AccountType.EWALLET }
            val aliases = p.walletHint?.let { NotificationParser.walletAliases[it] }.orEmpty()
            return wallets.firstOrNull { w -> aliases.any { w.name.contains(it, ignoreCase = true) } }
                ?: wallets.filter { account != null && it.linkedAccountId == account.id }.singleOrNull()
                ?: s.notifAccountMap[pkg]?.let { id -> wallets.firstOrNull { it.id == id } }
                ?: wallets.singleOrNull()
        }
        val amountText = Money.format(p.amountMinor, p.currency)
        val base = TxnEntity(
            type = TxnType.EXPENSE, amount = p.amountMinor, currency = p.currency, accountId = account?.id,
            date = occurred, source = TxnSource.NOTIFICATION, status = status, externalKey = key, rawText = raw,
        )

        when (p.kind) {
            ParsedKind.WITHDRAWAL -> {
                val cash = accounts.firstOrNull { it.type == AccountType.CASH }
                if (cash != null && cash.id != account?.id) {
                    return insert(base.copy(type = TxnType.TRANSFER, toAccountId = cash.id, merchant = "提款"), "RECORDED",
                        "提款 $amountText → ${cash.name}")
                }
            }
            ParsedKind.TOPUP_OUT -> {
                val w = wallet()
                // 錢包端的儲值通知先到：補上扣款帳戶
                nearby.firstOrNull { it.type == TxnType.TRANSFER && it.accountId == null && (w == null || it.toAccountId == w.id) }?.let {
                    return merged(it, it.copy(accountId = account?.id), "儲值 $amountText：${account?.name ?: "?"} → ${lookupName(it.toAccountId)}")
                }
                // 錢包畫面讀取已先記下這筆儲值：補上扣款帳戶即可
                if (w != null) db.txns().all().firstOrNull {
                    it.source == TxnSource.SCREEN && it.type == TxnType.TRANSFER && it.toAccountId == w.id && it.amount == p.amountMinor &&
                        kotlin.math.abs(it.date - occurred) <= 36L * 3600 * 1000
                }?.let {
                    return merged(it, it.copy(accountId = account?.id ?: it.accountId, date = occurred, status = TxnStatus.CONFIRMED),
                        "儲值 $amountText：${account?.name ?: "?"} → ${w.name}")
                }
                if (w != null && w.id != account?.id) {
                    return insert(base.copy(type = TxnType.TRANSFER, toAccountId = w.id, merchant = "電支儲值"), "RECORDED",
                        "儲值 $amountText：${account?.name ?: "?"} → ${w.name}")
                }
                return insert(base.copy(merchant = "電支儲值", status = TxnStatus.PENDING), "RECORDED", "電支儲值 $amountText（請選擇轉入的錢包）")
            }
            ParsedKind.TOPUP_IN -> {
                val w = wallet() ?: account
                if (nearby.any { it.type == TxnType.TRANSFER && it.toAccountId == w?.id }) { log.insert(logAs("DUPLICATE")); return null }
                // 銀行扣款通知先到（被記成支出）：改成轉帳
                nearby.firstOrNull { it.type == TxnType.EXPENSE && it.accountId != w?.id }?.let {
                    return merged(it, it.copy(type = TxnType.TRANSFER, toAccountId = w?.id, categoryId = null, merchant = "電支儲值"),
                        "儲值 $amountText → ${w?.name ?: "?"}")
                }
                return insert(base.copy(type = TxnType.TRANSFER, accountId = null, toAccountId = w?.id, merchant = "電支儲值", status = TxnStatus.PENDING),
                    "RECORDED", "儲值 $amountText → ${w?.name ?: "?"}（等待扣款通知）")
            }
            ParsedKind.TRANSFER_OUT, ParsedKind.TRANSFER_IN -> {
                // 自己兩個帳戶之間的轉帳：一邊轉出、一邊轉入，金額相同且時間相近 → 合併成一筆轉帳
                val isOut = p.kind == ParsedKind.TRANSFER_OUT
                // 已經記成轉帳（例如 Email 已補上轉入銀行）：這則是同一筆的另一端
                if (account != null && nearby.any { it.type == TxnType.TRANSFER && (if (isOut) it.accountId else it.toAccountId) == account.id }) {
                    log.insert(logAs("DUPLICATE")); return null
                }
                // 教過「轉出到某帳戶／從某帳戶轉入」：另一端沒有通知也直接記成轉帳
                val counterpart = learned?.toAccountId?.let { id -> accounts.firstOrNull { it.id == id } }
                if (counterpart != null && counterpart.id != account?.id && nearby.none {
                        it.type == (if (isOut) TxnType.INCOME else TxnType.EXPENSE) && it.rawText?.startsWith(TRANSFER_TAG) == true && it.accountId == counterpart.id
                    }
                ) {
                    val from = if (isOut) account?.id else counterpart.id
                    val to = if (isOut) counterpart.id else account?.id
                    return insert(base.copy(type = TxnType.TRANSFER, accountId = from, toAccountId = to, merchant = "轉帳"), "RECORDED",
                        "轉帳 $amountText：${lookupName(from)} → ${lookupName(to)}")
                }
                nearby.firstOrNull {
                    it.type == (if (isOut) TxnType.INCOME else TxnType.EXPENSE) && it.rawText?.startsWith(TRANSFER_TAG) == true &&
                        it.accountId != account?.id
                }?.let { other ->
                    val from = if (isOut) account?.id else other.accountId
                    val to = if (isOut) other.accountId else account?.id
                    return merged(other, other.copy(type = TxnType.TRANSFER, accountId = from, toAccountId = to, categoryId = null, merchant = "轉帳"),
                        "轉帳 $amountText：${lookupName(from)} → ${lookupName(to)}")
                }
            }
            ParsedKind.CARD_PAYMENT -> {
                val cards = accounts.filter { it.type == AccountType.CREDIT_CARD }
                val card = p.cardLast4?.let { l4 -> cards.firstOrNull { it.cardLast4 == l4 } }
                    ?: cards.filter { account != null && it.autopayFromId == account.id }.singleOrNull()
                    ?: cards.singleOrNull()
                if (card != null) {
                    // 已由排程建立的自動扣繳：以銀行實際扣款金額與時間校正，不重複記帳
                    val window5d = 5L * 24 * 3600 * 1000
                    db.txns().all().firstOrNull {
                        it.source == TxnSource.AUTOPAY && it.toAccountId == card.id && kotlin.math.abs(it.date - occurred) <= window5d
                    }?.let { ap ->
                        db.txns().upsert(ap.copy(amount = p.amountMinor, date = occurred, accountId = account?.id ?: ap.accountId, rawText = raw))
                        log.insert(logAs("MERGED", ap.id))
                        return IngestResult("RECORDED", ap.id, "卡費扣繳 $amountText：${card.name}（已校正金額）")
                    }
                    return insert(base.copy(type = TxnType.TRANSFER, toAccountId = card.id, merchant = "${card.name} 卡費"), "RECORDED",
                        "卡費 $amountText：${account?.name ?: "?"} → ${card.name}")
                }
            }
            ParsedKind.NORMAL -> Unit
        }

        val type = if (p.type == ParsedType.INCOME) TxnType.INCOME else TxnType.EXPENSE
        // 多個 App 同時通知同一筆消費（例如信用卡 + 支付 App）
        if (nearby.any { it.type == type && kotlin.math.abs(it.date - occurred) <= 3 * 60_000 }) {
            log.insert(logAs("DUPLICATE", nearby.first().id))
            return null
        }
        val tagged = if (p.kind == ParsedKind.TRANSFER_OUT || p.kind == ParsedKind.TRANSFER_IN) "$TRANSFER_TAG\n$raw" else raw
        var merchant = p.merchant ?: when (p.kind) {
            ParsedKind.TRANSFER_OUT -> "轉出"
            ParsedKind.TRANSFER_IN -> "轉入"
            else -> null
        }
        // 轉出／轉入多半是自己帳戶間的轉帳（之後會合併），不猜分類
        var categoryId = if (p.kind == ParsedKind.NORMAL) suggestCategory(p.merchant, type, occurred, p.amountMinor, account?.id)?.categoryId else null
        // 規則挑不出分類，或商家名稱是代碼（例如「GOOGLE*WALLET TEMP」）：請 AI 命名並挑分類
        var aiNote = if (aiParsed) "（AI 判讀，請確認）" else ""
        if (p.kind == ParsedKind.NORMAL && p.merchant != null && (categoryId == null || looksRaw(p.merchant))) {
            aiNameAndCategory(p.merchant, type, occurred)?.let { (name, cat) ->
                if (name != null && looksRaw(p.merchant)) merchant = name
                if (categoryId == null && cat != null) { categoryId = cat; aiNote += "（分類由 AI 推測）" }
            }
        }
        return insert(
            base.copy(type = type, merchant = merchant, rawText = if (aiParsed) "$tagged\n$AI_TAG" else tagged, categoryId = categoryId),
            "RECORDED", "${type.label} $amountText ${merchant.orEmpty()}$aiNote",
        )
    }

    /** 商家名稱看起來是代碼：有星號、底線，或幾乎全是大寫英數。 */
    private fun looksRaw(m: String): Boolean {
        val letters = m.filter { it.isLetter() }
        return '*' in m || '_' in m || (letters.length >= 4 && letters.all { it in 'A'..'Z' } && m.length > 8)
    }

    /** AI 命名與分類；AI 未啟用、失敗或超過額度時回傳 null。 */
    suspend fun aiNameAndCategory(merchant: String, type: TxnType, at: Long): Pair<String?, Long?>? {
        if (!com.enwei.moneymanager.net.GroqAi.enabled || type == TxnType.TRANSFER) return null
        val all = db.categories().all()
        val cats = all.filter { it.kind == type && !it.archived }.map { c ->
            c.id to (c.parentId?.let { pid -> all.find { it.id == pid }?.let { "${it.name} / ${c.name}" } } ?: c.name)
        }
        if (cats.isEmpty()) return null
        val hour = java.time.Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).hour
        return com.enwei.moneymanager.net.GroqAi.nameAndCategory(merchant, hour, cats).takeIf { it.first != null || it.second != null }
    }

    /**
     * 匯入從錢包 App 交易紀錄畫面讀到的資料（街口、悠遊付等不推播消費的錢包）。
     * 以「日期＋金額＋商家＋同畫面序號」去重；儲值若已由銀行通知記成轉帳則略過。
     */
    /** 錢包畫面讀取結果（測試模式時 created 為「會新增」的筆數）。 */
    data class ScreenIngestResult(val wallet: String?, val total: Int, val tooOld: Int, val existing: Int, val created: Int, val since: LocalDate)

    suspend fun ingestScreenRecords(pkg: String, records: List<ScreenRecord>, dryRun: Boolean = false): ScreenIngestResult {
        val s = settings.value
        val since = s.screenReadSince[pkg]?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
        val accounts = db.accounts().all().filter { !it.archived }
        val wallet = s.notifAccountMap[pkg]?.let { id -> accounts.firstOrNull { it.id == id } }
            ?: accounts.filter { it.type == AccountType.EWALLET }.singleOrNull()
            ?: return ScreenIngestResult(null, records.size, 0, 0, 0, since)
        val seen = HashMap<String, Int>()
        var created = 0
        var existing = 0
        val fresh = records.filter { !it.time.toLocalDate().isBefore(since) }
        fresh.forEach { r ->
            val base = "${r.time.toLocalDate()}${if (r.dateOnly) "" else "T" + r.time.toLocalTime()}:${if (r.isIncome) "+" else "-"}${r.amountMinor}:${r.merchant}"
            val n = seen.merge(base, 1) { x, y -> x + y }!!
            val key = "AX:$pkg:$base#$n"
            if (db.txns().byExternalKey(key) != null) { existing++; return@forEach }
            if (dryRun) { created++; return@forEach }
            val millis = r.time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (r.isTopUp) {
                val window = if (r.dateOnly) 36L * 3600 * 1000 else 15L * 60 * 1000
                val exists = db.txns().all().any {
                    it.type == TxnType.TRANSFER && it.toAccountId == wallet.id && it.amount == r.amountMinor && kotlin.math.abs(it.date - millis) <= window
                }
                if (!exists) {
                    if (db.txns().insert(
                            TxnEntity(
                                type = TxnType.TRANSFER, amount = r.amountMinor, accountId = wallet.linkedAccountId, toAccountId = wallet.id,
                                merchant = "儲值", date = millis, source = TxnSource.SCREEN,
                                status = if (wallet.linkedAccountId == null) TxnStatus.PENDING else TxnStatus.CONFIRMED, externalKey = key,
                            ),
                        ) > 0
                    ) created++
                }
                // 已由銀行通知記成轉帳的儲值：略過
                return@forEach
            }
            val type = if (r.isIncome) TxnType.INCOME else TxnType.EXPENSE
            // 錢包綁信用卡付款時，刷卡通知可能已記過同一筆
            if (!r.dateOnly && db.txns().all().any {
                    it.source == TxnSource.NOTIFICATION && it.type == type && it.amount == r.amountMinor && kotlin.math.abs(it.date - millis) <= 15L * 60 * 1000
                }
            ) return@forEach
            val id = db.txns().insert(
                TxnEntity(
                    type = type, amount = r.amountMinor, currency = wallet.currency, accountId = wallet.id,
                    categoryId = suggestCategory(r.merchant ?: r.typeLabel, type, millis, r.amountMinor, wallet.id)?.categoryId,
                    merchant = r.merchant, note = r.typeLabel, date = millis, source = TxnSource.SCREEN,
                    status = if (s.notifAutoConfirm) TxnStatus.CONFIRMED else TxnStatus.PENDING, externalKey = key,
                ),
            )
            if (id > 0) created++
        }
        return ScreenIngestResult(wallet.name, records.size, records.size - fresh.size, existing, created, since)
    }

    private suspend fun lookupName(id: Long?) = id?.let { db.accounts().get(it)?.name } ?: "?"

    /** 對帳：輸入帳戶實際餘額，差額記成一筆調整交易（適用於沒有通知的電子錢包）。 */
    suspend fun reconcile(account: AccountEntity, currentBalance: Long, actual: Long, categoryId: Long?): Long? {
        val diff = actual - currentBalance
        if (diff == 0L) return null
        return db.txns().insert(
            TxnEntity(
                type = if (diff < 0) TxnType.EXPENSE else TxnType.INCOME, amount = kotlin.math.abs(diff), currency = account.currency,
                accountId = account.id, categoryId = categoryId, merchant = "餘額調整（對帳）",
                note = "對帳：記錄餘額 ${Money.format(currentBalance)} → 實際 ${Money.format(actual)}",
                date = System.currentTimeMillis(), source = TxnSource.MANUAL,
            ),
        )
    }

    // ---------- 週期交易與信用卡自動扣繳 ----------

    suspend fun saveRecurring(r: RecurringEntity): Long {
        val res = db.recurring().upsert(r)
        return if (r.id == 0L) res else r.id
    }

    suspend fun getRecurring(id: Long) = db.recurring().get(id)
    suspend fun deleteRecurring(id: Long) = db.recurring().delete(id)

    fun nextOccurrence(r: RecurringEntity, today: LocalDate = LocalDate.now()): LocalDate {
        val anchor = LocalDate.ofEpochDay(r.anchorDate)
        val after = maxOf(r.lastRunDate?.let(LocalDate::ofEpochDay) ?: anchor.minusDays(1), today.minusDays(1))
        return Recurrence.next(r.frequency, r.interval, anchor, after, r.dayOfMonth)
    }

    /** 產生到今天為止所有到期的週期交易（離線多天也會補齊），回傳新增筆數。 */
    /**
     * 手續費月退：每個設定月退的證券帳戶，上個月（含以前、設定之後）的台股買賣，
     * 在次月退款日自動記一筆收入到交割帳戶。以「REBATE:帳戶:年月」去重。
     */
    suspend fun runFeeRebates(today: LocalDate = LocalDate.now()): Int {
        val s = settings.value
        if (s.brokerRebates.isEmpty()) return 0
        val accounts = db.accounts().all().associateBy { it.id }
        val trades = db.stocks().allTrades()
        var created = 0
        s.brokerRebates.forEach { (brokerId, rule) ->
            val broker = accounts[brokerId] ?: return@forEach
            val since = java.time.YearMonth.from(LocalDate.ofEpochDay(rule.sinceEpochDay))
            trades.filter { it.brokerAccountId == brokerId && it.market == Market.TW && (it.side == StockSide.BUY || it.side == StockSide.SELL) }
                .groupBy { java.time.YearMonth.from(LocalDate.ofEpochDay(it.date)) }
                .forEach { (ym, list) ->
                    if (ym < since) return@forEach
                    val payDate = ym.plusMonths(1).atDay(rule.payDay.coerceIn(1, 28))
                    if (today.isBefore(payDate)) return@forEach
                    val key = "REBATE:$brokerId:$ym"
                    if (db.txns().byExternalKey(key) != null) return@forEach
                    val amount = list.sumOf { t ->
                        val min = if (StockCalc.isOddLot(t.shares)) s.oddLotMinFee else s.minFee
                        StockCalc.rebate(t.fee, t.shares * t.price, rule.discount, min)
                    }
                    if (amount <= 0) return@forEach
                    db.txns().upsert(
                        TxnEntity(
                            type = TxnType.INCOME, amount = Money.toMinor(amount), currency = broker.currency,
                            accountId = broker.linkedAccountId ?: broker.id,
                            categoryId = db.categories().all().firstOrNull { it.kind == TxnType.INCOME && it.name.contains("退") }?.id,
                            merchant = "${broker.name} 手續費退佣 ${ym.year}/${ym.monthValue}",
                            note = "月退估算：${list.size} 筆交易，實收手續費 ${list.sumOf { it.fee }.toLong()} 元，依 ${rule.discount} 折計算",
                            date = payDate.toMillis(LocalTime.NOON), source = TxnSource.RECURRING, externalKey = key,
                        ),
                    )
                    created++
                }
        }
        return created
    }

    suspend fun runRecurring(today: LocalDate = LocalDate.now()): Int {
        var created = 0
        db.recurring().enabled().forEach { r ->
            val anchor = LocalDate.ofEpochDay(r.anchorDate)
            val from = r.lastRunDate?.let(LocalDate::ofEpochDay) ?: anchor.minusDays(1)
            val end = r.endDate?.let(LocalDate::ofEpochDay)?.let { minOf(it, today) } ?: today
            if (end.isBefore(from)) return@forEach
            val dates = Recurrence.occurrences(r.frequency, r.interval, anchor, from, end, r.dayOfMonth)
            db.withTransaction {
                dates.forEach { d ->
                    val id = db.txns().insert(
                        TxnEntity(
                            type = r.type, amount = r.amount, currency = r.currency, accountId = r.accountId,
                            toAccountId = r.toAccountId, categoryId = r.categoryId, merchant = r.name, note = r.note,
                            date = d.toMillis(LocalTime.of(9, 0)), source = TxnSource.RECURRING,
                            status = if (r.autoConfirm) TxnStatus.CONFIRMED else TxnStatus.PENDING,
                            externalKey = "R:${r.id}:${d.toEpochDay()}", recurringId = r.id,
                        ),
                    )
                    if (id > 0) created++
                }
                db.recurring().upsert(r.copy(lastRunDate = end.toEpochDay()))
            }
        }
        return created + runCardAutopay(today)
    }

    /** 信用卡自動扣繳：每到繳款日，從扣款帳戶轉帳「結帳日當時的欠款 − 之後已繳金額」到信用卡。 */
    suspend fun runCardAutopay(today: LocalDate = LocalDate.now()): Int {
        var created = 0
        val fx = currentFx()
        val accounts = db.accounts().all()
        accounts.filter {
            it.type == AccountType.CREDIT_CARD && it.autopayFromId != null && it.statementDay != null && it.dueDay != null && !it.archived
        }.forEach { card ->
            val cycle = settings.value.cycleFor(card)!!
            // 第一次設定時只從今天開始，不回溯
            var cursor = card.autopayLastRun?.let(LocalDate::ofEpochDay) ?: today.minusDays(1)
            while (true) {
                val due = cycle.nextDueDate(cursor)
                val deduct = cycle.autopayDateFor(due, card.autopayDay)
                if (deduct.isAfter(today)) break
                val statement = cycle.statementDateFor(due)
                val txns = db.txns().all().filter { it.status == TxnStatus.CONFIRMED }
                val owed = -balanceAsOf(card, txns, statement.plusDays(1).toMillis(LocalTime.MIDNIGHT), fx)
                val paidSince = txns.filter {
                    it.type == TxnType.TRANSFER && it.toAccountId == card.id &&
                        it.date >= statement.plusDays(1).toMillis(LocalTime.MIDNIGHT) &&
                        it.date < deduct.plusDays(1).toMillis(LocalTime.MIDNIGHT) && it.source != TxnSource.AUTOPAY
                }.sumOf { it.toAmount ?: it.amount }
                val pay = owed - paidSince
                if (pay > 0) {
                    val id = db.txns().insert(
                        TxnEntity(
                            type = TxnType.TRANSFER, amount = pay, currency = card.currency, accountId = card.autopayFromId,
                            toAccountId = card.id, merchant = "${card.name} 自動扣繳", note = "結帳日 $statement · 繳款日 $due",
                            date = deduct.toMillis(LocalTime.of(8, 0)), source = TxnSource.AUTOPAY,
                            externalKey = "AP:${card.id}:${due.toEpochDay()}",
                        ),
                    )
                    if (id > 0) created++
                }
                cursor = due
            }
            db.accounts().upsert(card.copy(autopayLastRun = today.toEpochDay()))
        }
        return created
    }

    private suspend fun currentFx(): FxConverter =
        FxConverter(settings.value.baseCurrency, db.fx().all().associate { it.currency to it.rate })

    private fun balanceAsOf(acc: AccountEntity, txns: List<TxnEntity>, beforeMillis: Long, fx: FxConverter): Long {
        var b = acc.initialBalance
        fun conv(t: TxnEntity) = fx.convert(t.amount, t.currency, acc.currency)
        txns.filter { it.date < beforeMillis }.forEach { t ->
            when (t.type) {
                TxnType.EXPENSE -> if (t.accountId == acc.id) b -= conv(t)
                TxnType.INCOME -> if (t.accountId == acc.id) b += conv(t)
                TxnType.TRANSFER -> {
                    if (t.accountId == acc.id) b -= conv(t)
                    if (t.toAccountId == acc.id) b += t.toAmount ?: conv(t)
                }
            }
        }
        return b
    }

    /** 信用卡：應繳金額（已出帳未繳）、未出帳金額、下次繳款日。 */
    suspend fun cardStatus(today: LocalDate = LocalDate.now()): Map<Long, CardStatus> {
        val fx = currentFx()
        val txns = db.txns().all().filter { it.status == TxnStatus.CONFIRMED }
        return db.accounts().all().filter { it.type == AccountType.CREDIT_CARD }.associate { card ->
            val owed = maxOf(0, -balanceAsOf(card, txns, Long.MAX_VALUE, fx))
            if (card.statementDay == null || card.dueDay == null) return@associate card.id to CardStatus(owed, 0, null)
            val cycle = settings.value.cycleFor(card)!!
            val lastStatement = cycle.lastStatementOnOrBefore(today)
            val cutoff = lastStatement.plusDays(1).toMillis(LocalTime.MIDNIGHT)
            val billed = maxOf(0, -balanceAsOf(card, txns, cutoff, fx))
            val paid = txns.filter { it.type == TxnType.TRANSFER && it.toAccountId == card.id && it.date >= cutoff }.sumOf { it.toAmount ?: it.amount }
            val payable = maxOf(0, billed - paid)
            val due = cycle.nextDueDate(lastStatement)
            card.id to CardStatus(payable, maxOf(0, owed - payable), due, if (card.autopayFromId != null) cycle.autopayDateFor(due, card.autopayDay) else null)
        }
    }

    /** 扣款帳戶餘額不足：[needed] 是這個帳戶接下來要自動扣繳的卡費合計。 */
    data class FundingWarning(
        val from: AccountEntity, val balance: Long, val needed: Long, val date: LocalDate, val cards: List<Pair<AccountEntity, Long>>,
    ) { val shortBy: Long get() = needed - balance }

    /**
     * 檢查 [days] 天內的信用卡自動扣繳：同一個扣款帳戶的應繳金額加總後，若大於帳戶目前餘額就警告。
     * 只看有設定自動扣繳的卡（手動繳費的卡不知道會從哪個帳戶付）。
     */
    suspend fun fundingWarnings(today: LocalDate = LocalDate.now(), days: Long = 45): List<FundingWarning> {
        val status = cardStatus(today)
        val accounts = db.accounts().all().filter { !it.archived }
        val bal = balances.first().associate { it.account.id to it.balance }
        return accounts.filter { it.type == AccountType.CREDIT_CARD && it.autopayFromId != null }
            .mapNotNull { card ->
                val st = status[card.id] ?: return@mapNotNull null
                val d = st.autopayDate ?: return@mapNotNull null
                if (st.payable <= 0 || d.isBefore(today) || d.isAfter(today.plusDays(days))) null else Triple(card, st.payable, d)
            }
            .groupBy { it.first.autopayFromId!! }
            .mapNotNull { (fromId, list) ->
                val from = accounts.firstOrNull { it.id == fromId } ?: return@mapNotNull null
                val needed = list.sumOf { it.second }
                val b = bal[fromId] ?: 0L
                if (b >= needed) null else FundingWarning(from, b, needed, list.minOf { it.third }, list.map { it.first to it.second })
            }
            .sortedBy { it.date }
    }

    /** 首頁「即將到來」：信用卡繳款日與預估金額。 */
    suspend fun upcomingCardBills(today: LocalDate = LocalDate.now()): List<Triple<AccountEntity, LocalDate, Long>> {
        val fx = currentFx()
        val txns = db.txns().all().filter { it.status == TxnStatus.CONFIRMED }
        return db.accounts().all().filter { it.type == AccountType.CREDIT_CARD && it.statementDay != null && it.dueDay != null && !it.archived }
            .map { card ->
                val cycle = settings.value.cycleFor(card)!!
                val due = cycle.nextDueDate(today.minusDays(1))
                val statement = cycle.statementDateFor(due)
                val cutoff = if (statement.isAfter(today)) Long.MAX_VALUE else statement.plusDays(1).toMillis(LocalTime.MIDNIGHT)
                val owed = -balanceAsOf(card, txns, cutoff, fx)
                Triple(card, due, maxOf(0, owed))
            }.sortedBy { it.second }
    }

    // ---------- 股票 ----------

    suspend fun saveTrade(t: StockTradeEntity): Long {
        val old = if (t.id == 0L) null else db.stocks().getTrade(t.id)
        val r = db.stocks().upsertTrade(t)
        // 名稱改了：同一檔的其他紀錄（含自動股利）一起更新
        if (!t.name.isNullOrBlank()) db.stocks().allTrades()
            .filter { it.id != t.id && it.market == t.market && it.symbol.equals(t.symbol, true) && it.name != t.name }
            .forEach { db.stocks().upsertTrade(it.copy(name = t.name)) }
        // 買進的日期、股數或代號變了：立即用已下載的除權息資料重算這檔（與原本那檔）的自動股利
        pruneAutoDividends()
        listOfNotNull(t.market to t.symbol.uppercase(), old?.let { it.market to it.symbol.uppercase() }).distinct().forEach { (m, sym) ->
            dividendCache[m to sym]?.let { applyDividends(m, sym, it) }
        }
        return if (t.id == 0L) r else t.id
    }

    /** 最近下載的除權息資料（代號 → 事件），儲存交易時可離線立即重算。 */
    val dividendCache = java.util.concurrent.ConcurrentHashMap<Pair<Market, String>, List<com.enwei.moneymanager.core.DividendEvent>>()

    /** 移除已不成立的自動股利：對應的買進被刪除、改了代號，或買進日改到除權息日當天或之後。 */
    suspend fun pruneAutoDividends(): Int {
        var n = 0
        // 配股本身也是一批持股，刪掉後依附它的股利也要跟著刪，所以重複到沒有變動為止
        while (true) {
            val all = db.stocks().allTrades()
            val byId = all.associateBy { it.id }
            val stale = all.filter { d ->
                d.externalKey?.let { k -> k.startsWith("DIV:") || k.startsWith("SDIV:") } == true && run {
                    val lot = d.lotTradeId?.let { byId[it] }
                    lot == null || lot.market != d.market || !lot.symbol.equals(d.symbol, true) || lot.date >= d.date
                }
            }
            if (stale.isEmpty()) return n
            stale.forEach { db.stocks().deleteTrade(it.id) }
            n += stale.size
        }
    }

    suspend fun getTrade(id: Long) = db.stocks().getTrade(id)
    suspend fun deleteTrade(id: Long) {
        val t = db.stocks().getTrade(id)
        // 刪除自動除息：記住，之後不再自動建立
        t?.externalKey?.let { k ->
            val mark = when {
                k.startsWith("DIV:") -> k.removePrefix("DIV:")
                k.startsWith("SDIV:") -> k
                else -> null
            }
            if (mark != null) settings.update { it.copy(dismissedDividends = it.dismissedDividends + mark) }
        }
        db.stocks().deleteTrade(id)
        // 刪掉買進／賣出會改變之後的持股：移除失效的自動股利並重算這檔
        if (t != null && t.source != TxnSource.DIVIDEND) {
            pruneAutoDividends()
            dividendCache[t.market to t.symbol.uppercase()]?.let { applyDividends(t.market, t.symbol.uppercase(), it) }
        }
    }

    /**
     * 自動除息：依除息資料與當時持有的各批庫存，建立／更新「自動除息」股利紀錄（不需使用者輸入）。
     * - 紀錄以 externalKey「DIV:代號:除息日:批次」去重；使用者手動改過的會變成「DIVM:」並不再覆蓋。
     * - 同一次除息若使用者已手動輸入股利（沒有 externalKey），就不自動建立。
     * - 庫存變動（新增、刪除、修改買賣）後重新計算，金額跟著更新，不再持有的批次會移除。
     */
    suspend fun applyDividends(market: Market, symbol: String, events: List<com.enwei.moneymanager.core.DividendEvent>, today: LocalDate = LocalDate.now()): Int {
        val sym = symbol.uppercase()
        val all = db.stocks().allTrades().filter { it.market == market && it.symbol.uppercase() == sym }
        val buys = all.filter { it.side == StockSide.BUY }
        if (buys.isEmpty()) return 0
        val firstDay = buys.minOf { it.date }
        val byId = all.associateBy { it.id }
        val records = all.map { it.toRecord() }
        var changed = 0
        db.withTransaction {
            events.filter { it.exEpochDay > firstDay && it.exEpochDay <= today.toEpochDay() }.forEach { ev ->
                val manual = all.any { it.side == StockSide.CASH_DIVIDEND && it.externalKey == null && kotlin.math.abs(it.date - ev.exEpochDay) <= 20 }
                val prefix = "$sym:${LocalDate.ofEpochDay(ev.exEpochDay)}:"
                val autoExisting = all.filter { k -> k.externalKey?.let { it.startsWith("DIV:$prefix") || it.startsWith("SDIV:$prefix") } == true }
                if (manual) {
                    autoExisting.forEach { db.stocks().deleteTrade(it.id); changed++ }
                    return@forEach
                }
                val keep = HashSet<String>()
                // 現金股利（除息）與配股（除權）各自依公式分配到各批
                listOf(
                    Triple("DIV", StockSide.CASH_DIVIDEND, com.enwei.moneymanager.core.DividendCalc.allocate(records, ev)),
                    Triple("SDIV", StockSide.STOCK_DIVIDEND, com.enwei.moneymanager.core.DividendCalc.allocateStock(records, ev)),
                ).forEach { (kind, side, alloc) ->
                    alloc.forEach { (lotId, amt) ->
                        val suffix = "$prefix$lotId"
                        val key = "$kind:$suffix"
                        keep += key
                        if (all.any { it.externalKey == "${kind}M:$suffix" } || "$kind:$suffix" in settings.value.dismissedDividends ||
                            (kind == "DIV" && suffix in settings.value.dismissedDividends)
                        ) return@forEach
                        val lot = byId[lotId] ?: return@forEach
                        val old = autoExisting.find { it.externalKey == key }
                        val note = if (side == StockSide.CASH_DIVIDEND)
                            "自動除息：每股 ${"%.4f".format(ev.perShare).trimEnd('0').trimEnd('.')} 元 × ${lot.shares.toLong()} 股"
                        else "自動除權：每股配 ${"%.4f".format(ev.stockPerShare).trimEnd('0').trimEnd('.')} 股 × ${lot.shares.toLong()} 股"
                        val cashAmt = if (side == StockSide.CASH_DIVIDEND) amt else 0.0
                        val shares = if (side == StockSide.STOCK_DIVIDEND) amt else 0.0
                        if (old == null) {
                            db.stocks().upsertTrade(
                                StockTradeEntity(
                                    market = market, symbol = sym, name = lot.name, side = side, shares = shares, price = 0.0,
                                    cashAmount = cashAmt, currency = lot.currency, date = ev.exEpochDay,
                                    cashAccountId = lot.cashAccountId, brokerAccountId = lot.brokerAccountId,
                                    note = note, source = TxnSource.DIVIDEND, externalKey = key, lotTradeId = lotId,
                                ),
                            )
                            changed++
                        } else if (old.cashAmount != cashAmt || old.shares != shares || old.cashAccountId != lot.cashAccountId ||
                            old.brokerAccountId != lot.brokerAccountId || old.note != note || old.name != lot.name
                        ) {
                            db.stocks().upsertTrade(old.copy(cashAmount = cashAmt, shares = shares, cashAccountId = lot.cashAccountId, brokerAccountId = lot.brokerAccountId, note = note, name = lot.name))
                            changed++
                        }
                    }
                }
                autoExisting.filter { it.externalKey !in keep }.forEach { db.stocks().deleteTrade(it.id); changed++ }
            }
        }
        return changed
    }

    /** 刪除整檔持股（這個代號的所有買賣與股利紀錄）；交割帳戶餘額會自動回復。 */
    suspend fun deleteHolding(market: Market, symbol: String) = db.stocks().deleteSymbol(market, symbol.uppercase())
    suspend fun saveQuotes(list: List<QuoteEntity>) = db.stocks().upsertQuotes(list)
    suspend fun allTrades() = db.stocks().allTrades()
    suspend fun saveFx(list: List<FxRateEntity>) = db.fx().upsertAll(list)

    suspend fun usedCurrencies(): Set<String> =
        (db.accounts().all().map { it.currency } + db.stocks().allTrades().map { it.currency }).toSet()

    // ---------- 備份 ----------

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }

    suspend fun exportJson(): String = json.encodeToString(
        Backup(
            exportedAt = System.currentTimeMillis(),
            accounts = db.accounts().all(),
            categories = db.categories().all(),
            transactions = db.txns().all(),
            recurring = db.recurring().all(),
            trades = db.stocks().allTrades(),
            rules = db.rules().all(),
            bookmarks = settings.value.bookmarks,
            memos = settings.value.memos,
            budgets = settings.value.budgets,
            notifPackages = settings.value.notifPackages,
            notifAccountMap = settings.value.notifAccountMap,
            brokerRebates = settings.value.brokerRebates,
            learnedSources = settings.value.learnedSources,
            cashCounts = settings.value.cashCounts,
            cardShift = settings.value.cardShift,
            cardBank = settings.value.cardBank,
            cardDueOffset = settings.value.cardDueOffset,
        ),
    )

    suspend fun importJson(text: String): Backup {
        val b = json.decodeFromString<Backup>(text)
        db.withTransaction {
            db.txns().clear(); db.accounts().clear(); db.categories().clear()
            db.recurring().clear(); db.stocks().clearTrades(); db.rules().clear()
            db.accounts().insertAll(b.accounts)
            db.categories().insertAll(b.categories)
            db.txns().insertAll(b.transactions)
            db.recurring().insertAll(b.recurring)
            db.stocks().insertTrades(b.trades)
            db.rules().insertAll(b.rules)
        }
        settings.update {
            it.copy(
                bookmarks = b.bookmarks, memos = b.memos, budgets = b.budgets,
                notifPackages = it.notifPackages + b.notifPackages, notifAccountMap = it.notifAccountMap + b.notifAccountMap,
                brokerRebates = b.brokerRebates, learnedSources = it.learnedSources + b.learnedSources, cashCounts = b.cashCounts,
                cardShift = b.cardShift, cardBank = b.cardBank, cardDueOffset = b.cardDueOffset,
            )
        }
        return b
    }

    fun monthTotals(txns: List<TxnEntity>, fx: FxConverter): Pair<Long, Long> {
        var income = 0L
        var expense = 0L
        txns.filter { it.status == TxnStatus.CONFIRMED }.forEach {
            when (it.type) {
                TxnType.INCOME -> income += fx.convert(it.amount, it.currency)
                TxnType.EXPENSE -> expense += fx.convert(it.amount, it.currency)
                TxnType.TRANSFER -> Unit
            }
        }
        return income to expense
    }
}

fun StockTradeEntity.toRecord() = TradeRecord(symbol.uppercase(), side, shares, price, fee, tax, date, cashAmount, id, lotTradeId)

/** 淨資產（基準幣）= 帳戶餘額換算 + 持股市值換算。 */
fun netWorth(balances: List<AccountBalance>, holdings: List<HoldingView>, fx: FxConverter): Long =
    balances.filter { it.account.includeInNetWorth && !it.account.archived }.sumOf { fx.convert(it.balance, it.account.currency) } +
        holdings.sumOf { fx.convert(Money.toMinor(it.marketValue), it.currency) }

private fun sumLong(a: Long, b: Long): Long = a + b

const val LINE_PACKAGE = "jp.naver.line.android"

/** LINE 的錢包付款通知（標題「LINE錢包」或內文以 LINE Pay 開頭），排除一般聊天訊息。 */
fun isLineWallet(title: String?, text: String?): Boolean {
    val t = title.orEmpty()
    val body = text.orEmpty().trimStart()
    return listOf("LINE錢包", "LINE Wallet", "LINE Pay", "LINE Bank", "LINE BANK").any { t.contains(it, ignoreCase = true) } ||
        body.startsWith("LINE Pay", ignoreCase = true)
}
