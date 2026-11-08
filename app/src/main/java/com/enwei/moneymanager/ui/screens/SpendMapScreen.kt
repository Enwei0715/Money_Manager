package com.enwei.moneymanager.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
    // 同一家店：記過位置的平均位置
    val merchantLoc = remember(all, samples) {
        all.mapNotNull { t -> val s = samples[t.id] ?: return@mapNotNull null; t.merchant?.trim()?.takeIf { it.isNotEmpty() }?.let { it to (s.lat to s.lon) } }
            .groupBy({ it.first }, { it.second }).mapValues { (_, l) -> l.map { it.first }.average() to l.map { it.second }.average() }
    }
    val inRange = all.filter { t ->
        t.type == TxnType.EXPENSE && t.status == TxnStatus.CONFIRMED &&
            java.time.Instant.ofEpochMilli(t.date).atZone(zone).toLocalDate().let { !it.isBefore(from) && !it.isAfter(to) }
    }
    fun locOf(t: com.enwei.moneymanager.data.TxnEntity): Pair<Double, Double>? =
        samples[t.id]?.let { it.lat to it.lon } ?: t.merchant?.trim()?.let { m -> merchantLoc[m] ?: geo[m] }
    val placed = inRange.mapNotNull { t -> locOf(t)?.let { t to it } }
    val unplaced = inRange.filter { locOf(it) == null }
    val spots = placed.groupBy { (t, l) -> (t.merchant?.trim().orEmpty()) to ("%.4f,%.4f".format(l.first, l.second)) }.map { (k, l) ->
        val total = l.sumOf { lk.fx.convert(it.first.amount, it.first.currency) }
        val cat = l.groupingBy { lk.category(lk.rootId(it.first.categoryId))?.name ?: "" }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
        SpendSpot(l.map { it.second.first }.average(), l.map { it.second.second }.average(), k.first.ifBlank { "（未命名）" }, total, l.size, cat)
    }.sortedByDescending { it.total }
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val html = remember(spots, dark) { mapHtml(spots, dark) }

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
                        if (unplaced.isNotEmpty()) "・沒有位置 ${unplaced.size} 筆" else "",
                        fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                if (spots.isEmpty()) Text("這段期間沒有可以標在地圖上的支出", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp))
                else MapView(html, Modifier.fillMaxWidth().height(360.dp).padding(horizontal = 12.dp).clip(RoundedCornerShape(12.dp)))
            }
            // 沒有位置的店：用店名在 OpenStreetMap 找（每秒一家）
            val missing = unplaced.mapNotNull { it.merchant?.trim()?.takeIf { m -> m.isNotEmpty() && m !in geo } }.distinct()
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MapView(html: String, modifier: Modifier) {
    AndroidView(
        factory = { c -> WebView(c).apply { settings.javaScriptEnabled = true; settings.domStorageEnabled = true; setBackgroundColor(0) } },
        update = { w -> if (w.tag != html) { w.tag = html; w.loadDataWithBaseURL("https://ledgerly.local/", html, "text/html", "utf-8", null) } },
        modifier = modifier,
    )
}

/** Leaflet 地圖（CARTO 底圖，深色模式用深色底圖）：圓的大小依金額，顏色依分類，點一下看店名與金額。 */
private fun mapHtml(spots: List<SpendSpot>, dark: Boolean): String {
    val max = spots.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
    val palette = listOf("#e8564f", "#3c82e6", "#f5a623", "#26a69a", "#ab47bc", "#7cb342", "#ef6c00", "#5c6bc0")
    val cats = spots.map { it.cat }.distinct()
    fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "&lt;")
    val data = spots.joinToString(",") { s ->
        val r = 6 + 18 * kotlin.math.sqrt(s.total.toDouble() / max)
        "{\"a\":${s.lat},\"o\":${s.lon},\"n\":\"${esc(s.name)}\",\"t\":\"${esc(Money.withSymbol(s.total, "TWD"))}\",\"c\":${s.count},\"r\":${"%.1f".format(r)},\"k\":\"${palette[cats.indexOf(s.cat) % palette.size]}\",\"g\":\"${esc(s.cat)}\"}"
    }
    val tiles = if (dark) "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png" else "https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png"
    return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>html,body,#m{margin:0;height:100%;background:${if (dark) "#1e1e21" else "#f2f2f2"}}.leaflet-popup-content{font:14px sans-serif}</style></head>
<body><div id="m"></div><script>
var d=[$data];var m=L.map('m',{zoomControl:false,attributionControl:true});
L.tileLayer('$tiles',{maxZoom:19,attribution:'© OpenStreetMap © CARTO'}).addTo(m);
var g=[];d.forEach(function(p){var c=L.circleMarker([p.a,p.o],{radius:p.r,color:p.k,weight:2,fillColor:p.k,fillOpacity:0.45}).addTo(m);
c.bindPopup('<b>'+p.n+'</b><br>'+p.t+' · '+p.c+' 筆'+(p.g?'<br><span style="color:#888">'+p.g+'</span>':''));g.push([p.a,p.o]);});
if(g.length==1){m.setView(g[0],16);}else{m.fitBounds(g,{padding:[30,30],maxZoom:16});}
</script></body></html>"""
}
