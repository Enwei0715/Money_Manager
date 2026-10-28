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
import kotlinx.serialization.json.put
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
data class Memo(
    val id: Long,
    val date: Long,
    val text: String,
    val title: String = "",
    /** 主題色（[memoColors] 的索引，0 = 無） */
    val color: Int = 0,
    /** 置頂 */
    val pinned: Boolean = false,
)

/** 備註主題色（0 為不上色）。 */
val memoColors: List<Long> = listOf(
    0x00000000, 0xFFFF7468, 0xFFFFB14A, 0xFFFFE97A, 0xFFB2FF6E, 0xFF52F5D1,
    0xFF63E8FF, 0xFF45A6FF, 0xFFC27BFF, 0xFFFFA3E3, 0xFFD6B98A, 0xFFB5B5B5,
)

/** 證券帳戶手續費月退：成交先收全額，次月 [payDay] 日退回差額。 */
@Serializable
data class RebateRule(val discount: Double, val sinceEpochDay: Long, val payDay: Int = 15)

/** 使用者教過的通知格式：某 App、標題相同的通知一律視為指定收支，記到指定帳戶。 */
@Serializable
data class LearnedSource(val pkg: String, val title: String, val income: Boolean, val accountId: Long?)

data class AppSettings(
    val baseCurrency: String = "TWD",
    val subCurrency: String = "USD",
    val defaultAccountId: Long? = null,
    val notifAutoConfirm: Boolean = false,
    val notifPackages: Set<String> = emptySet(),
    val feeDiscount: Double = 1.0,
    val minFee: Double = 20.0,
    val usFee: Double = 0.0,
    val oddLotMinFee: Double = 1.0,
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
    val learnedSources: List<LearnedSource> = emptyList(),
    /** 證券帳戶 id → 月退設定 */
    val brokerRebates: Map<Long, RebateRule> = emptyMap(),
    /** 使用者刪除過的自動除息（代號:除息日:批次），不再自動建立 */
    val dismissedDividends: Set<String> = emptySet(),
    /** 已清理過舊版自動建立的預設帳戶 */
    val seedCleaned: Boolean = false,
    /** 信用卡 id → 結帳日遇週末的調整（StatementShift 名稱） */
    val cardShift: Map<Long, String> = emptyMap(),
    /** 信用卡 id → 發卡銀行代碼 */
    val cardBank: Map<Long, String> = emptyMap(),
    /** 信用卡 id → 繳款截止日＝結帳日＋幾天（依銀行規則） */
    val cardDueOffset: Map<Long, Int> = emptyMap(),
    /** 介面語言：0 跟隨系統、1 繁體中文、2 English */
    val language: Int = 1,
    /** 自動雲端備份：目標檔案（SAF URI）、間隔天數（0 關閉）、上次成功時間、上次錯誤 */
    val autoBackupUri: String = "",
    val autoBackupDays: Int = 1,
    val lastAutoBackup: Long = 0,
    val lastAutoBackupError: String = "",
    /** 下載的國定假日（年|月日:名稱;…|補班月日;…，一年一行） */
    val holidayCache: String = "",
) {
    val feeSettings get() = FeeSettings(feeDiscount, minFee, usFee, oddLotMinFee)
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
    private val rebateSerializer = MapSerializer(Long.serializer(), RebateRule.serializer())
    private val longIntSerializer = MapSerializer(Long.serializer(), Int.serializer())
    private val longStrSerializer = MapSerializer(Long.serializer(), String.serializer())
    private val learnedSerializer = ListSerializer(LearnedSource.serializer())
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
        oddLotMinFee = prefs.getFloat("oddLotMinFee", 1f).toDouble(),
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
        seedCleaned = prefs.getBoolean("seedCleaned", false),
        language = prefs.getInt("language", 1),
        autoBackupUri = prefs.getString("autoBackupUri", "")!!,
        autoBackupDays = prefs.getInt("autoBackupDays", 1),
        lastAutoBackup = prefs.getLong("lastAutoBackup", 0),
        lastAutoBackupError = prefs.getString("lastAutoBackupError", "")!!,
        holidayCache = prefs.getString("holidayCache", "")!!,
        cardBank = runCatching { json.decodeFromString(longStrSerializer, prefs.getString("cardBank", "{}")!!) }.getOrDefault(emptyMap()),
        cardDueOffset = runCatching { json.decodeFromString(longIntSerializer, prefs.getString("cardDueOffset", "{}")!!) }.getOrDefault(emptyMap()),
        cardShift = runCatching { json.decodeFromString(longStrSerializer, prefs.getString("cardShift", "{}")!!) }.getOrDefault(emptyMap()),
        dismissedDividends = prefs.getStringSet("dismissedDividends", emptySet())!!.toSet(),
        brokerRebates = runCatching { json.decodeFromString(rebateSerializer, prefs.getString("brokerRebates", "{}")!!) }.getOrDefault(emptyMap()),
        learnedSources = runCatching { json.decodeFromString(learnedSerializer, prefs.getString("learnedSources", "[]")!!) }.getOrDefault(emptyList()),
        accountGroupNames = runCatching { json.decodeFromString(strMapSerializer, prefs.getString("accountGroupNames", "{}")!!) }.getOrDefault(emptyMap()),
    )

    @Synchronized
    /** 匯出所有設定（完整備份用）；GitHub Token 屬於憑證，不放進備份檔。 */
    fun exportRaw(): Map<String, kotlinx.serialization.json.JsonElement> = prefs.all.filterKeys {
        it !in setOf("githubToken", "autoBackupUri", "lastAutoBackup", "lastAutoBackupError")
    }.mapNotNull { (k, v) ->
        val e: kotlinx.serialization.json.JsonElement = when (v) {
            is Boolean -> kotlinx.serialization.json.buildJsonObject { put("t", "b"); put("v", v) }
            is Int -> kotlinx.serialization.json.buildJsonObject { put("t", "i"); put("v", v) }
            is Long -> kotlinx.serialization.json.buildJsonObject { put("t", "l"); put("v", v) }
            is Float -> kotlinx.serialization.json.buildJsonObject { put("t", "f"); put("v", v) }
            is String -> kotlinx.serialization.json.buildJsonObject { put("t", "s"); put("v", v) }
            is Set<*> -> kotlinx.serialization.json.buildJsonObject {
                put("t", "ss"); put("v", kotlinx.serialization.json.JsonArray(v.map { kotlinx.serialization.json.JsonPrimitive(it.toString()) }))
            }
            else -> return@mapNotNull null
        }
        k to e
    }.toMap()

    /** 以備份檔的設定取代目前設定（保留本機的 GitHub Token），並重新載入。 */
    @Synchronized
    fun importRaw(map: Map<String, kotlinx.serialization.json.JsonElement>) {
        val token = prefs.getString("githubToken", "")
        val keepUri = prefs.getString("autoBackupUri", "")
        val ed = prefs.edit().clear()
        map.forEach { (k, e) ->
            val o = e as? kotlinx.serialization.json.JsonObject ?: return@forEach
            val t = (o["t"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            val v = o["v"] ?: return@forEach
            val p = v as? kotlinx.serialization.json.JsonPrimitive
            when (t) {
                "b" -> p?.content?.toBooleanStrictOrNull()?.let { ed.putBoolean(k, it) }
                "i" -> p?.content?.toIntOrNull()?.let { ed.putInt(k, it) }
                "l" -> p?.content?.toLongOrNull()?.let { ed.putLong(k, it) }
                "f" -> p?.content?.toFloatOrNull()?.let { ed.putFloat(k, it) }
                "s" -> p?.content?.let { ed.putString(k, it) }
                "ss" -> (v as? kotlinx.serialization.json.JsonArray)?.let { arr -> ed.putStringSet(k, arr.map { (it as kotlinx.serialization.json.JsonPrimitive).content }.toSet()) }
            }
        }
        ed.putString("githubToken", token).putString("autoBackupUri", keepUri).commit()
        state.value = read()
    }

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
            .putFloat("oddLotMinFee", s.oddLotMinFee.toFloat())
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
            .putString("learnedSources", json.encodeToString(learnedSerializer, s.learnedSources))
            .putString("brokerRebates", json.encodeToString(rebateSerializer, s.brokerRebates))
            .putStringSet("dismissedDividends", s.dismissedDividends)
            .putBoolean("seedCleaned", s.seedCleaned)
            .putString("cardShift", json.encodeToString(longStrSerializer, s.cardShift))
            .putString("cardBank", json.encodeToString(longStrSerializer, s.cardBank))
            .putInt("language", s.language)
            .putString("autoBackupUri", s.autoBackupUri)
            .putInt("autoBackupDays", s.autoBackupDays)
            .putLong("lastAutoBackup", s.lastAutoBackup)
            .putString("lastAutoBackupError", s.lastAutoBackupError)
            .putString("holidayCache", s.holidayCache)
            .putString("cardDueOffset", json.encodeToString(longIntSerializer, s.cardDueOffset))
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

/** 這則通知符合哪個教過的格式（同一 App、標題相同；沒有標題時比對內文開頭）。 */
fun AppSettings.learnedFor(pkg: String, title: String?, text: String?): LearnedSource? =
    learnedSources.lastOrNull { it.pkg == pkg && it.title == learnKey(title, text) }

fun learnKey(title: String?, text: String?): String =
    title?.trim()?.takeIf { it.isNotEmpty() } ?: text.orEmpty().trim().take(8)

fun AppSettings.shiftFor(cardId: Long): com.enwei.moneymanager.core.StatementShift =
    cardShift[cardId]?.let { n -> com.enwei.moneymanager.core.StatementShift.entries.find { it.name == n } } ?: com.enwei.moneymanager.core.StatementShift.NONE

/** 這張信用卡的帳單週期（含週末調整與銀行的繳款天數）。 */
fun AppSettings.cycleFor(card: AccountEntity): com.enwei.moneymanager.core.CardCycle? {
    val sd = card.statementDay ?: return null
    val off = cardDueOffset[card.id]
    val dd = card.dueDay ?: if (off != null) 1 else return null
    return com.enwei.moneymanager.core.CardCycle(sd, dd, shiftFor(card.id), off)
}

/** 國定假日快取的編碼與解碼（core 的 YearCalendar 不依賴序列化套件）。 */
object HolidayCache {
    fun decode(text: String): Map<Int, com.enwei.moneymanager.core.YearCalendar> = text.lines().mapNotNull { line ->
        val parts = line.split('|')
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        val hol = parts.getOrNull(1).orEmpty().split(';').filter { ':' in it }.associate { it.substringBefore(':') to it.substringAfter(':') }
        val work = parts.getOrNull(2).orEmpty().split(';').filter { it.length == 4 }.toSet()
        year to com.enwei.moneymanager.core.YearCalendar(hol, work)
    }.toMap()

    fun encode(map: Map<Int, com.enwei.moneymanager.core.YearCalendar>): String = map.entries.sortedBy { it.key }.joinToString("\n") { (y, c) ->
        "$y|" + c.holidays.entries.joinToString(";") { "${it.key}:${it.value.replace(';', ' ').replace('|', ' ')}" } + "|" + c.workdays.joinToString(";")
    }
}
