package com.enwei.moneymanager.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Text
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
import com.enwei.moneymanager.ui.screens.AccountEditScreen
import com.enwei.moneymanager.ui.screens.AccountOrderScreen
import com.enwei.moneymanager.ui.screens.AccountsTab
import com.enwei.moneymanager.ui.screens.BackupScreen
import com.enwei.moneymanager.ui.screens.BookmarksScreen
import com.enwei.moneymanager.ui.screens.BudgetScreen
import com.enwei.moneymanager.ui.screens.CashCounterScreen
import com.enwei.moneymanager.ui.screens.WalletReaderScreen
import com.enwei.moneymanager.ui.screens.CategoriesScreen
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
import com.enwei.moneymanager.ui.screens.StatsTab
import com.enwei.moneymanager.ui.screens.TotalStatsScreen
import com.enwei.moneymanager.ui.screens.TradeEditScreen
import com.enwei.moneymanager.ui.screens.TransactionsTab
import com.enwei.moneymanager.ui.screens.TxnEditScreen

class MainActivity : ComponentActivity() {
    private val pendingRoute = mutableStateOf<String?>(null)
    private val locked = mutableStateOf(MoneyApp.repo.settings.value.passcodeOn)
    private val stoppedAt = mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val settings by MoneyApp.repo.settings.flow.collectAsStateWithLifecycle()
            ColorPrefs.setB = settings.colorSetB
            MoneyTheme {
                if (locked.value && settings.passcodeOn) {
                    LockScreen(settings.passcodeHash) { locked.value = false }
                } else {
                    val nav = rememberNavController()
                    val route by pendingRoute
                    LaunchedEffect(route) {
                        route?.takeIf { it != "home" && it != "txns" }?.let { nav.navigate(it) }
                        pendingRoute.value = null
                    }
                    AppScaffold(nav)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt.longValue = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        // 離開超過 30 秒再回來需要重新輸入密碼（開檔案選擇器等短暫跳出不會鎖定）
        if (stoppedAt.longValue > 0 && System.currentTimeMillis() - stoppedAt.longValue > 30_000 &&
            MoneyApp.repo.settings.value.passcodeOn
        ) locked.value = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingRoute.value = intent.getStringExtra(EXTRA_ROUTE)
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
    Tab("more", "更多", Icons.Filled.MoreHoriz),
)

@Composable
private fun BottomTabs(current: String?, onSelect: (String) -> Unit) {
    Column(Modifier.background(BarBackground)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(60.dp)) {
            tabs.forEach { t ->
                val sel = current == t.route
                val color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier.weight(1f).height(60.dp).clickable(
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

@Composable
private fun AppScaffold(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val showBar = tabs.any { it.route == current }
    val startScreen = remember { MoneyApp.repo.settings.value.startScreen }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // 外層只負責底部分頁列；狀態列與導覽列的留白交給各頁自己的標題列處理，才會依手機實際高度自動調整
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) BottomTabs(current) { route ->
                nav.navigate(route) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
    ) { inner ->
        NavHost(nav, startDestination = "txns", modifier = Modifier.padding(bottom = if (showBar) inner.calculateBottomPadding() else 0.dp)
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
            composable("cashcount/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                CashCounterScreen(nav, e.arguments!!.getLong("id"))
            }
            composable("bookmarks") { BookmarksScreen(nav) }
            composable("config") { ConfigScreen(nav) }
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
                "txn/{id}?type={type}&bookmark={bookmark}&date={date}&account={account}&to={to}&amount={amount}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("bookmark") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("date") { type = NavType.LongType; defaultValue = Long.MIN_VALUE },
                    navArgument("account") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("to") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("amount") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) { e ->
                val a = e.arguments!!
                TxnEditScreen(nav, a.getLong("id"), a.getString("type"), a.getLong("bookmark"), a.getLong("date").takeIf { it != Long.MIN_VALUE }, a.getLong("account").takeIf { it > 0 },
                    a.getLong("to").takeIf { it > 0 }, a.getLong("amount").takeIf { it > 0 })
            }
            composable("account/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                AccountEditScreen(nav, e.arguments!!.getLong("id"))
            }
            composable("recurring") { RecurringScreen(nav) }
            composable("recurring/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                RecurringEditScreen(nav, e.arguments!!.getLong("id"))
            }
            composable(
                "trade/{id}?market={market}&symbol={symbol}&broker={broker}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("market") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("symbol") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("broker") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) { e ->
                TradeEditScreen(nav, e.arguments!!.getLong("id"), e.arguments?.getString("market"), e.arguments?.getString("symbol"),
                    e.arguments!!.getLong("broker").takeIf { it > 0 })
            }
            composable("holding/{market}/{symbol}") { e ->
                HoldingScreen(nav, e.arguments!!.getString("market")!!, e.arguments!!.getString("symbol")!!)
            }
            composable("pending") { PendingScreen(nav) }
            composable("notif") { NotificationSettingsScreen(nav) }
            composable("fx") { FxScreen(nav) }
            composable("categories") { CategoriesScreen(nav) }
            composable("backup") { BackupScreen(nav) }
            composable("settings") { SettingsScreen(nav) }
        }
    }
}
