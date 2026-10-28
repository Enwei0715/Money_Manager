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
                    Text("規則判斷不出來時才使用", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                "今天已用：${if (s.aiUsageDay == today) s.aiUsageRequests else 0} 次請求、${"%,d".format(if (s.aiUsageDay == today) s.aiUsageTokens else 0L)} tokens" +
                    "（免費方案每個模型約每天 1,000 次、20 萬 tokens，實際以 Groq 主控台為準）",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "一般模型負責判讀通知、命名與分類，建議用 ${GroqAi.DEFAULT_MODEL}；進階模型推理較深、只用在較難的「不明」推估，建議用 ${GroqAi.HARD_MODEL}，" +
                    "不能用或超過額度時自動改用一般模型；看圖模型在「對帳 → 選截圖／拍照」時直接看圖找餘額。llama-3.1-8b-instant 已於 2026/8/16 停止提供給免費方案，會自動改用建議模型。",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            Text("會用在哪裡", fontSize = 16.sp)
            Text(
                "• 判讀通知：已啟用的銀行／支付 App 通知，規則看不懂時由 AI 判斷是不是交易、金額、收支或轉帳。AI 判讀的一律放到「待確認」。\n" +
                    "• 商家命名：通知裡的商家是代碼時（例如「GOOGLE*WALLET TEMP」），換成好讀的名稱，原文仍保留在交易裡。\n" +
                    "• 挑分類：你的紀錄和關鍵字都對不到時，從你的分類清單（含子分類）挑一個。你改過之後會記住，下次不再問 AI。",
                fontSize = 14.sp,
            )
            Text("會送出什麼", fontSize = 16.sp)
            Text(
                "只送出上述通知的文字或商家名稱、你的分類名稱，以及整理「不明」時那幾筆的日期、金額與帳戶名稱；" +
                    "用「對帳 → 選截圖／拍照」時，會把那張圖送去辨識餘額（請只選銀行／錢包的餘額畫面）。不會送出其他 App 的通知。" +
                    "資料會傳到 Groq 的伺服器處理。金鑰只存在這支手機，不會放進備份檔。",
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Groq 免費方案有每分鐘與每日的次數上限；超過時這段時間改用一般規則，不影響記帳。",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
