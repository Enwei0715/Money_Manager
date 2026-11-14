package com.enwei.moneymanager.ui

import com.enwei.moneymanager.ui.components.coachTarget
import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.graphics.Color
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.enwei.moneymanager.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.ui.screens.AccountDetailScreen
import com.enwei.moneymanager.ui.screens.AiSettingsScreen
import com.enwei.moneymanager.ui.screens.AiHubScreen
import com.enwei.moneymanager.ui.screens.AccountEditScreen
import com.enwei.moneymanager.ui.screens.AccountOrderScreen
import com.enwei.moneymanager.ui.screens.AccountsTab
import com.enwei.moneymanager.ui.screens.AccountGroupScreen
import com.enwei.moneymanager.ui.screens.AccountSettingsScreen
import com.enwei.moneymanager.ui.screens.CategorySettingsScreen
import com.enwei.moneymanager.ui.screens.SubcategoryScreen
import com.enwei.moneymanager.ui.screens.PasscodeScreen
import com.enwei.moneymanager.ui.screens.BackupScreen
import com.enwei.moneymanager.ui.screens.BudgetScreen
import com.enwei.moneymanager.ui.screens.CategoryStatScreen
import com.enwei.moneymanager.ui.screens.ConfigScreen
import com.enwei.moneymanager.ui.screens.FxScreen
import com.enwei.moneymanager.ui.screens.HoldingScreen
import com.enwei.moneymanager.ui.screens.LockScreen
import com.enwei.moneymanager.ui.screens.MoreScreen
import com.enwei.moneymanager.ui.screens.NotificationSettingsScreen
import com.enwei.moneymanager.ui.screens.PendingScreen
import com.enwei.moneymanager.ui.screens.Period
import com.enwei.moneymanager.ui.screens.RecurringEditScreen
import com.enwei.moneymanager.ui.screens.RecurringScreen
import com.enwei.moneymanager.ui.screens.SearchScreen
import com.enwei.moneymanager.ui.screens.SettingsScreen
import com.enwei.moneymanager.ui.screens.StockFeeScreen
import com.enwei.moneymanager.ui.screens.ReviewScreen
import com.enwei.moneymanager.ui.screens.StockDetailScreen
import com.enwei.moneymanager.ui.screens.StatsTab
import com.enwei.moneymanager.ui.screens.TotalStatsScreen
import com.enwei.moneymanager.ui.screens.TradeEditScreen
import com.enwei.moneymanager.ui.screens.TransactionsTab
import com.enwei.moneymanager.ui.screens.TxnEditScreen

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private val pendingRoute = mutableStateOf<String?>(null)
    private val locked = mutableStateOf(MoneyApp.repo.settings.value.passcodeOn)
    private val stoppedAt = mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 只在第一次建立時處理（切換語言、主題重建畫面時不要再開一次），讀完就清掉
        if (savedInstanceState == null) pendingRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
        intent?.removeExtra(EXTRA_ROUTE)
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val settings by MoneyApp.repo.settings.flow.collectAsStateWithLifecycle()
            // 沒指定幣別的金額（總額、圖表）依基準幣別決定要不要顯示小數
            com.enwei.moneymanager.core.Money.displayBase = settings.baseCurrency
            ColorPrefs.setB = settings.colorSetB
            val dark = isDarkTheme(settings.themeMode)
            androidx.compose.runtime.remember(settings.language) { I18n.init(applicationContext, settings.language) }
            // 狀態列、導覽列的圖示顏色跟著 App 主題（不是系統主題）切換
            // 開了密碼鎖：最近使用的 App 畫面縮圖與截圖不顯示帳務內容
            // 只有使用者選擇「畫面保護」才擋截圖（預設可截圖，方便截圖回報問題）
            LaunchedEffect(settings.passcodeOn, settings.screenProtect) {
                if (settings.passcodeOn && settings.screenProtect == true) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
            // 開了密碼鎖、還沒選過：問一次要不要擋截圖（之後可在 設定 → 密碼鎖下方 更改）
            if (settings.passcodeOn && settings.screenProtect == null && !locked.value) androidx.compose.material3.AlertDialog(
                onDismissRequest = {},
                title = { com.enwei.moneymanager.ui.components.Text("要允許截圖嗎？") },
                text = { com.enwei.moneymanager.ui.components.Text("開啟「畫面保護」後，最近使用的 App 畫面與截圖不會顯示帳務內容，但也無法截圖回報問題。之後可在「設定 → 安全與資料」更改。") },
                confirmButton = { androidx.compose.material3.TextButton(onClick = {
                    MoneyApp.repo.settings.update { it.copy(screenProtect = false) }
                }) { com.enwei.moneymanager.ui.components.Text("允許截圖") } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = {
                    MoneyApp.repo.settings.update { it.copy(screenProtect = true) }
                }) { com.enwei.moneymanager.ui.components.Text("開啟畫面保護") } },
            )
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            MoneyTheme(dark = dark, uiScale = settings.uiScale, preset = settings.themePreset, font = settings.fontChoice,
                textScale = settings.textScale, corner = settings.cornerStyle, tabular = settings.tabularNums, cards = settings.cardStyle, icons = settings.rowStyle == 0) {
                if (locked.value && settings.passcodeOn) {
                    LockScreen(settings.passcodeHash, biometric = settings.biometric) { locked.value = false }
                } else {
                    val nav = rememberNavController()
                    val route by pendingRoute
                    LaunchedEffect(route) {
                        // 小工具、通知開啟的分頁要「切換分頁」，不是疊在目前畫面上，否則底部分頁列會失靈
                        route?.takeIf { it != "home" }?.let { r ->
                            com.enwei.moneymanager.notify.AppNotices.clearFor(this@MainActivity, r)
                            // 通知點進來的交易已經被刪掉：提示後改開交易列表，不開一個空白的編輯畫面
                            val txnId = r.removePrefix("txn/").toLongOrNull()?.takeIf { r.startsWith("txn/") }
                            val gone = txnId != null && MoneyApp.repo.getTxn(txnId) == null
                            if (gone) {
                                android.widget.Toast.makeText(this@MainActivity, tr("這筆交易已經刪除"), android.widget.Toast.LENGTH_SHORT).show()
                                nav.openRoute("txns")
                            } else runCatching { nav.openRoute(r) }.onFailure { nav.openRoute("txns") }
                        }
                        pendingRoute.value = null
                    }
                    AppScaffold(nav)
                    // 第一次開啟：新手導覽
                    com.enwei.moneymanager.ui.components.Onboarding.Host { r -> runCatching { nav.navigate(r) } }
                    // 聚光燈導覽（新手導覽最後一步之後開始）
                    com.enwei.moneymanager.ui.components.CoachOverlay { r -> runCatching { nav.openRoute(r) } }
                    // 開啟 App 時檢查新版，有的話跳出更新視窗（可略過這版）
                    com.enwei.moneymanager.ui.screens.UpdatePrompt()
                    CrashReportDialog()
                }
            }
        }
    }

    /** 開檔案選擇器、系統設定等由 App 自己開啟的畫面，回來時不重新上鎖。 */
    private var launchedExternal = false

    @Deprecated("Deprecated in Java")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        launchedExternal = true
        @Suppress("DEPRECATION")
        super.startActivityForResult(intent, requestCode, options)
    }

    /** 對話框關掉、回到主畫面時：主畫面沒有正在輸入的欄位就把鍵盤收起來（Compose 對話框關掉後鍵盤常會留著）。 */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        window.decorView.postDelayed({
            // 主畫面有欄位正在輸入（Compose 的輸入框會讓畫面回報為文字編輯器）就不收
            val editing = currentFocus?.onCheckIsTextEditor() == true
            if (!editing) getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        }, 120)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) stoppedAt.longValue = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        val s = MoneyApp.repo.settings.value
        val away = System.currentTimeMillis() - stoppedAt.longValue
        if (stoppedAt.longValue > 0 && s.passcodeOn && !launchedExternal && away >= s.lockDelaySec * 1000L) locked.value = true
        launchedExternal = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingRoute.value = intent.getStringExtra(EXTRA_ROUTE)
        intent.removeExtra(EXTRA_ROUTE)
        setIntent(intent)
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}

/** 從狀態列、通知打開某個分頁裡的子頁時，先記下要切到哪一頁，再切換底部分頁（不另外疊一層畫面）。 */
object TabRequest {
    var accountsSection by androidx.compose.runtime.mutableStateOf<Int?>(null)
    var advisorSub by androidx.compose.runtime.mutableStateOf<Int?>(null)
    var txnsTab by androidx.compose.runtime.mutableStateOf<Int?>(null)
    /** 要帶進「問 AI」輸入框的問題（例如從推薦追蹤請 AI 檢討） */
    var chatQuestion by androidx.compose.runtime.mutableStateOf<String?>(null)
    /** 帶過去的問題要不要直接送出 */
    var chatAutoSend = false
    /** 打開統計頁時要切到哪個檢視（1＝分析） */
    var statsView by androidx.compose.runtime.mutableStateOf<Int?>(null)
}

/** 打開路由：「advisor」「advisor:子頁」切到帳戶→助理，「review」切到交易→檢討，其他分頁用切換、其餘畫面照常開啟。 */
/** 給其他檔案用：切到分頁或打開頁面。 */
fun openRouteFrom(nav: NavController, r: String) = nav.openRoute(r)

fun NavController.openRoute(r: String) {
    when {
        r == "advisor" || r.startsWith("advisor:") -> {
            TabRequest.advisorSub = r.substringAfter(':', "").toIntOrNull()
            openTab("assistant")
        }
        r == "review" -> { TabRequest.statsView = 1; openTab("stats") }
        tabs.any { it.route == r } -> openTab(r)
        // 小工具的「股票」：切到資產分頁的股票區塊，不另外疊一層
        r == "invest" -> { TabRequest.accountsSection = 1; openTab("accounts") }
        // 從通知或小工具打開的設定類頁面：先回到工具分頁首頁再開，按返回回到工具，不會回到不相干的舊畫面
        r.substringBefore('?').substringBefore('/') in TOOL_OWNED -> { openTab("more"); popBackStack("more", inclusive = false); navigate(r) }
        else -> navigate(r)
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

/** 屬於「工具」分頁的頁面（從通知、小工具打開時先回到工具首頁） */
private val TOOL_OWNED = setOf("settings", "backup", "pending", "config", "notif", "budget", "recurring", "ai", "ai-hub")

/** 編輯畫面：不顯示底部分頁列（避免按到分頁丟掉正在輸入的內容） */
private val EDITOR_PREFIXES = listOf("txn/", "account/", "trade/", "recurring/{", "search")

private val tabs = listOf(
    Tab("txns", "交易", com.enwei.moneymanager.ui.components.LineIcons.IcLedger),
    Tab("stats", "統計", com.enwei.moneymanager.ui.components.LineIcons.ToolBars),
    Tab("accounts", "資產", com.enwei.moneymanager.ui.components.LineIcons.IcWallet),
    // 股票助理獨立成一個分頁（不再放在帳戶裡）；股票功能關閉時不顯示
    Tab("assistant", "投資", com.enwei.moneymanager.ui.components.LineIcons.ToolAi),
    Tab("more", "工具", com.enwei.moneymanager.ui.components.LineIcons.IcGrid),
)

@Composable
private fun BottomTabs(current: String?, onSelect: (String) -> Unit) = ChromeScale {
    Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        com.enwei.moneymanager.ui.components.LineDivider()
        val stocksOn = MoneyApp.repo.settings.flow.collectAsStateWithLifecycle().value.featureStocks
        Row(Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 720.dp).navigationBarsPadding().height(56.dp)) {
            tabs.filter { it.route != "assistant" || stocksOn }.forEach { t ->
                val sel = current == t.route
                val color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier.weight(1f).height(56.dp).coachTarget("tab.${t.route}").clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                    ) { onSelect(t.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(50))
                            .padding(horizontal = 16.dp, vertical = 3.dp),
                    ) { Icon(t.icon, t.label, tint = color, modifier = Modifier.size(24.dp)) }
                    Text(t.label, color = color, fontSize = AppText.label, maxLines = 1,
                        fontWeight = if (sel) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal)
                }
            }
        }
    }
}

/**
 * 切換底部分頁。「交易」是起始頁：直接退回起始頁，不能用 restoreState——
 * 其他分頁的狀態是存在起始頁底下的，還原會把剛離開的分頁（例如統計）又帶回來，看起來就像按了沒反應。
 */
internal fun NavController.openTab(route: String) {
    // 切回「交易」時保留原本的位置；已經在交易頁再點一次「交易」才回到最新的交易
    if (route == "txns" && currentDestination?.route == "txns") com.enwei.moneymanager.ui.screens.TxnScroll.toTop = true
    val start = graph.findStartDestination()
    if (route == start.route) {
        if (!popBackStack(start.id, inclusive = false)) navigate(route) { launchSingleTop = true }
        return
    }
    // 已經在這個分頁（含它的子畫面）再點一次：回到分頁首頁；
    // 從別的分頁切回來：回到上次看的畫面（例如工具→預算 切走再回來仍在預算，按返回回工具）
    // 這個分頁的首頁目前在返回堆疊裡＝正在這個分頁（含子畫面）；別的分頁的畫面是存起來的，不在堆疊裡
    val again = runCatching { getBackStackEntry(route) }.isSuccess
    navigate(route) {
        popUpTo(start.id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    if (again) popBackStack(route, inclusive = false)
}

@Composable
private fun AppScaffold(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    // 只有分頁的最上層才顯示底部分頁列；從其他畫面疊上來的（例如記帳時開「帳戶」）不顯示，避免按分頁把正在編輯的內容丟掉
    // 鍵盤打開時收起底部分頁列（否則輸入框和鍵盤之間會留一大塊空白）
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    val imeOpen = WindowInsets.isImeVisible
    // 底部分頁列：除了編輯畫面（記帳、帳戶、交易、週期交易編輯、搜尋，按分頁會丟掉正在輸入的內容）以外，所有畫面都保留；
    // 分頁標示停在最後一個進過的分頁
    val isTab = tabs.any { it.route == current }
    val editing = current != null && EDITOR_PREFIXES.any { current.startsWith(it) }
    var lastTab by remember { mutableStateOf("txns") }
    androidx.compose.runtime.SideEffect { if (isTab && current != null && lastTab != current) lastTab = current }
    // 從其他分頁（按分頁列或返回鍵）回到「交易」：每日清單回到最新的交易；從記帳、明細返回則保留位置
    val prevRoute = remember { arrayOf<String?>(null) }
    if (current != prevRoute[0]) {
        prevRoute[0] = current
    }
    val showBar = !imeOpen && !editing
    // 換頁時底部分頁列一定出現
    androidx.compose.runtime.LaunchedEffect(current) {
        com.enwei.moneymanager.ui.components.Chrome.reveal()
    }
    val barHidden = com.enwei.moneymanager.ui.components.Chrome.bottomHidden
    val tabShown = if (isTab) current else lastTab
    val startScreen = remember { MoneyApp.repo.settings.value.startScreen }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // 外層只負責底部分頁列；狀態列與導覽列的留白交給各頁自己的標題列處理，才會依手機實際高度自動調整
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { Column {
            // 刪除後的「已刪除・復原」提示，放在分頁列上方
            com.enwei.moneymanager.ui.components.UndoBar()
            if (showBar) Column {
            androidx.compose.animation.AnimatedVisibility(
                !barHidden,
                enter = androidx.compose.animation.expandVertically(expandFrom = androidx.compose.ui.Alignment.Top),
                exit = androidx.compose.animation.shrinkVertically(shrinkTowards = androidx.compose.ui.Alignment.Top),
            ) {
                Column {
                    // AI 狀態列：進行中只留一條細進度線（投資頁上方已有完整進度，不重複佔兩行、不擋內容）；暫停與錯誤才顯示文字
                    com.enwei.moneymanager.ui.components.AiStatusBar(nav, compact = true) { r -> nav.openRoute(r) }
                    BottomTabs(tabShown) { route -> nav.openTab(route) }
                }
            }
            // 分頁列收起時仍保留手機導覽列的高度
            if (barHidden) androidx.compose.foundation.layout.Spacer(Modifier.navigationBarsPadding())
            } else if (!imeOpen && !editing) {
                // 其他畫面（個股、設定…）也保留 AI 狀態列，看得到報告還在跑，不會以為被關掉；
                // 記帳、帳戶等編輯畫面不顯示，避免擠在 App 內鍵盤和儲存按鈕下方
                com.enwei.moneymanager.ui.components.AiStatusBar(nav, padNav = true, compact = true) { r -> nav.openRoute(r) }
            }
        } },
    ) { inner ->
        NavHost(
            nav, startDestination = "txns",
            // 預設轉場約 0.7 秒太慢，改為快速淡入淡出
            enterTransition = { fadeIn(tween(120)) },
            exitTransition = { fadeOut(tween(80)) },
            popEnterTransition = { fadeIn(tween(120)) },
            popExitTransition = { fadeOut(tween(80)) },
            // 平板或橫向：內容最寬 720dp 並置中，不會被拉得很寬難讀
            modifier = Modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 720.dp)
                .nestedScroll(com.enwei.moneymanager.ui.components.Chrome.watcher).padding(bottom = inner.calculateBottomPadding())
                .consumeWindowInsets(if (showBar) WindowInsets.navigationBars.only(WindowInsetsSides.Bottom) else WindowInsets.navigationBars.only(WindowInsetsSides.Top))
                // 只有狀態列時：狀態列已含導覽列高度，內容不再重複留導覽列的空白
                .then(if (!showBar) Modifier.consumeWindowInsets(PaddingValues(bottom = inner.calculateBottomPadding())) else Modifier)) {
            composable("txns") { TransactionsTab(nav, initialTab = if (startScreen == 1) 1 else 0) }
            composable("stats") { StatsTab(nav) }
            composable("accounts") { AccountsTab(nav, showBack = nav.previousBackStackEntry?.destination?.route.let { it != null && tabs.none { t -> t.route == it } }) }
            composable("invest") { AccountsTab(nav, showBack = true, initialTab = 1) }
            composable("more") { MoreScreen(nav) }
            composable(
                "search?filter={filter}&month={month}",
                arguments = listOf(
                    navArgument("filter") { type = NavType.BoolType; defaultValue = false },
                    // 從交易頁進來時帶入正在看的月份（yyyy-MM），搜尋與篩選就從那個月開始
                    navArgument("month") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { e ->
                SearchScreen(nav, e.arguments!!.getBoolean("filter"),
                    runCatching { java.time.YearMonth.parse(e.arguments!!.getString("month")) }.getOrNull())
            }
            composable("budget") { BudgetScreen(nav) }
            composable("privacy") { com.enwei.moneymanager.ui.screens.PrivacyScreen(nav) }
            composable("report") { com.enwei.moneymanager.ui.screens.ReportScreen(nav) }
            composable("appearance") { com.enwei.moneymanager.ui.screens.AppearanceScreen(nav) }
            composable(
                "spendmap?month={month}",
                // 從交易頁進來時帶入正在看的月份（yyyy-MM），地圖從那個月開始
                arguments = listOf(navArgument("month") { type = NavType.StringType; defaultValue = "" }),
            ) { e ->
                com.enwei.moneymanager.ui.screens.SpendMapScreen(nav,
                    runCatching { java.time.YearMonth.parse(e.arguments!!.getString("month")) }.getOrNull())
            }
            composable("merchants") { com.enwei.moneymanager.ui.screens.MerchantPlacesScreen(nav) }
            composable("setup") { com.enwei.moneymanager.ui.screens.SetupScreen(nav) }
            composable("debts") { com.enwei.moneymanager.ui.screens.DebtsScreen(nav) }
            composable("config?g={g}", arguments = listOf(navArgument("g") { type = NavType.StringType; defaultValue = "" })) { e ->
                ConfigScreen(nav, e.arguments?.getString("g")?.takeIf { it.isNotBlank() })
            }
            composable("ai-hub") { AiHubScreen(nav) }
            // 股票搜尋：名稱或代號
            composable("stocksearch") {
                androidx.compose.material3.Scaffold(topBar = { com.enwei.moneymanager.ui.components.BackTopBar("搜尋股票", { nav.popBackStack() }) }) { pad ->
                    androidx.compose.foundation.layout.Box(Modifier.padding(pad).padding(16.dp)) {
                        com.enwei.moneymanager.ui.components.StockSearch({ s -> nav.navigate("stock/${s.code}") }, maxHeight = 2000.dp)
                    }
                }
            }
            composable("totalstats") { TotalStatsScreen(nav) }
            composable("accountorder") { AccountOrderScreen(nav) }
            composable("acct/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                AccountDetailScreen(nav, e.arguments!!.getLong("id"))
            }
            composable("catstat/{cat}/{kind}/{period}/{anchor}") { e ->
                // 參數不完整（例如捷徑或舊連結）時回上一頁，不閃退
                val a = e.arguments
                val cat = a?.getString("cat")?.toLongOrNull()
                val kind = runCatching { TxnType.valueOf(a?.getString("kind").orEmpty()) }.getOrNull()
                val period = runCatching { Period.valueOf(a?.getString("period").orEmpty()) }.getOrNull()
                val anchor = a?.getString("anchor")?.toLongOrNull()
                if (cat == null || kind == null || period == null || anchor == null) {
                    androidx.compose.runtime.LaunchedEffect(Unit) { nav.popBackStack() }
                } else CategoryStatScreen(nav, cat, kind, period, anchor)
            }
            composable(
                "txn/{id}?type={type}&date={date}&account={account}&to={to}&amount={amount}&split={split}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("date") { type = NavType.LongType; defaultValue = Long.MIN_VALUE },
                    navArgument("account") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("to") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("amount") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("split") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) { e ->
                val a = e.arguments!!
                TxnEditScreen(nav, a.getLong("id"), a.getString("type"), a.getLong("date").takeIf { it != Long.MIN_VALUE }, a.getLong("account").takeIf { it > 0 },
                    a.getLong("to").takeIf { it > 0 }, a.getLong("amount").takeIf { it > 0 }, a.getLong("split").takeIf { it > 0 })
            }
            composable(
                "account/{id}?type={type}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { e ->
                AccountEditScreen(nav, e.arguments!!.getLong("id"), e.arguments?.getString("type"))
            }
            composable("recurring") { RecurringScreen(nav) }
            composable("recurring/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                RecurringEditScreen(nav, e.arguments!!.getLong("id"))
            }
            composable(
                "trade/{id}?market={market}&symbol={symbol}&broker={broker}&lot={lot}&side={side}&pending={pending}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("market") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("symbol") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("broker") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("lot") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("side") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("pending") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) { e ->
                TradeEditScreen(nav, e.arguments!!.getLong("id"), e.arguments?.getString("market"), e.arguments?.getString("symbol"),
                    e.arguments!!.getLong("broker").takeIf { it > 0 }, e.arguments!!.getLong("lot").takeIf { it > 0 }, e.arguments?.getString("side"),
                    e.arguments!!.getLong("pending").takeIf { it > 0 })
            }
            composable("holding/{market}/{symbol}") { e ->
                HoldingScreen(nav, e.arguments!!.getString("market")!!, e.arguments!!.getString("symbol")!!)
            }
            composable("pending") { PendingScreen(nav) }
            composable("notif") { NotificationSettingsScreen(nav) }
            composable("notices") { com.enwei.moneymanager.ui.screens.NoticeSettingsScreen(nav) }
            composable("ai") { AiSettingsScreen(nav) }
            composable("fx") { FxScreen(nav) }
            composable(
                "categories?kind={kind}",
                arguments = listOf(navArgument("kind") { type = NavType.StringType; defaultValue = "EXPENSE" }),
            ) { e -> CategorySettingsScreen(nav, TxnType.valueOf(e.arguments!!.getString("kind")!!)) }
            composable("subcats/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                SubcategoryScreen(nav, e.arguments!!.getLong("id"))
            }
            composable("passcode") { PasscodeScreen(nav) }
            composable("accountgroups") { AccountGroupScreen(nav) }
            composable("accountsettings") { AccountSettingsScreen(nav) }
            composable("backup") { BackupScreen(nav) }
            composable("settings") { SettingsScreen(nav) }
            composable("stockfees") { StockFeeScreen(nav) }
            composable("review") { ReviewScreen(nav) }
            composable("advisor") { com.enwei.moneymanager.ui.screens.AssistantScreen(nav, showBack = true) }
            composable("assistant") { com.enwei.moneymanager.ui.screens.AssistantScreen(nav) }
            composable("stock/{symbol}") { e -> StockDetailScreen(nav, e.arguments?.getString("symbol").orEmpty()) }
        }
    }
}

/** 上次閃退的原因：開 App 時顯示一次，可以複製下來回報。 */
@Composable
private fun CrashReportDialog() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val file = remember { java.io.File(ctx.filesDir, MoneyApp.CRASH_FILE) }
    var text by remember { mutableStateOf(runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()) }
    val t = text ?: return
    // 關掉後改名保留一份，之後「設定 → 回報問題」仍可附上
    fun dismiss() { runCatching { file.renameTo(java.io.File(ctx.filesDir, "last_crash_prev.txt")) }; text = null }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = ::dismiss,
        title = { Text("上次 App 意外關閉") },
        text = {
            androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("錯誤紀錄", fontSize = AppText.secondary)
                Text(t, fontSize = AppText.badge, lineHeight = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", t))
                dismiss()
            }) { Text("複製") }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                androidx.compose.material3.TextButton(onClick = { com.enwei.moneymanager.ui.screens.shareProblemReport(ctx); dismiss() }) { Text("回報") }
                androidx.compose.material3.TextButton(onClick = ::dismiss) { Text("關閉") }
            }
        },
    )
}
