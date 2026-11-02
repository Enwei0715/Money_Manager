package com.enwei.moneymanager.ui.components

import com.enwei.moneymanager.ui.AppText

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.ui.ExpenseColor
import com.enwei.moneymanager.ui.IncomeColor

/** 預算顏色：80% 以下綠、80–100% 橘、超出紅。 */
fun budgetColor(ratio: Float): Color = when {
    ratio > 1f -> ExpenseColor
    ratio >= 0.8f -> Color(0xFFF5A623)
    else -> IncomeColor
}

/**
 * 統一的預算列（檢討頁、每日頁上方、新增交易都用這個）：
 * 第一行「名稱 ……… 百分比」，中間一條細進度條，第二行「已用 / 上限 ……… 剩多少（每天約多少）」。
 * 金額整數時不顯示小數，避免一行塞不下而換行。
 * [adding]：新增交易時這筆的金額，進度條上用淡色顯示加上去之後的位置。
 */
@Composable
fun BudgetMeter(
    name: String, used: Long, cap: Long,
    modifier: Modifier = Modifier,
    adding: Long = 0,
    daysLeft: Long? = null,
    compact: Boolean = false,
) {
    val after = used + adding.coerceAtLeast(0)
    val ratio = if (cap > 0) after.toFloat() / cap else 0f
    val col = budgetColor(ratio)
    val left = cap - after
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val fs = if (compact) 12.sp else 13.sp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontSize = fs, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${Money.format(after)} / ${Money.format(cap)}", fontSize = fs, color = muted, maxLines = 1)
            Text("  ${(ratio * 100).toInt()}%", fontSize = fs, fontWeight = FontWeight.SemiBold, color = col, maxLines = 1)
        }
        val h = if (compact) 4.dp else 6.dp
        Box(Modifier.fillMaxWidth().height(h).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(h / 2))) {
            if (adding > 0) Box(Modifier.fillMaxWidth(ratio.coerceIn(0f, 1f)).height(h).background(col.copy(alpha = 0.35f), RoundedCornerShape(h / 2)))
            val usedRatio = if (cap > 0) (used.toFloat() / cap).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth(usedRatio).height(h).background(col, RoundedCornerShape(h / 2)))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (adding > 0) "記這筆後" else if (daysLeft != null) "本月還有 $daysLeft 天" else "",
                fontSize = AppText.label, color = muted, modifier = Modifier.weight(1f), maxLines = 1)
            Text(
                if (left >= 0) "剩 ${Money.format(left)}" + (daysLeft?.takeIf { it > 0 }?.let { " · 每天約 ${Money.format(left / it)}" } ?: "")
                else "超出 ${Money.format(-left)}",
                fontSize = AppText.label, color = if (left < 0) ExpenseColor else muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 記帳畫面用的一行式預算：「飲食 7% ▬▬▬▬ 剩 6,732」，緊貼在分類下方、和欄位內容對齊，
 * 不把分類和帳戶隔開。[adding] 是這筆的金額：進度條淡色顯示記這筆之後的位置，右邊顯示記後剩多少。
 */
@Composable
fun BudgetInline(name: String, used: Long, cap: Long, adding: Long, modifier: Modifier = Modifier) {
    val after = used + adding.coerceAtLeast(0)
    val ratio = if (cap > 0) after.toFloat() / cap else 0f
    val col = budgetColor(ratio)
    val left = cap - after
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontSize = AppText.caption, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.9f, fill = false))
        Text(" ${(ratio * 100).toInt()}%", fontSize = AppText.caption, color = col, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Box(Modifier.padding(horizontal = 8.dp).weight(1f).height(4.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))) {
            if (adding > 0) Box(Modifier.fillMaxWidth(ratio.coerceIn(0f, 1f)).height(4.dp).background(col.copy(alpha = 0.35f), RoundedCornerShape(2.dp)))
            Box(Modifier.fillMaxWidth(if (cap > 0) (used.toFloat() / cap).coerceIn(0f, 1f) else 0f).height(4.dp).background(col, RoundedCornerShape(2.dp)))
        }
        Text(
            (if (adding > 0) "記後" else "") + if (left >= 0) "剩 ${Money.format(left)}" else "超出 ${Money.format(-left)}",
            fontSize = AppText.caption, color = if (left < 0) ExpenseColor else muted, maxLines = 1,
        )
    }
}

/**
 * 清單用的一行式分類預算：「飲食 ▬▬▬▬ 516 / 7,248」，很多分類時每個只佔一行。
 * 超過 80% 橘色、超出紅色；[onClick] 不為 null 時可點（預算設定頁點一下修改）。
 */
@Composable
fun BudgetRowCompact(name: String, used: Long, cap: Long, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val ratio = if (cap > 0) used.toFloat() / cap else 0f
    val col = budgetColor(ratio)
    Row(
        (if (onClick != null) modifier.clickable(onClick = onClick) else modifier).padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, fontSize = AppText.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.32f))
        Box(Modifier.weight(0.4f).height(5.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(ratio.coerceIn(0f, 1f)).height(5.dp).background(col, RoundedCornerShape(3.dp)))
        }
        Text(
            "${Money.format(used)} / ${Money.format(cap)}", fontSize = AppText.caption, maxLines = 1,
            color = if (ratio > 1f) ExpenseColor else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(0.38f),
        )
    }
}
