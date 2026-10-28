package com.enwei.moneymanager.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.enwei.moneymanager.ui.screens.AccountEditScreen
import com.enwei.moneymanager.ui.screens.AccountsScreen
import com.enwei.moneymanager.ui.screens.BackupScreen
import com.enwei.moneymanager.ui.screens.CategoriesScreen
import com.enwei.moneymanager.ui.screens.FxScreen
import com.enwei.moneymanager.ui.screens.HoldingScreen
import com.enwei.moneymanager.ui.screens.HomeScreen
import com.enwei.moneymanager.ui.screens.InvestScreen
import com.enwei.moneymanager.ui.screens.InvoiceScreen
import com.enwei.moneymanager.ui.screens.MoreScreen
import com.enwei.moneymanager.ui.screens.NotificationSettingsScreen
import com.enwei.moneymanager.ui.screens.PendingScreen
import com.enwei.moneymanager.ui.screens.RecurringEditScreen
import com.enwei.moneymanager.ui.screens.RecurringScreen
import com.enwei.moneymanager.ui.screens.ReportScreen
import com.enwei.moneymanager.ui.screens.SettingsScreen
import com.enwei.moneymanager.ui.screens.TradeEditScreen
import com.enwei.moneymanager.ui.screens.TxnEditScreen
import com.enwei.moneymanager.ui.screens.TxnListScreen

class MainActivity : ComponentActivity() {
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MoneyTheme {
                val nav = rememberNavController()
                val route by pendingRoute
                LaunchedEffect(route) {
                    route?.takeIf { it != "home" }?.let { nav.navigate(it) }
                    pendingRoute.value = null
                }
                AppScaffold(nav)
            }
        }
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
    Tab("home", "首頁", Icons.Filled.Home),
    Tab("txns", "明細", Icons.AutoMirrored.Filled.ReceiptLong),
    Tab("invest", "投資", Icons.Filled.ShowChart),
    Tab("report", "報表", Icons.Filled.PieChart),
    Tab("more", "更多", Icons.Filled.Menu),
)

@Composable
private fun AppScaffold(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val showBar = tabs.any { it.route == current }
    Scaffold(
        bottomBar = {
            if (showBar) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = current == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { inner ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(bottom = if (showBar) inner.calculateBottomPadding() else 0.dp).consumeWindowInsets(inner)) {
            composable("home") { HomeScreen(nav) }
            composable("txns") { TxnListScreen(nav) }
            composable("invest") { InvestScreen(nav) }
            composable("report") { ReportScreen() }
            composable("more") { MoreScreen(nav) }
            composable(
                "txn/{id}?type={type}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { e -> TxnEditScreen(nav, e.arguments!!.getLong("id"), e.arguments?.getString("type")) }
            composable("accounts") { AccountsScreen(nav) }
            composable("account/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                AccountEditScreen(nav, e.arguments!!.getLong("id"))
            }
            composable("recurring") { RecurringScreen(nav) }
            composable("recurring/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                RecurringEditScreen(nav, e.arguments!!.getLong("id"))
            }
            composable(
                "trade/{id}?market={market}&symbol={symbol}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("market") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("symbol") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { e -> TradeEditScreen(nav, e.arguments!!.getLong("id"), e.arguments?.getString("market"), e.arguments?.getString("symbol")) }
            composable("holding/{market}/{symbol}") { e ->
                HoldingScreen(nav, e.arguments!!.getString("market")!!, e.arguments!!.getString("symbol")!!)
            }
            composable("pending") { PendingScreen(nav) }
            composable("notif") { NotificationSettingsScreen(nav) }
            composable("invoice") { InvoiceScreen(nav) }
            composable("fx") { FxScreen(nav) }
            composable("categories") { CategoriesScreen(nav) }
            composable("backup") { BackupScreen(nav) }
            composable("settings") { SettingsScreen(nav) }
        }
    }
}
