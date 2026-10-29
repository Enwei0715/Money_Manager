package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.data.AccountEntity
import com.enwei.moneymanager.data.AccountType
import com.enwei.moneymanager.data.Repository
import com.enwei.moneymanager.ui.components.DateField
import com.enwei.moneymanager.ui.components.DropdownField
import com.enwei.moneymanager.ui.components.NumberField
import com.enwei.moneymanager.ui.components.Text
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 借貸帳戶明細上方：還款進度、下次收付款、到期日、利息估算，以及收回／還款、再借出／借入。 */
@Composable
fun LoanCard(acc: AccountEntity, balance: Long, lk: Lookups, nav: NavController) {
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val info = settings.loans[acc.id]
    // 0 不顯示、1 收回／還款、2 再借出／再借入
    var dialog by remember { mutableStateOf(0) }
    val gray = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (info == null) {
            Text("還沒有設定借貸細節（對象、本金、到期日、每月還款）。", fontSize = 14.sp, color = gray)
            OutlinedButton(onClick = { nav.navigate("account/${acc.id}") }) { Text("設定借貸") }
        } else {
            val l = Repository.LoanStatus(acc, info, balance)
            val verb = if (info.lent) "收回" else "還款"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        (info.counterparty.ifBlank { acc.name }) + if (info.lent) " 欠你" else "：你欠",
                        fontSize = 14.sp, color = gray,
                    )
                    Text(Money.withSymbol(l.outstanding, acc.currency), fontSize = 22.sp)
                }
                if (l.outstanding == 0L) Text("已結清", color = MaterialTheme.colorScheme.primary)
            }
            if (info.principal > 0) {
                LinearProgressIndicator(progress = { l.progress }, modifier = Modifier.fillMaxWidth())
                Text(
                    "本金 ${Money.withSymbol(info.principal, acc.currency)} · 已$verb ${Money.withSymbol(l.repaid, acc.currency)}（${(l.progress * 100).toInt()}%）",
                    fontSize = 13.sp, color = gray,
                )
            }
            val lines = listOfNotNull(
                "${LocalDate.ofEpochDay(info.startDay)} 開始",
                l.dueDate?.let { d ->
                    val left = ChronoUnit.DAYS.between(LocalDate.now(), d)
                    if (l.overdue) "已逾期 ${-left} 天（到期 $d）" else "$d 到期（剩 $left 天）"
                },
                info.installment?.let { inst ->
                    "每月 ${info.payDay ?: "?"} 日$verb ${Money.withSymbol(inst, acc.currency)}" +
                        (l.periodsLeft?.let { "，約 $it 期還清" } ?: "") + (l.nextPay?.let { "，下次 ${it.monthValue}/${it.dayOfMonth}" } ?: "")
                },
                if (info.annualRate > 0) "年利率 ${info.annualRate}% · 每月利息約 ${Money.withSymbol(l.monthlyInterest, acc.currency)}" else null,
            )
            lines.forEach { Text(it, fontSize = 13.sp, color = if (it.startsWith("已逾期")) MaterialTheme.colorScheme.error else gray) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = l.outstanding > 0, onClick = { dialog = 1 }) { Text(if (info.lent) "收回款項" else "還款") }
                TextButton(onClick = { dialog = 2 }) { Text(if (info.lent) "再借出" else "再借入") }
            }
            if (dialog != 0) LoanMoveDialog(acc, l, repay = dialog == 1, lk = lk) { dialog = 0 }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun LoanMoveDialog(acc: AccountEntity, l: Repository.LoanStatus, repay: Boolean, lk: Lookups, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val info = l.info
    val accounts = lk.accounts.filter { !it.archived && it.type != AccountType.LOAN && it.type != AccountType.BROKERAGE }
    var other by remember { mutableStateOf(info.payAccountId?.let { lk.account(it) } ?: accounts.firstOrNull()) }
    val suggested = if (repay) minOf(info.installment ?: l.outstanding, l.outstanding) else 0L
    var amount by remember { mutableStateOf(if (suggested > 0) Money.fromMinor(suggested).stripTrailingZeros().toPlainString() else "") }
    var interest by remember { mutableStateOf(if (repay && l.monthlyInterest > 0) Money.fromMinor(l.monthlyInterest).stripTrailingZeros().toPlainString() else "") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    val title = if (repay) (if (info.lent) "收回款項" else "還款") else (if (info.lent) "再借出" else "再借入")
    val amt = Money.parseMinor(amount)?.let { kotlin.math.abs(it) } ?: 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(amount, { amount = it }, "金額")
                if (repay && info.annualRate > 0) NumberField(interest, { interest = it }, "其中利息（另記為利息${if (info.lent) "收入" else "支出"}）")
                DropdownField(if (info.lent == repay) "收到哪個帳戶" else "從哪個帳戶付", accounts, other, { it.name }, { other = it })
                DateField(date, { date = it })
                if (repay && amt > l.outstanding) Text("超過尚欠金額 ${Money.withSymbol(l.outstanding, acc.currency)}", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = amt > 0 && other != null, onClick = {
                val o = other ?: return@TextButton
                scope.launch {
                    repo.recordLoanMove(acc.id, o.id, amt, Money.parseMinor(interest)?.let { kotlin.math.abs(it) } ?: 0L, date, repay)
                    onDismiss()
                }
            }) { Text("記下") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
