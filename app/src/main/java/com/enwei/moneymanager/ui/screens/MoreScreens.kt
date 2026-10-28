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
import androidx.compose.foundation.layout.Box
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
    val entries = listOf(
        Triple("config", "設定", com.enwei.moneymanager.ui.components.LineIcons.Settings),
        Triple("backup", "備份", com.enwei.moneymanager.ui.components.LineIcons.Backup),
        Triple("notif", "通知記帳", com.enwei.moneymanager.ui.components.LineIcons.Notification),
        Triple("walletreader", "錢包讀取", com.enwei.moneymanager.ui.components.LineIcons.Wallet),
        Triple("invest", "股票", com.enwei.moneymanager.ui.components.LineIcons.Stocks),
        Triple("recurring", "週期扣款", com.enwei.moneymanager.ui.components.LineIcons.Recurring),
        Triple("fx", "匯率", com.enwei.moneymanager.ui.components.LineIcons.Exchange),
        Triple("pending", "待確認", com.enwei.moneymanager.ui.components.LineIcons.Inbox),
        Triple("settings", "App 更新", com.enwei.moneymanager.ui.components.LineIcons.Update),
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
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
            Modifier.padding(pad).fillMaxWidth().padding(top = 24.dp),
        ) {
            items(entries.size) { i ->
                val (route, label, icon) = entries[i]
                Column(
                    Modifier.height(112.dp).clickable { nav.navigate(route) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    androidx.compose.material3.BadgedBox(badge = {
                        if (route == "pending" && pending.isNotEmpty()) Badge { Text("${pending.size}") }
                    }) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), modifier = Modifier.size(34.dp)) }
                    Spacer(Modifier.height(10.dp))
                    Text(label, fontSize = 15.sp)
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
    val testScope = rememberCoroutineScope()
    var notes by remember { mutableStateOf<List<TxnNotificationListener.ActiveNote>?>(null) }
    var previews by remember { mutableStateOf<List<Pair<Boolean, String>>>(emptyList()) }
    var testMsg by remember { mutableStateOf<String?>(null) }
    var teach by remember { mutableStateOf<TxnNotificationListener.ActiveNote?>(null) }
    fun loadNotes() = testScope.launch {
        val n = TxnNotificationListener.activeNotes()
        testMsg = if (n == null) "還讀不到通知列：請確認已開啟通知存取權（開啟後可能要稍等幾秒）" else if (n.isEmpty()) "通知列目前沒有通知" else null
        val list = n.orEmpty()
        val pv = list.map { repo.previewNotification(it.pkg, it.title, it.text) }
        // 看得出是交易的排前面
        val order = list.indices.sortedBy { i -> if (pv[i].second.startsWith("看不出") || pv[i].second.startsWith("LINE 聊天")) 1 else 0 }
        notes = order.map { list[it] }
        previews = order.map { pv[it] }
    }

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
                        com.enwei.moneymanager.ui.components.StatusLine(enabled, if (enabled) "已取得通知存取權" else "尚未開啟通知存取權")
                        Text(
                            "開啟後，帳理會讀取你勾選的銀行、信用卡、電子支付或簡訊 App 的通知，自動辨識金額、商家與卡號末四碼。資料只存在手機中。\n若顯示「受限制的設定」無法開啟：設定 → 應用程式 → 帳理 → 右上角 ⋮ →「允許受限制的設定」。",
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
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("自動轉帳與沒有通知的錢包", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "• 跨行提款／ATM 領出 → 記成「銀行 → 現金」轉帳\n" +
                                "• 銀行「電支交易」扣款（例如街口自動儲值）→ 記成「銀行 → 錢包」轉帳；錢包名稱需含「街口」「悠遊付」等字樣，或只有一個電子支付帳戶\n" +
                                "• 兩個自己的帳戶一邊轉出、一邊轉入，金額相同且 10 分鐘內 → 合併成一筆轉帳\n" +
                                "• 街口、悠遊付等不推播消費的錢包：用「更多 → 錢包讀取」，打開錢包的交易紀錄頁就會自動匯入；或在帳戶明細「對帳」\n" +
                                "• 信用卡自動扣繳通知：校正排程扣繳的金額，不會重複記帳\n" +
                                "• 通知內有交易時間時，以「金額＋時間」去重，重複跳出的通知不會重複記帳",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item { SectionTitle("用通知列上的真實通知測試（不會記帳）") }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "不用刷卡也能確認：讀取通知列上現有的通知，顯示每一則「會被怎麼記」。漏記的可以按「記下這筆」補記；" +
                            "看不懂的格式可以按「教它辨識」，之後同樣標題的通知就會照你教的記帳。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = { loadNotes() }) { Text(if (notes == null) "讀取目前通知" else "重新讀取") }
                    testMsg?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                }
            }
            notes?.let { list ->
                items(list.size, key = { "note$it" }) { i ->
                    val nn = list[i]
                    val (ok, desc) = previews.getOrNull(i) ?: (false to "")
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
            }
            if (settings.learnedSources.isNotEmpty()) {
                item { SectionTitle("教過的通知格式（點擊刪除）") }
                items(settings.learnedSources.size, key = { "learn$it" }) { i ->
                    val l = settings.learnedSources[i]
                    ListItem(
                        headlineContent = { Text("${labelOf[l.pkg] ?: l.pkg}：「${l.title}」") },
                        supportingContent = { Text("${if (l.income) "收入" else "支出"} → ${l.accountId?.let { lk.account(it)?.name } ?: "依卡號／預設帳戶"}") },
                        modifier = Modifier.clickable { repo.settings.update { it.copy(learnedSources = it.learnedSources - l) } },
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
                                p != null -> when (p.kind) {
                                    com.enwei.moneymanager.core.ParsedKind.WITHDRAWAL -> "提款（轉帳到現金）"
                                    com.enwei.moneymanager.core.ParsedKind.TOPUP_OUT -> "電支儲值扣款（轉帳到錢包${p.walletHint?.let { "：$it" } ?: ""}）"
                                    com.enwei.moneymanager.core.ParsedKind.TOPUP_IN -> "錢包儲值入帳${p.walletHint?.let { "：$it" } ?: ""}"
                                    com.enwei.moneymanager.core.ParsedKind.TRANSFER_OUT -> "轉出（若有對應轉入會合併為轉帳）"
                                    com.enwei.moneymanager.core.ParsedKind.TRANSFER_IN -> "轉入（若有對應轉出會合併為轉帳）"
                                    else -> if (p.type.name == "INCOME") "收入" else "支出"
                                } + " ${Money.format(p.amountMinor, p.currency)}" + (p.occurredAt?.let { " · 交易時間 $it" } ?: "") +
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
        var income by remember(nn) { mutableStateOf(false) }
        var accId by remember(nn) { mutableStateOf(settings.notifAccountMap[nn.pkg]) }
        val amount = NotificationParser.findAmount(listOfNotNull(nn.title, nn.text).joinToString(" "))
        AlertDialog(
            onDismissRequest = { teach = null },
            title = { Text("教它辨識") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("之後「${labelOf[nn.pkg] ?: nn.pkg}」標題為「${com.enwei.moneymanager.data.learnKey(nn.title, nn.text)}」的通知，會照下面的設定記帳。",
                        style = MaterialTheme.typography.bodySmall)
                    if (amount == null) Text("這則通知裡找不到金額，無法自動記帳。", color = MaterialTheme.colorScheme.error)
                    else Text("找到的金額：${Money.format(amount.first, amount.second)} ${amount.second}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!income, { income = false }, label = { Text("支出") })
                        FilterChip(income, { income = true }, label = { Text("收入") })
                    }
                    DropdownField(
                        "記到帳戶", lk.accounts.filter { !it.archived }, lk.account(accId), { it.name }, { accId = it.id },
                        noneLabel = "依卡號／預設帳戶", onNone = { accId = null },
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = amount != null, onClick = {
                    val key = com.enwei.moneymanager.data.learnKey(nn.title, nn.text)
                    repo.settings.update { st ->
                        st.copy(learnedSources = st.learnedSources.filterNot { it.pkg == nn.pkg && it.title == key } +
                            com.enwei.moneymanager.data.LearnedSource(nn.pkg, key, income, accId))
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
                "已匯出完整備份（所有帳戶、交易、股票、週期、分類、規則與設定）"
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
            Text("自動雲端備份", style = MaterialTheme.typography.titleMedium)
            Text(
                "選一個備份檔位置（Google 雲端硬碟、OneDrive、Dropbox 或手機資料夾都可以），帳理會定期把完整備份覆寫到那個檔案，不需要任何帳號設定或金鑰。" +
                    "另外，Android 系統的「Google 備份」也會自動備份帳理的資料（設定 → 系統 → 備份），重新安裝時可自動還原。",
                style = MaterialTheme.typography.bodyMedium,
            )
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
            Text("Money Manager 備份檔（.mmbak）", style = MaterialTheme.typography.titleMedium)
            Text(
                "在 Money Manager 的「更多 → 備份 → 匯出」取得 .mmbak 檔，在這裡匯入即可搬移所有帳戶、分類（含子分類）、交易、轉帳、餘額調整與備忘。也可以把帳理的資料匯出成 .mmbak。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(enabled = !busy, onClick = { mmImporter.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("從 .mmbak 匯入") }
            OutlinedButton(enabled = !busy, onClick = { mmExporter.launch("帳理-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.mmbak") },
                modifier = Modifier.fillMaxWidth()) { Text("匯出為 .mmbak") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            HorizontalDivider()
            Text("帳理完整備份（.lgbak）", style = MaterialTheme.typography.titleMedium)
            Text(
                "帳理專屬的備份檔，包含所有帳戶、交易、股票、週期、分類、商家規則、通知紀錄與全部設定。所有資料都只存在這支手機，建議定期匯出到雲端硬碟；換手機時在新手機還原即可。" +
                    "（GitHub Token 不會放進備份；股價與匯率會自動重新抓取）",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { exporter.launch("帳理備份-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.lgbak") }, modifier = Modifier.fillMaxWidth()) {
                Text("匯出完整備份（.lgbak）")
            }
            OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text("從備份還原（.lgbak 或舊版 JSON）")
            }
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
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
    var discount by remember { mutableStateOf(settings.feeDiscount.toString()) }
    var minFee by remember { mutableStateOf(settings.minFee.toString()) }
    var usFee by remember { mutableStateOf(settings.usFee.toString()) }
    var oddMin by remember { mutableStateOf(settings.oddLotMinFee.toString()) }
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
            oddLotMinFee = oddMin.toDoubleOrNull() ?: it.oddLotMinFee,
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

    Scaffold(topBar = { BackTopBar("設定與更新", { nav.popBackStack() }) }) { pad ->
        Column(
            Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("App 更新", style = MaterialTheme.typography.titleMedium)
            Text("目前版本 ${BuildConfig.VERSION_NAME}（build ${BuildConfig.VERSION_CODE}）", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                token, { v -> token = v; repo.settings.update { it.copy(githubToken = v.trim()) } }, label = { Text("GitHub Token（私人儲存庫需要）") }, singleLine = true,
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
            Text(
                "安裝時若出現「應用程式與現有套件衝突」，表示目前手機上的版本簽章不同（1.0.15 以前的版本）。" +
                    "請先到 更多 → 備份 匯出備份，解除安裝後再安裝新版並還原；之後的版本都能直接覆蓋更新。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()

            Text("股票手續費", style = MaterialTheme.typography.titleMedium)
            NumberField(discount, { discount = it; saveFees() }, "台股手續費折數（1 = 不打折）")
            NumberField(minFee, { minFee = it; saveFees() }, "台股整股最低手續費（常見 20 元）")
            NumberField(oddMin, { oddMin = it; saveFees() }, "台股零股最低手續費（永豐等電子下單為 1 元）")
            NumberField(usFee, { usFee = it; saveFees() }, "美股每筆手續費（USD）")
            Text("台股交易稅：一般股票 0.3%、ETF 0.1%，自動計算。", style = MaterialTheme.typography.bodySmall)
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
                        Text(
                            "街口、悠遊付不會推播每筆消費。開啟後，只要打開下方勾選的 App 並進入「交易紀錄」頁，帳理就會讀取畫面上的商家、金額、日期並自動匯入（重複的不會再匯入；已由銀行通知記成轉帳的儲值也會自動略過）。只讀取勾選的 App。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "若系統顯示「受限制的設定」而無法開啟：設定 → 應用程式 → 帳理 → 右上角 ⋮ →「允許受限制的設定」，再回來開啟。通知存取權也是同樣步驟。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { com.enwei.moneymanager.notify.WalletScreenReader.openSettings(ctx) }) {
                            Text(if (enabled) "無障礙設定" else "前往開啟")
                        }
                    }
                }
            }
            // 測試：確認讀取是否真的有運作
            item { SectionTitle("測試錢包讀取") }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("測試模式（只讀取，不入帳）")
                            Text("開啟後打開下方勾選的錢包 App 的交易紀錄頁，再回到這裡查看讀到什麼", style = MaterialTheme.typography.bodySmall)
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
                        e.result?.let { r ->
                            Text(
                                if (r.wallet == null) "辨識出 ${r.total} 筆，但找不到要記入的電子支付帳戶（請在下方指定）"
                                else "辨識出 ${r.total} 筆 → 記入「${r.wallet}」：" +
                                    "${if (testMode) "會新增" else "新增"} ${r.created} 筆、已存在 ${r.existing} 筆" +
                                    (if (r.tooOld > 0) "、早於開始日 ${r.since} 略過 ${r.tooOld} 筆" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (r.wallet == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                        }
                        e.records.take(6).forEach { rec ->
                            Text(
                                "${rec.time.toLocalDate()}${if (rec.dateOnly) "" else " %02d:%02d".format(rec.time.hour, rec.time.minute)}　" +
                                    "${rec.merchant ?: rec.typeLabel ?: ""}　${if (rec.isIncome || rec.isTopUp) "+" else "-"}${Money.format(rec.amountMinor, "TWD")}",
                                style = MaterialTheme.typography.bodySmall, maxLines = 1,
                            )
                        }
                        if (e.records.size > 6) Text("…共 ${e.records.size} 筆", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (wallets.isEmpty()) item {
                Text("提醒：請先到「帳戶」新增群組為「電子支付」的帳戶（例如：街口、悠遊付），匯入的交易才有地方記。",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            }
            item { SectionTitle("要讀取的錢包 App（已選 ${settings.screenPackages.size}）") }
            item {
                OutlinedTextField(filter, { filter = it }, label = { Text("搜尋 App") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
            val sorted = apps.filter { filter.isBlank() || it.label.contains(filter, true) || it.pkg.contains(filter, true) }
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
        }
    }
}

/** 現金點鈔：輸入各面額數量，算出總額並可對帳。 */
@Composable
fun CashCounterScreen(nav: NavController, accountId: Long) {
    val scope = rememberCoroutineScope()
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val balances by repo.balances.collectAsStateWithLifecycle(emptyList())
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

    Scaffold(containerColor = MaterialTheme.colorScheme.surface, topBar = { BackTopBar("點鈔：${b?.account?.name.orEmpty()}", { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
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
                    "張數會記住，下次打開可直接增減。花掉或收到現金時記得也在這裡調整，或直接按「以點鈔金額對帳」把差額記成一筆支出（分類：其他）。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
}
