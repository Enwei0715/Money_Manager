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

/**
 * 編輯中的巨集（名稱、步驟、通知設定）。每一步用記帳畫面編輯（txn/0?macro=true），
 * 離開這頁去編輯步驟時畫面狀態會被釋放，所以草稿放在這裡，回來時接著用。
 */
object MacroDraft {
    var forId by mutableStateOf<Long?>(null)
    var name by mutableStateOf("")
    val steps = mutableStateListOf<MacroStep>()
    var fromNotif by mutableStateOf(false)
    var keyword by mutableStateOf("")
    /** 正在記帳畫面編輯第幾步（-1 為新增一步） */
    private var editIndex = -1
    /** 去記帳畫面編輯步驟、回來時不要重新載入 */
    var returning = false

    fun load(id: Long, m: TxnMacro?) {
        forId = id; name = m?.name.orEmpty(); fromNotif = m?.fromNotif ?: false; keyword = m?.keyword.orEmpty()
        steps.clear(); steps.addAll(m?.allSteps().orEmpty())
    }
    fun editing(): MacroStep? = steps.getOrNull(editIndex) ?: MacroStep(accountId = steps.lastOrNull()?.accountId)
    fun open(nav: NavController, index: Int) { editIndex = index; returning = true; nav.navigate("txn/0?macro=true") }
    fun put(step: MacroStep) { if (editIndex in steps.indices) steps[editIndex] = step else steps.add(step) }
}

/** 新增或修改巨集。[id] 為 0 時新增。每一步點開就是記帳畫面（分類、鍵盤、帳戶、往來都一樣）。 */
@Composable
fun MacroEditScreen(nav: NavController, id: Long) {
    val lk = rememberLookups()
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val orig = remember(id) { repo.settings.value.macros.firstOrNull { it.id == id } }
    val d = MacroDraft
    // 這一頁第一次打開才載入；旋轉螢幕、從步驟編輯回來時保留正在改的草稿（狀態跟著這一頁保存）
    var loaded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (!loaded || d.forId != id) { if (!loaded || d.forId == null) d.load(id, orig); loaded = true; d.returning = false }
    val base = settings.baseCurrency
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ready = d.name.isNotBlank() && d.steps.isNotEmpty() && d.steps.all { it.amount > 0 }

    fun save() {
        val m = TxnMacro(id = orig?.id ?: System.currentTimeMillis(), name = d.name.trim(), steps = d.steps.toList(),
            fromNotif = d.fromNotif, keyword = d.keyword.trim())
        repo.settings.update { s -> s.copy(macros = if (orig == null) s.macros + m else s.macros.map { if (it.id == m.id) m else it }) }
        d.forId = null
        nav.popBackStack()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BackTopBar(if (orig == null) "新增巨集" else "編輯巨集", { d.forId = null; nav.popBackStack() }) {
            if (orig != null) TextButton(onClick = {
                repo.settings.update { s -> s.copy(macros = s.macros.filter { it.id != orig.id }) }
                com.enwei.moneymanager.ui.components.Undo.show(com.enwei.moneymanager.ui.tr("已刪除巨集")) {
                    repo.settings.update { s -> s.copy(macros = s.macros + orig) }
                }
                d.forId = null
                nav.popBackStack()
            }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding()) {
            Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
                FormLine("名稱") { FormInput(d.name, { d.name = it }, placeholder = "例如：房租、發薪日") }
            }
            // 步驟：每一步一列摘要，點了用記帳畫面編輯
            Text("步驟", fontSize = AppText.caption, color = muted, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp))
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                d.steps.forEachIndexed { i, st ->
                    Row(Modifier.fillMaxWidth().clickable { d.open(nav, i) }.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", fontSize = AppText.secondary, color = muted, modifier = Modifier.padding(end = 12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stepLine(st, lk, base), fontSize = AppText.body, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                color = when (st.type) { "INCOME" -> IncomeColor; "TRANSFER" -> MaterialTheme.colorScheme.onSurface; else -> ExpenseColor })
                            if (st.type == "EXPENSE" && st.shares.isNotEmpty()) Text(
                                st.shares.joinToString("・") { s -> "${lk.account(s.personId)?.name ?: "?"} 欠你 ${Money.withSymbol(s.amount, base)}" } +
                                    "・你的支出 ${Money.withSymbol((st.amount - st.shares.sumOf { it.amount }).coerceAtLeast(0), base)}",
                                fontSize = AppText.caption, color = muted,
                            )
                        }
                        Text("✕", color = muted, modifier = Modifier.clickable { d.steps.removeAt(i) }.padding(12.dp))
                    }
                    com.enwei.moneymanager.ui.components.LineDivider(Modifier.padding(start = 16.dp))
                }
                Text("＋ 新增步驟", fontSize = AppText.body, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable { d.open(nav, -1) }.padding(horizontal = 16.dp, vertical = 14.dp))
            }

            // 通知自動套用
            Text("通知", fontSize = AppText.caption, color = muted, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp))
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().clickable { d.fromNotif = !d.fromNotif }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("通知自動套用", fontSize = AppText.body)
                        val amts = d.steps.map { it.amount }.filter { it > 0 }.distinct()
                        if (amts.isNotEmpty()) Text("通知金額為 " + amts.joinToString("、") { Money.withSymbol(it, base) } + " 時執行整套", fontSize = AppText.caption, color = muted)
                    }
                    Switch(d.fromNotif, { d.fromNotif = it })
                }
                if (d.fromNotif) FormLine("關鍵字") { FormInput(d.keyword, { d.keyword = it }, placeholder = "選填，例如對方戶名") }
            }
            Spacer(Modifier.height(24.dp))
        }
        Button(onClick = { save() }, enabled = ready, modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) { Text("儲存") }
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
