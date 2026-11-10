package com.enwei.moneymanager.ui.screens

import com.enwei.moneymanager.ui.components.coachTarget
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import com.enwei.moneymanager.ui.components.Text
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
 * 位置來源依序：手動指定 → 記帳時的定位 → 用店名自動找。線上消費不列；沒寫分店的連鎖店列在「需指定分店」。
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
    var picking by remember { mutableStateOf<Place?>(null) }
    // Mapbox 金鑰（只存在這支手機）與搜尋
    var tokenOpen by remember { mutableStateOf(false) }
    var mbOn by remember { mutableStateOf(com.enwei.moneymanager.net.Mapbox.enabled) }
    var gOn by remember { mutableStateOf(com.enwei.moneymanager.net.GooglePlaces.enabled) }
    var gOpen by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf<Place?>(null) }

    fun chain(m: String) = CHAINS.any { m.contains(it, ignoreCase = true) }
    fun branchy(m: String) = chain(m) && m.trim().contains(' ')
    fun online(m: String, cat: Long?): Boolean {
        val root = lk.category(lk.rootId(cat))?.name.orEmpty()
        // 旅遊（國外）的消費不用店名在台灣找位置
        return ONLINE.any { m.contains(it, ignoreCase = true) } || ONLINE_ROOTS.any { root.contains(it) } || root.contains("旅遊") ||
            m in PlaceHints.excluded() || m.lowercase() in ONLINE_EXACT
    }
    var renaming by remember { mutableStateOf<Place?>(null) }

    // 只列支出的店；往來對象（人名）、退款回饋這類不是店家的不列
    val people = lk.accounts.filter { it.type == com.enwei.moneymanager.data.AccountType.LOAN }.map { it.name.trim() }.toSet()
    val txns = all.filter { t ->
        val m = t.merchant?.trim().orEmpty()
        t.type == TxnType.EXPENSE && t.status == TxnStatus.CONFIRMED && m.isNotEmpty() && m !in people &&
            NOT_PLACE.none { m.contains(it, ignoreCase = true) }
    }
    val current = remember(txns) { txns.mapNotNull { it.merchant?.trim() }.toSet() }
    // 舊店名的位置一併清掉，只留目前還有的
    LaunchedEffect(current) { if (current.isNotEmpty()) PlaceHints.pruneMerchantGeo(current) }

    val places = remember(txns, tick) {
        val samples = PlaceHints.samples(ctx).associateBy { it.txnId }
        val pins = PlaceHints.merchantPins()
        val via = PlaceHints.pinSources()
        val geo = PlaceHints.merchantGeo()
        val amb = PlaceHints.ambiguous()
        txns.filter { t -> val m = t.merchant!!.trim(); !online(m, t.categoryId) && (!chain(m) || branchy(m)) }
            .groupBy { it.merchant!!.trim() }
            .map { (m, list) ->
                val fixes = list.mapNotNull { samples[it.id] }
                when {
                    pins[m] != null -> Place(m, list.size, pins[m], via[m] ?: "已設定")
                    fixes.isNotEmpty() -> Place(m, list.size, fixes.map { it.lat }.average() to fixes.map { it.lon }.average(), "記帳定位")
                    geo[m] != null -> Place(m, list.size, geo[m], "自動")
                    m in amb -> Place(m, list.size, null, "多個結果")
                    m in geo -> Place(m, list.size, null, "找不到")
                    else -> Place(m, list.size, null, "")
                }
            }
            // 要處理的排前面：多個結果（點一下選）→ 沒有位置 → 找不到 → 已有位置
            .sortedWith(compareBy<Place>({ when { it.source == "多個結果" -> 0; it.loc == null && it.source.isEmpty() -> 1; it.loc == null -> 2; else -> 3 } }, { -it.count }))
    }
    val missing = places.filter { it.loc == null }
    // 只寫連鎖店名、沒寫分店的交易（例如「小北百貨」）：要指定分店才能標位置，不自動猜
    val bare = remember(txns, tick) {
        txns.filter { t -> val m = t.merchant!!.trim(); !online(m, t.categoryId) && chain(m) && !branchy(m) }
            .groupBy { it.merchant!!.trim() }.toList().sortedByDescending { it.second.size }
    }
    // 同一個連鎖店，資料裡已經出現過的分店（例如「小北百貨-新北福和店 SHOWBA」）
    fun knownBranches(name: String): List<String> {
        val key = CHAINS.filter { name.contains(it, ignoreCase = true) }.maxByOrNull { it.length } ?: name
        return current.filter { it != name && it.contains(key, ignoreCase = true) && branchy(it) }.sorted()
    }
    var branchFor by remember { mutableStateOf<String?>(null) }
    // 搜尋選到名稱不同的店：等使用者決定要不要改名
    var pendingPick by remember { mutableStateOf<Triple<Place, String, Pair<Double, Double>>?>(null) }

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
        val ll = PlaceHints.coordsFromMapsLink(text, PlaceHints.anchors(ctx).firstOrNull(), name)
        linkBusy = false
        if (ll != null) {
            PlaceHints.setMerchantPin(name, ll.first, ll.second, "連結"); tick++; linkFor = null
            android.widget.Toast.makeText(ctx, com.enwei.moneymanager.ui.tr("已設定「$name」的位置"), android.widget.Toast.LENGTH_SHORT).show()
        } else {
            linkFor = name; linkText = text
            android.widget.Toast.makeText(ctx, com.enwei.moneymanager.ui.tr("無法從此連結取得位置"), android.widget.Toast.LENGTH_SHORT).show()
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
            android.widget.Toast.makeText(ctx, com.enwei.moneymanager.ui.tr("需要位置權限，才能判斷搜尋範圍"), android.widget.Toast.LENGTH_SHORT).show()
            return@launch
        }
        names.forEach { PlaceHints.clearMerchantGeo(it) }
        val n = PlaceHints.geocodeMerchants(names, a) { i, total -> progress = "找位置中 $i/$total…" }
        progress = null; tick++
        android.widget.Toast.makeText(ctx, com.enwei.moneymanager.ui.tr("找到 $n 家店的位置"), android.widget.Toast.LENGTH_SHORT).show()
    }

    Scaffold(topBar = { BackTopBar("店家位置", { nav.popBackStack() }) }) { pad ->
        com.enwei.moneymanager.ui.components.PageTour("merchants", listOfNotNull(
            com.enwei.moneymanager.ui.components.Coach.Step("mp.google", "店家搜尋", "設定 Google Places 金鑰可提升店家搜尋的準確度，點選即可查看申請步驟。"),
            if (missing.isNotEmpty()) com.enwei.moneymanager.ui.components.Coach.Step("mp.auto", "自動找", "為尚無位置的店家一併搜尋；有多個結果者會標示「選擇」，由您挑選。") else null,
            com.enwei.moneymanager.ui.components.Coach.Step(null, "每一家店", "點選店家可搜尋正確店家、更改名稱，或於地圖上指定位置。"),
        ))
        LazyColumn(Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            item {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable { tokenOpen = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Mapbox", Modifier.weight(1f))
                    Text(if (mbOn) "已設定" else "未設定", fontSize = AppText.caption,
                        color = if (mbOn) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                // Google Places：店家搜尋（台灣小店最完整）
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable { gOpen = true }.coachTarget("mp.google").padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Google Places", Modifier.weight(1f))
                    Text(if (gOn) "本月查 ${com.enwei.moneymanager.net.GooglePlaces.usedThisMonth} 次" else "未設定", fontSize = AppText.caption,
                        color = if (gOn) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(progress ?: "${places.size} 家・${missing.size} 家沒有位置", fontSize = AppText.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (missing.isNotEmpty()) OutlinedButton(enabled = progress == null, onClick = { autoFind(missing.map { it.name }.take(40)) }, modifier = Modifier.coachTarget("mp.auto")) { Text("自動找") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (bare.isNotEmpty()) {
                item(key = "bare-head") {
                    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
                        Text("需指定分店", fontSize = AppText.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("這些交易只記了連鎖店名，指定分店後才能標示位置。", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                items(bare, key = { "bare-${it.first}" }) { (name, list) ->
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable { branchFor = name }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(name, fontSize = AppText.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${list.size} 筆", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("指定分店 ›", fontSize = AppText.caption, color = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                item(key = "places-head") {
                    Text("店家", fontSize = AppText.body, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            items(places, key = { it.name }) { p ->
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable { if (p.source == "多個結果") searching = p else open = p }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = AppText.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.count} 筆", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (p.source == "多個結果") "選擇 ›" else p.source.ifBlank { "沒有位置" }, fontSize = AppText.caption,
                        color = when {
                            p.source == "多個結果" -> MaterialTheme.colorScheme.primary
                            p.loc == null -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            // 不是實體店：收在最下面，可以還原
            val ex = PlaceHints.excluded().filter { it in current }.sorted()
            if (ex.isNotEmpty()) item(key = "ex-head") {
                Text("不是實體店", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            items(ex, key = { "ex-$it" }) { n ->
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(n, fontSize = AppText.body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("還原", fontSize = AppText.caption, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { PlaceHints.setExcluded(n, false); tick++ }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    // 一家店：上面是位置預覽（點地圖就能修改），下面只留常用的幾個動作；連結、目前位置等收在「其他方式」
    open?.let { p ->
        var more by remember(p.name) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(p.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    val action: @Composable (String, () -> Unit) -> Unit = { label, onClick -> ActionLine(label) { open = null; onClick() } }
                    if (p.loc != null) {
                        MapPreview(p.name, p.loc, Modifier.fillMaxWidth().height(150.dp)) { open = null; picking = p }
                        Text("${p.source}・點地圖可以修改", fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                    }
                    // 搜尋店家：有金鑰用 Google／Mapbox，沒有就用 OpenStreetMap
                    action("搜尋店家") { searching = p }
                    if (p.loc == null && !(mbOn || gOn)) action("自動找") { autoFind(listOf(p.name)) }
                    if (p.loc == null) action("在地圖上點選") { picking = p }
                    action("改名") { renaming = p }
                    if (!more) Text("其他方式", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().clickable { more = true }.padding(vertical = 12.dp))
                    else {
                        action("Google 地圖連結") {
                            googleFor = p.name
                            runCatching {
                                ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode(p.name))))
                            }
                        }
                        action("用目前位置") {
                            scope.launch {
                                val l = kotlinx.coroutines.withTimeoutOrNull(8000) { PlaceHints.current(ctx) }
                                if (l != null) { PlaceHints.setMerchantPin(p.name, l.latitude, l.longitude, "定位"); tick++ }
                                else android.widget.Toast.makeText(ctx, com.enwei.moneymanager.ui.tr("讀不到目前位置"), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        action("不是實體店（不放地圖）") { PlaceHints.setExcluded(p.name, true); tick++ }
                        if (p.loc != null) action("清除位置") { PlaceHints.clearMerchantPin(p.name); PlaceHints.clearMerchantGeo(p.name); tick++ }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = null }) { Text("關閉") } },
        )
    }

    branchFor?.let { name ->
        val list = bare.firstOrNull { it.first == name }?.second.orEmpty()
        if (list.isEmpty()) branchFor = null
        else BranchDialog(
            name, list, knownBranches(name),
            near = PlaceHints.samples(ctx).filter { s -> list.any { it.id == s.txnId } }.let { f ->
                if (f.isEmpty()) null else f.map { it.lat }.average() to f.map { it.lon }.average()
            } ?: PlaceHints.anchors(ctx).firstOrNull(),
            onPick = { ids, branch, loc ->
                scope.launch {
                    val n = repo.setMerchant(ids, branch)
                    loc?.let { PlaceHints.setMerchantPin(branch, it.first, it.second, "搜尋") }
                    tick++
                    android.widget.Toast.makeText(ctx, com.enwei.moneymanager.ui.tr("已指定為「$branch」（$n 筆）"), android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onClose = { branchFor = null },
        )
    }

    if (gOpen) GooglePlacesKeyDialog { gOpen = false; gOn = com.enwei.moneymanager.net.GooglePlaces.enabled; tick++ }
    if (tokenOpen) MapboxTokenDialog { tokenOpen = false; mbOn = com.enwei.moneymanager.net.Mapbox.enabled; tick++ }
    searching?.let { p ->
        MapboxSearchDialog(p.name, p.loc ?: PlaceHints.anchors(ctx).firstOrNull() ?: (25.0136 to 121.5414)) { hit ->
            searching = null
            if (hit != null) {
                val to = cleanPlaceName(hit.name)
                // 名稱不同時先問：只設定位置，還是連店名一起改（不會默默把所有同名交易改掉）
                if (to != p.name) pendingPick = Triple(p, to, hit.lat to hit.lon)
                else { PlaceHints.setMerchantPin(p.name, hit.lat, hit.lon, "搜尋"); tick++ }
            }
        }
    }
    pendingPick?.let { (p, to, ll) ->
        AlertDialog(
            onDismissRequest = { pendingPick = null },
            title = { Text("套用這家店") },
            text = { Text("「${p.name}」共 ${p.count} 筆交易。\n選到的店：$to", fontSize = AppText.body) },
            confirmButton = {
                TextButton(onClick = {
                    pendingPick = null
                    scope.launch {
                        val n = repo.renameMerchant(p.name, to)
                        PlaceHints.clearMerchantPin(p.name); PlaceHints.clearMerchantGeo(p.name)
                        PlaceHints.setMerchantPin(to, ll.first, ll.second, "搜尋"); tick++
                        android.widget.Toast.makeText(ctx, com.enwei.moneymanager.ui.tr("已改名為「$to」（$n 筆）"), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) { Text("改名並設定位置") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingPick = null
                    PlaceHints.setMerchantPin(p.name, ll.first, ll.second, "搜尋"); tick++
                }) { Text("只設定位置") }
            },
        )
    }

    renaming?.let { p ->
        var text by remember(p.name) { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("改名") },
            text = { androidx.compose.material3.OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(enabled = text.isNotBlank() && text.trim() != p.name, onClick = {
                    val to = text.trim(); renaming = null
                    scope.launch {
                        val n = repo.renameMerchant(p.name, to)
                        // 位置跟著新名字走
                        PlaceHints.merchantPins()[p.name]?.let { ll -> PlaceHints.setMerchantPin(to, ll.first, ll.second, PlaceHints.pinSources()[p.name] ?: "地圖"); PlaceHints.clearMerchantPin(p.name) }
                        PlaceHints.clearMerchantGeo(p.name)
                        tick++
                        android.widget.Toast.makeText(ctx, com.enwei.moneymanager.ui.tr("已改名（$n 筆）"), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) { Text("改名") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("取消") } },
        )
    }

    picking?.let { p ->
        val start = p.loc ?: PlaceHints.anchors(ctx).firstOrNull() ?: (25.0136 to 121.5414)
        MapPicker(p.name, start) { ll ->
            if (ll != null) { PlaceHints.setMerchantPin(p.name, ll.first, ll.second, "地圖"); tick++ }
            picking = null
        }
    }

    linkFor?.let { n ->
        AlertDialog(
            onDismissRequest = { if (!linkBusy) linkFor = null },
            title = { Text(n) },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(linkText, { linkText = it }, placeholder = { Text("Google 地圖連結") },
                        modifier = Modifier.fillMaxWidth(), maxLines = 4)
                    // 讀不到時顯示讀到了什麼，方便判斷
                    if (!linkBusy && PlaceHints.lastLinkDebug.isNotBlank()) Text(PlaceHints.lastLinkDebug, fontSize = AppText.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp), maxLines = 6)
                }
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

/** 設定 Mapbox 公開金鑰（pk. 開頭），存檔前先測試一次。 */
@Composable
private fun MapboxTokenDialog(onClose: () -> Unit) = KeyDialog("Mapbox 金鑰", "pk.…", { com.enwei.moneymanager.net.Mapbox.token },
    { com.enwei.moneymanager.net.Mapbox.token = it }, { com.enwei.moneymanager.net.Mapbox.test() },
    steps = listOf(
        "登入 Mapbox（可免費註冊）" to "https://account.mapbox.com/",
        "於 Access tokens 複製 Default public token（pk. 開頭）" to "https://account.mapbox.com/access-tokens/",
        "將金鑰貼於下方" to null,
    ), onClose = onClose)

/** 金鑰設定視窗：存檔前先測試，失敗就還原並顯示原因；留空＝刪除金鑰。 */
@Composable
private fun KeyDialog(title: String, hint: String, get: () -> String, set: (String) -> Unit, test: suspend () -> String?,
    /** 怎麼申請：步驟與可以直接打開的網址 */
    steps: List<Pair<String, String?>> = emptyList(), onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(get()) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Column {
                // 申請步驟：每一步一行，有網址的可以直接打開
                if (steps.isNotEmpty() && get().isBlank()) Column(Modifier.padding(bottom = 10.dp)) {
                    steps.forEachIndexed { i, (label, url) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("${i + 1}. $label", fontSize = AppText.secondary, modifier = Modifier.weight(1f))
                            if (url != null) TextButton(onClick = {
                                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                            }) { Text("打開") }
                        }
                    }
                }
                androidx.compose.material3.OutlinedTextField(text, { text = it.trim() }, placeholder = { Text(hint) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (msg.isNotBlank()) Text(msg, fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                val old = get()
                set(text)
                if (text.isBlank()) { onClose(); return@TextButton }
                busy = true; msg = "測試中…"
                scope.launch {
                    val err = test()
                    busy = false
                    if (err == null) onClose() else { msg = err; set(old) }
                }
            }) { Text(if (busy) "測試中…" else "儲存") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("取消") } },
    )
}

/** 用 Mapbox 搜尋店家：預設用店名，可以改關鍵字；列出名稱、地址與距離，點一下設定位置。 */
@Composable
private fun MapboxSearchDialog(name: String, near: Pair<Double, Double>, onDone: (com.enwei.moneymanager.net.Mapbox.Place?) -> Unit) {
    var q by remember { mutableStateOf(name) }
    var results by remember { mutableStateOf<List<com.enwei.moneymanager.net.Mapbox.Place>?>(null) }
    LaunchedEffect(q) {
        if (q.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(600)
        // Mapbox 找不到（台灣小店它常常沒有）就用 OpenStreetMap 在附近 3 公里找
        results = com.enwei.moneymanager.net.PlaceSearch.search(q, near, 10)
            .ifEmpty { PlaceHints.overpassSearch(q, near.first, near.second, 3000) }
    }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(name) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(q, { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val r = results
                when {
                    r == null -> Text("搜尋中…", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                    r.isEmpty() -> Text(com.enwei.moneymanager.net.PlaceSearch.lastError.ifBlank { "沒有結果，請嘗試其他關鍵字" }, fontSize = AppText.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                    else -> LazyColumn(Modifier.padding(top = 6.dp).height(320.dp)) {
                        items(r) { p ->
                            Column(Modifier.fillMaxWidth().clickable { onDone(p) }.padding(vertical = 8.dp)) {
                                Text(p.name, fontSize = AppText.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(p.meters?.let { if (it < 1000) "${it.toInt()} 公尺" else "%.1f 公里".format(it / 1000) }, p.address.ifBlank { null }).joinToString("・"),
                                    fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(null) }) { Text("取消") } },
    )
}


/** Google Places 金鑰：申請步驟（每一步可直接打開）＋貼上＋測試。店家位置與功能設定共用。 */
@Composable
internal fun GooglePlacesKeyDialog(onClose: () -> Unit) = KeyDialog("Google Places 金鑰", "AIza…",
    { com.enwei.moneymanager.net.GooglePlaces.key }, { com.enwei.moneymanager.net.GooglePlaces.key = it },
    { com.enwei.moneymanager.net.GooglePlaces.test() },
    steps = listOf(
        "登入 Google Cloud 並啟用帳單（每月有免費額度，個人使用通常不會產生費用）" to "https://console.cloud.google.com/billing",
        "於 API 程式庫啟用「Places API (New)」" to "https://console.cloud.google.com/apis/library/places.googleapis.com",
        "於「憑證」建立 API 金鑰，並建議限制僅能使用 Places API" to "https://console.cloud.google.com/apis/credentials",
        "（建議）將每日用量上限設為 200" to "https://console.cloud.google.com/apis/api/places.googleapis.com/quotas",
        "將 AIza 開頭的金鑰貼於下方" to null,
    ), onClose = onClose)

/**
 * 指定分店：勾選要指定的交易（預設全部），再從已出現過的分店或附近搜尋結果選一家。
 * 不會自動決定，每一筆都由使用者確認。
 */
@Composable
private fun BranchDialog(
    name: String, list: List<com.enwei.moneymanager.data.TxnEntity>, known: List<String>, near: Pair<Double, Double>?,
    onPick: (List<Long>, String, Pair<Double, Double>?) -> Unit, onClose: () -> Unit,
) {
    var picked by remember(name, list.size) { mutableStateOf(list.map { it.id }.toSet()) }
    var q by remember(name) { mutableStateOf(name) }
    var results by remember { mutableStateOf<List<com.enwei.moneymanager.net.Mapbox.Place>?>(null) }
    // 有金鑰用 Google／Mapbox；沒有就用 OpenStreetMap（免費，較慢，所以等久一點再查）
    val keyed = com.enwei.moneymanager.net.PlaceSearch.online
    LaunchedEffect(q) {
        if (q.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        results = null
        kotlinx.coroutines.delay(if (keyed) 500 else 1200)
        results = com.enwei.moneymanager.net.PlaceSearch.search(q.trim(), near, 8)
    }
    val fmt = remember { java.text.SimpleDateFormat("yyyy/M/d HH:mm", java.util.Locale.TAIWAN) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("指定「$name」的分店") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                item {
                    Text("要指定的交易（${picked.size} / ${list.size}）", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                items(list.sortedByDescending { it.date }, key = { "t${it.id}" }) { t ->
                    val on = t.id in picked
                    Row(
                        Modifier.fillMaxWidth().clickable { picked = if (on) picked - t.id else picked + t.id }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(on, { picked = if (on) picked - t.id else picked + t.id })
                        Column(Modifier.weight(1f)) {
                            Text(fmt.format(java.util.Date(t.date)) + "　" + com.enwei.moneymanager.core.Money.withSymbol(t.amount, t.currency),
                                fontSize = AppText.secondary)
                            if (!t.note.isNullOrBlank()) Text(t.note!!, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (known.isNotEmpty()) {
                    item {
                        Text("曾記錄的分店", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    items(known, key = { "k$it" }) { b ->
                        Text(b, fontSize = AppText.body, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable(enabled = picked.isNotEmpty()) {
                                onPick(picked.toList(), b, PlaceHints.merchantPins()[b])
                                picked = emptySet()
                            }.padding(vertical = 10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                item {
                    Text("搜尋分店", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                    androidx.compose.material3.OutlinedTextField(q, { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (results == null) Text("搜尋中…", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    if (results?.isEmpty() == true && q.trim().length >= 2) Text(if (keyed) "找不到結果，可改用其他關鍵字。" else "OpenStreetMap 找不到；可改用其他關鍵字，或設定 Google Places 金鑰。", fontSize = AppText.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
                items(results.orEmpty(), key = { "r${it.name}${it.lat}" }) { r ->
                    Column(Modifier.fillMaxWidth().clickable(enabled = picked.isNotEmpty()) {
                        onPick(picked.toList(), cleanPlaceName(r.name), r.lat to r.lon)
                        picked = emptySet()
                    }.padding(vertical = 8.dp)) {
                        Text(r.name, fontSize = AppText.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(r.meters?.let { if (it < 1000) "${it.toInt()} 公尺" else "%.1f 公里".format(it / 1000) }, r.address.ifBlank { null }).joinToString("・"),
                            fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("完成") } },
    )
}

/** 地圖服務回傳的店名常帶廣告詞（「｜直營」「-永和必吃…」）：只留店名本身 */
internal fun cleanPlaceName(raw: String): String {
    var m = raw.split('|', '｜').first().trim()
    m = m.replace(Regex("（[^）]*、[^）]*）"), "")
    m = m.replace(Regex("([）)])\\s*-\\s*\\S.*$"), "$1")
    return m.trim(' ', '-').ifBlank { raw.trim() }
}
