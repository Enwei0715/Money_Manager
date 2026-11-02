package com.enwei.moneymanager.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enwei.moneymanager.net.TwData
import kotlinx.coroutines.delay

/** 股票搜尋：台股打代號或名稱（例如「台積」「2330」），美股打代號或英文名稱（例如「AAPL」「apple」），點結果回傳代號。 */
@Composable
fun StockSearch(onPick: (TwData.StockInfo) -> Unit, maxHeight: Dp = 360.dp, autoFocusHint: String = "股票名稱或代號（台股、美股）") {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<TwData.StockInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(q) {
        if (q.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(200)
        loading = true
        val tw = runCatching { TwData.search(ctx, q) }.getOrDefault(emptyList())
        // 有英文字母時也查美股（台股在前）
        val us = if (q.any { it.isLetter() && it.code < 128 }) TwData.searchUs(q) else emptyList()
        results = (tw + us).distinctBy { it.code }
        failed = results.isEmpty() && TwData.nameOf("2330") == null
        loading = false
    }
    Column {
        OutlinedTextField(q, { q = it.take(20) }, label = { Text(autoFocusHint) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (loading) Text("搜尋中…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        if (!loading && q.isNotBlank() && results.isEmpty()) {
            Text(if (failed) "讀不到股票清單（需要網路）；也可以直接輸入完整代號" else "找不到符合的股票", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            // 直接輸入代號也能用
            if (q.trim().uppercase().matches(Regex("[0-9]{4,6}[A-Z]?|[A-Z][A-Z.\\-]{0,5}"))) Text("使用代號 ${q.trim().uppercase()} ›", fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onPick(TwData.StockInfo(q.trim().uppercase(), "")) }.padding(vertical = 10.dp))
        }
        LazyColumn(Modifier.heightIn(max = maxHeight)) {
            items(results, key = { it.code }) { s ->
                Row(Modifier.fillMaxWidth().clickable { onPick(s) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.code, fontSize = 14.sp, modifier = Modifier.padding(end = 12.dp))
                    Text(s.name, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (s.industry.isNotBlank()) Text(s.industry, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
