package com.enwei.moneymanager.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enwei.moneymanager.ui.tr

/**
 * 本月摘要小工具的自訂設定（每個小工具各自一份，只存在這支手機）。
 * [metric] 主要數字：0 支出、1 預算剩餘；[period] 期間：0 本月、1 本週、2 今天；
 * [buttons] 下方三個快捷按鈕（[BUTTONS] 的索引）；[alpha] 背景不透明度（%）。
 */
data class WidgetCfg(val metric: Int = 0, val period: Int = 0, val buttons: List<Int> = listOf(0, 1, 2), val alpha: Int = 100)

object WidgetConfig {
    /** 快捷按鈕：顯示文字、要打開的畫面（{cash} 換成現金帳戶 id）、文字顏色 */
    data class Btn(val label: String, val route: String, val color: Int)
    val BUTTONS = listOf(
        Btn("－ 現金支出", "txn/0?type=EXPENSE&account={cash}", 0xFFF0605A.toInt()),
        Btn("＋ 收入", "txn/0?type=INCOME", 0xFF3C82E6.toInt()),
        Btn("⇄ 轉帳", "txn/0?type=TRANSFER", 0xFF8C8C8C.toInt()),
        Btn("－ 記支出", "txn/0?type=EXPENSE", 0xFFF0605A.toInt()),
        Btn("待確認", "pending", 0xFFC9913F.toInt()),
        Btn("統計", "stats", 0xFF8C8C8C.toInt()),
    )
    val METRICS = listOf("支出", "預算剩餘")
    val PERIODS = listOf("本月", "本週", "今天")
    val ALPHAS = listOf(100, 85, 70, 50)

    private fun prefs(c: Context) = c.getSharedPreferences("widget_cfg", Context.MODE_PRIVATE)

    fun load(c: Context, id: Int): WidgetCfg {
        val raw = prefs(c).getString("w$id", null) ?: return WidgetCfg()
        val f = raw.split('|')
        return runCatching {
            WidgetCfg(f[0].toInt(), f[1].toInt(), f[2].split(',').map { it.toInt() }.filter { it in BUTTONS.indices }.take(3), f[3].toInt())
        }.getOrDefault(WidgetCfg())
    }

    fun save(c: Context, id: Int, cfg: WidgetCfg) =
        prefs(c).edit().putString("w$id", "${cfg.metric}|${cfg.period}|${cfg.buttons.joinToString(",")}|${cfg.alpha}").apply()

    fun remove(c: Context, id: Int) = prefs(c).edit().remove("w$id").apply()
}

/** 放上桌面時（或之後長按小工具「設定」）出現的設定頁 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // 按返回不儲存：系統視為取消
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val start = WidgetConfig.load(this, id)
        setContent {
            val s = com.enwei.moneymanager.MoneyApp.repo.settings.value
            com.enwei.moneymanager.ui.MoneyTheme(dark = com.enwei.moneymanager.ui.isDarkTheme(s.themeMode), preset = s.themePreset) {
                ConfigPage(start, onCancel = { finish() }) { cfg ->
                    WidgetConfig.save(this, id, cfg)
                    SummaryWidget.refresh(this)
                    setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                    finish()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigPage(start: WidgetCfg, onCancel: () -> Unit, onSave: (WidgetCfg) -> Unit) {
    var cfg by remember { mutableStateOf(start) }
    val primary = MaterialTheme.colorScheme.primary
    @Composable fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
        Text(tr(label), fontSize = 15.sp, color = if (on) primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
                .background(if (on) primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .border(1.dp, if (on) primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp))
    }
    @Composable fun Section(title: String) {
        Text(tr(title), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text(tr("小工具設定"), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Section("主要數字")
        FlowRow { WidgetConfig.METRICS.forEachIndexed { i, l -> Chip(l, cfg.metric == i) { cfg = cfg.copy(metric = i, period = if (i == 1) 0 else cfg.period) } } }
        if (cfg.metric == 0) {
            Section("期間")
            FlowRow { WidgetConfig.PERIODS.forEachIndexed { i, l -> Chip(l, cfg.period == i) { cfg = cfg.copy(period = i) } } }
        }
        Section("下方按鈕（依點選順序，最多 3 個）")
        FlowRow {
            WidgetConfig.BUTTONS.forEachIndexed { i, b ->
                val pos = cfg.buttons.indexOf(i)
                Chip(if (pos >= 0) "${pos + 1}. ${b.label}" else b.label, pos >= 0) {
                    cfg = cfg.copy(buttons = if (pos >= 0) cfg.buttons - i else (cfg.buttons + i).takeLast(3))
                }
            }
        }
        Section("背景不透明度")
        FlowRow { WidgetConfig.ALPHAS.forEach { a -> Chip("$a%", cfg.alpha == a) { cfg = cfg.copy(alpha = a) } } }
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(tr("取消")) }
            Button(onClick = { onSave(cfg) }, enabled = cfg.buttons.isNotEmpty()) { Text(tr("完成")) }
        }
    }
}
