package com.enwei.moneymanager.ui.screens

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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

val chartPalette = listOf(
    Color(0xFFF0605A), Color(0xFFF59E42), Color(0xFFF5C842), Color(0xFF8BC34A), Color(0xFF26A69A),
    Color(0xFF3C82E6), Color(0xFF7E57C2), Color(0xFFEC6FA0), Color(0xFF8D6E63), Color(0xFF90A4AE),
)

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
    val lk = rememberLookups()
    var period by rememberSaveable { mutableStateOf(Period.MONTH) }
    var anchor by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var kind by rememberSaveable { mutableStateOf(TxnType.EXPENSE) }
    var menu by remember { mutableStateOf(false) }
    var customStart by rememberSaveable { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var customEnd by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var pickRange by remember { mutableStateOf(false) }
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val all by repo.transactions.collectAsStateWithLifecycle(emptyList())
    val range = remember(period, anchor, customStart, customEnd, settings.monthStartDay, settings.weekStartDay) {
        rangeOf(period, anchor, customStart to customEnd)
    }
    val inRange = remember(all, range) { all.filter { it.status == TxnStatus.CONFIRMED }.inRange(range) }
    val totals = totalsOf(inRange, lk.fx)
    val byCat = inRange.filter { it.type == kind }.groupBy { lk.rootId(it.categoryId) }
        .map { (cid, list) -> cid to list.sumOf { lk.fx.convert(it.amount, it.currency) } }
        .sortedByDescending { it.second }
    val sum = byCat.sumOf { it.second }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(x = (-12).dp)) {
                    if (period != Period.CUSTOM) IconButton(onClick = { anchor = shift(period, anchor, -1) }) { Icon(Icons.Filled.ChevronLeft, "上一期") }
                    Text(range.title, fontSize = if (period == Period.CUSTOM) 15.sp else 18.sp,
                        modifier = Modifier.clickable { if (period == Period.CUSTOM) pickRange = true else anchor = LocalDate.now() })
                    if (period != Period.CUSTOM) IconButton(onClick = { anchor = shift(period, anchor, 1) }) { Icon(Icons.Filled.ChevronRight, "下一期") }
                }
            },
            actions = {
                Box(Modifier.padding(end = 8.dp)) {
                    Row(
                        Modifier.border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(8.dp)).clickable { menu = true }
                            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Text(period.label, fontSize = 15.sp); Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(18.dp)) }
                    DropdownMenu(menu, { menu = false }) {
                        Period.entries.forEach { p ->
                            DropdownMenuItem(text = { Text(p.label) }, onClick = { period = p; menu = false; if (p == Period.CUSTOM) pickRange = true })
                        }
                    }
                }
            },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            val tabs = listOf(TxnType.INCOME to totals.income, TxnType.EXPENSE to totals.expense)
            val idx = tabs.indexOfFirst { it.first == kind }
            TabRow(
                selectedTabIndex = idx, containerColor = MaterialTheme.colorScheme.surface,
                indicator = { pos -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[idx]), height = 3.dp, color = MaterialTheme.colorScheme.primary) },
            ) {
                tabs.forEach { (t, v) ->
                    Tab(selected = kind == t, onClick = { kind = t }, text = { Text("${t.label}  ${Money.withSymbol(v, lk.base)}", fontSize = 15.sp, maxLines = 1) },
                        selectedContentColor = MaterialTheme.colorScheme.onSurface, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                        PieChart(byCat.map { (cid, v) -> (lk.category(cid)?.name ?: "未分類") to v })
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                                Text(String.format("%.0f%%", pct), color = Color.White, fontSize = 13.sp,
                                    modifier = Modifier.padding(vertical = 5.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(cat?.name ?: "未分類", Modifier.weight(1f), fontSize = 17.sp)
                            Text(Money.withSymbol(v, lk.base), fontSize = 17.sp)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
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
fun PieChart(slices: List<Pair<String, Long>>) {
    val total = slices.sumOf { it.second }
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val separator = MaterialTheme.colorScheme.surface
    Canvas(Modifier.fillMaxWidth().height(340.dp).padding(vertical = 16.dp)) {
        val r = minOf(size.width, size.height) / 2 * 0.66f
        val c = Offset(size.width / 2, size.height / 2)
        val topLeft = Offset(c.x - r, c.y - r)
        if (total <= 0L) {
            drawArc(empty, 0f, 360f, true, topLeft, Size(2 * r, 2 * r))
            return@Canvas
        }
        var start = -90f
        val sweeps = slices.map { it.second.toFloat() / total * 360f }
        slices.forEachIndexed { i, _ ->
            drawArc(chartPalette[i % chartPalette.size], start, sweeps[i], true, topLeft, Size(2 * r, 2 * r))
            start += sweeps[i]
        }
        // 白色分隔線
        start = -90f
        if (slices.size > 1) sweeps.forEach { sw ->
            val rad = Math.toRadians(start.toDouble())
            drawLine(separator, c, Offset(c.x + (r * cos(rad)).toFloat(), c.y + (r * sin(rad)).toFloat()), strokeWidth = 3f)
            start += sw
        }
        // 標籤與引線：同一側的標籤依高度排序後上下推開，避免小比例的標籤重疊
        data class Label(val color: Color, val p1: Offset, val p2: Offset, val right: Boolean, val layout: androidx.compose.ui.text.TextLayoutResult, var y: Float)
        val labels = mutableListOf<Label>()
        start = -90f
        val twoLine = slices.count { it.second * 100f / total >= 0.5f } <= 10
        slices.forEachIndexed { i, (name, v) ->
            val pct = v * 100f / total
            val mid = Math.toRadians((start + sweeps[i] / 2).toDouble())
            if (pct >= 0.5f) {
                val p1 = Offset(c.x + (r * cos(mid)).toFloat(), c.y + (r * sin(mid)).toFloat())
                val p2 = Offset(c.x + (r * 1.12f * cos(mid)).toFloat(), c.y + (r * 1.12f * sin(mid)).toFloat())
                val text = if (twoLine) "$name\n${"%.1f".format(pct)} %" else "$name ${"%.1f".format(pct)}%"
                val layout = measurer.measure(text, TextStyle(fontSize = 12.sp, color = labelColor, lineHeight = 14.sp))
                labels += Label(chartPalette[i % chartPalette.size], p1, p2, cos(mid) >= 0, layout, p2.y)
            }
            start += sweeps[i]
        }
        listOf(true, false).forEach { side ->
            val list = labels.filter { it.right == side }.sortedBy { it.p2.y }
            // 由上往下：與上一個標籤至少隔一個標籤高度
            var prevBottom = Float.NEGATIVE_INFINITY
            list.forEach { l ->
                val h = l.layout.size.height.toFloat()
                l.y = maxOf(l.p2.y, prevBottom + h / 2 + 2f)
                prevBottom = l.y + h / 2
            }
            // 超出底部時由下往上推回
            var nextTop = size.height
            list.asReversed().forEach { l ->
                val h = l.layout.size.height.toFloat()
                l.y = minOf(l.y, nextTop - h / 2 - 2f).coerceAtLeast(h / 2)
                nextTop = l.y - h / 2
            }
        }
        labels.forEach { l ->
            val elbow = Offset(l.p2.x + if (l.right) 14f else -14f, l.y)
            val end = Offset(if (l.right) maxOf(elbow.x, c.x + r * 1.18f) else minOf(elbow.x, c.x - r * 1.18f), l.y)
            drawLine(l.color, l.p1, l.p2, strokeWidth = 2.5f)
            drawLine(l.color, l.p2, elbow, strokeWidth = 2.5f)
            drawLine(l.color, elbow, end, strokeWidth = 2.5f)
            val w = l.layout.size.width.toFloat()
            val x = if (l.right) end.x + 6f else end.x - 6f - w
            drawText(l.layout, topLeft = Offset(x.coerceIn(0f, size.width - w), l.y - l.layout.size.height / 2))
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
                    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                        values.forEachIndexed { i, v ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (v > 0) Money.format(v) else "", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
