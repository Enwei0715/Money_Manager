package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import com.enwei.moneymanager.ui.components.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.data.AccountType
import com.enwei.moneymanager.data.groupLabel
import com.enwei.moneymanager.data.groupOrder
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.ManageRow
import com.enwei.moneymanager.ui.components.ReorderableColumn
import com.enwei.moneymanager.ui.components.SymbolBadge
import com.enwei.moneymanager.ui.components.Symbols

@Composable
private fun GroupHeader(title: String) {
    Text(
        title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(start = 16.dp, top = 14.dp, bottom = 8.dp),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** 帳戶設定：依群組列出所有帳戶（含隱藏），點選進入編輯。 */
@Composable
fun AccountSettingsScreen(nav: NavController) {
    val accounts by repo.accounts.collectAsStateWithLifecycle(emptyList())
    val s by repo.settings.flow.collectAsStateWithLifecycle()
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        BackTopBar("帳戶設定", { nav.popBackStack() }) {
            IconButton(onClick = { nav.navigate("account/0") }) { Icon(Icons.Filled.Add, "新增帳戶") }
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            val byType = accounts.groupBy { it.type }
            s.groupOrder().forEach { type ->
                val list = byType[type].orEmpty().sortedWith(compareBy({ it.sortOrder }, { it.id }))
                if (list.isEmpty()) return@forEach
                GroupHeader(s.groupLabel(type))
                list.forEach { a ->
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable { nav.navigate("account/${a.id}") }
                            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            a.name + if (a.archived) "（隱藏）" else "", Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (a.archived) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** 帳戶群組：自訂群組名稱與顯示順序（拖曳排序）。 */
@Composable
fun AccountGroupScreen(nav: NavController) {
    val s by repo.settings.flow.collectAsStateWithLifecycle()
    val accounts by repo.accounts.collectAsStateWithLifecycle(emptyList())
    var renaming by remember { mutableStateOf<AccountType?>(null) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackTopBar("帳戶群組", { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ReorderableColumn(s.groupOrder(), { it.name }, { list ->
                repo.settings.update { it.copy(accountGroupOrder = list.map { t -> t.name }) }
            }) { type, handle ->
                val n = accounts.count { it.type == type }
                ManageRow(
                    title = s.groupLabel(type),
                    subtitle = listOfNotNull(type.label.takeIf { it != s.groupLabel(type) }, "$n 個帳戶").joinToString(" · "),
                    handle = handle, onDelete = null, onEdit = { renaming = type },
                    leading = { SymbolBadge(Symbols.account(type), size = 28.dp) },
                )
            }
            Text(
                "群組對應固定的帳戶類型，只能改名與排序。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
    renaming?.let { type ->
        var name by remember(type) { mutableStateOf(s.groupLabel(type)) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("群組名稱") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("預設：${type.label}") }) },
            confirmButton = {
                TextButton(onClick = {
                    repo.settings.update { st ->
                        val v = name.trim()
                        st.copy(accountGroupNames = if (v.isEmpty() || v == type.label) st.accountGroupNames - type.name else st.accountGroupNames + (type.name to v))
                    }
                    renaming = null
                }) { Text("儲存") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("取消") } },
        )
    }
}
