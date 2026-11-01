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

/** 舊版書籤交易範本資料型別，保留供偏好與 .lgbak 相容。 */
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

/** AI 對一筆交易建議的分類，使用者確認後才套用；[rejected] 為略過（不再重複詢問）。 */
@Serializable
data class AiSuggestion(val txnId: Long, val categoryId: Long, val reason: String = "", val rejected: Boolean = false)

/**
 * 借貸帳戶的細節：[lent] 為借出（別人欠你）或借入（你欠別人）；金額皆為最小單位。
 * 設定每月還款時會建立一個週期轉帳（[recurringId]），到期產生的轉帳先放「待確認」。
 */
@Serializable
data class LoanInfo(
    val counterparty: String = "",
    val lent: Boolean = true,
    val principal: Long = 0,
    /** 年利率（%） */
    val annualRate: Double = 0.0,
    val startDay: Long = java.time.LocalDate.now().toEpochDay(),
    /** 到期日（epochDay） */
    val dueDay: Long? = null,
    /** 每月還款金額 */
    val installment: Long? = null,
    /** 每月幾號還款 */
    val payDay: Int? = null,
    /** 還款／收款的帳戶 */
    val payAccountId: Long? = null,
    val recurringId: Long? = null,
)

/** 一檔股票的價格提醒：漲到 [target] 以上或跌到 [stop] 以下時通知。 */
@Serializable
data class PriceAlert(val target: Double? = null, val stop: Double? = null)

/** 資金配置與可選的持股提示（金額為最小單位、比例為 %）。 */
@Serializable
data class InvestPlanSettings(
    /** 使用者自行設定的預留金額 */
    val reserve: Long = 0,
    /** 可投資資金中長線的比例（其餘為短線） */
    val longRatio: Double = 0.5,
    val shortStopPct: Double = 8.0,
    val shortTrailPct: Double = 8.0,
    val longTrailPct: Double = 15.0,
    val takeProfitPct: Double = 15.0,
    /** 自己的交易紀律（交易前檢查） */
    val rules: List<String> = emptyList(),
    /** 持股停損／停利建議；新安裝預設關閉，使用者可自行啟用及設定。 */
    val disciplineOn: Boolean = false,
    /** 保本＋移動停利紀律 */
    val guardOn: Boolean = false,
    /** 移動停利：從最高價回落幾 % 為停利價 */
    val guardTrailPct: Double = 3.0,
    /** 60 分 K 兩條均線都跌破時留下的基本股數 */
    val baseShares: Double = 1.0,
    /** 每檔的保本金額、移動停利最高價、手動停利價與減碼價（key 為代號） */
    val guards: Map<String, GuardState> = emptyMap(),
) {
    companion object {
        private val legacySeededRuleHashes = setOf(
            "b4322682235d52cf1ee195b241f5ae04d3f12ddf027ed833f53e8b514304041d",
            "2e4325e7a4834bf73f78ff7b052361b7375fa85161726f3aad60075eee2cafa6",
        )

        fun hasLegacySeededRules(rules: List<String>): Boolean {
            if (rules.isEmpty()) return false
            val bytes = MessageDigest.getInstance("SHA-256").digest(rules.joinToString("\n").toByteArray(Charsets.UTF_8))
            val fingerprint = bytes.joinToString("") { "%02x".format(it) }
            return fingerprint in legacySeededRuleHashes
        }
    }
}

/** 一檔持股的保本＋移動停利狀態。 */
@Serializable
data class GuardState(
    /** 保本金額（原始成本）；null 表示自動用這一輪買進的總成本 */
    val principal: Double? = null,
    /** 出現多賺股數之後的最高價 */
    val peak: Double? = null,
    /** 手動設的停利價 */
    val takeProfit: Double? = null,
    /** 想減碼時設的觸發價 */
    val reduceAt: Double? = null,
)

/** AI 投資分析（最近一次）。 */
@Serializable
data class AdvisorReport(
    val createdAt: Long = 0,
    val trend: String = "",
    val stage: String = "",
    val demand: List<String> = emptyList(),
    val summary: String = "",
    val holdings: List<AdvisorHolding> = emptyList(),
    val ideas: List<AdvisorIdea> = emptyList(),
    val allocation: String = "",
    val checklist: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val news: List<String> = emptyList(),
)

@Serializable
data class AdvisorHolding(val symbol: String, val action: String, val stop: Double? = null, val reason: String = "")

@Serializable
data class AdvisorIdea(
    val symbol: String, val name: String = "", val style: String = "", val reason: String = "",
    val entry: String = "", val stop: String = "", val price: Double? = null,
)

/** AI 對一組可能重複交易的判斷（[same] 為同一筆；同一筆時附上較精確的名稱與分類）。 */
@Serializable
data class DupJudgement(val same: Boolean, val name: String? = null, val categoryId: Long? = null, val reason: String = "")

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

/** 使用者確認過的本機辨識偏好；title 欄位沿用舊備份結構，實際保存的是標題指紋。 */
@Serializable
data class LearnedSource(
    val pkg: String,
    /** 舊版欄位名稱，內容為 semantic:<hash>，不保存標題或通知原文。 */
    val title: String,
    val income: Boolean,
    val accountId: Long?,
    /** NORMAL（收入／支出）、TRANSFER_OUT（轉出）、TRANSFER_IN（轉入） */
    val kind: String = "NORMAL",
    /** 轉帳的另一個帳戶（選填）；不填則等另一端的通知自動配對 */
    val toAccountId: Long? = null,
) {
    val parsedKind: com.enwei.moneymanager.core.ParsedKind
        get() = runCatching { com.enwei.moneymanager.core.ParsedKind.valueOf(kind) }.getOrDefault(com.enwei.moneymanager.core.ParsedKind.NORMAL)

    /** 依本機辨識偏好解析這則通知（實際金額、時間、卡號仍從當次內文找）。 */
    fun parse(title: String?, text: String?): com.enwei.moneymanager.core.ParsedTransaction? =
        com.enwei.moneymanager.core.NotificationParser.parseLearned(
            title, text,
            if (income || parsedKind == com.enwei.moneymanager.core.ParsedKind.TRANSFER_IN) com.enwei.moneymanager.core.ParsedType.INCOME
            else com.enwei.moneymanager.core.ParsedType.EXPENSE,
            kind = parsedKind,
        )

    val label: String
        get() = when (parsedKind) {
            com.enwei.moneymanager.core.ParsedKind.TRANSFER_OUT -> "轉出"
            com.enwei.moneymanager.core.ParsedKind.TRANSFER_IN -> "轉入"
            else -> if (income) "收入" else "支出"
        }
}

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
    /** 以下舊版功能欄位保留供偏好與 .lgbak 相容，現行介面不再使用。 */
    val reminderEnabled: Boolean = false,
    val reminderMinutes: Int = 21 * 60,
    val bookmarks: List<Bookmark> = emptyList(),
    /** 上期結轉：把期初淨額當作收入顯示在每月第一天 */
    val carryOver: Boolean = true,
    val memos: List<Memo> = emptyList(),
    /** 每月預算：key 為分類 id，0 代表總預算；值為最小單位金額 */
    val budgets: Map<Long, Long> = emptyMap(),
    /** 儲蓄率目標（%）；null 為不設定 */
    val savingsGoal: Double? = null,
    /** 已發過的預算提醒（「月份:分類:門檻」） */
    val budgetAlerted: Set<String> = emptySet(),
    /** 通知來源 App → 記入的帳戶 */
    val notifAccountMap: Map<String, Long> = emptyMap(),
    /** 讀取交易紀錄畫面的錢包 App */
    val screenPackages: Set<String> = emptySet(),
    /** 每個錢包 App 從哪一天開始匯入（epochDay），避免第一次把半年歷史全部匯入 */
    val screenReadSince: Map<String, Long> = emptyMap(),
    /** 舊版點鈔資料：帳戶 id → 面額 → 數量；保留備份相容。 */
    val cashCounts: Map<Long, Map<Int, Int>> = emptyMap(),
    /** 0 跟隨系統、1 淺色、2 深色 */
    val themeMode: Int = 0,
    /** 介面縮放比例（整體文字、間距、圖示等比例） */
    val uiScale: Float = 1f,
    /** 舊版欄位；現行版本固定啟用子分類。 */
    val subcategoryOn: Boolean = true,
    /** 交易頁顯示「新增備註」按鈕 */
    val noteButton: Boolean = true,
    /** 交易頁左右滑動：0 無、1 切換期間 */
    val swipe: Int = 0,
    /** 交易列表顯示說明 */
    val showDescription: Boolean = false,
    /** 舊版常駐快速記帳通知設定，保留備份相容。 */
    val quickAdd: Boolean = false,
    /** 密碼鎖可用指紋／臉部解鎖 */
    val biometric: Boolean = false,
    /** 離開 App 多久後要求密碼（秒），0 = 立即 */
    val lockDelaySec: Int = 0,
    /** 舊版小工具外觀設定，保留備份相容。 */
    val widgetTheme: Int = 0,
    val widgetOpacity: Int = 100,
    /** 工具首頁的顯示順序（僅保存入口 id，不影響工具資料）。 */
    val toolOrder: List<String> = listOf("pending", "recurring", "fx", "backup", "ai-hub"),
    /** 工具首頁隱藏的入口 id。 */
    val hiddenTools: Set<String> = emptySet(),
    /** 工具頁用寬格（兩格寬、顯示摘要）的工具 */
    val wideTools: Set<String> = setOf("pending", "ai-hub"),
    /** 工具格的大小（id → "寬x高"，以格數計；沒有時依 wideTools 為 2x1 或 1x1） */
    val toolSizes: Map<String, String> = emptyMap(),
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
    /** 颱風假等停止上班日，格式 yyyy-MM-dd:名稱;… */
    val typhoonDays: String = "",
    /** Groq API 金鑰（憑證，不放進備份檔） */
    val groqKey: String = "",
    /** Google Gemini（AI Studio）金鑰：讀大量資料的工作用 */
    val geminiKey: String = "",
    val geminiModel: String = "gemini-flash-latest",
    val geminiLiteModel: String = "gemini-flash-lite-latest",
    /** 舊版 AI 手動分配欄位，保留備份相容；目前一律自動分工。 */
    val aiAutoRoute: Boolean = true,
    val aiRoutes: Map<String, String> = emptyMap(),
    /** 舊版用量統計欄位；保留備份相容，不再更新或顯示。 */
    val geminiUsageDay: Long = 0,
    val geminiUsageRequests: Int = 0,
    val geminiUsageTokens: Long = 0,
    /** AI 智慧辨識：規則判斷不出來時用 AI 判讀通知、命名商家、挑分類 */
    val aiEnabled: Boolean = false,
    val aiModel: String = "openai/gpt-oss-20b",
    /** 較難推估（整理不明花費）用的大模型 */
    val aiModelHard: String = "openai/gpt-oss-120b",
    /** 舊版截圖對帳模型欄位，保留備份相容。 */
    val aiModelVision: String = "qwen/qwen3.8-27b",
    /** 舊版 AI 用量統計欄位；保留備份相容，不再更新或顯示。 */
    val aiUsageDay: Long = 0,
    val aiUsageRequests: Int = 0,
    val aiUsageTokens: Long = 0,
    /** 使用者標記「不是重複」的交易組合（"小id-大id"） */
    val dupDismissed: Set<String> = emptySet(),
    val investPlan: InvestPlanSettings = InvestPlanSettings(),
    /** 股價提醒：代號 → 目標價／停損價 */
    val priceAlerts: Map<String, PriceAlert> = emptyMap(),
    /** 觀察清單（台股代號），股票頁顯示報價，新聞與 AI 分析也會一起看 */
    val watchlist: List<String> = emptyList(),
    /** 各個選單上次選的項目（key 為選單名稱，例如 "txn.account"），下次自動帶入 */
    val lastPicks: Map<String, String> = emptyMap(),
    /** 日曆的休、結、繳、扣標示：false 用文字，true 用顏色小點 */
    val calendarDots: Boolean = false,
    /** AI 語氣：0 溫和教練、1 理性顧問、2 簡潔直接（收支檢討、投資報告、股票助理共用） */
    val aiTone: Int = 0,
    /** 背景 AI 工作（新聞標註、整理、報告）只在 Wi-Fi 等不計量網路執行 */
    val aiWifiOnly: Boolean = false,
    /** 背景蒐集新聞、整理題材的間隔（小時） */
    val newsIntervalHours: Int = 3,
    /** 搜尋的篩選方式：0 圖表勾選頁（收入／支出圓環＋分類、帳戶勾選），1 欄位篩選（期間、帳戶、分類、金額） */
    val filterStyle: Int = 0,
    /** 舊版現金面額設定，保留備份相容。 */
    val cashByDenom: Boolean = true,
    /** 盤中依紀律（動態停損、分批停利）自動通知 */
    val stockAlertsOn: Boolean = true,
    /** 已通知過的提醒（「代號:種類」→ 日期 epochDay），同一天不重複 */
    val alertsFired: Map<String, Long> = emptyMap(),
    /** 有新版本時通知 */
    val notifyUpdates: Boolean = true,
    /** 已通知過的版本代碼 */
    val updateNotified: Int = 0,
    /** 使用者在更新視窗按「略過這版」的版本 */
    val updateSkipped: Int = 0,
    /** 最近一次自動檢查更新的結果（顯示在「App 更新」頁，方便知道有沒有檢查成功） */
    val updateCheckLog: String = "",
    /** 自動檢查發現的最新版本號（設定頁「App 更新」顯示有新版） */
    val updateAvailable: Int = 0,
    /** 上次產生報告時 AI 分析花的時間（毫秒），用來估算 */
    val reportAiMs: Long = 50_000,
    /** 背景蒐集新聞並每天產生投資報告 */
    val advisorDaily: Boolean = true,
    /** 交易的必要性（交易 id → NEED／SAVE／ONCE＋備註），收支檢討時讓 AI 參考 */
    val txnNecessity: Map<Long, String> = emptyMap(),
    /** 股票代號 → 股性（LONG／SHORT），沒設定時 ETF 視為長線、其他視為短線 */
    val stockStyle: Map<String, String> = emptyMap(),
    val advisorReport: AdvisorReport? = null,
    /** AI 收支檢討（期間代號 → 檢討），重新產生前沿用 */
    val reviews: Map<String, AiReview> = emptyMap(),
    /** 借貸帳戶 id → 借貸細節 */
    val loans: Map<Long, LoanInfo> = emptyMap(),
    /** 上次通知的借貸提醒（避免重複通知） */
    val loanWarnKey: String = "",
    /** AI 判斷過的重複組合（"小id-大id" → 判斷），不再重複詢問 */
    val dupVerdicts: Map<String, DupJudgement> = emptyMap(),
    /** AI 自動整理：背景判斷重複並合併兩筆都是自動記帳的同一筆、確認 AI 判讀的通知後自動學會格式 */
    val aiAutoTidy: Boolean = true,
    /** AI 自動整理做過的事（新到舊，最多 30 筆） */
    val aiTidyLog: List<String> = emptyList(),
    /** AI 建議的分類（待使用者確認） */
    val aiSuggestions: List<AiSuggestion> = emptyList(),
    /** 上次提醒「扣款帳戶餘額不足」的內容，避免同一件事重複通知 */
    val fundingWarnKey: String = "",
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
    private val aiSuggestionSerializer = ListSerializer(AiSuggestion.serializer())
    private val reviewSerializer = MapSerializer(String.serializer(), AiReview.serializer())
    private val loanSerializer = MapSerializer(Long.serializer(), LoanInfo.serializer())
    private val dupVerdictSerializer = MapSerializer(String.serializer(), DupJudgement.serializer())
    private val strMapSerializer = MapSerializer(String.serializer(), String.serializer())
    private val cashSerializer = MapSerializer(Long.serializer(), MapSerializer(Int.serializer(), Int.serializer()))
    private val state = MutableStateFlow(read())
    val flow: StateFlow<AppSettings> = state.asStateFlow()
    val value: AppSettings get() = state.value

    private fun read(): AppSettings {
        val savedPlan = runCatching {
            json.decodeFromString(InvestPlanSettings.serializer(), prefs.getString("investPlan", "")!!)
        }.getOrDefault(InvestPlanSettings())
        val seededRules = InvestPlanSettings.hasLegacySeededRules(savedPlan.rules)
        val defaultPlan = InvestPlanSettings()
        val plan = if (seededRules) {
            savedPlan.copy(
                reserve = if (savedPlan.reserve == 2_000_000L) defaultPlan.reserve else savedPlan.reserve,
                longRatio = if (savedPlan.longRatio == 2.0 / 3.0) defaultPlan.longRatio else savedPlan.longRatio,
                rules = emptyList(),
                disciplineOn = false,
                guardOn = if (savedPlan.guardTrailPct == 3.0 && savedPlan.baseShares == 1.0) false else savedPlan.guardOn,
            )
        } else savedPlan.copy(disciplineOn = prefs.getBoolean("stockDisciplineOn", prefs.contains("investPlan")))
        if (seededRules) {
            prefs.edit()
                .putString("investPlan", json.encodeToString(InvestPlanSettings.serializer(), plan))
                .putBoolean("stockDisciplineOn", false)
                .apply()
        }
        val savedLearned = runCatching { json.decodeFromString(learnedSerializer, prefs.getString("learnedSources", "[]")!!) }.getOrDefault(emptyList())
        val learned = savedLearned.asReversed().map { it.copy(title = learnKey(it.title, null)) }
            .distinctBy { it.pkg to it.title }.asReversed()
        if (learned != savedLearned) {
            prefs.edit().putString("learnedSources", json.encodeToString(learnedSerializer, learned)).apply()
        }
        return AppSettings(
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
        savingsGoal = prefs.getFloat("savingsGoal", -1f).takeIf { it >= 0f }?.toDouble(),
        budgetAlerted = prefs.getStringSet("budgetAlerted", emptySet()).orEmpty().toSet(),
        notifAccountMap = runCatching { json.decodeFromString(pkgMapSerializer, prefs.getString("notifAccountMap", "{}")!!) }.getOrDefault(emptyMap()),
        screenPackages = prefs.getStringSet("screenPackages", emptySet())!!.toSet(),
        screenReadSince = runCatching { json.decodeFromString(pkgMapSerializer, prefs.getString("screenReadSince", "{}")!!) }.getOrDefault(emptyMap()),
        cashCounts = runCatching { json.decodeFromString(cashSerializer, prefs.getString("cashCounts", "{}")!!) }.getOrDefault(emptyMap()),
        themeMode = prefs.getInt("themeMode", 0),
        uiScale = prefs.getFloat("uiScale2", 1f),
        subcategoryOn = true,
        noteButton = prefs.getBoolean("noteButton", true),
        swipe = prefs.getInt("swipe", 0),
        showDescription = prefs.getBoolean("showDescription", false),
        quickAdd = prefs.getBoolean("quickAdd", false),
        biometric = prefs.getBoolean("biometric", false),
        lockDelaySec = prefs.getInt("lockDelaySec", 0),
        widgetTheme = prefs.getInt("widgetTheme", 0),
        widgetOpacity = prefs.getInt("widgetOpacity", 100),
        toolOrder = if (prefs.contains("toolOrder")) prefs.getString("toolOrder", "")!!.split(',').filter { it.isNotBlank() }
            else AppSettings().toolOrder,
        hiddenTools = prefs.getStringSet("hiddenTools", emptySet())!!.toSet(),
        wideTools = prefs.getStringSet("wideTools", null)?.toSet() ?: AppSettings().wideTools,
        toolSizes = prefs.getStringSet("toolSizes", emptySet()).orEmpty().mapNotNull { e -> e.split('=').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap(),
        accountGroupOrder = prefs.getString("accountGroupOrder", "")!!.split(',').filter { it.isNotBlank() },
        seedCleaned = prefs.getBoolean("seedCleaned", false),
        language = prefs.getInt("language", 1),
        autoBackupUri = prefs.getString("autoBackupUri", "")!!,
        autoBackupDays = prefs.getInt("autoBackupDays", 1),
        lastAutoBackup = prefs.getLong("lastAutoBackup", 0),
        lastAutoBackupError = prefs.getString("lastAutoBackupError", "")!!,
        holidayCache = prefs.getString("holidayCache", "")!!,
        typhoonDays = prefs.getString("typhoonDays", "")!!,
        groqKey = prefs.getString("groqKey", "")!!,
        geminiKey = prefs.getString("geminiKey", "")!!,
        aiAutoRoute = prefs.getBoolean("aiAutoRoute", true),
        geminiModel = prefs.getString("geminiModel", "gemini-flash-latest")!!,
        geminiLiteModel = prefs.getString("geminiLiteModel", "gemini-flash-lite-latest")!!,
        aiRoutes = runCatching { json.decodeFromString(strMapSerializer, prefs.getString("aiRoutes", "{}")!!) }.getOrDefault(emptyMap()),
        geminiUsageDay = prefs.getLong("geminiUsageDay", 0),
        geminiUsageRequests = prefs.getInt("geminiUsageRequests", 0),
        geminiUsageTokens = prefs.getLong("geminiUsageTokens", 0),
        aiEnabled = prefs.getBoolean("aiEnabled", false),
        // llama-3.1-8b-instant 已停止提供給免費方案，舊設定自動換成新的預設模型
        aiModel = prefs.getString("aiModel", "openai/gpt-oss-20b")!!.let { if (it == "llama-3.1-8b-instant") "openai/gpt-oss-20b" else it },
        fundingWarnKey = prefs.getString("fundingWarnKey", "")!!,
        aiModelHard = prefs.getString("aiModelHard", "openai/gpt-oss-120b")!!,
        dupDismissed = prefs.getStringSet("dupDismissed", emptySet())!!.toSet(),
        loans = runCatching { json.decodeFromString(loanSerializer, prefs.getString("loans", "{}")!!) }.getOrDefault(emptyMap()),
        loanWarnKey = prefs.getString("loanWarnKey", "")!!,
        advisorDaily = prefs.getBoolean("advisorDaily", true),
        reportAiMs = prefs.getLong("reportAiMs", 50_000),
        priceAlerts = runCatching { json.decodeFromString(MapSerializer(String.serializer(), PriceAlert.serializer()), prefs.getString("priceAlerts", "{}")!!) }.getOrDefault(emptyMap()),
        watchlist = runCatching { json.decodeFromString(ListSerializer(String.serializer()), prefs.getString("watchlist", "[]")!!) }.getOrDefault(emptyList()),
        calendarDots = prefs.getBoolean("calendarDots", false),
        aiTone = prefs.getInt("aiTone", 0),
        aiWifiOnly = prefs.getBoolean("aiWifiOnly", false),
        newsIntervalHours = prefs.getInt("newsIntervalHours", 3),
        filterStyle = prefs.getInt("filterStyle", 0),
        lastPicks = runCatching { json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), prefs.getString("lastPicks", "{}")!!) }.getOrDefault(emptyMap()),
        cashByDenom = prefs.getBoolean("cashByDenom", true),
        stockAlertsOn = prefs.getBoolean("stockAlertsOn", true),
        alertsFired = runCatching { json.decodeFromString(MapSerializer(String.serializer(), Long.serializer()), prefs.getString("alertsFired", "{}")!!) }.getOrDefault(emptyMap()),
        notifyUpdates = prefs.getBoolean("notifyUpdates", true),
        updateNotified = prefs.getInt("updateNotified", 0),
        updateSkipped = prefs.getInt("updateSkipped", 0),
        updateCheckLog = prefs.getString("updateCheckLog", "")!!,
        updateAvailable = prefs.getInt("updateAvailable", 0),
        txnNecessity = runCatching { json.decodeFromString(longStrSerializer, prefs.getString("txnNecessity", "{}")!!) }.getOrDefault(emptyMap()),
        investPlan = plan,
        stockStyle = runCatching { json.decodeFromString(strMapSerializer, prefs.getString("stockStyle", "{}")!!) }.getOrDefault(emptyMap()),
        advisorReport = runCatching { json.decodeFromString(AdvisorReport.serializer(), prefs.getString("advisorReport", "")!!) }.getOrNull(),
        reviews = runCatching { json.decodeFromString(reviewSerializer, prefs.getString("reviews", "{}")!!) }.getOrDefault(emptyMap()),
        dupVerdicts = runCatching { json.decodeFromString(dupVerdictSerializer, prefs.getString("dupVerdicts", "{}")!!) }.getOrDefault(emptyMap()),
        aiAutoTidy = prefs.getBoolean("aiAutoTidy", true),
        aiTidyLog = runCatching { json.decodeFromString(ListSerializer(String.serializer()), prefs.getString("aiTidyLog", "[]")!!) }.getOrDefault(emptyList()),
        aiModelVision = prefs.getString("aiModelVision", "qwen/qwen3.8-27b")!!,
        aiUsageDay = prefs.getLong("aiUsageDay", 0),
        aiUsageRequests = prefs.getInt("aiUsageRequests", 0),
        aiUsageTokens = prefs.getLong("aiUsageTokens", 0),
        aiSuggestions = runCatching { json.decodeFromString(aiSuggestionSerializer, prefs.getString("aiSuggestions", "[]")!!) }.getOrDefault(emptyList()),
        cardBank = runCatching { json.decodeFromString(longStrSerializer, prefs.getString("cardBank", "{}")!!) }.getOrDefault(emptyMap()),
        cardDueOffset = runCatching { json.decodeFromString(longIntSerializer, prefs.getString("cardDueOffset", "{}")!!) }.getOrDefault(emptyMap()),
        cardShift = runCatching { json.decodeFromString(longStrSerializer, prefs.getString("cardShift", "{}")!!) }.getOrDefault(emptyMap()),
        dismissedDividends = prefs.getStringSet("dismissedDividends", emptySet())!!.toSet(),
        brokerRebates = runCatching { json.decodeFromString(rebateSerializer, prefs.getString("brokerRebates", "{}")!!) }.getOrDefault(emptyMap()),
        learnedSources = learned,
        accountGroupNames = runCatching { json.decodeFromString(strMapSerializer, prefs.getString("accountGroupNames", "{}")!!) }.getOrDefault(emptyMap()),
        )
    }

    @Synchronized
    /** 匯出所有設定（完整備份用）；GitHub Token 屬於憑證，不放進備份檔。 */
    fun exportRaw(): Map<String, kotlinx.serialization.json.JsonElement> = prefs.all.filterKeys {
        it !in setOf("githubToken", "groqKey", "geminiKey", "autoBackupUri", "lastAutoBackup", "lastAutoBackupError")
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
        val groq = prefs.getString("groqKey", "")
        val gemini = prefs.getString("geminiKey", "")
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
        ed.putString("githubToken", token).putString("groqKey", groq).putString("geminiKey", gemini).putString("autoBackupUri", keepUri).commit()
        state.value = read()
    }

    /**
     * 修改設定。必須同步：背景同步（匯率、報價、假日、備份時間）和畫面上的修改可能同時發生，
     * 不同步時兩邊都以舊設定為基礎各自整份寫回，後寫的會把先寫的蓋掉（例如剛改的設定在重開後又變回去）。
     */
    @Synchronized
    fun update(block: (AppSettings) -> AppSettings) {
        val s = block(state.value).copy(subcategoryOn = true)
        if (s == state.value) return
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
            .putFloat("savingsGoal", s.savingsGoal?.toFloat() ?: -1f)
            .putStringSet("budgetAlerted", s.budgetAlerted)
            .putString("notifAccountMap", json.encodeToString(pkgMapSerializer, s.notifAccountMap))
            .putStringSet("screenPackages", s.screenPackages)
            .putString("screenReadSince", json.encodeToString(pkgMapSerializer, s.screenReadSince))
            .putString("cashCounts", json.encodeToString(cashSerializer, s.cashCounts))
            .putInt("themeMode", s.themeMode)
            .putFloat("uiScale2", s.uiScale)
            .putBoolean("subcategoryOn", true)
            .putBoolean("noteButton", s.noteButton)
            .putInt("swipe", s.swipe)
            .putBoolean("showDescription", s.showDescription)
            .putBoolean("quickAdd", s.quickAdd)
            .putBoolean("biometric", s.biometric)
            .putInt("lockDelaySec", s.lockDelaySec)
            .putInt("widgetTheme", s.widgetTheme)
            .putInt("widgetOpacity", s.widgetOpacity)
            .putString("toolOrder", s.toolOrder.joinToString(","))
            .putStringSet("hiddenTools", s.hiddenTools)
            .putStringSet("wideTools", s.wideTools)
            .putStringSet("toolSizes", s.toolSizes.map { "${it.key}=${it.value}" }.toSet())
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
            .putString("typhoonDays", s.typhoonDays)
            .putString("groqKey", s.groqKey)
            .putString("geminiKey", s.geminiKey)
            .putBoolean("aiAutoRoute", s.aiAutoRoute)
            .putString("geminiModel", s.geminiModel)
            .putString("geminiLiteModel", s.geminiLiteModel)
            .putString("aiRoutes", json.encodeToString(strMapSerializer, s.aiRoutes))
            .putLong("geminiUsageDay", s.geminiUsageDay)
            .putInt("geminiUsageRequests", s.geminiUsageRequests)
            .putLong("geminiUsageTokens", s.geminiUsageTokens)
            .putBoolean("aiEnabled", s.aiEnabled)
            .putString("aiModel", s.aiModel)
            .putString("fundingWarnKey", s.fundingWarnKey)
            .putString("aiModelHard", s.aiModelHard)
            .putStringSet("dupDismissed", s.dupDismissed)
            .putString("loans", json.encodeToString(loanSerializer, s.loans))
            .putString("loanWarnKey", s.loanWarnKey)
            .putString("reviews", json.encodeToString(reviewSerializer, s.reviews))
            .putBoolean("advisorDaily", s.advisorDaily)
            .putLong("reportAiMs", s.reportAiMs)
            .putString("priceAlerts", json.encodeToString(MapSerializer(String.serializer(), PriceAlert.serializer()), s.priceAlerts))
            .putString("watchlist", json.encodeToString(ListSerializer(String.serializer()), s.watchlist))
            .putBoolean("calendarDots", s.calendarDots)
            .putInt("aiTone", s.aiTone)
            .putBoolean("aiWifiOnly", s.aiWifiOnly)
            .putInt("newsIntervalHours", s.newsIntervalHours)
            .putInt("filterStyle", s.filterStyle)
            .putString("lastPicks", json.encodeToString(MapSerializer(String.serializer(), String.serializer()), s.lastPicks))
            .putBoolean("cashByDenom", s.cashByDenom)
            .putBoolean("stockAlertsOn", s.stockAlertsOn)
            .putString("alertsFired", json.encodeToString(MapSerializer(String.serializer(), Long.serializer()), s.alertsFired))
            .putBoolean("notifyUpdates", s.notifyUpdates)
            .putInt("updateNotified", s.updateNotified)
            .putInt("updateSkipped", s.updateSkipped)
            .putString("updateCheckLog", s.updateCheckLog)
            .putInt("updateAvailable", s.updateAvailable)
            .putString("txnNecessity", json.encodeToString(longStrSerializer, s.txnNecessity))
            .putString("investPlan", json.encodeToString(InvestPlanSettings.serializer(), s.investPlan))
            .putBoolean("stockDisciplineOn", s.investPlan.disciplineOn)
            .putString("stockStyle", json.encodeToString(strMapSerializer, s.stockStyle))
            .putString("advisorReport", s.advisorReport?.let { json.encodeToString(AdvisorReport.serializer(), it) } ?: "")
            .putString("dupVerdicts", json.encodeToString(dupVerdictSerializer, s.dupVerdicts))
            .putBoolean("aiAutoTidy", s.aiAutoTidy)
            .putString("aiTidyLog", json.encodeToString(ListSerializer(String.serializer()), s.aiTidyLog))
            .putString("aiModelVision", s.aiModelVision)
            .putLong("aiUsageDay", s.aiUsageDay)
            .putInt("aiUsageRequests", s.aiUsageRequests)
            .putLong("aiUsageTokens", s.aiUsageTokens)
            .putString("aiSuggestions", json.encodeToString(aiSuggestionSerializer, s.aiSuggestions))
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

/** 這則通知符合哪個本機辨識偏好。 */
fun AppSettings.learnedFor(pkg: String, title: String?, text: String?): LearnedSource? =
    learnKey(title, text).takeIf { it.isNotBlank() }?.let { key -> learnedSources.lastOrNull { it.pkg == pkg && it.title == key } }

private val learnedKeyPattern = Regex("semantic:[0-9a-f]{24}")

@Suppress("UNUSED_PARAMETER")
fun learnKey(title: String?, text: String?): String {
    val source = title?.trim()?.takeIf { it.isNotEmpty() } ?: return ""
    if (learnedKeyPattern.matches(source)) return source
    val normalized = source.lowercase().replace(Regex("\\d+"), "#").replace(Regex("\\s+"), " ").trim()
    val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
    return "semantic:" + digest.joinToString("") { "%02x".format(it) }.take(24)
}

fun AppSettings.shiftFor(cardId: Long): com.enwei.moneymanager.core.StatementShift =
    cardShift[cardId]?.let { n -> com.enwei.moneymanager.core.StatementShift.entries.find { it.name == n } } ?: com.enwei.moneymanager.core.StatementShift.NONE

/** 這張信用卡的帳單週期（含週末調整與銀行的繳款天數）。 */
fun AppSettings.cycleFor(card: AccountEntity): com.enwei.moneymanager.core.CardCycle? {
    val sd = card.statementDay ?: return null
    val off = cardDueOffset[card.id]
    val dd = card.dueDay ?: if (off != null) 1 else return null
    val lag = cardBank[card.id]?.let { code -> com.enwei.moneymanager.core.TaiwanBanks.all.firstOrNull { it.code == code } }?.autopayLagBusinessDays ?: 0
    return com.enwei.moneymanager.core.CardCycle(sd, dd, shiftFor(card.id), off, lag)
}

/** 國定假日快取的編碼與解碼（core 的 YearCalendar 不依賴序列化套件）。 */
/** 颱風假紀錄的編碼（yyyy-MM-dd:名稱;…）。 */
object TyphoonDays {
    fun decode(text: String): Map<java.time.LocalDate, String> = text.split(';').mapNotNull { e ->
        val d = runCatching { java.time.LocalDate.parse(e.substringBefore(':')) }.getOrNull() ?: return@mapNotNull null
        d to e.substringAfter(':', "颱風假")
    }.toMap()

    fun encode(map: Map<java.time.LocalDate, String>): String = map.entries.sortedBy { it.key }
        .joinToString(";") { "${it.key}:${it.value.replace(';', ' ').replace(':', ' ')}" }
}

object HolidayCache {
    fun decode(text: String): Map<Int, com.enwei.moneymanager.core.YearCalendar> = text.lines().mapNotNull { line ->
        val parts = line.split('|')
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        val hol = parts.getOrNull(1).orEmpty().split(';').filter { ':' in it }.associate { it.substringBefore(':') to it.substringAfter(':') }
        val work = parts.getOrNull(2).orEmpty().split(';').filter { it.length == 4 }.toSet()
        val names = parts.getOrNull(3).orEmpty().split(';').filter { ':' in it }.associate { it.substringBefore(':') to it.substringAfter(':') }
        year to com.enwei.moneymanager.core.YearCalendar(hol, work, names)
    }.toMap()

    fun encode(map: Map<Int, com.enwei.moneymanager.core.YearCalendar>): String = map.entries.sortedBy { it.key }.joinToString("\n") { (y, c) ->
        "$y|" + c.holidays.entries.joinToString(";") { "${it.key}:${it.value.replace(';', ' ').replace('|', ' ')}" } + "|" + c.workdays.joinToString(";") +
            "|" + c.names.entries.joinToString(";") { "${it.key}:${it.value.replace(';', ' ').replace('|', ' ')}" }
    }
}
