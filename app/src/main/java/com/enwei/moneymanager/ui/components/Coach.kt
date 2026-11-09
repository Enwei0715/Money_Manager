package com.enwei.moneymanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.enwei.moneymanager.ui.AppText

/**
 * 聚光燈導覽（像遊戲的新手教學）：把畫面調暗，只亮出要介紹的那個按鈕或區塊，旁邊一張說明卡。
 * 各畫面用 [coachTarget] 標記位置；[Coach.start] 開始，可上一步、下一步或「跳過全部」。
 */
object Coach {
    /** 每個標記目前在螢幕上的位置 */
    val rects = mutableStateMapOf<String, Rect>()
    var step by androidx.compose.runtime.mutableIntStateOf(-1)
        private set

    /** 一步：要亮出的目標（null＝不亮任何東西，置中說明）、要先切到哪個分頁 */
    data class Step(val target: String?, val title: String, val body: String, val tab: String? = null)

    val steps = listOf(
        Step(null, "帶你逛一圈", "接下來用一分鐘，一個一個指給你看每個功能在哪裡。隨時可以按「跳過全部」。"),
        Step("tab.txns", "交易", "每天的收入、支出都在這裡，依日期排好。", tab = "txns"),
        Step("txns.summary", "本月總覽", "這個月的收入、支出與結餘；設了預算的話，下方會看到用了多少、每天還能花多少。", tab = "txns"),
        Step("fab.add", "記一筆", "按這裡記帳：輸入金額、選分類與帳戶就好。上方可以切換支出／收入／轉帳／股票。", tab = "txns"),
        Step("txns.map", "支出地圖", "記帳時有定位的話，可以在地圖上看每天、每週、每月在哪裡花錢。", tab = "txns"),
        Step("txns.search", "搜尋與篩選", "找某家店、某個分類或某段期間的交易，也能看篩選後的圖表。", tab = "txns"),
        Step("tab.stats", "統計", "錢花在哪：分類圓餅、每月比較、收支趨勢。", tab = "stats"),
        Step("tab.accounts", "資產", "所有帳戶、信用卡（依每期帳單算應繳）、往來（誰欠你、你欠誰）與股票。", tab = "accounts"),
        Step("tab.assistant", "股票助理", "每日報告、新聞整理、問 AI 與資金紀律。需要先在設定裡填 AI 金鑰。", tab = "assistant"),
        Step("tab.more", "工具", "預算、週期交易、備份、通知記帳等工具都在這；可以按「編輯」調整排列。", tab = "more"),
        Step("more.settings", "設定", "外觀（主題、字體、版面）、記帳方式、通知與 AI 都在設定裡。", tab = "more"),
        Step(null, "就這樣！", "開始記第一筆吧。想再看一次，到「設定 → 關於 → 新手導覽」。", tab = "txns"),
    )

    /** 這次要走的步驟：依問卷與功能開關，關掉的功能不介紹 */
    var active by androidx.compose.runtime.mutableStateOf(steps)
        private set

    fun start(s: com.enwei.moneymanager.data.AppSettings? = null, split: Boolean = true) {
        active = steps.filter { st ->
            when (st.target) {
                "tab.assistant" -> s?.featureStocks != false
                "txns.map" -> s?.locationHints != false
                else -> true
            }
        }.map { st ->
            if (st.target == "fab.add" && split) st.copy(body = st.body + "幫別人付錢時點「分帳」，可以分給好幾個人。") else st
        }
        step = 0
    }
    fun stop() { step = -1 }
    fun next() { step = if (step + 1 < active.size) step + 1 else -1 }
    fun prev() { if (step > 0) step-- }
}

/** 標記一個可以被導覽亮出來的元件。 */
fun Modifier.coachTarget(id: String): Modifier = this.onGloballyPositioned { Coach.rects[id] = it.boundsInRoot() }

/** 導覽的覆蓋層：放在整個 App 最上層。[onTab] 切換底部分頁。 */
@Composable
fun CoachOverlay(onTab: (String) -> Unit) {
    val i = Coach.step
    val steps = Coach.active
    if (i !in steps.indices) return
    val st = steps[i]
    LaunchedEffect(i) { st.tab?.let(onTab) }
    val density = LocalDensity.current
    val hole = st.target?.let { Coach.rects[it] }?.takeIf { it.width > 0 && it.height > 0 }
    val dim = Color.Black.copy(alpha = 0.72f)
    val ring = MaterialTheme.colorScheme.primary
    BoxWithConstraints(
        Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { Coach.next() },
    ) {
        val pad = with(density) { 6.dp.toPx() }
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(dim)
            if (hole != null) {
                val tl = Offset(hole.left - pad, hole.top - pad)
                val sz = Size(hole.width + pad * 2, hole.height + pad * 2)
                drawRoundRect(Color.Transparent, tl, sz, CornerRadius(14f, 14f), blendMode = BlendMode.Clear)
                drawRoundRect(ring, tl, sz, CornerRadius(14f, 14f), style = Stroke(4f))
            }
        }
        // 說明卡：目標在下半部就放上面，反之放下面；沒有目標就置中
        val screenH = with(density) { maxHeight.toPx() }
        val cardModifier = when {
            hole == null -> Modifier.align(Alignment.Center)
            hole.center.y > screenH / 2 -> Modifier.align(Alignment.TopCenter)
                .offset { IntOffset(0, (hole.top - pad - with(density) { 220.dp.toPx() }).toInt().coerceAtLeast(with(density) { 48.dp.roundToPx() })) }
            else -> Modifier.align(Alignment.TopCenter).offset { IntOffset(0, (hole.bottom + pad + with(density) { 12.dp.toPx() }).toInt()) }
        }
        Surface(
            color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp), shadowElevation = 6.dp,
            modifier = cardModifier.padding(horizontal = 20.dp).widthIn(max = 420.dp).fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(st.title, fontSize = AppText.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${i + 1} / ${steps.size}", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(st.body, fontSize = AppText.body)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (i < steps.lastIndex) TextButton(onClick = { Coach.stop(); onTab("txns") }) { Text("跳過全部") }
                    Spacer(Modifier.weight(1f))
                    if (i > 0) TextButton(onClick = { Coach.prev() }) { Text("上一步") }
                    Button(onClick = { Coach.next() }) { Text(if (i < steps.lastIndex) "下一步" else "開始使用") }
                }
            }
        }
    }
}
