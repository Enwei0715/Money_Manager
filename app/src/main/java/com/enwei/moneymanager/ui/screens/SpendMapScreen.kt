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
private val ONLINE = listOf("蝦皮", "Claude", "Firstory", "CapCut", "ElevenLabs", "Endel", "Uber One", "Netflix", "Spotify", "YouTube", "Apple", "Google",
    "LINE 貼圖", "LINE 主題", "LINE 官方", "MyCard", "Steam", "Chess.com", "傳說對決", "Brawl Stars", "Clash of Clans", "Pixel Survival", "Sky 光遇",
    "售票網站", "銀行手續費", "對帳調整", "房東", "電費", "保險", "Uber Eats", "foodpanda", "momo", "PChome", "Amazon")
private val ONLINE_ROOTS = listOf("訂閱", "保險", "金融費用", "居住")

/** 連鎖店：每家分店位置不同，只用付款當下記下的位置（不套用其他分店、不用店名找）。 */
private val CHAINS = listOf("7-11", "全家", "萊爾富", "OK超商", "全聯", "小北百貨", "麥當勞", "摩斯漢堡", "肯德基", "85度C", "星巴克", "路易莎", "大創", "愛買",
    "家樂福", "寶雅", "屈臣氏", "康是美", "吉野家", "八方", "可不可", "50嵐", "迷客夏", "中油", "台北捷運", "公車", "YouBike", "台鐵", "台灣高鐵", "Subway", "漢堡王", "Burger King")

/** 地圖上的一個點：同一家店（或同一個位置）在這段期間的支出合計。 */
private data class SpendSpot(val lat: Double, val lon: Double, val name: String, val total: Long, val count: Int, val cat: String)

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
        return ONLINE.any { m.contains(it, ignoreCase = true) } || ONLINE_ROOTS.any { root.contains(it) }
    }
    fun chain(m: String) = CHAINS.any { m.contains(it, ignoreCase = true) }
    // 同一家店：記過位置的平均位置
    val merchantLoc = remember(all, samples) {
        all.mapNotNull { t -> val s = samples[t.id] ?: return@mapNotNull null; t.merchant?.trim()?.takeIf { it.isNotEmpty() }?.let { it to (s.lat to s.lon) } }
            .groupBy({ it.first }, { it.second })
            // 同一個店名在相距 300 公尺以上的地方都記過：當成連鎖店，不套用平均位置
            .filter { (m, l) -> !chain(m) && l.all { p -> l.all { q -> FloatArray(1).also { r -> android.location.Location.distanceBetween(p.first, p.second, q.first, q.second, r) }[0] < 300f } } }
            .mapValues { (_, l) -> l.map { it.first }.average() to l.map { it.second }.average() }
    }
    val inRange = all.filter { t ->
        t.type == TxnType.EXPENSE && t.status == TxnStatus.CONFIRMED &&
            java.time.Instant.ofEpochMilli(t.date).atZone(zone).toLocalDate().let { !it.isBefore(from) && !it.isAfter(to) }
    }
    fun locOf(t: com.enwei.moneymanager.data.TxnEntity): Pair<Double, Double>? =
        samples[t.id]?.let { it.lat to it.lon } ?: t.merchant?.trim()?.takeIf { !chain(it) }?.let { m -> merchantLoc[m] ?: geo[m] }
    val offline = inRange.filter { !online(it) }
    val onlineCount = inRange.size - offline.size
    val placed = offline.mapNotNull { t -> locOf(t)?.let { t to it } }
    val unplaced = offline.filter { locOf(it) == null }
    val spots = placed.groupBy { (t, l) -> (t.merchant?.trim().orEmpty()) to ("%.4f,%.4f".format(l.first, l.second)) }.map { (k, l) ->
        val total = l.sumOf { lk.fx.convert(it.first.amount, it.first.currency) }
        val cat = l.groupingBy { lk.category(lk.rootId(it.first.categoryId))?.name ?: "" }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
        SpendSpot(l.map { it.second.first }.average(), l.map { it.second.second }.average(), k.first.ifBlank { "（未命名）" }, total, l.size, cat)
    }.sortedByDescending { it.total }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.4f

    Scaffold(topBar = { BackTopBar("支出地圖", { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            item {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.enwei.moneymanager.ui.components.SubTabs(listOf("每日", "每週", "每月"), span) { span = it }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { anchor = when (span) { 0 -> anchor.minusDays(1); 1 -> anchor.minusWeeks(1); else -> anchor.minusMonths(1) } }) { Icon(Icons.Filled.ChevronLeft, "上一期") }
                        Text(label, fontSize = AppText.section, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        IconButton(onClick = { anchor = when (span) { 0 -> anchor.plusDays(1); 1 -> anchor.plusWeeks(1); else -> anchor.plusMonths(1) } }) { Icon(Icons.Filled.ChevronRight, "下一期") }
                    }
                    val placedSum = placed.sumOf { lk.fx.convert(it.first.amount, it.first.currency) }
                    Text("地圖上 ${placed.size} 筆 ${Money.withSymbol(placedSum, lk.base)}" +
                        (if (unplaced.isNotEmpty()) "・沒有位置 ${unplaced.size} 筆" else "") + (if (onlineCount > 0) "・線上 $onlineCount 筆" else ""),
                        fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                if (spots.isEmpty()) Text("這段期間沒有可以標在地圖上的支出", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp))
                else SpendMap(spots, dark, Modifier.fillMaxWidth().height(380.dp).clipToBounds())
            }
            // 沒有位置的店：用店名在 OpenStreetMap 找（每秒一家）
            val missing = unplaced.mapNotNull { it.merchant?.trim()?.takeIf { m -> m.isNotEmpty() && m !in geo && !chain(m) } }.distinct()
            if (missing.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(progress ?: "${missing.size} 家店還沒有位置", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    OutlinedButton(enabled = progress == null, onClick = {
                        val center = samples.values.takeIf { it.isNotEmpty() }?.let { s -> s.map { it.lat }.average() to s.map { it.lon }.average() } ?: (25.033 to 121.565)
                        scope.launch {
                            val n = PlaceHints.geocodeMerchants(missing.take(40), center) { i, total -> progress = "找位置中 $i/$total…" }
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
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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

/** 地圖圖磚的記憶體快取（最近用過的 120 張）。 */
private object TileCache {
    val mem = object : android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(120) {}
    val inflight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    val failed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    val gate = kotlinx.coroutines.sync.Semaphore(4)
}

private fun mx(lon: Double) = (lon + 180.0) / 360.0
private fun my(lat: Double): Double {
    val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
    return (1 - kotlin.math.ln(kotlin.math.tan(r) + 1 / kotlin.math.cos(r)) / Math.PI) / 2
}

/**
 * 支出地圖（App 自己畫，不靠 WebView）：CARTO 圖磚＋每個地點一個圓（越大花越多、顏色依分類）。
 * 手指拖曳移動、兩指縮放、點兩下放大；點圓顯示店名與金額。
 */
@Composable
private fun SpendMap(spots: List<SpendSpot>, dark: Boolean, modifier: Modifier) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val tilePx = with(density) { 256.dp.toPx() }
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf(0) }
    var sizePx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var zoom by remember(spots) { mutableStateOf(-1.0) }
    var cx by remember(spots) { mutableStateOf(0.5) }
    var cy by remember(spots) { mutableStateOf(0.5) }
    var picked by remember(spots) { mutableStateOf<SpendSpot?>(null) }
    val palette = listOf(0xFFE8564F, 0xFF3C82E6, 0xFFF5A623, 0xFF26A69A, 0xFFAB47BC, 0xFF7CB342, 0xFFEF6C00, 0xFF5C6BC0)
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
    val style = if (dark) "dark_all" else "rastertiles/voyager"
    fun load(z: Int, x: Int, y: Int) {
        val key = "$style/$z/$x/$y"
        if (TileCache.mem.get(key) != null || key in TileCache.failed || !TileCache.inflight.add(key)) return
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                TileCache.gate.acquire()
                try {
                    val url = "https://${"abcd"[(x + y) and 3]}.basemaps.cartocdn.com/$style/$z/$x/$y@2x.png"
                    val req = okhttp3.Request.Builder().url(url).header("User-Agent", "Ledgerly/2.1 (Android)").build()
                    com.enwei.moneymanager.net.Http.client.newCall(req).execute().use { r ->
                        val bytes = r.body?.bytes()
                        val bmp = if (r.isSuccessful && bytes != null) android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) else null
                        if (bmp != null) TileCache.mem.put(key, bmp.asImageBitmap()) else TileCache.failed.add(key)
                    }
                } finally { TileCache.gate.release() }
            } catch (_: Exception) {
            } finally {
                TileCache.inflight.remove(key)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { version++ }
            }
        }
    }
    val bg = if (dark) androidx.compose.ui.graphics.Color(0xFF1E1E21) else androidx.compose.ui.graphics.Color(0xFFF2F2F2)
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
                .pointerInput(spots) {
                    detectTapGestures(
                        onDoubleTap = { if (zoom >= 0) zoom = (zoom + 1).coerceAtMost(18.0) },
                        onTap = { o ->
                            if (zoom >= 0) {
                                val world = tilePx * Math.pow(2.0, zoom)
                                fun dist(sp: SpendSpot) = kotlin.math.hypot((mx(sp.lon) - cx) * world + sizePx.width / 2 - o.x, (my(sp.lat) - cy) * world + sizePx.height / 2 - o.y)
                                picked = spots.minByOrNull { dist(it) }?.takeIf { dist(it) < 30.dp.toPx() }
                            }
                        },
                    )
                },
        ) {
            @Suppress("UNUSED_VARIABLE") val v = version
            if (zoom < 0) return@Canvas
            val z = kotlin.math.round(zoom).toInt().coerceIn(2, 18)
            val n = 1 shl z
            val world = tilePx * Math.pow(2.0, zoom)
            val t = world / n
            val left = cx * world - size.width / 2; val top = cy * world - size.height / 2
            val x0 = kotlin.math.floor(left / t).toInt(); val x1 = kotlin.math.floor((left + size.width) / t).toInt()
            val y0 = kotlin.math.floor(top / t).toInt().coerceAtLeast(0); val y1 = kotlin.math.floor((top + size.height) / t).toInt().coerceAtMost(n - 1)
            for (ty in y0..y1) for (tx in x0..x1) {
                val wx = ((tx % n) + n) % n
                val img = TileCache.mem.get("$style/$z/$wx/$ty")
                if (img == null) { load(z, wx, ty); continue }
                drawImage(img, dstOffset = androidx.compose.ui.unit.IntOffset((tx * t - left).toInt(), (ty * t - top).toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(kotlin.math.ceil(t).toInt() + 1, kotlin.math.ceil(t).toInt() + 1))
            }
            spots.forEach { sp ->
                val px = ((mx(sp.lon) - cx) * world + size.width / 2).toFloat(); val py = ((my(sp.lat) - cy) * world + size.height / 2).toFloat()
                val r = (6 + 16 * kotlin.math.sqrt(sp.total.toDouble() / maxTotal)).dp.toPx()
                val col = androidx.compose.ui.graphics.Color(palette[cats.indexOf(sp.cat).coerceAtLeast(0) % palette.size])
                drawCircle(col.copy(alpha = 0.45f), r, androidx.compose.ui.geometry.Offset(px, py))
                drawCircle(col, r, androidx.compose.ui.geometry.Offset(px, py), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                if (sp == picked) drawCircle(androidx.compose.ui.graphics.Color.White, r + 3.dp.toPx(), androidx.compose.ui.geometry.Offset(px, py), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            }
            val credit = textMeasurer.measure("© OpenStreetMap © CARTO", androidx.compose.ui.text.TextStyle(fontSize = 9.sp, color = if (dark) androidx.compose.ui.graphics.Color(0xAAFFFFFF) else androidx.compose.ui.graphics.Color(0xAA000000)))
            drawText(credit, topLeft = androidx.compose.ui.geometry.Offset(size.width - credit.size.width - 6.dp.toPx(), size.height - credit.size.height - 4.dp.toPx()))
        }
        // 縮放按鈕
        Column(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("+" to 1.0, "−" to -1.0).forEach { (l, d) ->
                androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier.size(36.dp).clickable { if (zoom >= 0) zoom = (zoom + d).coerceIn(2.0, 18.0) }) {
                    Box(contentAlignment = Alignment.Center) { Text(l, fontSize = AppText.title) }
                }
            }
        }
        picked?.let { sp ->
            androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp), shadowElevation = 2.dp,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(sp.name, fontSize = AppText.body, fontWeight = FontWeight.SemiBold)
                    Text("${Money.withSymbol(sp.total, "TWD")}・${sp.count} 筆" + (if (sp.cat.isNotBlank()) "・${sp.cat}" else ""), fontSize = AppText.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
