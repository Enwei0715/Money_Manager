package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.core.Market
import com.enwei.moneymanager.core.StockCalc
import com.enwei.moneymanager.core.StockSide
import com.enwei.moneymanager.data.AccountEntity
import com.enwei.moneymanager.data.AccountType
import com.enwei.moneymanager.data.RebateRule
import com.enwei.moneymanager.data.Repository
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.NumberField
import com.enwei.moneymanager.ui.components.Text
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.floor
import kotlin.math.max

/**
 * 股票手續費與月退：全域預設（折數、最低手續費、美股手續費），
 * 每個證券帳戶的手續費方式（不打折／成交即折／月退）、退款日與各月退佣，以及手續費試算。
 */
@Composable
fun StockFeeScreen(nav: NavController) {
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val lk = rememberLookups()
    var discount by remember { mutableStateOf(settings.feeDiscount.toString()) }
    var minFee by remember { mutableStateOf(settings.minFee.toString()) }
    var oddMin by remember { mutableStateOf(settings.oddLotMinFee.toString()) }
    var usFee by remember { mutableStateOf(settings.usFee.toString()) }
    fun saveFees() = repo.settings.update {
        it.copy(
            feeDiscount = discount.toDoubleOrNull()?.coerceIn(0.01, 1.0) ?: it.feeDiscount,
            minFee = minFee.toDoubleOrNull() ?: it.minFee,
            usFee = usFee.toDoubleOrNull() ?: it.usFee,
            oddLotMinFee = oddMin.toDoubleOrNull() ?: it.oddLotMinFee,
        )
    }
    val brokers = lk.accounts.filter { it.type == AccountType.BROKERAGE && !it.archived }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface, topBar = { BackTopBar("股票手續費與月退", { nav.popBackStack() }) }) { pad ->
        Column(
            Modifier.padding(pad).imePadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("預設（沒有個別設定的證券帳戶使用）", fontSize = 14.sp)
            NumberField(discount, { discount = it; saveFees() }, "台股手續費折數（1 = 不打折，2.8 折填 0.28）")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(minFee, { minFee = it; saveFees() }, "整股最低手續費", Modifier.weight(1f), suffix = "元")
                NumberField(oddMin, { oddMin = it; saveFees() }, "零股最低手續費", Modifier.weight(1f), suffix = "元")
            }
            NumberField(usFee, { usFee = it; saveFees() }, "美股每筆手續費", suffix = "USD")

            HorizontalDivider()
            Text("各證券帳戶", fontSize = 14.sp)
            if (brokers.isEmpty()) Text(
                "還沒有證券帳戶。",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            brokers.forEach { b -> BrokerFeeCard(b, lk, settings.brokerRebates[b.id]) }

            HorizontalDivider()
            FeeCalculator(brokers, settings.brokerRebates)
            Spacer(Modifier.padding(24.dp))
        }
    }
}

/** 一個證券帳戶的手續費方式與月退紀錄。 */
@Composable
private fun BrokerFeeCard(b: AccountEntity, lk: Lookups, rule: RebateRule?) {
    val scope = rememberCoroutineScope()
    val txns by repo.transactions.collectAsStateWithLifecycle(emptyList())
    val trades by repo.trades.collectAsStateWithLifecycle(emptyList())
    // 0 不打折（用預設）、1 成交即折、2 月退
    var mode by remember(b.id, rule, b.feeDiscount) { mutableStateOf(if (rule != null) 2 else if (b.feeDiscount != null) 1 else 0) }
    var disc by remember(b.id, rule, b.feeDiscount) { mutableStateOf((rule?.discount ?: b.feeDiscount)?.toString().orEmpty()) }
    var payDay by remember(b.id, rule) { mutableStateOf((rule?.payDay ?: 15).toString()) }
    var since by remember(b.id, rule) { mutableStateOf(rule?.sinceEpochDay ?: YearMonth.now().atDay(1).toEpochDay()) }
    var msg by remember(b.id) { mutableStateOf<String?>(null) }
    var months by remember(b.id) { mutableStateOf<List<Repository.RebateMonth>>(emptyList()) }
    LaunchedEffect(b.id, rule, txns, trades) { months = repo.rebateMonths(b.id) }
    val mine = trades.filter { it.brokerAccountId == b.id && it.market == Market.TW && (it.side == StockSide.BUY || it.side == StockSide.SELL) }
    val first = mine.minOfOrNull { it.date }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.name, fontSize = 14.sp)
                    Text(
                        "交割帳戶：${lk.account(b.linkedAccountId)?.name ?: "未設定"} · 台股買賣 ${mine.size} 筆",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("用預設", "成交即折", "月退").forEachIndexed { i, label -> FilterChip(mode == i, { mode = i; msg = null }, label = { Text(label) }) }
            }
            when (mode) {
                1 -> NumberField(disc, { disc = it }, "成交時的折數（例如 0.6）")
                2 -> {
                    NumberField(disc, { disc = it }, "月退後的折數（例如 0.28）")
                    NumberField(payDay, { payDay = it.filter(Char::isDigit).take(2) }, "每月退款日（次月）", decimal = false, suffix = "日")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("從 ${LocalDate.ofEpochDay(since).let { "${it.year}/${it.monthValue}" }} 的交易開始計算", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        if (first != null && YearMonth.from(LocalDate.ofEpochDay(first)).atDay(1).toEpochDay() < since) OutlinedButton(onClick = {
                            since = YearMonth.from(LocalDate.ofEpochDay(first)).atDay(1).toEpochDay()
                        }) { Text("從第一筆開始") }
                    }
                }
                else -> Unit
            }
            val d = disc.toDoubleOrNull()?.takeIf { it in 0.01..1.0 }
            Row(verticalAlignment = Alignment.CenterVertically) {
                msg?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f)) } ?: Spacer(Modifier.weight(1f))
                Button(enabled = mode == 0 || d != null, onClick = {
                    scope.launch {
                        when (mode) {
                            0 -> repo.setBrokerFees(b.id, null, null)
                            1 -> repo.setBrokerFees(b.id, d, null)
                            else -> repo.setBrokerFees(b.id, null, RebateRule(d!!, since, (payDay.toIntOrNull() ?: 15).coerceIn(1, 28)))
                        }
                        msg = "已儲存"
                    }
                }) { Text("儲存") }
            }
            if (mode == 2 && rule != null && months.isNotEmpty()) {
                HorizontalDivider()
                Text("各月退佣", fontSize = 14.sp)
                months.take(8).forEach { m ->
                    val booked = m.booked
                    Row {
                        Text("${m.month.year}/${m.month.monthValue}", fontSize = 13.sp, modifier = Modifier.width(72.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${m.trades} 筆 · 實收手續費 ${m.paidFee.toLong()} 元", fontSize = 13.sp)
                            Text(
                                when {
                                    booked != null -> "已入帳 ${com.enwei.moneymanager.core.Money.full(booked.amount)} 元" +
                                        if (booked.rawText != null) "（依銀行通知校正）" else "（估算）"
                                    LocalDate.now().isBefore(m.payDate) -> "預估退 ${m.rebate.toLong()} 元，${m.payDate.monthValue}/${m.payDate.dayOfMonth} 入帳"
                                    else -> "預估退 ${m.rebate.toLong()} 元（已由銀行通知記錄或無須退款）"
                                },
                                fontSize = 13.sp,
                                color = if (booked != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 手續費試算：輸入股數與價格，比較各證券帳戶實付的手續費與交易稅。 */
@Composable
private fun FeeCalculator(brokers: List<AccountEntity>, rebates: Map<Long, RebateRule>) {
    val s by repo.settings.flow.collectAsStateWithLifecycle()
    var shares by remember { mutableStateOf("1000") }
    var price by remember { mutableStateOf("") }
    var etf by remember { mutableStateOf(false) }
    Text("手續費試算", fontSize = 14.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(shares, { shares = it }, "股數", Modifier.weight(1f), decimal = false)
        NumberField(price, { price = it }, "成交價", Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(!etf, { etf = false }, label = { Text("一般股票") })
        FilterChip(etf, { etf = true }, label = { Text("ETF") })
    }
    val n = shares.toDoubleOrNull() ?: 0.0
    val p = price.toDoubleOrNull() ?: 0.0
    val amount = n * p
    if (amount <= 0) return
    val odd = StockCalc.isOddLot(n)
    val min = if (odd) s.oddLotMinFee else s.minFee
    val full = max(min, floor(amount * StockCalc.TW_FEE_RATE))
    val tax = StockCalc.twTax(if (etf) "00" else "", amount)
    fun fee(d: Double) = max(min, floor(amount * StockCalc.TW_FEE_RATE * d))
    Text(
        "成交金額 ${"%,.0f".format(amount)} 元${if (odd) "（零股）" else ""}\n原價手續費 ${full.toLong()} 元 · 賣出交易稅 ${tax.toLong()} 元",
        fontSize = 13.sp,
    )
    val rows = buildList {
        add("預設（${s.feeDiscount} 折）" to "手續費 ${fee(s.feeDiscount).toLong()} 元")
        brokers.forEach { b ->
            val r = rebates[b.id]
            when {
                r != null -> add(b.name to "成交收 ${full.toLong()} 元，次月退 ${(full - fee(r.discount)).toLong()} 元，實付 ${fee(r.discount).toLong()} 元")
                b.feeDiscount != null -> add(b.name to "手續費 ${fee(b.feeDiscount).toLong()} 元（${b.feeDiscount} 折）")
            }
        }
    }
    rows.forEach { (name, v) ->
        Column {
            Text(name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(v, fontSize = 14.sp)
        }
    }
}
