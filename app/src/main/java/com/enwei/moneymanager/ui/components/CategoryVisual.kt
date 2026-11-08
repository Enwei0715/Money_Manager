package com.enwei.moneymanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 分類的圖示與顏色：依大分類名稱的關鍵字挑圖示；顏色用一組低彩度色票，同一分類永遠同色。
 * 交易列、統計、預算都用這個，看起來一致。
 */
object CategoryVisual {
    private val rules: List<Pair<List<String>, ImageVector>> = listOf(
        listOf("早餐", "飲料", "咖啡", "點心") to LineIcons.IcCafe,
        listOf("飲食", "餐", "食") to LineIcons.IcFood,
        listOf("交通", "車", "捷運", "油") to LineIcons.IcBus,
        listOf("日用", "雜貨", "超市") to LineIcons.IcBasket,
        listOf("服飾", "衣") to LineIcons.IcShirt,
        listOf("購物", "3C", "家電") to LineIcons.IcBag,
        listOf("居住", "房租", "水電", "家") to LineIcons.IcHome,
        listOf("娛樂", "遊戲", "電影") to LineIcons.IcGame,
        listOf("訂閱") to LineIcons.IcPlay,
        listOf("醫療", "藥", "診所") to LineIcons.IcMedical,
        listOf("旅遊", "旅行") to LineIcons.IcPlane,
        listOf("運動", "健身") to LineIcons.IcDumbbell,
        listOf("學習", "課程", "書") to LineIcons.IcBook,
        listOf("保險") to LineIcons.IcShield,
        listOf("人情", "禮") to LineIcons.IcGift,
        listOf("工作") to LineIcons.IcBriefcase,
        listOf("金融", "手續費", "銀行") to LineIcons.IcBank,
        listOf("薪資", "收入", "獎金") to LineIcons.IcCash,
        listOf("家庭", "支援") to LineIcons.IcPeople,
        listOf("回饋", "點數") to LineIcons.IcGift,
        listOf("退款") to LineIcons.IcRefund,
        listOf("股", "投資", "股利") to LineIcons.IcChart,
    )
    private val palette = listOf(
        Color(0xFFE07A5F), Color(0xFF3D8BBF), Color(0xFF81B29A), Color(0xFFF2B263), Color(0xFF9C89B8),
        Color(0xFF5FA8A0), Color(0xFFD08C60), Color(0xFF7D8FB3), Color(0xFFB5838D), Color(0xFF6A994E),
    )

    fun icon(name: String?): ImageVector = name?.let { n -> rules.firstOrNull { (keys, _) -> keys.any { n.contains(it, ignoreCase = true) } }?.second }
        ?: LineIcons.IcDots

    fun color(key: Long?): Color = key?.let { palette[(it % palette.size).toInt().let { i -> if (i < 0) -i else i }] } ?: Color(0xFF9E9E9E)
}

/** 分類圓形小圖示：淡色底＋分類色圖示。轉帳傳 transfer = true。 */
@Composable
fun CategoryAvatar(name: String?, colorKey: Long?, size: Dp = 36.dp, transfer: Boolean = false) {
    val c = if (transfer) Color(0xFF8C8C8C) else CategoryVisual.color(colorKey)
    Box(Modifier.size(size).background(c.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(if (transfer) LineIcons.IcSwap else CategoryVisual.icon(name), null, tint = c, modifier = Modifier.size(size * 0.55f))
    }
}
