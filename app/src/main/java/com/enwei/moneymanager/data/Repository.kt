package com.enwei.moneymanager.data

import androidx.room.withTransaction
import com.enwei.moneymanager.MoneyApp
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
import kotlinx.coroutines.launch
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
        val before = if (t.id != 0L) db.txns().get(t.id) else null
        val r = db.txns().upsert(t)
        if (t.status == TxnStatus.CONFIRMED) learnMerchant(t)
        if (before?.status == TxnStatus.PENDING && t.status == TxnStatus.CONFIRMED) learnFromConfirmed(t)
        return if (t.id == 0L) r else t.id
    }

    suspend fun deleteTxn(id: Long) {
        db.txns().delete(id)
    }

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
        db.txns().all().filter { it.source == TxnSource.NOTIFICATION && it.rawText?.contains(AI_TAG) == true && it.status == TxnStatus.CONFIRMED }
            .forEach { learnFromConfirmed(it) }
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

    // ---------- AI 整理「不明」 ----------

    /** 沒有分類或分類為「不明」的已確認收支。 */
    private fun isUnknown(t: TxnEntity, cats: Map<Long, CategoryEntity>): Boolean =
        t.type != TxnType.TRANSFER && t.status == TxnStatus.CONFIRMED && (t.categoryId == null || cats[t.categoryId]?.name == "不明")

    /** 還沒請 AI 看過的「不明」交易數。 */
    /** 計算量大的查詢都在背景執行緒做，畫面不會卡。 */
    suspend fun unknownCount(): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { unknownCountImpl() }

    private suspend fun unknownCountImpl(): Int {
        val cats = db.categories().all().associateBy { it.id }
        val asked = settings.value.aiSuggestions.map { it.txnId }.toSet()
        return db.txns().all().count { isUnknown(it, cats) && it.id !in asked }
    }

    /**
     * 請 AI 幫「不明」交易挑分類，結果放進待確認的「AI 建議分類」（不會直接修改交易）。
     * 每次送約 8 筆，附上這個人在類似情境（同帳戶、金額相近、時段相近）過去常用的分類當參考。
     */
    suspend fun aiSortUnknown(onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> }): Int {
        if (!com.enwei.moneymanager.net.GroqAi.enabled) return 0
        val catsAll = db.categories().all()
        val cats = catsAll.associateBy { it.id }
        fun path(id: Long) = cats[id]?.let { c -> c.parentId?.let { cats[it]?.name + " / " } .orEmpty() + c.name } ?: "?"
        val accounts = db.accounts().all().associateBy { it.id }
        val all = db.txns().all()
        val asked = settings.value.aiSuggestions.map { it.txnId }.toSet()
        val targets = all.filter { isUnknown(it, cats) && it.id !in asked }
        val known = all.filter { it.status == TxnStatus.CONFIRMED && it.type != TxnType.TRANSFER && it.categoryId != null && !isUnknown(it, cats) }
        val zone = ZoneId.systemDefault()
        fun at(t: TxnEntity) = java.time.Instant.ofEpochMilli(t.date).atZone(zone).toLocalDateTime()
        var created = 0
        var done = 0
        onProgress(0, targets.size)
        for (type in listOf(TxnType.EXPENSE, TxnType.INCOME)) {
            val choices = catsAll.filter { it.kind == type && !it.archived && it.name != "不明" && it.name != "對帳調整" && catsAll.none { c -> c.parentId == it.id } }
                .map { it.id to path(it.id) }
            if (choices.isEmpty()) continue
            val big = com.enwei.moneymanager.net.GroqAi.bigContext(com.enwei.moneymanager.net.GroqAi.AiTask.UNKNOWN)
            for (chunk in targets.filter { it.type == type }.chunked(if (big) 60 else 8)) {
                val items = chunk.map { t ->
                    val time = at(t)
                    val similar = known.filter { k ->
                        k.type == type && k.accountId == t.accountId && k.amount in (t.amount * 7 / 10)..(t.amount * 13 / 10)
                    }
                    val near = similar.filter { kotlin.math.abs(at(it).hour - time.hour) <= 2 }.ifEmpty { similar }
                    val hints = near.groupingBy { path(it.categoryId!!) }.eachCount().entries.sortedByDescending { it.value }.take(3)
                        .joinToString("、") { "${it.key}×${it.value}" }
                    val desc = "${time.toLocalDate()} 週${Recurrence.weekdayName(time.dayOfWeek)} ${"%02d:%02d".format(time.hour, time.minute)}｜" +
                        "${type.label} ${Money.format(t.amount, t.currency)}｜帳戶：${accounts[t.accountId]?.name ?: "未指定"}" +
                        listOfNotNull(t.merchant?.takeIf { it.isNotBlank() }, t.note?.takeIf { it.isNotBlank() }).joinToString("，").let { if (it.isEmpty()) "" else "｜說明：$it" }
                    com.enwei.moneymanager.net.GroqAi.Unsorted(t.id, desc, hints)
                }
                val res = com.enwei.moneymanager.net.GroqAi.suggestCategories(items, choices)
                val add = res.map { (id, v) -> AiSuggestion(id, v.first, v.second) }
                // 判斷不出來的也記下（標為略過），避免下次重複詢問
                val skipped = chunk.filter { it.id !in res }.map { AiSuggestion(it.id, -1, "AI 判斷不出來", rejected = true) }
                if (res.isNotEmpty() || com.enwei.moneymanager.net.GroqAi.lastError == null) {
                    settings.update { st -> st.copy(aiSuggestions = st.aiSuggestions + add + skipped) }
                }
                created += add.size
                done += chunk.size
                onProgress(done, targets.size)
                // 免費方案每分鐘有請求次數上限
                kotlinx.coroutines.delay(if (big) 6000 else 2500)
            }
        }
        return created
    }

    // ---------- 可能重複的交易 ----------

    /** 一組可能重複的交易：[keep] 為合併時保留的那筆（帳戶以通知為準）。 */
    data class DupPair(val keep: TxnEntity, val drop: TxnEntity) {
        val key: String get() = "${minOf(keep.id, drop.id)}-${maxOf(keep.id, drop.id)}"
    }

    /**
     * 找出近 [days] 天可能重複記的交易：同收支、同金額、30 分鐘內（錢包畫面只有日期時為同一天），
     * 而且至少一筆來自通知或錢包畫面（自動記帳最容易重複）。標記過「不是重複」的不再列出。
     */
    suspend fun findDuplicates(days: Long = 90): List<DupPair> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { findDuplicatesImpl(days) }

    private suspend fun findDuplicatesImpl(days: Long): List<DupPair> {
        val since = System.currentTimeMillis() - days * 86_400_000
        val dismissed = settings.value.dupDismissed
        val list = db.txns().all().filter { it.type != TxnType.TRANSFER && it.date >= since }
        fun rank(t: TxnEntity) = when (t.source) { TxnSource.NOTIFICATION -> 0; TxnSource.MANUAL -> 1; TxnSource.SCREEN -> 2; else -> 3 }
        fun midnight(t: TxnEntity) = java.time.Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalTime() == LocalTime.MIDNIGHT
        val out = ArrayList<DupPair>()
        val used = HashSet<Long>()
        list.groupBy { it.type to it.amount }.values.filter { it.size > 1 }.forEach { group ->
            val sorted = group.sortedBy { it.date }
            for (i in sorted.indices) for (j in i + 1 until sorted.size) {
                val a = sorted[i]; val b = sorted[j]
                if (a.id in used || b.id in used) continue
                val auto = setOf(TxnSource.NOTIFICATION, TxnSource.SCREEN)
                if (a.source !in auto && b.source !in auto) continue
                val near = kotlin.math.abs(a.date - b.date) <= 30 * 60_000L ||
                    ((midnight(a) || midnight(b)) && a.date.toLocalDate() == b.date.toLocalDate())
                if (!near) continue
                val (keep, drop) = if (rank(a) <= rank(b)) a to b else b to a
                val p = DupPair(keep, drop)
                if (p.key in dismissed) continue
                out += p; used += a.id; used += b.id
            }
        }
        return out.sortedByDescending { it.keep.date }
    }

    /** 合併：保留一筆，套用較好的名稱與分類，刪掉另一筆。 */
    suspend fun mergeDuplicate(p: DupPair, name: String? = null, categoryId: Long? = null) {
        val keep = db.txns().get(p.keep.id) ?: return
        val drop = db.txns().get(p.drop.id)
        val merchant = name ?: if (weakMerchant(keep.merchant) && drop != null && !weakMerchant(drop.merchant)) drop.merchant else keep.merchant
        db.txns().upsert(
            keep.copy(
                merchant = merchant, categoryId = categoryId ?: keep.categoryId ?: drop?.categoryId,
                note = keep.note ?: drop?.note,
                rawText = listOfNotNull(keep.rawText, drop?.rawText).joinToString("\n—\n").ifEmpty { null },
            ),
        )
        if (drop != null) db.txns().delete(drop.id)
    }

    fun dismissDuplicate(p: DupPair) = settings.update { it.copy(dupDismissed = it.dupDismissed + p.key) }

    /** 請 AI 判斷每組是否為同一筆，並給出較精確的交易名稱與分類。 */
    suspend fun aiJudgeDuplicates(
        pairs: List<DupPair>,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Map<String, DupJudgement> {
        if (!com.enwei.moneymanager.net.GroqAi.enabled || pairs.isEmpty()) return emptyMap()
        val all = db.categories().all()
        val accounts = db.accounts().all().associateBy { it.id }
        fun path(id: Long) = all.find { it.id == id }?.let { c -> c.parentId?.let { pid -> all.find { it.id == pid }?.name + " / " }.orEmpty() + c.name } ?: "?"
        fun desc(t: TxnEntity) = "${t.date.toLocalDateTime().toString().replace('T', ' ').take(16)}｜${accounts[t.accountId]?.name ?: "未指定"}｜" +
            "${t.source.label}｜${t.merchant ?: "（無名稱）"}${t.note?.let { "，$it" } ?: ""}" +
            (t.categoryId?.let { "｜分類 ${path(it)}" } ?: "") + (t.rawText?.take(120)?.let { "｜原文：${it.replace('\n', ' ')}" } ?: "")
        val out = HashMap<String, DupJudgement>()
        val chunks = pairs.groupBy { it.keep.type }.values.flatMap { it.chunked(6) }
        var done = 0
        onProgress(done, pairs.size)
        for (chunk in chunks) {
            val type = chunk.first().keep.type
            val cats = all.filter { it.kind == type && !it.archived && all.none { c -> c.parentId == it.id } && it.name != "不明" }.map { it.id to path(it.id) }
            val items = chunk.map { p -> p.key to "金額 ${Money.format(p.keep.amount, p.keep.currency)}\n  A：${desc(p.keep)}\n  B：${desc(p.drop)}" }
            out += com.enwei.moneymanager.net.GroqAi.judgeDuplicates(items, cats)
            done += chunk.size
            onProgress(done, pairs.size)
            kotlinx.coroutines.delay(2500)
        }
        // 記住判斷結果：下次打開不用再問 AI
        if (out.isNotEmpty()) settings.update { st -> st.copy(dupVerdicts = (st.dupVerdicts + out).entries.toList().takeLast(300).associate { it.key to it.value }) }
        return out
    }

    /** 新的自動記帳進來後，等一陣子（通知、錢包畫面可能陸續到）再請 AI 整理一次。 */
    fun scheduleAutoTidy() {
        if (!settings.value.aiAutoTidy || !com.enwei.moneymanager.net.GroqAi.enabled) return
        com.enwei.moneymanager.work.AiBackgroundWork.scheduleAutoTidy(MoneyApp.instance)
    }

    /**
     * AI 自動整理（背景執行）：近 14 天新出現的可能重複，請 AI 判斷；
     * 兩筆都是自動記帳（通知、錢包畫面）且 AI 判定為同一筆時直接合併，其餘留在「待確認」由使用者決定。
     */
    suspend fun aiAutoTidy(onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> }): Int {
        val s = settings.value
        if (!s.aiAutoTidy || !com.enwei.moneymanager.net.GroqAi.enabled) return 0
        val fresh = findDuplicates(14).filter { it.key !in s.dupVerdicts }.take(12)
        if (fresh.isEmpty()) return 0
        val verdicts = aiJudgeDuplicates(fresh, onProgress)
        val auto = setOf(TxnSource.NOTIFICATION, TxnSource.SCREEN)
        val done = ArrayList<String>()
        fresh.forEach { p ->
            val v = verdicts[p.key] ?: return@forEach
            if (!v.same || p.keep.source !in auto || p.drop.source !in auto) return@forEach
            mergeDuplicate(p, v.name, v.categoryId)
            done += "${p.keep.date.toLocalDate()} ${Money.format(p.keep.amount, p.keep.currency)} ${v.name ?: p.keep.merchant ?: ""}：" +
                "合併${p.keep.source.label}與${p.drop.source.label}的同一筆" + (v.reason.takeIf { it.isNotBlank() }?.let { "（$it）" } ?: "")
        }
        if (done.isNotEmpty()) settings.update { it.copy(aiTidyLog = (done + it.aiTidyLog).take(30)) }
        return done.size
    }

    /**
     * 確認一筆 AI 判讀的通知交易後，自動學會這種通知的格式（同「教它辨識」），之後由規則直接辨識、不再呼叫 AI。
     * 只在規則讀得出同樣的金額時才學，確保學到的格式真的能用。
     */
    private suspend fun learnFromConfirmed(t: TxnEntity) {
        val s = settings.value
        if (!s.aiAutoTidy || t.source != TxnSource.NOTIFICATION || t.rawText?.contains(AI_TAG) != true) return
        val entry = db.notificationLog().all().lastOrNull { it.txnId == t.id } ?: return
        val raw = listOfNotNull(entry.title, entry.text).joinToString(" ")
        if (NotificationParser.findAmount(NotificationParser.clean(raw))?.first != t.amount) return
        val key = learnKey(entry.title, entry.text)
        if (key.isBlank()) return
        if (s.learnedSources.any { it.pkg == entry.packageName && it.title == key }) return
        val src = sourceBank(entry.packageName, entry.title)
        val learned = when (t.type) {
            TxnType.EXPENSE -> LearnedSource(entry.packageName, key, income = false, accountId = t.accountId)
            TxnType.INCOME -> LearnedSource(entry.packageName, key, income = true, accountId = t.accountId)
            TxnType.TRANSFER -> {
                // 通知來自轉入端的銀行時是「轉入」，否則當成「轉出」
                val inbound = src != null && t.toAccountId?.let { id -> db.accounts().get(id)?.let { BankDirectory.matches(src, it.name) } } == true
                if (inbound) LearnedSource(entry.packageName, key, income = true, accountId = t.toAccountId, kind = "TRANSFER_IN", toAccountId = t.accountId)
                else LearnedSource(entry.packageName, key, income = false, accountId = t.accountId, kind = "TRANSFER_OUT", toAccountId = t.toAccountId)
            }
        }
        val msg = "已保存本機通知辨識偏好（${learned.label}）"
        settings.update { it.copy(learnedSources = it.learnedSources + learned, aiTidyLog = (listOf(msg) + it.aiTidyLog).take(30)) }
        // 學會之後把 AI 標記拿掉，這筆就和規則辨識的一樣
        db.txns().get(t.id)?.let { cur -> db.txns().upsert(cur.copy(rawText = cur.rawText?.replace("\n$AI_TAG", "")?.replace(AI_TAG, ""))) }
    }

    /** 接受 AI 建議：套用分類。 */
    suspend fun acceptAiSuggestion(sg: AiSuggestion) {
        db.txns().get(sg.txnId)?.let { t -> if (db.categories().all().any { it.id == sg.categoryId }) db.txns().upsert(t.copy(categoryId = sg.categoryId)) }
        settings.update { st -> st.copy(aiSuggestions = st.aiSuggestions.map { if (it.txnId == sg.txnId) it.copy(rejected = true, reason = "已套用") else it }) }
    }

    /** 略過 AI 建議：維持原分類，不再詢問。 */
    fun rejectAiSuggestion(sg: AiSuggestion) {
        settings.update { st -> st.copy(aiSuggestions = st.aiSuggestions.map { if (it.txnId == sg.txnId) it.copy(rejected = true) else it }) }
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
        settings.value.loans[a.id]?.recurringId?.let { db.recurring().delete(it) }
        settings.update { s ->
            s.copy(
                defaultAccountId = s.defaultAccountId.takeIf { it != a.id },
                notifAccountMap = s.notifAccountMap.filterValues { it != a.id },
                cashCounts = s.cashCounts - a.id,
                cardShift = s.cardShift - a.id,
                cardBank = s.cardBank - a.id,
                cardDueOffset = s.cardDueOffset - a.id,
                brokerRebates = s.brokerRebates - a.id,
                loans = s.loans - a.id,
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
     * 清掉舊版自動建立、從未使用的「現金」「銀行帳戶」：餘額 0、沒有任何交易、股票、週期或連結，
     * 而且還有其他帳戶時才刪除。
     */
    private suspend fun removeUnusedSeedAccounts() {
        val accounts = db.accounts().all()
        val txns = db.txns().all()
        val trades = db.stocks().allTrades()
        val recurring = db.recurring().all()
        val candidates = accounts.filter { a ->
            (a.name == "現金" && a.type == AccountType.CASH || a.name == "銀行帳戶" && a.type == AccountType.BANK) &&
                a.initialBalance == 0L &&
                txns.none { it.accountId == a.id || it.toAccountId == a.id } &&
                trades.none { it.cashAccountId == a.id || it.brokerAccountId == a.id } &&
                recurring.none { it.accountId == a.id || it.toAccountId == a.id } &&
                accounts.none { it.autopayFromId == a.id || it.linkedAccountId == a.id }
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
    suspend fun previewNotification(
        pkg: String, title: String?, text: String?, postedAt: Long? = null,
        aiOverride: com.enwei.moneymanager.core.ParsedTransaction? = null,
        aiHandled: Boolean = false,
    ): Pair<Boolean, String> {
        val s = settings.value
        if (pkg == LINE_PACKAGE && !isLineWallet(title, text)) return false to "LINE 聊天訊息，不處理"
        val fillMail = pkg == GMAIL_PACKAGE && isBrokerFillMail(title, text)
        if (pkg == GMAIL_PACKAGE && !fillMail) return false to previewEmail(title, text)
        if (isRedacted(text)) return false to "內容被 Android 隱藏：Android 15 起，系統判斷含驗證碼（OTP）的通知會對第三方 App 隱藏內容，" +
            "App 收不到金額。一般的消費通知不含驗證碼，通常不會被隱藏。若一定要讀取，可在電腦用 adb 執行下方指令開放。"
        val learned = s.learnedFor(pkg, title, text)
        val bankSrc = sourceBank(pkg, title)
        val enabled = fillMail || pkg == LINE_PACKAGE || learned != null || pkg in s.notifPackages ||
            (bankSrc != null && db.accounts().all().any { !it.archived && BankDirectory.matches(bankSrc, it.name) })
        NotificationParser.parseStockFill(title, text)?.let { f ->
            val broker = fillBroker(pkg, title, text, bankSrc)
            val lines = listOfNotNull(
                "股票成交：${if (f.side == StockSide.SELL) "賣出" else "買進"} ${f.symbol} ${f.name.orEmpty()} ${f.shares.toLong()} 股 @ ${f.price}",
                f.at?.let { "成交時間：${it.toLocalDate()} ${"%02d:%02d".format(it.hour, it.minute)}" },
                "會新增到「${broker?.name ?: "（沒有證券帳戶，請先新增）"}」的持股" + (broker?.let { b -> lookupName(b.linkedAccountId).takeIf { it != "?" }?.let { "，交割帳戶：$it" } } ?: ""),
                if (enabled) null else "這個 App 尚未勾選監聽，勾選後才會自動建立",
            )
            return (enabled && broker != null) to lines.joinToString("\n")
        }
        var aiParsed = aiOverride != null
        val p = aiOverride ?: learned?.parse(title, text)
            ?: NotificationParser.parse(title, text)
            ?: (if (!aiHandled && enabled && com.enwei.moneymanager.net.GroqAi.enabled) com.enwei.moneymanager.net.GroqAi.parseNotification(title, text)?.also { aiParsed = true } else null)
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
        val at = p.occurredAt?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: postedAt ?: System.currentTimeMillis()
        // 已經記過的同一筆（這則通知本身、錢包畫面讀到的、手動記的）：說明記帳時會怎麼處理
        val raw1 = listOfNotNull(title, text).joinToString("\n")
        val same = if (p.kind == ParsedKind.NORMAL) db.txns().all().filter { it.type == type && it.amount == p.amountMinor }.let { c ->
            c.firstOrNull { it.rawText?.contains(raw1) == true && kotlin.math.abs(it.date - at) <= 24L * 3600 * 1000 }?.let { "這則通知已經記過（${it.date.toLocalDate()}），不會重複記帳" }
                ?: c.firstOrNull {
                    (it.source == TxnSource.SCREEN || (it.source == TxnSource.MANUAL && (it.accountId == null || it.accountId == account?.id))) &&
                        (kotlin.math.abs(it.date - at) <= 15L * 60 * 1000 || (it.source == TxnSource.SCREEN && it.date.toLocalDate() == at.toLocalDate() &&
                            Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalTime() == LocalTime.MIDNIGHT))
                }?.let { "和${if (it.source == TxnSource.SCREEN) "錢包畫面讀到" else "手動記"}的「${it.merchant ?: "未命名"}」是同一筆：會合併，帳戶改成這則通知的帳戶" }
                ?: c.firstOrNull { it.source == TxnSource.NOTIFICATION && kotlin.math.abs(it.date - at) <= 3 * 60_000 }
                    ?.let { "3 分鐘內另一個 App 已通知同一筆（${it.merchant ?: "未命名"}），不會重複記帳" }
        } else null
        var cat = if (p.kind == ParsedKind.NORMAL) suggestCategory(p.merchant, type, at, p.amountMinor, account?.id)
            ?.let { sg -> categoryPath(sg.categoryId)?.let { "$it（${sg.reason}）" } } else null
        var aiName: String? = null
        val pm = p.merchant
        if (p.kind == ParsedKind.NORMAL && pm != null && (cat == null || looksRaw(pm))) {
            aiNameAndCategory(pm, type, at)?.let { (name, c) ->
                if (looksRaw(pm)) aiName = name
                if (cat == null && c != null) cat = categoryPath(c)?.let { "$it（AI 推測）" }
            }
        }
        val lines = listOfNotNull(
            "$what ${Money.format(p.amountMinor, p.currency)} ${p.currency}",
            "帳戶：${account?.name ?: "（未指定，請設定預設帳戶）"}" + (p.cardLast4?.let { "（卡號末四碼 $it）" } ?: ""),
            p.merchant?.let { "商家：$it" + (aiName?.let { n -> " → AI 命名「$n」" } ?: "") },
            if (aiParsed) "（規則看不懂，由 AI 判讀；記帳時放到「待確認」，確認後會自動學會這種格式）" else null,
            "分類：" + (cat ?: "無法判斷（沒有商家名稱或沒有相似紀錄），記帳後可在通知上一鍵選分類"),
            p.occurredAt?.let { "交易時間：${it.toLocalDate()} ${"%02d:%02d".format(it.hour, it.minute)}" },
            learned?.let { l -> "（依你教過的格式辨識：${l.label}" + (l.toAccountId?.let { id -> accounts.firstOrNull { it.id == id }?.let { a -> if (l.parsedKind == ParsedKind.TRANSFER_IN) "，從「${a.name}」" else "，到「${a.name}」" } } ?: "") + "）" },
            same?.let { "重複檢查：$it" },
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
    data class NotificationInput(val pkg: String, val title: String?, val text: String?, val postedAt: Long)

    /**
     * 試算通知列上的一批通知，並和實際記帳一樣推算轉帳：
     * 一邊轉出、一邊轉入（金額相同、10 分鐘內、不同帳戶）會合併成一筆轉帳；
     * 銀行的轉帳 Email 寫有轉入銀行時，會把同一家銀行剛剛的轉出改成轉帳。
     */
    suspend fun previewBatch(list: List<NotePreview>): List<Pair<Boolean, String>> {
        val ai = classifyNotificationBatch(list.map { NotificationInput(it.pkg, it.title, it.text, it.postedAt) })
        val base = list.mapIndexed { index, note ->
            previewNotification(note.pkg, note.title, note.text, note.postedAt, ai.parsed[index], index in ai.handled)
        }.toMutableList()
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

    /**
     * 成交通知要記到哪個證券帳戶：通知裡的帳號前綴（例如「9A9L-0609**2」）對到帳戶說明裡填的帳號 →
     * 這個 App 指定的帳戶 → 同一家券商名稱 → 第一個證券帳戶。
     */
    private suspend fun fillBroker(pkg: String, title: String?, text: String?, bankSrc: BankDirectory.Bank?): AccountEntity? {
        val brokers = db.accounts().all().filter { it.type == AccountType.BROKERAGE && !it.archived }
        val fillText = listOfNotNull(title, text).joinToString(" ")
        return brokers.firstOrNull { b -> b.description?.substringBefore('-')?.trim()?.takeIf { it.length >= 3 }?.let { fillText.contains(it) } == true }
            ?: settings.value.notifAccountMap[pkg]?.let { id -> brokers.firstOrNull { it.id == id } }
            ?: bankSrc?.let { b -> brokers.firstOrNull { BankDirectory.matches(b, it.name) } }
            ?: brokers.firstOrNull()
    }

    /** Email 形式的成交通知：內文的帳號前綴對得到你的證券帳戶才處理（其他 Email 不記帳）。 */
    private suspend fun isBrokerFillMail(title: String?, text: String?): Boolean {
        if (NotificationParser.parseStockFill(title, text) == null) return false
        val all = listOfNotNull(title, text).joinToString(" ")
        return db.accounts().all().any { b ->
            b.type == AccountType.BROKERAGE && !b.archived &&
                b.description?.substringBefore('-')?.trim()?.takeIf { it.length >= 3 }?.let { all.contains(it) } == true
        }
    }

    suspend fun ingestNotificationBatch(notices: List<NotificationInput>): List<IngestResult?> {
        if (notices.isEmpty()) return emptyList()
        val ai = classifyNotificationBatch(notices)
        return notices.mapIndexed { index, notice ->
            ingestNotification(notice.pkg, notice.title, notice.text, notice.postedAt, aiOverride = ai.parsed[index], aiHandled = index in ai.handled)
        }
    }

    private suspend fun classifyNotificationBatch(notices: List<NotificationInput>): com.enwei.moneymanager.net.GroqAi.NotificationBatchResult {
        val s = settings.value
        val accounts = db.accounts().all().filter { !it.archived }
        val candidates = notices.mapIndexedNotNull { index, notice ->
            if (notice.pkg == GMAIL_PACKAGE || isRedacted(notice.text) || NotificationParser.parseStockFill(notice.title, notice.text) != null) {
                return@mapIndexedNotNull null
            }
            val learned = s.learnedFor(notice.pkg, notice.title, notice.text)
            if (learned?.parse(notice.title, notice.text) != null || NotificationParser.parse(notice.title, notice.text) != null) {
                return@mapIndexedNotNull null
            }
            val bank = sourceBank(notice.pkg, notice.title)
            val enabled = notice.pkg in s.notifPackages ||
                (notice.pkg == LINE_PACKAGE && isLineWallet(notice.title, notice.text)) ||
                (bank != null && accounts.any { BankDirectory.matches(bank, it.name) })
            if (!enabled) return@mapIndexedNotNull null
            Triple(index, notice.title, notice.text)
        }
        return if (candidates.isNotEmpty()) com.enwei.moneymanager.net.GroqAi.parseNotificationBatch(candidates)
        else com.enwei.moneymanager.net.GroqAi.NotificationBatchResult(emptyMap(), emptySet())
    }

    suspend fun ingestNotification(
        pkg: String, title: String?, text: String?, postedAt: Long, force: Boolean = false,
        aiOverride: com.enwei.moneymanager.core.ParsedTransaction? = null,
        aiHandled: Boolean = false,
    ): IngestResult? {
        // LINE：只處理「LINE錢包」付款通知（LINE Bank Money／LINE Pay），聊天訊息一律略過；不需另外勾選 LINE
        val isLine = pkg == LINE_PACKAGE
        if (isLine && !isLineWallet(title, text)) return null
        val s = settings.value
        val log = db.notificationLog()
        // 同一則通知可能因更新而重複送達
        if (!force && log.countSame(pkg, title, text, postedAt - 10 * 60_000) > 0) return null
        log.prune(System.currentTimeMillis() - 60L * 24 * 3600 * 1000)
        // 銀行寄的 Email：只用來補充轉帳的轉入銀行，不另外記帳（其他 Email 一律不處理）
        // 券商寄來的成交 Email 例外：當成成交通知建立持股
        val fillMail = pkg == GMAIL_PACKAGE && isBrokerFillMail(title, text)
        if (pkg == GMAIL_PACKAGE && !fillMail) return enrichFromEmail(title, text, postedAt)
        val learned = s.learnedFor(pkg, title, text)
        val bankSrc = sourceBank(pkg, title)
        val allAccounts = db.accounts().all().filter { !it.archived }
        // 來源 App 是你有帳戶的銀行（例如「大戶 DAWHO」對應帳戶「大戶投」）：不用另外勾選
        val enabled = force || fillMail || isLine || learned != null || pkg in s.notifPackages ||
            (bankSrc != null && allAccounts.any { BankDirectory.matches(bankSrc, it.name) })
        fun logAs(result: String, txnId: Long? = null) =
            NotificationLogEntity(packageName = pkg, title = title, text = text, postedAt = postedAt, result = result, txnId = txnId)

        val fill = NotificationParser.parseStockFill(title, text)
        var parsed = if (fill != null) null else aiOverride ?: learned?.parse(title, text) ?: NotificationParser.parse(title, text)
        // 規則看不懂、但來自已啟用的銀行／支付 App：請 AI 判讀（結果一律放「待確認」）
        var aiParsed = aiOverride != null
        if (fill == null && parsed == null && !aiHandled && enabled && !isRedacted(text) && com.enwei.moneymanager.net.GroqAi.enabled) {
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
            val broker = fillBroker(pkg, title, text, bankSrc)
            val (bFee, bTax) = if (broker?.feeDiscount != null)
                StockCalc.estimate(market, fill.symbol, fill.side, fill.shares, fill.price, s.feeSettings.copy(discount = broker.feeDiscount)) else fee to tax
            // 有成交時間時以成交時間去重：App 推播和成交 Email 都來時只記一次
            val stamp = fill.at?.let { "T$it" } ?: "${postedAt / 60_000}"
            val tradeDay = fill.at?.toLocalDate()?.takeIf { kotlin.math.abs(it.toEpochDay() - postedAt.toLocalDate().toEpochDay()) <= 7 } ?: postedAt.toLocalDate()
            val id = db.stocks().insertTrade(
                StockTradeEntity(
                    market = market, symbol = fill.symbol, name = fill.name, side = fill.side, shares = fill.shares,
                    price = fill.price, fee = bFee, tax = bTax, date = tradeDay.toEpochDay(),
                    cashAccountId = broker?.linkedAccountId ?: broker?.id, brokerAccountId = broker?.id,
                    source = TxnSource.NOTIFICATION, note = "由成交通知自動建立",
                    externalKey = "N:${fill.symbol}:${fill.side}:${fill.shares}:${fill.price}:$stamp",
                ),
            )
            if (id <= 0) { log.insert(logAs("DUPLICATE")); return null }
            log.insert(logAs("STOCK"))
            val sideLabel = if (fill.side == StockSide.SELL) "賣出" else "買進"
            return IngestResult("STOCK", id, "$sideLabel ${fill.symbol} ${fill.name.orEmpty()} ${fill.shares.toLong()} 股 @ ${fill.price} → ${broker?.name ?: "未指定證券帳戶"}")
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
        // 券商手續費退佣入帳：已自動估算記過的那筆，改成銀行實際入帳的金額與日期
        if (type == TxnType.INCOME && account != null && rebateWords.any { raw0.contains(it) }) {
            db.txns().all().filter {
                it.externalKey?.startsWith("REBATE:") == true && it.accountId == account.id && it.rawText == null &&
                    kotlin.math.abs(it.date - occurred) <= 20L * 86_400_000
            }.minByOrNull { kotlin.math.abs(it.date - occurred) }?.let { est ->
                return merged(est, est.copy(amount = p.amountMinor, date = occurred, note = "${est.note.orEmpty()}（已依實際入帳 $amountText 校正）".trim()),
                    "手續費退佣 $amountText → ${account.name}（校正估算金額）")
            }
        }
        // 錢包畫面先讀到、或自己先手動記了同一筆：合併成一筆（帳戶以通知為準，商家名稱保留較完整的）
        if (p.kind == ParsedKind.NORMAL) db.txns().all().firstOrNull {
            it.type == type && it.amount == p.amountMinor &&
                (kotlin.math.abs(it.date - occurred) <= 15L * 60 * 1000 ||
                    // 錢包畫面只有日期（記在當天 0:00）
                    (it.source == TxnSource.SCREEN && it.date.toLocalDate() == occurred.toLocalDate() &&
                        java.time.Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalTime() == LocalTime.MIDNIGHT)) &&
                (it.source == TxnSource.SCREEN || (it.source == TxnSource.MANUAL && (it.accountId == null || it.accountId == account?.id)))
        }?.let { other ->
            val name = if (weakMerchant(other.merchant) && !weakMerchant(p.merchant)) p.merchant else other.merchant
            val updated = other.copy(
                accountId = account?.id ?: other.accountId, merchant = name,
                categoryId = other.categoryId ?: suggestCategory(name, type, occurred, p.amountMinor, account?.id)?.categoryId,
            )
            return merged(other, updated, "${type.label} $amountText ${name.orEmpty()}（和${if (other.source == TxnSource.SCREEN) "錢包畫面" else "手動記帳"}的同一筆合併）")
        }
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
        val pm = p.merchant
        if (p.kind == ParsedKind.NORMAL && pm != null && (categoryId == null || looksRaw(pm))) {
            aiNameAndCategory(pm, type, occurred)?.let { (name, cat) ->
                if (name != null && looksRaw(pm)) merchant = name
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
    data class ScreenIngestResult(
        val wallet: String?, val total: Int, val tooOld: Int, val existing: Int, val created: Int, val since: LocalDate,
        /** 和已記的交易（通知、手動）合併、補上商家名稱的筆數 */
        val merged: Int = 0,
        /** 每一筆的處理結果（測試頁顯示） */
        val details: List<String> = emptyList(),
    )

    /** 商家名稱有沒有參考價值：空白、支付工具名稱、代碼都算沒有。 */
    private fun weakMerchant(m: String?): Boolean = m.isNullOrBlank() || CategoryGuesser.isGenericMerchant(m) || looksRaw(m) ||
        m in setOf("轉出", "轉入", "儲值", "電支儲值", "提款")

    /**
     * 找已經記過的同一筆：同收支、同金額，時間在 15 分鐘內（畫面只有日期時同一天），
     * 而且記在這個錢包、或是從通知來的（例如錢包綁信用卡，刷卡通知先到）。
     */
    private fun findSameTxn(all: List<TxnEntity>, type: TxnType, amount: Long, millis: Long, dateOnly: Boolean, walletId: Long?, claimed: Set<Long>): TxnEntity? {
        val day = millis.toLocalDate()
        return all.firstOrNull { t ->
            t.id !in claimed && t.type == type && t.amount == amount && t.externalKey?.startsWith("AX:") != true &&
                (if (dateOnly) t.date.toLocalDate() == day else kotlin.math.abs(t.date - millis) <= 15L * 60 * 1000) &&
                (t.accountId == walletId || t.source == TxnSource.NOTIFICATION)
        }
    }

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
        var merged = 0
        var aiCalls = 0
        val details = ArrayList<String>()
        // 這次讀取已對到的紀錄（同一天買兩筆一樣金額時，不會兩筆都對到同一則通知）
        val claimed = HashSet<Long>()
        val fresh = records.filter { !it.time.toLocalDate().isBefore(since) }
        fresh.forEach { r ->
            val label = "${r.time.toLocalDate()}${if (r.dateOnly) "" else " %02d:%02d".format(r.time.hour, r.time.minute)} " +
                "${r.merchant ?: r.typeLabel ?: ""} ${Money.format(r.amountMinor, wallet.currency)}"
            val base = "${r.time.toLocalDate()}${if (r.dateOnly) "" else "T" + r.time.toLocalTime()}:${if (r.isIncome) "+" else "-"}${r.amountMinor}:${r.merchant}"
            val n = seen.merge(base, 1) { x, y -> x + y }!!
            val key = "AX:$pkg:$base#$n"
            if (db.txns().byExternalKey(key) != null) { existing++; details += "$label：已匯入過"; return@forEach }
            val millis = r.time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val all = db.txns().all()
            if (r.isTopUp) {
                val window = if (r.dateOnly) 36L * 3600 * 1000 else 15L * 60 * 1000
                val exists = all.any {
                    it.type == TxnType.TRANSFER && it.toAccountId == wallet.id && it.amount == r.amountMinor && kotlin.math.abs(it.date - millis) <= window
                }
                if (exists) { existing++; details += "$label：儲值已由銀行通知記成轉帳"; return@forEach }
                details += "$label：${if (dryRun) "會新增" else "新增"}儲值（${lookupName(wallet.linkedAccountId)} → ${wallet.name}）"
                if (dryRun) { created++; return@forEach }
                if (db.txns().insert(
                        TxnEntity(
                            type = TxnType.TRANSFER, amount = r.amountMinor, accountId = wallet.linkedAccountId, toAccountId = wallet.id,
                            merchant = "儲值", date = millis, source = TxnSource.SCREEN,
                            status = if (wallet.linkedAccountId == null) TxnStatus.PENDING else TxnStatus.CONFIRMED, externalKey = key,
                        ),
                    ) > 0
                ) created++
                return@forEach
            }
            val type = if (r.isIncome) TxnType.INCOME else TxnType.EXPENSE
            // 已經記過（刷卡通知、錢包通知或手動記的）：不重複記，把畫面上的商家名稱、分類補到那一筆
            findSameTxn(all, type, r.amountMinor, millis, r.dateOnly, wallet.id, claimed)?.let { t ->
                merged++; claimed += t.id
                val better = !r.merchant.isNullOrBlank() && weakMerchant(t.merchant)
                details += "$label：和已記的「${t.merchant ?: "未命名"}」是同一筆" + if (better) "，${if (dryRun) "會" else "已"}補上商家名稱" else "，不重複記帳"
                if (!dryRun && (better || t.categoryId == null)) {
                    val name = if (better) r.merchant else t.merchant
                    val cat = t.categoryId ?: suggestCategory(name ?: r.typeLabel, type, t.date, t.amount, t.accountId)?.categoryId
                    db.txns().upsert(t.copy(merchant = name, categoryId = cat, note = t.note ?: r.typeLabel))
                }
                return@forEach
            }
            details += "$label：${if (dryRun) "會新增" else "新增"}"
            if (dryRun) { created++; return@forEach }
            var cat = suggestCategory(r.merchant ?: r.typeLabel, type, millis, r.amountMinor, wallet.id)?.categoryId
            // 規則判斷不出分類時請 AI 幫忙（每次讀取最多 5 筆，避免一次用掉太多額度）
            val rm = r.merchant
            if (cat == null && rm != null && aiCalls < 5) { aiCalls++; cat = aiNameAndCategory(rm, type, millis)?.second }
            val id = db.txns().insert(
                TxnEntity(
                    type = type, amount = r.amountMinor, currency = wallet.currency, accountId = wallet.id,
                    categoryId = cat, merchant = r.merchant, note = r.typeLabel, date = millis, source = TxnSource.SCREEN,
                    status = if (s.notifAutoConfirm) TxnStatus.CONFIRMED else TxnStatus.PENDING, externalKey = key,
                ),
            )
            if (id > 0) created++
        }
        return ScreenIngestResult(wallet.name, records.size, records.size - fresh.size, existing, created, since, merged, details)
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
    /** 一個月的月退：交易筆數、實收手續費、估算退佣、退款日，以及已入帳的那筆（若有）。 */
    data class RebateMonth(
        val month: java.time.YearMonth, val trades: Int, val paidFee: Double, val rebate: Double, val payDate: LocalDate, val booked: TxnEntity?,
    )

    private fun rebateOf(list: List<com.enwei.moneymanager.data.StockTradeEntity>, rule: RebateRule, s: AppSettings) = list.sumOf { t ->
        val min = if (StockCalc.isOddLot(t.shares)) s.oddLotMinFee else s.minFee
        StockCalc.rebate(t.fee, t.shares * t.price, rule.discount, min)
    }

    private val rebateWords = listOf("退佣", "手續費退", "退手續費", "手續費折讓", "折讓")

    /** 證券帳戶各月的月退狀況（新到舊）：從起算月到本月，沒有交易的月份略過。 */
    suspend fun rebateMonths(brokerId: Long): List<RebateMonth> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { rebateMonthsImpl(brokerId) }

    private suspend fun rebateMonthsImpl(brokerId: Long): List<RebateMonth> {
        val s = settings.value
        val rule = s.brokerRebates[brokerId] ?: return emptyList()
        val since = java.time.YearMonth.from(LocalDate.ofEpochDay(rule.sinceEpochDay))
        val txns = db.txns().all()
        return db.stocks().allTrades()
            .filter { it.brokerAccountId == brokerId && it.market == Market.TW && (it.side == StockSide.BUY || it.side == StockSide.SELL) }
            .groupBy { java.time.YearMonth.from(LocalDate.ofEpochDay(it.date)) }
            .filterKeys { it >= since }
            .map { (ym, list) ->
                RebateMonth(
                    ym, list.size, list.sumOf { it.fee }, rebateOf(list, rule, s), ym.plusMonths(1).atDay(rule.payDay.coerceIn(1, 28)),
                    txns.firstOrNull { it.externalKey == "REBATE:$brokerId:$ym" },
                )
            }.sortedByDescending { it.month }
    }

    /** 設定證券帳戶的手續費方式：成交即折（[atTrade]）或月退（[rebate]），兩者擇一。 */
    suspend fun setBrokerFees(brokerId: Long, atTrade: Double?, rebate: RebateRule?) {
        db.accounts().get(brokerId)?.let { db.accounts().upsert(it.copy(feeDiscount = if (rebate != null) null else atTrade)) }
        settings.update { st -> st.copy(brokerRebates = if (rebate == null) st.brokerRebates - brokerId else st.brokerRebates + (brokerId to rebate)) }
        runFeeRebates()
    }

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
                    // 銀行的退佣入帳通知已經記過：不再估算一筆
                    val cash = broker.linkedAccountId ?: broker.id
                    val payMs = payDate.toMillis(LocalTime.NOON)
                    if (db.txns().all().any { t ->
                            t.type == TxnType.INCOME && t.accountId == cash && kotlin.math.abs(t.date - payMs) <= 12L * 86_400_000 &&
                                t.externalKey?.startsWith("REBATE:") != true &&
                                rebateWords.any { w -> t.merchant?.contains(w) == true || t.rawText?.contains(w) == true }
                        }
                    ) return@forEach
                    val amount = rebateOf(list, rule, s)
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
            it.type == AccountType.CREDIT_CARD && it.autopayFromId != null && !it.archived
        }.forEach { card ->
            // 繳款日為固定日或「結帳日＋N 天」都適用
            val cycle = settings.value.cycleFor(card) ?: return@forEach
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
    suspend fun cardStatus(today: LocalDate = LocalDate.now()): Map<Long, CardStatus> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { cardStatusImpl(today) }

    private suspend fun cardStatusImpl(today: LocalDate): Map<Long, CardStatus> {
        val fx = currentFx()
        val txns = db.txns().all().filter { it.status == TxnStatus.CONFIRMED }
        return db.accounts().all().filter { it.type == AccountType.CREDIT_CARD }.associate { card ->
            val owed = maxOf(0, -balanceAsOf(card, txns, Long.MAX_VALUE, fx))
            // 繳款日可以是固定日，也可以是「結帳日＋N 天」（依銀行代碼帶入），兩種都要算
            val cycle = settings.value.cycleFor(card) ?: return@associate card.id to CardStatus(owed, 0, null)
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
    /**
     * 手動標記或取消颱風假（臺北市停止上班，銀行也不營業）。會影響信用卡結帳日、繳款截止日與自動扣繳的順延。
     */
    fun setTyphoonDay(day: LocalDate, on: Boolean) {
        settings.update { st ->
            val map = TyphoonDays.decode(st.typhoonDays).toMutableMap()
            if (on) map[day] = "颱風假"
            else map.remove(day)
            com.enwei.moneymanager.core.TaiwanHolidays.setExtra(map)
            st.copy(typhoonDays = TyphoonDays.encode(map))
        }
    }

    // ---------- 借貸 ----------

    /** 一筆借貸的現況：[outstanding] 為尚未還清的金額，[repaid] 為已還（以本金計）。 */
    data class LoanStatus(val account: AccountEntity, val info: LoanInfo, val balance: Long, val today: LocalDate = LocalDate.now()) {
        val outstanding: Long get() = kotlin.math.abs(balance)
        val repaid: Long get() = (info.principal - outstanding).coerceAtLeast(0)
        val progress: Float get() = if (info.principal > 0) (repaid.toFloat() / info.principal).coerceIn(0f, 1f) else 0f
        val dueDate: LocalDate? get() = info.dueDay?.let(LocalDate::ofEpochDay)
        val overdue: Boolean get() = outstanding > 0 && dueDate?.isBefore(today) == true
        /** 下一次每月還款日 */
        val nextPay: LocalDate? get() = info.payDay?.takeIf { outstanding > 0 }?.let { d ->
            val m = java.time.YearMonth.from(today)
            val thisMonth = m.atDay(minOf(d, m.lengthOfMonth()))
            if (!thisMonth.isBefore(today)) thisMonth else m.plusMonths(1).let { n -> n.atDay(minOf(d, n.lengthOfMonth())) }
        }
        /** 依年利率估算的每月利息 */
        val monthlyInterest: Long get() = Math.round(outstanding * info.annualRate / 100.0 / 12.0)
        /** 照每月還款金額，還要幾期還清 */
        val periodsLeft: Int? get() = info.installment?.takeIf { it > 0 }?.let { ((outstanding + it - 1) / it).toInt() }
    }

    suspend fun loanStatuses(today: LocalDate = LocalDate.now()): List<LoanStatus> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { loanStatusesImpl(today) }

    private suspend fun loanStatusesImpl(today: LocalDate): List<LoanStatus> {
        val loans = settings.value.loans
        if (loans.isEmpty()) return emptyList()
        return balances.first().filter { it.account.type == AccountType.LOAN && !it.account.archived }
            .mapNotNull { b -> loans[b.account.id]?.let { LoanStatus(b.account, it, b.balance, today) } }
    }

    /** 儲存借貸細節；設定每月還款時建立（或更新）週期轉帳，到期先放「待確認」。 */
    suspend fun saveLoan(accountId: Long, info: LoanInfo) {
        val acc = db.accounts().get(accountId) ?: return
        var rid = info.recurringId
        val inst = info.installment?.takeIf { it > 0 }
        if (inst != null && info.payDay != null && info.payAccountId != null) {
            val old = rid?.let { db.recurring().get(it) }
            val r = RecurringEntity(
                id = old?.id ?: 0, name = "${info.counterparty.ifBlank { acc.name }} 每月${if (info.lent) "收款" else "還款"}",
                type = TxnType.TRANSFER, amount = inst, currency = acc.currency,
                // 借出：從借貸帳戶轉回收款帳戶；借入：從還款帳戶轉到借貸帳戶
                accountId = if (info.lent) accountId else info.payAccountId,
                toAccountId = if (info.lent) info.payAccountId else accountId,
                frequency = com.enwei.moneymanager.core.Frequency.MONTHLY, dayOfMonth = info.payDay,
                anchorDate = old?.anchorDate ?: LocalDate.now().toEpochDay(), lastRunDate = old?.lastRunDate,
                endDate = info.dueDay, autoConfirm = false,
            )
            rid = saveRecurring(r)
        } else if (rid != null) {
            db.recurring().delete(rid)
            rid = null
        }
        settings.update { it.copy(loans = it.loans + (accountId to info.copy(recurringId = rid))) }
    }

    suspend fun removeLoan(accountId: Long) {
        settings.value.loans[accountId]?.recurringId?.let { db.recurring().delete(it) }
        settings.update { it.copy(loans = it.loans - accountId) }
    }

    /**
     * 記一筆借貸往來：[repay] 為還款／收回（欠款減少），否則為再借出／再借入（欠款增加）。
     * [interest] 另外記成利息收入（借出）或利息支出（借入）。
     */
    suspend fun recordLoanMove(loanId: Long, otherId: Long, amount: Long, interest: Long, date: LocalDate, repay: Boolean) {
        val acc = db.accounts().get(loanId) ?: return
        val info = settings.value.loans[loanId] ?: LoanInfo()
        val who = info.counterparty.ifBlank { acc.name }
        val millis = date.toMillis(LocalTime.now())
        // 借出時錢流向借貸帳戶、收回時流回；借入相反
        val toLoan = if (info.lent) !repay else repay
        if (amount > 0) db.txns().insert(
            TxnEntity(
                type = TxnType.TRANSFER, amount = amount, currency = acc.currency,
                accountId = if (toLoan) otherId else loanId, toAccountId = if (toLoan) loanId else otherId,
                merchant = "$who ${if (repay) (if (info.lent) "還款" else "我還款") else (if (info.lent) "借出" else "借入")}",
                date = millis, source = TxnSource.MANUAL, status = TxnStatus.CONFIRMED,
            ),
        )
        if (interest > 0) {
            val kind = if (info.lent) TxnType.INCOME else TxnType.EXPENSE
            val cat = db.categories().all().firstOrNull { it.kind == kind && !it.archived && it.name.contains("利息") }?.id
            db.txns().insert(
                TxnEntity(
                    type = kind, amount = interest, currency = acc.currency, accountId = otherId, categoryId = cat,
                    merchant = "$who 利息", date = millis, source = TxnSource.MANUAL, status = TxnStatus.CONFIRMED,
                ),
            )
        }
    }

    /** 借貸提醒：[days] 天內的每月還款、14 天內到期與已逾期。 */
    suspend fun loanReminders(today: LocalDate = LocalDate.now(), days: Long = 3): List<String> = loanStatuses(today).flatMap { l ->
        val who = l.info.counterparty.ifBlank { l.account.name }
        val verb = if (l.info.lent) "收款" else "還款"
        listOfNotNull(
            l.nextPay?.takeIf { l.info.installment != null && !it.isAfter(today.plusDays(days)) }?.let {
                "${it.monthValue}/${it.dayOfMonth} 每月$verb：$who ${Money.withSymbol(minOf(l.info.installment ?: 0, l.outstanding), l.account.currency)}"
            },
            if (l.overdue) "$who 已逾期（到期 ${l.dueDate}），尚欠 ${Money.withSymbol(l.outstanding, l.account.currency)}"
            else l.dueDate?.takeIf { l.outstanding > 0 && !it.isAfter(today.plusDays(14)) }?.let {
                "$who ${it.monthValue}/${it.dayOfMonth} 到期，尚欠 ${Money.withSymbol(l.outstanding, l.account.currency)}"
            },
        )
    }

    suspend fun fundingWarnings(today: LocalDate = LocalDate.now(), days: Long = 45): List<FundingWarning> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { fundingWarningsImpl(today, days) }

    private suspend fun fundingWarningsImpl(today: LocalDate, days: Long): List<FundingWarning> {
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
        return db.accounts().all().filter { it.type == AccountType.CREDIT_CARD && !it.archived }
            .mapNotNull { card ->
                val cycle = settings.value.cycleFor(card) ?: return@mapNotNull null
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
                // 除權息超過 60 天（股利早已入帳）：已記的不再因資料來源事後修改而變動，只補記新買進批次漏算的
                val frozen = ev.exEpochDay < today.toEpochDay() - 60
                val prefix = "$sym:${LocalDate.ofEpochDay(ev.exEpochDay)}:"
                val autoExisting = all.filter { k -> k.externalKey?.let { it.startsWith("DIV:$prefix") || it.startsWith("SDIV:$prefix") } == true }
                if (manual) {
                    if (frozen) return@forEach
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
                        } else if (!frozen && old.cashAmount != cashAmt || old.shares != shares || old.cashAccountId != lot.cashAccountId ||
                            old.brokerAccountId != lot.brokerAccountId || old.note != note || old.name != lot.name
                        ) {
                            db.stocks().upsertTrade(old.copy(cashAmount = cashAmt, shares = shares, cashAccountId = lot.cashAccountId, brokerAccountId = lot.brokerAccountId, note = note, name = lot.name))
                            changed++
                        }
                    }
                }
                if (!frozen) autoExisting.filter { it.externalKey !in keep }.forEach { db.stocks().deleteTrade(it.id); changed++ }
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
