package com.enwei.moneymanager.ui.screens

import com.enwei.moneymanager.ui.AppText

import com.enwei.moneymanager.data.hasAiKey
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
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
        title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = AppText.body,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ConfigRow(title: String, value: String? = null, sub: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = AppText.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (value != null) Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = AppText.secondary, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
        Text("›", color = MaterialTheme.colorScheme.outline, fontSize = AppText.title, modifier = Modifier.padding(start = 8.dp))
    }
    androidx.compose.material3.HorizontalDivider(Modifier.background(MaterialTheme.colorScheme.surface).padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

private data class Choice<T>(val title: String, val options: List<Pair<T, String>>, val selected: T?, val description: String?, val onPick: (T) -> Unit,
    val preview: (@Composable (T) -> Unit)? = null)

/** 設定選項的小預覽：用簡化的樣子示範選了會長怎樣。 */
@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background, shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(10.dp)) { content() }
}

@Composable
private fun CalendarMarkPreview(dots: Boolean) = PreviewFrame {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(Triple(7, 2095L, 376L), Triple(8, 0L, 216L), Triple(9, 0L, 0L)).forEach { (d, inc, exp) ->
            Column(Modifier.weight(1f).border(0.6.dp, MaterialTheme.colorScheme.outlineVariant).padding(4.dp)) {
                Text("$d", fontSize = AppText.caption)
                if (dots) Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (inc > 0) Box(Modifier.size(6.dp).background(com.enwei.moneymanager.ui.IncomeColor, CircleShape))
                    if (exp > 0) Box(Modifier.size(6.dp).background(com.enwei.moneymanager.ui.ExpenseColor, CircleShape))
                } else {
                    if (inc > 0) Text("$inc", fontSize = AppText.badge, color = com.enwei.moneymanager.ui.IncomeColor)
                    if (exp > 0) Text("$exp", fontSize = AppText.badge, color = com.enwei.moneymanager.ui.ExpenseColor)
                }
                if (inc == 0L && exp == 0L) Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun FilterStylePreview(fields: Boolean) = PreviewFrame {
    if (!fields) Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).border(7.dp, MaterialTheme.colorScheme.primary, CircleShape))
        Column(Modifier.padding(start = 12.dp)) {
            listOf("☑ 飲食 42%", "☑ 交通 18%", "☐ 購物 9%").forEach { Text(it, fontSize = AppText.caption) }
        }
    } else Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("分類 ▾", "帳戶 ▾", "期間 ▾").forEach {
            Text(it, fontSize = AppText.caption, modifier = Modifier.weight(1f).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)).padding(6.dp))
        }
    }
}

@Composable
private fun TextLinePreview(text: String) = PreviewFrame { Text(text, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant) }

private fun lockDelayLabel(sec: Int) = when (sec) {
    0 -> "立即"
    in 1..59 -> "$sec 秒後"
    else -> "${sec / 60} 分鐘後"
}

/** 設定：依用途分組管理帳戶、記帳、外觀、自動化、AI、安全與備份更新。 */
/** 設定的大分類：首頁只列這幾張卡，點進去才看該類的設定，不必在一長頁裡找。 */
private data class CfgGroup(
    val id: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val sections: List<String>,
    /** 只有一個頁面的分類：直接打開那一頁 */
    val route: String? = null,
    val visible: (com.enwei.moneymanager.data.AppSettings) -> Boolean = { true },
)

private val cfgGroups = listOf(
    CfgGroup("accounts", "帳戶與分類", androidx.compose.material.icons.Icons.Outlined.AccountBalanceWallet, listOf("帳戶", "分類與預算")),
    CfgGroup("entry", "記帳", androidx.compose.material.icons.Icons.Outlined.EditNote, listOf("記帳", "期間", "幣別")),
    CfgGroup("display", "外觀與顯示", androidx.compose.material.icons.Icons.Outlined.Palette, listOf("外觀", "期間與顯示", "列表")),
    CfgGroup("features", "功能開關", androidx.compose.material.icons.Icons.Outlined.ToggleOn, listOf("功能開關")),
    CfgGroup("autorecord", "自動記帳", androidx.compose.material.icons.Icons.Outlined.Sms, listOf("自動記帳"), route = "notif", visible = { it.featureNotif }),
    CfgGroup("ai", "AI", androidx.compose.material.icons.Icons.Outlined.AutoAwesome, listOf("AI"), visible = { it.aiEnabled }),
    CfgGroup("stocks", "股票", androidx.compose.material.icons.Icons.Outlined.ShowChart, listOf("股票"), visible = { it.featureStocks }),
    CfgGroup("notify", "通知", androidx.compose.material.icons.Icons.Outlined.Notifications, listOf("通知"), route = "notices"),
    CfgGroup("data", "安全與資料", androidx.compose.material.icons.Icons.Outlined.Lock, listOf("安全", "資料")),
    CfgGroup("update", "App 更新", androidx.compose.material.icons.Icons.Outlined.SystemUpdate, listOf("關於"), route = "settings"),
)

@Composable
fun ConfigScreen(nav: NavController, group: String? = null) {
    val grp = cfgGroups.firstOrNull { it.id == group }
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    // 目前畫到哪一節（sec 設定、cfg 依此決定要不要顯示）
    var cur = ""
    val searching = grp == null && q.isNotEmpty()
    @androidx.compose.runtime.NonSkippableComposable @Composable fun sec(title: String) {
        cur = title
        if (grp != null && title in grp.sections && grp.sections.size > 1) SectionHeader(title)
    }
    @androidx.compose.runtime.NonSkippableComposable @Composable fun cfg(title: String, value: String? = null, onClick: () -> Unit) {
        when {
            grp != null -> if (cur in grp.sections) ConfigRow(title, value, onClick = onClick)
            searching -> if (title.contains(q, true) || com.enwei.moneymanager.ui.tr(title).contains(q, true) || cur.contains(q, true))
                ConfigRow(title, value, sub = cur, onClick = onClick)
        }
    }
    val ctx = LocalContext.current
    val s by repo.settings.flow.collectAsStateWithLifecycle()
    var choice by remember { mutableStateOf<Choice<Any>?>(null) }

    @Suppress("UNCHECKED_CAST")
    fun <T> ask(title: String, options: List<Pair<T, String>>, selected: T?, description: String? = null,
        preview: (@Composable (T) -> Unit)? = null, onPick: (T) -> Unit) {
        choice = Choice(title, options, selected, description, onPick, preview) as Choice<Any>
    }
    fun onOff(b: Boolean) = if (b) "ON" else "OFF"
    fun askOnOff(title: String, value: Boolean, description: String?, onPick: (Boolean) -> Unit) =
        ask(title, listOf(false to "OFF", true to "ON"), value, description, onPick)
    val currencies = (listOf("TWD") + FxService.common).distinct()

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackTopBar(grp?.title ?: "設定", { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            // 設定首頁：搜尋＋六個大分類卡片（一個畫面看完）
            if (grp == null) item {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("搜尋設定") },
                        leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) androidx.compose.material3.IconButton(onClick = { query = "" }) {
                                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Close, "清除")
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                    )
                    if (!searching) cfgGroups.filter { it.visible(s) }.chunked(2).forEach { rowGroups ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowGroups.forEach { g ->
                                // 分類卡：淡色圓形圖示＋名稱＋一行說明
                                val desc = when (g.id) {
                                    "accounts" -> "帳戶、分類、預算"; "entry" -> "記帳方式、期間、幣別"; "display" -> "主題、字體、列表"
                                    "features" -> "開關各項功能"; "autorecord" -> "通知、錢包畫面"; "ai" -> "金鑰、模型、語氣"
                                    "stocks" -> "手續費、紀律"; "notify" -> "七種通知"; "data" -> "密碼、備份還原"; else -> "版本與更新"
                                }
                                com.enwei.moneymanager.ui.components.AppCard(Modifier.weight(1f).height(72.dp).clickable { nav.navigate(g.route ?: "config?g=${g.id}") }) {
                                    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        androidx.compose.foundation.layout.Box(
                                            Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), androidx.compose.foundation.shape.CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) { androidx.compose.material3.Icon(g.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                                        Column(Modifier.padding(start = 10.dp)) {
                                            Text(g.title, fontSize = AppText.body, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(desc, fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                            if (rowGroups.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (!searching) Text("版本 ${BuildConfig.VERSION_NAME}", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            if (grp != null || searching) item {
                sec("帳戶")
                cfg("帳戶設定") { nav.navigate("accountsettings") }
                cfg("帳戶群組") { nav.navigate("accountgroups") }

                sec("分類與預算")
                cfg("支出分類設定") { nav.navigate("categories?kind=EXPENSE") }
                cfg("收入分類設定") { nav.navigate("categories?kind=INCOME") }
                cfg("預算設定") { nav.navigate("budget") }

                sec("記帳")
                cfg("輸入順序", if (s.inputFromCategory) "從分類" else "從金額") {
                    ask("輸入順序", listOf(false to "從金額", true to "從分類"), s.inputFromCategory,
                        preview = { v -> TextLinePreview(if (v) "按＋ → 先選分類 → 再輸入金額" else "按＋ → 先輸入金額 → 再選分類") }) { v ->
                        repo.settings.update { it.copy(inputFromCategory = v) }
                    }
                }
                cfg("時間輸入", onOff(s.showTime)) {
                    askOnOff("時間輸入", s.showTime, null) { v -> repo.settings.update { it.copy(showTime = v) } }
                }
                cfg("週期交易", null) { nav.navigate("recurring") }
                cfg("自動完成", onOff(s.autocomplete)) {
                    askOnOff("自動完成", s.autocomplete, null) { v -> repo.settings.update { it.copy(autocomplete = v) } }
                }
                sec("期間與顯示")
                cfg("日曆標示", if (s.calendarDots) "顏色小點" else "文字") {
                    ask("日曆標示", listOf(false to "文字", true to "顏色小點"), s.calendarDots, preview = { v -> CalendarMarkPreview(v) }) { v -> repo.settings.update { it.copy(calendarDots = v) } }
                }
                cfg("搜尋的篩選方式", if (s.filterStyle == 0) "圖表勾選" else "欄位篩選") {
                    ask("搜尋的篩選方式", listOf(0 to "圖表勾選", 1 to "欄位篩選"), s.filterStyle, preview = { v -> FilterStylePreview(v == 1) }) { v ->
                        repo.settings.update { it.copy(filterStyle = v) }
                    }
                }
                sec("期間")
                cfg("每月起始日", "每月 ${s.monthStartDay} 日") {
                    ask("每月起始日", (1..28).map { it to "每月 $it 日" }, s.monthStartDay) { v ->
                        repo.settings.update { it.copy(monthStartDay = v) }
                    }
                }
                cfg("每週起始日", if (s.weekStartDay == 7) "週日" else "週一") {
                    ask("每週起始日", listOf(7 to "週日", 1 to "週一"), s.weekStartDay) { v -> repo.settings.update { it.copy(weekStartDay = v) } }
                }
                sec("列表")
                cfg("滑動切換", if (s.swipe == 1) "切換期間" else "無") {
                    ask("滑動切換", listOf(0 to "無", 1 to "切換期間"), s.swipe,
                        preview = { v -> TextLinePreview(if (v == 1) "在交易列表左右滑動 → 切到上個月／下個月" else "左右滑動不做任何事，只能按標題的箭頭換月") }) { v -> repo.settings.update { it.copy(swipe = v) } }
                }

                sec("幣別")
                cfg("主幣別", "${s.baseCurrency} (${Money.symbolOf(s.baseCurrency).trim()})") {
                    ask("主幣別", currencies.map { it to it }, s.baseCurrency) { c -> repo.settings.update { it.copy(baseCurrency = c) } }
                }
                cfg("副幣別", Money.symbolOf(s.subCurrency).trim()) {
                    ask("副幣別", currencies.map { it to it }, s.subCurrency) { c -> repo.settings.update { it.copy(subCurrency = c) } }
                }
                cfg("即時匯率") { nav.navigate("fx") }

                sec("外觀")
                cfg("主題、字體與版面", com.enwei.moneymanager.ui.Themes.of(s.themePreset).label + "・" + com.enwei.moneymanager.ui.Themes.fonts.getOrElse(s.fontChoice) { "系統字體" }) { nav.navigate("appearance") }
                cfg("語言", listOf("跟隨系統", "繁體中文", "English")[s.language.coerceIn(0, 2)]) {
                    ask("語言", listOf(0 to "跟隨系統", 1 to "繁體中文", 2 to "English"), s.language) { v ->
                        repo.settings.update { it.copy(language = v) }
                        com.enwei.moneymanager.ui.I18n.init(ctx, v)
                        com.enwei.moneymanager.ui.applyAppLocale(ctx, v)
                    }
                }
                sec("功能開關")
                cfg("通知自動記帳", onOff(s.featureNotif)) {
                    askOnOff("通知自動記帳", s.featureNotif, null) { v -> repo.settings.update { it.copy(featureNotif = v) } }
                }
                cfg("股票與投資助理", onOff(s.featureStocks)) {
                    askOnOff("股票與投資助理", s.featureStocks, null) { v ->
                        repo.settings.update { it.copy(featureStocks = v) }
                    }
                }
                cfg("AI 功能", onOff(s.aiEnabled)) {
                    askOnOff("AI 功能", s.aiEnabled, null) { v ->
                        repo.settings.update { it.copy(aiEnabled = v) }
                        if (v && !s.hasAiKey) nav.navigate("ai")
                    }
                }

                if (s.featureNotif) {
                    sec("自動記帳")
                    cfg("通知自動記帳設定") { nav.navigate("notif") }
                }

                if (s.aiEnabled) {
                sec("AI")
                cfg("AI 模型設定", if (s.hasAiKey) "已設定金鑰" else "需要設定金鑰") { nav.navigate("ai") }
                // AI 說話的口吻：收支檢討、投資報告、股票助理都會照這個語氣
                cfg("AI 語氣", com.enwei.moneymanager.net.GroqAi.AiTone.entries.getOrElse(s.aiTone) { com.enwei.moneymanager.net.GroqAi.AiTone.GENTLE }.label) {
                    ask(
                        "AI 語氣", com.enwei.moneymanager.net.GroqAi.AiTone.entries.mapIndexed { i, t -> i to "${t.label}：${t.desc}" }, s.aiTone,
                    ) { v -> repo.settings.update { it.copy(aiTone = v) } }
                }
                cfg("背景 AI 只用 Wi-Fi", onOff(s.aiWifiOnly)) {
                    askOnOff("背景 AI 只用 Wi-Fi", s.aiWifiOnly, null) { v ->
                        repo.settings.update { it.copy(aiWifiOnly = v) }
                        com.enwei.moneymanager.work.NewsWorker.schedule(ctx, replace = true)
                    }
                }
                }

                if (s.featureStocks) {
                sec("股票")
                cfg("手續費與月退") { nav.navigate("stockfees") }
                cfg("觀察清單", "${s.watchlist.size} 檔") { nav.navigate("invest") }
                }

                // 通知：App 會發的每一種通知集中在這裡開關（股價提醒也搬到這裡）
                sec("通知")
                run {
                    val kinds = com.enwei.moneymanager.notify.NoticeKind.entries.filter { it.available(s) }
                    val on = kinds.count { it.enabled(s) }
                    cfg("通知類別", "$on / ${kinds.size}") { nav.navigate("notices") }
                }

                sec("安全")
                cfg("密碼鎖", onOff(s.passcodeOn)) { nav.navigate("passcode") }
                sec("資料")
                cfg("備份與還原") { nav.navigate("backup") }

                sec("關於")
                cfg("新手導覽") { com.enwei.moneymanager.ui.components.Onboarding.show.value = true }
                cfg("App 更新", if (s.updateAvailable > BuildConfig.VERSION_CODE) "有新版 2.1.${s.updateAvailable}" else BuildConfig.VERSION_NAME) { nav.navigate("settings") }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    choice?.let { c ->
        OptionDialog(c.title, c.options, c.selected, c.description, onDismiss = { choice = null }, preview = c.preview) { c.onPick(it) }
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
        Text(if (error) "密碼錯誤，請再試一次" else title, color = Color.White, fontSize = AppText.title)
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
                        else Text(k, color = Color.White, fontSize = AppText.display)
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
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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

    val cats = lk.categories.filter { it.kind == TxnType.EXPENSE && !it.archived && it.parentId == null }
    val setCats = cats.filter { s.budgets.containsKey(it.id) }
        .sortedByDescending { c -> (used(c.id) ?: 0L).toDouble() / (s.budgets[c.id] ?: 1L).coerceAtLeast(1) }
    val unsetCats = cats.filter { !s.budgets.containsKey(it.id) }.sortedByDescending { suggest[it.id] ?: 0L }
    val today = java.time.LocalDate.now()
    val daysLeft = month?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it.end) + 1 }?.takeIf { it > 0 }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackTopBar("預算設定", { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ① 總預算＋儲蓄率目標（點一下修改）
            item {
                com.enwei.moneymanager.ui.components.AppCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val total = s.budgets[0L]
                        Box(Modifier.clickable { editing = 0L to "總預算" }) {
                            if (total != null) com.enwei.moneymanager.ui.components.BudgetMeter("總預算", used(0L) ?: 0L, total, Modifier.fillMaxWidth(), daysLeft = daysLeft)
                            else Row(Modifier.fillMaxWidth()) {
                                Text("總預算", fontSize = AppText.body, modifier = Modifier.weight(1f))
                                Text("未設定", fontSize = AppText.body, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Row(Modifier.fillMaxWidth().clickable { editGoal = true }, verticalAlignment = Alignment.CenterVertically) {
                            Text("儲蓄率目標", fontSize = AppText.body, modifier = Modifier.weight(1f))
                            Text(s.savingsGoal?.let { "%.0f%%".format(it) } ?: "未設定", fontSize = AppText.body, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            // ② 分類預算：每個一行（點一下修改）
            if (setCats.isNotEmpty()) item {
                com.enwei.moneymanager.ui.components.AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("分類預算", fontSize = AppText.section, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("合計 ${Money.format(setCats.sumOf { s.budgets[it.id] ?: 0L })}", fontSize = AppText.caption, color = gray)
                        }
                        setCats.forEach { c ->
                            com.enwei.moneymanager.ui.components.BudgetRowCompact(c.name, used(c.id) ?: 0L, s.budgets[c.id] ?: 0L, Modifier.fillMaxWidth()) {
                                editing = c.id to c.name
                            }
                        }
                    }
                }
            }
            // ③ 還沒設定的分類：小標籤，點一下設定
            if (unsetCats.isNotEmpty()) item {
                com.enwei.moneymanager.ui.components.AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("新增分類預算", fontSize = AppText.section, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (suggest.isNotEmpty()) TextButton(onClick = {
                                // 只填還沒設定、平均 500 元以上的分類
                                unsetCats.forEach { c -> suggest[c.id]?.takeIf { it >= 50_000 }?.let { setCap(c.id, it) } }
                                if (!s.budgets.containsKey(0L)) suggest[0L]?.let { setCap(0L, it) }
                            }) { Text("全部帶入平均", fontSize = AppText.secondary) }
                        }
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            unsetCats.forEach { c ->
                                androidx.compose.material3.AssistChip(onClick = { editing = c.id to c.name }, label = {
                                    Text("＋ " + c.name + (suggest[c.id]?.let { " ${Money.format(it)}" } ?: ""), fontSize = AppText.caption)
                                })
                            }
                        }
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
                        Text("生物辨識（指紋／臉部）", fontSize = AppText.body)
                        if (!bioOk) Text("此裝置尚未設定生物辨識", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
