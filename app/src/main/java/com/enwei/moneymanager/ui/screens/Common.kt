package com.enwei.moneymanager.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.core.FxConverter
import com.enwei.moneymanager.data.AccountEntity
import com.enwei.moneymanager.data.CategoryEntity
import com.enwei.moneymanager.data.Repository
import java.time.format.DateTimeFormatter

val repo: Repository get() = MoneyApp.repo

val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")
val dateTimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm")

class Lookups(val accounts: List<AccountEntity>, val categories: List<CategoryEntity>, val fx: FxConverter, val base: String) {
    private val accMap = accounts.associateBy { it.id }
    private val catMap = categories.associateBy { it.id }
    fun account(id: Long?) = id?.let { accMap[it] }
    fun category(id: Long?) = id?.let { catMap[it] }
}

@Composable
fun rememberLookups(): Lookups {
    val accounts by repo.accounts.collectAsStateWithLifecycle(emptyList())
    val categories by repo.categories.collectAsStateWithLifecycle(emptyList())
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val fx by repo.fx.collectAsStateWithLifecycle(FxConverter("TWD", emptyMap()))
    return remember(accounts, categories, fx, settings.baseCurrency) { Lookups(accounts, categories, fx, settings.baseCurrency) }
}
