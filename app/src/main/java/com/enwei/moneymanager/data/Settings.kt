package com.enwei.moneymanager.data

import android.content.Context
import android.content.SharedPreferences
import com.enwei.moneymanager.core.FeeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val baseCurrency: String = "TWD",
    val defaultAccountId: Long? = null,
    val notifAutoConfirm: Boolean = false,
    val notifPackages: Set<String> = emptySet(),
    val invoiceAccountId: Long? = null,
    val feeDiscount: Double = 1.0,
    val minFee: Double = 20.0,
    val usFee: Double = 0.0,
    val githubToken: String = "",
    val lastFxUpdate: Long = 0,
    val lastQuoteUpdate: Long = 0,
) {
    val feeSettings get() = FeeSettings(feeDiscount, minFee, usFee)
}

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val flow: StateFlow<AppSettings> = state.asStateFlow()
    val value: AppSettings get() = state.value

    private fun read() = AppSettings(
        baseCurrency = prefs.getString("baseCurrency", "TWD")!!,
        defaultAccountId = prefs.getLong("defaultAccountId", -1).takeIf { it > 0 },
        notifAutoConfirm = prefs.getBoolean("notifAutoConfirm", false),
        notifPackages = prefs.getStringSet("notifPackages", emptySet())!!.toSet(),
        invoiceAccountId = prefs.getLong("invoiceAccountId", -1).takeIf { it > 0 },
        feeDiscount = prefs.getFloat("feeDiscount", 1f).toDouble(),
        minFee = prefs.getFloat("minFee", 20f).toDouble(),
        usFee = prefs.getFloat("usFee", 0f).toDouble(),
        githubToken = prefs.getString("githubToken", "")!!,
        lastFxUpdate = prefs.getLong("lastFxUpdate", 0),
        lastQuoteUpdate = prefs.getLong("lastQuoteUpdate", 0),
    )

    fun update(block: (AppSettings) -> AppSettings) {
        val s = block(state.value)
        prefs.edit()
            .putString("baseCurrency", s.baseCurrency)
            .putLong("defaultAccountId", s.defaultAccountId ?: -1)
            .putBoolean("notifAutoConfirm", s.notifAutoConfirm)
            .putStringSet("notifPackages", s.notifPackages)
            .putLong("invoiceAccountId", s.invoiceAccountId ?: -1)
            .putFloat("feeDiscount", s.feeDiscount.toFloat())
            .putFloat("minFee", s.minFee.toFloat())
            .putFloat("usFee", s.usFee.toFloat())
            .putString("githubToken", s.githubToken)
            .putLong("lastFxUpdate", s.lastFxUpdate)
            .putLong("lastQuoteUpdate", s.lastQuoteUpdate)
            .apply()
        state.value = s
    }
}
