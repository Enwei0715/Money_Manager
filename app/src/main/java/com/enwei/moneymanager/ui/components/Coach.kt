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

    /** 功能鍵：notif 通知記帳、split 往來、map 定位與地圖、stocks 股票、ai AI；null＝基本功能都會介紹 */
    private data class S(val need: String?, val step: Step)

    private fun all(stocks: Boolean, ai: Boolean, split: Boolean) = listOf(
        S(null, Step(null, "功能導覽", "接下來依序介紹各頁面，以及你在設定中選擇開啟的功能。可隨時點選「跳過全部」，日後於「設定 → 關於 → 新手導覽」重新查看。")),
        S(null, Step("tab.txns", "交易", "每日的收入與支出依日期排列；點選交易可查看與修改。", tab = "txns")),
        S(null, Step("txns.summary", "本月總覽", "本月的收入、支出與結餘；設定預算後，可在此查看使用比例與每日可用金額。", tab = "txns")),
        S(null, Step("fab.add", "新增交易", "輸入金額，選擇分類與帳戶即可儲存；上方可切換支出、收入、轉帳" + (if (stocks) "與股票" else "") + "。" +
            (if (split) "與他人分帳、代墊或收回欠款時，使用表單最下方的「往來」。" else ""), tab = "txns")),
        S("notif", Step(null, "通知自動記帳", "銀行、信用卡與行動支付的付款通知會自動記為交易，帳戶與分類依通知內容與過去的紀錄判斷。無法確定的交易會列為「待確認」，交易頁上方出現鈴鐺圖示並顯示筆數，點選即可逐筆確認或修改。", tab = "txns")),
        S("map", Step("txns.map", "定位與支出地圖", "付款時記錄所在位置：記帳時推薦附近店家，並可在地圖上依日、週、月檢視消費地點。點選下方清單中的店家可導航或修正位置。", tab = "txns")),
        S(null, Step("txns.search", "搜尋與篩選", "依店家、說明、分類、帳戶或期間查詢交易，並檢視篩選結果的圖表與趨勢。", tab = "txns")),
        S(null, Step("tab.stats", "統計", "分類比例、每月比較與收支趨勢；可切換期間與收入、支出。", tab = "stats")),
        S(null, Step("tab.accounts", "資產", "所有帳戶依群組列出，頂端為資產、負債與合計；信用卡依帳單週期計算本期應繳與未出帳金額。", tab = "accounts")),
        S("split", Step("ac.debts", "往來", "與他人分攤費用、代墊與借還款的紀錄集中於此，列出每位對象的應收與應付；點選對象可查看明細並記錄還款。", tab = "accounts")),
        S("stocks", Step("ac.tab1", "股票", "分為「持股」與「觀察」：持股為實際買入、計入資產的股票，顯示市值與損益；觀察僅追蹤報價。右上角可新增交易與更新股價。", tab = "accounts")),
        S("stocks", Step("tab.assistant", "股票助理", if (ai) "今日報告、問 AI、新聞整理與紀律四個分頁：AI 依你的持股與新聞產生每日報告，紀律分頁依你設定的門檻提示停損與停利。"
            else "「紀律」分頁依你設定的門檻提示停損與停利，並可記錄自己的投資原則；完成 AI 設定後，可使用每日報告與 AI 問答。", tab = "assistant")),
        S(null, Step("tab.more", "工具", "常用工具集中於此；點選右上角「編輯」或長按任一格，可調整排列、大小或隱藏。", tab = "more")),
        S(null, Step("tool.budget", "預算", "設定每月總預算與各分類預算；接近或超過上限時提醒，交易頁的本月總覽也會顯示使用比例。", tab = "more")),
        S(null, Step("tool.recurring", "週期交易", "房租、訂閱、薪資等固定收支，設定週期後於到期日自動記錄。", tab = "more")),
        S("notif", Step("tool.notif", "通知記帳設定", "選擇要讀取哪些 App 的通知、查看通知紀錄並測試辨識結果。Android 15 會隱藏部分付款通知，可依頁面中的步驟以 Shizuku 開啟讀取。", tab = "more")),
        S("ai", Step("tool.ai-hub", "AI 中心", "設定 AI 金鑰或自架伺服器並測試連線；AI 用於判讀通知、收支分析與每日投資報告。", tab = "more")),
        S(null, Step("tool.review", "收支分析", "依期間整理支出總覽、與前期的變化及消費習慣" + (if (ai) "；AI 建議分頁會指出可調整之處。" else "。"), tab = "more")),
        S("map", Step("tool.merchants", "店家位置", "管理已記錄的店家位置；設定 Google Places 或 Mapbox 金鑰可提高店家搜尋的準確度，未設定時使用 OpenStreetMap。", tab = "more")),
        S(null, Step("tool.backup", "備份", "所有資料只存在這支手機；建議設定自動備份到雲端硬碟，換手機時在新手機還原即可。", tab = "more")),
        S(null, Step("more.settings", "設定", "外觀、記帳方式、通知與 AI 等設定。「功能開關」可隨時開啟或關閉上述功能，「功能設定檢查」列出尚未完成的設定。", tab = "more")),
        S(null, Step(null, "導覽完成", "第一次進入各頁面時，還會有該頁的詳細說明。如需再次查看，請至「設定 → 關於 → 新手導覽」。", tab = "txns")),
    )

    val steps: List<Step> get() = all(true, true, true).map { it.step }

    /** 這次要走的步驟：依問卷與功能開關，關掉的功能不介紹 */
    var active by androidx.compose.runtime.mutableStateOf(steps)
        private set

    /** 頁面導覽（第一次打開某個頁面時的簡短介紹）：不切換分頁，跳過就只是關掉 */
    var pageMode by androidx.compose.runtime.mutableStateOf(false)
        private set
    private val seen by lazy { com.enwei.moneymanager.MoneyApp.instance.getSharedPreferences("page_tours", android.content.Context.MODE_PRIVATE) }

    /** 第一次來到 [id] 這個頁面時走一次 [steps]；看過就不再出現 */
    fun page(id: String, steps: List<Step>) {
        if (step >= 0 || seen.getBoolean(id, false) || steps.isEmpty()) return
        seen.edit().putBoolean(id, true).apply()
        active = steps; pageMode = true; step = 0
    }
    /** 重看導覽時：各頁面的介紹也重新出現 */
    fun resetPages() = seen.edit().clear().apply()

    /** 開始整體導覽：只介紹基本功能與 [picks]（問卷勾選的功能；null 時依目前設定） */
    fun start(s: com.enwei.moneymanager.data.AppSettings? = null, picks: Set<String>? = null) {
        pageMode = false
        resetPages()
        val w = picks ?: buildSet {
            if (s?.featureNotif != false) add("notif"); if (s?.locationHints == true) add("map")
            if (s?.featureStocks != false) add("stocks"); if (s?.aiEnabled == true) add("ai"); if (s?.featureSplit != false) add("split")
        }
        // 功能開關以實際設定為準（問卷勾了但之後關掉的不介紹）
        val on = w.filter { k ->
            when (k) {
                "notif" -> s?.featureNotif != false
                "stocks" -> s?.featureStocks != false
                "split" -> s?.featureSplit != false
                "map" -> s?.locationHints != false
                "ai" -> s?.aiEnabled != false
                else -> true
            }
        }.toSet()
        active = all("stocks" in on, "ai" in on, "split" in on).filter { it.need == null || it.need in on }.map { it.step }
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
                    if (i < steps.lastIndex) TextButton(onClick = { Coach.stop(); if (!Coach.pageMode) onTab("txns") }) { Text(if (Coach.pageMode) "略過" else "跳過全部") }
                    Spacer(Modifier.weight(1f))
                    if (i > 0) TextButton(onClick = { Coach.prev() }) { Text("上一步") }
                    Button(onClick = { Coach.next() }) { Text(if (i < steps.lastIndex) "下一步" else if (Coach.pageMode) "知道了" else "開始使用") }
                }
            }
        }
    }
}


/** 頁面第一次打開時的簡短導覽（等畫面排好再開始，才找得到要亮的位置）。 */
@Composable
fun PageTour(id: String, steps: List<Coach.Step>) {
    LaunchedEffect(id) {
        kotlinx.coroutines.delay(700)
        Coach.page(id, steps)
    }
}
