package com.enwei.moneymanager.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enwei.moneymanager.ui.AppText

/** 區塊右上角的「？」：點了用一段白話說明這塊在算什麼、數字怎麼看。 */
@Composable
fun HelpIcon(title: String, body: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier.size(32.dp)) {
        Icon(Icons.AutoMirrored.Outlined.HelpOutline, "說明", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(title, fontSize = AppText.title) },
        text = { Text(body, fontSize = AppText.body, modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { open = false }) { Text("知道了") } },
    )
}

object HelpText {
    const val SCORES = """每一檔都用 App 自己抓的資料算出五個 0～100 分（50 分＝中性），再依大盤狀態加權成總分：

• 趨勢：近 20 日漲跌、比大盤強多少、是否站上月線（20 日）與季線（60 日）、離半年高點多遠。
• 籌碼：近 5 日三大法人買賣超佔成交量的比例、外資與投信連續買（賣）幾天。
• 基本：月營收年增、是否創 13 個月新高、近四季 EPS 年增、本益比高低。
• 消息：近幾天新聞的多空語氣與則數。
• 穩定：看股價波動大不大（年化波動 20% 以下＝100 分、70% 以上＝0 分）。分數越高代表越穩，不是越危險。

權重：多頭時趨勢、籌碼比重高；空頭時基本面與穩定度比重提高。總分 65 以上偏多、35 以下偏弱。只是篩選與排序的參考，不是買賣建議。"""

    const val SIM = """時光機是用這檔股票「過去一年真實的每日漲跌」重新抽樣，模擬接下來 20 個交易日 3000 次：

• 停損：約 1.5 倍日波動 × √5（介於 4%～10%）。
• 目標：停損距離的 2 倍（2R）。
• 結果統計三種：先碰到目標、先碰到停損、20 天內都沒碰到；期望值＝平均每次賺賠幾 R。

過去的波動不代表未來，只用來判斷「這個停損停利設定合不合理」。"""

    const val PROJECTION = """趨勢推估把三種想法畫在一起：

• 趨勢線：找出最近的波段高低點，連成壓力線與支撐線，往後延伸 20 天（通道、楔形、三角收斂等型態）。
• 統計區間：依最近 60 個交易日的波動，算出之後每一天「八成機率會落在裡面」的價格範圍（淡色區塊）。
  － 樂觀＝區間上緣；基準＝中間；悲觀＝區間下緣。波動越大的股票，區間越寬。
  － 近期的漲跌趨勢只取一半計入，不會把最近的急漲或急跌直接往後延伸。
• 高於目前價的機率：依同一組統計算出 20 日後收盤高於今天的機率。

這是統計推估，不是預測；遇到重大消息時走勢可能完全不同。"""

    const val REVENUE = """每月 10 日前公司會公布上個月的營收。

• 年增：和去年同月比，排除淡旺季影響，最常用來看成長。
• 月增：和上個月比，容易受天數、季節影響，參考就好。
• 柱子顏色：紅＝年增、綠＝年減；最右邊亮色是最新一個月。
• 近 13 個月平均年增和最近三個月比，可以看出成長是在加速還是放緩。"""

    const val TRAIL = """保本＋移動停利（60 分 K）：

• 站上快線與慢線（例如 20MA、60MA）且連續幾根確認，才算買點。
• 跌破其中一條（超過容許的 %）先賣一部分，兩條都跌破再賣剩下的。
• 保本：先把本金收回，留下「基本股」讓獲利繼續跑。
• 參數在「股票助理 → 資金紀律」調整。"""

    const val CREDIT = """信用卡依每一期帳單計算：

• 應繳餘額：已經出帳、還沒繳的金額（繳款日前要繳）。
• 未出帳：這一期還沒結帳的刷卡，下次帳單才會出現。
• 帳戶餘額：應繳＋未出帳，扣掉已經繳的；負數代表你欠銀行的錢。"""
}
