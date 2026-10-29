package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enwei.moneymanager.core.Cash
import com.enwei.moneymanager.ui.components.Text

/** 記帳時可以選的面額（2000、200 元少見，不列） */
val CASH_INPUT = listOf(1000, 500, 100, 50, 10, 5, 1)

/** 鈔票與硬幣的顏色（接近實際新台幣的色系） */
private fun cashColor(d: Int): Color = when (d) {
    2000 -> Color(0xFF7E57C2)
    1000 -> Color(0xFF4F7CAC)
    500 -> Color(0xFFB0654A)
    200 -> Color(0xFF5E9E6E)
    100 -> Color(0xFFD06A6A)
    50 -> Color(0xFFD4A537)
    10, 5 -> Color(0xFFB8BCC2)
    else -> Color(0xFFC08A5B)
}

/** 一張鈔票或一枚硬幣的圖示（鈔票是長方形、硬幣是圓形），[scale] 1 為一般大小。 */
@Composable
fun CashIcon(d: Int, scale: Float = 1f) {
    val c = cashColor(d)
    if (Cash.isBill(d)) {
        Box(
            Modifier.size(width = (46 * scale).dp, height = (26 * scale).dp)
                .background(c.copy(alpha = 0.18f), RoundedCornerShape((4 * scale).dp))
                .border(1.2.dp, c, RoundedCornerShape((4 * scale).dp)),
            contentAlignment = Alignment.Center,
        ) { Text("$d", fontSize = (11 * scale).sp, fontWeight = FontWeight.Bold, color = c, maxLines = 1) }
    } else {
        val sz: Dp = when (d) { 50 -> 30; 10 -> 27; 5 -> 24; else -> 21 }.let { (it * scale).dp }
        Box(
            Modifier.size(sz).background(c.copy(alpha = 0.25f), CircleShape).border(1.2.dp, c, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("$d", fontSize = (10 * scale).sp, fontWeight = FontWeight.Bold, color = if (d == 50) Color(0xFF8A6A10) else Color(0xFF55595E), maxLines = 1) }
    }
}

/** 一組鈔票硬幣的小圖示列，例如「[100]×4 (10)×3」；空的顯示 [empty]。 */
@Composable
fun CashIcons(m: Map<Int, Int>, empty: String = "不用找") {
    val list = Cash.DENOMS.filter { (m[it] ?: 0) > 0 }
    if (list.isEmpty()) { Text(empty, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        list.forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CashIcon(d, 0.8f)
                if ((m[d] ?: 0) > 1) Text("×${m[d]}", fontSize = 12.sp, modifier = Modifier.padding(start = 2.dp))
            }
        }
    }
}

/**
 * 現金帳戶的金額輸入：不用數字鍵盤，直接增減各面額的數量組出金額（點圖示加一、點「－」減一）。
 * [have] 是皮夾裡目前的數量（顯示在每個面額下方）。
 */
@Composable
fun CashAmountInput(counts: Map<Int, Int>, have: Map<Int, Int>, color: Color, onChange: (Map<Int, Int>) -> Unit) {
    val total = Cash.total(counts)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("金額", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
            Text("$ ${"%,d".format(total)}", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.weight(1f))
            if (total > 0) Text("清除", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onChange(emptyMap()) }.padding(8.dp))
        }
        listOf(CASH_INPUT.filter { Cash.isBill(it) }, CASH_INPUT.filter { !Cash.isBill(it) }).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { d ->
                    DenomTile(d, counts[d] ?: 0, have[d], Modifier.weight(1f)) { n -> onChange((counts + (d to n)).filterValues { it > 0 }) }
                }
                // 紙鈔一列 3 個、硬幣一列 4 個：補空位讓兩列寬度對齊
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DenomTile(d: Int, n: Int, have: Int?, modifier: Modifier, onChange: (Int) -> Unit) {
    val sel = n > 0
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (sel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(92.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(46.dp).clickable { onChange(n + 1) }, contentAlignment = Alignment.Center) { CashIcon(d) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clickable(enabled = n > 0) { onChange(n - 1) }, contentAlignment = Alignment.Center) {
                    Text("－", fontSize = 16.sp, color = if (n > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant)
                }
                Text("$n", fontSize = 16.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.width(24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Box(Modifier.size(28.dp).clickable { onChange(n + 1) }, contentAlignment = Alignment.Center) { Text("＋", fontSize = 16.sp) }
            }
            if (have != null) Text("有 $have", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 付款（收款）方式推薦：每種方式用圖示顯示「給出去／收到」與「找回／找出去」，點一下選用。
 * 皮夾付不出來的方式會標示。
 */
@Composable
fun CashOptions(options: List<Cash.PayOption>, selected: Int, income: Boolean, have: Map<Int, Int>, onSelect: (Int) -> Unit) {
    if (options.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (income) "怎麼收的" else "怎麼付的",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        options.forEachIndexed { i, o ->
            val sel = i == selected
            // 支出：給出去的要從皮夾拿；收入：找出去的要從皮夾拿
            val fromWallet = if (income) o.back else o.give
            val short = have.isNotEmpty() && fromWallet.any { (d, n) -> (have[d] ?: 0) < n }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (sel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(if (sel) 1.5.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().clickable { onSelect(i) },
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (income) "收到" else "付出", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
                        Box(Modifier.weight(1f)) { CashIcons(o.give) }
                        Text("${"%,d".format(Cash.total(o.give))}", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (income) "找出" else "找回", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
                        Box(Modifier.weight(1f)) { CashIcons(o.back) }
                        if (o.back.isNotEmpty()) Text("${"%,d".format(Cash.total(o.back))}", fontSize = 13.sp)
                    }
                    if (short) Text("皮夾裡的數量不夠", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** 皮夾增減後的數量（不會小於 0）。 */
fun cashAfter(have: Map<Int, Int>, out: Map<Int, Int>, inn: Map<Int, Int>): Map<Int, Int> =
    Cash.apply(have, out, inn).mapValues { maxOf(0, it.value) }.filterValues { it > 0 }
