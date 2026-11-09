package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.data.PlaceHints
import com.enwei.moneymanager.data.TxnStatus
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.ui.AppText
import com.enwei.moneymanager.ui.components.BackTopBar
import kotlinx.coroutines.launch

/** 一家店：名稱、筆數、位置與來源。 */
private data class Place(val name: String, val count: Int, val loc: Pair<Double, Double>?, val source: String)

/**
 * 店家位置：清單來自目前的支出與收入（改名、刪掉的舊店名不會出現）。
 * 位置來源依序：手動指定 → 記帳時的定位 → 用店名自動找。線上消費與沒寫分店的連鎖店不列。
 */
@Composable
fun MerchantPlacesScreen(nav: NavController) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val lk = rememberLookups()
    val scope = rememberCoroutineScope()
    val all by repo.transactions.collectAsStateWithLifecycle(emptyList())
    var tick by remember { mutableStateOf(0) }
    var progress by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<Place?>(null) }

    fun chain(m: String) = CHAINS.any { m.contains(it, ignoreCase = true) }
    fun branchy(m: String) = chain(m) && m.trim().contains(' ')
    fun online(m: String, cat: Long?): Boolean {
        val root = lk.category(lk.rootId(cat))?.name.orEmpty()
        return ONLINE.any { m.contains(it, ignoreCase = true) } || ONLINE_ROOTS.any { root.contains(it) }
    }

    // 只列支出的店；往來對象（人名）、退款回饋這類不是店家的不列
    val people = lk.accounts.filter { it.type == com.enwei.moneymanager.data.AccountType.LOAN }.map { it.name.trim() }.toSet()
    val txns = all.filter { t ->
        val m = t.merchant?.trim().orEmpty()
        t.type == TxnType.EXPENSE && t.status == TxnStatus.CONFIRMED && m.isNotEmpty() && m !in people &&
            listOf("退款", "回饋", "分攤", "轉帳", "還款", "代墊").none { m.contains(it) }
    }
    val current = remember(txns) { txns.mapNotNull { it.merchant?.trim() }.toSet() }
    // 舊店名的位置一併清掉，只留目前還有的
    LaunchedEffect(current) { if (current.isNotEmpty()) PlaceHints.pruneMerchantGeo(current) }

    val places = remember(txns, tick) {
        val samples = PlaceHints.samples(ctx).associateBy { it.txnId }
        val pins = PlaceHints.merchantPins()
        val geo = PlaceHints.merchantGeo()
        txns.filter { t -> val m = t.merchant!!.trim(); !online(m, t.categoryId) && (!chain(m) || branchy(m)) }
            .groupBy { it.merchant!!.trim() }
            .map { (m, list) ->
                val fixes = list.mapNotNull { samples[it.id] }
                when {
                    pins[m] != null -> Place(m, list.size, pins[m], "手動")
                    fixes.isNotEmpty() -> Place(m, list.size, fixes.map { it.lat }.average() to fixes.map { it.lon }.average(), "記帳定位")
                    geo[m] != null -> Place(m, list.size, geo[m], "自動")
                    m in geo -> Place(m, list.size, null, "找不到")
                    else -> Place(m, list.size, null, "")
                }
            }
            .sortedWith(compareBy<Place>({ it.loc != null }, { -it.count }))
    }
    val missing = places.filter { it.loc == null }

    // Google 地圖：開啟搜尋 → 分享、複製連結 → 回到這裡自動讀剪貼簿
    var googleFor by remember { mutableStateOf<String?>(null) }
    // 貼連結的視窗：先帶入剪貼簿的內容，讀不到也可以自己貼上
    var linkFor by remember { mutableStateOf<String?>(null) }
    var linkText by remember { mutableStateOf("") }
    var linkBusy by remember { mutableStateOf(false) }
    fun clip(): String {
        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
        return runCatching { cm?.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() }.getOrNull().orEmpty()
    }
    fun applyLink(name: String, text: String) = scope.launch {
        linkBusy = true
        val ll = PlaceHints.coordsFromMapsLink(text, PlaceHints.anchors(ctx).firstOrNull())
        linkBusy = false
        if (ll != null) {
            PlaceHints.setMerchantPin(name, ll.first, ll.second); tick++; linkFor = null
            android.widget.Toast.makeText(ctx, com.enwei.moneymanager.ui.tr("已設定「$name」的位置"), android.widget.Toast.LENGTH_SHORT).show()
        } else {
            linkFor = name; linkText = text
            android.widget.Toast.makeText(ctx, com.enwei.moneymanager.ui.tr("這個連結讀不出位置"), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    fun applyClipboard(name: String) {
        val t = clip()
        if (Regex("https?://").containsMatchIn(t)) applyLink(name, t) else { linkFor = name; linkText = t }
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) googleFor?.let { n ->
                googleFor = null
                scope.launch { kotlinx.coroutines.delay(400); applyClipboard(n) }
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    suspend fun anchors(): List<Pair<Double, Double>> = PlaceHints.anchors(ctx).ifEmpty {
        listOfNotNull(kotlinx.coroutines.withTimeoutOrNull(8000) { PlaceHints.current(ctx) }?.let { it.latitude to it.longitude })
    }
    fun autoFind(names: List<String>) = scope.launch {
        val a = anchors()
        if (a.isEmpty()) {
            android.widget.Toast.makeText(ctx, com.enwei.moneymanager.ui.tr("需要定位權限，才知道要在哪一帶找店"), android.widget.Toast.LENGTH_SHORT).show()
            return@launch
        }
        names.forEach { PlaceHints.clearMerchantGeo(it) }
        val n = PlaceHints.geocodeMerchants(names, a) { i, total -> progress = "找位置中 $i/$total…" }
        progress = null; tick++
        android.widget.Toast.makeText(ctx, com.enwei.moneymanager.ui.tr("找到 $n 家店的位置"), android.widget.Toast.LENGTH_SHORT).show()
    }

    Scaffold(topBar = { BackTopBar("店家位置", { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(progress ?: "${places.size} 家・${missing.size} 家沒有位置", fontSize = AppText.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (missing.isNotEmpty()) OutlinedButton(enabled = progress == null, onClick = { autoFind(missing.map { it.name }.take(40)) }) { Text("自動找") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            items(places, key = { it.name }) { p ->
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable { open = p }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = AppText.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.count} 筆", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(p.source.ifBlank { "沒有位置" }, fontSize = AppText.caption,
                        color = if (p.loc == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    open?.let { p ->
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(p.name) },
            text = {
                Column {
                    Text(p.loc?.let { "${p.source}・%.5f, %.5f".format(it.first, it.second) } ?: p.source.ifBlank { "沒有位置" },
                        fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    val action: @Composable (String, () -> Unit) -> Unit = { label, onClick -> ActionLine(label) { open = null; onClick() } }
                    action("自動找") { autoFind(listOf(p.name)) }
                    action("在 Google 地圖找") {
                        googleFor = p.name
                        runCatching {
                            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode(p.name))))
                        }
                    }
                    action("貼上地圖連結") { applyClipboard(p.name) }
                    action("用目前位置") {
                        scope.launch {
                            val l = kotlinx.coroutines.withTimeoutOrNull(8000) { PlaceHints.current(ctx) }
                            if (l != null) { PlaceHints.setMerchantPin(p.name, l.latitude, l.longitude); tick++ }
                            else android.widget.Toast.makeText(ctx, com.enwei.moneymanager.ui.tr("讀不到目前位置"), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (p.source == "手動") action("取消手動位置") { PlaceHints.clearMerchantPin(p.name); tick++ }
                }
            },
            confirmButton = { TextButton(onClick = { open = null }) { Text("關閉") } },
        )
    }

    linkFor?.let { n ->
        AlertDialog(
            onDismissRequest = { if (!linkBusy) linkFor = null },
            title = { Text(n) },
            text = {
                androidx.compose.material3.OutlinedTextField(linkText, { linkText = it }, placeholder = { Text("Google 地圖連結") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 4)
            },
            confirmButton = {
                TextButton(enabled = !linkBusy && linkText.isNotBlank(), onClick = { applyLink(n, linkText) }) { Text(if (linkBusy) "讀取中…" else "設定") }
            },
            dismissButton = { TextButton(onClick = { linkFor = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ActionLine(label: String, onClick: () -> Unit) {
    Text(label, fontSize = AppText.body, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
