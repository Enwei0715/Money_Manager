package com.enwei.moneymanager.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 細線條圖示（24 格、線寬 1.3、圓角端點），用在「更多」頁等需要較精緻外觀的地方。
 * 顏色由 Icon 的 tint 決定。
 */
object LineIcons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.3f,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    /** 收支檢討：清單板 */
    val Review = icon(
        "review",
        "M9 4.5h6v2.5H9z",
        "M7 5.75h-0.5A1.5 1.5 0 0 0 5 7.25v11.25A1.5 1.5 0 0 0 6.5 20h11a1.5 1.5 0 0 0 1.5 -1.5V7.25A1.5 1.5 0 0 0 17.5 5.75H17",
        "M8.5 11.5h7 M8.5 14.5h7 M8.5 17.5h4",
    )

    /** 投資助理：指南針 */
    val Advisor = icon(
        "advisor",
        "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0",
        "M15 9l-1.9 4.1L9 15l1.9 -4.1z",
    )

    /** 設定：兩條滑桿 */
    val Settings = icon(
        "settings",
        "M4 7.5h8.5 M16.5 7.5H20 M4 16.5h3.5 M11.5 16.5H20",
        "M12.5 7.5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
        "M7.5 16.5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
    )

    /** 備份：雲朵與上傳箭頭 */
    val Backup = icon(
        "backup",
        "M8 18.5H7a4.25 4.25 0 0 1 -0.55 -8.46A5.75 5.75 0 0 1 17.6 8.9A4.3 4.3 0 0 1 17 18.5h-1",
        "M12 19.5v-7.5 M9.5 14.5L12 12l2.5 2.5",
    )

    /** 通知記帳：鈴鐺 */
    val Notification = icon(
        "notification",
        "M6.5 15.5v-4.5a5.5 5.5 0 0 1 11 0v4.5l1.5 2h-14z",
        "M10.25 20a1.9 1.9 0 0 0 3.5 0",
    )

    /** 錢包讀取：錢包 */
    val Wallet = icon(
        "wallet",
        "M5 6.5h11.5a1.5 1.5 0 0 1 1.5 1.5v1",
        "M5 6.5a1.5 1.5 0 0 0 -1.5 1.5v9.5a2 2 0 0 0 2 2h13a1.5 1.5 0 0 0 1.5 -1.5v-7a1.5 1.5 0 0 0 -1.5 -1.5H4.5",
        "M16.5 14h0.01",
    )

    /** 股票：上升的折線 */
    val Stocks = icon(
        "stocks",
        "M3.5 17l5.5 -5.5 3.5 3.5 7.5 -7.5",
        "M15.5 7.5h4.5V12",
    )

    /** 週期扣款：月曆與循環箭頭 */
    val Recurring = icon(
        "recurring",
        "M11 19.5H5.5a1.5 1.5 0 0 1 -1.5 -1.5V7a1.5 1.5 0 0 1 1.5 -1.5h13A1.5 1.5 0 0 1 20 7v4",
        "M4 10h16 M8 3.5v4 M16 3.5v4",
        "M20.5 17.25a3.25 3.25 0 1 1 -1 -2.35 M19.75 13.25v1.9h-1.9",
    )

    /** 匯率：左右交換箭頭 */
    val Exchange = icon(
        "exchange",
        "M4 8.5h15 M15.5 5L19 8.5l-3.5 3.5",
        "M20 15.5H5 M8.5 12L5 15.5L8.5 19",
    )

    /** 待確認：收件匣 */
    val Inbox = icon(
        "inbox",
        "M3.5 13.5l2.3 -6.9A1.5 1.5 0 0 1 7.2 5.5h9.6a1.5 1.5 0 0 1 1.4 1.1l2.3 6.9v4.5a1.5 1.5 0 0 1 -1.5 1.5h-14a1.5 1.5 0 0 1 -1.5 -1.5z",
        "M3.5 13.5h4.5l1.5 2h5l1.5 -2h4.5",
    )

    /** App 更新：下載箭頭 */
    val Update = icon(
        "update",
        "M12 4v10.5 M8 10.5l4 4l4 -4",
        "M4.5 15.5v2.5a1.5 1.5 0 0 0 1.5 1.5h12a1.5 1.5 0 0 0 1.5 -1.5v-2.5",
    )
}
