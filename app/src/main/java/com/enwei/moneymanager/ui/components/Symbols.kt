package com.enwei.moneymanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Contactless
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.enwei.moneymanager.data.AccountType
import com.enwei.moneymanager.data.TxnType

/**
 * App 統一的單色符號：圓角方塊底 + 白色實心圖形，取代彩色表情符號。
 * 分類依名稱關鍵字對應，找不到時使用通用符號。
 */
object Symbols {
    private val rules: List<Pair<List<String>, ImageVector>> = listOf(
        listOf("咖啡", "飲料", "手搖") to Icons.Rounded.LocalCafe,
        listOf("餐", "食", "飯", "吃", "早午", "宵夜", "food", "meal", "dinner", "lunch") to Icons.Rounded.Restaurant,
        listOf("超商", "便利", "超市", "雜貨") to Icons.Rounded.Storefront,
        listOf("日用", "生活用品") to Icons.Rounded.LocalGroceryStore,
        listOf("油", "停車", "汽車", "機車", "car") to Icons.Rounded.DirectionsCar,
        listOf("加油") to Icons.Rounded.LocalGasStation,
        listOf("交通", "捷運", "公車", "高鐵", "計程", "transport") to Icons.Rounded.DirectionsBus,
        listOf("服飾", "衣", "鞋") to Icons.Rounded.Checkroom,
        listOf("購物", "網購", "shopping") to Icons.Rounded.ShoppingBag,
        listOf("娛樂", "遊戲", "電影", "訂閱") to Icons.Rounded.SportsEsports,
        listOf("房租", "租") to Icons.Rounded.Apartment,
        listOf("居家", "家", "水電", "瓦斯", "house") to Icons.Rounded.Home,
        listOf("電信", "網路", "手機", "電話") to Icons.Rounded.Wifi,
        listOf("醫", "藥", "健康", "health") to Icons.Rounded.LocalHospital,
        listOf("運動", "健身") to Icons.Rounded.FitnessCenter,
        listOf("保險") to Icons.Rounded.Shield,
        listOf("教育", "學", "書", "課", "education") to Icons.Rounded.School,
        listOf("社交", "禮", "交際", "紅包", "gift") to Icons.Rounded.Redeem,
        listOf("美容", "美髮", "保養", "beauty") to Icons.Rounded.Face,
        listOf("寵物", "pet") to Icons.Rounded.Pets,
        listOf("小孩", "育兒", "孩子") to Icons.Rounded.ChildCare,
        listOf("旅", "機票", "住宿", "travel") to Icons.Rounded.Flight,
        listOf("稅", "費", "規費") to Icons.AutoMirrored.Rounded.ReceiptLong,
        listOf("薪", "salary", "工作") to Icons.Rounded.Work,
        listOf("獎金", "bonus", "中獎") to Icons.Rounded.EmojiEvents,
        listOf("投資", "股", "股利", "股息") to Icons.AutoMirrored.Rounded.ShowChart,
        listOf("利息", "interest") to Icons.Rounded.AccountBalance,
        listOf("退款", "退費", "refund") to Icons.AutoMirrored.Rounded.Undo,
        listOf("零用", "收入", "副業") to Icons.Rounded.Savings,
    )

    fun category(name: String?, kind: TxnType? = null): ImageVector {
        val n = name?.lowercase() ?: return Icons.Rounded.Category
        rules.firstOrNull { (keys, _) -> keys.any { n.contains(it) } }?.let { return it.second }
        return when (kind) {
            TxnType.INCOME -> Icons.Rounded.Payments
            TxnType.TRANSFER -> Icons.Rounded.Repeat
            else -> Icons.Rounded.Category
        }
    }

    fun account(type: AccountType): ImageVector = when (type) {
        AccountType.CASH -> Icons.Rounded.Payments
        AccountType.BANK -> Icons.Rounded.AccountBalance
        AccountType.CREDIT_CARD -> Icons.Rounded.CreditCard
        AccountType.EWALLET -> Icons.Rounded.PhoneAndroid
        AccountType.TRANSIT_CARD -> Icons.Rounded.Contactless
        AccountType.BROKERAGE -> Icons.AutoMirrored.Rounded.ShowChart
        AccountType.LOAN -> Icons.Rounded.RequestQuote
        AccountType.OTHER -> Icons.Rounded.AccountBalanceWallet
    }

    val transfer: ImageVector get() = Icons.Rounded.Repeat
    val wallet: ImageVector get() = Icons.Rounded.AccountBalanceWallet
}

/** 圓角方塊符號（與 App 圖示同一風格）。 */
@Composable
fun SymbolBadge(icon: ImageVector, color: Color = MaterialTheme.colorScheme.primary, size: Dp = 34.dp) {
    Box(
        Modifier.size(size).background(color, RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.6f)) }
}

/** 狀態列：勾選／警示符號 + 文字。 */
@Composable
fun StatusLine(ok: Boolean, text: String) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Error, null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        com.enwei.moneymanager.ui.components.Text(text, style = MaterialTheme.typography.titleSmall)
    }
}
