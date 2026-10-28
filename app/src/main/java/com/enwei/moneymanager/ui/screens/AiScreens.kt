package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.net.GroqAi
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.Text
import kotlinx.coroutines.launch

/** AI 智慧辨識（Groq）設定：API 金鑰、模型、開關與連線測試。 */
@Composable
fun AiSettingsScreen(nav: NavController) {
    val s by repo.settings.flow.collectAsStateWithLifecycle()
    var key by remember { mutableStateOf(s.groqKey) }
    var model by remember(s.aiModel) { mutableStateOf(s.aiModel) }
    var result by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Scaffold(containerColor = MaterialTheme.colorScheme.surface, topBar = { BackTopBar("AI 智慧辨識", { nav.popBackStack() }) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("啟用 AI 智慧辨識", fontSize = 17.sp)
                    Text("判讀通知、整理商家名稱與分類", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(s.aiEnabled, { v -> repo.settings.update { it.copy(aiEnabled = v) } }, enabled = s.groqKey.isNotBlank())
            }
            OutlinedTextField(
                key, { key = it; repo.settings.update { st -> st.copy(groqKey = it.trim()) } },
                label = { Text("Groq API 金鑰") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                model, { model = it; repo.settings.update { st -> st.copy(aiModel = it.trim()) } },
                label = { Text("一般模型") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            var hardModel by remember(s.aiModelHard) { mutableStateOf(s.aiModelHard) }
            OutlinedTextField(
                hardModel, { hardModel = it; repo.settings.update { st -> st.copy(aiModelHard = it.trim()) } },
                label = { Text("進階模型（整理不明花費）") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            var visionModel by remember(s.aiModelVision) { mutableStateOf(s.aiModelVision) }
            OutlinedTextField(
                visionModel, { visionModel = it; repo.settings.update { st -> st.copy(aiModelVision = it.trim()) } },
                label = { Text("看圖模型（截圖對帳）") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            val today = java.time.LocalDate.now().toEpochDay()
            Text(
                "今天已用：${if (s.aiUsageDay == today) s.aiUsageRequests else 0} 次請求、${"%,d".format(if (s.aiUsageDay == today) s.aiUsageTokens else 0L)} tokens",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(enabled = key.isNotBlank() && !testing, onClick = {
                testing = true; result = null
                scope.launch {
                    result = GroqAi.test()?.let { "連線失敗：$it" } ?: "連線成功，可以使用"
                    testing = false
                }
            }) { Text(if (testing) "測試中…" else "測試連線") }
            result?.let { Text(it, color = if (it.startsWith("連線成功")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
            GroqAi.lastError?.takeIf { result == null }?.let {
                Text("上次錯誤：$it", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.padding(4.dp))
            Text(
                "只會送出已啟用 App 的通知文字、商家名稱、分類名稱，以及對帳時選的截圖到 Groq 處理；金鑰只存在這支手機。超過免費額度時改用一般規則，不影響記帳。",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
