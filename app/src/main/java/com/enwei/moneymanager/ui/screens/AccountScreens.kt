package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.enwei.moneymanager.ui.components.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enwei.moneymanager.core.CardCycle
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.data.AccountEntity
import com.enwei.moneymanager.data.AccountType
import com.enwei.moneymanager.data.shiftFor
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.FormInput
import com.enwei.moneymanager.ui.components.FormLine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 帳戶資訊：群組、名稱、金額、說明；信用卡另有結帳日、繳款日、自動扣繳。 */
@Composable
fun AccountEditScreen(nav: NavController, id: Long, initType: String? = null) {
    val lk = rememberLookups()
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<AccountEntity?>(null) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(initType?.let { t -> AccountType.entries.find { it.name == t } } ?: AccountType.BANK) }
    var currency by remember { mutableStateOf(lk.base) }
    var initial by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("") }
    var last4 by remember { mutableStateOf("") }
    var statementDay by remember { mutableStateOf("") }
    var dueDay by remember { mutableStateOf("") }
    var autopayDay by remember { mutableStateOf("") }
    var autopayFrom by remember { mutableStateOf<Long?>(null) }
    var includeNet by remember { mutableStateOf(true) }
    var archived by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }
    var currencyMenu by remember { mutableStateOf(false) }
    var autopayMenu by remember { mutableStateOf(false) }
    var linkedId by remember { mutableStateOf<Long?>(null) }
    var feeDiscount by remember { mutableStateOf("") }
    var rebate by remember { mutableStateOf("") }
    var shift by remember { mutableStateOf(com.enwei.moneymanager.core.StatementShift.NONE) }
    var bankQuery by remember { mutableStateOf("") }
    var dueOffset by remember { mutableStateOf<Int?>(null) }
    val balances by repo.balances.collectAsStateWithLifecycle(emptyList())
    // 「金額」欄位以目前餘額表示；儲存時換算回期初餘額
    val currentBalance = balances.find { it.account.id == id }?.balance

    LaunchedEffect(id) {
        if (id == 0L) return@LaunchedEffect
        val a = repo.accounts.first().find { it.id == id } ?: return@LaunchedEffect
        original = a
        name = a.name; type = a.type; currency = a.currency
        val cur = repo.balances.first().find { it.account.id == id }?.balance ?: a.initialBalance
        initial = Money.fromMinor(if (a.type == AccountType.CREDIT_CARD) -cur else cur).stripTrailingZeros().toPlainString()
        linkedId = a.linkedAccountId
        feeDiscount = a.feeDiscount?.toString().orEmpty()
        rebate = repo.settings.value.brokerRebates[a.id]?.discount?.toString().orEmpty()
        shift = repo.settings.value.shiftFor(a.id)
        bankQuery = repo.settings.value.cardBank[a.id].orEmpty()
        dueOffset = repo.settings.value.cardDueOffset[a.id]
        description = a.description.orEmpty()
        limit = if (a.creditLimit > 0) Money.fromMinor(a.creditLimit).stripTrailingZeros().toPlainString() else ""
        last4 = a.cardLast4.orEmpty(); statementDay = a.statementDay?.toString().orEmpty(); dueDay = a.dueDay?.toString().orEmpty(); autopayDay = a.autopayDay?.toString().orEmpty()
        autopayFrom = a.autopayFromId; includeNet = a.includeInNetWorth; archived = a.archived
    }
    val isCard = type == AccountType.CREDIT_CARD
    val sDay = statementDay.toIntOrNull()?.takeIf { it in 1..31 }
    val bank = com.enwei.moneymanager.core.TaiwanBanks.find(bankQuery)
    // 依銀行規則時，繳款日由結帳日＋天數推算（存一個近似的日數供舊邏輯顯示）
    val dDay = if (dueOffset != null && sDay != null) ((sDay - 1 + dueOffset!!) % 30) + 1 else dueDay.toIntOrNull()?.takeIf { it in 1..31 }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            BackTopBar("帳戶資訊", { nav.popBackStack() }) {
                if (original != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "刪除") }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(12.dp))
            FormLine("群組", onClick = { typeMenu = true }) {
                Box {
                    Text(type.label, fontSize = 17.sp)
                    DropdownMenu(typeMenu, { typeMenu = false }) {
                        AccountType.entries.forEach { t -> DropdownMenuItem(text = { Text(t.label) }, onClick = { type = t; typeMenu = false }) }
                    }
                }
            }
            FormLine("名稱") { FormInput(name, { name = it }) }
            FormLine(if (isCard) "目前欠款" else if (type == AccountType.BROKERAGE) "證券戶現金" else "目前餘額", trailing = {
                Box {
                    Box(
                        Modifier.size(44.dp).border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)).clickable { currencyMenu = true },
                        contentAlignment = Alignment.Center,
                    ) { Text(Money.symbolOf(currency).trim(), color = MaterialTheme.colorScheme.primary) }
                    DropdownMenu(currencyMenu, { currencyMenu = false }) {
                        currencyOptions(lk).forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { currency = c; currencyMenu = false }) }
                    }
                }
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${Money.symbolOf(currency).trim()} ", fontSize = 17.sp)
                    FormInput(initial, { s -> initial = s.filter { it.isDigit() || it == '.' || it == '-' } }, "0.00", number = true)
                }
            }
            FormLine("說明") { FormInput(description, { description = it }) }
            if (isCard) {
                FormLine("末四碼") { FormInput(last4, { s -> last4 = s.filter(Char::isDigit).take(4) }, "用於辨識刷卡通知", number = true) }
                FormLine("信用額度") { FormInput(limit, { limit = it }, "", number = true) }
                FormLine("發卡銀行") {
                    FormInput(bankQuery, { q ->
                        bankQuery = q
                        // 輸入代碼或名稱即自動帶入該銀行的繳款規則
                        com.enwei.moneymanager.core.TaiwanBanks.find(q)?.let { b ->
                            dueOffset = b.dueOffsetDays
                            b.fixedStatementDay?.let { d -> statementDay = if (d < 0) "31" else d.toString() }
                            if (name.isBlank()) name = b.name.substringBefore("（") + "信用卡"
                        }
                    }, "銀行代碼或名稱")
                }
                if (bank != null) Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp)) {
                    Text(
                        "${bank.code} ${bank.name}：繳款截止日＝結帳日＋${bank.dueOffsetDays} 天" +
                            (bank.note?.let { "\n$it" } ?: "") + if (!bank.verified) "\n（此銀行資料不足，先用常見值，請對照帳單）" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (bank.statementDays.isNotEmpty()) Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        bank.statementDays.forEach { d ->
                            androidx.compose.material3.FilterChip(sDay == d, { statementDay = d.toString() }, label = { Text("$d 日") })
                        }
                    }
                }
                FormLine("結帳日") { FormInput(statementDay, { statementDay = it.filter(Char::isDigit).take(2) }, "月底填 31", number = true) }
                if (dueOffset != null) FormLine("繳款日", trailing = {
                    androidx.compose.material3.TextButton(onClick = { dueOffset = null }) { Text("改用固定日") }
                }) {
                    Text("結帳日＋$dueOffset 天" + (sDay?.let { s0 -> CardCycle(s0, 1, shift, dueOffset).let { c -> "（本期 ${c.nextDueDate(LocalDate.now().minusDays(1))}）" } } ?: ""), fontSize = 17.sp)
                } else FormLine("繳款日") { FormInput(dueDay, { dueDay = it.filter(Char::isDigit).take(2) }, "", number = true) }
                FormLine("扣繳日") { FormInput(autopayDay, { autopayDay = it.filter(Char::isDigit).take(2) }, "銀行實際扣款日，空白＝繳款日", number = true) }
                FormLine("扣繳帳戶", onClick = { autopayMenu = true }) {
                    Box {
                        Text(lk.account(autopayFrom)?.name ?: "不自動扣繳", fontSize = 17.sp)
                        DropdownMenu(autopayMenu, { autopayMenu = false }) {
                            DropdownMenuItem(text = { Text("不自動扣繳") }, onClick = { autopayFrom = null; autopayMenu = false })
                            lk.accounts.filter { it.type != AccountType.CREDIT_CARD && !it.archived }.forEach { a ->
                                DropdownMenuItem(text = { Text(a.name) }, onClick = { autopayFrom = a.id; autopayMenu = false })
                            }
                        }
                    }
                }
                DropdownLine("結帳日遇週末", shift.label, com.enwei.moneymanager.core.StatementShift.entries.map { it to it.label }) { shift = it }
                if (sDay != null && dDay != null) {
                    val cycle = CardCycle(sDay, dDay, shift, dueOffset)
                    val due = cycle.nextDueDate(LocalDate.now().minusDays(1))
                    val apDay = autopayDay.toIntOrNull()?.takeIf { it in 1..31 }
                    Text(
                        "本期：結帳日 ${cycle.statementDateFor(due)} → 繳款日 $due" +
                            if (autopayFrom != null) "\n${cycle.autopayDateFor(due, apDay)} 會自動從「${lk.account(autopayFrom)?.name}」轉帳「結帳日欠款 − 已繳金額」到這張卡；銀行若傳來扣繳通知，會以實際金額校正。" else "\n選擇扣繳帳戶後，到期會自動記一筆轉帳。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            } else if (type == AccountType.BROKERAGE) {
                DropdownLine("交割帳戶", lk.account(linkedId)?.name ?: "未設定",
                    listOf<Pair<Long?, String>>(null to "未設定") + lk.accounts.filter { it.type == AccountType.BANK && !it.archived }.map { it.id to it.name }) { linkedId = it }
                FormLine("手續費折數") { FormInput(feeDiscount, { feeDiscount = it }, "成交當下的折數；月退券商填 1", number = true) }
                FormLine("月退後折數") { FormInput(rebate, { rebate = it }, "空白＝不月退", number = true) }
                Text(
                    "月退：成交時先收全額手續費，次月 15 日退回差額。填了之後，每月 15 日會自動在交割帳戶記一筆「手續費退佣」收入（從現在這個月的交易開始計算）。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                Text(
                    "股票買賣的金額會進出「交割帳戶」；這個證券帳戶的價值 = 持股市值 ＋ 證券戶現金（台股通常為 0，複委託可填外幣餘額）。持股在「帳戶 → 股票」或本帳戶明細新增。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            } else if (type == AccountType.EWALLET) {
                DropdownLine("儲值來源", lk.account(linkedId)?.name ?: "未設定",
                    listOf<Pair<Long?, String>>(null to "未設定") + lk.accounts.filter { it.type != AccountType.EWALLET && !it.archived }.map { it.id to it.name }) { linkedId = it }
                Text(
                    "設定自動儲值的扣款銀行後，該銀行的「電支交易」通知會自動記成轉帳到這個錢包。錢包本身若不推播消費，請在帳戶明細用「對帳」補記。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            } else if (type == AccountType.BANK) {
                FormLine("末四碼") { FormInput(last4, { s -> last4 = s.filter(Char::isDigit).take(4) }, "帳號末四碼，用於辨識通知", number = true) }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(thickness = 10.dp, color = MaterialTheme.colorScheme.background)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("計入總額", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                Switch(includeNet, { includeNet = it }, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = Color.White))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().clickable { archived = !archived }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("顯示／隱藏", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                Icon(if (archived) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (archived) "已隱藏" else "顯示中",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(thickness = 10.dp, color = MaterialTheme.colorScheme.background)
            Button(
                onClick = {
                    val entered = (Money.parseMinor(initial) ?: 0).let { if (isCard) -it else it }
                    val o = original
                    // 新帳戶：輸入值就是期初餘額；既有帳戶：調整期初餘額讓目前餘額等於輸入值
                    val newInitial = if (o == null || currentBalance == null) entered else o.initialBalance + (entered - currentBalance)
                    val a = (o ?: AccountEntity(name = name, type = type, sortOrder = lk.accounts.size)).copy(
                        name = name.trim(), type = type, currency = currency,
                        initialBalance = newInitial,
                        linkedAccountId = if (type == AccountType.BROKERAGE || type == AccountType.EWALLET) linkedId else null,
                        feeDiscount = if (type == AccountType.BROKERAGE) feeDiscount.toDoubleOrNull()?.takeIf { it in 0.01..1.0 } else null,
                        creditLimit = Money.parseMinor(limit) ?: 0,
                        cardLast4 = last4.ifBlank { null },
                        statementDay = if (isCard) sDay else null, dueDay = if (isCard) dDay else null,
                        autopayFromId = if (isCard) autopayFrom else null,
                        autopayDay = if (isCard) autopayDay.toIntOrNull()?.takeIf { it in 1..31 } else null,
                        includeInNetWorth = includeNet, archived = archived, description = description.ifBlank { null },
                    )
                    scope.launch {
                        val savedId = repo.saveAccount(a)
                        val r = rebate.toDoubleOrNull()?.takeIf { it > 0 && it < 1 && type == AccountType.BROKERAGE }
                        repo.settings.update { st ->
                            st.copy(
                                cardShift = if (type == AccountType.CREDIT_CARD && shift != com.enwei.moneymanager.core.StatementShift.NONE)
                                    st.cardShift + (savedId to shift.name) else st.cardShift - savedId,
                                cardBank = if (type == AccountType.CREDIT_CARD && bank != null) st.cardBank + (savedId to bank.code) else st.cardBank - savedId,
                                cardDueOffset = if (type == AccountType.CREDIT_CARD && dueOffset != null) st.cardDueOffset + (savedId to dueOffset!!)
                                else st.cardDueOffset - savedId,
                            )
                        }
                        repo.settings.update { st ->
                            val old = st.brokerRebates[savedId]
                            st.copy(
                                brokerRebates = if (r == null) st.brokerRebates - savedId
                                else st.brokerRebates + (savedId to com.enwei.moneymanager.data.RebateRule(
                                    r, old?.sinceEpochDay ?: java.time.YearMonth.now().atDay(1).toEpochDay(),
                                )),
                            )
                        }
                        repo.runFeeRebates()
                        nav.popBackStack()
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text("儲存", fontSize = 17.sp) }
            if (original != null && name.isNotBlank()) Text(
                "修改「目前餘額」會自動調整期初餘額，不會新增或改動任何交易。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp).background(MaterialTheme.colorScheme.surface),
            )
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("刪除帳戶？") },
        text = { Text("相關交易會保留但不再屬於任何帳戶。若只是不再使用，建議改用「顯示／隱藏」。") },
        confirmButton = { TextButton(onClick = { scope.launch { repo.deleteAccount(original!!); nav.popBackStack() } }) { Text("刪除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
    )
}
