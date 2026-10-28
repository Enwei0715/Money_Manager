package com.enwei.moneymanager.data

import androidx.room.withTransaction
import com.enwei.moneymanager.core.CardCycle
import com.enwei.moneymanager.core.CategoryGuesser
import com.enwei.moneymanager.core.FxConverter
import com.enwei.moneymanager.core.ParsedKind
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.core.NotificationParser
import com.enwei.moneymanager.core.ParsedType
import com.enwei.moneymanager.core.Recurrence
import com.enwei.moneymanager.core.StockCalc
import com.enwei.moneymanager.core.StockSide
import com.enwei.moneymanager.core.Market
import com.enwei.moneymanager.core.TradeRecord
import kotlinx.coroutines.flow.Flow
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
    val realizedPnl: Double,
    val dividends: Double,
) {
    val marketValue: Double get() = shares * (price ?: avgCost)
    val unrealized: Double get() = if (price == null) 0.0 else marketValue - cost
    val unrealizedPct: Double get() = if (cost > 0) unrealized / cost * 100 else 0.0
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
)

data class CardStatus(val payable: Long, val outstanding: Long, val dueDate: LocalDate?)

private const val TRANSFER_TAG = "#轉帳"

data class IngestResult(val kind: String, val txnId: Long? = null, val message: String)

class Repository(private val db: AppDatabase, val settings: SettingsStore) {
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

    val balances: Flow<List<AccountBalance>> = combine(accounts, transactions, trades) { accs, txns, trades ->
        computeBalances(accs, txns, trades)
    }

    private fun computeBalances(
        accs: List<AccountEntity>, txns: List<TxnEntity>, trades: List<StockTradeEntity>,
    ): List<AccountBalance> {
        val delta = HashMap<Long, Long>()
        txns.filter { it.status == TxnStatus.CONFIRMED }.forEach { t ->
            when (t.type) {
                TxnType.EXPENSE -> t.accountId?.let { delta.merge(it, -t.amount, ::sumLong) }
                TxnType.INCOME -> t.accountId?.let { delta.merge(it, t.amount, ::sumLong) }
                TxnType.TRANSFER -> {
                    t.accountId?.let { delta.merge(it, -t.amount, ::sumLong) }
                    t.toAccountId?.let { delta.merge(it, t.toAmount ?: t.amount, ::sumLong) }
                }
            }
        }
        trades.forEach { tr ->
            val acc = tr.cashAccountId ?: return@forEach
            delta.merge(acc, Money.toMinor(StockCalc.cashFlow(tr.toRecord())), ::sumLong)
        }
        return accs.map { AccountBalance(it, it.initialBalance + (delta[it.id] ?: 0)) }
    }

    val holdings: Flow<List<HoldingView>> = combine(trades, quotes) { trades, quotes ->
        val quoteMap = quotes.associateBy { it.key }
        trades.groupBy { it.market to it.symbol.uppercase() }.mapNotNull { (key, list) ->
            val pos = StockCalc.positions(list.map { it.toRecord().copy(symbol = key.second) })[key.second] ?: return@mapNotNull null
            val q = quoteMap[quoteKey(key.first, key.second)]
            HoldingView(
                market = key.first,
                symbol = key.second,
                name = list.firstNotNullOfOrNull { it.name } ?: q?.name,
                currency = list.first().currency,
                shares = pos.shares,
                avgCost = pos.avgCost,
                cost = pos.cost,
                price = q?.price,
                previousClose = q?.previousClose,
                realizedPnl = pos.realizedPnl,
                dividends = pos.dividends,
            )
        }.sortedByDescending { it.marketValue }
    }

    // ---------- 交易 ----------

    suspend fun getTxn(id: Long) = db.txns().get(id)

    suspend fun saveTxn(t: TxnEntity): Long {
        val r = db.txns().upsert(t)
        if (t.status == TxnStatus.CONFIRMED) learnMerchant(t)
        return if (t.id == 0L) r else t.id
    }

    suspend fun deleteTxn(id: Long) = db.txns().delete(id)

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

    private suspend fun guessCategory(merchant: String?, type: TxnType): Long? {
        val cats = db.categories().all().filter { it.kind == type && !it.archived }
        val ruleMap = db.rules().all().mapNotNull { r -> cats.find { it.id == r.categoryId }?.let { r.keyword to it.name } }.toMap()
        val name = CategoryGuesser.guess(merchant, ruleMap) ?: return null
        return cats.find { it.name == name }?.id
    }

    // ---------- 帳戶 / 分類 ----------

    suspend fun saveAccount(a: AccountEntity): Long {
        val r = db.accounts().upsert(a)
        return if (a.id == 0L) r else a.id
    }

    suspend fun deleteAccount(a: AccountEntity) {
        db.withTransaction {
            db.txns().detachAccount(a.id)
            db.accounts().delete(a)
        }
    }

    suspend fun saveCategory(c: CategoryEntity) = db.categories().upsert(c)

    suspend fun ensureSeedData() {
        if (db.categories().count() > 0) return
        val expense = listOf(
            "餐飲" to "🍜", "便利商店" to "🏪", "日用品" to "🧻", "交通" to "🚇", "購物" to "🛍️", "娛樂" to "🎮",
            "居家" to "🏠", "電信網路" to "📶", "醫療" to "💊", "保險" to "🛡️", "教育" to "📚", "社交" to "🎁",
            "美容" to "💇", "寵物" to "🐶", "旅遊" to "✈️", "稅費" to "🧾", "其他" to "📦",
        )
        val income = listOf("薪資" to "💼", "獎金" to "🏆", "投資收益" to "📈", "利息" to "🏦", "退款" to "↩️", "副業" to "🧑‍💻", "其他收入" to "💰")
        db.categories().insertAll(
            expense.mapIndexed { i, (n, ic) -> CategoryEntity(name = n, kind = TxnType.EXPENSE, icon = ic, sortOrder = i) } +
                income.mapIndexed { i, (n, ic) -> CategoryEntity(name = n, kind = TxnType.INCOME, icon = ic, sortOrder = i) },
        )
        if (db.accounts().all().isEmpty()) {
            val cash = db.accounts().upsert(AccountEntity(name = "現金", type = AccountType.CASH, sortOrder = 0))
            db.accounts().upsert(AccountEntity(name = "銀行帳戶", type = AccountType.BANK, sortOrder = 1))
            settings.update { it.copy(defaultAccountId = cash) }
        }
    }

    // ---------- 通知自動記帳 ----------

    suspend fun ingestNotification(pkg: String, title: String?, text: String?, postedAt: Long): IngestResult? {
        val s = settings.value
        val log = db.notificationLog()
        // 同一則通知可能因更新而重複送達
        if (log.countSame(pkg, title, text, postedAt - 10 * 60_000) > 0) return null
        val enabled = pkg in s.notifPackages
        fun logAs(result: String, txnId: Long? = null) =
            NotificationLogEntity(packageName = pkg, title = title, text = text, postedAt = postedAt, result = result, txnId = txnId)

        val fill = NotificationParser.parseStockFill(title, text)
        val parsed = if (fill == null) NotificationParser.parse(title, text) else null
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
            val brokerage = db.accounts().all().firstOrNull { it.type == AccountType.BROKERAGE && !it.archived }
            val id = db.stocks().insertTrade(
                StockTradeEntity(
                    market = market, symbol = fill.symbol, name = fill.name, side = fill.side, shares = fill.shares,
                    price = fill.price, fee = fee, tax = tax, date = postedAt.toLocalDate().toEpochDay(),
                    cashAccountId = brokerage?.id, source = TxnSource.NOTIFICATION, note = "由成交通知自動建立",
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
        val key = p.occurredAt?.let { "N:${p.amountMinor}:${it.withNano(0)}" }
        if (key != null && db.txns().byExternalKey(key) != null) { log.insert(logAs("DUPLICATE")); return null }

        val accounts = db.accounts().all().filter { !it.archived }
        val account = p.cardLast4?.let { l4 -> accounts.firstOrNull { it.cardLast4 == l4 } }
            ?: s.notifAccountMap[pkg]?.let { id -> accounts.firstOrNull { it.id == id } }
            ?: accounts.firstOrNull { it.id == s.defaultAccountId }
        val status = if (s.notifAutoConfirm) TxnStatus.CONFIRMED else TxnStatus.PENDING
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
            db.txns().upsert(updated)
            log.insert(logAs("MERGED", existing.id))
            return IngestResult("RECORDED", existing.id, message)
        }
        fun wallet(): AccountEntity? {
            val wallets = accounts.filter { it.type == AccountType.EWALLET }
            val aliases = p.walletHint?.let { NotificationParser.walletAliases[it] }.orEmpty()
            return wallets.firstOrNull { w -> aliases.any { w.name.contains(it, ignoreCase = true) } }
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
            ParsedKind.NORMAL -> Unit
        }

        val type = if (p.type == ParsedType.INCOME) TxnType.INCOME else TxnType.EXPENSE
        // 多個 App 同時通知同一筆消費（例如信用卡 + 支付 App）
        if (nearby.any { it.type == type && kotlin.math.abs(it.date - occurred) <= 3 * 60_000 }) {
            log.insert(logAs("DUPLICATE", nearby.first().id))
            return null
        }
        val tagged = if (p.kind == ParsedKind.TRANSFER_OUT || p.kind == ParsedKind.TRANSFER_IN) "$TRANSFER_TAG\n$raw" else raw
        val merchant = p.merchant ?: when (p.kind) {
            ParsedKind.TRANSFER_OUT -> "轉出"
            ParsedKind.TRANSFER_IN -> "轉入"
            else -> null
        }
        return insert(
            base.copy(type = type, categoryId = guessCategory(p.merchant, type), merchant = merchant, rawText = tagged),
            "RECORDED", "${type.label} $amountText ${p.merchant.orEmpty()}",
        )
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
        val accounts = db.accounts().all()
        accounts.filter {
            it.type == AccountType.CREDIT_CARD && it.autopayFromId != null && it.statementDay != null && it.dueDay != null && !it.archived
        }.forEach { card ->
            val cycle = CardCycle(card.statementDay!!, card.dueDay!!)
            // 第一次設定時只從今天開始，不回溯
            var cursor = card.autopayLastRun?.let(LocalDate::ofEpochDay) ?: today.minusDays(1)
            while (true) {
                val due = cycle.nextDueDate(cursor)
                if (due.isAfter(today)) break
                val statement = cycle.statementDateFor(due)
                val txns = db.txns().all().filter { it.status == TxnStatus.CONFIRMED }
                val owed = -balanceAsOf(card, txns, statement.plusDays(1).toMillis(LocalTime.MIDNIGHT))
                val paidSince = txns.filter {
                    it.type == TxnType.TRANSFER && it.toAccountId == card.id &&
                        it.date >= statement.plusDays(1).toMillis(LocalTime.MIDNIGHT) &&
                        it.date < due.plusDays(1).toMillis(LocalTime.MIDNIGHT)
                }.sumOf { it.toAmount ?: it.amount }
                val pay = owed - paidSince
                if (pay > 0) {
                    val id = db.txns().insert(
                        TxnEntity(
                            type = TxnType.TRANSFER, amount = pay, currency = card.currency, accountId = card.autopayFromId,
                            toAccountId = card.id, merchant = "${card.name} 自動扣繳", note = "帳單結帳日 $statement",
                            date = due.toMillis(LocalTime.of(8, 0)), source = TxnSource.AUTOPAY,
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

    private fun balanceAsOf(acc: AccountEntity, txns: List<TxnEntity>, beforeMillis: Long): Long {
        var b = acc.initialBalance
        txns.filter { it.date < beforeMillis }.forEach { t ->
            when (t.type) {
                TxnType.EXPENSE -> if (t.accountId == acc.id) b -= t.amount
                TxnType.INCOME -> if (t.accountId == acc.id) b += t.amount
                TxnType.TRANSFER -> {
                    if (t.accountId == acc.id) b -= t.amount
                    if (t.toAccountId == acc.id) b += t.toAmount ?: t.amount
                }
            }
        }
        return b
    }

    /** 信用卡：應繳金額（已出帳未繳）、未出帳金額、下次繳款日。 */
    suspend fun cardStatus(today: LocalDate = LocalDate.now()): Map<Long, CardStatus> {
        val txns = db.txns().all().filter { it.status == TxnStatus.CONFIRMED }
        return db.accounts().all().filter { it.type == AccountType.CREDIT_CARD }.associate { card ->
            val owed = maxOf(0, -balanceAsOf(card, txns, Long.MAX_VALUE))
            if (card.statementDay == null || card.dueDay == null) return@associate card.id to CardStatus(owed, 0, null)
            val cycle = CardCycle(card.statementDay, card.dueDay)
            val thisMonth = Recurrence.dayInMonth(java.time.YearMonth.from(today), card.statementDay)
            val lastStatement = if (thisMonth.isAfter(today)) Recurrence.dayInMonth(java.time.YearMonth.from(today).minusMonths(1), card.statementDay) else thisMonth
            val cutoff = lastStatement.plusDays(1).toMillis(LocalTime.MIDNIGHT)
            val billed = maxOf(0, -balanceAsOf(card, txns, cutoff))
            val paid = txns.filter { it.type == TxnType.TRANSFER && it.toAccountId == card.id && it.date >= cutoff }.sumOf { it.toAmount ?: it.amount }
            val payable = maxOf(0, billed - paid)
            val due = cycle.nextDueDate(lastStatement)
            card.id to CardStatus(payable, maxOf(0, owed - payable), due)
        }
    }

    /** 首頁「即將到來」：信用卡繳款日與預估金額。 */
    suspend fun upcomingCardBills(today: LocalDate = LocalDate.now()): List<Triple<AccountEntity, LocalDate, Long>> {
        val txns = db.txns().all().filter { it.status == TxnStatus.CONFIRMED }
        return db.accounts().all().filter { it.type == AccountType.CREDIT_CARD && it.statementDay != null && it.dueDay != null && !it.archived }
            .map { card ->
                val cycle = CardCycle(card.statementDay!!, card.dueDay!!)
                val due = cycle.nextDueDate(today.minusDays(1))
                val statement = cycle.statementDateFor(due)
                val cutoff = if (statement.isAfter(today)) Long.MAX_VALUE else statement.plusDays(1).toMillis(LocalTime.MIDNIGHT)
                val owed = -balanceAsOf(card, txns, cutoff)
                Triple(card, due, maxOf(0, owed))
            }.sortedBy { it.second }
    }

    // ---------- 股票 ----------

    suspend fun saveTrade(t: StockTradeEntity): Long {
        val r = db.stocks().upsertTrade(t)
        return if (t.id == 0L) r else t.id
    }

    suspend fun getTrade(id: Long) = db.stocks().getTrade(id)
    suspend fun deleteTrade(id: Long) = db.stocks().deleteTrade(id)
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

fun StockTradeEntity.toRecord() = TradeRecord(symbol.uppercase(), side, shares, price, fee, tax, date, cashAmount)

/** 淨資產（基準幣）= 帳戶餘額換算 + 持股市值換算。 */
fun netWorth(balances: List<AccountBalance>, holdings: List<HoldingView>, fx: FxConverter): Long =
    balances.filter { it.account.includeInNetWorth && !it.account.archived }.sumOf { fx.convert(it.balance, it.account.currency) } +
        holdings.sumOf { fx.convert(Money.toMinor(it.marketValue), it.currency) }

private fun sumLong(a: Long, b: Long): Long = a + b
