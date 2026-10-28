package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.data.TxnStatus
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.data.toLocalDate
import com.enwei.moneymanager.ui.ExpenseColor
import com.enwei.moneymanager.ui.IncomeColor
import com.enwei.moneymanager.ui.components.EmptyHint
import com.enwei.moneymanager.ui.components.MonthSwitcher
import com.enwei.moneymanager.ui.components.SectionTitle
import java.time.YearMonth

private val palette = listOf(
    Color(0xFF2A9D8F), Color(0xFFE76F51), Color(0xFFF4A261), Color(0xFF264653), Color(0xFFE9C46A),
    Color(0xFF8E7DBE), Color(0xFF5FA8D3), Color(0xFFD62828), Color(0xFF6A994E), Color(0xFFB5838D),
)

@Composable
fun ReportScreen() {
    val lk = rememberLookups()
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var kind by rememberSaveable { mutableStateOf(TxnType.EXPENSE) }
    val all by repo.transactions.collectAsStateWithLifecycle(emptyList())
    val confirmed = remember(all) { all.filter { it.status == TxnStatus.CONFIRMED && it.type != TxnType.TRANSFER } }

    val inMonth = confirmed.filter { it.type == kind && YearMonth.from(it.date.toLocalDate()) == month }
    val byCat = inMonth.groupBy { it.categoryId }
        .map { (cid, list) -> cid to list.sumOf { lk.fx.convert(it.amount, it.currency) } }
        .sortedByDescending { it.second }
    val total = byCat.sumOf { it.second }

    val months = (5 downTo 0).map { month.minusMonths(it.toLong()) }
    val trend = months.map { m ->
        val list = confirmed.filter { YearMonth.from(it.date.toLocalDate()) == m }
        repo.monthTotals(list, lk.fx)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("報表") }) }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item { MonthSwitcher(month, { month = it }) }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    listOf(TxnType.EXPENSE, TxnType.INCOME).forEachIndexed { i, t ->
                        SegmentedButton(kind == t, { kind = t }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(t.label) }
                    }
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    val track = MaterialTheme.colorScheme.surfaceVariant
                    Canvas(Modifier.size(200.dp)) {
                        val stroke = 36.dp.toPx()
                        val d = size.minDimension - stroke
                        val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
                        if (total == 0L) {
                            drawArc(track, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke))
                        } else {
                            var start = -90f
                            byCat.forEachIndexed { i, (_, v) ->
                                val sweep = v.toFloat() / total * 360f
                                drawArc(palette[i % palette.size], start, sweep, false, topLeft, Size(d, d), style = Stroke(stroke))
                                start += sweep
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(kind.label + "合計", style = MaterialTheme.typography.labelMedium)
                        Text(Money.format(total, lk.base), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            if (byCat.isEmpty()) item { EmptyHint("本月沒有${kind.label}") }
            items(byCat.withIndex().toList(), key = { "c" + it.value.first }) { (i, pair) ->
                val (cid, v) = pair
                val cat = lk.category(cid)
                val pct = if (total > 0) v.toFloat() / total else 0f
                ListItem(
                    leadingContent = {
                        Surface(shape = CircleShape, color = palette[i % palette.size], modifier = Modifier.size(14.dp)) {}
                    },
                    headlineContent = { Text("${cat?.icon ?: "❔"} ${cat?.name ?: "未分類"}") },
                    supportingContent = { LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth(), color = palette[i % palette.size]) },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Money.format(v, lk.base), style = MaterialTheme.typography.titleSmall)
                            Text(String.format("%.1f%%", pct * 100), style = MaterialTheme.typography.labelSmall)
                        }
                    },
                )
            }
            item { SectionTitle("近 6 個月收支") }
            item {
                val maxV = trend.maxOfOrNull { maxOf(it.first, it.second) }?.coerceAtLeast(1) ?: 1
                Row(
                    Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom,
                ) {
                    months.forEachIndexed { i, m ->
                        val (inc, exp) = trend[i]
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.height(140.dp)) {
                                Bar(inc.toFloat() / maxV, IncomeColor)
                                Spacer(Modifier.width(3.dp))
                                Bar(exp.toFloat() / maxV, ExpenseColor)
                            }
                            Text("${m.monthValue}月", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    val avgExp = trend.map { it.second }.average().toLong()
                    val avgInc = trend.map { it.first }.average().toLong()
                    Text("月均收入 ${Money.format(avgInc, lk.base)}", color = IncomeColor)
                    Text("月均支出 ${Money.format(avgExp, lk.base)}", color = ExpenseColor)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Canvas(Modifier.width(12.dp).height(140.dp)) {
        val h = size.height * fraction.coerceIn(0f, 1f)
        drawRect(color, Offset(0f, size.height - h), Size(size.width, h))
    }
}
