package com.enwei.moneymanager.ui.screens

import com.enwei.moneymanager.ui.components.coachTarget
import com.enwei.moneymanager.ui.AppText

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDateRangePickerState
import com.enwei.moneymanager.core.Periods
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.enwei.moneymanager.ui.components.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.data.TxnEntity
import com.enwei.moneymanager.data.TxnStatus
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.data.toLocalDate
import com.enwei.moneymanager.ui.ExpenseColor
import com.enwei.moneymanager.ui.IncomeColor
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.EmptyHint
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlin.math.cos
import kotlin.math.sin

/** 由暖到冷的漸層色：比例大的分類用珊瑚紅，依序到橘、黃、綠、青、藍、紫。 */
val chartPalette = listOf(
    Color(0xFFF7685F), Color(0xFFFF9447), Color(0xFFFFC145), Color(0xFFFFE330), Color(0xFFC6E64B),
    Color(0xFF6CD26C), Color(0xFF4FE0CF), Color(0xFF4DB6F2), Color(0xFF7D8DF5), Color(0xFFB27CF0), Color(0xFFF07CC6),
)

/** 色塊上的文字顏色：淺色底用深色字。 */
fun onChartColor(c: Color): Color = if (c.luminance() > 0.55f) Color(0xFF3A3A3A) else Color.White

enum class Period(val label: String) { WEEK("每週"), MONTH("每月"), YEAR("每年"), CUSTOM("期間") }

data class PeriodRange(val start: LocalDate, val end: LocalDate, val title: String)

fun rangeOf(period: Period, anchor: LocalDate, custom: Pair<LocalDate, LocalDate>? = null): PeriodRange {
    val s = repo.settings.value
    return when (period) {
        Period.WEEK -> {
            val (a, b) = Periods.week(anchor, DayOfWeek.of(s.weekStartDay))
            PeriodRange(a, b, "${a.monthValue}/${a.dayOfMonth} ~ ${b.monthValue}/${b.dayOfMonth}")
        }
        Period.MONTH -> {
            val m = Periods.monthOf(anchor, s.monthStartDay)
            val (a, b) = Periods.month(m, s.monthStartDay)
            PeriodRange(a, b, "${m.year}年${m.monthValue}月")
        }
        Period.YEAR -> PeriodRange(LocalDate.of(anchor.year, 1, 1), LocalDate.of(anchor.year, 12, 31), "${anchor.year}年")
        Period.CUSTOM -> {
            val (a, b) = custom ?: (anchor.withDayOfMonth(1) to anchor)
            PeriodRange(a, b, "${a.year % 100}/${a.monthValue}/${a.dayOfMonth} ~ ${b.year % 100}/${b.monthValue}/${b.dayOfMonth}")
        }
    }
}

fun shift(period: Period, anchor: LocalDate, n: Long): LocalDate = when (period) {
    Period.WEEK -> anchor.plusWeeks(n)
    Period.MONTH, Period.CUSTOM -> anchor.plusMonths(n)
    Period.YEAR -> anchor.plusYears(n)
}

private fun List<TxnEntity>.inRange(r: PeriodRange) = filter { val d = it.date.toLocalDate(); !d.isBefore(r.start) && !d.isAfter(r.end) }

@Composable
fun StatsTab(nav: NavController) {
    // 第一次打開這頁：逐一介紹期間、類型與圖表
    com.enwei.moneymanager.ui.components.PageTour("stats", listOf(
        com.enwei.moneymanager.ui.components.Coach.Step("st.period", "期間", "左右箭頭切換上一期或下一期；點選中間的期間名稱可回到本期（自訂期間時則重新選擇期間）。"),
        com.enwei.moneymanager.ui.components.Coach.Step("st.kind", "類型與期間長度", "切換支出或收入，以及每週、每月、每年或自訂期間。"),
        com.enwei.moneymanager.ui.components.Coach.Step("st.pie", "分類比例", "圓餅圖顯示各分類所佔比例；下方清單點選分類可查看子分類與明細交易。"),
        com.enwei.moneymanager.ui.components.Coach.Step(null, "每月比較與分析", "往下捲動可查看近 6 個月比較；上方「分析」分頁整理支出總覽、與前期的變化及消費習慣。"),
    ))
    val lk = rememberLookups()
    // 期間與收支種類都記住，下次回來維持上次的檢視
    var period by com.enwei.moneymanager.ui.components.rememberChoice("stats.period", Period.entries, Period.MONTH)
    var anchor by rememberSaveable { mutableStateOf(LocalDate.now()) }
    // 統計頁是所有分析的家：「分類」看比例與每月比較，「分析」是收支檢討（原本在交易頁）
    var view by com.enwei.moneymanager.ui.components.rememberTab("stats.view", 0)
    var reviewSpan by rememberSaveable { mutableStateOf(com.enwei.moneymanager.data.ReviewSpan.MONTH) }
    LaunchedEffect(com.enwei.moneymanager.ui.TabRequest.statsView) {
        com.enwei.moneymanager.ui.TabRequest.statsView?.let { view = it; com.enwei.moneymanager.ui.TabRequest.statsView = null }
    }
    // 分析檢視：上一期／下一期依檢討的期間（月、季、年）移動
    fun step(n: Int) {
        anchor = if (view == 1) when (reviewSpan) {
            com.enwei.moneymanager.data.ReviewSpan.QUARTER -> anchor.plusMonths(3L * n)
            com.enwei.moneymanager.data.ReviewSpan.YEAR -> anchor.plusYears(n.toLong())
            else -> anchor.plusMonths(n.toLong())
        } else shift(period, anchor, n.toLong())
    }
    // 同一天內保留你選的期間；隔天再打開時回到本期（避免停在上個月卻沒發現）
    var anchorDay by rememberSaveable { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) { if (anchorDay != LocalDate.now()) { anchor = LocalDate.now(); anchorDay = LocalDate.now() } }
    var kind by com.enwei.moneymanager.ui.components.rememberChoice("stats.kind", TxnType.entries, TxnType.EXPENSE)
    var menu by remember { mutableStateOf(false) }
    var customStart by rememberSaveable { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var customEnd by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var pickRange by remember { mutableStateOf(false) }
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val all by repo.transactions.collectAsStateWithLifecycle(emptyList())
    val range = remember(period, anchor, customStart, customEnd, settings.monthStartDay, settings.weekStartDay) {
        rangeOf(period, anchor, customStart to customEnd)
    }
    val inRange = remember(all, range, settings.statsExcluded) { all.filter { it.status == TxnStatus.CONFIRMED && it.inStats(settings.statsExcluded) }.inRange(range) }
    val totals = totalsOf(inRange, lk.fx)
    val byCat = inRange.filter { it.type == kind }.groupBy { lk.rootId(it.categoryId) }
        .map { (cid, list) -> cid to list.sumOf { lk.fx.convert(it.amount, it.currency) } }
        .sortedByDescending { it.second }
    val sum = byCat.sumOf { it.second }

    val topScroll = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // 叫回收起的標題列（換分頁、切換檢視時）

    LaunchedEffect(com.enwei.moneymanager.ui.components.Chrome.resetTick) { topScroll.state.heightOffset = 0f }
    // 切換「分類／分析」時把收起的頂列放回來：分析內容短、不能捲動時，否則就找不到切換期間的箭頭
    LaunchedEffect(view) { topScroll.state.heightOffset = 0f }
    Scaffold(modifier = Modifier.nestedScroll(topScroll.nestedScrollConnection), containerColor = MaterialTheme.colorScheme.background, topBar = {
        com.enwei.moneymanager.ui.ChromeScale { TopAppBar(
            scrollBehavior = topScroll,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, scrolledContainerColor = MaterialTheme.colorScheme.surface),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(x = (-12).dp).coachTarget("st.period")) {
                    if (period != Period.CUSTOM || view == 1) IconButton(onClick = { step(-1) }) { Icon(Icons.Filled.ChevronLeft, "上一期") }
                    val ym = Periods.monthOf(anchor, settings.monthStartDay)
                    val title = if (view == 1) when (reviewSpan) {
                        com.enwei.moneymanager.data.ReviewSpan.QUARTER -> "${ym.year}年Q${(ym.monthValue - 1) / 3 + 1}"
                        com.enwei.moneymanager.data.ReviewSpan.YEAR -> "${ym.year}年"
                        else -> "${ym.year}年${ym.monthValue}月"
                    } else range.title
                    Text(title, fontSize = if (period == Period.CUSTOM && view == 0) AppText.section else AppText.title, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { if (period == Period.CUSTOM) pickRange = true else anchor = LocalDate.now() })
                    if (period != Period.CUSTOM || view == 1) IconButton(onClick = { step(1) }) { Icon(Icons.Filled.ChevronRight, "下一期") }
                }
            },
            actions = {
                // 期間選單：兩個檢視共用同一個位置（分類＝週／月／年／自訂；分析＝每月／每季／每年）
                Box(Modifier.padding(end = 8.dp)) {
                    Row(
                        Modifier.coachTarget("st.kind").border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(8.dp)).clickable { menu = true }
                            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Text(if (view == 1) "每${reviewSpan.label}" else period.label, fontSize = AppText.body); Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(18.dp)) }
                    DropdownMenu(menu, { menu = false }) {
                        if (view == 1) com.enwei.moneymanager.data.ReviewSpan.entries.forEach { sp ->
                            DropdownMenuItem(text = { Text("每${sp.label}") }, onClick = { reviewSpan = sp; menu = false })
                        } else Period.entries.forEach { p ->
                            DropdownMenuItem(text = { Text(p.label) }, onClick = { period = p; menu = false; if (p == Period.CUSTOM) pickRange = true })
                        }
                    }
                }
            },
        ) }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Box(Modifier.background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 6.dp)) {
                com.enwei.moneymanager.ui.components.SubTabs(listOf("分類", "分析"), view) { view = it }
            }
            if (view == 1) {
                val ym = Periods.monthOf(anchor, settings.monthStartDay)
                ReviewContent(nav, reviewSpan to ym) { reviewSpan = it }
                return@Column
            }
            val tabs = listOf(TxnType.INCOME to totals.income, TxnType.EXPENSE to totals.expense)
            val idx = tabs.indexOfFirst { it.first == kind }
            val fitGroup1 = com.enwei.moneymanager.ui.components.rememberFitGroup()
            com.enwei.moneymanager.ui.components.CollapsingTop(Modifier.fillMaxSize(), header = {
            com.enwei.moneymanager.ui.ChromeScale { TabRow(
                selectedTabIndex = idx, containerColor = MaterialTheme.colorScheme.surface,
                indicator = { pos -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[idx]), height = 3.dp, color = MaterialTheme.colorScheme.primary) },
            ) {
                tabs.forEach { (t, v) ->
                    Tab(selected = kind == t, onClick = { kind = t }, text = { com.enwei.moneymanager.ui.components.FitText("${t.label}  ${Money.withSymbol(v, lk.base)}", fontSize = AppText.body, group = fitGroup1) },
                        selectedContentColor = MaterialTheme.colorScheme.onSurface, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } }
            }) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.background(MaterialTheme.colorScheme.surface).coachTarget("st.pie")) {
                        PieChart(byCat.map { (cid, v) -> (lk.category(cid)?.name ?: "未分類") to v }, "${kind.label}合計", Money.withSymbol(sum, lk.base))
                    }
                    com.enwei.moneymanager.ui.components.LineDivider()
                    Spacer(Modifier.height(10.dp))
                }
                if (byCat.isEmpty()) item { EmptyHint("這段期間沒有${kind.label}") }
                items(byCat.withIndex().toList(), key = { "c${it.value.first}" }) { (i, pair) ->
                    val (cid, v) = pair
                    val cat = lk.category(cid)
                    val pct = if (sum > 0) v * 100.0 / sum else 0.0
                    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                val p = if (period == Period.CUSTOM) Period.MONTH else period
                                nav.navigate("catstat/${cid ?: 0}/${kind.name}/${p.name}/${anchor.toEpochDay()}")
                            }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(color = chartPalette[i % chartPalette.size], shape = RoundedCornerShape(6.dp), modifier = Modifier.width(56.dp)) {
                                Text(String.format("%.0f%%", pct), color = onChartColor(chartPalette[i % chartPalette.size]), fontSize = AppText.secondary,
                                    modifier = Modifier.padding(vertical = 5.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(cat?.name ?: "未分類", Modifier.weight(1f), fontSize = AppText.body)
                            Text(Money.withSymbol(v, lk.base), fontSize = AppText.body)
                        }
                        com.enwei.moneymanager.ui.components.LineDivider()
                    }
                }
                // 近 6 個月比較：同一種收支每個月的總額，最右邊是本月（統計頁導覽說的「每月比較」）
                if (period == Period.MONTH) item {
                    val months = remember(all, anchor, kind, settings.monthStartDay, settings.statsExcluded) {
                        val cur = Periods.monthOf(anchor, settings.monthStartDay)
                        (5 downTo 0).map { back ->
                            val ym = cur.minusMonths(back.toLong())
                            val r = Periods.month(ym, settings.monthStartDay)
                            val total = all.asSequence().filter { it.type == kind && it.status == TxnStatus.CONFIRMED && it.inStats(settings.statsExcluded) }
                                .filter { val d = it.date.toLocalDate(); !d.isBefore(r.first) && !d.isAfter(r.second) }
                                .sumOf { lk.fx.convert(it.amount, it.currency) }
                            ym to total
                        }
                    }
                    if (months.any { it.second > 0 }) Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                        com.enwei.moneymanager.ui.components.LabeledBars(
                            "近 6 個月${kind.label}",
                            months.map { (ym, v) -> Triple("${ym.year}/${ym.monthValue}", v, Money.withSymbol(v, lk.base)) },
                            color = if (kind == TxnType.EXPENSE) com.enwei.moneymanager.ui.ExpenseColor else com.enwei.moneymanager.ui.IncomeColor,
                        )
                        val avg = months.dropLast(1).map { it.second }.filter { it > 0 }.let { l -> if (l.isEmpty()) 0L else l.sum() / l.size }
                        if (avg > 0) Text("前 5 個月平均 ${Money.withSymbol(avg, lk.base)}；本期 " +
                            "%+.0f%%".format((months.last().second - avg) * 100.0 / avg), fontSize = AppText.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
            }
        }
    }
    if (pickRange) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = customStart.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = customEnd.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickRange = false },
            confirmButton = {
                TextButton(onClick = {
                    val s0 = state.selectedStartDateMillis
                    val e0 = state.selectedEndDateMillis ?: s0
                    if (s0 != null && e0 != null) {
                        customStart = java.time.Instant.ofEpochMilli(s0).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        customEnd = java.time.Instant.ofEpochMilli(e0).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    }
                    pickRange = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { pickRange = false }) { Text("取消") } },
        ) { DateRangePicker(state, Modifier.height(480.dp)) }
    }
}

@Composable
fun PieChart(slices: List<Pair<String, Long>>, centerTitle: String? = null, centerValue: String? = null) {
    val total = slices.sumOf { it.second }
    val measurer = rememberTextMeasurer()
    // 圖上的字用設定的字型（主題的字型家族），不是系統預設
    val baseText = MaterialTheme.typography.bodyMedium
    val labelColor = MaterialTheme.colorScheme.onSurface
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val separator = MaterialTheme.colorScheme.surface
    // 動畫：每次資料改變都從 12 點鐘方向（0 度）順時針轉開到完整圓餅，轉完標籤再淡入
    val prog = remember(slices) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(slices) { prog.animateTo(1f, androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    val centerStyle = baseText.copy(fontSize = AppText.title, fontWeight = FontWeight.SemiBold, color = labelColor)
    val centerSub = baseText.copy(fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // 圓環：中間寫本期總額（最重要的數字），高度比原本的實心圓餅小
    Canvas(Modifier.fillMaxWidth().height(if (slices.count { total > 0 && it.second * 100f / total >= 3f } > 7) 360.dp else 320.dp).padding(vertical = 12.dp)) {
        val r = minOf(size.width * 0.27f, size.height * 0.42f)
        val c = Offset(size.width / 2, size.height / 2)
        val topLeft = Offset(c.x - r, c.y - r)
        if (total <= 0L) {
            drawArc(empty, 0f, 360f, true, topLeft, Size(2 * r, 2 * r))
            return@Canvas
        }
        val sweeps = slices.map { it.second.toFloat() / total * 360f }
        val limit = 360f * prog.value
        var start = -90f
        slices.indices.forEach { i ->
            val drawn = (limit - (start + 90f)).coerceIn(0f, sweeps[i])
            if (drawn > 0f) drawArc(chartPalette[i % chartPalette.size], start, drawn, true, topLeft, Size(2 * r, 2 * r))
            start += sweeps[i]
        }
        // 挖空中間成圓環，寫總額
        drawCircle(separator, r * 0.6f, c)
        if (centerValue != null) {
            val v = measurer.measure(centerValue, centerStyle)
            val t = centerTitle?.let { measurer.measure(it, centerSub) }
            val h = v.size.height + (t?.size?.height ?: 0)
            var y = c.y - h / 2f
            t?.let { drawText(it, topLeft = Offset(c.x - it.size.width / 2f, y)); y += it.size.height }
            drawText(v, topLeft = Offset(c.x - v.size.width / 2f, y))
        }
        val labelAlpha = ((prog.value - 0.75f) / 0.25f).coerceIn(0f, 1f)
        if (labelAlpha <= 0f) return@Canvas
        // 細分隔線
        start = -90f
        if (slices.size > 1) sweeps.forEach { sw ->
            val rad = Math.toRadians(start.toDouble())
            // 只畫在圓環上（從挖空的內緣到外緣），不穿過中間的合計文字
            drawLine(separator, Offset(c.x + (r * 0.6f * cos(rad)).toFloat(), c.y + (r * 0.6f * sin(rad)).toFloat()),
                Offset(c.x + (r * cos(rad)).toFloat(), c.y + (r * sin(rad)).toFloat()), strokeWidth = 2f)
            start += sw
        }

        // 標籤：兩行（名稱／百分比），沿著圓餅外圍的橢圓排列；同側依高度排序後上下推開，用平滑曲線連到扇形。
        class Label(val color: Color, val angle: Double, var layout: androidx.compose.ui.text.TextLayoutResult, val right: Boolean, var y: Float, val text: String, val pct: Float)
        val labels = mutableListOf<Label>()
        // 小於 3% 的扇形太窄，標籤一定擠在一起：合成一個「其他」標籤（扇形照畫）
        val bigCount = slices.count { it.second * 100f / total >= 3f }
        val oneLine = bigCount > 8
        val labelStyle = baseText.copy(fontSize = AppText.secondary, color = labelColor, lineHeight = 18.sp)
        fun measureLabel(text: String, pct: Float, single: Boolean) = measurer.measure(
            if (single) "${com.enwei.moneymanager.ui.tr(text)} ${"%.1f".format(pct)}%" else "${com.enwei.moneymanager.ui.tr(text)}\n${"%.1f".format(pct)}%",
            labelStyle,
        )
        fun add(text: String, pct: Float, mid: Double, color: Color) {
            labels += Label(color, mid, measureLabel(text, pct, oneLine), cos(mid) >= 0, (c.y + r * 1.22f * sin(mid)).toFloat(), text, pct)
        }
        start = -90f
        var smallStart = Float.NaN; var smallSweep = 0f; var smallPct = 0f; var smallColor = labelColor
        slices.forEachIndexed { i, (name, v) ->
            val pct = v * 100f / total
            if (pct >= 3f) add(name, pct, Math.toRadians((start + sweeps[i] / 2).toDouble()), chartPalette[i % chartPalette.size])
            else {
                if (smallStart.isNaN()) { smallStart = start; smallColor = chartPalette[i % chartPalette.size] }
                smallSweep += sweeps[i]; smallPct += pct
            }
            start += sweeps[i]
        }
        if (smallPct > 0f) add(if (slices.size - bigCount == 1) slices.last().first else "其他", smallPct,
            Math.toRadians((smallStart + smallSweep / 2).toDouble()), smallColor)
        val ring = r * 1.28f
        listOf(true, false).forEach { side ->
            val list = labels.filter { it.right == side }.sortedBy { it.y }
            // 同一側兩行標籤放不下：改成一行（名稱 百分比），避免上下推擠時疊在一起
            if (list.sumOf { it.layout.size.height + 4 } > size.height) list.forEach { it.layout = measureLabel(it.text, it.pct, true) }
            // 一行還是放不下：比例最小的不寫標籤（下方清單仍看得到），絕不重疊
            val shownSide = list.toMutableList()
            while (shownSide.size > 1 && shownSide.sumOf { it.layout.size.height + 4 } > size.height) {
                val smallest = shownSide.minBy { it.pct }; shownSide.remove(smallest); labels.remove(smallest)
            }
            var prevBottom = Float.NEGATIVE_INFINITY
            shownSide.forEach { l ->
                val h = l.layout.size.height.toFloat()
                l.y = maxOf(l.y, prevBottom + h / 2 + 4f)
                prevBottom = l.y + h / 2
            }
            var nextTop = size.height
            shownSide.asReversed().forEach { l ->
                val h = l.layout.size.height.toFloat()
                l.y = minOf(l.y, nextTop - h / 2 - 4f).coerceAtLeast(h / 2)
                nextTop = l.y - h / 2
            }
        }
        labels.forEach { l ->
            val dir = if (l.right) 1f else -1f
            val dy = l.y - c.y
            // 標籤水平位置：貼著外圍橢圓；在圓餅正上／下方時往外側留一點距離
            val dx = kotlin.math.sqrt(maxOf(0f, ring * ring - dy * dy)).coerceAtLeast(r * 0.35f)
            val anchor = Offset(c.x + dir * dx, l.y)
            val p1 = Offset(c.x + (r * 1.01f * cos(l.angle)).toFloat(), c.y + (r * 1.01f * sin(l.angle)).toFloat())
            val out = Offset(c.x + (r * 1.12f * cos(l.angle)).toFloat(), c.y + (r * 1.12f * sin(l.angle)).toFloat())
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(p1.x, p1.y)
                cubicTo(out.x, out.y, anchor.x - dir * 18f, anchor.y, anchor.x, anchor.y)
            }
            drawPath(path, l.color, alpha = labelAlpha, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            val w = l.layout.size.width.toFloat()
            val x = if (l.right) anchor.x + 8f else anchor.x - 8f - w
            drawText(l.layout, topLeft = Offset(x.coerceIn(0f, maxOf(0f, size.width - w)), l.y - l.layout.size.height / 2), alpha = labelAlpha)
        }
    }
}

/** 單一分類：近 6 期趨勢與本期明細。 */
@Composable
fun CategoryStatScreen(nav: NavController, categoryId: Long, kind: TxnType, period: Period, anchorDay: Long) {
    val lk = rememberLookups()
    val all by repo.transactions.collectAsStateWithLifecycle(emptyList())
    val cat = lk.category(categoryId.takeIf { it > 0 })
    val anchor = LocalDate.ofEpochDay(anchorDay)
    val mine = remember(all, categoryId, kind, lk) {
        all.filter { it.status == TxnStatus.CONFIRMED && it.type == kind && (lk.rootId(it.categoryId) ?: 0L) == categoryId }
    }
    val ranges = (5 downTo 0).map { rangeOf(period, shift(period, anchor, -it.toLong())) }
    val values = ranges.map { r -> mine.inRange(r).sumOf { lk.fx.convert(it.amount, it.currency) } }
    val current = mine.inRange(ranges.last())
    val color = if (kind == TxnType.INCOME) IncomeColor else ExpenseColor

    Scaffold(topBar = { BackTopBar(cat?.name ?: "未分類", { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            item {
                Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                    Text(ranges.last().title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money.format(values.last(), lk.base), style = MaterialTheme.typography.headlineSmall, color = color)
                    Spacer(Modifier.height(12.dp))
                    val maxV = values.maxOrNull()?.coerceAtLeast(1) ?: 1
                    Row(Modifier.fillMaxWidth().heightIn(min = 150.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                        values.forEachIndexed { i, v ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (v > 0) Money.format(v) else "", fontSize = AppText.badge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Canvas(Modifier.width(22.dp).height(100.dp)) {
                                    val h = size.height * v / maxV
                                    drawRect(if (i == values.lastIndex) color else color.copy(alpha = 0.4f), Offset(0f, size.height - h), Size(size.width, h))
                                }
                                Text(
                                    when (period) {
                                        Period.MONTH, Period.CUSTOM -> "${ranges[i].start.monthValue}月"
                                        Period.YEAR -> "${ranges[i].start.year}"
                                        Period.WEEK -> "${ranges[i].start.monthValue}/${ranges[i].start.dayOfMonth}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    Text("平均 ${Money.format(values.average().toLong(), lk.base)}", style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
            if (current.isEmpty()) item { EmptyHint("本期沒有交易") }
            current.groupBy { it.date.toLocalDate() }.toSortedMap(Comparator.reverseOrder()).forEach { (d, txns) ->
                item(key = "d$d") { DayBlock(d, txns, lk, nav) }
            }
        }
    }
}
