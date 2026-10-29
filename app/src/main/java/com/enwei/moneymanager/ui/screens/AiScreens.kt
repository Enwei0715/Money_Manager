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
                Provider.GROQ, "速度快，適合頻繁的小工作",
                s.groqKey, { k -> repo.settings.update { it.copy(groqKey = k.trim()) } },
            ) {
                ModelField("一般模型", s.aiModel) { v -> repo.settings.update { it.copy(aiModel = v) } }
                ModelField("進階模型", s.aiModelHard) { v -> repo.settings.update { it.copy(aiModelHard = v) } }
            }

            // Gemini
            ProviderCard(
                Provider.GEMINI, "適合讀取較大量的資料；金鑰到 aistudio.google.com 申請",
                s.geminiKey, { k -> repo.settings.update { it.copy(geminiKey = k.trim()) } },
            ) {
                ModelField("主要模型（報告、檢討、對話）", s.geminiModel) { v -> repo.settings.update { it.copy(geminiModel = v) } }
                ModelField("輕量模型（新聞標註、錢包畫面）", s.geminiLiteModel) { v -> repo.settings.update { it.copy(geminiLiteModel = v) } }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("模型自動分工", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "App 會依工作類型與資料量選擇 Groq 或 Gemini，並在額度不足或模型無回應時改用其他可用模型。",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                "金鑰只存在這支手機、不放進備份。Gemini 免費方案可能會用送出的內容改進模型。",
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
    p: Provider, desc: String, key: String, onKey: (String) -> Unit, models: @Composable () -> Unit,
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
                Spacer(Modifier.weight(1f))
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
