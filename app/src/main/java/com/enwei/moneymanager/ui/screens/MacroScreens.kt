package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.enwei.moneymanager.data.MacroStep
import com.enwei.moneymanager.data.TxnMacro
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.ui.AppText
import com.enwei.moneymanager.ui.ExpenseColor
import com.enwei.moneymanager.ui.IncomeColor
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
private val TYPES = listOf("EXPENSE" to "支出", "INCOME" to "收入", "TRANSFER" to "轉帳")
private fun typeLabel(t: String) = TYPES.firstOrNull { it.first == t }?.second ?: "支出"

/** 一步的一行摘要：類型 金額・分類・商家・帳戶 */
private fun stepLine(st: MacroStep, lk: Lookups, base: String): String = listOfNotNull(
    "${typeLabel(st.type)} ${Money.withSymbol(st.amount, base)}",
    if (st.type == "TRANSFER") null else lk.catLabel(st.categoryId),
    st.merchant.takeIf { it.isNotBlank() },
    lk.account(st.accountId)?.name?.let { a -> if (st.type == "TRANSFER") "$a → ${lk.account(st.toAccountId)?.name ?: "未選"}" else a },
).joinToString("・")

/** 巨集清單：每個巨集一張卡片，列出每一步；按「執行」依序全部記下（可復原），點卡片修改。 */
@Composable
fun MacroListScreen(nav: NavController) {
    com.enwei.moneymanager.ui.components.PageTour("macros2", listOf(
        com.enwei.moneymanager.ui.components.Coach.Step(null, "巨集", "把一整套固定的動作存起來：每一步可以是支出、收入或轉帳，支出可加往來對象各欠你的金額。按「執行」就依序全部記好，例如房租、發薪日分配、固定的團購代墊。"),
        com.enwei.moneymanager.ui.components.Coach.Step(null, "通知自動套用", "開啟後，收到金額等於某一步的付款或入帳通知，就自動執行整套，那一步用通知的日期與帳戶；可加關鍵字（例如對方戶名），避免同金額的其他交易被套用。"),
    ))
    val lk = rememberLookups()
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val base = settings.baseCurrency
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BackTopBar("巨集", { nav.popBackStack() }) {
            IconButton(onClick = { nav.navigate("macro/0") }) { Icon(Icons.Filled.Add, "新增巨集") }
        }
        if (settings.macros.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("還沒有巨集", fontSize = AppText.body, color = muted)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { nav.navigate("macro/0") }) { Text("新增巨集") }
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(settings.macros, key = { it.id }) { m ->
                val steps = m.allSteps()
                AppCard(Modifier.fillMaxWidth().clickable { nav.navigate("macro/${m.id}") }) {
                    Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name, fontSize = AppText.body, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${steps.size} 步", fontSize = AppText.caption, color = muted)
                            }
                            FilledTonalButton(onClick = {
                                scope.launch {
                                    val ids = repo.runMacro(m)
                                    com.enwei.moneymanager.ui.components.Undo.show(com.enwei.moneymanager.ui.tr("已記下「${m.name}」")) { ids.forEach { repo.deleteTxn(it) } }
                                }
                            }, enabled = steps.isNotEmpty()) { Text("執行") }
                        }
                        steps.forEachIndexed { i, st ->
                            Text("${i + 1}. " + stepLine(st, lk, base), fontSize = AppText.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = when (st.type) { "INCOME" -> IncomeColor; "TRANSFER" -> MaterialTheme.colorScheme.onSurface; else -> ExpenseColor })
                            if (st.type == "EXPENSE" && st.shares.isNotEmpty()) Text(
                                st.shares.joinToString("・") { s -> "${lk.account(s.personId)?.name ?: "?"} 欠你 ${Money.withSymbol(s.amount, base)}" },
                                fontSize = AppText.caption, color = muted, modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                        if (m.fromNotif) Text("通知金額相符時自動套用" + (if (m.keyword.isNotBlank()) "・含「${m.keyword}」" else ""),
                            fontSize = AppText.caption, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** 編輯中的一步（金額用文字，往來是對象 id 與金額文字） */
private data class StepDraft(
    val type: String = "EXPENSE", val amount: String = "", val accountId: Long? = null, val toAccountId: Long? = null,
    val categoryId: Long? = null, val merchant: String = "", val shares: List<Pair<Long, String>> = emptyList(),
) {
    val total get() = parseMinor(amount)
    val owed get() = shares.sumOf { parseMinor(it.second) }
    val ok get() = total > 0 && accountId != null && (type != "TRANSFER" || toAccountId != null) && (type != "EXPENSE" || owed <= total)
    fun toStep() = MacroStep(type, total, accountId, if (type == "TRANSFER") toAccountId else null, if (type == "TRANSFER") null else categoryId,
        merchant.trim(), if (type == "EXPENSE") shares.map { MacroShare(it.first, parseMinor(it.second)) }.filter { it.amount > 0 } else emptyList())
}

/** 新增或修改巨集。[id] 為 0 時新增。 */
@Composable
fun MacroEditScreen(nav: NavController, id: Long) {
    val lk = rememberLookups()
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val orig = remember(id) { repo.settings.value.macros.firstOrNull { it.id == id } }
    var name by remember { mutableStateOf(orig?.name.orEmpty()) }
    val steps = remember {
        mutableStateListOf<StepDraft>().apply {
            addAll(orig?.allSteps().orEmpty().map { st ->
                StepDraft(st.type, macroAmt(st.amount), st.accountId, st.toAccountId, st.categoryId, st.merchant, st.shares.map { it.personId to macroAmt(it.amount) })
            })
            if (isEmpty()) add(StepDraft())
        }
    }
    var fromNotif by remember { mutableStateOf(orig?.fromNotif ?: false) }
    var keyword by remember { mutableStateOf(orig?.keyword.orEmpty()) }
    // 選擇視窗：哪一步、選什麼（acc／to／cat／person）
    var pick by remember { mutableStateOf<Pair<Int, String>?>(null) }
    val base = settings.baseCurrency
    val ready = name.isNotBlank() && steps.isNotEmpty() && steps.all { it.ok }
    val mine = lk.accounts.filter { !it.archived && it.type != AccountType.LOAN }
    val people = lk.accounts.filter { !it.archived && it.type == AccountType.LOAN }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    fun set(i: Int, f: (StepDraft) -> StepDraft) { steps[i] = f(steps[i]) }

    fun save() {
        val m = TxnMacro(id = orig?.id ?: System.currentTimeMillis(), name = name.trim(), steps = steps.map { it.toStep() },
            fromNotif = fromNotif, keyword = keyword.trim())
        repo.settings.update { s -> s.copy(macros = if (orig == null) s.macros + m else s.macros.map { if (it.id == m.id) m else it }) }
        nav.popBackStack()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
            Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
                FormLine("名稱") { FormInput(name, { name = it }, placeholder = "例如：房租、發薪日") }
            }

            // 每一步一張卡片：步驟編號、類型、金額、帳戶、分類或轉入帳戶、商家、往來
            steps.forEachIndexed { i, st ->
                AppCard(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("步驟 ${i + 1}", fontSize = AppText.secondary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (steps.size > 1) TextButton(onClick = { steps.removeAt(i) }) { Text("移除", color = muted, fontSize = AppText.secondary) }
                    }
                    FormLine("類型") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TYPES.forEach { (v, l) -> Choice(l, st.type == v) { set(i) { it.copy(type = v) } } }
                        }
                    }
                    FormLine("金額") { FormInput(st.amount, { s -> set(i) { it.copy(amount = s.filter { c -> c.isDigit() || c == '.' }) } }, placeholder = "0", number = true) }
                    FormLine(if (st.type == "INCOME") "存入帳戶" else "帳戶", onClick = { pick = i to "acc" }) {
                        Text(lk.account(st.accountId)?.name ?: "選擇帳戶", color = if (st.accountId == null) muted else Color.Unspecified)
                    }
                    if (st.type == "TRANSFER") FormLine("轉入帳戶", onClick = { pick = i to "to" }) {
                        Text(lk.account(st.toAccountId)?.name ?: "選擇帳戶", color = if (st.toAccountId == null) muted else Color.Unspecified)
                    } else FormLine("分類", onClick = { pick = i to "cat" }) {
                        Text(lk.catLabel(st.categoryId) ?: "選擇分類", color = if (st.categoryId == null) muted else Color.Unspecified)
                    }
                    FormLine(if (st.type == "INCOME") "來源" else "商家") { FormInput(st.merchant, { v -> set(i) { it.copy(merchant = v) } }, placeholder = "選填") }
                    // 往來（只有支出）：每人一列「X 欠你」
                    if (st.type == "EXPENSE") {
                        st.shares.forEachIndexed { j, (pid, v) ->
                            FormLine("${lk.account(pid)?.name ?: "?"} 欠你", trailing = {
                                Text("✕", color = muted, modifier = Modifier.clickable { set(i) { it.copy(shares = it.shares.filterIndexed { k, _ -> k != j }) } }
                                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp))
                            }) {
                                FormInput(v, { s -> set(i) { it.copy(shares = it.shares.mapIndexed { k, e -> if (k == j) e.first to s.filter { c -> c.isDigit() || c == '.' } else e }) } },
                                    placeholder = "0", number = true)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (people.any { p -> st.shares.none { it.first == p.id } }) Text("＋ 往來對象", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { pick = i to "person" }.padding(vertical = 6.dp))
                            Spacer(Modifier.weight(1f))
                            if (st.shares.isNotEmpty() && st.total > 0) Text(
                                if (st.owed > st.total) "超過金額 ${Money.withSymbol(st.total, base)}" else "別人欠你 ${Money.withSymbol(st.owed, base)}",
                                fontSize = AppText.secondary, color = if (st.owed > st.total) MaterialTheme.colorScheme.error else muted,
                            )
                        }
                    } else Spacer(Modifier.height(8.dp))
                }
            }
            OutlinedButton(onClick = { steps.add(StepDraft(accountId = steps.lastOrNull()?.accountId)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) { Text("＋ 新增步驟") }

            // 通知自動套用
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().clickable { fromNotif = !fromNotif }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("通知自動套用", fontSize = AppText.body)
                        val amts = steps.map { it.total }.filter { it > 0 }.distinct()
                        if (amts.isNotEmpty()) Text("通知金額為 " + amts.joinToString("、") { Money.withSymbol(it, base) } + " 時執行整套", fontSize = AppText.caption, color = muted)
                    }
                    Switch(fromNotif, { fromNotif = it })
                }
                if (fromNotif) FormLine("關鍵字") { FormInput(keyword, { keyword = it }, placeholder = "選填，例如對方戶名") }
            }
            Spacer(Modifier.height(24.dp))
        }
        Button(onClick = { save() }, enabled = ready, modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) { Text("儲存") }
    }

    pick?.let { (i, kind) ->
        val st = steps.getOrNull(i) ?: return@let
        when (kind) {
            "acc", "to" -> OptionDialog(
                if (kind == "acc") "帳戶" else "轉入帳戶",
                (if (kind == "to") mine + people else mine).filter { kind != "to" || it.id != st.accountId }.map { it.id to it.name },
                if (kind == "acc") st.accountId else st.toAccountId, onDismiss = { pick = null },
            ) { v -> set(i) { if (kind == "acc") it.copy(accountId = v) else it.copy(toAccountId = v) } }
            "cat" -> {
                val k = if (st.type == "INCOME") TxnType.INCOME else TxnType.EXPENSE
                OptionDialog(
                    "分類",
                    lk.categories.filter { !it.archived && it.kind == k }
                        .sortedWith(compareBy({ lk.rootId(it.id)?.let { r -> lk.category(r)?.sortOrder } ?: 0 }, { lk.rootId(it.id) ?: 0L }, { if (it.parentId == null) 0 else 1 }, { it.sortOrder }))
                        .map { it.id to (lk.catLabel(it.id) ?: it.name) },
                    st.categoryId, onDismiss = { pick = null },
                ) { v -> set(i) { it.copy(categoryId = v) } }
            }
            "person" -> OptionDialog(
                "往來對象", people.filter { p -> st.shares.none { it.first == p.id } }.map { it.id to it.name }, null, onDismiss = { pick = null },
            ) { pid -> set(i) { it.copy(shares = it.shares + (pid to "")) } }
        }
    }
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
