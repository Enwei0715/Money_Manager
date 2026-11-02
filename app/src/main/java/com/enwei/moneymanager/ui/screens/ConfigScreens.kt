package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.enwei.moneymanager.ui.components.Text
import androidx.compose.material3.TextButton
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
import com.enwei.moneymanager.data.spendingReview
import com.enwei.moneymanager.data.budgetSuggestions
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.data.hashPasscode
import com.enwei.moneymanager.net.FxService
import com.enwei.moneymanager.BuildConfig
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.OptionDialog
import com.enwei.moneymanager.ui.components.FormInput
import com.enwei.moneymanager.ui.components.FormLine
import java.time.DayOfWeek

private fun uiScaleLabel(v: Float) = when {
    v <= 0.85f -> "較小（85%）"
    v <= 0.92f -> "小（92%）"
    v <= 1f -> "標準（100%）"
    v <= 1.1f -> "大（110%）"
    else -> "特大（120%）"
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ConfigRow(title: String, value: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (value != null) Text(value, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
    }
}

private data class Choice<T>(val title: String, val options: List<Pair<T, String>>, val selected: T?, val description: String?, val onPick: (T) -> Unit)

private fun lockDelayLabel(sec: Int) = when (sec) {
    0 -> "立即"
    in 1..59 -> "$sec 秒後"
    else -> "${sec / 60} 分鐘後"
}

/** 設定：依用途分組管理帳戶、記帳、外觀、自動化、AI、安全與備份更新。 */
@Composable
fun ConfigScreen(nav: NavController) {
    val ctx = LocalContext.current
    val s by repo.settings.flow.collectAsStateWithLifecycle()
    var choice by remember { mutableStateOf<Choice<Any>?>(null) }

    @Suppress("UNCHECKED_CAST")
    fun <T> ask(title: String, options: List<Pair<T, String>>, selected: T?, description: String? = null, onPick: (T) -> Unit) {
        choice = Choice(title, options, selected, description, onPick) as Choice<Any>
    }
    fun onOff(b: Boolean) = if (b) "ON" else "OFF"
    fun askOnOff(title: String, value: Boolean, description: String?, onPick: (Boolean) -> Unit) =
        ask(title, listOf(false to "OFF", true to "ON"), value, description, onPick)
    val currencies = (listOf("TWD") + FxService.common).distinct()

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackTopBar("設定", { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                SectionHeader("帳戶")
                ConfigRow("帳戶設定") { nav.navigate("accountsettings") }
                ConfigRow("帳戶群組") { nav.navigate("accountgroups") }

                SectionHeader("分類與預算")
                ConfigRow("支出分類設定") { nav.navigate("categories?kind=EXPENSE") }
                ConfigRow("收入分類設定") { nav.navigate("categories?kind=INCOME") }
                ConfigRow("預算設定") { nav.navigate("budget") }

                SectionHeader("記帳")
                ConfigRow("輸入順序", if (s.inputFromCategory) "從分類" else "從金額") {
                    ask("輸入順序", listOf(false to "從金額", true to "從分類"), s.inputFromCategory, "新增交易時先跳出哪個輸入面板。") { v ->
                        repo.settings.update { it.copy(inputFromCategory = v) }
                    }
                }
                ConfigRow("時間輸入", onOff(s.showTime)) {
                    askOnOff("時間輸入", s.showTime, "記帳時除了日期也可以設定時間。") { v -> repo.settings.update { it.copy(showTime = v) } }
                }
                ConfigRow("週期交易", null) { nav.navigate("recurring") }
                ConfigRow("自動完成", onOff(s.autocomplete)) {
                    askOnOff("自動完成", s.autocomplete, "輸入內容時依過去紀錄建議，並帶入分類。") { v -> repo.settings.update { it.copy(autocomplete = v) } }
                }
                SectionHeader("期間與顯示")
                ConfigRow("起始畫面（每日／日曆）", if (s.startScreen == 1) "日曆" else "每日") {
                    ask("起始畫面", listOf(0 to "每日", 1 to "日曆"), s.startScreen) { v -> repo.settings.update { it.copy(startScreen = v) } }
                }
                ConfigRow("日曆標示", if (s.calendarDots) "顏色小點" else "文字") {
                    ask("日曆標示", listOf(false to "文字（休、結、繳、扣）", true to "顏色小點"), s.calendarDots,
                        "放在日曆日期與金額之間；點日期可以看完整名稱。") { v -> repo.settings.update { it.copy(calendarDots = v) } }
                }
                ConfigRow("搜尋的篩選方式", if (s.filterStyle == 0) "圖表勾選" else "欄位篩選") {
                    ask("搜尋的篩選方式", listOf(0 to "圖表勾選（收入／支出圓環、勾選分類與帳戶）", 1 to "欄位篩選（期間、帳戶、分類、金額）"), s.filterStyle) { v ->
                        repo.settings.update { it.copy(filterStyle = v) }
                    }
                }
                ConfigRow("每月起始日", "每月 ${s.monthStartDay} 日") {
                    ask("每月起始日", (1..28).map { it to "每月 $it 日" }, s.monthStartDay, "例如發薪日是 5 號，設成 5 日就會以 5 號到下月 4 號為一個月。") { v ->
                        repo.settings.update { it.copy(monthStartDay = v) }
                    }
                }
                ConfigRow("每週起始日", if (s.weekStartDay == 7) "週日" else "週一") {
                    ask("每週起始日", listOf(7 to "週日", 1 to "週一"), s.weekStartDay) { v -> repo.settings.update { it.copy(weekStartDay = v) } }
                }
                ConfigRow("滑動切換", if (s.swipe == 1) "切換期間" else "無") {
                    ask("滑動切換", listOf(0 to "無", 1 to "切換期間"), s.swipe, "在交易頁左右滑動時要做什麼。") { v -> repo.settings.update { it.copy(swipe = v) } }
                }
                ConfigRow("顯示說明", onOff(s.showDescription)) {
                    askOnOff("顯示說明", s.showDescription, "在交易列表中顯示每筆交易的說明文字。") { v -> repo.settings.update { it.copy(showDescription = v) } }
                }

                SectionHeader("幣別")
                ConfigRow("主幣別", "${s.baseCurrency} (${Money.symbolOf(s.baseCurrency).trim()})") {
                    ask("主幣別", currencies.map { it to it }, s.baseCurrency, "淨資產與報表統一換算成的幣別。") { c -> repo.settings.update { it.copy(baseCurrency = c) } }
                }
                ConfigRow("副幣別", Money.symbolOf(s.subCurrency).trim()) {
                    ask("副幣別", currencies.map { it to it }, s.subCurrency, "記帳鍵盤上可以一鍵切換的第二種幣別。") { c -> repo.settings.update { it.copy(subCurrency = c) } }
                }
                ConfigRow("即時匯率") { nav.navigate("fx") }

                SectionHeader("外觀")
                ConfigRow("主題", listOf("跟隨系統", "淺色", "深色")[s.themeMode.coerceIn(0, 2)]) {
                    ask("主題", listOf(0 to "跟隨系統", 1 to "淺色", 2 to "深色"), s.themeMode) { v -> repo.settings.update { it.copy(themeMode = v) } }
                }
                ConfigRow("語言", listOf("跟隨系統", "繁體中文", "English")[s.language.coerceIn(0, 2)]) {
                    ask("語言", listOf(0 to "跟隨系統", 1 to "繁體中文", 2 to "English"), s.language, "App 名稱也會跟著語言切換（帳理／Ledgerly）。") { v ->
                        repo.settings.update { it.copy(language = v) }
                        com.enwei.moneymanager.ui.I18n.init(ctx, v)
                        com.enwei.moneymanager.ui.applyAppLocale(ctx, v)
                    }
                }
                ConfigRow("介面大小", uiScaleLabel(s.uiScale)) {
                    ask("介面大小", listOf(0.85f to "較小（85%）", 0.92f to "小（92%）", 1f to "標準（100%）", 1.1f to "大（110%）", 1.2f to "特大（120%）"), s.uiScale) { v ->
                        repo.settings.update { it.copy(uiScale = v) }
                    }
                }
                ConfigRow("收支顏色", if (s.colorSetB) "設定 B" else "設定 A") {
                    ask(
                        "收支顏色", listOf(false to "設定 A", true to "設定 B"), s.colorSetB,
                        "預設收入顯示藍色、支出顯示紅色，也可以反過來。\n\nA. 收入：藍、支出：紅\nB. 收入：紅、支出：藍",
                    ) { v -> repo.settings.update { it.copy(colorSetB = v) } }
                }

                SectionHeader("自動記帳")
                ConfigRow("通知自動記帳") { nav.navigate("notif") }
                ConfigRow("錢包畫面讀取") { nav.navigate("walletreader") }

                SectionHeader("AI")
                ConfigRow("AI 模型設定", onOff(s.aiEnabled && (s.groqKey.isNotBlank() || s.geminiKey.isNotBlank()))) { nav.navigate("ai") }
                // AI 說話的口吻：收支檢討、投資報告、股票助理都會照這個語氣
                ConfigRow("AI 語氣", com.enwei.moneymanager.net.GroqAi.AiTone.entries.getOrElse(s.aiTone) { com.enwei.moneymanager.net.GroqAi.AiTone.GENTLE }.label) {
                    ask(
                        "AI 語氣", com.enwei.moneymanager.net.GroqAi.AiTone.entries.mapIndexed { i, t -> i to "${t.label}：${t.desc}" }, s.aiTone,
                        "收支檢討、投資報告與股票助理的口吻；下次產生的內容開始套用。",
                    ) { v -> repo.settings.update { it.copy(aiTone = v) } }
                }
                ConfigRow("背景 AI 只用 Wi-Fi", onOff(s.aiWifiOnly)) {
                    askOnOff("背景 AI 只用 Wi-Fi", s.aiWifiOnly, "開啟後，新聞標註、重點整理與每日報告只在 Wi-Fi 等不計量網路時於背景執行；手動按「立即更新」與對話不受影響。") { v ->
                        repo.settings.update { it.copy(aiWifiOnly = v) }
                        com.enwei.moneymanager.work.NewsWorker.schedule(ctx, replace = true)
                    }
                }

                SectionHeader("股票")
                ConfigRow("手續費與月退") { nav.navigate("stockfees") }
                ConfigRow("股價提醒（保本停利、減碼、目標價）", onOff(s.stockAlertsOn)) {
                    askOnOff("股價提醒", s.stockAlertsOn, "台股交易時間每 15 分鐘檢查：跌破停利價或減碼價、跌破 60 分 K 均線、多賺的股數增加、達到目標價或停損價、持股即將除權息時通知。") { v ->
                        repo.settings.update { it.copy(stockAlertsOn = v) }
                    }
                }
                ConfigRow("觀察清單", "${s.watchlist.size} 檔") { nav.navigate("invest") }

                SectionHeader("安全")
                ConfigRow("密碼鎖", onOff(s.passcodeOn)) { nav.navigate("passcode") }
                SectionHeader("資料")
                ConfigRow("備份與還原（含 .mmbak）") { nav.navigate("backup") }

                SectionHeader("關於")
                ConfigRow("App 更新", if (s.updateAvailable > BuildConfig.VERSION_CODE) "有新版 2.0.${s.updateAvailable}" else BuildConfig.VERSION_NAME) { nav.navigate("settings") }
                ConfigRow("開啟時檢查新版本", onOff(s.notifyUpdates)) {
                    askOnOff("開啟時檢查新版本", s.notifyUpdates, "開啟 App 時有新版本會跳出更新視窗（可略過這一版），背景也會通知。") { v ->
                        repo.settings.update { it.copy(notifyUpdates = v, updateSkipped = 0) }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    choice?.let { c ->
        OptionDialog(c.title, c.options, c.selected, c.description, onDismiss = { choice = null }) { c.onPick(it) }
    }
}

/** 珊瑚紅底的密碼鍵盤。 */
@Composable
private fun PinPad(title: String, entered: Int, error: Boolean, onDigit: (Char) -> Unit, onBack: () -> Unit, onBio: (() -> Unit)? = null) {
    val coral = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxSize().background(coral).statusBarsPadding().padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(80.dp))
        Icon(androidx.compose.ui.res.painterResource(com.enwei.moneymanager.R.drawable.ic_stat_ledger), null, tint = Color.White, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(40.dp))
        Text(if (error) "密碼錯誤，請再試一次" else title, color = Color.White, fontSize = 18.sp)
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
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(if (onBio != null) "bio" else "", "0", "⌫")).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { k ->
                    Box(
                        Modifier.weight(1f).height(84.dp).clickable(enabled = k.isNotEmpty()) {
                            when (k) { "⌫" -> onBack(); "bio" -> onBio?.invoke(); else -> onDigit(k[0]) }
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k == "⌫") Icon(Icons.AutoMirrored.Outlined.Backspace, "刪除", tint = Color.White, modifier = Modifier.size(32.dp))
                        else if (k == "bio") Icon(androidx.compose.material.icons.Icons.Rounded.Fingerprint, "生物辨識", tint = Color.White, modifier = Modifier.size(34.dp))
                        else Text(k, color = Color.White, fontSize = 30.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun LockScreen(hash: String, title: String = "請輸入密碼", biometric: Boolean = false, onUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    if (biometric) androidx.compose.runtime.LaunchedEffect(Unit) { promptBiometric(ctx, onUnlock) }
    PinPad(title, pin.length, error, onDigit = { d ->
        if (pin.length >= 4) return@PinPad
        pin += d
        error = false
        if (pin.length == 4) {
            if (hashPasscode(pin) == hash) onUnlock() else { error = true; pin = "" }
        }
    }, onBack = { pin = pin.dropLast(1) }, onBio = if (biometric) ({ promptBiometric(ctx, onUnlock) }) else null)
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
    fun setCap(id: Long, minor: Long?) {
        repo.settings.update { st -> st.copy(budgets = if (minor == null || minor <= 0) st.budgets - id else st.budgets + (id to minor)) }
    }
    // 這個月的實際支出（每項預算的使用進度）
    val all by repo.transactions.collectAsStateWithLifecycle(emptyList())
    var month by remember { mutableStateOf<com.enwei.moneymanager.data.ReviewData?>(null) }
    LaunchedEffect(all) { month = runCatching { repo.spendingReview(com.enwei.moneymanager.data.ReviewSpan.MONTH, 0) }.getOrNull() }
    // 建議值：前三個月的平均月支出
    var suggest by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    LaunchedEffect(Unit) { suggest = runCatching { repo.budgetSuggestions() }.getOrDefault(emptyMap()) }
    fun used(id: Long): Long? = month?.let { d -> if (id == 0L) d.expense else d.categories.find { it.id == id }?.amount ?: 0L }
    var editing by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var editGoal by remember { mutableStateOf(false) }
    val gray = MaterialTheme.colorScheme.onSurfaceVariant

    // 一列：名稱＋已用／預算、進度條、剩餘與平均（和「檢討」頁的目標卡片同樣排法）；點一下修改
    @Composable
    fun BudgetLine(id: Long, name: String) {
        val cap = s.budgets[id]
        val u = used(id)
        val avg = suggest[id]
        Column(Modifier.fillMaxWidth().clickable { editing = id to name }.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontSize = 15.sp, modifier = Modifier.weight(1f))
                if (cap != null) Text("${Money.full(u ?: 0)} / ${Money.full(cap)}", fontSize = 14.sp)
                else Text("未設定", fontSize = 13.sp, color = gray)
            }
            if (cap != null) {
                val pct = (u ?: 0).toFloat() / cap.coerceAtLeast(1)
                val col = when { pct >= 1f -> MaterialTheme.colorScheme.error; pct >= 0.8f -> Color(0xFFFFA000); else -> MaterialTheme.colorScheme.primary }
                Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth(pct.coerceIn(0f, 1f)).height(6.dp).background(col, RoundedCornerShape(3.dp)))
                }
                Row {
                    val left = cap - (u ?: 0)
                    Text(if (left >= 0) "剩餘 ${Money.full(left)}" else "超出 ${Money.full(-left)}", fontSize = 12.sp,
                        color = if (left >= 0) gray else MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    avg?.let { Text("近三月平均 ${Money.full(it)}", fontSize = 12.sp, color = gray) }
                }
            } else avg?.let { Text("近三月平均 ${Money.full(it)}", fontSize = 12.sp, color = gray) }
        }
    }

    val cats = lk.categories.filter { it.kind == TxnType.EXPENSE && !it.archived && it.parentId == null }
    val setCats = cats.filter { s.budgets.containsKey(it.id) }
    val unsetCats = cats.filter { !s.budgets.containsKey(it.id) }.sortedByDescending { suggest[it.id] ?: 0L }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackTopBar("預算設定", { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("點一下修改。進度也會顯示在「交易 → 檢討」；AI 檢討建議的目標按「採用」會寫到這裡。", color = gray, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        // 儲蓄率目標
                        Row(Modifier.fillMaxWidth().clickable { editGoal = true }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("儲蓄率目標", fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Text(s.savingsGoal?.let { "%.0f%%".format(it) } ?: "未設定", fontSize = 14.sp, color = if (s.savingsGoal == null) gray else MaterialTheme.colorScheme.onSurface)
                        }
                        BudgetLine(0L, "總預算")
                    }
                }
            }
            if (setCats.isNotEmpty()) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text("分類預算", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        setCats.forEach { BudgetLine(it.id, it.name) }
                    }
                }
            }
            if (unsetCats.isNotEmpty()) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("還沒設定（${unsetCats.size}）", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (suggest.isNotEmpty()) TextButton(onClick = {
                                // 只填還沒設定、平均 500 元以上的分類
                                unsetCats.forEach { c -> suggest[c.id]?.takeIf { it >= 50_000 }?.let { setCap(c.id, it) } }
                                if (!s.budgets.containsKey(0L)) suggest[0L]?.let { setCap(0L, it) }
                            }) { Text("全部帶入平均", fontSize = 13.sp) }
                        }
                        unsetCats.forEach { BudgetLine(it.id, it.name) }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    editing?.let { (id, name) ->
        var text by remember(id) { mutableStateOf(s.budgets[id]?.let { Money.fromMinor(it).stripTrailingZeros().toPlainString() }.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("$name 預算") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("每月金額（留空＝不設定）") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth())
                    suggest[id]?.let { v ->
                        TextButton(onClick = { text = Money.fromMinor(v).stripTrailingZeros().toPlainString() }) { Text("帶入近三月平均 ${Money.full(v)}") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { setCap(id, Money.parseMinor(text)); editing = null }) { Text("儲存") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } },
        )
    }
    if (editGoal) {
        var text by remember { mutableStateOf(s.savingsGoal?.let { "%.0f".format(it) }.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editGoal = false },
            title = { Text("儲蓄率目標") },
            text = {
                OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("%（留空＝不設定）") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())
            },
            confirmButton = { TextButton(onClick = { repo.settings.update { it.copy(savingsGoal = text.toDoubleOrNull()?.takeIf { g -> g > 0 }) }; editGoal = false }) { Text("儲存") } },
            dismissButton = { TextButton(onClick = { editGoal = false }) { Text("取消") } },
        )
    }
}

private tailrec fun android.content.Context.findFragmentActivity(): androidx.fragment.app.FragmentActivity? = when (this) {
    is androidx.fragment.app.FragmentActivity -> this
    is android.content.ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

private const val BIO_AUTH = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK

fun biometricAvailable(ctx: android.content.Context): Boolean =
    androidx.biometric.BiometricManager.from(ctx).canAuthenticate(BIO_AUTH) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS

/** 指紋／臉部解鎖；裝置不支援或使用者取消時什麼都不做（改用密碼）。 */
fun promptBiometric(ctx: android.content.Context, onSuccess: () -> Unit) {
    val activity = ctx.findFragmentActivity() ?: return
    if (!biometricAvailable(activity)) return
    val prompt = androidx.biometric.BiometricPrompt(
        activity, androidx.core.content.ContextCompat.getMainExecutor(activity),
        object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) = onSuccess()
        },
    )
    prompt.authenticate(
        androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle("解鎖帳理")
            .setNegativeButtonText("使用密碼")
            .setAllowedAuthenticators(BIO_AUTH)
            .build(),
    )
}

/** 密碼鎖設定：開啟／關閉、變更密碼、生物辨識、離開多久後要求密碼。 */
@Composable
fun PasscodeScreen(nav: NavController) {
    val ctx = LocalContext.current
    val s by repo.settings.flow.collectAsStateWithLifecycle()
    var setup by remember { mutableStateOf(false) }
    var verifyFor by remember { mutableStateOf<String?>(null) }
    var pickDelay by remember { mutableStateOf(false) }
    val bioOk = remember { biometricAvailable(ctx) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackTopBar("密碼鎖", { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad)) {
            if (!s.passcodeOn) {
                ConfigRow("開啟密碼鎖") { setup = true }
            } else {
                ConfigRow("關閉密碼鎖") { verifyFor = "off" }
                ConfigRow("變更密碼") { verifyFor = "change" }
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                        .clickable(enabled = bioOk) { repo.settings.update { it.copy(biometric = !it.biometric) } }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("生物辨識（指紋／臉部）", fontSize = 14.sp)
                        if (!bioOk) Text("此裝置尚未設定生物辨識", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.Switch(s.biometric && bioOk, { v -> repo.settings.update { it.copy(biometric = v) } }, enabled = bioOk)
                }
                SectionHeader("要求密碼")
                ConfigRow("離開 App 後", lockDelayLabel(s.lockDelaySec)) { pickDelay = true }
            }
        }
    }
    if (setup) PasscodeSetupDialog(onDone = { hash ->
        repo.settings.update { it.copy(passcodeHash = hash) }
        setup = false
    }, onCancel = { setup = false })
    verifyFor?.let { purpose ->
        Dialog(onDismissRequest = { verifyFor = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            LockScreen(s.passcodeHash, title = "輸入目前密碼", biometric = false) {
                if (purpose == "off") repo.settings.update { it.copy(passcodeHash = "", biometric = false) } else setup = true
                verifyFor = null
            }
        }
    }
    if (pickDelay) OptionDialog(
        "要求密碼", listOf(0, 30, 60, 300, 900).map { it to lockDelayLabel(it) }, s.lockDelaySec,
        "離開 App 超過這段時間再回來，就需要輸入密碼。", onDismiss = { pickDelay = false },
    ) { v -> repo.settings.update { it.copy(lockDelaySec = v) } }
}
