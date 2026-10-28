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
    /** 讀取交易紀錄畫面的錢包 App */
    val screenPackages: Set<String> = emptySet(),
    /** 每個錢包 App 從哪一天開始匯入（epochDay），避免第一次把半年歷史全部匯入 */
    val screenReadSince: Map<String, Long> = emptyMap(),
    /** 現金點鈔：帳戶 id → 面額 → 數量 */
    val cashCounts: Map<Long, Map<Int, Int>> = emptyMap(),
    /** 0 跟隨系統、1 淺色、2 深色 */
    val themeMode: Int = 0,
    /** 介面縮放比例（整體文字、間距、圖示等比例） */
    val uiScale: Float = 0.9f,
    /** 子分類功能 */
    val subcategoryOn: Boolean = true,
    /** 交易頁顯示「新增備註」按鈕 */
    val noteButton: Boolean = true,
    /** 交易頁左右滑動：0 無、1 切換期間 */
    val swipe: Int = 0,
    /** 交易列表顯示說明 */
    val showDescription: Boolean = false,
    /** 通知列常駐「快速記帳」 */
    val quickAdd: Boolean = false,
    /** 密碼鎖可用指紋／臉部解鎖 */
    val biometric: Boolean = false,
    /** 離開 App 多久後要求密碼（秒），0 = 立即 */
    val lockDelaySec: Int = 0,
    /** 小工具：0 跟隨系統、1 淺色、2 深色 */
    val widgetTheme: Int = 0,
    /** 小工具背景不透明度（%） */
    val widgetOpacity: Int = 100,
    /** 帳戶群組順序（AccountType 名稱） */
    val accountGroupOrder: List<String> = emptyList(),
    /** 帳戶群組自訂名稱 */
    val accountGroupNames: Map<String, String> = emptyMap(),
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
    private val strMapSerializer = MapSerializer(String.serializer(), String.serializer())
    private val cashSerializer = MapSerializer(Long.serializer(), MapSerializer(Int.serializer(), Int.serializer()))
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
        screenPackages = prefs.getStringSet("screenPackages", emptySet())!!.toSet(),
        screenReadSince = runCatching { json.decodeFromString(pkgMapSerializer, prefs.getString("screenReadSince", "{}")!!) }.getOrDefault(emptyMap()),
        cashCounts = runCatching { json.decodeFromString(cashSerializer, prefs.getString("cashCounts", "{}")!!) }.getOrDefault(emptyMap()),
        themeMode = prefs.getInt("themeMode", 0),
        uiScale = prefs.getFloat("uiScale", 0.9f),
        subcategoryOn = prefs.getBoolean("subcategoryOn", true),
        noteButton = prefs.getBoolean("noteButton", true),
        swipe = prefs.getInt("swipe", 0),
        showDescription = prefs.getBoolean("showDescription", false),
        quickAdd = prefs.getBoolean("quickAdd", false),
        biometric = prefs.getBoolean("biometric", false),
        lockDelaySec = prefs.getInt("lockDelaySec", 0),
        widgetTheme = prefs.getInt("widgetTheme", 0),
        widgetOpacity = prefs.getInt("widgetOpacity", 100),
        accountGroupOrder = prefs.getString("accountGroupOrder", "")!!.split(',').filter { it.isNotBlank() },
        accountGroupNames = runCatching { json.decodeFromString(strMapSerializer, prefs.getString("accountGroupNames", "{}")!!) }.getOrDefault(emptyMap()),
    )

    @Synchronized
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
            .putStringSet("screenPackages", s.screenPackages)
            .putString("screenReadSince", json.encodeToString(pkgMapSerializer, s.screenReadSince))
            .putString("cashCounts", json.encodeToString(cashSerializer, s.cashCounts))
            .putInt("themeMode", s.themeMode)
            .putFloat("uiScale", s.uiScale)
            .putBoolean("subcategoryOn", s.subcategoryOn)
            .putBoolean("noteButton", s.noteButton)
            .putInt("swipe", s.swipe)
            .putBoolean("showDescription", s.showDescription)
            .putBoolean("quickAdd", s.quickAdd)
            .putBoolean("biometric", s.biometric)
            .putInt("lockDelaySec", s.lockDelaySec)
            .putInt("widgetTheme", s.widgetTheme)
            .putInt("widgetOpacity", s.widgetOpacity)
            .putString("accountGroupOrder", s.accountGroupOrder.joinToString(","))
            .putString("accountGroupNames", json.encodeToString(strMapSerializer, s.accountGroupNames))
            .apply()
        state.value = s
    }
}

/** 帳戶群組（帳戶類型）顯示名稱，可在設定中自訂。 */
fun AppSettings.groupLabel(t: AccountType): String = accountGroupNames[t.name]?.takeIf { it.isNotBlank() } ?: t.label

/** 依使用者設定的群組順序排列的帳戶類型。 */
fun AppSettings.groupOrder(): List<AccountType> {
    val custom = accountGroupOrder.mapNotNull { n -> AccountType.entries.find { it.name == n } }
    return custom + AccountType.entries.filter { it !in custom }
}
