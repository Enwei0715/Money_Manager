package com.enwei.moneymanager.ui.screens

import com.enwei.moneymanager.ui.AppText

import com.enwei.moneymanager.data.hasAiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.net.GroqAi
import com.enwei.moneymanager.net.GroqAi.Provider
import com.enwei.moneymanager.ui.components.AiAllTasksProgress
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.Text
import kotlinx.coroutines.launch

/** AI 設定：Groq 與 Gemini 金鑰、模型及自動分工。 */
@Composable
fun AiSettingsScreen(nav: NavController) {
    val s by repo.settings.flow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackTopBar("AI 智慧辨識", { nav.popBackStack() }) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AiAllTasksProgress()
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 設定像樹狀：上層「啟用 AI」關閉時，下面的設定全部隱藏也不生效
                    SwitchRow("啟用 AI", "判讀通知、整理名稱與分類、收支與股票分析", s.aiEnabled, true) { v ->
                        repo.settings.update { it.copy(aiEnabled = v) }
                    }
                    if (s.aiEnabled) SwitchRow("AI 自動整理", "自動合併重複記的同一筆；確認 AI 判讀的通知後自動學會格式", s.aiAutoTidy, true) { v ->
                        repo.settings.update { it.copy(aiAutoTidy = v) }
                    }
                    if (s.aiEnabled && !s.hasAiKey)
                        Text("需要至少一個金鑰", fontSize = AppText.caption, color = MaterialTheme.colorScheme.error)
                }
            }
            var tab by com.enwei.moneymanager.ui.components.rememberTab("ai.tab")
            if (s.aiEnabled) com.enwei.moneymanager.ui.components.SubTabs(listOf("金鑰與模型", "自動分工"), tab) { tab = it }
            if (s.aiEnabled && tab == 1) RouteOverview()
            if (s.aiEnabled && tab == 0) {

            // Groq
            ProviderCard(
                Provider.GROQ, "速度快，適合頻繁的小工作",
                s.groqKey, { k -> repo.settings.update { it.copy(groqKey = k.trim()) } },
                onPickModel = { m -> repo.settings.update { it.copy(aiModel = m) } },
            ) {
                ModelField("一般模型", s.aiModel) { v -> repo.settings.update { it.copy(aiModel = v) } }
                ModelField("進階模型", s.aiModelHard) { v -> repo.settings.update { it.copy(aiModelHard = v) } }
            }

            // Gemini
            ProviderCard(
                Provider.GEMINI, "適合讀取較大量的資料；金鑰到 aistudio.google.com 申請",
                s.geminiKey, { k -> repo.settings.update { it.copy(geminiKey = k.trim()) } },
                onPickModel = { m -> repo.settings.update { it.copy(geminiModel = m) } },
            ) {
                ModelField("主要模型（報告、分析、對話）", s.geminiModel) { v -> repo.settings.update { it.copy(geminiModel = v) } }
                ModelField("輕量模型（新聞標註、錢包畫面）", s.geminiLiteModel) { v -> repo.settings.update { it.copy(geminiLiteModel = v) } }
            }

            // OpenRouter：免費模型（:free）當作額外備援
            ProviderCard(
                Provider.OPENROUTER, "免費模型備援，金鑰到 openrouter.ai 申請",
                s.openRouterKey, { k -> repo.settings.update { it.copy(openRouterKey = k.trim()) }; com.enwei.moneymanager.work.AiHealth.clearCooldown { m -> m.endsWith(":free") } },
                onPickModel = { m -> repo.settings.update { it.copy(openRouterModel = m) } },
            ) {
                ModelField("優先模型（空白＝自動挑免費模型）", s.openRouterModel) { v -> repo.settings.update { it.copy(openRouterModel = v.trim()) } }
                val n = remember(s.openRouterKey) { com.enwei.moneymanager.net.OpenRouterModels.cached().size }
                Text(
                    "免費模型 $n 個・每分鐘 20 次・每天 50 次（儲值 10 美元後 1,000 次）",
                    fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                "金鑰只存在這支手機、不放進備份",
                fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, on: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = AppText.body)
            Text(desc, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(on, onChange, enabled = enabled)
    }
}

@Composable
private fun ProviderCard(
    p: Provider, desc: String, key: String, onKey: (String) -> Unit,
    /** 在「可用模型」清單點選時，設成這家的優先模型 */
    onPickModel: ((String) -> Unit)? = null,
    models: @Composable () -> Unit,
) {
    var showModels by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var k by remember { mutableStateOf(key) }
    var result by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(p.label, fontSize = AppText.section, fontWeight = FontWeight.SemiBold)
            Text(desc, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                k, { k = it; onKey(it) }, label = { Text("${p.label} API 金鑰") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            )
            models()
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onPickModel != null) TextButton(enabled = k.isNotBlank(), onClick = { showModels = true }) { Text("可用模型") }
                Spacer(Modifier.weight(1f))
                OutlinedButton(enabled = k.isNotBlank() && !testing, onClick = {
                    testing = true; result = null
                    scope.launch { result = GroqAi.testProvider(p)?.let { if (it.startsWith("部分可用")) it else "連線失敗：$it" } ?: "連線成功"; testing = false }
                }) { Text(if (testing) "測試中…" else "測試連線") }
            }
            result?.let { Text(it, fontSize = AppText.secondary, color = if (it == "連線成功") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
            // 這家平台最近的實際使用狀況（背景工作也會記錄）
            val tick by GroqAi.ProviderLog.flow.collectAsStateWithLifecycle()
            val log = remember(tick) { GroqAi.ProviderLog.get(p) }
            val fmt = remember { java.text.SimpleDateFormat("M/d HH:mm", java.util.Locale.TAIWAN) }
            if (log.okAt > 0 || log.errAt > 0) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (log.okAt > 0) Text("上次成功 ${fmt.format(java.util.Date(log.okAt))}・${log.okModel}", fontSize = AppText.caption, color = MaterialTheme.colorScheme.primary)
                if (log.errAt > log.okAt) Text("上次失敗 ${fmt.format(java.util.Date(log.errAt))}：${log.err}", fontSize = AppText.caption, color = MaterialTheme.colorScheme.error)
            } else if (key.isNotBlank()) Text("還沒有使用紀錄", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showModels && onPickModel != null) ModelListDialog(p, { onPickModel(it); showModels = false }) { showModels = false }
        }
    }
}

@Composable
private fun ModelField(label: String, value: String, onChange: (String) -> Unit) {
    var v by remember(value) { mutableStateOf(value) }
    OutlinedTextField(v, { v = it; onChange(it.trim()) }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}


/** 每項 AI 工作實際的模型順序（第一個是主力，後面依序備援；暫停中的模型淡色）。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RouteOverview() {
    val s by repo.settings.flow.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    // 大量資料的工作用長輸入的順序，其他用短輸入的順序
    val longTasks = setOf(GroqAi.AiTask.NEWS_TAG, GroqAi.AiTask.DIGEST, GroqAi.AiTask.WALLET,
        GroqAi.AiTask.REVIEW, GroqAi.AiTask.REPORT, GroqAi.AiTask.CHAT)
    fun short(m: String) = m.substringAfterLast('/').removeSuffix(":free")
    fun color(p: Provider) = when (p) {
        Provider.GROQ -> androidx.compose.ui.graphics.Color(0xFFF0605A)
        Provider.GEMINI -> androidx.compose.ui.graphics.Color(0xFF3C82E6)
        Provider.OPENROUTER -> androidx.compose.ui.graphics.Color(0xFF7E57C2)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Provider.entries.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.layout.Box(Modifier.padding(end = 4.dp).size(8.dp)
                            .background(color(p), androidx.compose.foundation.shape.CircleShape))
                        Text(p.label, fontSize = AppText.caption)
                    }
                }
            }
            GroqAi.AiTask.entries.forEach { t ->
                val rs = GroqAi.routes(t, if (t in longTasks) Int.MAX_VALUE else 0)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t.label, fontSize = AppText.secondary, fontWeight = FontWeight.SemiBold)
                    if (rs.isEmpty()) Text("沒有可用的模型", fontSize = AppText.caption, color = MaterialTheme.colorScheme.error)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        rs.take(5).forEachIndexed { i, r ->
                            val cooling = com.enwei.moneymanager.work.AiHealth.cooldownUntil(r.model) > now
                            androidx.compose.material3.Surface(
                                color = color(r.provider).copy(alpha = if (cooling) 0.06f else if (i == 0) 0.28f else 0.14f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                            ) {
                                Text(short(r.model), fontSize = AppText.label, maxLines = 1,
                                    color = if (cooling) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}


/** 這家平台目前可用的模型（即時查詢）：可搜尋，點一下設成優先模型。 */
@Composable
private fun ModelListDialog(p: Provider, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var list by remember { mutableStateOf<List<Pair<String, Int>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var q by remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(p) {
        val r = GroqAi.listModels(p)
        if (r == null) error = GroqAi.lastError ?: "查詢失敗" else list = r
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${p.label} 可用模型" + (list?.let { "（${it.size}）" } ?: "")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("搜尋") }, modifier = Modifier.fillMaxWidth())
                when {
                    error != null -> Text(error!!, fontSize = AppText.secondary, color = MaterialTheme.colorScheme.error)
                    list == null -> androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                    else -> androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        val shown = list!!.filter { q.isBlank() || it.first.contains(q.trim(), true) }
                        items(shown.size) { i ->
                            val (id, ctx) = shown[i]
                            Row(Modifier.fillMaxWidth().clickable { onPick(id) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(id, fontSize = AppText.secondary, modifier = Modifier.weight(1f))
                                if (ctx > 0) Text("${ctx / 1000}k", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } },
    )
}
