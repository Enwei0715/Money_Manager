package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.data.PlaceHints
import com.enwei.moneymanager.data.TxnStatus
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.ui.AppText
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.Text
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** 線上消費（沒有實體地點）：不標在地圖上、也不用店名找位置。 */
internal val ONLINE = listOf("蝦皮", "Claude", "Firstory", "CapCut", "ElevenLabs", "Endel", "Uber One", "Netflix", "Spotify", "YouTube", "Apple", "Google",
    "LINE 貼圖", "LINE 主題", "LINE 官方", "MyCard", "Steam", "Chess.com", "傳說對決", "Brawl Stars", "Clash of Clans", "Pixel Survival", "Sky 光遇",
    "售票網站", "銀行手續費", "對帳調整", "房東", "電費", "保險", "Uber Eats", "foodpanda", "momo", "PChome", "Amazon")
internal val ONLINE_ROOTS = listOf("訂閱", "保險", "金融費用", "居住")

/** 連鎖店：每家分店位置不同，只用付款當下記下的位置（不套用其他分店、不用店名找）。 */
/** 不是一個地點的「店名」：交通、調整、手續費、房租等，不放上地圖 */
internal val NOT_PLACE = listOf("調整", "未記錄", "手續費", "對帳", "房東", "公車", "捷運", "鐵路", "高鐵", "YouBike", "還款", "退款", "回饋", "分攤", "代墊", "轉帳")

internal val CHAINS = listOf("7-11", "7-ELEVEN", "全家", "萊爾富", "OK超商", "全聯", "小北百貨", "麥當勞", "摩斯漢堡", "肯德基", "85度C", "星巴克", "路易莎", "大創", "愛買",
    "家樂福", "寶雅", "屈臣氏", "康是美", "吉野家", "八方", "可不可", "50嵐", "迷客夏", "中油", "台北捷運", "公車", "YouBike", "台鐵", "台灣高鐵", "Subway", "漢堡王", "Burger King")

/** 地圖上的一個點：同一家店（或同一個位置）在這段期間的支出合計。 */
private data class SpendSpot(val lat: Double, val lon: Double, val name: String, val total: Long, val count: Int, val cat: String, val key: String = "", val fixed: Boolean = false)

/**
 * 支出地圖：每日／每週／每月的支出畫在地圖上（圓越大花越多），下方列出各地點。
 * 位置來自記帳當下記下的位置；同一家店在別處記過位置的也套用；還沒有位置的店可用店名在 OpenStreetMap 找。
 */
@Composable
fun SpendMapScreen(nav: NavController) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val lk = rememberLookups()
    val all by repo.transactions.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var span by com.enwei.moneymanager.ui.components.rememberTab("spendmap.span", 2)
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    var geo by remember { mutableStateOf(PlaceHints.merchantGeo()) }
    var progress by remember { mutableStateOf<String?>(null) }
    val zone = ZoneId.systemDefault()
    val (from, to) = when (span) {
        0 -> anchor to anchor
        1 -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusDays(6) }
        else -> anchor.withDayOfMonth(1) to anchor.with(TemporalAdjusters.lastDayOfMonth())
    }
    val label = when (span) {
        0 -> "${from.monthValue}/${from.dayOfMonth}"
        1 -> "${from.monthValue}/${from.dayOfMonth}–${to.monthValue}/${to.dayOfMonth}"
        else -> "${from.year} 年 ${from.monthValue} 月"
    }
    val samples = remember(all) { PlaceHints.samples(ctx).associateBy { it.txnId } }
    fun online(t: com.enwei.moneymanager.data.TxnEntity): Boolean {
        val m = t.merchant?.trim().orEmpty()
        val root = lk.category(lk.rootId(t.categoryId))?.name.orEmpty()
        return ONLINE.any { m.contains(it, ignoreCase = true) } || ONLINE_ROOTS.any { root.contains(it) } || NOT_PLACE.any { m.contains(it, ignoreCase = true) }
    }
    fun chain(m: String) = CHAINS.any { m.contains(it, ignoreCase = true) }
    // 連鎖店名稱帶分店（例如「萊爾富 永和貞寧店」）：可以用名稱找到那一間
    fun branchy(m: String) = chain(m) && m.trim().contains(' ')
    // 同一家店：記過位置的平均位置
    val merchantLoc = remember(all, samples) {
        all.mapNotNull { t -> val s = samples[t.id] ?: return@mapNotNull null; t.merchant?.trim()?.takeIf { it.isNotEmpty() }?.let { it to (s.lat to s.lon) } }
            .groupBy({ it.first }, { it.second })
            // 同一家店（不是沒寫分店的連鎖店）：所有記過的位置平均成一個點
            .filter { (m, _) -> !chain(m) || branchy(m) }
            .mapValues { (_, l) -> l.map { it.first }.average() to l.map { it.second }.average() }
    }
    val inRange = all.filter { t ->
        t.type == TxnType.EXPENSE && t.status == TxnStatus.CONFIRMED &&
            java.time.Instant.ofEpochMilli(t.date).atZone(zone).toLocalDate().let { !it.isBefore(from) && !it.isAfter(to) }
    }
    var pins by remember { mutableStateOf(PlaceHints.merchantPins()) }
    /**
     * 一筆支出的位置：手動指定 → 用店名找到的 → 這家店記過的位置平均（同一家店一定是同一個點）；
     * 沒寫分店的連鎖店才用這一筆自己記下的位置。
     */
    fun locOf(t: com.enwei.moneymanager.data.TxnEntity): Pair<Double, Double>? {
        val m = t.merchant?.trim().orEmpty()
        if (m.isNotEmpty()) {
            pins[m]?.let { return it }
            if (!chain(m) || branchy(m)) { geo[m]?.let { return it }; merchantLoc[m]?.let { return it } }
        }
        return samples[t.id]?.let { it.lat to it.lon }
    }
    val offline = inRange.filter { !online(it) }
    val onlineCount = inRange.size - offline.size
    val placed = offline.mapNotNull { t -> locOf(t)?.let { t to it } }
    val unplaced = offline.filter { locOf(it) == null }
    // 同一家店合成一個點；沒寫分店的連鎖店依位置（約 300 公尺內算同一間）分開
    val spots = placed.groupBy { (t, l) ->
        val m = t.merchant?.trim().orEmpty()
        if (m.isNotEmpty() && (!chain(m) || branchy(m) || m in pins)) m to "" else m to "%.3f,%.3f".format(Math.round(l.first / 0.003) * 0.003, Math.round(l.second / 0.003) * 0.003)
    }.map { (k, l) ->
        val total = l.sumOf { lk.fx.convert(it.first.amount, it.first.currency) }
        val cat = l.groupingBy { lk.category(lk.rootId(it.first.categoryId))?.name ?: "" }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
        SpendSpot(l.map { it.second.first }.average(), l.map { it.second.second }.average(),
            k.first.ifBlank { "（未命名）" }, total, l.size, cat, "${k.first}|${k.second}", k.first in pins)
    }.sortedByDescending { it.total }
    var selKey by remember { mutableStateOf<String?>(null) }
    var focusTick by remember { mutableStateOf(0) }
    val snapping: String? = null
    // Google 地圖：開啟搜尋 → 使用者按分享、複製連結 → 回到這裡自動讀剪貼簿裡的連結
    var googleFor by remember { mutableStateOf<String?>(null) }
    var googleHit by remember { mutableStateOf<Pair<String, Pair<Double, Double>>?>(null) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) googleFor?.let { key ->
                scope.launch {
                    kotlinx.coroutines.delay(400)
                    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                    val text = runCatching { cm?.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() }.getOrNull().orEmpty()
                    PlaceHints.coordsFromMapsLink(text, PlaceHints.anchors(ctx).firstOrNull(), key.substringBefore('|'))?.let { googleHit = key to it }
                }
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.4f

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    Scaffold(topBar = { BackTopBar("支出地圖", { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background), state = listState) {
            item {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.enwei.moneymanager.ui.components.SubTabs(listOf("每日", "每週", "每月"), span) { span = it }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { anchor = when (span) { 0 -> anchor.minusDays(1); 1 -> anchor.minusWeeks(1); else -> anchor.minusMonths(1) } }) { Icon(Icons.Filled.ChevronLeft, "上一期") }
                        Text(label, fontSize = AppText.section, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        IconButton(onClick = { anchor = when (span) { 0 -> anchor.plusDays(1); 1 -> anchor.plusWeeks(1); else -> anchor.plusMonths(1) } }) { Icon(Icons.Filled.ChevronRight, "下一期") }
                    }
                    val placedSum = placed.sumOf { lk.fx.convert(it.first.amount, it.first.currency) }
                    // 摘要只留一句；點一下才看沒有位置、線上的筆數
                    var more by remember { mutableStateOf(false) }
                    val extra = (unplaced.isNotEmpty() || onlineCount > 0)
                    Row(Modifier.clickable(enabled = extra) { more = !more }, verticalAlignment = Alignment.CenterVertically) {
                        Text("地圖上 ${placed.size} 筆 ${Money.withSymbol(placedSum, lk.base)}", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (extra) Text(if (more) "  ︿" else "  ﹀", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (more) Text(listOfNotNull(unplaced.size.takeIf { it > 0 }?.let { "沒有位置 $it 筆" }, onlineCount.takeIf { it > 0 }?.let { "線上 $it 筆" }).joinToString("・"),
                        fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                if (spots.isEmpty()) Text("這段期間沒有可以標在地圖上的支出", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp))
                else SpendMap(spots, dark, Modifier.fillMaxWidth().height(380.dp).clipToBounds(), selKey, focusTick, status = snapping,
                    onSelect = { selKey = it?.key },
                    // 修正位置：記成這家店的位置（這家店所有支出都用這個點）
                    onMove = { sp, la, lo -> PlaceHints.setMerchantPin(sp.name, la, lo); pins = PlaceHints.merchantPins() },
                    onReset = { sp -> PlaceHints.clearMerchantPin(sp.name); pins = PlaceHints.merchantPins() },
                    onGoogle = { sp ->
                        googleFor = sp.key
                        val q = android.net.Uri.encode(sp.name)
                        val geo = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:${sp.lat},${sp.lon}?q=$q"))
                        val web = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=$q"))
                        runCatching { ctx.startActivity(geo.setPackage("com.google.android.apps.maps")) }.recoverCatching { ctx.startActivity(web) }
                        android.widget.Toast.makeText(ctx, com.enwei.moneymanager.ui.tr("在 Google 地圖找到店家後按「分享」→「複製連結」，再回到帳理"), android.widget.Toast.LENGTH_LONG).show()
                    })
                googleHit?.let { (key, ll) ->
                    val name = spots.firstOrNull { it.key == key }?.name.orEmpty()
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { googleHit = null; googleFor = null },
                        title = { Text("套用 Google 地圖的位置？") },
                        text = { Text("「$name」改用剛複製的 Google 地圖位置") },
                        confirmButton = { androidx.compose.material3.TextButton(onClick = {
                            PlaceHints.setMerchantPin(name, ll.first, ll.second); pins = PlaceHints.merchantPins(); selKey = "$name|"; focusTick++
                            googleHit = null; googleFor = null
                        }) { Text("套用") } },
                        dismissButton = { androidx.compose.material3.TextButton(onClick = { googleHit = null; googleFor = null }) { Text("取消") } },
                    )
                }
            }
            // 沒有位置的店：用店名在 OpenStreetMap 找（每秒一家）
            val missing = unplaced.mapNotNull { it.merchant?.trim()?.takeIf { m -> m.isNotEmpty() && m !in geo && (!chain(m) || branchy(m)) } }.distinct()
            if (missing.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(progress ?: "${missing.size} 家店還沒有位置", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    OutlinedButton(enabled = progress == null, onClick = {
                        scope.launch {
                            // 只在常活動的區域附近找（依記過的付款位置）；還沒有記錄時用目前位置
                            val anchors = PlaceHints.anchors(ctx).ifEmpty {
                                listOfNotNull(kotlinx.coroutines.withTimeoutOrNull(8000) { PlaceHints.current(ctx) }?.let { it.latitude to it.longitude })
                            }
                            if (anchors.isEmpty()) {
                                android.widget.Toast.makeText(ctx, com.enwei.moneymanager.ui.tr("需要定位權限，才知道要在哪一帶找店"), android.widget.Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            val n = PlaceHints.geocodeMerchants(missing.take(40), anchors) { i, total -> progress = "找位置中 $i/$total…" }
                            geo = PlaceHints.merchantGeo(); progress = null
                            android.widget.Toast.makeText(ctx, com.enwei.moneymanager.ui.tr("找到 $n 家店的位置"), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("用店名找位置") }
                }
            }
            if (spots.isNotEmpty()) item {
                Text("地點", fontSize = AppText.section, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
            }
            items(spots, key = { "${it.name}${it.lat}${it.lon}" }) { s ->
                // 點一下：地圖移到這個地點並標出來
                val on = s.key == selKey
                Row(Modifier.fillMaxWidth().background(if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                    .clickable { selKey = s.key; focusTick++; scope.launch { listState.animateScrollToItem(0) } }
                    .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, fontSize = AppText.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${s.cat.ifBlank { "—" }}・${s.count} 筆", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Money.withSymbol(s.total, lk.base), fontSize = AppText.body, color = com.enwei.moneymanager.ui.ExpenseColor)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

/** 地圖針頭的半徑（dp）：金額越大越大，但差距不誇張 */
private fun pinR(sp: SpendSpot, maxTotal: Long) = 7.0 + 5.0 * kotlin.math.sqrt(sp.total.toDouble() / maxTotal)

/** 地圖圖磚的記憶體快取（最近用過的 240 張）。 */
private object TileCache {
    val mem = object : android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(240) {}
    val inflight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    /** 下載失敗的時間：幾秒後自動重試，不會永遠空白 */
    val failedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    /** 最近一次畫面需要的圖磚：排隊輪到時已經不在畫面上（縮放、拖走了）就不下載，避免塞車 */
    @Volatile var visible: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    val gate = kotlinx.coroutines.sync.Semaphore(6)
    /** 預先載入另外排隊，不跟畫面上的圖磚搶 */
    val preGate = kotlinx.coroutines.sync.Semaphore(2)
    /** 圖磚存在手機的快取（60MB）：看過的地方下次直接從手機讀，不用再下載 */
    val client by lazy {
        com.enwei.moneymanager.net.Http.client.newBuilder()
            .cache(okhttp3.Cache(java.io.File(com.enwei.moneymanager.MoneyApp.instance.cacheDir, "map_tiles"), 60L * 1024 * 1024))
            .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(14, java.util.concurrent.TimeUnit.SECONDS)
            .addNetworkInterceptor { ch -> ch.proceed(ch.request()).newBuilder().header("Cache-Control", "public, max-age=1209600").build() }
            .build()
    }
}

private fun mx(lon: Double) = (lon + 180.0) / 360.0
private fun my(lat: Double): Double {
    val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
    return (1 - kotlin.math.ln(kotlin.math.tan(r) + 1 / kotlin.math.cos(r)) / Math.PI) / 2
}

/**
 * 支出地圖（App 自己畫，不靠 WebView）：OpenStreetMap 圖磚＋每個地點一個圓（越大花越多、顏色依分類）。
 * 手指拖曳移動、兩指縮放、點兩下放大；點圓顯示店名與金額。
 */
@Composable
private fun SpendMap(
    spots: List<SpendSpot>, dark: Boolean, modifier: Modifier, selKey: String?, focusTick: Int, status: String? = null,
    onSelect: (SpendSpot?) -> Unit, onMove: (SpendSpot, Double, Double) -> Unit, onReset: (SpendSpot) -> Unit,
    onGoogle: (SpendSpot) -> Unit = {},
    /** 選位置模式：點地圖就是新位置，畫面不重新對焦、不顯示資訊卡 */
    pickOnly: Boolean = false,
    /** 顯示右上角的縮放按鈕（小預覽圖不顯示） */
    controls: Boolean = true,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val tilePx = with(density) { 256.dp.toPx() }
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf(0) }
    var sizePx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val viewKey: Any = if (pickOnly) Unit else spots
    var zoom by remember(viewKey) { mutableStateOf(-1.0) }
    var cx by remember(viewKey) { mutableStateOf(0.5) }
    var cy by remember(viewKey) { mutableStateOf(0.5) }
    val picked = spots.firstOrNull { it.key == selKey }
    var moving by remember { mutableStateOf(pickOnly) }
    // 從下方清單點地點：地圖移過去並放大
    androidx.compose.runtime.LaunchedEffect(focusTick) {
        if (focusTick > 0) picked?.let { cx = mx(it.lon); cy = my(it.lat); if (zoom < 16.0) zoom = 16.0 }
    }
    val palette = listOf(0xFFE4574F, 0xFF3D7FD9, 0xFFE89A2C, 0xFF2A9D8F, 0xFF9B5DC4, 0xFF6BA544, 0xFFD9682B, 0xFF5B6ABF)
    val cats = remember(spots) { spots.map { it.cat }.distinct() }
    val maxTotal = spots.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
    // 第一次量到大小時：把所有點框進畫面
    if (zoom < 0 && sizePx.width > 0 && spots.isNotEmpty()) {
        val xs = spots.map { mx(it.lon) }; val ys = spots.map { my(it.lat) }
        cx = (xs.min() + xs.max()) / 2; cy = (ys.min() + ys.max()) / 2
        val pad = with(density) { 48.dp.toPx() }
        val dx = (xs.max() - xs.min()).coerceAtLeast(1e-9); val dy = (ys.max() - ys.min()).coerceAtLeast(1e-9)
        val z = minOf(kotlin.math.log2((sizePx.width - pad * 2) / (dx * tilePx)), kotlin.math.log2((sizePx.height - pad * 2) / (dy * tilePx)))
        zoom = if (spots.size == 1) 16.0 else z.coerceIn(3.0, 16.0)
    }
    // 底圖：有 Mapbox 金鑰用 Mapbox Streets，否則 OpenStreetMap（深色模式在畫的時候轉暗）
    val style = if (com.enwei.moneymanager.net.Mapbox.enabled) "mb" else "osm"
    fun load(z: Int, x: Int, y: Int, pre: Boolean = false) {
        if (z < 0 || y < 0 || y >= (1 shl z)) return
        val key = "$style/$z/$x/$y"
        if (!pre) TileCache.visible.add(key)
        if (TileCache.mem.get(key) != null) return
        if ((TileCache.failedAt[key] ?: 0L) > System.currentTimeMillis() - 4000) return
        if (!TileCache.inflight.add(key)) return
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var failed = false
            var fetched = false
            try {
                val gate = if (pre) TileCache.preGate else TileCache.gate
                gate.acquire()
                try {
                    // 輪到時已經不在畫面上：跳過（下次需要再排）
                    if (!pre && key !in TileCache.visible) return@launch
                    fetched = true
                    // 依序試：通用電子地圖 → OpenStreetMap（這一格載不到就換下一個，不會一片空白）
                    val urls = buildList {
                        if (key.startsWith("mb/")) add(com.enwei.moneymanager.net.Mapbox.tileUrl(z, x, y))
                        add("https://tile.openstreetmap.org/$z/$x/$y.png")
                    }
                    var bmp: android.graphics.Bitmap? = null
                    for (url in urls) {
                        val req = okhttp3.Request.Builder().url(url).header("User-Agent", "Ledgerly/2.1 (Android personal finance app)").build()
                        bmp = runCatching {
                            TileCache.client.newCall(req).execute().use { r ->
                                val bytes = r.body?.bytes()
                                if (r.isSuccessful && bytes != null) android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) else null
                            }
                        }.getOrNull()
                        if (bmp != null) break
                    }
                    if (bmp != null) { TileCache.mem.put(key, bmp.asImageBitmap()); TileCache.failedAt.remove(key) }
                    else { TileCache.failedAt[key] = System.currentTimeMillis(); failed = true }
                } finally { gate.release() }
            } catch (_: Exception) {
                failed = true
            } finally {
                TileCache.inflight.remove(key)
                if (fetched || failed) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.NonCancellable) { version++ }
            }
            // 失敗的圖磚過一下自動重畫，重新下載
            if (failed) { kotlinx.coroutines.delay(4500); version++ }
        }
    }
    // 預先載入：一打開就把整個範圍放大、縮小一級的圖磚先抓好，縮放時不用等
    androidx.compose.runtime.LaunchedEffect(style, zoom >= 0, sizePx) {
        if (zoom < 0 || sizePx.width == 0) return@LaunchedEffect
        val base = (kotlin.math.round(zoom).toInt() + 1).coerceIn(2, 19)
        for (z in listOf(base - 1, base + 1, base - 2, base + 2)) {
            if (z !in 2..19) continue
            val n = 1 shl z
            val world = tilePx * Math.pow(2.0, z - 1.0)
            val t = world / n
            val left = cx * world - sizePx.width / 2; val top = cy * world - sizePx.height / 2
            val xs = kotlin.math.floor(left / t).toInt()..kotlin.math.floor((left + sizePx.width) / t).toInt()
            val ys = kotlin.math.floor(top / t).toInt().coerceAtLeast(0)..kotlin.math.floor((top + sizePx.height) / t).toInt().coerceAtMost(n - 1)
            if (xs.count() * ys.count() > 40) continue
            for (ty in ys) for (tx in xs) load(z, ((tx % n) + n) % n, ty, pre = true)
        }
    }
    val bg = if (dark) androidx.compose.ui.graphics.Color(0xFF1E1E21) else androidx.compose.ui.graphics.Color(0xFFF2F2F2)
    // 深色模式：反轉亮度並降低飽和，變成暗色底圖
    val tileFilter = if (!dark) null else androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
        -0.55f, -0.25f, -0.08f, 0f, 235f,
        -0.20f, -0.60f, -0.08f, 0f, 235f,
        -0.20f, -0.25f, -0.43f, 0f, 235f,
        0f, 0f, 0f, 1f, 0f,
    )))
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    Box(modifier.background(bg).onSizeChanged { sizePx = it }) {
        androidx.compose.foundation.Canvas(
            Modifier.fillMaxSize()
                .pointerInput(spots) {
                    detectTransformGestures { _, pan, scale, _ ->
                        if (zoom >= 0) {
                            val world = tilePx * Math.pow(2.0, zoom)
                            cx -= pan.x / world; cy = (cy - pan.y / world).coerceIn(0.0, 1.0)
                            zoom = (zoom + kotlin.math.log2(scale.toDouble())).coerceIn(2.0, 18.0)
                        }
                    }
                }
                .pointerInput(spots, moving, selKey) {
                    detectTapGestures(
                        onDoubleTap = { if (zoom >= 0) zoom = (zoom + 1).coerceAtMost(18.0) },
                        onTap = { o ->
                            if (zoom >= 0) {
                                val world = tilePx * Math.pow(2.0, zoom)
                                val wx = cx + (o.x - sizePx.width / 2) / world; val wy = cy + (o.y - sizePx.height / 2) / world
                                val sp = picked
                                if (moving && sp != null) {
                                    // 修正位置：點的地方就是正確位置
                                    onMove(sp, Math.toDegrees(kotlin.math.atan(kotlin.math.sinh(Math.PI * (1 - 2 * wy)))), wx * 360 - 180)
                                    moving = pickOnly
                                } else {
                                    // 點針頭（在位置上方）也算點到
                                    fun dist(p: SpendSpot) = kotlin.math.hypot((mx(p.lon) - cx) * world + sizePx.width / 2 - o.x,
                                        (my(p.lat) - cy) * world + sizePx.height / 2 - o.y)
                                    onSelect(spots.minByOrNull { dist(it) }?.takeIf { dist(it) < 30.dp.toPx() })
                                }
                            }
                        },
                    )
                },
        ) {
            @Suppress("UNUSED_VARIABLE") val v = version
            if (zoom < 0) return@Canvas
            // 用高一級的圖磚、畫成一半大小：高解析度螢幕上比較清楚
            val z = (kotlin.math.round(zoom).toInt() + 1).coerceIn(2, 19)
            val n = 1 shl z
            val world = tilePx * Math.pow(2.0, zoom)
            val t = world / n
            val left = cx * world - size.width / 2; val top = cy * world - size.height / 2
            val x0 = kotlin.math.floor(left / t).toInt(); val x1 = kotlin.math.floor((left + size.width) / t).toInt()
            val y0 = kotlin.math.floor(top / t).toInt().coerceAtLeast(0); val y1 = kotlin.math.floor((top + size.height) / t).toInt().coerceAtMost(n - 1)
            // 這一畫面需要的圖磚重新算（排隊中但已不需要的會被跳過）
            TileCache.visible = java.util.concurrent.ConcurrentHashMap.newKeySet()
            for (ty in y0..y1) for (tx in x0..x1) {
                val wx = ((tx % n) + n) % n
                val img = TileCache.mem.get("$style/$z/$wx/$ty")
                if (img == null) {
                    load(z, wx, ty)
                    // 還在下載：先用上面幾級（已經有的）圖磚放大頂著，不會一片空白
                    var drawn = false
                    for (up in 1..4) {
                        val pz = z - up
                        if (pz < 0) break
                        val parent = TileCache.mem.get("$style/$pz/${wx shr up}/${ty shr up}") ?: continue
                        val part = parent.width shr up
                        if (part < 1) break
                        val mask = (1 shl up) - 1
                        drawImage(parent, srcOffset = androidx.compose.ui.unit.IntOffset((wx and mask) * part, (ty and mask) * part),
                            srcSize = androidx.compose.ui.unit.IntSize(part, part),
                            dstOffset = androidx.compose.ui.unit.IntOffset((tx * t - left).toInt(), (ty * t - top).toInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(kotlin.math.ceil(t).toInt() + 1, kotlin.math.ceil(t).toInt() + 1),
                            colorFilter = tileFilter)
                        drawn = true
                        break
                    }
                    if (!drawn) load(z - 1, wx / 2, ty / 2)
                    continue
                }
                drawImage(img, dstOffset = androidx.compose.ui.unit.IntOffset((tx * t - left).toInt(), (ty * t - top).toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(kotlin.math.ceil(t).toInt() + 1, kotlin.math.ceil(t).toInt() + 1),
                    colorFilter = tileFilter)
            }
            // 畫面外一圈先預載：拖動時旁邊已經有圖（下載不忙時才排）
            if (TileCache.inflight.size < 8) {
                for (ty in (y0 - 1)..(y1 + 1)) for (tx in (x0 - 1)..(x1 + 1)) {
                    if (ty in y0..y1 && tx in x0..x1) continue
                    load(z, ((tx % n) + n) % n, ty, pre = true)
                }
            }
            // 地點標記：跟地圖上的店家圖示同一種風格——實心圓＋白邊＋淡淡的陰影，顏色＝分類、大小＝金額
            spots.sortedBy { if (it == picked) 1 else 0 }.forEach { sp ->
                val px = ((mx(sp.lon) - cx) * world + size.width / 2).toFloat(); val py = ((my(sp.lat) - cy) * world + size.height / 2).toFloat()
                val r = (pinR(sp, maxTotal) * 0.8).dp.toPx() * (if (sp == picked) 1.25f else 1f)
                val col = androidx.compose.ui.graphics.Color(palette[cats.indexOf(sp.cat).coerceAtLeast(0) % palette.size])
                val c = androidx.compose.ui.geometry.Offset(px, py)
                drawCircle(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f), r + 3.dp.toPx(), c + androidx.compose.ui.geometry.Offset(0f, 1.5.dp.toPx()))
                drawCircle(androidx.compose.ui.graphics.Color.White, r + 2.dp.toPx(), c)
                drawCircle(col, r, c)
                drawCircle(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f), r * 0.32f, c)
            }
            // 店名：像地圖上的地名一樣，深色字＋白色描邊（深色模式相反），寫在圓點右邊；重疊時只寫金額大的
            val labelFg = if (dark) androidx.compose.ui.graphics.Color(0xFFEDEDED) else androidx.compose.ui.graphics.Color(0xFF2B2B2B)
            val halo = if (dark) androidx.compose.ui.graphics.Color(0xE61E1E21) else androidx.compose.ui.graphics.Color(0xF2FFFFFF)
            val taken = mutableListOf<androidx.compose.ui.geometry.Rect>()
            (listOfNotNull(picked) + spots.take(8)).distinct().forEach { sp ->
                val px = ((mx(sp.lon) - cx) * world + size.width / 2).toFloat(); val py = ((my(sp.lat) - cy) * world + size.height / 2).toFloat()
                if (px < -40 || px > size.width + 40 || py < -20 || py > size.height + 20) return@forEach
                val r = (pinR(sp, maxTotal) * 0.8).dp.toPx()
                val base = androidx.compose.ui.text.TextStyle(fontSize = 11.5.sp, fontWeight = if (sp == picked) FontWeight.SemiBold else FontWeight.Medium)
                val lay = textMeasurer.measure(sp.name.take(10), base.copy(color = labelFg))
                val lx = px + r + 5.dp.toPx(); val ly = py - lay.size.height / 2f
                val rect = androidx.compose.ui.geometry.Rect(lx - 2.dp.toPx(), ly, lx + lay.size.width + 2.dp.toPx(), ly + lay.size.height)
                if (taken.any { it.overlaps(rect) }) return@forEach
                taken += rect
                val haloLay = textMeasurer.measure(sp.name.take(10), base.copy(color = halo,
                    drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round)))
                drawText(haloLay, topLeft = androidx.compose.ui.geometry.Offset(lx, ly))
                drawText(lay, topLeft = androidx.compose.ui.geometry.Offset(lx, ly))
            }
            val credit = textMeasurer.measure(if (style == "mb") "© Mapbox © OpenStreetMap" else "© OpenStreetMap contributors", androidx.compose.ui.text.TextStyle(fontSize = 9.sp, color = if (dark) androidx.compose.ui.graphics.Color(0xAAFFFFFF) else androidx.compose.ui.graphics.Color(0xAA000000)))
            drawText(credit, topLeft = androidx.compose.ui.geometry.Offset(size.width - credit.size.width - 6.dp.toPx(), size.height - credit.size.height - 4.dp.toPx()))
        }
        // 對準店家的進度：地圖右下角一行小字
        status?.let {
            Text(it, fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 22.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
        // 縮放按鈕
        if (controls) Column(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("+" to 1.0, "−" to -1.0).forEach { (l, d) ->
                androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier.size(36.dp).clickable { if (zoom >= 0) zoom = (zoom + d).coerceIn(2.0, 18.0) }) {
                    Box(contentAlignment = Alignment.Center) { Text(l, fontSize = AppText.title) }
                }
            }
        }
        if (!pickOnly) picked?.let { sp ->
            androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp), shadowElevation = 2.dp,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(sp.name, fontSize = AppText.body, fontWeight = FontWeight.SemiBold)
                    Text("${Money.withSymbol(sp.total, "TWD")}・${sp.count} 筆" + (if (sp.cat.isNotBlank()) "・${sp.cat}" else ""), fontSize = AppText.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (moving) Text("在地圖上點正確的位置", fontSize = AppText.caption, color = MaterialTheme.colorScheme.primary)
                    // 只有一個「修正位置」，點了才列出方式
                    var fixMenu by remember(sp.key) { mutableStateOf(false) }
                    Box(Modifier.padding(top = 4.dp)) {
                        Text(if (moving) "取消" else "修正位置", fontSize = AppText.caption, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { if (moving) moving = false else fixMenu = true })
                        androidx.compose.material3.DropdownMenu(fixMenu, { fixMenu = false }) {
                            androidx.compose.material3.DropdownMenuItem(text = { Text("在地圖上點正確位置") }, onClick = { fixMenu = false; moving = true })
                            androidx.compose.material3.DropdownMenuItem(text = { Text("用 Google 地圖找") }, onClick = { fixMenu = false; onGoogle(sp) })
                            if (sp.fixed) androidx.compose.material3.DropdownMenuItem(text = { Text("還原") }, onClick = { fixMenu = false; onReset(sp) })
                        }
                    }
                }
            }
        }
    }
}


/**
 * 在地圖上點選店家位置：一開始對準目前的位置（沒有就用常活動的區域），點一下就把針移過去，按「設定」存成這家店的位置。
 */
@Composable
internal fun MapPicker(name: String, start: Pair<Double, Double>, onDone: (Pair<Double, Double>?) -> Unit) {
    var at by remember { mutableStateOf(start) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.4f
    val spot = SpendSpot(at.first, at.second, name, 1, 1, "", "pick")
    androidx.compose.ui.window.Dialog(onDismissRequest = { onDone(null) },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontSize = AppText.body, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                androidx.compose.material3.TextButton(onClick = { onDone(null) }) { Text("取消") }
                androidx.compose.material3.TextButton(onClick = { onDone(at) }) { Text("設定") }
            }
            SpendMap(listOf(spot), dark, Modifier.fillMaxWidth().height(460.dp).clipToBounds(), "pick", 0,
                status = "點地圖上的正確位置", onSelect = {}, onMove = { _, la, lo -> at = la to lo }, onReset = {}, pickOnly = true)
        }
    }
}


/** 小地圖預覽：顯示一個位置，點一下打開選位置的地圖修改。 */
@Composable
internal fun MapPreview(name: String, at: Pair<Double, Double>, modifier: Modifier, onClick: () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.4f
    val spot = SpendSpot(at.first, at.second, name, 1, 1, "", "preview")
    androidx.compose.foundation.layout.Box(modifier.clipToBounds()) {
        SpendMap(listOf(spot), dark, Modifier.fillMaxSize(), null, 0, onSelect = {}, onMove = { _, _, _ -> }, onReset = {}, controls = false)
        // 蓋一層：整塊只當按鈕用（不拖動地圖，也不擋住頁面捲動）
        androidx.compose.foundation.layout.Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}
