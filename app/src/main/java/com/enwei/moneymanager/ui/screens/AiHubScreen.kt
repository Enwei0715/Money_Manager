package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.ui.Alignment
import com.enwei.moneymanager.data.hasAiKey
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.ui.components.AiAllTasksProgress
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.Text

@Composable
fun AiHubScreen(nav: NavController) {
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val ready = settings.aiEnabled && settings.hasAiKey
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackTopBar("AI 中心", { nav.popBackStack() }) },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AiAllTasksProgress()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, androidx.compose.foundation.shape.CircleShape))
                Text(if (ready) "AI 已啟用" else "AI 尚未設定", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp))
            }
            HubCard("收支分析", androidx.compose.material.icons.Icons.Outlined.Assessment) { nav.navigate("review") }
            if (settings.featureStocks) HubCard("股票助理", androidx.compose.material.icons.Icons.Outlined.ShowChart) { nav.navigate("advisor") }
            HubCard("待確認與 AI 建議", androidx.compose.material.icons.Icons.Outlined.Inbox) { nav.navigate("pending") }
            HubCard("AI 設定", androidx.compose.material.icons.Icons.Outlined.Settings) { nav.navigate("ai") }
        }
    }
}

@Composable
private fun HubCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 14.dp).weight(1f))
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
