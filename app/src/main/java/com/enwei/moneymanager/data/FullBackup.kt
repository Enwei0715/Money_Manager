package com.enwei.moneymanager.data

import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.json.encodeToStream

/**
 * 帳理專屬完整備份（.lgbak）：gzip 壓縮的 JSON，包含所有資料表與所有設定。
 * 也包含店家位置、記帳定位、地圖修正與 K 線設定。
 * 不含：GitHub Token 與各種 API 金鑰、股價與匯率快取、新聞資料集（開啟後會自動重新抓取）。
 */
@Serializable
data class FullBackup(
    val format: String = FORMAT,
    val version: Int = 1,
    val appVersion: String,
    val dbVersion: Int,
    val exportedAt: Long,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TxnEntity>,
    val recurring: List<RecurringEntity>,
    val trades: List<StockTradeEntity>,
    val rules: List<MerchantRuleEntity>,
    val notificationLog: List<NotificationLogEntity>,
    /** 所有設定（SharedPreferences 原樣） */
    val settings: Map<String, JsonElement>,
    /** 股票助理：報告（含歷史）、對話、公司說明（新聞資料集本身不備份，會自動重新蒐集） */
    val advisor: NewsStore? = null,
    /** 店家位置、記帳定位、地圖修正、K 線設定等另外存放的資料（檔名 → 內容）；舊備份沒有這欄，還原時保留手機上的 */
    val extras: Map<String, Map<String, JsonElement>>? = null,
    /** 每筆交易記帳時的定位（支出地圖用） */
    val placeSamples: String? = null,
) {
    companion object {
        const val FORMAT = "ledgerly-backup"
        const val EXTENSION = "lgbak"
    }
}

object FullBackupIO {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 一起備份的其他設定檔（金鑰類的 mapbox、gplaces 只存在手機，不備份） */
    private val EXTRA_PREFS = listOf(
        "merchant_pin", "place_hints", "merchant_geo3", "merchant_amb", "merchant_excluded",
        "spot_fix", "spot_snap", "link_coords", "kline",
        // 自架 AI 伺服器的網址與模型（不是金鑰）
        "local_ai",
    )
    private fun prefsOf(name: String) = com.enwei.moneymanager.MoneyApp.instance.getSharedPreferences(name, android.content.Context.MODE_PRIVATE)

    private fun rawOf(p: android.content.SharedPreferences): Map<String, JsonElement> = p.all.mapNotNull { (k, v) ->
        val e: JsonElement = when (v) {
            is Boolean -> kotlinx.serialization.json.buildJsonObject { put("t", kotlinx.serialization.json.JsonPrimitive("b")); put("v", kotlinx.serialization.json.JsonPrimitive(v)) }
            is Int -> kotlinx.serialization.json.buildJsonObject { put("t", kotlinx.serialization.json.JsonPrimitive("i")); put("v", kotlinx.serialization.json.JsonPrimitive(v)) }
            is Long -> kotlinx.serialization.json.buildJsonObject { put("t", kotlinx.serialization.json.JsonPrimitive("l")); put("v", kotlinx.serialization.json.JsonPrimitive(v)) }
            is Float -> kotlinx.serialization.json.buildJsonObject { put("t", kotlinx.serialization.json.JsonPrimitive("f")); put("v", kotlinx.serialization.json.JsonPrimitive(v)) }
            is String -> kotlinx.serialization.json.buildJsonObject { put("t", kotlinx.serialization.json.JsonPrimitive("s")); put("v", kotlinx.serialization.json.JsonPrimitive(v)) }
            is Set<*> -> kotlinx.serialization.json.buildJsonObject {
                put("t", kotlinx.serialization.json.JsonPrimitive("ss"))
                put("v", kotlinx.serialization.json.JsonArray(v.map { kotlinx.serialization.json.JsonPrimitive(it.toString()) }))
            }
            else -> return@mapNotNull null
        }
        k to e
    }.toMap()

    private fun applyRaw(p: android.content.SharedPreferences, map: Map<String, JsonElement>) {
        val ed = p.edit().clear()
        map.forEach { (k, e) ->
            val o = e as? kotlinx.serialization.json.JsonObject ?: return@forEach
            val t = (o["t"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            val v = o["v"] ?: return@forEach
            val pv = v as? kotlinx.serialization.json.JsonPrimitive
            when (t) {
                "b" -> pv?.content?.toBooleanStrictOrNull()?.let { ed.putBoolean(k, it) }
                "i" -> pv?.content?.toIntOrNull()?.let { ed.putInt(k, it) }
                "l" -> pv?.content?.toLongOrNull()?.let { ed.putLong(k, it) }
                "f" -> pv?.content?.toFloatOrNull()?.let { ed.putFloat(k, it) }
                "s" -> pv?.content?.let { ed.putString(k, it) }
                "ss" -> (v as? kotlinx.serialization.json.JsonArray)?.let { arr -> ed.putStringSet(k, arr.map { (it as kotlinx.serialization.json.JsonPrimitive).content }.toSet()) }
            }
        }
        ed.commit()
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    suspend fun export(repo: Repository, appVersion: String, out: OutputStream) {
        val db = repo.db
        val b = FullBackup(
            appVersion = appVersion,
            dbVersion = db.openHelper.readableDatabase.version,
            exportedAt = System.currentTimeMillis(),
            accounts = db.accounts().all(),
            categories = db.categories().all(),
            transactions = db.txns().all(),
            recurring = db.recurring().all(),
            trades = db.stocks().allTrades(),
            rules = db.rules().all(),
            notificationLog = db.notificationLog().all(),
            settings = repo.settings.exportRaw(),
            extras = EXTRA_PREFS.associateWith { rawOf(prefsOf(it)) },
            placeSamples = PlaceHints.exportSamples(com.enwei.moneymanager.MoneyApp.instance),
            advisor = com.enwei.moneymanager.MoneyApp.news.load().let { NewsStore(report = it.report, history = it.history, chat = it.chat, chatThreads = it.chatThreads, business = it.business, historyKeepDays = it.historyKeepDays) },
        )
        // 直接串流寫出，不先組成一整個字串（資料多時避免記憶體不足）
        GZIPOutputStream(out).buffered().use { json.encodeToStream(FullBackup.serializer(), b, it) }
    }

    /** 讀取並驗證；不是帳理備份檔時丟出例外。 */
    fun read(input: InputStream): FullBackup {
        val text = GZIPInputStream(input).bufferedReader().use { it.readText() }
        val b = json.decodeFromString(FullBackup.serializer(), text)
        require(b.format == FullBackup.FORMAT) { "不是帳理備份檔" }
        return b
    }

    /** 以備份完整取代目前所有資料與設定。 */
    suspend fun restore(repo: Repository, b: FullBackup) {
        // 還原前先把目前的資料存一份在 App 內（files/pre_restore.lgbak），還原結果不對時還能找回
        runCatching {
            val f = java.io.File(com.enwei.moneymanager.MoneyApp.instance.filesDir, "pre_restore.lgbak")
            val tmp = java.io.File(f.path + ".tmp")
            tmp.outputStream().use { export(repo, com.enwei.moneymanager.BuildConfig.VERSION_NAME, it) }
            tmp.renameTo(f)
        }
        val db = repo.db
        db.withTransaction {
            db.txns().clear(); db.accounts().clear(); db.categories().clear()
            db.recurring().clear(); db.stocks().clearTrades(); db.rules().clear(); db.notificationLog().clear()
            db.accounts().insertAll(b.accounts)
            db.categories().insertAll(b.categories)
            db.txns().insertAll(b.transactions)
            db.recurring().insertAll(b.recurring)
            db.stocks().insertTrades(b.trades)
            db.rules().insertAll(b.rules)
            db.notificationLog().insertAll(b.notificationLog)
        }
        repo.settings.importRaw(b.settings)
        b.extras?.forEach { (name, map) -> if (name in EXTRA_PREFS) applyRaw(prefsOf(name), map) }
        b.placeSamples?.let { PlaceHints.importSamples(com.enwei.moneymanager.MoneyApp.instance, it) }
        b.advisor?.let { a ->
            com.enwei.moneymanager.MoneyApp.news.update {
                it.copy(report = a.report, history = a.history, chat = a.chat, chatThreads = a.chatThreads.ifEmpty { it.chatThreads }, business = it.business + a.business, historyKeepDays = a.historyKeepDays)
            }
        }
    }
}
