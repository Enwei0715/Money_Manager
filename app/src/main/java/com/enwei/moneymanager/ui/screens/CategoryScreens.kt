package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.data.CategoryEntity
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.ManageRow
import com.enwei.moneymanager.ui.components.OptionDialog
import com.enwei.moneymanager.ui.components.ReorderableColumn
import com.enwei.moneymanager.ui.components.SectionTitle
import kotlinx.coroutines.launch

@Composable
private fun KindSwitch(kind: TxnType, onChange: (TxnType) -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).height(44.dp)) {
        listOf(TxnType.EXPENSE, TxnType.INCOME).forEach { k ->
            val sel = k == kind
            Column(Modifier.weight(1f).fillMaxHeight().clickable { onChange(k) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(k.label, color = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
            }
        }
    }
}

/** 分類設定：大分類清單（子分類數量與名稱摘要），可新增、編輯、刪除、拖曳排序。 */
@Composable
fun CategorySettingsScreen(nav: NavController, initialKind: TxnType) {
    val scope = rememberCoroutineScope()
    val cats by repo.categories.collectAsStateWithLifecycle(emptyList())
    val rules by repo.rules.collectAsStateWithLifecycle(emptyList())
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val lk = rememberLookups()
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var deleting by remember { mutableStateOf<CategoryEntity?>(null) }
    val roots = cats.filter { it.kind == kind && it.parentId == null }.sortedWith(compareBy({ it.sortOrder }, { it.id }))

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        BackTopBar("${kind.label}分類設定", { nav.popBackStack() }) {
            IconButton(onClick = { editing = CategoryEntity(name = "", kind = kind, icon = "", sortOrder = roots.size) }) { Icon(Icons.Filled.Add, "新增") }
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            KindSwitch(kind) { kind = it }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ReorderableColumn(roots, { it.id }, { scope.launch { repo.reorderCategories(it) } }) { c, handle ->
                val subs = cats.filter { it.parentId == c.id }.sortedBy { it.sortOrder }
                ManageRow(
                    title = c.name + (if (settings.subcategoryOn && subs.isNotEmpty()) " (${subs.size})" else "") + if (c.archived) "（隱藏）" else "",
                    subtitle = if (settings.subcategoryOn) subs.joinToString(", ") { it.name } else null,
                    handle = handle, dimmed = c.archived,
                    onDelete = { deleting = c }, onEdit = { editing = c },
                    onClick = if (settings.subcategoryOn) ({ nav.navigate("subcats/${c.id}") }) else null,
                )
            }
            if (!settings.subcategoryOn) Text(
                "子分類功能已關閉，可在 設定 → 分類與預算 → 子分類 開啟。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            val myRules = rules.filter { r -> lk.category(r.categoryId)?.kind == kind }
            if (myRules.isNotEmpty()) {
                SectionTitle("已學習的商家分類（點擊刪除）", Modifier.padding(top = 16.dp))
                myRules.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                            .clickable { scope.launch { repo.deleteRule(r.keyword) } }.padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Text(r.keyword, Modifier.weight(1f))
                        Text(lk.catLabel(r.categoryId) ?: "?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    editing?.let { c -> CategoryEditDialog(c, roots, onDismiss = { editing = null }) }
    deleting?.let { c -> DeleteCategoryDialog(c, cats.count { it.parentId == c.id }) { deleting = null } }
}

/** 某個大分類底下的子分類清單。 */
@Composable
fun SubcategoryScreen(nav: NavController, parentId: Long) {
    val scope = rememberCoroutineScope()
    val cats by repo.categories.collectAsStateWithLifecycle(emptyList())
    val parent = cats.find { it.id == parentId }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var deleting by remember { mutableStateOf<CategoryEntity?>(null) }
    val subs = cats.filter { it.parentId == parentId }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    val roots = cats.filter { parent != null && it.kind == parent.kind && it.parentId == null }.sortedBy { it.sortOrder }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        BackTopBar(parent?.name ?: "子分類", { nav.popBackStack() }) {
            if (parent != null) IconButton(onClick = {
                editing = CategoryEntity(name = "", kind = parent.kind, icon = "", sortOrder = subs.size, parentId = parentId)
            }) { Icon(Icons.Filled.Add, "新增子分類") }
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ReorderableColumn(subs, { it.id }, { scope.launch { repo.reorderCategories(it) } }) { c, handle ->
                ManageRow(
                    title = c.name + if (c.archived) "（隱藏）" else "", handle = handle, dimmed = c.archived,
                    onDelete = { deleting = c }, onEdit = { editing = c },
                )
            }
            if (subs.isEmpty()) Text(
                "還沒有子分類，按右上角 ＋ 新增。", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(24.dp),
            )
        }
    }
    editing?.let { c -> CategoryEditDialog(c, roots, onDismiss = { editing = null }) }
    deleting?.let { c -> DeleteCategoryDialog(c, 0) { deleting = null } }
}

@Composable
private fun DeleteCategoryDialog(c: CategoryEntity, childCount: Int, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("刪除「${c.name}」？") },
        text = {
            Text(
                if (c.parentId != null) "這個子分類的交易會改記到所屬大分類。"
                else "已記錄的交易會變成未分類" + if (childCount > 0) "，底下 $childCount 個子分類也會一併刪除。" else "。",
            )
        },
        confirmButton = {
            TextButton(onClick = { scope.launch { repo.deleteCategory(c) }; onDismiss() }) {
                Text("刪除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 新增／編輯分類。大分類可「移為子分類」（沒有子分類時）；子分類可移到其他大分類或升為大分類。
 */
@Composable
private fun CategoryEditDialog(c: CategoryEntity, roots: List<CategoryEntity>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val cats by repo.categories.collectAsStateWithLifecycle(emptyList())
    var name by remember(c) { mutableStateOf(c.name) }
    var archived by remember(c) { mutableStateOf(c.archived) }
    var parentId by remember(c) { mutableStateOf(c.parentId) }
    var pickParent by remember { mutableStateOf(false) }
    val hasChildren = c.id != 0L && cats.any { it.parentId == c.id }
    val subOn = repo.settings.flow.collectAsStateWithLifecycle().value.subcategoryOn
    val candidates = roots.filter { it.id != c.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (c.id == 0L) (if (c.parentId != null) "新增子分類" else "新增分類") else "編輯") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("名稱") }, singleLine = true)
                if (c.id != 0L) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("隱藏（不出現在記帳選單）", Modifier.weight(1f))
                    Switch(archived, { archived = it })
                }
                if (subOn && c.id != 0L && !hasChildren && candidates.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { pickParent = true }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(if (c.parentId == null) "→ 移為子分類" else "移至其他大分類", fontSize = 16.sp)
                            Text(
                                parentId?.let { p -> "所屬：${roots.find { it.id == p }?.name ?: "?"}" } ?: "目前為大分類",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val moved = parentId != c.parentId
                val order = if (moved) cats.count { it.parentId == parentId && it.kind == c.kind } else c.sortOrder
                scope.launch { repo.saveCategory(c.copy(name = name.trim(), archived = archived, parentId = parentId, sortOrder = order)) }
                onDismiss()
            }) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
    if (pickParent) OptionDialog(
        title = "選擇要移入的大分類",
        options = listOfNotNull<Pair<Long?, String>>(if (c.parentId != null) null to "（設為大分類）" else null) + candidates.map { it.id to it.name },
        selected = parentId,
        onDismiss = { pickParent = false },
    ) { parentId = it }
}
