package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.Text
import com.enwei.moneymanager.ui.components.ReorderableColumn

data class ToolItem(val id: String, val label: String, val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

/**
 * 工具頁可以放的工具。新增工具只要在這裡加一項（id、名稱、要開的畫面、圖示）；
 * 寬格顯示的摘要寫在 MoreScreens 的 toolSummary。
 */
object ToolCatalog {
    private val L = com.enwei.moneymanager.ui.components.LineIcons
    val items = listOf(
        ToolItem("pending", "待確認", "pending", L.ToolPending),
        ToolItem("recurring", "週期交易", "recurring", L.ToolRecurring),
        ToolItem("fx", "匯率", "fx", L.ToolFx),
        ToolItem("backup", "備份", "backup", L.ToolBackup),
        ToolItem("ai-hub", "AI 中心", "ai-hub", L.ToolAi),
        ToolItem("advisor", "股票助理", "advisor", L.ToolCandles),
        ToolItem("review", "收支分析", "review", L.ToolBars),
        ToolItem("budget", "預算", "budget", L.ToolGauge),
        ToolItem("totalstats", "資產走勢", "totalstats", L.ToolTrend),
        ToolItem("notif", "通知記帳", "notif", L.ToolBell),
        ToolItem("stockfees", "手續費與月退", "stockfees", L.ToolReceipt),
        ToolItem("settings", "App 更新", "settings", L.ToolUpdate),
        ToolItem("merchants", "店家位置", "merchants", L.IcStore),
        ToolItem("spendmap", "支出地圖", "spendmap", L.IcMap),
        ToolItem("debts", "往來", "debts", L.IcPeople),
        ToolItem("stocksearch", "找股票", "stocksearch", L.IcSearch),
        ToolItem("accounts-cfg", "帳戶設定", "accountsettings", L.IcWallet),
    )

    /** 工具頁目前顯示的工具（依使用者排的順序）。不在順序裡的工具放在「可加入的工具」。 */
    fun shown(order: List<String>, hidden: Set<String>) = order.mapNotNull { id -> items.find { it.id == id } }.filter { it.id !in hidden }

    fun available(order: List<String>, hidden: Set<String>): List<ToolItem> {
        val shownIds = shown(order, hidden).map { it.id }.toSet()
        return items.filter { it.id !in shownIds }
    }
}

@Composable
fun ToolSettingsScreen(nav: NavController) {
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val off = settings.offTools()
    val visible = ToolCatalog.shown(settings.toolOrder, settings.hiddenTools).filter { it.id !in off }
    val hidden = ToolCatalog.available(settings.toolOrder, settings.hiddenTools).filter { it.id !in off }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackTopBar("工具首頁與順序", { nav.popBackStack() }) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            Text("首頁顯示與排序", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            ReorderableColumn(visible, { it.id }, { reordered ->
                repo.settings.update { current ->
                    val hiddenOrder = current.toolOrder.filter { it in current.hiddenTools || it in off }
                    current.copy(toolOrder = (reordered.map { it.id } + hiddenOrder).distinct())
                }
            }) { item, handle ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(item.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(item.label, Modifier.weight(1f).padding(start = 12.dp))
                    TextButton(onClick = {
                        repo.settings.update { it.copy(hiddenTools = it.hiddenTools + item.id) }
                    }) {
                        Icon(Icons.Rounded.Remove, null, modifier = Modifier.size(18.dp))
                        Text("隱藏")
                    }
                    Box(handle.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.DragHandle, "拖曳排序", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                com.enwei.moneymanager.ui.components.LineDivider()
            }

            if (hidden.isNotEmpty()) {
                Text("未顯示的工具", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp))
                hidden.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(item.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(item.label, Modifier.weight(1f).padding(start = 12.dp))
                        TextButton(onClick = {
                            repo.settings.update { it.copy(hiddenTools = it.hiddenTools - item.id, toolOrder = (it.toolOrder + item.id).distinct()) }
                        }) {
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                            Text("顯示")
                        }
                    }
                    com.enwei.moneymanager.ui.components.LineDivider()
                }
            }
            Text(
                "設定與通知記帳設定固定從工具頁右上角進入，不會重複列在工具清單。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/** 「設定 → 功能開關」關掉的功能，對應的工具一律不顯示 */
fun com.enwei.moneymanager.data.AppSettings.offTools(): Set<String> = buildSet {
    if (!featureStocks) addAll(listOf("advisor", "stockfees", "stocksearch"))
    if (!featureNotif) add("notif")
    if (!aiEnabled) add("ai-hub")
    if (!locationHints) addAll(listOf("spendmap", "merchants"))
    if (!featureSplit) add("debts")
}
