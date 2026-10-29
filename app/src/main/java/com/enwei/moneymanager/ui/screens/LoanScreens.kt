package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (info == null) {
            Text("設定對象、本金與還款方式，開始追蹤借貸進度。", fontSize = 14.sp, color = gray)
            OutlinedButton(onClick = { nav.navigate("account/${acc.id}") }) { Text("設定借貸") }
        } else {
            val l = Repository.LoanStatus(acc, info, balance)
            val verb = if (info.lent) "收回" else "還款"
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(if (info.lent) "借出給" else "向其借入", fontSize = 13.sp, color = gray)
                    Text(info.counterparty.ifBlank { acc.name }, fontSize = 17.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Surface(
                    color = if (l.outstanding == 0L) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(
                        if (l.outstanding == 0L) "已結清" else if (info.lent) "待收回" else "待償還",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = if (l.outstanding == 0L) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 13.sp,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (info.lent) "對方尚欠你" else "你尚欠", fontSize = 13.sp, color = gray)
                Text(Money.withSymbol(l.outstanding, acc.currency), fontSize = 26.sp)
            }
            if (info.principal > 0) {
                Column(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("本金進度", Modifier.weight(1f), fontSize = 13.sp, color = gray)
                        Text("${(l.progress * 100).toInt()}%", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    LinearProgressIndicator(progress = { l.progress }, modifier = Modifier.fillMaxWidth())
                    LoanDetailLine("本金", Money.withSymbol(info.principal, acc.currency))
                    LoanDetailLine("已$verb", Money.withSymbol(l.repaid, acc.currency))
                }
            }
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LoanDetailLine("開始日", LocalDate.ofEpochDay(info.startDay).toString())
                LoanDetailLine(
                    "到期日",
                    l.dueDate?.let { due ->
                        val daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), due)
                        when {
                            l.overdue -> "逾期 ${-daysLeft} 天 · $due"
                            else -> "$due · 剩餘 $daysLeft 天"
                        }
                    } ?: "未設定",
                    alert = l.overdue,
                )
                info.installment?.let { amount ->
                    LoanDetailLine("每月$verb", "${Money.withSymbol(amount, acc.currency)} · ${info.payDay?.let { "每月 $it 日" } ?: "未設定日期"}")
                    l.nextPay?.let { LoanDetailLine("下次收付", "${it.monthValue}/${it.dayOfMonth}") }
                    l.periodsLeft?.let { LoanDetailLine("預估期數", "$it 期") }
                }
                if (info.annualRate > 0) {
                    LoanDetailLine("年利率", "${info.annualRate}%")
                    LoanDetailLine("每月利息估算", Money.withSymbol(l.monthlyInterest, acc.currency))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = l.outstanding > 0,
                    onClick = { dialog = 1 },
                ) { Text(if (info.lent) "收回款項" else "還款") }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { dialog = 2 },
                ) { Text(if (info.lent) "再借出" else "再借入") }
            }
            if (dialog != 0) LoanMoveDialog(acc, l, repay = dialog == 1, lk = lk) { dialog = 0 }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun LoanDetailLine(label: String, value: String, alert: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.width(88.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
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
