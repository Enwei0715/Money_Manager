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

    /** 細線條＋小實心點綴（dots：圓心 x、y 與半徑），工具頁用的新圖示 */
    private fun accent(name: String, paths: List<String>, dots: List<Triple<Float, Float, Float>>): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(pathData = addPathNodes(d), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.3f,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            }
            dots.forEach { (x, y, r) ->
                addPath(pathData = addPathNodes("M${x - r} ${y}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"), fill = SolidColor(Color.Black))
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

    /** 工具：待確認 */
    val ToolPending = accent("ToolPending", listOf("M4 13l2-6.2A1.5 1.5 0 0 1 7.4 5.8H13", "M4 13v4.7a1.5 1.5 0 0 0 1.5 1.5h13a1.5 1.5 0 0 0 1.5-1.5V13", "M4 13h4.2l1.3 2.2h5l1.3-2.2H20", "M20 13l-0.9-2.8"), listOf(Triple(18f, 6.2f, 1.7f)))

    /** 工具：週期交易 */
    val ToolRecurring = accent("ToolRecurring", listOf("M18.6 8.6A7.2 7.2 0 1 0 19.2 13.2", "M19.4 4.8v4h-4", "M12 8.6V12l2.4 1.6"), listOf())

    /** 工具：匯率 */
    val ToolFx = accent("ToolFx", listOf("M3.8 10a5.2 5.2 0 1 0 10.4 0a5.2 5.2 0 1 0 -10.4 0", "M13.9 8.9a5.2 5.2 0 1 1 -4.95 6.85"), listOf(Triple(9f, 10f, 1.1f)))

    /** 工具：備份 */
    val ToolBackup = accent("ToolBackup", listOf("M8 18.5H7a4.25 4.25 0 0 1 -0.55 -8.46A5.75 5.75 0 0 1 17.6 8.9A4.3 4.3 0 0 1 17 18.5h-1", "M12 19.5v-7.5", "M9.5 14.5L12 12l2.5 2.5"), listOf())

    /** 工具：AI 中心 */
    val ToolAi = accent("ToolAi", listOf("M11 4c0.55 4.1 2.4 5.95 6.5 6.5c-4.1 0.55 -5.95 2.4 -6.5 6.5c-0.55 -4.1 -2.4 -5.95 -6.5 -6.5c4.1 -0.55 5.95 -2.4 6.5 -6.5z", "M18 15.5v4.5", "M15.75 17.75h4.5"), listOf())

    /** 工具：股票助理 */
    val ToolCandles = accent("ToolCandles", listOf("M8 3.8v16.4", "M6.2 8h3.6v6.8H6.2z", "M16 5.5v13", "M14.2 9.8h3.6v4.6h-3.6z"), listOf())

    /** 工具：收支分析 */
    val ToolBars = accent("ToolBars", listOf("M4.5 19.5h15", "M7.5 16.5v-4.5", "M12 16.5V7.5", "M16.5 16.5v-6.5"), listOf(Triple(12f, 4.8f, 1.2f)))

    /** 工具：預算 */
    val ToolGauge = accent("ToolGauge", listOf("M4.8 16.5a7.2 7.2 0 0 1 14.4 0", "M12 16.5l3.4 -4.2", "M4.5 19.5h15"), listOf(Triple(12f, 16.5f, 1.4f)))

    /** 工具：資產走勢 */
    val ToolTrend = accent("ToolTrend", listOf("M4 16.5l4.5-4.5 3.5 2.6 5.5-6.1", "M4 19.8h16"), listOf(Triple(18.6f, 7.6f, 1.6f)))

    /** 工具：通知記帳 */
    val ToolBell = accent("ToolBell", listOf("M6.5 15.5v-4.5a5.5 5.5 0 0 1 11 0v4.5l1.5 2h-14z", "M10.25 20a1.9 1.9 0 0 0 3.5 0"), listOf(Triple(17.6f, 5.4f, 1.6f)))

    /** 工具：手續費 */
    val ToolReceipt = accent("ToolReceipt", listOf("M6.5 3.8h11v16.4l-1.85-1.2-1.85 1.2-1.8-1.2-1.8 1.2-1.85-1.2-1.85 1.2z", "M9.6 14.4l4.8-4.8"), listOf(Triple(9.9f, 9.9f, 0.95f), Triple(14.1f, 14.1f, 0.95f)))

    /** 工具：App 更新 */
    val ToolUpdate = accent("ToolUpdate", listOf("M12 4v10.5", "M8 10.5l4 4 4-4", "M4.5 15.5v2.5a1.5 1.5 0 0 0 1.5 1.5h12a1.5 1.5 0 0 0 1.5-1.5v-2.5"), listOf())

    // ---- 全 App 共用的細線條圖示（同一套線寬與造型） ----
    /** 交易 */
    val IcLedger = accent("IcLedger", listOf("M5.5 4h10.5a2 2 0 0 1 2 2v14H7.5a2 2 0 0 1 -2-2z", "M5.5 18a2 2 0 0 1 2-2H18", "M9 8h5.5", "M9 11h4"), listOf())

    /** 工具 */
    val IcGrid = accent("IcGrid", listOf("M5 5h5v5H5z", "M14 5h5v5h-5z", "M5 14h5v5H5z"), listOf(Triple(16.5f, 16.5f, 2.3f)))

    /** 日期 */
    val IcCalendar = accent("IcCalendar", listOf("M4.5 6.5h15v13h-15z", "M4.5 10.5h15", "M8.5 4v4", "M15.5 4v4"), listOf(Triple(15.5f, 15f, 1.1f)))

    /** 分類 */
    val IcShapes = accent("IcShapes", listOf("M4.5 4.5h6v6h-6z", "M13.5 4.5h6v6h-6z", "M4.5 13.5h6v6h-6z", "M13.5 16.5a3 3 0 1 0 6 0a3 3 0 1 0 -6 0"), listOf())

    /** 帳戶 */
    val IcWallet = accent("IcWallet", listOf("M5 6.5h11.5a1.5 1.5 0 0 1 1.5 1.5v1", "M5 6.5a1.5 1.5 0 0 0 -1.5 1.5v9.5a2 2 0 0 0 2 2h13a1.5 1.5 0 0 0 1.5 -1.5v-7a1.5 1.5 0 0 0 -1.5 -1.5H4.5"), listOf(Triple(16.5f, 14f, 1.1f)))

    /** 轉入 */
    val IcArrowIn = accent("IcArrowIn", listOf("M9.5 12h11", "M16.5 8l4 4-4 4", "M14 4.5H6a1.5 1.5 0 0 0 -1.5 1.5v12A1.5 1.5 0 0 0 6 19.5h8"), listOf())

    /** 內容 */
    val IcStore = accent("IcStore", listOf("M4 9.5l1.5-5h13l1.5 5", "M4 9.5a2.7 2.7 0 0 0 5.3 0a2.7 2.7 0 0 0 5.4 0a2.7 2.7 0 0 0 5.3 0", "M5.5 11.5v8h13v-8", "M10 19.5v-4.5h4v4.5"), listOf())

    /** 說明 */
    val IcLines = accent("IcLines", listOf("M5 7h14", "M5 11h14", "M5 15h9"), listOf())

    /** 手續費 */
    val IcPercent = accent("IcPercent", listOf("M6 18L18 6"), listOf(Triple(7.5f, 7.5f, 1.8f), Triple(16.5f, 16.5f, 1.8f)))

    /** 分帳 */
    val IcPeople = accent("IcPeople", listOf("M9 11a3 3 0 1 0 0-6a3 3 0 1 0 0 6", "M3.5 19.5a5.5 5.5 0 0 1 11 0", "M16 6a2.6 2.6 0 1 1 0 5.2", "M17.2 14a4.7 4.7 0 0 1 3.3 4.5"), listOf())

    /** 對象 */
    val IcPerson = accent("IcPerson", listOf("M12 11a3.5 3.5 0 1 0 0-7a3.5 3.5 0 1 0 0 7", "M5 20a7 7 0 0 1 14 0"), listOf())

    /** 現金 */
    val IcCash = accent("IcCash", listOf("M3.5 7h17v10h-17z", "M12 9.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5"), listOf(Triple(6.5f, 12f, 0.9f), Triple(17.5f, 12f, 0.9f)))

    /** 銀行 */
    val IcBank = accent("IcBank", listOf("M3.5 9.5L12 4.5l8.5 5", "M6.5 11.5v6", "M10 11.5v6", "M14 11.5v6", "M17.5 11.5v6", "M4 19.5h16"), listOf())

    /** 信用卡 */
    val IcCard = accent("IcCard", listOf("M3.5 6.5h17v11h-17z", "M3.5 10h17", "M6.5 14.5h4"), listOf())

    /** 電子支付 */
    val IcPhone = accent("IcPhone", listOf("M7.5 3.5h9a1 1 0 0 1 1 1v15a1 1 0 0 1 -1 1h-9a1 1 0 0 1 -1-1v-15a1 1 0 0 1 1-1z", "M10.5 17.5h3"), listOf())

    /** 票證 */
    val IcTap = accent("IcTap", listOf("M4 6.5h16v11H4z", "M9.5 10.2a2.6 2.6 0 0 1 0 3.6", "M12.2 8.8a4.6 4.6 0 0 1 0 6.4", "M14.9 7.6a6.6 6.6 0 0 1 0 8.8"), listOf())

    /** 證券 */
    val IcChart = accent("IcChart", listOf("M4 17l5-5 3.5 3 7-7.5", "M15 7.5h4.5V12"), listOf())

    /** 飲料 */
    val IcCafe = accent("IcCafe", listOf("M5 9h11v5a4.5 4.5 0 0 1 -4.5 4.5h-2A4.5 4.5 0 0 1 5 14z", "M16 10.5h1.5a2 2 0 0 1 0 4H16", "M9 4.5c-0.8 1 0.8 1.8 0 2.8", "M12.5 4.5c-0.8 1 0.8 1.8 0 2.8"), listOf())

    /** 飲食 */
    val IcFood = accent("IcFood", listOf("M6.5 3.5v6a2 2 0 0 0 4 0v-6", "M8.5 3.5v17", "M16.5 20.5V3.5c-2 1.2-3 3.5-3 6.5v3.5h3"), listOf())

    /** 日用 */
    val IcBasket = accent("IcBasket", listOf("M3.5 9.5h17l-1.8 9a1.5 1.5 0 0 1 -1.5 1.2H6.8a1.5 1.5 0 0 1 -1.5-1.2z", "M8 9.5l3-5", "M16 9.5l-3-5", "M9.5 13v3.5", "M14.5 13v3.5"), listOf())

    /** 交通 */
    val IcBus = accent("IcBus", listOf("M6 4h12a1.5 1.5 0 0 1 1.5 1.5v11a1.5 1.5 0 0 1 -1.5 1.5H6a1.5 1.5 0 0 1 -1.5-1.5v-11A1.5 1.5 0 0 1 6 4z", "M4.5 11.5h15", "M7.5 18v2", "M16.5 18v2"), listOf(Triple(8f, 14.8f, 0.9f), Triple(16f, 14.8f, 0.9f)))

    /** 汽車 */
    val IcCar = accent("IcCar", listOf("M4.5 15.5v-3l2-5h11l2 5v3z", "M4.5 15.5v2.5h2.5v-2.5", "M17 15.5v2.5h2.5v-2.5", "M4.5 12.5h15"), listOf(Triple(8f, 14f, 0.9f), Triple(16f, 14f, 0.9f)))

    /** 加油 */
    val IcFuel = accent("IcFuel", listOf("M5 20.5V5.5a1.5 1.5 0 0 1 1.5-1.5h6a1.5 1.5 0 0 1 1.5 1.5v15", "M4 20.5h11", "M7.5 8h4", "M14 10.5h1.5a1.5 1.5 0 0 1 1.5 1.5v4.5a1.3 1.3 0 0 0 2.6 0V8.5L17 6"), listOf())

    /** 服飾 */
    val IcShirt = accent("IcShirt", listOf("M9 4l-5 2.5 1.5 4 2.5-1v11h8v-11l2.5 1 1.5-4-5-2.5a3 3 0 0 1 -6 0z"), listOf())

    /** 購物 */
    val IcBag = accent("IcBag", listOf("M5 8h14l-1 12.5H6z", "M9 10.5V7a3 3 0 0 1 6 0v3.5"), listOf())

    /** 娛樂 */
    val IcGame = accent("IcGame", listOf("M7.5 8h9a4 4 0 0 1 3.9 4.9l-0.9 3.9a2 2 0 0 1 -3.5 0.8L14 16h-4l-2 1.6a2 2 0 0 1 -3.5-0.8l-0.9-3.9A4 4 0 0 1 7.5 8z", "M8 11v3", "M6.5 12.5h3"), listOf(Triple(15.5f, 11.5f, 0.8f), Triple(17f, 13.5f, 0.8f)))

    /** 訂閱 */
    val IcPlay = accent("IcPlay", listOf("M4 6h16v12H4z", "M10.5 9.5l4 2.5-4 2.5z"), listOf())

    /** 居住 */
    val IcHome = accent("IcHome", listOf("M4 11l8-6.5 8 6.5", "M6 9.5v10h12v-10", "M10 19.5v-5h4v5"), listOf())

    /** 房租 */
    val IcBuilding = accent("IcBuilding", listOf("M5 20.5V5a1 1 0 0 1 1-1h8a1 1 0 0 1 1 1v15.5", "M15 9.5h3a1 1 0 0 1 1 1v10", "M3.5 20.5h17"), listOf(Triple(8.5f, 7.8f, 0.7f), Triple(11.5f, 7.8f, 0.7f), Triple(8.5f, 11.2f, 0.7f), Triple(11.5f, 11.2f, 0.7f), Triple(8.5f, 14.6f, 0.7f), Triple(11.5f, 14.6f, 0.7f)))

    /** 電信 */
    val IcWifi = accent("IcWifi", listOf("M4 9.5a11.5 11.5 0 0 1 16 0", "M7 12.8a7.2 7.2 0 0 1 10 0", "M9.8 16a3.2 3.2 0 0 1 4.4 0"), listOf(Triple(12f, 19f, 1.1f)))

    /** 醫療 */
    val IcMedical = accent("IcMedical", listOf("M4.5 6.5a2 2 0 0 1 2-2h11a2 2 0 0 1 2 2v11a2 2 0 0 1 -2 2h-11a2 2 0 0 1 -2-2z", "M12 8.5v7", "M8.5 12h7"), listOf())

    /** 運動 */
    val IcDumbbell = accent("IcDumbbell", listOf("M6.5 12h11", "M6 8.5v7", "M18 8.5v7", "M3.5 10v4", "M20.5 10v4"), listOf())

    /** 保險 */
    val IcShield = accent("IcShield", listOf("M12 3.8l7 2.7v5c0 4.4-3 7.6-7 9-4-1.4-7-4.6-7-9v-5z", "M9.3 12l1.9 1.9 3.6-3.6"), listOf())

    /** 學習 */
    val IcBook = accent("IcBook", listOf("M12 6.5c-2-1.5-5-2-8-1.5v13c3-0.5 6 0 8 1.5 2-1.5 5-2 8-1.5V5c-3-0.5-6 0-8 1.5z", "M12 6.5v13"), listOf())

    /** 人情 */
    val IcGift = accent("IcGift", listOf("M4.5 9h15v3h-15z", "M5.5 12v8h13v-8", "M12 9v11", "M12 9c-1.5-3.5-5-4-5-1.5S10 9 12 9c2 0 5 1 5-1.5S13.5 5.5 12 9z"), listOf())

    /** 美容 */
    val IcMirror = accent("IcMirror", listOf("M12 3.8a5.5 5.5 0 1 0 0 11a5.5 5.5 0 1 0 0-11", "M12 14.8v5.7", "M9 20.5h6"), listOf())

    /** 寵物 */
    val IcPaw = accent("IcPaw", listOf("M8 17c0-2.5 1.8-4.5 4-4.5s4 2 4 4.5c0 1.6-1.2 2.5-2.5 2.5-0.6 0-1-0.3-1.5-0.3s-0.9 0.3-1.5 0.3C9.2 19.5 8 18.6 8 17z"), listOf(Triple(6.3f, 10.5f, 1.4f), Triple(9.8f, 7.5f, 1.4f), Triple(14.2f, 7.5f, 1.4f), Triple(17.7f, 10.5f, 1.4f)))

    /** 育兒 */
    val IcSmile = accent("IcSmile", listOf("M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16", "M9 14c1.6 1.5 4.4 1.5 6 0"), listOf(Triple(9.3f, 10f, 0.9f), Triple(14.7f, 10f, 0.9f)))

    /** 旅遊 */
    val IcPlane = accent("IcPlane", listOf("M20.5 4L3.5 11l6.5 2.5 2.5 6.5z", "M10 13.5L20.5 4"), listOf())

    /** 規費 */
    val IcReceipt = accent("IcReceipt", listOf("M6.5 3.8h11v16.4l-1.85-1.2-1.85 1.2-1.8-1.2-1.8 1.2-1.85-1.2-1.85 1.2z", "M9 8.5h6", "M9 12h6", "M9 15.5h3.5"), listOf())

    /** 工作 */
    val IcBriefcase = accent("IcBriefcase", listOf("M4 8.5h16v10.5H4z", "M9 8.5V6.5a1.5 1.5 0 0 1 1.5-1.5h3A1.5 1.5 0 0 1 15 6.5v2", "M4 13h16"), listOf())

    /** 獎金 */
    val IcTrophy = accent("IcTrophy", listOf("M7.5 4.5h9v4a4.5 4.5 0 0 1 -9 0z", "M7.5 6H5a2.5 2.5 0 0 0 2.6 3.4", "M16.5 6H19a2.5 2.5 0 0 1 -2.6 3.4", "M12 13v3.5", "M8.5 20h7", "M9.5 20l0.8-3.5h3.4l0.8 3.5"), listOf())

    /** 退款 */
    val IcRefund = accent("IcRefund", listOf("M9 7.5L5 11.5l4 4", "M5 11.5h9.5a4.5 4.5 0 0 1 0 9H11"), listOf())

    /** 儲蓄 */
    val IcCoins = accent("IcCoins", listOf("M5 8a7 2.5 0 1 0 14 0a7 2.5 0 1 0 -14 0", "M5 8v4c0 1.4 3.1 2.5 7 2.5s7-1.1 7-2.5V8", "M5 12v4c0 1.4 3.1 2.5 7 2.5s7-1.1 7-2.5v-4"), listOf())

    /** 轉帳 */
    val IcSwap = accent("IcSwap", listOf("M4 9h15", "M15.5 5.5L19 9l-3.5 3.5", "M20 15H5", "M8.5 11.5L5 15l3.5 3.5"), listOf())

    /** 其他 */
    val IcDots = accent("IcDots", listOf(), listOf(Triple(6f, 12f, 1.4f), Triple(12f, 12f, 1.4f), Triple(18f, 12f, 1.4f)))

    /** 記帳 */
    val IcPencil = accent("IcPencil", listOf("M14.5 5.5l4 4L9 19H5v-4z", "M12.5 7.5l4 4"), listOf())

    /** 外觀 */
    val IcPalette = accent("IcPalette", listOf("M12 4a8 8 0 0 0 0 16c1.1 0 1.6-0.8 1.6-1.6 0-1.2-1.1-1.5-1.1-2.6 0-0.9 0.7-1.6 1.6-1.6H16a4 4 0 0 0 4-4C20 6.9 16.4 4 12 4z"), listOf(Triple(8f, 11.5f, 1.1f), Triple(10.3f, 7.8f, 1.1f), Triple(14.5f, 7.8f, 1.1f)))

    /** 功能開關 */
    val IcToggle = accent("IcToggle", listOf("M3.5 12a4.5 4.5 0 0 1 4.5-4.5h8a4.5 4.5 0 0 1 0 9H8A4.5 4.5 0 0 1 3.5 12z"), listOf(Triple(16f, 12f, 2.2f)))

    /** 自動記帳 */
    val IcChat = accent("IcChat", listOf("M4.5 6a1.5 1.5 0 0 1 1.5-1.5h12A1.5 1.5 0 0 1 19.5 6v9a1.5 1.5 0 0 1 -1.5 1.5H10l-4 3.5v-3.5H6A1.5 1.5 0 0 1 4.5 15z"), listOf(Triple(8.5f, 10.5f, 0.9f), Triple(12f, 10.5f, 0.9f), Triple(15.5f, 10.5f, 0.9f)))

    /** 安全 */
    val IcLock = accent("IcLock", listOf("M6.5 10.5h11v9.5h-11z", "M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5"), listOf(Triple(12f, 15f, 1.1f)))

    /** 搜尋 */
    val IcSearch = accent("IcSearch", listOf("M10.5 5a5.5 5.5 0 1 0 0 11a5.5 5.5 0 1 0 0-11", "M15 15l4.5 4.5"), listOf())

    /** 地圖 */
    val IcMap = accent("IcMap", listOf("M9 5L4 7v13l5-2 6 2 5-2V5l-5 2z", "M9 5v13", "M15 7v13"), listOf())

    /** 備註 */
    val IcNote = accent("IcNote", listOf("M5.5 4.5h13v10l-5 5h-8z", "M13.5 19.5v-5h5", "M8.5 9h7", "M8.5 12h4"), listOf())
}
