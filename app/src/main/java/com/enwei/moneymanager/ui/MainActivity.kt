package com.enwei.moneymanager.ui

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.enwei.moneymanager.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
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
import com.enwei.moneymanager.ui.screens.WalletReaderScreen
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
import com.enwei.moneymanager.ui.screens.ToolSettingsScreen

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
            ColorPrefs.setB = settings.colorSetB
            val dark = isDarkTheme(settings.themeMode)
            androidx.compose.runtime.remember(settings.language) { I18n.init(applicationContext, settings.language) }
            // 狀態列、導覽列的圖示顏色跟著 App 主題（不是系統主題）切換
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            MoneyTheme(dark = dark, uiScale = settings.uiScale) {
                if (locked.value && settings.passcodeOn) {
                    LockScreen(settings.passcodeHash, biometric = settings.biometric) { locked.value = false }
                } else {
                    val nav = rememberNavController()
                    val route by pendingRoute
                    LaunchedEffect(route) {
                        // 小工具、通知開啟的分頁要「切換分頁」，不是疊在目前畫面上，否則底部分頁列會失靈
                        route?.takeIf { it != "home" }?.let { r -> if (tabs.any { it.route == r }) nav.openTab(r) else nav.navigate(r) }
                        pendingRoute.value = null
                    }
                    AppScaffold(nav)
                    // 開啟 App 時檢查新版，有的話跳出更新視窗（可略過這版）
                    com.enwei.moneymanager.ui.screens.UpdatePrompt()
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

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("txns", "交易", Icons.AutoMirrored.Outlined.MenuBook),
    Tab("stats", "統計", Icons.Outlined.BarChart),
    Tab("accounts", "帳戶", Icons.Outlined.AccountBalanceWallet),
    Tab("more", "工具", Icons.Filled.MoreHoriz),
)

@Composable
private fun BottomTabs(current: String?, onSelect: (String) -> Unit) {
    Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(56.dp)) {
            tabs.forEach { t ->
                val sel = current == t.route
                val color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier.weight(1f).height(56.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                    ) { onSelect(t.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(t.icon, t.label, tint = color, modifier = Modifier.size(26.dp))
                    Text(t.label, color = color, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * 切換底部分頁。「交易」是起始頁：直接退回起始頁，不能用 restoreState——
 * 其他分頁的狀態是存在起始頁底下的，還原會把剛離開的分頁（例如統計）又帶回來，看起來就像按了沒反應。
 */
private fun NavHostController.openTab(route: String) {
    val start = graph.findStartDestination()
    if (route == start.route) {
        if (!popBackStack(start.id, inclusive = false)) navigate(route) { launchSingleTop = true }
        return
    }
    navigate(route) {
        popUpTo(start.id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppScaffold(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    // 只有分頁的最上層才顯示底部分頁列；從其他畫面疊上來的（例如記帳時開「帳戶」）不顯示，避免按分頁把正在編輯的內容丟掉
    val showBar = tabs.any { it.route == current } &&
        nav.previousBackStackEntry?.destination?.route.let { prev -> prev == null || tabs.any { it.route == prev } }
    val startScreen = remember { MoneyApp.repo.settings.value.startScreen }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // 外層只負責底部分頁列；狀態列與導覽列的留白交給各頁自己的標題列處理，才會依手機實際高度自動調整
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) BottomTabs(current) { route -> nav.openTab(route) }
        },
    ) { inner ->
        NavHost(
            nav, startDestination = "txns",
            // 預設轉場約 0.7 秒太慢，改為快速淡入淡出
            enterTransition = { fadeIn(tween(120)) },
            exitTransition = { fadeOut(tween(80)) },
            popEnterTransition = { fadeIn(tween(120)) },
            popExitTransition = { fadeOut(tween(80)) },
            modifier = Modifier.padding(bottom = if (showBar) inner.calculateBottomPadding() else 0.dp)
                .consumeWindowInsets(WindowInsets.navigationBars.only(if (showBar) WindowInsetsSides.Bottom else WindowInsetsSides.Top))) {
            composable("txns") { TransactionsTab(nav, initialTab = if (startScreen == 1) 1 else 0) }
            composable("stats") { StatsTab(nav) }
            composable("accounts") { AccountsTab(nav, showBack = nav.previousBackStackEntry?.destination?.route.let { it != null && tabs.none { t -> t.route == it } }) }
            composable("invest") { AccountsTab(nav, showBack = true, initialTab = 1) }
            composable("more") { MoreScreen(nav) }
            composable(
                "search?filter={filter}",
                arguments = listOf(navArgument("filter") { type = NavType.BoolType; defaultValue = false }),
            ) { e -> SearchScreen(nav, e.arguments!!.getBoolean("filter")) }
            composable("budget") { BudgetScreen(nav) }
            composable("walletreader") { WalletReaderScreen(nav) }
            composable("config") { ConfigScreen(nav) }
            composable("tool-order") { ToolSettingsScreen(nav) }
            composable("ai-hub") { AiHubScreen(nav) }
            composable("totalstats") { TotalStatsScreen(nav) }
            composable("accountorder") { AccountOrderScreen(nav) }
            composable("acct/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                AccountDetailScreen(nav, e.arguments!!.getLong("id"))
            }
            composable("catstat/{cat}/{kind}/{period}/{anchor}") { e ->
                val a = e.arguments!!
                CategoryStatScreen(
                    nav, a.getString("cat")!!.toLong(), TxnType.valueOf(a.getString("kind")!!),
                    Period.valueOf(a.getString("period")!!), a.getString("anchor")!!.toLong(),
                )
            }
            composable(
                "txn/{id}?type={type}&date={date}&account={account}&to={to}&amount={amount}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("date") { type = NavType.LongType; defaultValue = Long.MIN_VALUE },
                    navArgument("account") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("to") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("amount") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) { e ->
                val a = e.arguments!!
                TxnEditScreen(nav, a.getLong("id"), a.getString("type"), a.getLong("date").takeIf { it != Long.MIN_VALUE }, a.getLong("account").takeIf { it > 0 },
                    a.getLong("to").takeIf { it > 0 }, a.getLong("amount").takeIf { it > 0 })
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
                "trade/{id}?market={market}&symbol={symbol}&broker={broker}&lot={lot}&side={side}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("market") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("symbol") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("broker") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("lot") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("side") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { e ->
                TradeEditScreen(nav, e.arguments!!.getLong("id"), e.arguments?.getString("market"), e.arguments?.getString("symbol"),
                    e.arguments!!.getLong("broker").takeIf { it > 0 }, e.arguments!!.getLong("lot").takeIf { it > 0 }, e.arguments?.getString("side"))
            }
            composable("holding/{market}/{symbol}") { e ->
                HoldingScreen(nav, e.arguments!!.getString("market")!!, e.arguments!!.getString("symbol")!!)
            }
            composable("pending") { PendingScreen(nav) }
            composable("notif") { NotificationSettingsScreen(nav) }
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
            composable("advisor") { AccountsTab(nav, showBack = true, initialTab = 2) }
            composable("stock/{symbol}") { e -> StockDetailScreen(nav, e.arguments?.getString("symbol").orEmpty()) }
        }
    }
}
