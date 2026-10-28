package com.enwei.moneymanager.ui.screens

import com.enwei.moneymanager.core.FxConverter
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.core.StockCalc
import com.enwei.moneymanager.data.StockTradeEntity
import com.enwei.moneymanager.data.TxnEntity
import com.enwei.moneymanager.data.toMillis
import com.enwei.moneymanager.data.toRecord
import java.time.LocalDate
import java.time.LocalTime

/**
 * 帳戶明細的一列：一般交易或股票買賣。
 * 帳戶餘額（Repository.computeBalances）、明細列表、每筆後餘額、期間存入／提出、圖表
 * 全部由同一份 ledger 計算，任何新增、修改、刪除都會同時反映在所有地方。
 */
data class LedgerEntry(val date: Long, val delta: Long, val txn: TxnEntity? = null, val trade: StockTradeEntity? = null) {
    val key: String get() = txn?.let { "t${it.id}" } ?: "s${trade!!.id}"
}

/** 股票交易在帳本上的時間：成交日中午（與餘額計算一致）。 */
fun StockTradeEntity.ledgerMillis(): Long = LocalDate.ofEpochDay(date).toMillis(LocalTime.NOON)

/** 股票交易對交割帳戶的金額影響（換算成帳戶幣別；與 computeBalances 相同算法）。 */
fun tradeCashDelta(tr: StockTradeEntity, accountId: Long, accountCurrency: String, fx: FxConverter): Long =
    if (tr.cashAccountId != accountId) 0
    else fx.convert(Money.toMinor(StockCalc.cashFlow(tr.toRecord())), tr.currency, accountCurrency)

/**
 * 指定帳戶的所有明細，由新到舊。股票交易：交割帳戶顯示金額進出；證券帳戶（交割在別處）也列出但金額影響為 0。
 */
fun ledgerFor(accountId: Long, accountCurrency: String, txns: List<TxnEntity>, trades: List<StockTradeEntity>, fx: FxConverter): List<LedgerEntry> {
    val a = txns.asSequence().filter { it.accountId == accountId || it.toAccountId == accountId }
        .map { LedgerEntry(it.date, deltaFor(it, accountId, fx, accountCurrency), txn = it) }
    val b = trades.asSequence().filter { it.cashAccountId == accountId || it.brokerAccountId == accountId }
        .map { LedgerEntry(it.ledgerMillis(), tradeCashDelta(it, accountId, accountCurrency, fx), trade = it) }
    return (a + b).sortedWith(compareByDescending<LedgerEntry> { it.date }.thenByDescending { it.key }).toList()
}

/** 某時間點（不含）之前的餘額 = 目前餘額 − 該時間點之後的所有變動。 */
fun balanceAt(current: Long, millis: Long, ledger: List<LedgerEntry>): Long =
    current - ledger.filter { it.date >= millis }.sumOf { it.delta }

/** 每一列之後的帳戶餘額（key 為 [LedgerEntry.key]）。 */
fun runningBalances(current: Long, ledger: List<LedgerEntry>): Map<String, Long> {
    var running = current
    val out = HashMap<String, Long>(ledger.size)
    ledger.forEach { e -> out[e.key] = running; running -= e.delta }
    return out
}
