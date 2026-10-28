package com.enwei.moneymanager.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
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
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.BuildConfig
import com.enwei.moneymanager.core.InvoiceCsvParser
import com.enwei.moneymanager.core.Money
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
    val entries = listOf(
        Triple("config", "設定", Icons.Outlined.Settings),
        Triple("backup", "備份", Icons.Outlined.SettingsBackupRestore),
        Triple("notif", "通知記帳", Icons.Outlined.NotificationsActive),
        Triple("invoice", "電子發票", Icons.AutoMirrored.Outlined.ReceiptLong),
        Triple("invest", "投資", Icons.AutoMirrored.Outlined.ShowChart),
        Triple("recurring", "週期扣款", Icons.Outlined.EventRepeat),
        Triple("fx", "匯率", Icons.Outlined.CurrencyExchange),
        Triple("pending", "待確認", Icons.Outlined.Inbox),
        Triple("settings", "App 更新", Icons.Outlined.SystemUpdate),
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            androidx.compose.material3.TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                title = { Text("設定") },
                actions = {
                    Text(BuildConfig.VERSION_NAME, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 20.sp,
                        modifier = Modifier.padding(end = 16.dp))
                },
            )
        },
    ) { pad ->
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
            Modifier.padding(pad).fillMaxWidth().padding(top = 24.dp),
        ) {
            items(entries.size) { i ->
                val (route, label, icon) = entries[i]
                Column(
                    Modifier.height(130.dp).clickable { nav.navigate(route) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    androidx.compose.material3.BadgedBox(badge = {
                        if (route == "pending" && pending.isNotEmpty()) Badge { Text("${pending.size}") }
                    }) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), modifier = Modifier.size(44.dp)) }
                    Spacer(Modifier.height(14.dp))
                    Text(label, fontSize = 17.sp)
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
    var testText by remember { mutableStateOf("") }
    val labelOf = remember(apps) { apps.associate { it.pkg to it.label } }
    val candidates = log.filter { it.result == "CANDIDATE" && it.packageName !in settings.notifPackages }.map { it.packageName }.distinct()

    fun toggle(pkg: String, on: Boolean) = repo.settings.update {
        it.copy(notifPackages = if (on) it.notifPackages + pkg else it.notifPackages - pkg)
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
                        Text(if (enabled) "✅ 已取得通知存取權" else "⚠️ 尚未開啟通知存取權", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "開啟後，記帳本會讀取你勾選的銀行、信用卡、電子支付（LINE Pay、街口、全支付…）或簡訊 App 的通知，自動辨識金額、商家與卡號末四碼。資料只存在手機中。",
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
                        { "${it.type.icon} ${it.name}" }, { a -> repo.settings.update { it.copy(defaultAccountId = a.id) } },
                    )
                    Text("提示：在帳戶中填寫信用卡「末四碼」，通知就會自動記到對應的卡片。券商的成交回報會自動建立股票交易。", style = MaterialTheme.typography.bodySmall)
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
            item { SectionTitle("測試辨識") }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(testText, { testText = it }, label = { Text("貼上一則通知內容") }, modifier = Modifier.fillMaxWidth())
                    if (testText.isNotBlank()) {
                        val fill = NotificationParser.parseStockFill(null, testText)
                        val p = NotificationParser.parse(null, testText)
                        Text(
                            when {
                                fill != null -> "股票成交：${fill.side} ${fill.symbol} ${fill.name.orEmpty()} ${fill.shares} 股 @ ${fill.price}"
                                p != null -> "${if (p.type.name == "INCOME") "收入" else "支出"} ${Money.format(p.amountMinor, p.currency)}" +
                                    (p.merchant?.let { " · 商家：$it" } ?: "") + (p.cardLast4?.let { " · 末四碼 $it" } ?: "")
                                else -> "無法辨識為交易"
                            },
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
            item { SectionTitle("要監聽的 App（已選 ${settings.notifPackages.size}）") }
            item {
                OutlinedTextField(filter, { filter = it }, label = { Text("搜尋 App") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
            val sorted = apps.filter { filter.isBlank() || it.label.contains(filter, true) || it.pkg.contains(filter, true) }
                .sortedByDescending { it.pkg in settings.notifPackages }
            items(sorted, key = { it.pkg }) { a ->
                val on = a.pkg in settings.notifPackages
                ListItem(
                    headlineContent = { Text(a.label) },
                    supportingContent = { Text(a.pkg, style = MaterialTheme.typography.labelSmall) },
                    trailingContent = { Checkbox(on, { toggle(a.pkg, it) }) },
                    modifier = Modifier.clickable { toggle(a.pkg, !on) },
                )
            }
            item { SectionTitle("最近處理紀錄") }
            items(log.take(30), key = { "log${it.id}" }) { e ->
                val label = when (e.result) {
                    "RECORDED" -> "已記帳"; "STOCK" -> "股票成交"; "DUPLICATE" -> "重複略過"; "UNPARSED" -> "無法辨識"; else -> "未啟用來源"
                }
                ListItem(
                    overlineContent = { Text("${labelOf[e.packageName] ?: e.packageName} · ${timeFmt.format(Date(e.postedAt))} · $label") },
                    headlineContent = { Text(listOfNotNull(e.title, e.text).joinToString(" "), maxLines = 3, style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.clickable(enabled = e.txnId != null && e.result == "RECORDED") { nav.navigate("txn/${e.txnId}") },
                )
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun InvoiceScreen(nav: NavController) {
    val ctx = LocalContext.current
    val lk = rememberLookups()
    val scope = rememberCoroutineScope()
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    var result by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            result = runCatching {
                val text = withContext(Dispatchers.IO) { readTextAutoCharset(ctx, uri) }
                val invoices = InvoiceCsvParser.parse(text)
                if (invoices.isEmpty()) "檔案中沒有找到發票資料，請確認是財政部平台匯出的載具消費明細 CSV。"
                else {
                    val (added, merged) = repo.importInvoices(invoices)
                    "讀到 ${invoices.size} 張發票：新增 $added 筆待確認、合併 $merged 筆既有交易、其餘已匯入過。"
                }
            }.getOrElse { "匯入失敗：${it.message}" }
        }
    }
    Scaffold(topBar = { BackTopBar("電子發票載具", { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("匯入手機條碼載具的消費發票", style = MaterialTheme.typography.titleMedium)
            Text(
                "1. 到「財政部電子發票整合服務平台」登入手機條碼。\n" +
                    "2. 進入「消費／載具 → 載具消費明細查詢」，選擇月份後點「匯出 CSV」（也可設定每月寄送到 Email）。\n" +
                    "3. 回到這裡選擇下載的 CSV 檔。\n\n" +
                    "記帳本會自動：\n• 以發票號碼去重，重複匯入不會重複記帳\n• 與通知已記下的同金額消費合併，補上店名與品項\n• 依店名、品項推測分類，先放在「待確認」",
                style = MaterialTheme.typography.bodyMedium,
            )
            DropdownField(
                "發票預設記入帳戶", lk.accounts.filter { !it.archived }, lk.account(settings.invoiceAccountId ?: settings.defaultAccountId),
                { "${it.type.icon} ${it.name}" }, { a -> repo.settings.update { it.copy(invoiceAccountId = a.id) } },
            )
            Button(onClick = { picker.launch(arrayOf("text/*", "text/csv", "application/vnd.ms-excel", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) {
                Text("選擇 CSV 檔匯入")
            }
            result?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
            OutlinedButton(onClick = { nav.navigate("pending") }, modifier = Modifier.fillMaxWidth()) { Text("查看待確認") }
        }
    }
}

/** 財政部匯出檔有時是 Big5，若 UTF-8 解碼出現亂碼就改用 Big5。 */
private fun readTextAutoCharset(ctx: Context, uri: android.net.Uri): String {
    val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    val utf8 = String(bytes, Charsets.UTF_8)
    return if (utf8.contains('�')) String(bytes, charset("Big5")) else utf8
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
    LaunchedEffect(Unit) { if (System.currentTimeMillis() - settings.lastFxUpdate > 3600_000) refresh() }

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
fun CategoriesScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val cats by repo.categories.collectAsStateWithLifecycle(emptyList())
    val rules by repo.rules.collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    Scaffold(topBar = {
        BackTopBar("分類管理", { nav.popBackStack() }) {
            TextButton(onClick = { editing = CategoryEntity(name = "", kind = TxnType.EXPENSE, icon = "📦", sortOrder = cats.size) }) { Text("新增") }
        }
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            listOf(TxnType.EXPENSE, TxnType.INCOME).forEach { k ->
                item(key = k.name) { SectionTitle(k.label + "分類") }
                items(cats.filter { it.kind == k }, key = { it.id }) { c ->
                    ListItem(
                        headlineContent = { Text("${c.icon} ${c.name}" + if (c.archived) "（隱藏）" else "") },
                        modifier = Modifier.clickable { editing = c },
                    )
                }
            }
            if (rules.isNotEmpty()) {
                item { SectionTitle("已學習的商家分類（點擊刪除）") }
                items(rules, key = { "r" + it.keyword }) { r ->
                    val c = cats.find { it.id == r.categoryId }
                    ListItem(
                        headlineContent = { Text(r.keyword) },
                        trailingContent = { Text("${c?.icon.orEmpty()} ${c?.name ?: "?"}") },
                        modifier = Modifier.clickable { scope.launch { repo.deleteRule(r.keyword) } },
                    )
                }
            }
        }
    }
    editing?.let { c ->
        var name by remember(c) { mutableStateOf(c.name) }
        var icon by remember(c) { mutableStateOf(c.icon) }
        var kind by remember(c) { mutableStateOf(c.kind) }
        var archived by remember(c) { mutableStateOf(c.archived) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (c.id == 0L) "新增分類" else "編輯分類") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("名稱") }, singleLine = true)
                    OutlinedTextField(icon, { icon = it.take(4) }, label = { Text("圖示（Emoji）") }, singleLine = true)
                    if (c.id == 0L) DropdownField("類型", listOf(TxnType.EXPENSE, TxnType.INCOME), kind, { it.label }, { kind = it })
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("隱藏", Modifier.weight(1f)); Switch(archived, { archived = it }) }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch { repo.saveCategory(c.copy(name = name.trim(), icon = icon.ifBlank { "📦" }, kind = kind, archived = archived)) }
                    editing = null
                }) { Text("儲存") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } },
        )
    }
}

@Composable
fun BackupScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var confirmUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = runCatching {
                val json = repo.exportJson()
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(json.toByteArray()) } }
                "已匯出備份"
            }.getOrElse { "匯出失敗：${it.message}" }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmUri = uri }
    Scaffold(topBar = { BackTopBar("備份與還原", { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("所有資料都只存在這支手機。建議定期匯出備份到 Google 雲端硬碟或電腦；換手機時在新手機匯入即可。", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { exporter.launch("記帳本備份-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.json") }, modifier = Modifier.fillMaxWidth()) {
                Text("匯出備份（JSON）")
            }
            OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "application/octet-stream", "text/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text("從備份還原")
            }
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
    confirmUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmUri = null },
            title = { Text("還原備份？") },
            text = { Text("目前手機上的帳戶、交易、週期規則與股票紀錄會被備份內容取代。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUri = null
                    scope.launch {
                        status = runCatching {
                            val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
                            val b = repo.importJson(text)
                            "已還原：${b.accounts.size} 個帳戶、${b.transactions.size} 筆交易、${b.trades.size} 筆股票交易"
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
    var discount by remember { mutableStateOf(settings.feeDiscount.toString()) }
    var minFee by remember { mutableStateOf(settings.minFee.toString()) }
    var usFee by remember { mutableStateOf(settings.usFee.toString()) }
    var token by remember { mutableStateOf(settings.githubToken) }
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var apk by remember { mutableStateOf<File?>(null) }

    fun saveFees() = repo.settings.update {
        it.copy(
            feeDiscount = discount.toDoubleOrNull()?.coerceIn(0.01, 1.0) ?: it.feeDiscount,
            minFee = minFee.toDoubleOrNull() ?: it.minFee,
            usFee = usFee.toDoubleOrNull() ?: it.usFee,
        )
    }

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
            updateStatus = "檢查失敗：${it.message}" + if (token.isBlank()) "\n私人儲存庫需要填入 GitHub Token" else ""
        }
    }

    fun install(file: File) {
        if (!Updater.canInstall(ctx)) {
            updateStatus = "請允許「安裝不明應用程式」後再按一次安裝"
            Updater.openInstallPermission(ctx)
        } else Updater.install(ctx, file)
    }

    Scaffold(topBar = { BackTopBar("設定與更新", { nav.popBackStack() }) }) { pad ->
        Column(
            Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("App 更新", style = MaterialTheme.typography.titleMedium)
            Text("目前版本 ${BuildConfig.VERSION_NAME}（build ${BuildConfig.VERSION_CODE}）", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                token, { token = it }, label = { Text("GitHub Token（私人儲存庫需要）") }, singleLine = true,
                supportingText = { Text("到 GitHub → Settings → Developer settings → Fine-grained tokens 建立，只勾選此儲存庫的 Contents: Read-only") },
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
            HorizontalDivider()

            Text("股票手續費", style = MaterialTheme.typography.titleMedium)
            NumberField(discount, { discount = it; saveFees() }, "台股手續費折數（例：0.6 = 六折，0.28 = 2.8 折）")
            NumberField(minFee, { minFee = it; saveFees() }, "台股最低手續費（整股常見 20 元）")
            NumberField(usFee, { usFee = it; saveFees() }, "美股每筆手續費（USD）")
            Text("台股交易稅：一般股票 0.3%、ETF 0.1%，自動計算。", style = MaterialTheme.typography.bodySmall)
        }
    }
}
