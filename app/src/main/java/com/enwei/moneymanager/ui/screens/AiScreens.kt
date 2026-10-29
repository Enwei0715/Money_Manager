package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.enwei.moneymanager.net.GroqAi.AiTask
import com.enwei.moneymanager.net.GroqAi.Provider
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.Text
import kotlinx.coroutines.launch

/** AI 設定：Groq 與 Gemini 兩家的金鑰、模型、用量與測試，以及每項工作交給哪一家。 */
@Composable
fun AiSettingsScreen(nav: NavController) {
    val s by repo.settings.flow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val today = java.time.LocalDate.now().toEpochDay()
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackTopBar("AI 智慧辨識", { nav.popBackStack() }) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SwitchRow("啟用 AI", "判讀通知、整理名稱與分類、檢討與股票分析", s.aiEnabled, s.groqKey.isNotBlank() || s.geminiKey.isNotBlank()) { v ->
                        repo.settings.update { it.copy(aiEnabled = v) }
                    }
                    SwitchRow("AI 自動整理", "自動合併重複記的同一筆；確認 AI 判讀的通知後自動學會格式", s.aiAutoTidy, s.aiEnabled) { v ->
                        repo.settings.update { it.copy(aiAutoTidy = v) }
                    }
                }
            }

            // Groq
            ProviderCard(
                Provider.GROQ, "速度快，適合頻繁的小工作；免費方案每分鐘約 8,000 tokens、每天約 20 萬 tokens",
                s.groqKey, { k -> repo.settings.update { it.copy(groqKey = k.trim()) } },
                usage = if (s.aiUsageDay == today) "今天 ${s.aiUsageRequests} 次、${"%,d".format(s.aiUsageTokens)} tokens" else "今天還沒用",
            ) {
                ModelField("一般模型", s.aiModel) { v -> repo.settings.update { it.copy(aiModel = v) } }
                ModelField("進階模型", s.aiModelHard) { v -> repo.settings.update { it.copy(aiModelHard = v) } }
                ModelField("看圖模型", s.aiModelVision) { v -> repo.settings.update { it.copy(aiModelVision = v) } }
            }

            // Gemini
            ProviderCard(
                Provider.GEMINI, "一次可以讀大量資料（上千則新聞、整期交易、圖片）；免費方案每天請求次數有限，到 aistudio.google.com 申請金鑰",
                s.geminiKey, { k -> repo.settings.update { it.copy(geminiKey = k.trim()) } },
                usage = if (s.geminiUsageDay == today) "今天 ${s.geminiUsageRequests} 次、${"%,d".format(s.geminiUsageTokens)} tokens" else "今天還沒用",
            ) {
                ModelField("主要模型（報告、檢討、對話、看圖）", s.geminiModel) { v -> repo.settings.update { it.copy(geminiModel = v) } }
                ModelField("輕量模型（新聞標註、錢包畫面）", s.geminiLiteModel) { v -> repo.settings.update { it.copy(geminiLiteModel = v) } }
            }

            // 工作分配
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("模型選擇", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    SwitchRow("自動選擇（建議）", "依工作難度與資料量挑模型：簡單短的用 Groq 20b，要推理的用 Groq 120b，資料量大的用 Gemini；用完額度自動換下一個",
                        s.aiAutoRoute, true) { v -> repo.settings.update { it.copy(aiAutoRoute = v) } }
                    AiTask.entries.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.label, fontSize = 14.sp)
                                // 自動時顯示這項工作的候選順序（資料量小／大兩種情況）
                                val small = GroqAi.routes(t, 1000).joinToString(" → ") { it.model.substringAfterLast('/') }
                                val large = GroqAi.routes(t, 50_000).joinToString(" → ") { it.model.substringAfterLast('/') }
                                Text(
                                    t.desc + "\n" + if (small == large) small else "資料少：$small\n資料多：$large",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!s.aiAutoRoute) {
                                val want = s.aiRoutes[t.name]?.let { r -> Provider.entries.find { it.name == r } } ?: t.default
                                Provider.entries.forEach { p ->
                                    FilterChip(want == p, { repo.settings.update { it.copy(aiRoutes = it.aiRoutes + (t.name to p.name)) } },
                                        label = { Text(p.label, fontSize = 12.sp) }, modifier = Modifier.padding(start = 4.dp))
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                    if (!s.aiAutoRoute) OutlinedButton(onClick = { repo.settings.update { it.copy(aiRoutes = emptyMap()) } }) { Text("恢復建議分配") }
                }
            }

            // 最近的 AI 呼叫：哪項工作、哪一家、花多久、成功或失敗原因
            val log by GroqAi.callLog.collectAsStateWithLifecycle()
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("最近的 AI 呼叫", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    GroqAi.paused().forEach { (m, t) ->
                        Text("$m 暫停到 " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.TAIWAN).format(java.util.Date(t)) + "（剛才額度用完或沒有回應，先用下一個模型）",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                    if (log.isEmpty()) Text("這次開啟 App 後還沒有呼叫", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    log.forEach { c ->
                        Text(
                            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.TAIWAN).format(java.util.Date(c.time)) +
                                "  ${c.task.label} · ${c.provider.label} ${c.model} · ${"%.1f".format(c.ms / 1000.0)} 秒 · " + (if (c.ok) "成功" else "失敗：${c.error ?: "沒有回應"}"),
                            fontSize = 11.sp, color = if (c.ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Text(
                "只會送出已啟用 App 的通知文字、商家與分類名稱、檢討時的交易明細、股票分析用的新聞與持股，以及對帳時選的截圖。" +
                    "金鑰只存在這支手機、不放進備份。Gemini 免費方案可能會用送出的內容改進模型；不想讓交易明細送到 Gemini，可以把「收支檢討」「整理不明花費」改成 Groq。",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, on: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(on, onChange, enabled = enabled)
    }
}

@Composable
private fun ProviderCard(
    p: Provider, desc: String, key: String, onKey: (String) -> Unit, usage: String, models: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var k by remember { mutableStateOf(key) }
    var result by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(p.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                k, { k = it; onKey(it) }, label = { Text("${p.label} API 金鑰") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            )
            models()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(usage, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                OutlinedButton(enabled = k.isNotBlank() && !testing, onClick = {
                    testing = true; result = null
                    scope.launch { result = GroqAi.testProvider(p)?.let { "連線失敗：$it" } ?: "連線成功"; testing = false }
                }) { Text(if (testing) "測試中…" else "測試連線") }
            }
            result?.let { Text(it, fontSize = 13.sp, color = if (it == "連線成功") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ModelField(label: String, value: String, onChange: (String) -> Unit) {
    var v by remember(value) { mutableStateOf(value) }
    OutlinedTextField(v, { v = it; onChange(it.trim()) }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}
