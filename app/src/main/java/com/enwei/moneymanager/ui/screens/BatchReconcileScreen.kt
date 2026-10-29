package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.core.BalanceExtractor
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.data.AccountEntity
import com.enwei.moneymanager.data.AccountType
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.net.GroqAi
import com.enwei.moneymanager.ui.ExpenseColor
import com.enwei.moneymanager.ui.IncomeColor
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 一張截圖讀出的一個帳戶餘額（使用者可以改帳戶與金額、決定要不要對帳）。 */
private class ShotRow(val image: Int, accountId: Long?, amount: String, val label: String, val sure: Boolean) {
    var accountId by mutableStateOf(accountId)
    var amount by mutableStateOf(amount)
    var checked by mutableStateOf(accountId != null)
}

/**
 * 多張截圖一次對帳：選好幾張銀行、錢包、證券 App 的截圖，App 自己判斷每張是哪個帳戶並讀出餘額，
 * 確認（可改帳戶、金額）後一次把差額記成調整。
 */
@Composable
fun BatchReconcileScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val balances by repo.balances.collectAsStateWithLifecycle(emptyList())
    val accounts = balances.map { it.account }.filter { !it.archived && it.type != AccountType.BROKERAGE }
    val rows = remember { mutableStateListOf<ShotRow>() }
    var progress by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf<List<String>>(emptyList()) }
    var done by remember { mutableStateOf<String?>(null) }

    fun describe(a: AccountEntity) = listOfNotNull(a.name, a.type.label, a.cardLast4?.let { "末四碼 $it" }, a.description?.take(30)).joinToString("｜")

    // 沒有 AI 時：畫面文字裡有帳戶名稱或卡號末四碼就對上，餘額用規則挑
    fun guess(lines: List<String>): Pair<Long?, Long?> {
        val text = lines.joinToString(" ")
        val acc = accounts.firstOrNull { a -> a.cardLast4?.let { it.length == 4 && it in text } == true }
            ?: accounts.filter { it.name.length >= 2 && text.contains(it.name) }.maxByOrNull { it.name.length }
        return acc?.id to BalanceExtractor.candidates(lines).firstOrNull()?.amountMinor
    }

    fun plain(minor: Long) = if (minor % 100 == 0L) (minor / 100).toString() else "%.2f".format(minor / 100.0)

    fun read(uris: List<android.net.Uri>) {
        rows.clear(); notes = emptyList(); done = null
        scope.launch {
            val msgs = ArrayList<String>()
            uris.forEachIndexed { i, uri ->
                progress = "讀第 ${i + 1}／${uris.size} 張…"
                val lines = runCatching { com.enwei.moneymanager.ocr.ScreenOcr.read(ctx, uri) }.getOrElse { emptyList() }
                val seen = lines.flatMap { BalanceExtractor.amounts(it) }.toSet()
                val ai = if (GroqAi.enabled) GroqAi.identifyBalances(ctx, uri, accounts.map { it.id to describe(it) }, seen) else null
                if (!ai.isNullOrEmpty()) ai.forEach { r ->
                    rows += ShotRow(i + 1, r.accountId, plain(r.balanceMinor), r.label, r.confidence >= 0.7 && r.accountId != null)
                } else {
                    val (acc, bal) = guess(lines)
                    if (bal != null) rows += ShotRow(i + 1, acc, plain(bal), "", false)
                    else msgs += "第 ${i + 1} 張：找不到餘額"
                }
            }
            notes = msgs
            progress = null
        }
    }

    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(20),
    ) { uris -> if (uris.isNotEmpty()) read(uris) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackTopBar("截圖對帳", { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("一次選好幾張銀行、信用卡、電子支付 App 的餘額截圖，App 會判斷每張是哪個帳戶、讀出餘額；確認後一次對帳，差額記成調整。",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!GroqAi.enabled) Text("沒開 AI 時只能用畫面上的帳戶名稱或卡號末四碼辨認，認不出來的請自己選帳戶。",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    Button(enabled = progress == null, onClick = {
                        picker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (rows.isEmpty()) "選截圖（可多張）" else "重新選截圖") }
                    progress?.let { Text(it, color = MaterialTheme.colorScheme.primary); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    notes.forEach { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
                    done?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            }
            itemsIndexed(rows) { _, r -> ShotCard(r, accounts, balances.associate { it.account.id to it.balance }) }
            if (rows.isNotEmpty() && progress == null) item {
                val ready = rows.filter { it.checked && it.accountId != null && Money.parseMinor(it.amount) != null }
                // 同一個帳戶出現在好幾張圖時只用最後一張
                val byAcc = ready.associateBy { it.accountId!! }
                Button(enabled = byAcc.isNotEmpty(), onClick = {
                    scope.launch {
                        val other = repo.categories.first().firstOrNull { it.kind == TxnType.EXPENSE && it.name == "其他" }?.id
                        var n = 0
                        byAcc.forEach { (id, r) ->
                            val b = balances.find { it.account.id == id } ?: return@forEach
                            val actual = Money.parseMinor(r.amount) ?: return@forEach
                            if (repo.reconcile(b.account, b.balance, actual, if (actual < b.balance) other else null) != null) n++
                        }
                        done = if (n == 0) "全部相符，不需要調整" else "已對帳 ${byAcc.size} 個帳戶，調整了 $n 筆"
                        rows.clear()
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("對帳勾選的 ${byAcc.size} 個帳戶") }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ShotCard(r: ShotRow, accounts: List<AccountEntity>, current: Map<Long, Long>) {
    var menu by remember { mutableStateOf(false) }
    val acc = accounts.find { it.id == r.accountId }
    val actual = Money.parseMinor(r.amount)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(r.checked, { r.checked = it }, enabled = r.accountId != null)
                Column(Modifier.weight(1f)) {
                    Box {
                        Text((acc?.name ?: "選擇帳戶") + " ▾", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            color = if (acc == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clickable { menu = true })
                        DropdownMenu(menu, { menu = false }) {
                            accounts.forEach { a -> DropdownMenuItem(text = { Text(a.name) }, onClick = { r.accountId = a.id; r.checked = true; menu = false }) }
                        }
                    }
                    Text("第 ${r.image} 張" + (if (r.label.isNotBlank()) " · 畫面上是「${r.label}」" else "") + if (!r.sure && acc != null) " · 請確認帳戶" else "",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedTextField(r.amount, { r.amount = it.filter { c -> c.isDigit() || c == '.' || c == '-' } }, label = { Text("截圖上的餘額") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            if (acc != null && actual != null) {
                val cur = current[acc.id] ?: 0
                val diff = actual - cur
                Text(
                    "帳理目前 ${Money.withSymbol(cur, acc.currency)} → " + when {
                        diff == 0L -> "相符"
                        diff < 0 -> "記一筆支出 ${Money.withSymbol(-diff, acc.currency)}"
                        else -> "記一筆收入 ${Money.withSymbol(diff, acc.currency)}"
                    },
                    fontSize = 13.sp, color = if (diff < 0) ExpenseColor else if (diff > 0) IncomeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
