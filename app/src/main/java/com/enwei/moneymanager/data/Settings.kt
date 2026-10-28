package com.enwei.moneymanager.data

import android.content.Context
import android.content.SharedPreferences
import com.enwei.moneymanager.core.FeeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** 書籤：常用交易範本。 */
@Serializable
data class Bookmark(
    val id: Long,
    val type: TxnType,
    val amount: Long,
    val currency: String,
    val accountId: Long?,
    val toAccountId: Long?,
    val categoryId: Long?,
    val merchant: String?,
    val note: String?,
)

/** 交易頁「備註」分頁的備忘。 */
@Serializable
data class Memo(val id: Long, val date: Long, val text: String)

data class AppSettings(
    val baseCurrency: String = "TWD",
    val subCurrency: String = "USD",
    val defaultAccountId: Long? = null,
    val notifAutoConfirm: Boolean = false,
    val notifPackages: Set<String> = emptySet(),
    val feeDiscount: Double = 1.0,
    val minFee: Double = 20.0,
    val usFee: Double = 0.0,
    val githubToken: String = "",
    val lastFxUpdate: Long = 0,
    val lastQuoteUpdate: Long = 0,
    /** 0 = 每日、1 = 日曆 */
    val startScreen: Int = 0,
    val monthStartDay: Int = 1,
    /** java.time.DayOfWeek 的值，7 = 週日 */
    val weekStartDay: Int = 7,
    /** false：收入藍支出紅；true：收入紅支出藍 */
    val colorSetB: Boolean = false,
    val showTime: Boolean = true,
    val autocomplete: Boolean = true,
    /** true：先選分類再輸入金額 */
    val inputFromCategory: Boolean = false,
    val passcodeHash: String = "",
    val reminderEnabled: Boolean = false,
    val reminderMinutes: Int = 21 * 60,
    val bookmarks: List<Bookmark> = emptyList(),
    /** 上期結轉：把期初淨額當作收入顯示在每月第一天 */
    val carryOver: Boolean = true,
    val memos: List<Memo> = emptyList(),
    /** 每月預算：key 為分類 id，0 代表總預算；值為最小單位金額 */
    val budgets: Map<Long, Long> = emptyMap(),
    /** 通知來源 App → 記入的帳戶 */
    val notifAccountMap: Map<String, Long> = emptyMap(),
) {
    val feeSettings get() = FeeSettings(feeDiscount, minFee, usFee)
    val passcodeOn get() = passcodeHash.isNotEmpty()
}

fun hashPasscode(pin: String): String =
    MessageDigest.getInstance("SHA-256").digest("mm-salt:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val bookmarkSerializer = ListSerializer(Bookmark.serializer())
    private val memoSerializer = ListSerializer(Memo.serializer())
    private val budgetSerializer = MapSerializer(Long.serializer(), Long.serializer())
    private val pkgMapSerializer = MapSerializer(String.serializer(), Long.serializer())
    private val state = MutableStateFlow(read())
    val flow: StateFlow<AppSettings> = state.asStateFlow()
    val value: AppSettings get() = state.value

    private fun read() = AppSettings(
        baseCurrency = prefs.getString("baseCurrency", "TWD")!!,
        subCurrency = prefs.getString("subCurrency", "USD")!!,
        defaultAccountId = prefs.getLong("defaultAccountId", -1).takeIf { it > 0 },
        notifAutoConfirm = prefs.getBoolean("notifAutoConfirm", false),
        notifPackages = prefs.getStringSet("notifPackages", emptySet())!!.toSet(),
        feeDiscount = prefs.getFloat("feeDiscount", 1f).toDouble(),
        minFee = prefs.getFloat("minFee", 20f).toDouble(),
        usFee = prefs.getFloat("usFee", 0f).toDouble(),
        githubToken = prefs.getString("githubToken", "")!!,
        lastFxUpdate = prefs.getLong("lastFxUpdate", 0),
        lastQuoteUpdate = prefs.getLong("lastQuoteUpdate", 0),
        startScreen = prefs.getInt("startScreen", 0),
        monthStartDay = prefs.getInt("monthStartDay", 1),
        weekStartDay = prefs.getInt("weekStartDay", 7),
        colorSetB = prefs.getBoolean("colorSetB", false),
        showTime = prefs.getBoolean("showTime", true),
        autocomplete = prefs.getBoolean("autocomplete", true),
        inputFromCategory = prefs.getBoolean("inputFromCategory", false),
        passcodeHash = prefs.getString("passcodeHash", "")!!,
        reminderEnabled = prefs.getBoolean("reminderEnabled", false),
        reminderMinutes = prefs.getInt("reminderMinutes", 21 * 60),
        bookmarks = runCatching { json.decodeFromString(bookmarkSerializer, prefs.getString("bookmarks", "[]")!!) }.getOrDefault(emptyList()),
        carryOver = prefs.getBoolean("carryOver", true),
        memos = runCatching { json.decodeFromString(memoSerializer, prefs.getString("memos", "[]")!!) }.getOrDefault(emptyList()),
        budgets = runCatching { json.decodeFromString(budgetSerializer, prefs.getString("budgets", "{}")!!) }.getOrDefault(emptyMap()),
        notifAccountMap = runCatching { json.decodeFromString(pkgMapSerializer, prefs.getString("notifAccountMap", "{}")!!) }.getOrDefault(emptyMap()),
    )

    fun update(block: (AppSettings) -> AppSettings) {
        val s = block(state.value)
        prefs.edit()
            .putString("baseCurrency", s.baseCurrency)
            .putString("subCurrency", s.subCurrency)
            .putLong("defaultAccountId", s.defaultAccountId ?: -1)
            .putBoolean("notifAutoConfirm", s.notifAutoConfirm)
            .putStringSet("notifPackages", s.notifPackages)
            .putFloat("feeDiscount", s.feeDiscount.toFloat())
            .putFloat("minFee", s.minFee.toFloat())
            .putFloat("usFee", s.usFee.toFloat())
            .putString("githubToken", s.githubToken)
            .putLong("lastFxUpdate", s.lastFxUpdate)
            .putLong("lastQuoteUpdate", s.lastQuoteUpdate)
            .putInt("startScreen", s.startScreen)
            .putInt("monthStartDay", s.monthStartDay)
            .putInt("weekStartDay", s.weekStartDay)
            .putBoolean("colorSetB", s.colorSetB)
            .putBoolean("showTime", s.showTime)
            .putBoolean("autocomplete", s.autocomplete)
            .putBoolean("inputFromCategory", s.inputFromCategory)
            .putString("passcodeHash", s.passcodeHash)
            .putBoolean("reminderEnabled", s.reminderEnabled)
            .putInt("reminderMinutes", s.reminderMinutes)
            .putString("bookmarks", json.encodeToString(bookmarkSerializer, s.bookmarks))
            .putBoolean("carryOver", s.carryOver)
            .putString("memos", json.encodeToString(memoSerializer, s.memos))
            .putString("budgets", json.encodeToString(budgetSerializer, s.budgets))
            .putString("notifAccountMap", json.encodeToString(pkgMapSerializer, s.notifAccountMap))
            .apply()
        state.value = s
    }
}
