package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.horizontalScroll
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFmt = SimpleDateFormat("M/d HH:mm", Locale.TAIWAN)

@Composable
fun MoreScreen(nav: NavController) {
    val pending by repo.pending.collectAsStateWithLifecycle(emptyList())
    val L = com.enwei.moneymanager.ui.components.LineIcons
    // 依類別分組的功能入口；細部設定集中在「設定」裡
    val groups = listOf(
        "自動記帳" to listOf(
            Triple("notif", "通知記帳", L.Notification),
            Triple("walletreader", "錢包讀取", L.Wallet),
            Triple("pending", "待確認", L.Inbox),
            Triple("reconcile-batch", "截圖對帳", L.Scan),
            Triple("cashcount/0", "現金點鈔", L.Cash),
        ),
        "投資" to listOf(
            Triple("invest", "股票", L.Stocks),
            Triple("advisor", "股票助理", L.Advisor),
        ),
        "工具" to listOf(
            Triple("recurring", "週期扣款", L.Recurring),
            Triple("fx", "匯率", L.Exchange),
            Triple("backup", "備份", L.Backup),
            Triple("config", "設定", L.Settings),
        ),
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            androidx.compose.material3.TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                title = { Text("更多") },
                actions = {
                    Text("Ledgerly " + BuildConfig.VERSION_NAME, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp,
                        modifier = Modifier.padding(end = 16.dp))
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            groups.forEach { (title, entries) ->
                Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 16.dp))
                entries.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { (route, label, icon) ->
                            // 圖示固定在同一高度：英文標籤較長換成兩行時，圖示不會被往上推而對不齊
                            Column(
                                Modifier.weight(1f).height(112.dp).clickable { nav.navigate(route) }.padding(top = 18.dp, start = 4.dp, end = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top,
                            ) {
                                androidx.compose.material3.BadgedBox(badge = {
                                    if (route == "pending" && pending.isNotEmpty()) Badge { Text("${pending.size}") }
                                }) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), modifier = Modifier.size(34.dp)) }
                                Spacer(Modifier.height(10.dp))
                                Text(label, fontSize = 15.sp, lineHeight = 18.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
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
    var testText by remember { mutableStateOf("") }
    var testTitle by remember { mutableStateOf("") }
    var testPkg by remember { mutableStateOf<String?>(null) }
    var pasted by remember { mutableStateOf<TxnNotificationListener.ActiveNote?>(null) }
    var pastedPreview by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val labelOf = remember(apps) { apps.associate { it.pkg to it.label } }
    val candidates = log.filter { it.result == "CANDIDATE" && it.packageName !in settings.notifPackages }.map { it.packageName }.distinct()
    val testScope = rememberCoroutineScope()
    var notes by remember { mutableStateOf<List<TxnNotificationListener.ActiveNote>?>(null) }
    var previews by remember { mutableStateOf<List<Pair<Boolean, String>>>(emptyList()) }
    var testMsg by remember { mutableStateOf<String?>(null) }
    var teach by remember { mutableStateOf<TxnNotificationListener.ActiveNote?>(null) }
    fun loadNotes() = testScope.launch {
        val n = TxnNotificationListener.activeNotes()
        testMsg = if (n == null) "還讀不到通知列：請確認已開啟通知存取權（開啟後可能要稍等幾秒）" else if (n.isEmpty()) "通知列目前沒有通知" else null
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
        Card(
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
                    TextButton(onClick = {
                        clip.setText(androidx.compose.ui.text.AnnotatedString(repo.SENSITIVE_ADB))
                        testMsg = "已複製指令"
                    }) { Text("複製指令") }
                } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (parsedOk) OutlinedButton(onClick = {
                        testScope.launch {
                            val r = repo.ingestNotification(nn.pkg, nn.title, nn.text, nn.postedAt, force = true)
                            testMsg = r?.let { "已記下：${it.message}" } ?: "沒有記下（可能已經記過了）"
                        }
                    }) { Text("記下這筆") }
                    if (!desc.startsWith("LINE 聊天")) TextButton(onClick = { teach = nn }) { Text("教它辨識") }
                }
            }
        }
    }

    Scaffold(topBar = { BackTopBar("通知自動記帳", { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad).imePadding()) {
            item {
                Card(
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
                        Button(onClick = { TxnNotificationListener.openSettings(ctx) }) { Text(if (enabled) "通知存取設定" else "前往開啟") }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("直接入帳（不經確認）")
                            Text("關閉時會先放在「待確認」", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(settings.notifAutoConfirm, { v -> repo.settings.update { it.copy(notifAutoConfirm = v) } })
                    }
                    DropdownField(
                        "無法對應卡號時記入的帳戶", lk.accounts.filter { !it.archived }, lk.account(settings.defaultAccountId),
                        { it.name }, { a -> repo.settings.update { it.copy(defaultAccountId = a.id) } },
                    )
                    Text("信用卡填寫「末四碼」，通知會自動記到對應的卡片。", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (candidates.isNotEmpty()) {
                item { SectionTitle("偵測到可能的交易通知來源") }
                items(candidates, key = { "cand$it" }) { pkg ->
                    ListItem(
                        headlineContent = { Text(labelOf[pkg] ?: pkg) },
                        supportingContent = { Text(log.first { it.packageName == pkg }.let { listOfNotNull(it.title, it.text).joinToString(" ") }, maxLines = 2) },
                        trailingContent = { TextButton(onClick = { toggle(pkg, true) }) { Text("啟用") } },
                    )
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
                        if (on) {
                            // 這個 App 的通知記到哪個帳戶（信用卡會優先用末四碼對應）
                            var menu by remember { mutableStateOf(false) }
                            Box {
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
                        } else Text(a.pkg, style = MaterialTheme.typography.labelSmall)
                    },
                    trailingContent = { Checkbox(on, { toggle(a.pkg, it) }) },
                    modifier = Modifier.clickable { toggle(a.pkg, !on) },
                )
            }
            item { SectionTitle("測試通知辨識") }
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
            if (settings.learnedSources.isNotEmpty()) {
                item { SectionTitle("教過的通知格式（點擊刪除）") }
                items(settings.learnedSources.size, key = { "learn$it" }) { i ->
                    val l = settings.learnedSources[i]
                    ListItem(
                        headlineContent = { Text("${labelOf[l.pkg] ?: l.pkg}：「${l.title}」") },
                        supportingContent = {
                            Text("${l.label} → ${l.accountId?.let { lk.account(it)?.name } ?: "依卡號／預設帳戶"}" +
                                (l.toAccountId?.let { lk.account(it)?.name }?.let { if (l.parsedKind == com.enwei.moneymanager.core.ParsedKind.TRANSFER_IN) "（從 $it）" else "（到 $it）" } ?: ""))
                        },
                        modifier = Modifier.clickable { repo.settings.update { it.copy(learnedSources = it.learnedSources - l) } },
                    )
                }
            }
            item { SectionTitle("最近處理紀錄") }
            items(log.take(30), key = { "log${it.id}" }) { e ->
                val label = when (e.result) {
                    "RECORDED" -> "已記帳"; "STOCK" -> "股票成交"; "DUPLICATE" -> "重複略過"; "MERGED" -> "合併為轉帳"; "UNPARSED" -> "無法辨識"; else -> "未啟用來源"
                }
                ListItem(
                    overlineContent = { Text("${labelOf[e.packageName] ?: e.packageName} · ${timeFmt.format(Date(e.postedAt))} · $label") },
                    headlineContent = { Text(listOfNotNull(e.title, e.text).joinToString(" "), maxLines = 3, style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.clickable(enabled = (e.txnId != null && e.result == "RECORDED") || e.result == "UNPARSED" || e.result == "CANDIDATE") {
                        if (e.result == "RECORDED") nav.navigate("txn/${e.txnId}")
                        else teach = TxnNotificationListener.ActiveNote(e.packageName, e.title, e.text, e.postedAt)
                    },
                )
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
            title = { Text("教它辨識") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("之後「${labelOf[nn.pkg] ?: nn.pkg}」標題為「${com.enwei.moneymanager.data.learnKey(nn.title, nn.text)}」的通知，會照下面的設定記帳。",
                        style = MaterialTheme.typography.bodySmall)
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
                TextButton(enabled = amount != null, onClick = {
                    val key = com.enwei.moneymanager.data.learnKey(nn.title, nn.text)
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
    var from by remember { mutableStateOf("USD") }

    fun refresh() = scope.launch {
        status = "更新中…"
        status = runCatching { SyncWorker.refreshFx(repo) }.fold({ "已更新" }, { "更新失敗：${it.message}" })
    }
    // 畫面開著時每分鐘更新即時匯率
    LaunchedEffect(Unit) {
        while (true) {
            status = runCatching { SyncWorker.refreshFx(repo) }.fold({ "即時報價" }, { "更新失敗：${it.message}" })
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
                        DropdownField("幣別", FxService.common, from, { it }, { from = it }, Modifier.width(120.dp))
                    }
                    Money.parseMinor(amount)?.let {
                        Text("= ${Money.format(lk.fx.convert(it, from), settings.baseCurrency)} ${settings.baseCurrency}", style = MaterialTheme.typography.titleLarge)
                    }
                }
                HorizontalDivider()
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
            }.getOrElse { "匯出失敗：${it.message}" }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmUri = uri }
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
            }.getOrElse { "匯出失敗：${it.message}" }
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
            }.getOrElse { "匯入失敗：${it.message}" }
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
            Text("選雲端硬碟或手機裡的檔案，定期自動覆寫完整備份。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            HorizontalDivider()
            Text("帳理完整備份（.lgbak）", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { exporter.launch("帳理備份-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.lgbak") }, modifier = Modifier.fillMaxWidth()) {
                Text("匯出完整備份（.lgbak）")
            }
            OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text("從備份還原（.lgbak 或舊版 JSON）")
            }
            HorizontalDivider()
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
            text = { Text("「合併」會保留帳理現有資料，已匯入過的交易不會重複；「取代」會先清空帳理的帳戶、分類、交易與週期設定（股票紀錄保留）。") },
            confirmButton = { TextButton(onClick = { mmUri = null; runMmImport(uri, replace = false) }) { Text("合併") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { mmUri = null; runMmImport(uri, replace = true) }) { Text("取代") }
                    TextButton(onClick = { mmUri = null }) { Text("取消") }
                }
            },
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
                        status = runCatching {
                            val bytes = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
                            // gzip 開頭 1F 8B：帳理完整備份；否則當作舊版 JSON
                            if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
                                val b = withContext(Dispatchers.IO) { com.enwei.moneymanager.data.FullBackupIO.read(bytes.inputStream()) }
                                com.enwei.moneymanager.data.FullBackupIO.restore(repo, b)
                                com.enwei.moneymanager.notify.QuickAdd.sync(ctx, repo.settings.value.quickAdd)
                                "已還原完整備份：${b.accounts.size} 個帳戶、${b.transactions.size} 筆交易、${b.trades.size} 筆股票紀錄與所有設定"
                            } else {
                                val b = repo.importJson(bytes.decodeToString())
                                "已還原：${b.accounts.size} 個帳戶、${b.transactions.size} 筆交易、${b.trades.size} 筆股票交易"
                            }
                        }.getOrElse { "還原失敗：${it.message}" }
                    }
                }) { Text("還原") }
            },
            dismissButton = { TextButton(onClick = { confirmUri = null }) { Text("取消") } },
        )
    }
}

@Composable
fun SettingsScreen(nav: NavController) {
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
                Updater.isNewer(r) -> "有新版本 ${r.tag}"
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

    Scaffold(topBar = { BackTopBar("App 更新", { nav.popBackStack() }) }) { pad ->
        Column(
            Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("App 更新", style = MaterialTheme.typography.titleMedium)
            Text("目前版本 ${BuildConfig.VERSION_NAME}（build ${BuildConfig.VERSION_CODE}）", style = MaterialTheme.typography.bodySmall)
            if (settings.updateCheckLog.isNotBlank()) Text("自動檢查：${settings.updateCheckLog}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                token, { v -> token = v; repo.settings.update { it.copy(githubToken = v.trim()) } }, label = { Text("GitHub Token（私人儲存庫需要）") }, singleLine = true,
                supportingText = { Text("私人儲存庫才需要（權限 Contents: Read-only）") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                Button(onClick = { check() }) { Text("檢查更新") }
                Spacer(Modifier.width(8.dp))
                val r = release
                if (r != null && Updater.isNewer(r)) Button(enabled = progress == null, onClick = {
                    scope.launch {
                        progress = 0f
                        runCatching { Updater.download(ctx, r, token) { p -> progress = p } }
                            .onSuccess { apk = it; progress = null; install(it) }
                            .onFailure { progress = null; updateStatus = "下載失敗：${it.message}" }
                    }
                }) { Text("下載並安裝") }
                apk?.let { f -> Spacer(Modifier.width(8.dp)); OutlinedButton(onClick = { install(f) }) { Text("安裝") } }
            }
            progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
            updateStatus?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            release?.notes?.takeIf { it.isNotBlank() && Updater.isNewer(release!!) }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** 錢包畫面讀取設定：給不推播消費的街口、悠遊付等錢包使用。 */
@Composable
fun WalletReaderScreen(nav: NavController) {
    val ctx = LocalContext.current
    val lk = rememberLookups()
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    var enabled by remember { mutableStateOf(com.enwei.moneymanager.notify.WalletScreenReader.isEnabled(ctx)) }
    OnResume { enabled = com.enwei.moneymanager.notify.WalletScreenReader.isEnabled(ctx) }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.Default) { installedApps(ctx) } }
    var filter by remember { mutableStateOf("") }
    var showAllApps by remember { mutableStateOf(false) }
    val wallets = lk.accounts.filter { it.type == com.enwei.moneymanager.data.AccountType.EWALLET && !it.archived }
    val today = java.time.LocalDate.now().toEpochDay()
    val testMode by com.enwei.moneymanager.notify.WalletScreenReader.testMode.collectAsStateWithLifecycle()
    val scans by com.enwei.moneymanager.notify.WalletScreenReader.scans.collectAsStateWithLifecycle()

    fun toggle(pkg: String, on: Boolean) = repo.settings.update {
        it.copy(
            screenPackages = if (on) it.screenPackages + pkg else it.screenPackages - pkg,
            screenReadSince = if (on && pkg !in it.screenReadSince) it.screenReadSince + (pkg to today) else it.screenReadSince,
        )
    }

    Scaffold(topBar = { BackTopBar("錢包畫面讀取", { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.enwei.moneymanager.ui.components.StatusLine(enabled, if (enabled) "錢包讀取已開啟" else "尚未開啟無障礙服務「帳理錢包讀取」")
                        Text("打開錢包 App 的交易紀錄頁就會自動匯入，重複的會合併。", style = MaterialTheme.typography.bodySmall)
                        if (!enabled) Text(
                            "無法開啟時：設定 → 應用程式 → 帳理 → ⋮ →「允許受限制的設定」。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { com.enwei.moneymanager.notify.WalletScreenReader.openSettings(ctx) }) {
                            Text(if (enabled) "無障礙設定" else "前往開啟")
                        }
                    }
                }
            }
            if (wallets.isEmpty()) item {
                Text("請先新增「電子支付」帳戶（例如街口、悠遊付），匯入的交易才有地方記。",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            item { SectionTitle("要讀取的錢包 App（已選 ${settings.screenPackages.size}）") }
            item {
                if (showAllApps) OutlinedTextField(filter, { filter = it }, label = { Text("搜尋 App") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                TextButton(onClick = { showAllApps = !showAllApps; filter = "" }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(if (showAllApps) "只顯示已選的 App" else "＋ 新增要讀取的錢包 App")
                }
            }
            val sorted = apps.filter { (showAllApps || it.pkg in settings.screenPackages) && (filter.isBlank() || it.label.contains(filter, true) || it.pkg.contains(filter, true)) }
                .sortedByDescending { it.pkg in settings.screenPackages }
            items(sorted, key = { "w" + it.pkg }) { a ->
                val on = a.pkg in settings.screenPackages
                ListItem(
                    headlineContent = { Text(a.label) },
                    supportingContent = {
                        if (on) Column {
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                Text(
                                    "記入：" + (settings.notifAccountMap[a.pkg]?.let { lk.account(it)?.name } ?: wallets.singleOrNull()?.name ?: "請選擇錢包帳戶") + " ▾",
                                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.clickable { menu = true }.padding(vertical = 4.dp),
                                )
                                androidx.compose.material3.DropdownMenu(menu, { menu = false }) {
                                    wallets.forEach { w ->
                                        androidx.compose.material3.DropdownMenuItem(text = { Text(w.name) }, onClick = {
                                            repo.settings.update { it.copy(notifAccountMap = it.notifAccountMap + (a.pkg to w.id)) }; menu = false
                                        })
                                    }
                                }
                            }
                            val since = settings.screenReadSince[a.pkg]?.let(java.time.LocalDate::ofEpochDay)
                            Text(
                                "匯入 ${since ?: "今天"} 之後的紀錄　（改為過去 30 天）",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable {
                                    repo.settings.update { it.copy(screenReadSince = it.screenReadSince + (a.pkg to today - 30)) }
                                }.padding(vertical = 4.dp),
                            )
                        } else Text(a.pkg, style = MaterialTheme.typography.labelSmall)
                    },
                    trailingContent = { Checkbox(on, { toggle(a.pkg, it) }) },
                    modifier = Modifier.clickable { toggle(a.pkg, !on) },
                )
            }
            // 測試：確認讀取是否真的有運作
            item { SectionTitle("測試錢包讀取") }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("測試模式（只讀取，不入帳）")
                            Text("打開錢包的交易紀錄頁後回來查看", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(testMode, { com.enwei.moneymanager.notify.WalletScreenReader.testMode.value = it })
                    }
                    val connected = com.enwei.moneymanager.notify.WalletScreenReader.connected
                    com.enwei.moneymanager.ui.components.StatusLine(
                        enabled && connected,
                        when {
                            !enabled -> "無障礙服務未開啟"
                            !connected -> "服務已開啟但尚未連線（關閉再開啟一次無障礙服務）"
                            settings.screenPackages.isEmpty() -> "服務運作中，但還沒有勾選要讀取的錢包 App"
                            else -> "服務運作中"
                        },
                    )
                    if (scans.isEmpty()) Text("還沒有讀取紀錄", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(scans.size, key = { "scan$it" }) { i ->
                val e = scans[i]
                val label = apps.find { it.pkg == e.pkg }?.label ?: e.pkg
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("$label · ${timeFmt.format(Date(e.time))} · 讀到 ${e.lines} 行文字", style = MaterialTheme.typography.labelSmall)
                        e.problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        if (e.byAi) Text("版面規則看不懂，由 AI 從畫面整理出 ${e.records.size} 筆", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                        e.result?.let { r ->
                            Text(
                                if (r.wallet == null) "辨識出 ${r.total} 筆，但找不到要記入的電子支付帳戶（請在下方指定）"
                                else "辨識出 ${r.total} 筆 → 記入「${r.wallet}」：" +
                                    "${if (testMode) "會新增" else "新增"} ${r.created} 筆、已存在 ${r.existing} 筆" +
                                    (if (r.merged > 0) "、和通知／手動記的對上 ${r.merged} 筆（不重複記）" else "") +
                                    (if (r.tooOld > 0) "、早於開始日 ${r.since} 略過 ${r.tooOld} 筆" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (r.wallet == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                        }
                        var expanded by remember(e.time) { mutableStateOf(false) }
                        val details = e.result?.details.orEmpty()
                        if (details.isNotEmpty()) {
                            // 每一筆的處理結果：新增、已匯入、和通知對上（補名稱）…
                            details.take(if (expanded) details.size else 6).forEach { d ->
                                Text(
                                    d, style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        "同一筆" in d || "轉帳" in d -> MaterialTheme.colorScheme.tertiary
                                        "新增" in d -> MaterialTheme.colorScheme.onSurface
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            if (details.size > 6) Text(
                                if (expanded) "收起" else "顯示全部 ${details.size} 筆", color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 2.dp),
                            )
                        } else {
                            e.records.take(if (expanded) e.records.size else 6).forEach { rec ->
                                Text(
                                    "${rec.time.toLocalDate()}${if (rec.dateOnly) "" else " %02d:%02d".format(rec.time.hour, rec.time.minute)}　" +
                                        "${rec.merchant ?: rec.typeLabel ?: ""}　${if (rec.isIncome || rec.isTopUp) "+" else "-"}${Money.format(rec.amountMinor, "TWD")}",
                                    style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                )
                            }
                            if (e.records.size > 6) Text(
                                if (expanded) "收起" else "顯示全部 ${e.records.size} 筆", color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 現金點鈔：輸入各面額數量，算出總額並可對帳。 */
@Composable
fun CashCounterScreen(nav: NavController, accountIdArg: Long) {
    val scope = rememberCoroutineScope()
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val balances by repo.balances.collectAsStateWithLifecycle(emptyList())
    // 從「更多」進來時（id 0）用第一個現金帳戶
    val accountId = if (accountIdArg > 0) accountIdArg
    else balances.firstOrNull { it.account.type == com.enwei.moneymanager.data.AccountType.CASH && !it.account.archived }?.account?.id ?: 0L
    val b = balances.find { it.account.id == accountId }
    val denoms = listOf(2000, 1000, 500, 200, 100, 50, 10, 5, 1)
    var counts by remember(settings.cashCounts[accountId]) { mutableStateOf(settings.cashCounts[accountId].orEmpty()) }
    val total = denoms.sumOf { it.toLong() * (counts[it] ?: 0) } * 100
    val recorded = b?.balance ?: 0
    var done by remember { mutableStateOf<String?>(null) }

    fun set(d: Int, n: Int) {
        counts = counts + (d to n.coerceAtLeast(0))
        repo.settings.update { it.copy(cashCounts = it.cashCounts + (accountId to counts)) }
    }

    // 拍照點鈔：AI 數出照片裡各面額的數量，確認後套用
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var photoMsg by remember { mutableStateOf<String?>(null) }
    var photoCounts by remember { mutableStateOf<Map<Int, Int>?>(null) }
    var counting by remember { mutableStateOf(false) }
    fun countPhoto(uri: android.net.Uri) {
        counting = true; photoMsg = null; photoCounts = null
        scope.launch {
            val r = com.enwei.moneymanager.net.GroqAi.countCash(ctx, uri)
            if (r == null || r.first.isEmpty()) photoMsg = "數不出來：${com.enwei.moneymanager.net.GroqAi.lastError ?: "請把鈔票硬幣攤開、光線充足再拍一次"}"
            else { photoCounts = r.first; photoMsg = r.second.takeIf { it.isNotBlank() } }
            counting = false
        }
    }
    val photoUri = remember {
        val f = java.io.File(ctx.cacheDir, "photos").apply { mkdirs() }.let { java.io.File(it, "cash.jpg") }
        androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    }
    val takePhoto = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { ok -> if (ok) countPhoto(photoUri) }
    val pickPhoto = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) countPhoto(u) }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface, topBar = { BackTopBar("點鈔：${b?.account?.name.orEmpty()}", { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                // 用紙鈔硬幣記一筆：付出／收到的鈔票硬幣與找零會自動增減這裡的張數
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { nav.navigate("txn/0?type=EXPENSE&account=$accountId&cash=true") }, modifier = Modifier.weight(1f)) { Text("現金付款") }
                    OutlinedButton(onClick = { nav.navigate("txn/0?type=INCOME&account=$accountId&cash=true") }, modifier = Modifier.weight(1f)) { Text("收到現金") }
                }
                if (com.enwei.moneymanager.net.GroqAi.enabled) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !counting, onClick = { takePhoto.launch(photoUri) }, modifier = Modifier.weight(1f)) { Text(if (counting) "點鈔中…" else "拍照點鈔") }
                    OutlinedButton(enabled = !counting, onClick = {
                        pickPhoto.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, modifier = Modifier.weight(1f)) { Text("選照片") }
                }
                photoCounts?.let { pc ->
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("AI 數到：" + denoms.filter { (pc[it] ?: 0) > 0 }.joinToString("、") { "$it 元 ×${pc[it]}" } +
                            "，共 ${Money.withSymbol(denoms.sumOf { it.toLong() * (pc[it] ?: 0) } * 100)}", color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                counts = pc
                                repo.settings.update { it.copy(cashCounts = it.cashCounts + (accountId to pc)) }
                                photoCounts = null; photoMsg = "已套用，請再核對一次數量"
                            }) { Text("套用") }
                            OutlinedButton(onClick = {
                                val merged = denoms.associateWith { (counts[it] ?: 0) + (pc[it] ?: 0) }.filterValues { it > 0 }
                                counts = merged
                                repo.settings.update { it.copy(cashCounts = it.cashCounts + (accountId to merged)) }
                                photoCounts = null; photoMsg = "已加到目前的數量"
                            }) { Text("加到目前數量") }
                        }
                    }
                }
                photoMsg?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp)) }
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("點鈔合計", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.withSymbol(total), fontSize = 28.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("帳上餘額 ${Money.withSymbol(recorded)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val diff = total - recorded
                        Text(
                            if (diff == 0L) "相符" else "差額 ${Money.withSymbol(diff)}",
                            color = if (diff == 0L) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                HorizontalDivider()
            }
            items(denoms, key = { it }) { d ->
                val n = counts[d] ?: 0
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.width(110.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (d >= 100) androidx.compose.material.icons.Icons.Rounded.Payments else androidx.compose.material.icons.Icons.Rounded.Toll, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("$d 元", fontSize = 17.sp)
                    }
                    IconButton(onClick = { set(d, n - 1) }) { Text("－", fontSize = 22.sp) }
                    androidx.compose.foundation.text.BasicTextField(
                        value = if (n == 0) "" else n.toString(),
                        onValueChange = { t -> set(d, t.filter(Char::isDigit).take(5).toIntOrNull() ?: 0) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.width(64.dp),
                        decorationBox = { inner -> Box(contentAlignment = Alignment.Center) { if (n == 0) Text("0", color = MaterialTheme.colorScheme.outlineVariant, fontSize = 20.sp); inner() } },
                    )
                    IconButton(onClick = { set(d, n + 1) }) { Text("＋", fontSize = 22.sp) }
                    Text(Money.full(d.toLong() * n * 100), Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { counts = emptyMap(); repo.settings.update { it.copy(cashCounts = it.cashCounts - accountId) } },
                        modifier = Modifier.weight(1f)) { Text("清除") }
                    Button(
                        enabled = b != null && total != recorded,
                        onClick = {
                            val acc = b?.account ?: return@Button
                            scope.launch {
                                val other = repo.categories.first().firstOrNull { it.kind == com.enwei.moneymanager.data.TxnType.EXPENSE && it.name == "其他" }?.id
                                repo.reconcile(acc, recorded, total, if (total < recorded) other else null)
                                done = "已將帳上餘額調整為 ${Money.withSymbol(total)}"
                            }
                        },
                        modifier = Modifier.weight(2f),
                    ) { Text("以點鈔金額對帳") }
                }
                done?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 20.dp)) }
                Text(
                    "現金記帳時，付出、收到與找零會自動增減這裡的張數。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
}
