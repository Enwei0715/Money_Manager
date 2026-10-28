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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.data.hashPasscode
import com.enwei.moneymanager.net.FxService
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.FormInput
import com.enwei.moneymanager.ui.components.FormLine
import com.enwei.moneymanager.work.ReminderWorker
import java.time.DayOfWeek

@Composable
private fun SectionHeader(title: String) {
    Text(
        title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(start = 24.dp, top = 28.dp, bottom = 12.dp),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ConfigRow(title: String, value: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (value != null) Text(value, color = MaterialTheme.colorScheme.primary, fontSize = 17.sp)
    }
}

private data class Choice<T>(val title: String, val options: List<Pair<T, String>>, val onPick: (T) -> Unit)

/** 詳細設定：帳戶、分類／週期、顯示與輸入、其他。 */
@Composable
fun ConfigScreen(nav: NavController) {
    val ctx = LocalContext.current
    val s by repo.settings.flow.collectAsStateWithLifecycle()
    var choice by remember { mutableStateOf<Choice<Any>?>(null) }
    var pinSetup by remember { mutableStateOf(false) }
    var pinDisable by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    @Suppress("UNCHECKED_CAST")
    fun <T> ask(title: String, options: List<Pair<T, String>>, onPick: (T) -> Unit) {
        choice = Choice(title, options, onPick) as Choice<Any>
    }
    fun onOff(b: Boolean) = if (b) "ON" else "OFF"
    val currencies = (listOf("TWD") + FxService.common).distinct()

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackTopBar("設定", { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                SectionHeader("帳戶")
                ConfigRow("帳戶設定") { nav.navigate("accounts") }
                ConfigRow("修改帳戶順序") { nav.navigate("accountorder") }
                ConfigRow("新增帳戶") { nav.navigate("account/0") }

                SectionHeader("分類／週期")
                ConfigRow("收入分類設定") { nav.navigate("categories") }
                ConfigRow("支出分類設定") { nav.navigate("categories") }
                ConfigRow("預算設定") { nav.navigate("budget") }
                ConfigRow("週期設定") { nav.navigate("recurring") }

                SectionHeader("設定")
                ConfigRow("主幣別設定", "${s.baseCurrency} (${Money.symbolOf(s.baseCurrency).trim()})") {
                    ask("主幣別", currencies.map { it to it }) { c -> repo.settings.update { it.copy(baseCurrency = c) } }
                }
                ConfigRow("副幣別設定", Money.symbolOf(s.subCurrency).trim()) {
                    ask("副幣別（記帳鍵盤快速切換）", currencies.map { it to it }) { c -> repo.settings.update { it.copy(subCurrency = c) } }
                }
                ConfigRow("起始畫面（每日／日曆）", if (s.startScreen == 1) "日曆" else "每日") {
                    ask("起始畫面", listOf(0 to "每日", 1 to "日曆")) { v -> repo.settings.update { it.copy(startScreen = v) } }
                }
                ConfigRow("每月起始日", "每月 ${s.monthStartDay} 日") {
                    ask("每月起始日", (1..28).map { it to "每月 $it 日" }) { v -> repo.settings.update { it.copy(monthStartDay = v) } }
                }
                ConfigRow("每週起始日", if (s.weekStartDay == 7) "週日" else "週一") {
                    ask("每週起始日", listOf(7 to "週日", 1 to "週一")) { v -> repo.settings.update { it.copy(weekStartDay = v) } }
                }
                ConfigRow("上期結轉", onOff(s.carryOver)) { repo.settings.update { it.copy(carryOver = !it.carryOver) } }
                ConfigRow("收支顏色設定", if (s.colorSetB) "設定 B" else "設定 A") {
                    ask("收支顏色", listOf(false to "設定 A：收入藍、支出紅", true to "設定 B：收入紅、支出藍")) { v ->
                        repo.settings.update { it.copy(colorSetB = v) }
                    }
                }
                ConfigRow("時間輸入", onOff(s.showTime)) { repo.settings.update { it.copy(showTime = !it.showTime) } }
                ConfigRow("自動完成", onOff(s.autocomplete)) { repo.settings.update { it.copy(autocomplete = !it.autocomplete) } }
                ConfigRow("輸入順序", if (s.inputFromCategory) "從分類" else "從金額") {
                    ask("輸入順序", listOf(false to "從金額", true to "從分類")) { v -> repo.settings.update { it.copy(inputFromCategory = v) } }
                }

                SectionHeader("其他")
                ConfigRow("密碼鎖", onOff(s.passcodeOn)) { if (s.passcodeOn) pinDisable = true else pinSetup = true }
                ConfigRow("每日提醒", if (s.reminderEnabled) "%02d:%02d".format(s.reminderMinutes / 60, s.reminderMinutes % 60) else "OFF") {
                    if (s.reminderEnabled) {
                        repo.settings.update { it.copy(reminderEnabled = false) }
                        ReminderWorker.schedule(ctx)
                    } else pickTime = true
                }
                ConfigRow("通知自動記帳") { nav.navigate("notif") }
                ConfigRow("電子發票載具") { nav.navigate("invoice") }
                ConfigRow("即時匯率") { nav.navigate("fx") }
                ConfigRow("股票手續費與 App 更新") { nav.navigate("settings") }
                ConfigRow("備份與還原") { nav.navigate("backup") }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    choice?.let { c ->
        AlertDialog(
            onDismissRequest = { choice = null },
            title = { Text(c.title) },
            text = {
                LazyColumn(Modifier.height(minOf(56 * c.options.size, 400).dp)) {
                    items(c.options) { (v, label) ->
                        Text(label, fontSize = 17.sp, modifier = Modifier.fillMaxWidth().clickable { c.onPick(v); choice = null }.padding(vertical = 14.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choice = null }) { Text("取消") } },
        )
    }
    if (pinSetup) PasscodeSetupDialog(onDone = { hash ->
        repo.settings.update { it.copy(passcodeHash = hash) }
        pinSetup = false
    }, onCancel = { pinSetup = false })
    if (pinDisable) Dialog(onDismissRequest = { pinDisable = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LockScreen(s.passcodeHash, title = "輸入目前密碼以關閉") {
            repo.settings.update { it.copy(passcodeHash = "") }
            pinDisable = false
        }
    }
    if (pickTime) {
        val state = rememberTimePickerState(s.reminderMinutes / 60, s.reminderMinutes % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("每日提醒時間") },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    repo.settings.update { it.copy(reminderEnabled = true, reminderMinutes = state.hour * 60 + state.minute) }
                    ReminderWorker.schedule(ctx)
                    pickTime = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("取消") } },
        )
    }
}

/** 珊瑚紅底的密碼鍵盤。 */
@Composable
private fun PinPad(title: String, entered: Int, error: Boolean, onDigit: (Char) -> Unit, onBack: () -> Unit) {
    val coral = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxSize().background(coral).statusBarsPadding().padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(80.dp))
        Icon(Icons.Outlined.Savings, null, tint = Color.White, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(40.dp))
        Text(if (error) "密碼錯誤，請再試一次" else title, color = Color.White, fontSize = 22.sp)
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier.size(20.dp).border(1.5.dp, Color.White, CircleShape)
                        .background(if (i < entered) Color.White else Color.Transparent, CircleShape),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫")).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { k ->
                    Box(
                        Modifier.weight(1f).height(84.dp).clickable(enabled = k.isNotEmpty()) { if (k == "⌫") onBack() else onDigit(k[0]) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k == "⌫") Icon(Icons.AutoMirrored.Outlined.Backspace, "刪除", tint = Color.White, modifier = Modifier.size(32.dp))
                        else Text(k, color = Color.White, fontSize = 30.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun LockScreen(hash: String, title: String = "請輸入密碼", onUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    PinPad(title, pin.length, error, onDigit = { d ->
        if (pin.length >= 4) return@PinPad
        pin += d
        error = false
        if (pin.length == 4) {
            if (hashPasscode(pin) == hash) onUnlock() else { error = true; pin = "" }
        }
    }, onBack = { pin = pin.dropLast(1) })
}

@Composable
private fun PasscodeSetupDialog(onDone: (String) -> Unit, onCancel: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        PinPad(if (first == null) "設定 4 位數密碼" else "再輸入一次確認", pin.length, error, onDigit = { d ->
            if (pin.length >= 4) return@PinPad
            pin += d
            error = false
            if (pin.length == 4) {
                when {
                    first == null -> { first = pin; pin = "" }
                    first == pin -> onDone(hashPasscode(pin))
                    else -> { error = true; first = null; pin = "" }
                }
            }
        }, onBack = { pin = pin.dropLast(1) })
    }
}

/** 每月預算：總預算與各支出分類。 */
@Composable
fun BudgetScreen(nav: NavController) {
    val lk = rememberLookups()
    val s by repo.settings.flow.collectAsStateWithLifecycle()
    val fields = remember { mutableStateOf(s.budgets.mapValues { Money.fromMinor(it.value).stripTrailingZeros().toPlainString() }) }
    fun set(id: Long, text: String) {
        fields.value = fields.value + (id to text)
        val minor = Money.parseMinor(text)
        repo.settings.update { st -> st.copy(budgets = if (minor == null || minor <= 0) st.budgets - id else st.budgets + (id to minor)) }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.surface, topBar = { BackTopBar("預算設定", { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Text("每月預算，留空代表不設定。進度顯示在「交易 → 總計」。", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp, modifier = Modifier.padding(20.dp))
                FormLine("總預算") { FormInput(fields.value[0L].orEmpty(), { set(0L, it) }, "0", number = true) }
            }
            items(lk.categories.filter { it.kind == TxnType.EXPENSE && !it.archived }, key = { it.id }) { c ->
                FormLine("${c.icon} ${c.name}") { FormInput(fields.value[c.id].orEmpty(), { set(c.id, it) }, "", number = true) }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

/** 書籤：常用交易範本，點一下帶入記帳頁。 */
@Composable
fun BookmarksScreen(nav: NavController) {
    val lk = rememberLookups()
    val s by repo.settings.flow.collectAsStateWithLifecycle()
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackTopBar("書籤", { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            if (s.bookmarks.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("如何新增書籤？", fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "在「交易」頁點選一筆交易，下方有「刪除」「複製」「書籤」按鈕，按「書籤」即可加入。也可以在記帳頁輸入金額後按右上角的星號。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            items(s.bookmarks, key = { it.id }) { b ->
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable { nav.navigate("txn/0?bookmark=${b.id}") }
                        .padding(start = 20.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.width(96.dp)) {
                        Text(if (b.type == TxnType.TRANSFER) "轉帳" else lk.category(b.categoryId)?.name ?: "未分類",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f)) {
                        b.merchant?.let { Text(it, fontSize = 17.sp) }
                        Text(lk.account(b.accountId)?.name.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                    Text(if (b.amount > 0) Money.withSymbol(b.amount, b.currency) else "", color = typeColor(b.type))
                    IconButton(onClick = { repo.settings.update { st -> st.copy(bookmarks = st.bookmarks.filter { it.id != b.id }) } }) {
                        Icon(Icons.Outlined.Delete, "刪除書籤", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
