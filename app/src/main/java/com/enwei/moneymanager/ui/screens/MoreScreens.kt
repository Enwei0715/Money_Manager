package com.enwei.moneymanager.ui.screens

import com.enwei.moneymanager.ui.components.coachTarget
import com.enwei.moneymanager.ui.components.listRow
import com.enwei.moneymanager.ui.AppText

import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Toll
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import com.enwei.moneymanager.ui.components.Text
import com.enwei.moneymanager.data.Repository
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.BuildConfig
import com.enwei.moneymanager.core.Money
import kotlinx.coroutines.flow.first
import com.enwei.moneymanager.core.NotificationParser
import com.enwei.moneymanager.data.CategoryEntity
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.net.FxService
import com.enwei.moneymanager.net.ReleaseInfo
import com.enwei.moneymanager.net.Updater
import com.enwei.moneymanager.notify.TxnNotificationListener
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.DropdownField
import com.enwei.moneymanager.ui.components.NumberField
import com.enwei.moneymanager.ui.components.SectionTitle
import com.enwei.moneymanager.work.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFmt = SimpleDateFormat("M/d HH:mm", Locale.TAIWAN)

@Composable
fun MoreScreen(nav: NavController) {
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    // 設定裡關掉的功能，對應的工具也不顯示
    val off = settings.offTools()
    val shown = ToolCatalog.shown(settings.toolOrder, settings.hiddenTools).filter { it.id !in off }
    val available = ToolCatalog.available(settings.toolOrder, settings.hiddenTools).filter { it.id !in off }
    // 編輯模式：長按任一格或按右上「編輯」進入；可以移動、切換寬窄、隱藏、從清單加入
    var editing by remember { mutableStateOf(false) }
    val summary = rememberToolSummaries()
    // 關掉的功能的工具保留在順序裡，重新打開功能時回到原位
    fun setOrder(ids: List<String>) = repo.settings.update { it.copy(toolOrder = (ids + it.toolOrder.filter { t -> t in off }).distinct(), hiddenTools = it.hiddenTools - ids.toSet()) }
    fun move(id: String, by: Int) {
        val ids = shown.map { it.id }.toMutableList()
        val i = ids.indexOf(id); val j = (i + by).coerceIn(0, ids.lastIndex)
        if (i < 0 || i == j) return
        ids.add(j, ids.removeAt(i)); setOrder(ids)
    }
    // 編輯中按返回：先結束編輯，不直接離開頁面
    androidx.activity.compose.BackHandler(enabled = editing) { editing = false }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            androidx.compose.material3.TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                title = { Text("工具") },
                actions = {
                    TextButton(onClick = { editing = !editing }) { Text(if (editing) "完成" else "編輯") }
                    IconButton(onClick = { nav.navigate("config") }, modifier = Modifier.coachTarget("more.settings")) { Icon(Icons.Outlined.Settings, "設定") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (shown.isEmpty()) Text("還沒有工具", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(14.dp))
            if (editing) Text("長按工具後拖曳可移動，拖曳右下角可改大小；上下滑動可捲動頁面", fontSize = AppText.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            // 三欄網格：每格可以是 1×1、2×1、3×1、1×2、2×2；編輯時拖動移動、拖角落改大小
            ToolGrid(
                shown,
                shown.associate { t -> t.id to (parseSize(settings.toolSizes[t.id]) ?: if (t.id in settings.wideTools) 2 to 1 else 1 to 1) },
                settings.toolPos.mapNotNull { (k, v) -> v.split(',').takeIf { it.size == 2 }?.let { a -> a[0].toIntOrNull()?.let { r -> a[1].toIntOrNull()?.let { c -> k to (r to c) } } } }.toMap(),
                summary, editing,
                // 股票助理、收支檢討這類其實是分頁的工具：切到那個分頁，不另外疊一層（底部分頁列不會消失）
                onOpen = { t -> com.enwei.moneymanager.ui.openRouteFrom(nav, t.route) },
                onLongPress = { editing = true },
                onMove = { pos -> repo.settings.update { st -> st.copy(toolPos = pos.mapValues { "${it.value.first},${it.value.second}" }) } },
                onResize = { id, w, h, pos -> repo.settings.update { st -> st.copy(toolSizes = st.toolSizes + (id to "${w}x$h"),
                    toolPos = pos.mapValues { "${it.value.first},${it.value.second}" }) } },
                onRemove = { id -> setOrder(shown.map { it.id } - id) },
            )
            // 可加入的工具：編輯時才顯示
            if (editing && available.isNotEmpty()) {
                Text("可加入的工具", fontSize = AppText.body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 12.dp))
                available.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().listRow(MaterialTheme.colorScheme.surface)
                            .clickable { setOrder(shown.map { it.id } + t.id) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(t.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        Text(t.label, fontSize = AppText.body, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        Text("＋ 加入", fontSize = AppText.body, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** 一格工具：小格是圖示＋名稱；寬格多一行摘要。編輯時下方出現移動、寬窄、隱藏按鈕。 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ToolTile(
    t: ToolItem, wide: Boolean, summary: String?, editing: Boolean, modifier: Modifier,
    onOpen: () -> Unit, onLongPress: () -> Unit, onLeft: () -> Unit, onRight: () -> Unit, onToggleWide: () -> Unit, onHide: () -> Unit,
) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(com.enwei.moneymanager.ui.AppearancePrefs.cardRadius), color = MaterialTheme.colorScheme.surface,
        border = if (editing) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null,
        modifier = modifier.heightIn(min = 96.dp).combinedClickable(onClick = onOpen, onLongClick = onLongPress),
    ) {
        // 內容上下、左右置中（寬格的圖示＋名稱＋摘要整組置中）
        Column(
            Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(t.icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), modifier = Modifier.size(30.dp))
                Column(Modifier.padding(start = 10.dp).weight(1f, fill = false)) {
                    Text(t.label, fontSize = AppText.body, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1)
                    if (!summary.isNullOrBlank()) Text(summary, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            } else Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(t.icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text(t.label, fontSize = AppText.body, lineHeight = 19.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            if (editing) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("◀" to onLeft, (if (wide) "窄" else "寬") to onToggleWide, "▶" to onRight, "✕" to onHide).forEach { (l, f) ->
                    Text(l, fontSize = AppText.secondary, color = if (l == "✕") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = f).padding(horizontal = 4.dp, vertical = 2.dp))
                }
            }
        }
    }
}

/** 各工具寬格顯示的摘要（待確認筆數、下一筆週期交易、匯率、上次備份、AI 狀態…）。 */
@Composable
private fun rememberToolSummaries(): Map<String, String> {
    val lk = rememberLookups()
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val pending by repo.pending.collectAsStateWithLifecycle(emptyList())
    val recurring by repo.recurring.collectAsStateWithLifecycle(emptyList())
    val balances by repo.balances.collectAsStateWithLifecycle(emptyList())
    val txns by repo.transactions.collectAsStateWithLifecycle(emptyList())
    val report by com.enwei.moneymanager.work.ReportJob.state.collectAsStateWithLifecycle()
    val news by com.enwei.moneymanager.MoneyApp.news.flow.collectAsStateWithLifecycle()
    val fmt = SimpleDateFormat("M/d HH:mm", Locale.TAIWAN)
    val today = java.time.LocalDate.now()
    val monthRange = com.enwei.moneymanager.core.Periods.month(com.enwei.moneymanager.core.Periods.monthOf(today, settings.monthStartDay), settings.monthStartDay)
    val monthExpense = remember(txns, monthRange) {
        totalsOf(txns.filter { t ->
            val d = java.time.Instant.ofEpochMilli(t.date).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            !d.isBefore(monthRange.first) && !d.isAfter(monthRange.second)
        }, lk.fx).expense
    }
    val next = remember(recurring) {
        recurring.filter { it.enabled }.map { it to repo.nextOccurrence(it, today) }.minByOrNull { it.second }
    }
    return buildMap {
        put("pending", if (pending.isEmpty()) "沒有待確認的交易" else "${pending.size} 筆待確認")
        put("recurring", next?.let { (r, d) -> "下一筆 ${d.monthValue}/${d.dayOfMonth} ${r.name}" } ?: "沒有週期交易")
        if (settings.subCurrency != lk.base) put("fx", "1 ${settings.subCurrency} ≈ ${"%.2f".format(lk.fx.convert(1_000_000, settings.subCurrency) / 1_000_000.0)} ${lk.base}")
        put("backup", if (settings.lastAutoBackup > 0) "上次備份 ${fmt.format(Date(settings.lastAutoBackup))}" else "尚未設定自動備份")
        put("ai-hub", report?.takeIf { it.running }?.step ?: if (com.enwei.moneymanager.net.GroqAi.enabled) "AI 待命中" else "尚未設定 AI 金鑰")
        put("advisor", news?.report?.let { "報告 ${fmt.format(Date(it.createdAt))}" } ?: "還沒有報告")
        put("review", "本月支出 ${Money.withSymbol(monthExpense, lk.base)}")
        settings.budgets[0L]?.let { b -> put("budget", "已用 ${if (b > 0) monthExpense * 100 / b else 0}%・剩 ${Money.withSymbol((b - monthExpense).coerceAtLeast(0), lk.base).substringBefore(".")}") }
        put("totalstats", "淨資產 ${Money.withSymbol(balances.filter { it.account.includeInNetWorth && !it.account.archived }.sumOf { lk.fx.convert(it.balance, it.account.currency) }, lk.base)}")
        put("notif", "監聽 ${settings.notifPackages.size} 個 App")
        put("settings", if (settings.updateAvailable > BuildConfig.VERSION_CODE) "有新版可更新" else "目前 ${BuildConfig.VERSION_NAME}")
    }
}

/** 回到畫面時重新檢查（例如從系統設定開啟通知權限後返回）。 */
@Composable
private fun OnResume(block: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) block() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

private data class AppEntry(val pkg: String, val label: String)

private fun installedApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.pkg != ctx.packageName }
        .distinctBy { it.pkg }
        .sortedBy { it.label }
}

@Composable
fun NotificationSettingsScreen(nav: NavController) {
    // 第一次打開這頁：介紹三個分頁
    com.enwei.moneymanager.ui.components.PageTour("notif", listOf(
        com.enwei.moneymanager.ui.components.Coach.Step("nf.tab0", "紀錄", "列出讀取到的每則通知與處理結果（已記帳、待確認或略過）；辨識不正確時可「設定辨識方式」，下次即自動套用。"),
        com.enwei.moneymanager.ui.components.Coach.Step("nf.tab1", "來源 App", "指定要讀取的銀行、信用卡與支付 App；未勾選的 App 不會被讀取。"),
        com.enwei.moneymanager.ui.components.Coach.Step("nf.tab2", "進階", "地點測試、通知辨識測試與貼上通知測試；Android 15 被隱藏的通知內容，可在此依步驟開啟讀取。"),
    ))
    val ctx = LocalContext.current
    val lk = rememberLookups()
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val log by repo.notificationLog.collectAsStateWithLifecycle(emptyList())
    var enabled by remember { mutableStateOf(TxnNotificationListener.isEnabled(ctx)) }
    OnResume { enabled = TxnNotificationListener.isEnabled(ctx) }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.Default) { installedApps(ctx) } }
    var filter by remember { mutableStateOf("") }
    var showAllApps by remember { mutableStateOf(false) }
    var allLog by remember { mutableStateOf(false) }
    var testText by remember { mutableStateOf("") }
    var testTitle by remember { mutableStateOf("") }
    var testPkg by remember { mutableStateOf<String?>(null) }
    var pasted by remember { mutableStateOf<TxnNotificationListener.ActiveNote?>(null) }
    var pastedPreview by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val labelOf = remember(apps) { apps.associate { it.pkg to it.label } }
    val candidates = log.filter { it.result == "CANDIDATE" && it.packageName !in settings.notifPackages && it.packageName !in settings.notifIgnored }
        .map { it.packageName }.distinct()
    val testScope = rememberCoroutineScope()
    var notes by remember { mutableStateOf<List<TxnNotificationListener.ActiveNote>?>(null) }
    var previews by remember { mutableStateOf<List<Pair<Boolean, String>>>(emptyList()) }
    var testMsg by remember { mutableStateOf<String?>(null) }
    var simulationPreviews by remember { mutableStateOf<List<Pair<Boolean, String>>?>(null) }
    var teach by remember { mutableStateOf<TxnNotificationListener.ActiveNote?>(null) }
    // 三個分頁：紀錄（處理結果）、來源 App（監聽與帳戶）、進階（測試、教過的規則）
    var tab by com.enwei.moneymanager.ui.components.rememberTab("notif.tab")
    var logFilter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    // 交易已被刪除的紀錄要顯示成「已刪除」，可以重新記帳
    val txns by repo.transactions.collectAsStateWithLifecycle(emptyList())
    val txnIds = remember(txns) { txns.map { it.id }.toHashSet() }
    val txnById = remember(txns) { txns.associateBy { it.id } }
    fun loadNotes() = testScope.launch {
        val n = TxnNotificationListener.activeNotes()
        testMsg = if (n == null) "仍無法讀取通知列：請確認已開啟通知存取權（開啟後可能要稍等幾秒）" else if (n.isEmpty()) "通知列目前沒有通知" else null
        val list = n.orEmpty()
        val pv = repo.previewBatch(list.map { Repository.NotePreview(it.pkg, it.title, it.text, it.postedAt) })
        // 看得出是交易的排前面
        val order = list.indices.sortedBy { i -> if (pv[i].second.startsWith("看不出") || pv[i].second.startsWith("LINE 聊天")) 1 else 0 }
        notes = order.map { list[it] }
        previews = order.map { pv[it] }
    }

    fun toggle(pkg: String, on: Boolean) = repo.settings.update {
        it.copy(notifPackages = if (on) it.notifPackages + pkg else it.notifPackages - pkg)
    }

    @Composable
    fun NoteCard(nn: TxnNotificationListener.ActiveNote, ok: Boolean, desc: String) {
        val redacted = repo.isRedacted(nn.text)
        val parsedOk = !redacted && !desc.startsWith("看不出") && !desc.startsWith("LINE 聊天")
        com.enwei.moneymanager.ui.components.AppCard(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${labelOf[nn.pkg] ?: nn.pkg} · ${timeFmt.format(Date(nn.postedAt))}", style = MaterialTheme.typography.labelSmall)
                Text(listOfNotNull(nn.title, nn.text).joinToString("\n"), maxLines = 4, style = MaterialTheme.typography.bodySmall)
                com.enwei.moneymanager.ui.components.StatusLine(ok, if (ok) "會自動記帳" else if (parsedOk) "辨識得出，但不會自動記帳" else "不會記帳")
                Text(desc, style = MaterialTheme.typography.bodySmall)
                if (redacted) {
                    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        androidx.compose.material3.Text(
                            repo.SENSITIVE_ADB, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row {
                        // 不用電腦：用 Shizuku 一鍵開啟
                        TextButton(onClick = { nav.navigate("setup") }) { Text("一鍵開啟（無須電腦）") }
                        TextButton(onClick = {
                            clip.setText(androidx.compose.ui.text.AnnotatedString(repo.SENSITIVE_ADB))
                            testMsg = "已複製指令"
                        }) { Text("複製 adb 指令") }
                    }
                } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (parsedOk) OutlinedButton(onClick = {
                        testScope.launch {
                            val r = repo.ingestNotification(nn.pkg, nn.title, nn.text, nn.postedAt, force = true)
                            testMsg = r?.let { "已記下：${it.message}" } ?: "沒有記下（可能已經記過了）"
                        }
                    }) { Text("記下這筆") }
                    if (!desc.startsWith("LINE 聊天")) TextButton(onClick = { teach = nn }) { Text("設定辨識方式") }
                }
            }
        }
    }

    Scaffold(topBar = { BackTopBar("通知自動記帳", { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad).imePadding()) {
            if (enabled) item {
                // 已開啟：只留一行狀態，設定在「來源 App」分頁
                val today = java.time.LocalDate.now()
                val todayLog = log.filter { java.time.Instant.ofEpochMilli(it.postedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == today }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.enwei.moneymanager.ui.components.StatusLine(true, "通知存取已開啟")
                    Spacer(Modifier.weight(1f))
                    Text("今天 ${todayLog.size} 則 · 記帳 ${todayLog.count { it.result == "RECORDED" || it.result == "MERGED" }}", fontSize = AppText.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (enabled && tab == 0) item { AutoAccuracyCard(txns) }
            if (!enabled) item {
                com.enwei.moneymanager.ui.components.AppCard(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        com.enwei.moneymanager.ui.components.StatusLine(enabled, if (enabled) "已取得通知存取權" else "尚未開啟通知存取權")
                        // 只在還沒開啟時說明「受限制的設定」的解法
                        if (!enabled) Text(
                            "無法開啟時：設定 → 應用程式 → 帳理 → ⋮ →「允許受限制的設定」。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        // 開啟前先說明讀取什麼、做什麼用並取得同意（已開啟時直接到設定）
                        var disclose by remember { mutableStateOf(false) }
                        Button(onClick = { if (enabled) TxnNotificationListener.openSettings(ctx) else disclose = true }) { Text(if (enabled) "通知存取設定" else "前往開啟") }
                        if (disclose) com.enwei.moneymanager.ui.components.AccessDisclosure(0, { TxnNotificationListener.openSettings(ctx) }) { disclose = false }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(com.enwei.moneymanager.ui.AppearancePrefs.cardRadius)).padding(3.dp)) {
                    listOf("紀錄", "來源 App", "進階").forEachIndexed { i, l ->
                        val on = tab == i
                        Box(Modifier.weight(1f).coachTarget("nf.tab$i").background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { tab = i }.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                            Text(l, fontSize = AppText.secondary, fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal)
                        }
                    }
                }
            }
            if (tab == 1) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownField(
                        "無法對應卡號時記入的帳戶", lk.accounts.filter { !it.archived }, lk.account(settings.defaultAccountId),
                        { it.name }, { a -> repo.settings.update { it.copy(defaultAccountId = a.id) } },
                    )
                    LocationHintRow(settings.locationHints)
                }
            }
            if (candidates.isNotEmpty()) {
                item { SectionTitle("可能的交易通知來源（${candidates.size}）") }
                // 小卡：App 名稱、偵測到幾則、最近一則的內容；可「啟用」或「忽略」（忽略後不再出現）
                items(candidates, key = { "cand$it" }) { pkg ->
                    val notes = log.filter { it.packageName == pkg && it.result == "CANDIDATE" }
                    Box(
                        modifier = Modifier.fillMaxWidth().listRow()) {
                        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${labelOf[pkg] ?: pkg} · ${notes.size} 則", fontSize = AppText.body, maxLines = 1)
                                Text(notes.first().let { listOfNotNull(it.title, it.text).joinToString(" ") }, fontSize = AppText.caption, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { repo.settings.update { it.copy(notifIgnored = it.notifIgnored + pkg) } }) {
                                Text("忽略", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            androidx.compose.material3.FilledTonalButton(onClick = { toggle(pkg, true) },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) { Text("啟用", fontSize = AppText.secondary) }
                        }
                    }
                }
            }
            item { SectionTitle("要監聽的 App（已選 ${settings.notifPackages.size}）") }
            // 預設只列已勾選的 App；要新增時再展開全部（可搜尋）
            item {
                if (showAllApps) OutlinedTextField(filter, { filter = it }, label = { Text("搜尋 App") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                TextButton(onClick = { showAllApps = !showAllApps; filter = "" }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(if (showAllApps) "只顯示已選的 App" else "＋ 新增要監聽的 App")
                }
            }
            val sorted = apps.filter { (showAllApps || it.pkg in settings.notifPackages) && (filter.isBlank() || it.label.contains(filter, true) || it.pkg.contains(filter, true)) }
                .sortedByDescending { it.pkg in settings.notifPackages }
            items(sorted, key = { it.pkg }) { a ->
                val on = a.pkg in settings.notifPackages
                ListItem(
                    headlineContent = { Text(a.label) },
                    supportingContent = {
                        if (on) Row(verticalAlignment = Alignment.CenterVertically) {
                            // 這個 App 的通知記到哪個帳戶（信用卡會優先用末四碼對應）
                            var menu by remember { mutableStateOf(false) }
                            Box(Modifier.weight(1f, fill = false)) {
                                Text(
                                    "記入：" + (settings.notifAccountMap[a.pkg]?.let { lk.account(it)?.name } ?: "依卡號／預設帳戶") + " ▾",
                                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.clickable { menu = true }.padding(vertical = 4.dp),
                                )
                                androidx.compose.material3.DropdownMenu(menu, { menu = false }) {
                                    androidx.compose.material3.DropdownMenuItem(text = { Text("依卡號／預設帳戶") }, onClick = {
                                        repo.settings.update { it.copy(notifAccountMap = it.notifAccountMap - a.pkg) }; menu = false
                                    })
                                    lk.accounts.filter { !it.archived }.forEach { acc ->
                                        androidx.compose.material3.DropdownMenuItem(text = { Text(acc.name) }, onClick = {
                                            repo.settings.update { it.copy(notifAccountMap = it.notifAccountMap + (a.pkg to acc.id)) }; menu = false
                                        })
                                    }
                                }
                            }
                            // 股票來源：只有勾了的 App，成交通知才會建立股票交易
                            if (settings.featureStocks) {
                                val stock = a.pkg in settings.stockPackages
                                androidx.compose.material3.FilterChip(stock, {
                                    repo.settings.update { it.copy(stockPackages = if (stock) it.stockPackages - a.pkg else it.stockPackages + a.pkg) }
                                }, label = { Text("股票成交", fontSize = AppText.caption) }, modifier = Modifier.padding(start = 8.dp).height(28.dp))
                            }
                        } else Text(a.pkg, style = MaterialTheme.typography.labelSmall)
                    },
                    trailingContent = { Checkbox(on, { toggle(a.pkg, it) }) },
                    modifier = Modifier.clickable { toggle(a.pkg, !on) },
                )
            }
            }
            if (tab == 2) {
            // 測試區（讀通知列、貼上通知）預設收起，需要時再展開
            // 「進階」分頁本身就是收起來的地方，測試區直接展開
            if (settings.locationHints) {
                item { SectionTitle("地點測試") }
                item { LocationTest(onSetup = { nav.navigate("merchants") }) }
            }
            item { SectionTitle("測試通知辨識") }
            run {
                item {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { loadNotes() }) { Text(if (notes == null) "讀取目前通知" else "重新讀取") }
                        testMsg?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                notes?.let { list ->
                    items(list.size, key = { "note$it" }) { i ->
                        val (ok, desc) = previews.getOrNull(i) ?: (false to "")
                        NoteCard(list[i], ok, desc)
                    }
                }
                // 貼上一則通知測試：和讀取通知列一樣跑完整流程（股票成交、教過的格式、AI 判讀、轉帳、重複檢查）
                item { SectionTitle("貼上通知測試") }
                item {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val choices = (settings.notifPackages + listOf("com.google.android.gm")).distinct()
                        DropdownField("來源 App", choices, testPkg ?: choices.firstOrNull(), { labelOf[it] ?: it }, { testPkg = it })
                        OutlinedTextField(testTitle, { testTitle = it }, label = { Text("通知標題（選填）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(testText, { testText = it }, label = { Text("通知內容") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                        Button(enabled = testText.isNotBlank(), onClick = {
                            val pkg = testPkg ?: choices.firstOrNull() ?: return@Button
                            val nn = TxnNotificationListener.ActiveNote(pkg, testTitle.ifBlank { null }, testText, System.currentTimeMillis())
                            testScope.launch {
                                pasted = nn
                                pastedPreview = repo.previewBatch(listOf(Repository.NotePreview(nn.pkg, nn.title, nn.text, nn.postedAt))).first()
                            }
                        }) { Text("測試") }
                    }
                }
                pasted?.let { nn ->
                    item(key = "pasted") {
                        NoteCard(nn, pastedPreview?.first == true, pastedPreview?.second.orEmpty())
                        testMsg?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp)) }
                    }
                }
            }
            if (BuildConfig.DEBUG) {
                item { SectionTitle("整合模擬器（Debug；只試算、不寫入）") }
                item {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val packages = settings.notifPackages.toList()
                            val debitPkg = packages.firstOrNull() ?: "com.example.synthetic.bank"
                            val creditPkg = packages.getOrNull(1) ?: debitPkg
                            val now = System.currentTimeMillis()
                            val samples = listOf(
                                Repository.NotePreview(debitPkg, "刷卡通知", "NT\$125 在合成商店刷卡消費", now),
                                Repository.NotePreview(debitPkg, "退款通知", "合成商店退款入帳 NT\$50", now + 1),
                                Repository.NotePreview(debitPkg, "轉出通知", "跨行轉出 NT\$300", now + 2),
                                Repository.NotePreview(creditPkg, "轉入通知", "跨行轉入 NT\$300", now + 3),
                                Repository.NotePreview(debitPkg, "交易通知", "金額 NT\$420，交易已完成，對象：合成服務", now + 4),
                            )
                            testScope.launch { simulationPreviews = repo.previewBatch(samples) }
                        }) { Text("執行合成情境") }
                        simulationPreviews?.forEachIndexed { index, result ->
                            Text("情境 ${index + 1}：${result.second}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (settings.learnedSources.isNotEmpty()) {
                item { SectionTitle("本機通知辨識偏好（點擊刪除）") }
                items(settings.learnedSources.size, key = { "learn$it" }) { i ->
                    val l = settings.learnedSources[i]
                    ListItem(
                        headlineContent = { Text("${labelOf[l.pkg] ?: l.pkg} · 本機辨識規則") },
                        supportingContent = {
                            Text("${l.label} → ${l.accountId?.let { lk.account(it)?.name } ?: "依卡號／預設帳戶"}" +
                                (l.toAccountId?.let { lk.account(it)?.name }?.let { if (l.parsedKind == com.enwei.moneymanager.core.ParsedKind.TRANSFER_IN) "（從 $it）" else "（到 $it）" } ?: ""))
                        },
                        modifier = Modifier.clickable { repo.settings.update { it.copy(learnedSources = it.learnedSources - l) } },
                    )
                }
            }
            if (settings.promoSigs.isNotEmpty()) {
                item { SectionTitle("教過的促銷通知（只存關鍵詞，點擊刪除）") }
                items(settings.promoSigs.size, key = { "promo$it" }) { i ->
                    val p = settings.promoSigs[i]
                    ListItem(
                        headlineContent = { Text("${labelOf[p.pkg] ?: p.pkg.ifBlank { "任何 App" }} · 促銷") },
                        supportingContent = { Text(p.phrases.joinToString("、")) },
                        modifier = Modifier.clickable { repo.settings.update { it.copy(promoSigs = it.promoSigs - p) } },
                    )
                }
            }
            }
            if (tab == 0) {
                // 篩選：全部／已記帳／待確認／略過／無法辨識（單選，用和其他頁一樣的子分頁）
                val filters = listOf("全部", "已記帳", "待確認", "略過", "無法辨識")
                item {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        com.enwei.moneymanager.ui.components.SubTabs(filters, logFilter) { logFilter = it; allLog = false }
                    }
                }
                fun deleted(e: com.enwei.moneymanager.data.NotificationLogEntity) =
                    e.result == "DELETED" || e.result == "DISMISSED" ||
                        (e.result == "RECORDED" || e.result == "MERGED") && e.txnId != null && e.txnId !in txnIds
                val pendingIds = txns.filter { it.status == com.enwei.moneymanager.data.TxnStatus.PENDING }.map { it.id }.toHashSet()
                val shown = log.filter { e ->
                    when (logFilter) {
                        1 -> (e.result == "RECORDED" || e.result == "MERGED" || e.result == "STOCK") && !deleted(e) && e.txnId !in pendingIds
                        2 -> e.txnId in pendingIds || e.result == "OFFLINE"
                        3 -> e.result == "PROMO" || e.result == "DUPLICATE" || e.result == "CANDIDATE" || deleted(e)
                        4 -> e.result == "UNPARSED" || e.result == "ERROR"
                        else -> true
                    }
                }
                if (shown.isEmpty()) item { Text("沒有紀錄", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
                items(shown.take(if (allLog) 100 else 20), key = { "log${it.id}" }) { e ->
                    val gone = deleted(e)
                    val pending = e.txnId in pendingIds
                    val (label, col) = when {
                        e.result == "DISMISSED" -> "待確認已刪除" to MaterialTheme.colorScheme.onSurfaceVariant
                        gone -> "交易已刪除" to MaterialTheme.colorScheme.onSurfaceVariant
                        pending -> "待確認" to MaterialTheme.colorScheme.primary
                        e.result == "RECORDED" -> "已記帳" to MaterialTheme.colorScheme.primary
                        e.result == "MERGED" -> "已合併" to MaterialTheme.colorScheme.primary
                        e.result == "STOCK" -> "股票成交" to MaterialTheme.colorScheme.primary
                        e.result == "STOCK_PENDING" -> "股票待確認" to Color(0xFFFFA000)
                        e.result == "DUPLICATE" -> "重複略過" to MaterialTheme.colorScheme.onSurfaceVariant
                        e.result == "SETTLE" -> "交割款（不記帳）" to MaterialTheme.colorScheme.onSurfaceVariant
                        e.result == "PROMO" -> "促銷略過" to MaterialTheme.colorScheme.onSurfaceVariant
                        e.result == "UNPARSED" -> "無法辨識" to MaterialTheme.colorScheme.error
                        e.result == "ERROR" -> "處理失敗・點選重試" to MaterialTheme.colorScheme.error
                        e.result == "OFFLINE" -> "等網路後判讀" to Color(0xFFFFA000)
                        else -> "未啟用來源" to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth().listRow().clickable {
                            when {
                                !gone && e.txnId != null && (e.result == "RECORDED" || e.result == "MERGED") -> nav.navigate("txn/${e.txnId}")
                                gone || e.result == "ERROR" -> testScope.launch {
                                    val r = repo.ingestNotification(e.packageName, e.title, e.text, e.postedAt, force = true)
                                    testMsg = r?.let { "已重新記帳：${it.message}" } ?: "沒有記下（看不出是交易）"
                                }
                                // 先前看不懂、現在認得出是成交通知（只是缺股數或價格）：直接重新判讀，放進股票交易待確認
                                e.result == "UNPARSED" && com.enwei.moneymanager.core.NotificationParser.parseStockFillPartial(e.title, e.text) != null -> testScope.launch {
                                    val r = repo.ingestNotification(e.packageName, e.title, e.text, e.postedAt, force = true)
                                    testMsg = r?.message ?: "已在待確認"
                                }
                                e.result == "STOCK_PENDING" -> nav.navigate("pending")
                                e.result in setOf("UNPARSED", "CANDIDATE", "PROMO") -> teach = TxnNotificationListener.ActiveNote(e.packageName, e.title, e.text, e.postedAt)
                            }
                        }) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${labelOf[e.packageName] ?: knownAppNames[e.packageName] ?: e.packageName.substringAfterLast('.')} · ${timeFmt.format(Date(e.postedAt))}", fontSize = AppText.label,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
                                Text(label, fontSize = AppText.label, color = col,
                                    modifier = Modifier.background(col.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
                            }
                            // 已記帳：先寫記成什麼（金額・店名），通知原文縮成一行放下面
                            val rec = e.txnId?.let { id -> txnById[id] }
                            if (rec != null && !gone) {
                                Text(listOfNotNull(Money.withSymbol(rec.amount, rec.currency), rec.merchant?.takeIf { it.isNotBlank() }).joinToString("・"),
                                    fontSize = AppText.body, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(listOfNotNull(e.title, e.text).joinToString(" "), fontSize = AppText.caption, maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            } else Text(listOfNotNull(e.title, e.text).joinToString(" "), fontSize = AppText.secondary, maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            if (gone) Text("點選重新記帳", fontSize = AppText.label, color = MaterialTheme.colorScheme.primary)
                            else if (e.result == "UNPARSED" && com.enwei.moneymanager.core.NotificationParser.parseStockFillPartial(e.title, e.text) != null)
                                Text("點選放到股票交易待確認", fontSize = AppText.label, color = MaterialTheme.colorScheme.primary)
                            else if (e.result in setOf("UNPARSED", "CANDIDATE", "PROMO")) Text("點選設定為交易", fontSize = AppText.label, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                testMsg?.let { m -> item { Text(m, color = MaterialTheme.colorScheme.primary, fontSize = AppText.caption, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) } }
                if (shown.size > 20) item {
                    TextButton(onClick = { allLog = !allLog }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(if (allLog) "收起" else "顯示更多紀錄") }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
    teach?.let { nn ->
        // 0 支出、1 收入、2 轉出、3 轉入
        var mode by remember(nn) { mutableStateOf(0) }
        var accId by remember(nn) { mutableStateOf(settings.notifAccountMap[nn.pkg]) }
        var toId by remember(nn) { mutableStateOf<Long?>(null) }
        val amount = NotificationParser.findAmount(listOfNotNull(nn.title, nn.text).joinToString(" "))
        val key = com.enwei.moneymanager.data.learnKey(nn.title, nn.text)
        // AI 先幫忙判斷收支類型與帳戶，使用者確認或修改後再儲存
        var aiHint by remember(nn) { mutableStateOf<String?>(null) }
        LaunchedEffect(nn) {
            if (!com.enwei.moneymanager.net.GroqAi.enabled) return@LaunchedEffect
            aiHint = "AI 判斷中…"
            val accs = lk.accounts.filter { !it.archived }.map { a ->
                a.id to listOfNotNull(a.name, a.type.label, a.cardLast4?.let { "末四碼 $it" }, a.description).joinToString("｜")
            }
            val h = com.enwei.moneymanager.net.GroqAi.suggestTeach(nn.title, nn.text, accs)
            if (h == null) { aiHint = null; return@LaunchedEffect }
            mode = h.mode
            h.accountId?.let { accId = it }
            if (h.mode >= 2) toId = h.toAccountId?.takeIf { it != accId }
            aiHint = "AI 建議：${listOf("支出", "收入", "轉出", "轉入")[h.mode]}" +
                (h.accountId?.let { lk.account(it)?.name }?.let { "，$it" } ?: "") + (h.reason.takeIf { it.isNotBlank() }?.let { "（$it）" } ?: "")
        }
        AlertDialog(
            onDismissRequest = { teach = null },
            title = { Text("設定辨識方式") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (key.isBlank()) "這則通知沒有標題，為避免保存原始內容，無法建立本機辨識偏好。"
                        else "此偏好只保存在本機；AI 判讀期間可讀取這次通知，但不保存通知原文，也不會用來微調 Groq／Gemini。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    aiHint?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall) }
                    if (amount == null) Text("這則通知裡找不到金額，無法自動記帳。", color = MaterialTheme.colorScheme.error)
                    else Text("找到的金額：${Money.format(amount.first, amount.second)} ${amount.second}")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("支出", "收入", "轉出", "轉入").forEachIndexed { i, l -> FilterChip(mode == i, { mode = i }, label = { Text(l) }) }
                    }
                    DropdownField(
                        if (mode >= 2) "這則通知的帳戶" else "記到帳戶", lk.accounts.filter { !it.archived }, lk.account(accId), { it.name }, { accId = it.id },
                        noneLabel = "依卡號／預設帳戶", onNone = { accId = null },
                    )
                    if (mode >= 2) {
                        DropdownField(
                            if (mode == 2) "轉到（選填）" else "從（選填）", lk.accounts.filter { !it.archived && it.id != accId }, lk.account(toId), { it.name }, { toId = it.id },
                            noneLabel = "等另一端的通知自動配對", onNone = { toId = null },
                        )
                        Text(
                            if (toId == null) "另一端也有通知時會自動合併成一筆轉帳"
                            else "每次直接記成轉帳，不等另一端的通知",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = amount != null && key.isNotBlank(), onClick = {
                    repo.settings.update { st ->
                        st.copy(learnedSources = st.learnedSources.filterNot { it.pkg == nn.pkg && it.title == key } +
                            com.enwei.moneymanager.data.LearnedSource(
                                nn.pkg, key, income = mode == 1, accountId = accId,
                                kind = when (mode) { 2 -> "TRANSFER_OUT"; 3 -> "TRANSFER_IN"; else -> "NORMAL" },
                                toAccountId = if (mode >= 2) toId else null,
                            ))
                    }
                    teach = null
                    if (notes != null) loadNotes()
                }) { Text("儲存") }
            },
            dismissButton = { TextButton(onClick = { teach = null }) { Text("取消") } },
        )
    }
}

@Composable
fun FxScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val rates by repo.fxRates.collectAsStateWithLifecycle(emptyList())
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val lk = rememberLookups()
    var status by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("100") }
    var from by remember { mutableStateOf(repo.settings.value.lastPicks["fx.from"] ?: "USD") }

    fun refresh() = scope.launch {
        status = "更新中…"
        status = runCatching { SyncWorker.refreshFx(repo) }.fold({ "已更新" }, { "更新失敗：${com.enwei.moneymanager.ui.friendlyError(it)}" })
    }
    // 畫面開著時每分鐘更新即時匯率
    LaunchedEffect(Unit) {
        while (true) {
            status = runCatching { SyncWorker.refreshFx(repo) }.fold({ "即時報價" }, { "更新失敗：${com.enwei.moneymanager.ui.friendlyError(it)}" })
            kotlinx.coroutines.delay(60_000)
        }
    }

    Scaffold(topBar = {
        BackTopBar("即時匯率", { nav.popBackStack() }) { IconButton(onClick = { refresh() }) { Icon(Icons.Filled.Refresh, "更新") } }
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownField("基準幣別（淨資產、報表以此計算）", listOf("TWD", "USD", "JPY", "EUR", "HKD", "CNY"), settings.baseCurrency, { it }, { c ->
                        repo.settings.update { it.copy(baseCurrency = c) }; refresh()
                    })
                    Text(
                        (if (settings.lastFxUpdate > 0) "更新於 ${timeFmt.format(Date(settings.lastFxUpdate))}" else "尚未更新") + (status?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text("換算", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NumberField(amount, { amount = it }, "金額", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        DropdownField("幣別", FxService.common, from, { it }, { from = it }, Modifier.width(120.dp), memoryKey = "fx.from")
                    }
                    Money.parseMinor(amount)?.let {
                        Text("= ${Money.format(lk.fx.convert(it, from), settings.baseCurrency)} ${settings.baseCurrency}", style = MaterialTheme.typography.titleLarge)
                    }
                }
                com.enwei.moneymanager.ui.components.LineDivider()
            }
            val shown = FxService.common.mapNotNull { c -> rates.find { it.currency == c } }
            items(shown, key = { it.currency }) { r ->
                ListItem(
                    headlineContent = { Text(r.currency) },
                    supportingContent = { Text("1 ${settings.baseCurrency} = ${String.format("%.4f", r.rate)} ${r.currency}") },
                    trailingContent = {
                        Text("1 ${r.currency} = ${String.format("%.4f", 1 / r.rate)} ${settings.baseCurrency}", style = MaterialTheme.typography.titleSmall)
                    },
                )
            }
        }
    }
}

@Composable
fun BackupScreen(nav: NavController) {
    // 第一次打開這頁：介紹備份與還原
    com.enwei.moneymanager.ui.components.PageTour("backup", listOf(
        com.enwei.moneymanager.ui.components.Coach.Step(null, "完整備份", "匯出 .lgbak 檔即包含所有交易、帳戶、分類與設定（不含金鑰），可在其他裝置還原。"),
        com.enwei.moneymanager.ui.components.Coach.Step(null, "自動備份", "選擇一個資料夾（例如雲端硬碟同步資料夾）後，帳理會定期自動備份；資料夾權限失效時會在交易頁提醒。"),
        com.enwei.moneymanager.ui.components.Coach.Step(null, "還原方式", "「合併」保留現有資料並加入備份內容；「取代」會先清空再匯入，請先確認。"),
    ))
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var confirmUri by remember { mutableStateOf<android.net.Uri?>(null) }
    // 帳理完整備份（.lgbak）：所有資料表＋所有設定
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = runCatching {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)!!.use { com.enwei.moneymanager.data.FullBackupIO.export(repo, BuildConfig.VERSION_NAME, it) }
                }
                "已匯出完整備份"
            }.getOrElse { "匯出失敗：${com.enwei.moneymanager.ui.friendlyError(it)}" }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmUri = uri }
    var pwBytes by remember { mutableStateOf<ByteArray?>(null) }
    val csvLk = rememberLookups()
    val csvExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = runCatching {
                val list = repo.allTxns().filter { it.status == com.enwei.moneymanager.data.TxnStatus.CONFIRMED }
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(buildCsv(list, csvLk).toByteArray(Charsets.UTF_8)) } }
                "已匯出 ${list.size} 筆交易"
            }.getOrElse { "匯出失敗：${com.enwei.moneymanager.ui.friendlyError(it)}" }
        }
    }
    var mmUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    val mmImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> mmUri = uri }
    val mmExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            status = runCatching {
                val tmp = java.io.File(ctx.cacheDir, "export.mmbak")
                com.enwei.moneymanager.data.MmbakIO.export(ctx, repo, tmp)
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)!!.use { o -> tmp.inputStream().use { it.copyTo(o) } } }
                tmp.delete()
                "已匯出 .mmbak（Money Manager 備份格式）"
            }.getOrElse { "匯出失敗：${com.enwei.moneymanager.ui.friendlyError(it)}" }
            busy = false
        }
    }
    fun runMmImport(uri: android.net.Uri, replace: Boolean) {
        busy = true
        scope.launch {
            status = runCatching {
                val tmp = java.io.File(ctx.cacheDir, "import.mmbak")
                withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { i.copyTo(it) } } }
                val r = com.enwei.moneymanager.data.MmbakIO.import(ctx, repo, tmp, replace)
                tmp.delete()
                "匯入完成：新增 ${r.accounts} 個帳戶、${r.categories} 個分類、${r.transactions} 筆交易" +
                    (if (r.skipped > 0) "（${r.skipped} 筆已存在略過）" else "") +
                    "、${r.memos} 則備忘、${r.recurring} 個週期設定。\n請到「帳戶」核對餘額；信用卡的結帳日、繳款日已帶入，扣繳帳戶與日期請再確認。"
            }.getOrElse { "匯入失敗：${com.enwei.moneymanager.ui.friendlyError(it)}" }
            busy = false
        }
    }
    val bs by repo.settings.flow.collectAsStateWithLifecycle()
    // 自動雲端備份：選一次備份檔位置（雲端硬碟、OneDrive、Dropbox、手機資料夾皆可），之後自動覆寫
    val autoTarget = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        repo.settings.update { it.copy(autoBackupUri = uri.toString(), autoBackupDays = it.autoBackupDays.coerceAtLeast(1)) }
        com.enwei.moneymanager.work.BackupWorker.runNow(ctx)
    }
    var freqMenu by remember { mutableStateOf(false) }
    Scaffold(topBar = { BackTopBar("備份與還原", { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Text("自動雲端備份", style = MaterialTheme.typography.titleMedium)
            val target = bs.autoBackupUri.takeIf { it.isNotBlank() }?.let { android.net.Uri.parse(it) }
            Text(
                if (target == null) "尚未選擇備份位置"
                else "備份位置：" + (target.lastPathSegment?.substringAfterLast('/') ?: target.toString()) +
                    "\n上次備份：" + (if (bs.lastAutoBackup > 0) timeFmt.format(Date(bs.lastAutoBackup)) else "尚未備份") +
                    (if (bs.lastAutoBackupError.isNotBlank()) "\n上次失敗：${bs.lastAutoBackupError}" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = if (bs.lastAutoBackupError.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { autoTarget.launch("帳理自動備份.lgbak") }) { Text(if (target == null) "選擇備份位置" else "更換位置") }
                if (target != null) OutlinedButton(onClick = { com.enwei.moneymanager.work.BackupWorker.runNow(ctx) }) { Text("立即備份") }
            }
            if (target != null) Box {
                Text(
                    "頻率：" + when (bs.autoBackupDays) { 0 -> "關閉"; 1 -> "每天"; 7 -> "每週"; else -> "每 ${bs.autoBackupDays} 天" } + " ▾",
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { freqMenu = true }.padding(vertical = 6.dp),
                )
                androidx.compose.material3.DropdownMenu(freqMenu, { freqMenu = false }) {
                    listOf(1 to "每天", 3 to "每 3 天", 7 to "每週", 0 to "關閉").forEach { (d, l) ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(l) }, onClick = {
                            repo.settings.update { it.copy(autoBackupDays = d) }; freqMenu = false
                        })
                    }
                }
            }
            com.enwei.moneymanager.ui.components.LineDivider()
            Text("帳理完整備份（.lgbak）", style = MaterialTheme.typography.titleMedium)
            // 備份加密：設定密碼後，匯出與自動備份的檔案都會加密（密碼只存在這支手機）
            var encOn by remember { mutableStateOf(com.enwei.moneymanager.data.BackupPassword.isSet()) }
            var encSetup by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("備份加密")
                    Text(if (encOn) "已開啟：備份檔需要密碼才能還原" else "關閉：備份檔未加密，請存放在只有你能存取的位置",
                        fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(encOn, { v ->
                    if (v) encSetup = true else { com.enwei.moneymanager.data.BackupPassword.set(null); encOn = false }
                })
            }
            if (encSetup) {
                var p1 by remember { mutableStateOf("") }
                var p2 by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { encSetup = false },
                    title = { Text("設定備份密碼") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("換手機還原時需要輸入這組密碼；忘記密碼就無法還原，請妥善保存。", fontSize = AppText.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            androidx.compose.material3.OutlinedTextField(p1, { p1 = it }, singleLine = true, label = { Text("密碼（至少 8 個字元）") },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                            androidx.compose.material3.OutlinedTextField(p2, { p2 = it }, singleLine = true, label = { Text("再輸入一次") },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                        }
                    },
                    confirmButton = {
                        TextButton(enabled = p1.length >= 8 && p1 == p2, onClick = {
                            com.enwei.moneymanager.data.BackupPassword.set(p1); encOn = true; encSetup = false
                        }) { Text("設定") }
                    },
                    dismissButton = { TextButton(onClick = { encSetup = false }) { Text("取消") } },
                )
            }
            Button(onClick = { exporter.launch("帳理備份-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.lgbak") }, modifier = Modifier.fillMaxWidth()) {
                Text("匯出完整備份（.lgbak）")
            }
            OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text("從備份還原（.lgbak 或舊版 JSON）")
            }
            // 上次還原前自動保存的資料：還原結果不對時可以找回
            val snap = remember(status) { com.enwei.moneymanager.data.FullBackupIO.snapshotFile().takeIf { it.exists() } }
            if (snap != null) {
                var undoAsk by remember { mutableStateOf(false) }
                TextButton(onClick = { undoAsk = true }) {
                    Text("復原為上次還原前的資料（" + SimpleDateFormat("M/d HH:mm", Locale.US).format(Date(snap.lastModified())) + "）")
                }
                if (undoAsk) AlertDialog(
                    onDismissRequest = { undoAsk = false },
                    title = { Text("復原還原前的資料？") },
                    text = { Text("目前的資料會被上次還原前自動保存的資料取代。") },
                    confirmButton = {
                        TextButton(onClick = {
                            undoAsk = false
                            scope.launch {
                                status = com.enwei.moneymanager.MoneyApp.instance.appScope.async {
                                    runCatching {
                                        val b = withContext(Dispatchers.IO) { snap.inputStream().use { com.enwei.moneymanager.data.FullBackupIO.read(it) } }
                                        com.enwei.moneymanager.data.FullBackupIO.restore(repo, b, snapshot = false)
                                        "已復原為還原前的資料"
                                    }.getOrElse { "復原失敗：${com.enwei.moneymanager.ui.friendlyError(it)}" }
                                }.await()
                            }
                        }) { Text("復原") }
                    },
                    dismissButton = { TextButton(onClick = { undoAsk = false }) { Text("取消") } },
                )
            }
            com.enwei.moneymanager.ui.components.LineDivider()
            // 交易明細匯出成 Excel 可開的 CSV（原本在交易頁「總計」分頁）
            Text("匯出交易明細", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { csvExporter.launch("帳理交易明細-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.csv") },
                modifier = Modifier.fillMaxWidth()) { Text("匯出全部交易（CSV，可用 Excel 開啟）") }
            com.enwei.moneymanager.ui.components.LineDivider()
            Text("Money Manager 備份檔（.mmbak）", style = MaterialTheme.typography.titleMedium)
            Button(enabled = !busy, onClick = { mmImporter.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("從 .mmbak 匯入") }
            OutlinedButton(enabled = !busy, onClick = { mmExporter.launch("帳理-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.mmbak") },
                modifier = Modifier.fillMaxWidth()) { Text("匯出為 .mmbak") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    mmUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { mmUri = null },
            title = { Text("匯入 Money Manager 資料") },
            text = { Text("合併：保留現有資料　取代：清空後匯入（股票保留）") },
            confirmButton = { TextButton(onClick = { mmUri = null; runMmImport(uri, replace = false) }) { Text("合併") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { mmUri = null; runMmImport(uri, replace = true) }) { Text("取代") }
                    TextButton(onClick = { mmUri = null }) { Text("取消") }
                }
            },
        )
    }
    // 加密備份的密碼輸入
    pwBytes?.let { bytes ->
        var pw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pwBytes = null },
            title = { Text("輸入備份密碼") },
            text = {
                androidx.compose.material3.OutlinedTextField(pw, { pw = it }, singleLine = true, label = { Text("備份密碼") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            },
            confirmButton = {
                TextButton(enabled = pw.isNotEmpty(), onClick = {
                    val entered = pw.toCharArray(); pwBytes = null
                    scope.launch {
                        status = com.enwei.moneymanager.MoneyApp.instance.appScope.async { runCatching {
                            val b = withContext(Dispatchers.IO) { com.enwei.moneymanager.data.FullBackupIO.read(bytes.inputStream(), entered) }
                            com.enwei.moneymanager.data.FullBackupIO.restore(repo, b)
                            "已還原完整備份：${b.accounts.size} 個帳戶、${b.transactions.size} 筆交易、${b.trades.size} 筆股票紀錄與所有設定"
                        }.getOrElse { if (it is com.enwei.moneymanager.data.FullBackupIO.PasswordNeeded) "備份密碼不正確" else "還原失敗：${com.enwei.moneymanager.ui.friendlyError(it)}" } }.await()
                    }
                }) { Text("還原") }
            },
            dismissButton = { TextButton(onClick = { pwBytes = null }) { Text("取消") } },
        )
    }
    confirmUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmUri = null },
            title = { Text("還原備份？") },
            text = { Text("目前手機上的所有資料與設定會被備份內容取代。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUri = null
                    scope.launch {
                        // 在 App 層執行：離開這頁或畫面旋轉也不會把還原中斷在一半
                        status = com.enwei.moneymanager.MoneyApp.instance.appScope.async { runCatching {
                            val bytes = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
                            // gzip 開頭 1F 8B 或加密標頭：帳理完整備份；否則當作舊版 JSON
                            if (com.enwei.moneymanager.data.FullBackupIO.looksLikeBackup(bytes.copyOf(minOf(8, bytes.size)))) {
                                val b = try {
                                    withContext(Dispatchers.IO) { com.enwei.moneymanager.data.FullBackupIO.read(bytes.inputStream()) }
                                } catch (e: com.enwei.moneymanager.data.FullBackupIO.PasswordNeeded) {
                                    // 加密的備份、這支手機沒有（或不同的）備份密碼：請使用者輸入
                                    pwBytes = bytes
                                    throw e
                                }
                                com.enwei.moneymanager.data.FullBackupIO.restore(repo, b)
                                "已還原完整備份：${b.accounts.size} 個帳戶、${b.transactions.size} 筆交易、${b.trades.size} 筆股票紀錄與所有設定"
                            } else {
                                val b = repo.importJson(bytes.decodeToString())
                                "已還原：${b.accounts.size} 個帳戶、${b.transactions.size} 筆交易、${b.trades.size} 筆股票交易"
                            }
                        }.getOrElse { "還原失敗：${com.enwei.moneymanager.ui.friendlyError(it)}" } }.await()
                    }
                }) { Text("還原") }
            },
            dismissButton = { TextButton(onClick = { confirmUri = null }) { Text("取消") } },
        )
    }
}

@Composable
fun SettingsScreen(nav: NavController) {
    if (!com.enwei.moneymanager.BuildConfig.SELF_UPDATE) { PlayUpdateScreen(nav); return }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    var token by remember { mutableStateOf(settings.githubToken) }
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var apk by remember { mutableStateOf<File?>(null) }

    fun check() = scope.launch {
        repo.settings.update { it.copy(githubToken = token.trim()) }
        updateStatus = "檢查中…"
        runCatching { Updater.latest(token) }.onSuccess { r ->
            release = r
            updateStatus = when {
                r == null -> "找不到可下載的版本"
                Updater.isNewer(r) -> "有新版本 ${r.display}"
                else -> "已是最新版本（${BuildConfig.VERSION_NAME}）"
            }
        }.onFailure {
            val msg = it.message.orEmpty()
            updateStatus = when {
                token.isBlank() -> "檢查失敗：私人儲存庫需要填入 GitHub Token"
                "401" in msg -> "Token 無效或已過期，請重新建立"
                "404" in msg -> "Token 看不到這個儲存庫（GitHub 對無權限的私人儲存庫回傳 404）。\n" +
                    "請確認 Token 的 Repository access 選了「Only select repositories → ${BuildConfig.UPDATE_REPO.substringAfter('/')}」，" +
                    "並在 Permissions → Repository permissions 把 Contents 設為 Read-only。"
                else -> "檢查失敗：$msg"
            }
        }
    }

    fun install(file: File) {
        if (!Updater.canInstall(ctx)) {
            updateStatus = "請允許「安裝不明應用程式」後再按一次安裝"
            Updater.openInstallPermission(ctx)
        } else Updater.install(ctx, file)
    }

    // 打開就自動檢查一次
    LaunchedEffect(Unit) { if (release == null) check() }
    val r = release
    val newer = r != null && Updater.isNewer(r)
    val checking = updateStatus == "檢查中…"
    Scaffold(topBar = { BackTopBar("App 更新", { nav.popBackStack() }) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()
                .verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ① 版本卡：目前版本、狀態標籤、一個主要按鈕
            com.enwei.moneymanager.ui.components.AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(com.enwei.moneymanager.ui.AppearancePrefs.cardRadius)) {
                            Icon(androidx.compose.material.icons.Icons.Outlined.SystemUpdate, null, tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(12.dp).size(28.dp))
                        }
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text("目前版本", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(BuildConfig.VERSION_NAME, fontSize = AppText.title, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        }
                        val (chip, chipColor) = when {
                            checking -> "檢查中" to MaterialTheme.colorScheme.surfaceVariant
                            newer -> "有新版" to MaterialTheme.colorScheme.primaryContainer
                            r != null -> "已是最新" to MaterialTheme.colorScheme.secondaryContainer
                            updateStatus != null -> "無法檢查" to MaterialTheme.colorScheme.errorContainer
                            else -> null to MaterialTheme.colorScheme.surfaceVariant
                        }
                        if (chip != null) Surface(color = chipColor, shape = RoundedCornerShape(50)) {
                            Text(chip, fontSize = AppText.caption, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                    if (newer) Text("新版本 ${r!!.display}", fontSize = AppText.section, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary)
                    // 失敗或提示訊息（成功時狀態已在標籤裡，不重複）
                    updateStatus?.takeIf { !checking && (r == null || it.startsWith("下載") || it.startsWith("請允許")) }?.let {
                        Text(it, fontSize = AppText.secondary, color = MaterialTheme.colorScheme.error)
                    }
                    progress?.let {
                        LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
                        Text("下載中 ${(it * 100).toInt()}%", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val f = apk
                    when {
                        f != null -> Button(onClick = { install(f) }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("安裝") }
                        newer -> Button(enabled = progress == null, modifier = Modifier.fillMaxWidth().height(48.dp), onClick = {
                            scope.launch {
                                progress = 0f
                                runCatching { Updater.download(ctx, r!!, token) { p -> progress = p } }
                                    .onSuccess { apk = it; progress = null; install(it) }
                                    .onFailure { progress = null; updateStatus = "下載失敗：${com.enwei.moneymanager.ui.friendlyError(it)}" }
                            }
                        }) { Text("下載並安裝") }
                        else -> OutlinedButton(enabled = !checking, onClick = { check() }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            Text(if (checking) "檢查中…" else "重新檢查")
                        }
                    }
                    if (settings.updateCheckLog.isNotBlank()) Text("上次自動檢查：${settings.updateCheckLog}", fontSize = AppText.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // ② 更新內容：和更新視窗一樣只列最新 3 段
            if (newer && r!!.notes.isNotBlank()) com.enwei.moneymanager.ui.components.AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("更新內容", fontSize = AppText.body, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    parseNotes(r.notes).asReversed().take(3).forEach { (title, lines) ->
                        if (title.isNotBlank()) Text(title, fontSize = AppText.secondary, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                        lines.forEach { l ->
                            Row {
                                Text("•", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 6.dp))
                                Text(l.removePrefix("・").trim(), fontSize = AppText.secondary)
                            }
                        }
                    }
                }
            }
            // ③ 設定：自動檢查開關
            com.enwei.moneymanager.ui.components.AppCard(Modifier.fillMaxWidth()) {
                Column {
                    Row(Modifier.fillMaxWidth().clickable { repo.settings.update { it.copy(notifyUpdates = !it.notifyUpdates, updateSkipped = 0) } }
                        .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("自動檢查新版本", fontSize = AppText.section)
                        }
                        androidx.compose.material3.Switch(settings.notifyUpdates, { v -> repo.settings.update { it.copy(notifyUpdates = v, updateSkipped = 0) } })
                    }

                }
            }
        }
    }
}

@Composable
private fun PlayUpdateScreen(nav: NavController) {
    val ctx = LocalContext.current
    Scaffold(topBar = { BackTopBar("App 更新", { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("目前版本 ${com.enwei.moneymanager.BuildConfig.VERSION_NAME}（build ${com.enwei.moneymanager.BuildConfig.VERSION_CODE}）", fontSize = AppText.body)
            Text("這個版本從 Google Play 安裝，新版會由 Google Play 自動更新。", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = {
                val id = ctx.packageName
                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$id"))) }
                    .onFailure { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=$id"))) }
            }) { Text("在 Google Play 查看") }
        }
    }
}


/**
 * 「依位置推測店家」開關：開啟時先要定位權限，再請使用者把位置權限設成「一律允許」
 * （讀通知時 App 多半不在前景，沒有背景權限就拿不到位置）。
 */
@Composable
private fun LocationHintRow(on: Boolean) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var tick by remember { mutableStateOf(0) }
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val fg = remember(tick) { com.enwei.moneymanager.data.PlaceHints.hasPermission(ctx) }
    val bg = remember(tick) { com.enwei.moneymanager.data.PlaceHints.hasBackground(ctx) }
    fun openAppSettings() = ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        android.net.Uri.fromParts("package", ctx.packageName, null)))
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { r ->
        tick++
        if (r.values.any { it }) repo.settings.update { it.copy(locationHints = true) }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("依位置推測店家", fontSize = AppText.body)
                Text("通知沒寫店家時，用付款當下的位置與過去紀錄補上", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.Switch(on && fg, { v ->
                if (!v) repo.settings.update { it.copy(locationHints = false) }
                else if (fg) repo.settings.update { it.copy(locationHints = true) }
                else ask.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
            })
        }
        // 背景位置：先說明蒐集什麼、做什麼用，同意後才前往系統設定（Google Play 的顯著揭露規定）
        var disclose by remember { mutableStateOf(false) }
        if (on && fg && !bg) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("請將位置權限設為「一律允許」，App 未開啟時才能取得位置", fontSize = AppText.caption, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            TextButton(onClick = { disclose = true }) { Text("前往設定") }
        }
        if (disclose) com.enwei.moneymanager.ui.components.AccessDisclosure(2, { openAppSettings() }) { disclose = false }
    }
}


/** 地點測試：權限、目前位置、這裡的過去紀錄與附近店家，以及上一次讀通知時的推測結果。 */
@Composable
private fun LocationTest(onSetup: () -> Unit = {}) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    // 每一行：true＝正常、false＝有問題、null＝只是說明（不算錯）
    var lines by remember { mutableStateOf<List<Pair<String, Boolean?>>>(emptyList()) }
    var suggestKey by remember { mutableStateOf(false) }
    val ph = com.enwei.moneymanager.data.PlaceHints
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("已記下 ${ph.sampleCount(ctx)} 個付款地點", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (ph.lastResult.isNotBlank()) Text("上次讀通知：${ph.lastResult}", fontSize = AppText.caption)
        Button(enabled = !running, onClick = {
            running = true; lines = emptyList()
            scope.launch {
                // 一步一步顯示結果；每一步都有時間上限，不會卡在「測試中」
                fun add(t: String, ok: Boolean?) { lines = lines + (t to ok) }
                suggestKey = false
                try {
                    add("定位權限：" + (if (ph.hasPermission(ctx)) "已允許" else "未允許"), ph.hasPermission(ctx))
                    add("背景位置：" + (if (ph.hasBackground(ctx)) "一律允許" else "未設為一律允許（App 沒開著時無法取得）"), ph.hasBackground(ctx))
                    val locJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).async { runCatching { ph.current(ctx) }.getOrNull() }
                    val loc = kotlinx.coroutines.withTimeoutOrNull(10_000) { locJob.await() }
                    if (loc == null) add("目前位置：10 秒內拿不到（請確認手機的定位已開啟，或到室外再試）", false)
                    else {
                        val age = (System.currentTimeMillis() - loc.time) / 1000
                        add("目前位置：${"%.5f".format(loc.latitude)}, ${"%.5f".format(loc.longitude)}（誤差約 ${loc.accuracy.toInt()} 公尺，${age} 秒前）", true)
                        val g = ph.fromHistory(ctx, loc, repo.transactions.first())
                        add(g?.let { "過去紀錄：常在這裡記「${it.merchant}」" } ?: "過去紀錄：這附近還沒有", if (g != null) true else null)
                        // Google（手機內建）：這個位置的店名
                        add("查附近店家中…", true)
                        val txns = repo.transactions.first()
                        val known = ph.knownNear(loc, txns.mapNotNull { it.merchant?.trim() }.toSet())
                        val mbOn = com.enwei.moneymanager.net.PlaceSearch.online
                        val osm = kotlinx.coroutines.withTimeoutOrNull(20_000) {
                            runCatching { if (mbOn) com.enwei.moneymanager.net.PlaceSearch.nearby(loc.latitude, loc.longitude).map { it.name } else ph.nearbyStores(loc) }.getOrNull()
                        }
                        val src = com.enwei.moneymanager.net.PlaceSearch.source
                        val err = if (mbOn) com.enwei.moneymanager.net.PlaceSearch.lastError else ph.lastNearbyError
                        lines = lines.dropLast(1)
                        add(if (known.isNotEmpty()) "已知位置的店（200 公尺內）：${known.joinToString("、")}" else "已知位置的店：200 公尺內沒有", if (known.isNotEmpty()) true else null)
                        when {
                            !osm.isNullOrEmpty() -> add("$src 附近的店：${osm.take(6).joinToString("、")}", true)
                            // 沒設定金鑰時用的是免費的公共伺服器，常常忙不過來：不當成錯誤，建議改用 Google Places
                            !mbOn -> { add("OpenStreetMap 的公共伺服器目前很忙，查不到附近的店", null); suggestKey = true }
                            osm == null -> add("$src：20 秒內沒有回應", false)
                            err.isNotBlank() -> add("$src：$err", false)
                            else -> add("$src：附近沒有登錄的店", null)
                        }
                    }
                } catch (e: Exception) {
                    add("測試失敗：${e.message ?: e.javaClass.simpleName}", false)
                } finally { running = false }
            }
        }) { Text(if (running) "測試中…" else "測試目前位置") }
        lines.forEach { (t, ok) -> Text((when (ok) { true -> "✓ "; false -> "✗ "; null -> "・ " }) + t, fontSize = AppText.secondary,
            color = when (ok) { true -> MaterialTheme.colorScheme.onSurface; false -> MaterialTheme.colorScheme.error; null -> MaterialTheme.colorScheme.onSurfaceVariant }) }
        // 建議設定 Google Places：附近的店、店名搜尋都會準很多
        if (suggestKey && !running) Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("設定 Google Places 金鑰可提升店家搜尋的準確度", fontSize = AppText.secondary, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = onSetup) { Text("前往設定") }
        }
    }
}


/** 常見來源 App 的名稱（手機上沒有安裝、讀不到 App 名稱時用） */
internal val knownAppNames = mapOf(
    "jp.naver.line.android" to "LINE", "com.google.android.gm" to "Gmail", "com.google.android.apps.messaging" to "訊息",
    "com.samsung.android.messaging" to "訊息", "com.cathaybk.mymobibank.android" to "國泰世華", "com.esunbank" to "玉山銀行",
    "com.chb.mobilebank" to "彰化銀行", "tw.com.post.ap" to "行動郵局", "com.jkos.app" to "街口支付",
)

/**
 * 自動記帳準確度（本月）：通知記下的交易中，有幾筆完全沒被修改。
 * 數字只存在這支手機，用來讓你知道自動記帳可以信任到什麼程度、哪一欄最常要改。
 */
@Composable
private fun AutoAccuracyCard(txns: List<com.enwei.moneymanager.data.TxnEntity>) {
    val ym = remember { java.time.YearMonth.now() }
    val zone = remember { java.time.ZoneId.systemDefault() }
    val fixes = remember(txns) { com.enwei.moneymanager.data.AutoAccuracy.corrections(ym) }
    val touched = remember(txns) { com.enwei.moneymanager.data.AutoAccuracy.touched(ym) }
    val auto = remember(txns) {
        txns.count { it.source == com.enwei.moneymanager.data.TxnSource.NOTIFICATION &&
            java.time.YearMonth.from(java.time.Instant.ofEpochMilli(it.date).atZone(zone)) == ym }
    }
    val deleted = fixes[com.enwei.moneymanager.data.AutoAccuracy.Field.DELETED] ?: 0
    val total = auto + deleted
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (total == 0) {
            Text("本月還沒有自動記帳的交易", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        val ok = (total - touched).coerceAtLeast(0)
        Text("本月自動記帳 $total 筆，$ok 筆不用修改（${ok * 100 / total}%）", fontSize = AppText.secondary)
        val parts = com.enwei.moneymanager.data.AutoAccuracy.Field.entries.mapNotNull { f -> fixes[f]?.takeIf { it > 0 }?.let { "${f.label} $it" } }
        if (parts.isNotEmpty()) Text("修改過：" + parts.joinToString("・"), fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
