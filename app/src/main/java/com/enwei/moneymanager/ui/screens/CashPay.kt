package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enwei.moneymanager.core.Cash
import com.enwei.moneymanager.ui.components.NumberField
import com.enwei.moneymanager.ui.components.Text

/**
 * 用紙鈔硬幣記現金：支出時選付出的鈔票硬幣、自動算找零（找回的也可以自己改）；
 * 收入時選收到的、需要時再選找出去的。[have] 是皮夾裡目前各面額的數量。
 * 完成時回傳實際金額（元）、從皮夾拿出去的、放進皮夾的。
 */
@Composable
fun CashPayDialog(
    have: Map<Int, Int>,
    priceYuan: Long?,
    income: Boolean,
    onDone: (amountYuan: Long, out: Map<Int, Int>, inn: Map<Int, Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    var price by remember { mutableStateOf(priceYuan?.toString().orEmpty()) }
    // 支出：main = 付出的、back = 找回的；收入：main = 收到的、back = 找出去的
    var main by remember { mutableStateOf(emptyMap<Int, Int>()) }
    var back by remember { mutableStateOf<Map<Int, Int>?>(null) }
    val p = price.replace(",", "").toDoubleOrNull()?.toLong()
    val mainTotal = Cash.total(main)
    val due = if (p != null) (mainTotal - p).coerceAtLeast(0) else 0
    // 找零沒被手動改過時，自動用張數最少的組合
    val backNow = back ?: Cash.change(due)
    val amount = mainTotal - Cash.total(backNow)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (income) "收到現金" else "用紙鈔硬幣付款") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField(price, { price = it; back = null }, if (income) "應收金額（可不填）" else "價格（可不填，依付出與找零計算）", suffix = "元")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (income) "收到" else "付出", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (!income && p != null) TextButton(onClick = {
                        Cash.suggestPay(p, have)?.let { main = it; back = null }
                    }) { Text("從皮夾自動挑") }
                }
                Cash.DENOMS.forEach { d ->
                    DenomStepper(d, main[d] ?: 0, if (income) null else have[d] ?: 0) { n -> main = main + (d to n); back = null }
                }
                HorizontalDivider()
                Text(
                    (if (income) "收到 $mainTotal 元" else "付出 $mainTotal 元") + if (p != null && mainTotal >= p) "，應找 $due 元" else if (p != null) "，還差 ${p - mainTotal} 元" else "",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.primary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (income) "找出去的" else "找回的", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (back != null) TextButton(onClick = { back = null }) { Text("自動") }
                }
                Column(Modifier.heightIn(max = 400.dp)) {
                    Cash.DENOMS.filter { it != 2000 }.forEach { d ->
                        DenomStepper(d, backNow[d] ?: 0, if (income) have[d] ?: 0 else null) { n -> back = backNow + (d to n) }
                    }
                }
                Text(
                    "實際${if (income) "收入" else "花費"} $amount 元" + if (p != null && amount != p && mainTotal >= p) "（和價格差 ${amount - p} 元，請確認找零）" else "",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = if (p != null && amount != p) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                if (have.isEmpty()) Text("還沒盤點皮夾：到帳戶的「點鈔」先輸入各面額數量，之後付款、找零都會自動增減。",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = amount > 0, onClick = {
                val keep = { m: Map<Int, Int> -> m.filterValues { it > 0 } }
                if (income) onDone(amount, keep(backNow), keep(main)) else onDone(amount, keep(main), keep(backNow))
            }) { Text("完成") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 一個面額的數量增減；[have] 不為 null 時顯示皮夾裡有幾張並標出超過的。 */
@Composable
private fun DenomStepper(d: Int, n: Int, have: Int?, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$d 元" + if (Cash.isBill(d)) "" else "（硬幣）", fontSize = 14.sp, modifier = Modifier.width(96.dp))
        IconButton(enabled = n > 0, onClick = { onChange(n - 1) }) { Text("－", fontSize = 20.sp) }
        Text("$n", fontSize = 17.sp, modifier = Modifier.width(32.dp))
        IconButton(onClick = { onChange(n + 1) }) { Text("＋", fontSize = 20.sp) }
        have?.let {
            Text("有 $it", fontSize = 11.sp, color = if (n > it) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** 皮夾增減後的數量（不會小於 0）。 */
fun cashAfter(have: Map<Int, Int>, out: Map<Int, Int>, inn: Map<Int, Int>): Map<Int, Int> =
    Cash.apply(have, out, inn).mapValues { maxOf(0, it.value) }.filterValues { it > 0 }
