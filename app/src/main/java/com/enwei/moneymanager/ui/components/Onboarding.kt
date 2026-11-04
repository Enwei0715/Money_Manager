package com.enwei.moneymanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.ui.AppText
import kotlinx.coroutines.launch

/** 新手導覽：第一次開啟 App 時顯示，之後可從「設定 → 關於 → 新手導覽」再看一次。 */
object Onboarding {
    /** 要不要顯示導覽（設定頁可以手動打開） */
    val show = mutableStateOf(false)

    private data class Page(val icon: ImageVector, val title: String, val body: String, val action: String? = null, val route: String? = null)

    private val pages = listOf(
        Page(Icons.Outlined.Savings, "歡迎使用帳理", "記帳、預算、資產與投資，集中在一個 App。"),
        Page(Icons.Outlined.EditNote, "記一筆", "交易頁右下的「＋」：輸入金額、選分類就完成。記過的店家會自動帶出分類。"),
        Page(Icons.Outlined.NotificationsActive, "通知自動記帳", "銀行、信用卡與支付 App 的付款通知會自動記帳；不確定的放在「待確認」。", "開啟通知記帳", "notif"),
        Page(Icons.Outlined.PieChart, "預算與分析", "設定每月預算，快超過時提醒；「分析」看每月變化與消費習慣。", "設定預算", "budget"),
        Page(Icons.Outlined.AccountBalanceWallet, "資產", "帳戶、信用卡、借貸與股票都在「資產」；信用卡依每期帳單計算應繳與未出帳。", "新增帳戶", "account/0"),
        Page(Icons.Outlined.AutoAwesome, "AI 助理", "填入 Groq 或 Gemini 的免費金鑰，就能自動判讀通知、檢討收支、產生投資報告。", "設定 AI", "ai"),
    )

    /** 第一次開啟（還沒看過導覽）時顯示；[onOpen] 開啟某個畫面。 */
    @Composable
    fun Host(onOpen: (String) -> Unit) {
        val settings by MoneyApp.repo.settings.flow.collectAsStateWithLifecycle()
        androidx.compose.runtime.LaunchedEffect(settings.onboarded) { if (!settings.onboarded) show.value = true }
        if (!show.value) return
        fun finish() {
            show.value = false
            MoneyApp.repo.settings.update { it.copy(onboarded = true) }
        }
        Dialog(onDismissRequest = { finish() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            val pager = rememberPagerState { pages.size }
            val scope = rememberCoroutineScope()
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        if (pager.currentPage < pages.size - 1) TextButton(onClick = { finish() }) { Text("略過") }
                    }
                    HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i ->
                        val p = pages[i]
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Box(Modifier.size(112.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(p.icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(28.dp))
                            Text(p.title, fontSize = AppText.display, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            Text(p.body, fontSize = AppText.body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                                modifier = Modifier.widthIn(max = 360.dp))
                            if (p.action != null && p.route != null) {
                                Spacer(Modifier.height(20.dp))
                                OutlinedButton(onClick = { finish(); onOpen(p.route) }) { Text(p.action) }
                            }
                        }
                    }
                    // 頁面指示點
                    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                        repeat(pages.size) { i ->
                            val on = i == pager.currentPage
                            Box(Modifier.padding(horizontal = 4.dp).size(if (on) 10.dp else 8.dp)
                                .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp)))
                        }
                    }
                    Button(onClick = {
                        if (pager.currentPage < pages.size - 1) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } else finish()
                    }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text(if (pager.currentPage < pages.size - 1) "下一步" else "開始使用", fontSize = AppText.section)
                    }
                }
            }
        }
    }
}
