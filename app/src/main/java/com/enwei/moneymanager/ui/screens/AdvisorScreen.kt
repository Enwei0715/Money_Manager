package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.core.Technicals
import com.enwei.moneymanager.core.TradeStyle
import com.enwei.moneymanager.data.AdvisorReport
import com.enwei.moneymanager.data.AdvisorSnapshot
import com.enwei.moneymanager.data.InvestPlanSettings
import com.enwei.moneymanager.data.advisorSnapshot
import com.enwei.moneymanager.data.runAdvisor
import com.enwei.moneymanager.net.GroqAi
import com.enwei.moneymanager.ui.StockDown
import com.enwei.moneymanager.ui.StockUp
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.NumberField
import com.enwei.moneymanager.ui.components.Text
import kotlinx.coroutines.launch

/**
 * 投資助理：大盤階段、資金控管（生活費／長線／短線）、持股的動態停損與分批停利、交易紀律，
 * 以及讀取新聞後由 AI 做的分析與觀察標的。
 */
@Composable
fun AdvisorScreen(nav: NavController) {
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val holdings by repo.holdingsByBroker.collectAsStateWithLifecycle(emptyMap())
    var snap by remember { mutableStateOf<AdvisorSnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var step by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var editPlan by remember { mutableStateOf(false) }
    var editRules by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(holdings, settings.investPlan, settings.stockStyle) {
        snap = runCatching { repo.advisorSnapshot(fetch = true) }.getOrNull()
        loading = false
    }
    fun m(v: Long) = Money.withSymbol(v, "TWD")

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackTopBar("投資助理", { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            val s = snap

            // ① 大盤
            if (s != null) Section("大盤") {
                s.index?.let { b ->
                    val c = b.close
                    val ch5 = Technicals.change(c, 5)
                    Text("加權指數 ${"%,.0f".format(c.last())}", fontSize = 18.sp)
                    Text(
                        listOfNotNull(
                            Technicals.change(c, 1)?.let { "今日 ${"%+.2f".format(it)}%" },
                            ch5?.let { "5 日 ${"%+.1f".format(it)}%" },
                            Technicals.change(c, 20)?.let { "20 日 ${"%+.1f".format(it)}%" },
                        ).joinToString(" · "),
                        fontSize = 13.sp, color = if ((ch5 ?: 0.0) >= 0) StockUp else StockDown,
                    )
                } ?: Text("讀不到指數資料", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                StageChip(s.indexStage.label, s.indexStage.hint)
                if (s.sox != null) Text("費城半導體：${s.soxStage.label}（${s.soxStage.hint}）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ② 資金控管
            if (s != null) Section("資金控管", action = "調整" to { editPlan = true }) {
                val a = s.alloc
                Text("可動用現金 ${m(a.cash)}（已扣信用卡欠款）· 保留生活費 ${m(a.reserve)}", fontSize = 13.sp)
                Text("可投資 ${m(a.investable)}：長線 ${"%.0f".format(settings.investPlan.longRatio * 100)}% / 短線 ${"%.0f".format(100 - settings.investPlan.longRatio * 100)}%", fontSize = 13.sp)
                Bucket("長線", a.longValue, a.longTarget, a.longRoom, ::m)
                Bucket("短線", a.shortValue, a.shortTarget, a.shortRoom, ::m)
                if (a.reserveShort > 0) Text("⚠ 現金比生活費少 ${m(a.reserveShort)}，先不要加碼", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                if (a.shortOver > 0) Text("⚠ 短線超出計畫 ${m(a.shortOver)}，新的短線單先暫停", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            // ③ 持股紀律
            if (s != null && s.holdings.isNotEmpty()) Section("持股紀律（動態停損、分批停利）") {
                s.holdings.forEach { h ->
                    Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${h.symbol} ${h.name.orEmpty()}", fontSize = 15.sp, modifier = Modifier.weight(1f))
                            TradeStyle.entries.forEach { st ->
                                FilterChip(h.style == st, {
                                    repo.settings.update { it.copy(stockStyle = it.stockStyle + (h.symbol to st.name)) }
                                }, label = { Text(st.label, fontSize = 12.sp) }, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                        val g = h.gainPct
                        Text(
                            "均價 ${"%.2f".format(h.avgCost)} → 現價 ${h.price?.let { "%.2f".format(it) } ?: "?"}" +
                                (g?.let { "（${"%+.1f".format(it)}%）" } ?: "") + (h.peak?.let { " · 買進後最高 ${"%.2f".format(it)}" } ?: ""),
                            fontSize = 13.sp, color = if ((g ?: 0.0) >= 0) StockUp else StockDown,
                        )
                        h.advice?.let { ad ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = if (ad.triggered) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(4.dp),
                                ) { Text(ad.action, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                                Text(
                                    "  " + listOfNotNull(
                                        ad.stop.takeIf { it > 0 }?.let { "停損 ${"%.2f".format(it)}" },
                                        ad.nextTakeProfit?.let { "停利 ${"%.2f".format(it)}" },
                                    ).joinToString(" · "),
                                    fontSize = 13.sp,
                                )
                            }
                            Text(ad.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ④ 交易紀律
            Section("我的交易紀律", action = "編輯" to { editRules = true }) {
                settings.investPlan.rules.forEachIndexed { i, r -> Text("${i + 1}. $r", fontSize = 14.sp) }
            }

            // ⑤ AI 分析
            Section("AI 市場分析與建議") {
                if (!GroqAi.enabled) Text("到「設定 → AI 智慧辨識」填入金鑰後，可以讀取新聞並分析。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Button(enabled = step == null, onClick = {
                    err = null
                    scope.launch {
                        val r = runCatching { repo.runAdvisor { step = it } }.getOrNull()
                        if (r == null) err = "分析失敗：${GroqAi.lastError ?: "沒有回應"}"
                        step = null
                    }
                }) { Text(if (step != null) "分析中…" else "讀取新聞並分析") }
                step?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                err?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
                settings.advisorReport?.let { ReportBody(it) }
            }
            Text("AI 分析與推薦僅供參考，不是投資建議；下單前請自行判斷並遵守自己的紀律。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }
    if (editPlan) PlanDialog(settings.investPlan) { editPlan = false }
    if (editRules) RulesDialog(settings.investPlan.rules) { editRules = false }
}

@Composable
private fun Section(title: String, action: Pair<String, () -> Unit>? = null, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                action?.let { (l, f) -> TextButton(onClick = f) { Text(l) } }
            }
            content()
        }
    }
}

@Composable
private fun StageChip(label: String, hint: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(4.dp)) {
            Text(label, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        }
        Text("  $hint", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Bucket(label: String, used: Long, target: Long, room: Long, m: (Long) -> String) {
    Column {
        Row {
            Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("${m(used)} / ${m(target)}", fontSize = 13.sp)
        }
        val f = if (target > 0) (used.toFloat() / target).coerceIn(0f, 1f) else 0f
        Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(f).height(6.dp).background(if (used > target) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
        }
        Text("還可投入 ${m(room)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** AI 分析的固定版面：趨勢與階段 → 需求類型 → 看法 → 持股建議 → 觀察標的 → 資金 → 檢查清單 → 提醒 → 新聞。 */
@Composable
private fun ReportBody(r: AdvisorReport) {
    var showNews by remember { mutableStateOf(false) }
    val gray = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        "分析於 " + java.text.SimpleDateFormat("M/d HH:mm", java.util.Locale.TAIWAN).format(java.util.Date(r.createdAt)) + "（讀了 ${r.news.size} 則新聞）",
        fontSize = 11.sp, color = gray,
    )
    if (r.stage.isNotBlank() || r.trend.isNotBlank()) StageChip(r.stage.ifBlank { "趨勢" }, r.trend)
    if (r.demand.isNotEmpty()) {
        Text("市場需求類型", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(r.demand.joinToString("　") { "#$it" }, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
    }
    if (r.summary.isNotBlank()) Text(r.summary, fontSize = 14.sp)
    if (r.holdings.isNotEmpty()) {
        Text("持股建議", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        r.holdings.forEach { h ->
            Text("${h.symbol}｜${h.action}" + (h.stop?.let { "｜停損 ${"%.2f".format(it)}" } ?: "") + "\n   ${h.reason}", fontSize = 13.sp)
        }
    }
    if (r.ideas.isNotEmpty()) {
        Text("觀察標的", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        r.ideas.forEach { i ->
            Column(Modifier.padding(vertical = 2.dp)) {
                Text(
                    "${i.symbol} ${i.name}" + (if (i.style.isNotBlank()) "（${i.style}）" else "") +
                        (i.price?.let { "　現價 ${"%.2f".format(it)}" } ?: "　代號未確認，請自行查證"),
                    fontSize = 14.sp, color = if (i.price == null) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
                Text(i.reason, fontSize = 13.sp)
                if (i.entry.isNotBlank()) Text("進場：${i.entry}", fontSize = 12.sp, color = gray)
                if (i.stop.isNotBlank()) Text("停損：${i.stop}", fontSize = 12.sp, color = gray)
            }
        }
    }
    if (r.allocation.isNotBlank()) {
        Text("資金分配", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(r.allocation, fontSize = 13.sp)
    }
    if (r.checklist.isNotEmpty()) {
        Text("交易前檢查", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        r.checklist.forEach { Text("☐ $it", fontSize = 13.sp) }
    }
    if (r.warnings.isNotEmpty()) {
        Text("提醒", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        r.warnings.forEach { Text("! $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
    }
    if (r.news.isNotEmpty()) {
        Text(if (showNews) "收起新聞 ▴" else "看讀過的新聞 ▾", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { showNews = !showNews })
        if (showNews) r.news.forEach { Text("• $it", fontSize = 12.sp, color = gray) }
    }
}

@Composable
private fun PlanDialog(p: InvestPlanSettings, onDismiss: () -> Unit) {
    var reserve by remember { mutableStateOf(Money.fromMinor(p.reserve).stripTrailingZeros().toPlainString()) }
    var longPct by remember { mutableStateOf("%.0f".format(p.longRatio * 100)) }
    var stop by remember { mutableStateOf(p.shortStopPct.toString()) }
    var trail by remember { mutableStateOf(p.shortTrailPct.toString()) }
    var longTrail by remember { mutableStateOf(p.longTrailPct.toString()) }
    var tp by remember { mutableStateOf(p.takeProfitPct.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("資金與紀律參數") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(reserve, { reserve = it }, "保留生活費", suffix = "元")
                NumberField(longPct, { longPct = it }, "長線占可投資資金", suffix = "%")
                NumberField(stop, { stop = it }, "短線停損", suffix = "%")
                NumberField(trail, { trail = it }, "短線移動停損（從高點回落）", suffix = "%")
                NumberField(longTrail, { longTrail = it }, "長線移動停損（從高點回落）", suffix = "%")
                NumberField(tp, { tp = it }, "分批停利門檻（長線為兩倍）", suffix = "%")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                repo.settings.update {
                    it.copy(
                        investPlan = p.copy(
                            reserve = Money.parseMinor(reserve) ?: p.reserve,
                            longRatio = (longPct.toDoubleOrNull() ?: (p.longRatio * 100)).coerceIn(0.0, 100.0) / 100,
                            shortStopPct = stop.toDoubleOrNull()?.coerceIn(1.0, 50.0) ?: p.shortStopPct,
                            shortTrailPct = trail.toDoubleOrNull()?.coerceIn(1.0, 50.0) ?: p.shortTrailPct,
                            longTrailPct = longTrail.toDoubleOrNull()?.coerceIn(1.0, 60.0) ?: p.longTrailPct,
                            takeProfitPct = tp.toDoubleOrNull()?.coerceIn(1.0, 200.0) ?: p.takeProfitPct,
                        ),
                    )
                }
                onDismiss()
            }) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RulesDialog(rules: List<String>, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(rules.joinToString("\n")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("我的交易紀律（一行一條）") },
        text = { OutlinedTextField(text, { text = it }, minLines = 6, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            TextButton(onClick = {
                val list = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                repo.settings.update { it.copy(investPlan = it.investPlan.copy(rules = list.ifEmpty { InvestPlanSettings().rules })) }
                onDismiss()
            }) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
