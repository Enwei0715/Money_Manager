package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.data.AccountType
import com.enwei.moneymanager.data.MacroShare
import com.enwei.moneymanager.data.TxnMacro
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.ui.AppText
import com.enwei.moneymanager.ui.components.AppCard
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.FormInput
import com.enwei.moneymanager.ui.components.FormLine
import com.enwei.moneymanager.ui.components.OptionDialog
import com.enwei.moneymanager.ui.components.Text
import kotlinx.coroutines.launch

/** 巨集裡的金額文字：整數不帶小數 */
private fun macroAmt(minor: Long): String = if (minor % 100 == 0L) (minor / 100).toString() else "%.2f".format(minor / 100.0)
private fun parseMinor(s: String): Long = s.replace(",", "").trim().toDoubleOrNull()?.takeIf { it > 0 }?.let { Money.toMinor(it) } ?: 0L

/** 巨集清單：每個巨集一張卡片，按「執行」立刻記下（可復原），點卡片修改。 */
@Composable
fun MacroListScreen(nav: NavController) {
    com.enwei.moneymanager.ui.components.PageTour("macros", listOf(
        com.enwei.moneymanager.ui.components.Coach.Step(null, "巨集", "把每次都一樣的一組帳存起來，例如房租：付給房東 25,500，室友各欠你 7,500、9,500。之後按「執行」就一次記好，自己只負擔剩下的部分。"),
        com.enwei.moneymanager.ui.components.Coach.Step(null, "通知自動套用", "在巨集裡開啟「通知自動套用」：收到金額相同的付款通知時，直接照巨集記帳；可加關鍵字（例如房東戶名）避免同金額的其他付款被套用。"),
    ))
    val lk = rememberLookups()
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val base = settings.baseCurrency
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BackTopBar("巨集", { nav.popBackStack() }) {
            IconButton(onClick = { nav.navigate("macro/0") }) { Icon(Icons.Filled.Add, "新增巨集") }
        }
        if (settings.macros.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("還沒有巨集", fontSize = AppText.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { nav.navigate("macro/0") }) { Text("新增巨集") }
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(settings.macros, key = { it.id }) { m ->
                val transfer = m.type == "TRANSFER"
                val own = m.amount - m.shares.sumOf { it.amount }
                AppCard(Modifier.fillMaxWidth().clickable { nav.navigate("macro/${m.id}") }) {
                    Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name, fontSize = AppText.body, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(Money.withSymbol(m.amount, base), fontSize = AppText.title, fontWeight = FontWeight.SemiBold,
                                    color = if (transfer) MaterialTheme.colorScheme.onSurface else com.enwei.moneymanager.ui.ExpenseColor)
                            }
                            FilledTonalButton(onClick = {
                                scope.launch {
                                    val id = repo.runMacro(m)
                                    com.enwei.moneymanager.ui.components.Undo.show(com.enwei.moneymanager.ui.tr("已記下「${m.name}」")) { repo.deleteTxn(id) }
                                }
                            }) { Text("執行") }
                        }
                        // 記成什麼：類型・分類或轉入帳戶・從哪個帳戶
                        Text(listOfNotNull(
                            if (transfer) "轉帳" else lk.catLabel(m.categoryId) ?: "支出",
                            m.merchant.takeIf { it.isNotBlank() },
                            lk.account(m.accountId)?.let { if (transfer) "${it.name} → ${lk.account(m.toAccountId)?.name ?: "未選"}" else it.name },
                        ).joinToString("・"), fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!transfer && m.shares.isNotEmpty()) Text(
                            m.shares.joinToString("・") { s -> "${lk.account(s.personId)?.name ?: "?"} 欠你 ${Money.withSymbol(s.amount, base)}" } +
                                "・自己負擔 ${Money.withSymbol(own.coerceAtLeast(0), base)}",
                            fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (m.fromNotif) Text(
                            "通知 ${Money.withSymbol(m.amount, base)}" + (if (m.keyword.isNotBlank()) "・含「${m.keyword}」" else "") + " 時自動套用",
                            fontSize = AppText.caption, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** 新增或修改巨集。[id] 為 0 時新增。 */
@Composable
fun MacroEditScreen(nav: NavController, id: Long) {
    val lk = rememberLookups()
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val orig = remember(id) { repo.settings.value.macros.firstOrNull { it.id == id } }
    var name by rememberSaveable { mutableStateOf(orig?.name.orEmpty()) }
    var transfer by rememberSaveable { mutableStateOf(orig?.type == "TRANSFER") }
    var amount by rememberSaveable { mutableStateOf(orig?.amount?.let(::macroAmt).orEmpty()) }
    var accountId by rememberSaveable { mutableStateOf(orig?.accountId) }
    var toAccountId by rememberSaveable { mutableStateOf(orig?.toAccountId) }
    var categoryId by rememberSaveable { mutableStateOf(orig?.categoryId) }
    var merchant by rememberSaveable { mutableStateOf(orig?.merchant.orEmpty()) }
    // 往來：對象 id 與金額文字（「id=金額」以 ; 串起來，旋轉畫面也保留）
    var sharesRaw by rememberSaveable { mutableStateOf(orig?.shares.orEmpty().joinToString(";") { "${it.personId}=${macroAmt(it.amount)}" }) }
    val shares = sharesRaw.split(';').filter { it.contains('=') }.mapNotNull { e -> e.substringBefore('=').toLongOrNull()?.let { it to e.substringAfter('=') } }
    fun setShares(l: List<Pair<Long, String>>) { sharesRaw = l.joinToString(";") { "${it.first}=${it.second}" } }
    var fromNotif by rememberSaveable { mutableStateOf(orig?.fromNotif ?: false) }
    var keyword by rememberSaveable { mutableStateOf(orig?.keyword.orEmpty()) }
    var pick by remember { mutableStateOf<String?>(null) }
    val base = settings.baseCurrency
    val total = parseMinor(amount)
    val owed = shares.sumOf { parseMinor(it.second) }
    val over = !transfer && owed > total
    val ready = name.isNotBlank() && total > 0 && accountId != null && !over && (!transfer || toAccountId != null)
    val mine = lk.accounts.filter { !it.archived && it.type != AccountType.LOAN }
    val people = lk.accounts.filter { !it.archived && it.type == AccountType.LOAN }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    fun save() {
        val m = TxnMacro(
            id = orig?.id ?: System.currentTimeMillis(), name = name.trim(), type = if (transfer) "TRANSFER" else "EXPENSE", amount = total,
            accountId = accountId, toAccountId = if (transfer) toAccountId else null, categoryId = if (transfer) null else categoryId,
            merchant = merchant.trim(), shares = if (transfer) emptyList() else shares.map { MacroShare(it.first, parseMinor(it.second)) }.filter { it.amount > 0 },
            fromNotif = fromNotif, keyword = keyword.trim(),
        )
        repo.settings.update { s -> s.copy(macros = if (orig == null) s.macros + m else s.macros.map { if (it.id == m.id) m else it }) }
        nav.popBackStack()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        BackTopBar(if (orig == null) "新增巨集" else "編輯巨集", { nav.popBackStack() }) {
            if (orig != null) TextButton(onClick = {
                repo.settings.update { s -> s.copy(macros = s.macros.filter { it.id != orig.id }) }
                com.enwei.moneymanager.ui.components.Undo.show(com.enwei.moneymanager.ui.tr("已刪除巨集")) {
                    repo.settings.update { s -> s.copy(macros = s.macros + orig) }
                }
                nav.popBackStack()
            }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding()) {
            FormLine("名稱") { FormInput(name, { name = it }, placeholder = "例如：房租") }
            FormLine("類型") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(false to "支出", true to "轉帳").forEach { (v, l) -> Choice(l, transfer == v) { transfer = v } }
                }
            }
            FormLine("金額") { FormInput(amount, { s -> amount = s.filter { it.isDigit() || it == '.' } }, placeholder = "0", number = true) }
            FormLine("帳戶", onClick = { pick = "acc" }) {
                Text(lk.account(accountId)?.name ?: "選擇帳戶", color = if (accountId == null) muted else Color.Unspecified)
            }
            if (transfer) FormLine("轉入帳戶", onClick = { pick = "to" }) {
                Text(lk.account(toAccountId)?.name ?: "選擇帳戶", color = if (toAccountId == null) muted else Color.Unspecified)
            } else FormLine("分類", onClick = { pick = "cat" }) {
                Text(lk.catLabel(categoryId) ?: "選擇分類", color = if (categoryId == null) muted else Color.Unspecified)
            }
            FormLine("商家") { FormInput(merchant, { merchant = it }, placeholder = "例如：房東") }

            // 往來：每位對象一列「X 欠你」，最後一列是自己負擔
            if (!transfer) {
                SectionLabel("往來")
                shares.forEachIndexed { i, (pid, v) ->
                    FormLine("${lk.account(pid)?.name ?: "?"} 欠你", trailing = {
                        Text("✕", color = muted, modifier = Modifier.clickable { setShares(shares.filterIndexed { j, _ -> j != i }) }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp))
                    }) {
                        FormInput(v, { s -> setShares(shares.mapIndexed { j, e -> if (j == i) e.first to s.filter { it.isDigit() || it == '.' } else e }) }, placeholder = "0", number = true)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (people.any { p -> shares.none { it.first == p.id } }) Text("＋ 加入對象", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { pick = "person" }.padding(vertical = 6.dp))
                    Spacer(Modifier.weight(1f))
                    if (shares.isNotEmpty() && total > 0) Text(
                        if (over) "超過總額 ${Money.withSymbol(total, base)}" else "自己負擔 ${Money.withSymbol(total - owed, base)}",
                        fontSize = AppText.secondary, color = if (over) MaterialTheme.colorScheme.error else muted,
                    )
                }
                if (people.isEmpty()) Text("先在「往來」新增對象，才能加入", fontSize = AppText.caption, color = muted, modifier = Modifier.padding(horizontal = 16.dp))
            }

            SectionLabel("通知")
            Row(Modifier.fillMaxWidth().clickable { fromNotif = !fromNotif }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("通知自動套用", fontSize = AppText.body)
                    if (total > 0) Text("付款通知金額為 ${Money.withSymbol(total, base)} 時套用", fontSize = AppText.caption, color = muted)
                }
                Switch(fromNotif, { fromNotif = it })
            }
            if (fromNotif) FormLine("關鍵字") { FormInput(keyword, { keyword = it }, placeholder = "選填，例如房東戶名") }
            Spacer(Modifier.height(24.dp))
        }
        Button(onClick = { save() }, enabled = ready, modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) { Text("儲存") }
    }

    when (pick) {
        "acc", "to" -> OptionDialog(
            if (pick == "acc") "帳戶" else "轉入帳戶",
            (if (pick == "to") mine + people else mine).filter { pick != "to" || it.id != accountId }.map { it.id to it.name },
            if (pick == "acc") accountId else toAccountId, onDismiss = { pick = null },
        ) { v -> if (pick == "acc") accountId = v else toAccountId = v }
        "cat" -> OptionDialog(
            "分類",
            lk.categories.filter { !it.archived && it.kind == TxnType.EXPENSE }
                .sortedWith(compareBy({ lk.rootId(it.id)?.let { r -> lk.category(r)?.sortOrder } ?: 0 }, { if (it.parentId == null) 0 else 1 }, { it.sortOrder }))
                .map { it.id to (lk.catLabel(it.id) ?: it.name) },
            categoryId, onDismiss = { pick = null },
        ) { categoryId = it }
        "person" -> OptionDialog(
            "往來對象", people.filter { p -> shares.none { it.first == p.id } }.map { it.id to it.name }, null, onDismiss = { pick = null },
        ) { pid -> setShares(shares + (pid to "")) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 2.dp))
}

@Composable
private fun Choice(label: String, on: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Text(label, fontSize = AppText.secondary, color = if (on) primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .background(if (on) primary.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(14.dp))
            .border(1.dp, if (on) primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 5.dp))
}
