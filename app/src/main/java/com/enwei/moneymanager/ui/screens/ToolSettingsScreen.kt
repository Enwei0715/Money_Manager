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

object ToolCatalog {
    val items = listOf(
        ToolItem("pending", "待確認", "pending", com.enwei.moneymanager.ui.components.LineIcons.Inbox),
        ToolItem("recurring", "週期交易", "recurring", com.enwei.moneymanager.ui.components.LineIcons.Recurring),
        ToolItem("fx", "匯率", "fx", com.enwei.moneymanager.ui.components.LineIcons.Exchange),
        ToolItem("backup", "備份", "backup", com.enwei.moneymanager.ui.components.LineIcons.Backup),
        ToolItem("ai-hub", "AI 中心", "ai-hub", com.enwei.moneymanager.ui.components.LineIcons.Advisor),
    )
}

@Composable
fun ToolSettingsScreen(nav: NavController) {
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val order = settings.toolOrder.mapNotNull { id -> ToolCatalog.items.find { it.id == id } }
        .plus(ToolCatalog.items.filter { it.id !in settings.toolOrder })
    val visible = order.filter { it.id !in settings.hiddenTools }
    val hidden = order.filter { it.id in settings.hiddenTools }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackTopBar("工具首頁與順序", { nav.popBackStack() }) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            Text("首頁顯示與排序", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            ReorderableColumn(visible, { it.id }, { reordered ->
                repo.settings.update { current ->
                    val hiddenOrder = current.toolOrder.filter { it in current.hiddenTools }
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            if (hidden.isNotEmpty()) {
                Text("未顯示的工具", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
                hidden.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(item.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(item.label, Modifier.weight(1f).padding(start = 12.dp))
                        TextButton(onClick = {
                            repo.settings.update { it.copy(hiddenTools = it.hiddenTools - item.id) }
                        }) {
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                            Text("顯示")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
